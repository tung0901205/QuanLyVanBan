package com.qlda.workflowservice.event.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qlda.workflowservice.event.NotificationEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaNotificationEventPublisher implements NotificationEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaNotificationEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.notification-topic:notification-events}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(NotificationEvent event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("Notification event and eventId are required");
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.eventId(), payload).get(10, TimeUnit.SECONDS);
            log.debug("Notification event delivered to Kafka: type={}, referenceId={}",
                    event.eventType(), event.referenceId());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize notification event", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cannot publish notification event to Kafka", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Cannot publish notification event to Kafka", exception);
        }
    }
}
