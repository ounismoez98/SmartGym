package com.smartgym.membership.events;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles({"test", "dev"})
class MembershipEventPublisherTest {
    @Autowired
    private MembershipEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void resetRabbitMock() {
        reset(rabbitTemplate);
    }

    @Test
    void publishesJsonContractOnlyAfterCommit() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID memberId = UUID.randomUUID();
        transaction.executeWithoutResult(status -> eventPublisher.publish(
                "member.created", Map.of("memberId", memberId.toString(), "status", "ACTIVE")));

        org.mockito.ArgumentCaptor<MembershipDomainEvent> eventCaptor =
                org.mockito.ArgumentCaptor.forClass(MembershipDomainEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("gym.events"), eq("member.created"), eventCaptor.capture());
        String json = objectMapper.writeValueAsString(eventCaptor.getValue());
        JsonNode envelope = objectMapper.readTree(json);
        assertThat(envelope.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("eventId", "type", "occurredAt", "data");
        assertThat(envelope.get("eventId").asText()).isNotBlank();
        assertThat(envelope.get("type").asText()).isEqualTo("member.created");
        assertThat(envelope.get("occurredAt").asText()).contains("T");
        assertThat(envelope.get("data").get("memberId").asText()).isEqualTo(memberId.toString());
        assertThat(json).doesNotContain("email", "phone", "keycloak");
    }

    @Test
    void doesNotPublishEventWhenTransactionRollsBack() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            eventPublisher.publish("payment.recorded", Map.of("paymentId", UUID.randomUUID().toString()));
            status.setRollbackOnly();
        });

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void brokerFailureDoesNotFailCommittedBusinessOperation() {
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(eq("gym.events"), eq("payment.recorded"), any(Object.class));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> eventPublisher.publish(
                "payment.recorded", Map.of("paymentId", UUID.randomUUID().toString())));
    }
}
