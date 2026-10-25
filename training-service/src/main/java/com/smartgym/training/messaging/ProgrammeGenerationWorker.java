package com.smartgym.training.messaging;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgym.training.ai.ProgrammeGenerationPayload;
import com.smartgym.training.ai.ProgrammeGenerator;
import com.smartgym.training.application.AiJobService;
import com.smartgym.training.config.TrainingMessagingConfiguration;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;

@Component
@ConditionalOnProperty(name = "app.ai.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class ProgrammeGenerationWorker {
    private static final TypeReference<MessageEnvelope<ProgrammeGenerationPayload>> COMMAND_TYPE =
            new TypeReference<>() {};
    private static final int MAX_OUTPUT_CHARACTERS = 131_072;
    private final ObjectMapper objectMapper;
    private final ProgrammeGenerator generator;
    private final ReliableRabbitPublisher publisher;

    public ProgrammeGenerationWorker(
            ObjectMapper objectMapper,
            ProgrammeGenerator generator,
            ReliableRabbitPublisher publisher) {
        this.objectMapper = objectMapper;
        this.generator = generator;
        this.publisher = publisher;
    }

    @RabbitListener(
            queues = TrainingMessagingConfiguration.COMMAND_QUEUE,
            containerFactory = "manualAckRabbitListenerContainerFactory")
    public void receive(
            Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        MessageEnvelope<ProgrammeGenerationPayload> envelope;
        try {
            envelope = objectMapper.readValue(message.getBody(), COMMAND_TYPE);
            validateEnvelope(envelope);
        } catch (IOException | IllegalArgumentException exception) {
            channel.basicReject(deliveryTag, false);
            return;
        }

        int attempt = deliveryAttempt(message);
        try {
            String generatedJson = generateWithRepair(envelope.payload());
            if (generatedJson.length() > MAX_OUTPUT_CHARACTERS) {
                throw new IllegalArgumentException("Generated output exceeded the configured response limit.");
            }
            publishResult(envelope, new ProgrammeGenerationResult(generatedJson, null), true);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            if (attempt + 1 < TrainingMessagingConfiguration.MAX_DELIVERY_ATTEMPTS) {
                publisher.publishPersistentToQueue(
                        TrainingMessagingConfiguration.RETRY_QUEUE,
                        envelope.messageId(),
                        new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8),
                        attempt + 1);
                channel.basicAck(deliveryTag, false);
            } else {
                publishResult(
                        envelope,
                        new ProgrammeGenerationResult(
                                null,
                                "Local programme generation failed after bounded retries. Check model availability or retry the job."),
                        false);
                channel.basicReject(deliveryTag, false);
            }
        }
    }

    private String generateWithRepair(ProgrammeGenerationPayload payload) {
        String output = generator.generate(payload, null);
        try {
            objectMapper.readTree(output);
            return output;
        } catch (IOException firstFailure) {
            String repaired = generator.generate(payload, "The previous response was not valid JSON. Return one complete JSON object only.");
            try {
                objectMapper.readTree(repaired);
                return repaired;
            } catch (IOException secondFailure) {
                throw new IllegalArgumentException("The model returned malformed JSON after one repair attempt.", secondFailure);
            }
        }
    }

    private void publishResult(
            MessageEnvelope<ProgrammeGenerationPayload> command,
            ProgrammeGenerationResult result,
            boolean success) {
        try {
            MessageEnvelope<ProgrammeGenerationResult> response = new MessageEnvelope<>(
                    UUID.randomUUID(),
                    command.correlationId(),
                    command.jobId(),
                    AiJobService.RESULT_MESSAGE_TYPE,
                    1,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    result);
            String body = objectMapper.writeValueAsString(response);
            publisher.publish(
                    AiJobService.EXCHANGE,
                    success ? AiJobService.COMPLETED_ROUTING_KEY : AiJobService.FAILED_ROUTING_KEY,
                    response.messageId(),
                    body);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize the generation result event.", exception);
        }
    }

    private static int deliveryAttempt(Message message) {
        Object value = message.getMessageProperties()
                .getHeaders()
                .get(TrainingMessagingConfiguration.ATTEMPT_HEADER);
        return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
    }

    private static void validateEnvelope(MessageEnvelope<ProgrammeGenerationPayload> envelope) {
        if (envelope == null
                || envelope.messageId() == null
                || envelope.correlationId() == null
                || envelope.jobId() == null
                || envelope.version() != 1
                || !AiJobService.COMMAND_MESSAGE_TYPE.equals(envelope.messageType())
                || envelope.payload() == null
                || envelope.payload().parameters() == null
                || envelope.payload().candidates() == null
                || envelope.payload().candidates().isEmpty()) {
            throw new IllegalArgumentException("Invalid training programme generation command envelope.");
        }
    }
}
