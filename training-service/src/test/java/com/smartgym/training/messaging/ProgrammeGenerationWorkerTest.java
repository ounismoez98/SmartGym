package com.smartgym.training.messaging;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.smartgym.training.ai.CandidateExercise;
import com.smartgym.training.ai.GenerationParameters;
import com.smartgym.training.ai.ProgrammeGenerationPayload;
import com.smartgym.training.ai.ProgrammeGenerator;
import com.smartgym.training.domain.EquipmentCategory;
import com.smartgym.training.domain.ExerciseDifficulty;
import com.smartgym.training.domain.ExperienceLevel;
import com.smartgym.training.domain.MuscleGroup;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class ProgrammeGenerationWorkerTest {
    private static final TypeReference<MessageEnvelope<ProgrammeGenerationResult>> RESULT_TYPE =
            new TypeReference<>() {};

    @Test
    void malformedModelJsonGetsOneRepairAttemptThenPublishesResultBeforeAck() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        UUID exerciseId = UUID.randomUUID();
        MessageEnvelope<ProgrammeGenerationPayload> command = command(exerciseId);
        String validOutput = """
                {"title":"Draft","goal":"Strength","durationWeeks":4,"sessions":[]}
                """;
        ProgrammeGenerator generator = new ProgrammeGenerator() {
            private int calls;

            @Override
            public String generate(ProgrammeGenerationPayload payload, String repairFeedback) {
                calls++;
                return calls == 1 ? "{invalid" : validOutput;
            }
        };
        ReliableRabbitPublisher publisher = mock(ReliableRabbitPublisher.class);
        Channel channel = mock(Channel.class);
        ProgrammeGenerationWorker worker = new ProgrammeGenerationWorker(mapper, generator, publisher);

        worker.receive(message(mapper, command, null), channel, 9L);

        verify(publisher).publish(
                eq("smartgym.training"),
                eq("training.programme-generation.completed.v1"),
                org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.contains("\"outputJson\""));
        verify(channel).basicAck(9L, false);
        verify(channel, never()).basicReject(eq(9L), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void workerRetriesTransientFailureBoundedlyThenPublishesFailure() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        MessageEnvelope<ProgrammeGenerationPayload> command = command(UUID.randomUUID());
        ProgrammeGenerator generator = (payload, repair) -> {
            throw new IllegalStateException("Model unavailable");
        };
        ReliableRabbitPublisher publisher = mock(ReliableRabbitPublisher.class);
        Channel channel = mock(Channel.class);
        ProgrammeGenerationWorker worker = new ProgrammeGenerationWorker(mapper, generator, publisher);

        worker.receive(message(mapper, command, 0), channel, 10L);
        worker.receive(message(mapper, command, 1), channel, 11L);
        worker.receive(message(mapper, command, 2), channel, 12L);

        verify(publisher, times(2)).publishPersistentToQueue(
                eq("training.programme-generation.retry.v1"),
                eq(command.messageId()),
                org.mockito.ArgumentMatchers.anyString(),
                anyInt());
        verify(publisher).publish(
                eq("smartgym.training"),
                eq("training.programme-generation.failed.v1"),
                org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.contains("bounded retries"));
        verify(channel).basicAck(10L, false);
        verify(channel).basicAck(11L, false);
        verify(channel).basicReject(12L, false);
    }

    private static MessageEnvelope<ProgrammeGenerationPayload> command(UUID exerciseId) {
        return new MessageEnvelope<>(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "training.programme-generation.command",
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                new ProgrammeGenerationPayload(
                        new GenerationParameters("Strength", ExperienceLevel.BEGINNER, 1, 60,
                                List.of(EquipmentCategory.DUMBBELL), null, null),
                        List.of(new CandidateExercise(
                                exerciseId,
                                "Dumbbell row",
                                "Pull with control.",
                                MuscleGroup.BACK,
                                ExerciseDifficulty.BEGINNER,
                                EquipmentCategory.DUMBBELL))));
    }

    private static Message message(
            ObjectMapper mapper,
            MessageEnvelope<ProgrammeGenerationPayload> command,
            Integer attempt) throws Exception {
        MessageProperties properties = new MessageProperties();
        if (attempt != null) properties.setHeader("x-smartgym-attempt", attempt);
        return new Message(mapper.writeValueAsBytes(command), properties);
    }
}
