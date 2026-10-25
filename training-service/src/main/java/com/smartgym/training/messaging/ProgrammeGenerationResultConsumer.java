package com.smartgym.training.messaging;

import java.io.IOException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.training.application.AiJobService;
import com.smartgym.training.config.TrainingMessagingConfiguration;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class ProgrammeGenerationResultConsumer {
    private static final TypeReference<MessageEnvelope<ProgrammeGenerationResult>> RESULT_TYPE =
            new TypeReference<>() {};
    private final ObjectMapper objectMapper;
    private final AiJobService jobs;

    public ProgrammeGenerationResultConsumer(ObjectMapper objectMapper, AiJobService jobs) {
        this.objectMapper = objectMapper;
        this.jobs = jobs;
    }

    @RabbitListener(
            queues = TrainingMessagingConfiguration.RESULT_QUEUE,
            containerFactory = "manualAckRabbitListenerContainerFactory")
    public void receive(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        MessageEnvelope<ProgrammeGenerationResult> envelope;
        try {
            envelope = objectMapper.readValue(message.getBody(), RESULT_TYPE);
            if (envelope == null
                    || envelope.messageId() == null
                    || envelope.jobId() == null
                    || envelope.version() != 1
                    || !AiJobService.RESULT_MESSAGE_TYPE.equals(envelope.messageType())
                    || envelope.payload() == null) {
                throw new IllegalArgumentException("Invalid training programme generation result envelope.");
            }
        } catch (IOException | IllegalArgumentException exception) {
            channel.basicReject(deliveryTag, false);
            return;
        }
        jobs.handleResult(
                envelope.jobId(),
                envelope.payload().outputJson(),
                envelope.payload().failureReason());
        channel.basicAck(deliveryTag, false);
    }
}
