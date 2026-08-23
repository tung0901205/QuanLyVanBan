package com.qlda.authservice.service;

import com.qlda.authservice.common.ErrorCode;
import com.qlda.authservice.config.AuthProperties;
import com.qlda.authservice.dto.backup.BackupCreateResponse;
import com.qlda.authservice.dto.backup.BackupFileNameResponse;
import com.qlda.authservice.dto.backup.BackupItemResponse;
import com.qlda.authservice.dto.backup.CreateBackupRequest;
import com.qlda.authservice.dto.backup.RestoreBackupRequest;
import com.qlda.authservice.exception.ApiException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BackupService {

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final long COMMAND_TIMEOUT_MINUTES = 10;
    private final Path backupDir;
    private final DatabaseConnection database;

    public BackupService(AuthProperties authProperties, DataSourceProperties dataSourceProperties) {
        this.backupDir = Path.of(authProperties.getBackup().getDirectory()).toAbsolutePath().normalize();
        this.database = parseDatabaseConnection(dataSourceProperties);
    }

    public synchronized BackupCreateResponse createBackup(CreateBackupRequest request) {
        String backupType = request.backupType().trim().toUpperCase(Locale.ROOT);
        if (!List.of("FULL", "MANUAL").contains(backupType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                    "backupType must be FULL or MANUAL");
        }
        ensureBackupDirectory();
        String fileName = createDatabaseDump("backup_");
        return new BackupCreateResponse(fileName, "/api/auth/backups/" + fileName + "/download");
    }

    public List<BackupItemResponse> getBackups() {
        ensureBackupDirectory();
        try (var stream = Files.list(backupDir)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".dump"))
                    .sorted(Comparator.comparing(this::lastModified).reversed())
                    .map(path -> new BackupItemResponse(
                            path.getFileName().toString(),
                            getFileSize(path),
                            LocalDateTime.ofInstant(lastModified(path).toInstant(), ZoneId.systemDefault())
                    ))
                    .toList();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR, "Cannot list backups");
        }
    }

    public synchronized BackupFileNameResponse restoreBackup(RestoreBackupRequest request) {
        if (!Boolean.TRUE.equals(request.confirmRestore())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "confirmRestore must be true");
        }
        Path filePath = resolveBackupPath(request.fileName());
        if (!Files.exists(filePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.INVALID_REQUEST, "Backup file not found");
        }
        createDatabaseDump("backup_pre_restore_");
        runCommand(List.of(
                "pg_restore",
                "--host=" + database.host(),
                "--port=" + database.port(),
                "--username=" + database.username(),
                "--dbname=" + database.databaseName(),
                "--clean",
                "--if-exists",
                "--no-owner",
                "--no-privileges",
                "--single-transaction",
                "--exit-on-error",
                filePath.toString()
        ), null);
        return new BackupFileNameResponse(request.fileName());
    }

    public BackupFileNameResponse deleteBackup(String fileName) {
        Path filePath = resolveBackupPath(fileName);
        try {
            if (!Files.exists(filePath)) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.INVALID_REQUEST, "Backup file not found");
            }
            Files.delete(filePath);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR, "Cannot delete backup file");
        }
        return new BackupFileNameResponse(fileName);
    }

    public Path getBackupFile(String fileName) {
        Path filePath = resolveBackupPath(fileName);
        if (!Files.isRegularFile(filePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.INVALID_REQUEST, "Backup file not found");
        }
        return filePath;
    }

    private String createDatabaseDump(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String fileName = prefix + LocalDateTime.now().format(FILE_TIME_FORMATTER) + "_" + suffix + ".dump";
        Path backupPath = resolveBackupPath(fileName);
        try {
            runCommand(List.of(
                    "pg_dump",
                    "--host=" + database.host(),
                    "--port=" + database.port(),
                    "--username=" + database.username(),
                    "--dbname=" + database.databaseName(),
                    "--format=custom",
                    "--compress=6",
                    "--no-owner",
                    "--no-privileges",
                    "--file=" + backupPath
            ), backupPath);
            return fileName;
        } catch (RuntimeException exception) {
            try {
                Files.deleteIfExists(backupPath);
            } catch (IOException ignored) {
                exception.addSuppressed(ignored);
            }
            throw exception;
        }
    }

    private void runCommand(List<String> command, Path partialOutput) {
        ProcessBuilder processBuilder = new ProcessBuilder(new ArrayList<>(command));
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("PGPASSWORD", database.password());
        try {
            Process process = processBuilder.start();
            String output;
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var outputFuture = executor.submit(
                        () -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                if (!process.waitFor(COMMAND_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                    process.destroyForcibly();
                    throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR,
                            "Database backup command timed out");
                }
                output = outputFuture.get(10, TimeUnit.SECONDS);
            }
            if (process.exitValue() != 0) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR,
                        "Database backup command failed: " + abbreviate(output));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR,
                    "Database backup command was interrupted");
        } catch (IOException | ExecutionException | TimeoutException exception) {
            if (partialOutput != null) {
                try {
                    Files.deleteIfExists(partialOutput);
                } catch (IOException ignored) {
                    exception.addSuppressed(ignored);
                }
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR,
                    "Cannot execute database backup command");
        }
    }

    private String abbreviate(String output) {
        String normalized = output == null ? "unknown error" : output.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private void ensureBackupDirectory() {
        try {
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR, "Cannot create backup directory");
        }
    }

    private Path resolveBackupPath(String fileName) {
        if (fileName == null || !fileName.matches("backup_[A-Za-z0-9_-]+\\.dump")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "Invalid backup file name");
        }
        Path resolved = backupDir.resolve(fileName).normalize();
        if (!resolved.startsWith(backupDir)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "Invalid file name");
        }
        return resolved;
    }

    private long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 0;
        }
    }

    private FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException exception) {
            return FileTime.fromMillis(0);
        }
    }

    private DatabaseConnection parseDatabaseConnection(DataSourceProperties properties) {
        String jdbcUrl = properties.determineUrl();
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Backup requires a PostgreSQL JDBC URL");
        }
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String path = uri.getPath();
        if (uri.getHost() == null || path == null || path.length() <= 1) {
            throw new IllegalStateException("Invalid PostgreSQL JDBC URL");
        }
        return new DatabaseConnection(
                uri.getHost(),
                uri.getPort() > 0 ? uri.getPort() : 5432,
                path.substring(1),
                properties.determineUsername(),
                properties.determinePassword()
        );
    }

    private record DatabaseConnection(String host, int port, String databaseName, String username, String password) {
    }
}
