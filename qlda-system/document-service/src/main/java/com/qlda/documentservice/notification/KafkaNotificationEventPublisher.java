package com.qlda.documentservice.notification;

import com.qlda.documentservice.notification.dto.NotificationEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class KafkaNotificationEventPublisher implements NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationEventPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String notificationTopic;

    public KafkaNotificationEventPublisher(
        KafkaTemplate<Object, Object> kafkaTemplate,
        @Value("${app.kafka.notification-topic:notification-events}") String notificationTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.notificationTopic = notificationTopic;
    }

    @Override
    public void publish(NotificationEvent event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("Notification event and eventId are required");
        }
        String key = event.referenceId() == null ? event.eventId() : String.valueOf(event.referenceId());
        try {
            kafkaTemplate.send(notificationTopic, key, event).get(10, TimeUnit.SECONDS);
            log.debug("Notification event delivered to Kafka: type={}, referenceId={}",
                    event.eventType(), event.referenceId());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cannot publish notification event to Kafka", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Cannot publish notification event to Kafka", exception);
        }
    }
}
