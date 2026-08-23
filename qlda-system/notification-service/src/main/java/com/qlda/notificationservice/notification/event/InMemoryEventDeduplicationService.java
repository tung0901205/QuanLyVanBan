package com.qlda.notificationservice.notification.event;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class InMemoryEventDeduplicationService implements EventDeduplicationService {

    private final JdbcTemplate jdbcTemplate;

    public InMemoryEventDeduplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_notification_event WHERE event_id = ?",
                Integer.class,
                eventId
        );
        return count != null && count > 0;
    }

    @Override
    public void markProcessed(String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            jdbcTemplate.update("""
                    INSERT INTO processed_notification_event (event_id, processed_at)
                    VALUES (?, CURRENT_TIMESTAMP)
                    ON CONFLICT (event_id) DO NOTHING
                    """, eventId);
        }
    }
}
