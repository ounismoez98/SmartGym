package com.smartgym.training.messaging;

import java.util.UUID;

import com.smartgym.training.application.AiJobService;
import com.smartgym.training.domain.TrainingOutboxMessage;
import com.smartgym.training.repository.TrainingOutboxRepository;
import com.smartgym.training.config.TrainingMessagingConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.ai.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class TrainingOutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrainingOutboxPublisher.class);
    private final TrainingOutboxRepository outbox;
    private final ReliableRabbitPublisher publisher;

    public TrainingOutboxPublisher(TrainingOutboxRepository outbox, ReliableRabbitPublisher publisher) {
        this.outbox = outbox;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.ai.messaging.outbox-poll-delay:1000}")
    @Transactional
    public void publishPending() {
        for (TrainingOutboxMessage message : outbox.lockUnpublished(PageRequest.of(0, 10))) {
            try {
                publisher.publish(
                        AiJobService.EXCHANGE,
                        message.getRoutingKey(),
                        message.getMessageId(),
                        message.getPayloadJson());
                message.markPublished();
            } catch (RuntimeException exception) {
                message.markPublishFailed(exception.getMessage());
                LOGGER.warn("Training outbox message {} remains pending after publish failure.", message.getMessageId());
                break;
            }
        }
    }
}
