package com.qlda.authservice.service;

import com.qlda.authservice.common.ErrorCode;
import com.qlda.authservice.dto.audit.AuditLogExportResponse;
import com.qlda.authservice.dto.audit.AuditLogResponse;
import com.qlda.authservice.dto.common.PageData;
import com.qlda.authservice.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PersistentAuditLogStore {

    private static final String SELECT_COLUMNS = """
            SELECT id, user_id, full_name, action, object_name, object_id,
                   detail, ip_address, performed_at, status
            FROM auth_audit_log
            """;

    private final JdbcTemplate jdbcTemplate;

    public PersistentAuditLogStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Long userId, String fullName, String action, String objectName,
                       Long objectId, String detail, String ipAddress, Integer status) {
        jdbcTemplate.update("""
                INSERT INTO auth_audit_log
                    (user_id, full_name, action, object_name, object_id, detail,
                     ip_address, performed_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """, userId, fullName, action, objectName, objectId, detail,
                ipAddress, status == null ? 1 : status);
    }

    public PageData<AuditLogResponse> findPage(Pageable pageable, Long userId, String action,
                                                String objectName, LocalDate fromDate, LocalDate toDate) {
        QueryFilter filter = buildFilter(userId, action, objectName, fromDate, toDate);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_audit_log" + filter.whereClause(),
                Long.class, filter.arguments().toArray());
        List<Object> args = new ArrayList<>(filter.arguments());
        args.add(pageable.getPageSize());
        args.add(pageable.getOffset());
        List<AuditLogResponse> content = jdbcTemplate.query(
                SELECT_COLUMNS + filter.whereClause()
                        + " ORDER BY performed_at DESC, id DESC LIMIT ? OFFSET ?",
                this::mapRow, args.toArray());
        long totalElements = total == null ? 0L : total;
        int totalPages = pageable.getPageSize() == 0 ? 0
                : (int) Math.ceil((double) totalElements / pageable.getPageSize());
        return new PageData<>(content, pageable.getPageNumber(), pageable.getPageSize(),
                totalElements, totalPages);
    }

    public AuditLogResponse findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(SELECT_COLUMNS + " WHERE id = ?", this::mapRow, id);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.AUDIT_LOG_NOT_FOUND,
                    "Audit log not found");
        }
    }

    public AuditLogExportResponse export(LocalDate fromDate, LocalDate toDate) {
        QueryFilter filter = buildFilter(null, null, null, fromDate, toDate);
        List<AuditLogResponse> rows = jdbcTemplate.query(
                SELECT_COLUMNS + filter.whereClause() + " ORDER BY performed_at DESC, id DESC",
                this::mapRow, filter.arguments().toArray());
        StringBuilder csv = new StringBuilder(
                "Thời gian,Người dùng,Hành động,Đối tượng,Chi tiết,IP,Trạng thái\r\n");
        for (AuditLogResponse row : rows) {
            appendCsvRow(csv, String.valueOf(row.thoiGianThucHien()), row.hoTen(),
                    row.hanhDong(), row.doiTuong(), row.noiDungChiTiet(), row.diaChiIp(),
                    Integer.valueOf(1).equals(row.trangThai()) ? "Thành công" : "Thất bại");
        }
        byte[] bytes = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        String dataUrl = "data:text/csv;charset=utf-8;base64,"
                + Base64.getEncoder().encodeToString(bytes);
        return new AuditLogExportResponse("audit-log-" + LocalDate.now() + ".csv", dataUrl);
    }

    private QueryFilter buildFilter(Long userId, String action, String objectName,
                                    LocalDate fromDate, LocalDate toDate) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (userId != null) {
            where.append(" AND user_id = ?");
            args.add(userId);
        }
        if (StringUtils.hasText(action)) {
            where.append(" AND LOWER(action) LIKE ?");
            args.add("%" + action.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (StringUtils.hasText(objectName)) {
            where.append(" AND LOWER(object_name) LIKE ?");
            args.add("%" + objectName.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (fromDate != null) {
            where.append(" AND performed_at >= ?");
            args.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            where.append(" AND performed_at <= ?");
            args.add(Timestamp.valueOf(LocalDateTime.of(toDate, LocalTime.MAX)));
        }
        return new QueryFilter(where.toString(), args);
    }

    private AuditLogResponse mapRow(ResultSet rs, int rowNumber) throws SQLException {
        Timestamp performedAt = rs.getTimestamp("performed_at");
        return new AuditLogResponse(rs.getLong("id"), nullableLong(rs, "user_id"),
                rs.getString("full_name"), rs.getString("action"), rs.getString("object_name"),
                nullableLong(rs, "object_id"), rs.getString("detail"), rs.getString("ip_address"),
                performedAt == null ? null : performedAt.toLocalDateTime(), rs.getInt("status"));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private void appendCsvRow(StringBuilder csv, String... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) csv.append(',');
            String value = values[index] == null ? "" : values[index];
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    private record QueryFilter(String whereClause, List<Object> arguments) {
    }
}
