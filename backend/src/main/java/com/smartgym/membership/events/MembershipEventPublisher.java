package com.smartgym.membership.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.context.ApplicationEventPublisher;

@Component
public class MembershipEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MembershipEventPublisher.class);
    private static final String EXCHANGE = "gym.events";

    private final ApplicationEventPublisher applicationEventPublisher;
    private final RabbitTemplate rabbitTemplate;

    public MembershipEventPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            RabbitTemplate rabbitTemplate) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String type, Map<String, Object> data) {
        applicationEventPublisher.publishEvent(
                new MembershipDomainEvent(UUID.randomUUID(), type, Instant.now(), data));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(MembershipDomainEvent event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, event.type(), event);
        } catch (RuntimeException exception) {
            LOGGER.warn("Membership event delivery failed: eventId={}, type={}", event.eventId(), event.type());
        }
    }
}
