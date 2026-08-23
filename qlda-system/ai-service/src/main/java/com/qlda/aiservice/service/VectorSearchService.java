package com.qlda.aiservice.service;

import com.qlda.aiservice.entity.AiDocumentChunkEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final JdbcTemplate jdbcTemplate;

    private static final String SELECT_COLS = """
        id, van_ban_id, tep_dinh_kem_id, chunk_index, noi_dung,
        embedding::text AS embedding, metadata::text AS metadata, ngay_tao
        """;

    /**
     * Generic similarity search, used by chatbot where guide chunks are allowed.
     */
    public List<AiDocumentChunkEntity> findTopKBySimilarity(
            List<Double> queryEmbedding, Long vanBanId, int topK) {

        if (queryEmbedding == null || queryEmbedding.isEmpty()) {
            log.warn("Empty query embedding, returning empty results");
            return List.of();
        }

        String vectorStr = toVectorString(queryEmbedding);

        if (vanBanId != null) {
            return jdbcTemplate.query(
                "SELECT " + SELECT_COLS + ", 1 - (embedding <=> ?::vector) AS similarity_score " +
                "FROM ai_document_chunk WHERE van_ban_id = ? ORDER BY embedding <=> ?::vector LIMIT ?",
                chunkRowMapper(),
                vectorStr, vanBanId, vectorStr, topK
            );
        }

        return jdbcTemplate.query(
            "SELECT " + SELECT_COLS + ", 1 - (embedding <=> ?::vector) AS similarity_score " +
            "FROM ai_document_chunk ORDER BY embedding <=> ?::vector LIMIT ?",
            chunkRowMapper(),
            vectorStr, vectorStr, topK
        );
    }

    /**
     * Search only real document chunks. Guide chunks use van_ban_id=0 and must
     * never appear on the "Tìm kiếm văn bản" screen.
     */
    public List<AiDocumentChunkEntity> findTopKDocumentChunksBySimilarity(
            List<Double> queryEmbedding, Long vanBanId, int topK) {
        if (queryEmbedding == null || queryEmbedding.isEmpty()) return List.of();
        String vectorStr = toVectorString(queryEmbedding);
        if (vanBanId != null && vanBanId > 0) {
            return jdbcTemplate.query(
                "SELECT " + SELECT_COLS + ", 1 - (embedding <=> ?::vector) AS similarity_score " +
                "FROM ai_document_chunk " +
                "WHERE van_ban_id = ? AND van_ban_id > 0 " +
                "AND COALESCE(metadata::jsonb ->> 'type','') <> 'huong_dan' " +
                "ORDER BY embedding <=> ?::vector LIMIT ?",
                chunkRowMapper(), vectorStr, vanBanId, vectorStr, topK
            );
        }
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLS + ", 1 - (embedding <=> ?::vector) AS similarity_score " +
            "FROM ai_document_chunk " +
            "WHERE van_ban_id > 0 " +
            "AND COALESCE(metadata::jsonb ->> 'type','') <> 'huong_dan' " +
            "ORDER BY embedding <=> ?::vector LIMIT ?",
            chunkRowMapper(), vectorStr, vectorStr, topK
        );
    }

    public List<AiDocumentChunkEntity> findTopKUserGuide(List<Double> queryEmbedding, int topK) {
        if (queryEmbedding == null || queryEmbedding.isEmpty()) return List.of();
        String vectorStr = toVectorString(queryEmbedding);
        return jdbcTemplate.query(
            "SELECT " + SELECT_COLS + ", 1 - (embedding <=> ?::vector) AS similarity_score " +
            "FROM ai_document_chunk WHERE metadata::jsonb ->> 'type' = 'huong_dan' " +
            "ORDER BY embedding <=> ?::vector LIMIT ?",
            chunkRowMapper(), vectorStr, vectorStr, topK
        );
    }

    public void insertChunks(List<AiDocumentChunkEntity> entities) {
        if (entities == null || entities.isEmpty()) return;
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ai_document_chunk
                (van_ban_id, tep_dinh_kem_id, chunk_index, noi_dung, embedding, metadata, ngay_tao)
            VALUES (?, ?, ?, ?, ?::vector, ?::jsonb, ?)
            """,
            entities,
            entities.size(),
            (PreparedStatement ps, AiDocumentChunkEntity e) -> {
                ps.setLong(1, e.getVanBanId());
                if (e.getTepDinhKemId() != null) ps.setLong(2, e.getTepDinhKemId());
                else ps.setNull(2, Types.BIGINT);
                ps.setInt(3, e.getChunkIndex());
                ps.setString(4, e.getNoiDung());
                ps.setString(5, e.getEmbedding());
                ps.setString(6, e.getMetadata() != null ? e.getMetadata() : "{}");
                ps.setTimestamp(7, e.getNgayTao() != null
                    ? Timestamp.valueOf(e.getNgayTao())
                    : new Timestamp(System.currentTimeMillis()));
            }
        );
        log.debug("Inserted {} chunks", entities.size());
    }

    public long countGuideChunks() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ai_document_chunk WHERE metadata::jsonb ->> 'type' = 'huong_dan'",
            Integer.class
        );
        return count != null ? count : 0L;
    }

    public String toVectorString(List<Double> embedding) {
        return "[" + embedding.stream()
            .map(d -> String.format(java.util.Locale.ROOT, "%.8f", d))
            .collect(Collectors.joining(",")) + "]";
    }

    private RowMapper<AiDocumentChunkEntity> chunkRowMapper() {
        return (rs, rowNum) -> {
            AiDocumentChunkEntity e = new AiDocumentChunkEntity();
            e.setId(rs.getLong("id"));
            long vanBanId = rs.getLong("van_ban_id");
            e.setVanBanId(rs.wasNull() ? null : vanBanId);
            long tepId = rs.getLong("tep_dinh_kem_id");
            e.setTepDinhKemId(rs.wasNull() ? null : tepId);
            e.setChunkIndex(rs.getInt("chunk_index"));
            e.setNoiDung(rs.getString("noi_dung"));
            e.setEmbedding(rs.getString("embedding"));
            e.setMetadata(rs.getString("metadata"));
            Timestamp ts = rs.getTimestamp("ngay_tao");
            e.setNgayTao(ts != null ? ts.toLocalDateTime() : null);
            try {
                e.setSimilarityScore(rs.getDouble("similarity_score"));
                if (rs.wasNull()) e.setSimilarityScore(null);
            } catch (Exception ignored) {
                e.setSimilarityScore(null);
            }
            return e;
        };
    }
}
