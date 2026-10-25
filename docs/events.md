# Membership domain events

The API publishes events to the durable topic exchange `gym.events` after the
database transaction commits. The routing key is the event `type`.

Each message is JSON with this envelope:

```json
{
  "eventId": "2fb301d0-598c-482a-a08d-4b5073ec0a59",
  "type": "payment.recorded",
  "occurredAt": "2026-10-25T00:00:00Z",
  "data": {
    "paymentId": "c969005a-c16d-4b73-a574-9259757c20f7",
    "memberId": "d6496eac-f452-4026-9a82-a39abec38b20",
    "amountCents": 2400,
    "currency": "TND",
    "status": "RECORDED",
    "paidAt": "2026-10-24T00:00:00Z"
  }
}
```

Supported types:

| Type | Data fields |
| --- | --- |
| `member.created` | `memberId`, `status` |
| `member.updated` | `memberId`, `status` |
| `member.deactivated` | `memberId`, `status` |
| `employee.created` | `employeeId`, `status` |
| `employee.deactivated` | `employeeId`, `status` |
| `subscription.created` | `subscriptionId`, `memberId`, `planId`, `status`, `startsAt`, `endsAt` |
| `subscription.cancelled` | `subscriptionId`, `memberId`, `planId`, `status`, `startsAt`, `endsAt` |
| `subscription.expired` | `subscriptionId`, `memberId`, `planId`, `status`, `startsAt`, `endsAt` |
| `payment.recorded` | `paymentId`, `memberId`, `amountCents`, `currency`, `status`, `paidAt` |
| `payment.corrected` | `paymentId`, `correctionPaymentId`, `memberId`, `oldAmountCents`, `newAmountCents`, `status` |

Events contain identifiers and business facts only. Do not add member or
employee names, email addresses, phone numbers, Keycloak subjects, or tokens.

The current delivery policy is best effort: publication happens after commit,
so a broker outage never rolls back or fails the business operation. The
failure is logged with only event ID and type. The current implementation does
not persist an outbox or retry failed publications; such events can be lost if
RabbitMQ is unavailable.
