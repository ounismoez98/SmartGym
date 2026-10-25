package com.smartgym.membership.events;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    @ParameterizedTest
    @MethodSource("eventContracts")
    void publishesEveryEventWithRequiredFieldsAndNoPersonalContactData(
            String type, Map<String, Object> data, String[] requiredFields) throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> eventPublisher.publish(type, data));

        org.mockito.ArgumentCaptor<MembershipDomainEvent> eventCaptor =
                org.mockito.ArgumentCaptor.forClass(MembershipDomainEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("gym.events"), eq(type), eventCaptor.capture());
        JsonNode envelope = objectMapper.readTree(objectMapper.writeValueAsString(eventCaptor.getValue()));
        assertThat(envelope.get("type").asText()).isEqualTo(type);
        assertThat(envelope.get("data").properties())
                .extracting(Map.Entry::getKey)
                .contains(requiredFields);
        String json = objectMapper.writeValueAsString(envelope);
        assertThat(json).doesNotContain("email", "phone", "@", "+1 202-555");
    }

    private static Stream<Arguments> eventContracts() {
        String memberId = UUID.randomUUID().toString();
        String employeeId = UUID.randomUUID().toString();
        String planId = UUID.randomUUID().toString();
        String subscriptionId = UUID.randomUUID().toString();
        String paymentId = UUID.randomUUID().toString();
        String correctionId = UUID.randomUUID().toString();
        Map<String, Object> member = Map.of("memberId", memberId, "status", "ACTIVE");
        Map<String, Object> employee = Map.of("employeeId", employeeId, "status", "ACTIVE");
        Map<String, Object> subscription = Map.of(
                "subscriptionId", subscriptionId,
                "memberId", memberId,
                "planId", planId,
                "status", "ACTIVE",
                "startsAt", "2026-10-25T00:00:00Z",
                "endsAt", "2026-11-24T00:00:00Z");
        Map<String, Object> payment = Map.of(
                "paymentId", paymentId,
                "memberId", memberId,
                "amountCents", 2400,
                "currency", "TND",
                "status", "RECORDED",
                "paidAt", "2026-10-25T00:00:00Z");
        Map<String, Object> correctedPayment = Map.of(
                "paymentId", paymentId,
                "correctionPaymentId", correctionId,
                "memberId", memberId,
                "oldAmountCents", 2400,
                "newAmountCents", 2200,
                "status", "CORRECTED");
        return Stream.of(
                Arguments.of("member.created", member, new String[] {"memberId", "status"}),
                Arguments.of("member.updated", member, new String[] {"memberId", "status"}),
                Arguments.of("member.deactivated", member, new String[] {"memberId", "status"}),
                Arguments.of("employee.created", employee, new String[] {"employeeId", "status"}),
                Arguments.of("employee.deactivated", employee, new String[] {"employeeId", "status"}),
                Arguments.of("subscription.created", subscription,
                        new String[] {"subscriptionId", "memberId", "planId", "status", "startsAt", "endsAt"}),
                Arguments.of("subscription.cancelled", subscription,
                        new String[] {"subscriptionId", "memberId", "planId", "status", "startsAt", "endsAt"}),
                Arguments.of("subscription.expired", subscription,
                        new String[] {"subscriptionId", "memberId", "planId", "status", "startsAt", "endsAt"}),
                Arguments.of("payment.recorded", payment,
                        new String[] {"paymentId", "memberId", "amountCents", "currency", "status", "paidAt"}),
                Arguments.of("payment.corrected", correctedPayment,
                        new String[] {"paymentId", "correctionPaymentId", "memberId",
                                "oldAmountCents", "newAmountCents", "status"}));
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
