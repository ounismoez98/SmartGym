package com.smartgym.training.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class ReliableRabbitPublisher {
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(15);
    private final RabbitTemplate rabbitTemplate;

    public ReliableRabbitPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String exchange, String routingKey, UUID messageId, String json) {
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(messageId.toString())
                .build();
        CorrelationData correlation = new CorrelationData(messageId.toString());
        rabbitTemplate.send(exchange, routingKey, message, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(CONFIRM_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ rejected the message publish.");
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned an unroutable message.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting RabbitMQ publisher confirmation.", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("RabbitMQ publisher confirmation was not received.", exception);
        }
    }

    public void publishPersistentToQueue(String queueName, UUID messageId, String json, int attempt) {
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(messageId.toString())
                .setHeader(com.smartgym.training.config.TrainingMessagingConfiguration.ATTEMPT_HEADER, attempt)
                .build();
        CorrelationData correlation = new CorrelationData(messageId + "-" + attempt);
        rabbitTemplate.send("", queueName, message, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(CONFIRM_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ did not confirm the retry message.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting RabbitMQ retry confirmation.", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("RabbitMQ retry confirmation was not received.", exception);
        }
    }
}
