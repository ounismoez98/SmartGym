# Module 1 assumptions

- A renewal is created only after the member's current subscription expires;
  the module permits at most one `ACTIVE` subscription per member.
- Changing the plan on an `ACTIVE` subscription changes only its `planId`.
  Existing `startsAt` and `endsAt` values are preserved, and both plan IDs are
  recorded in the audit entry.
- Subscription plan deactivation (`active=false`) is preferred to deletion.
  Deletion is allowed only when no subscription refers to the plan.
- Payment correction is append-only: the original amount is preserved, a new
  `RECORDED` entry is linked to it, and the correction retains the original
  currency, payment method, and `paidAt`.
- The Groq integration is configurable but the selected remote-provider choice
  should be confirmed with the project supervisor because the original
  specification requests a local LLM. No real provider key is stored in the
  repository.
- If a reminder cannot be validated against its subscription facts after the
  single retry, the system stores an editable `DRAFT` with `[À COMPLÉTER]`
  placeholders rather than sending unverified information.
- MailHog is the local development SMTP sink. Sending a reminder records it as
  `SENT` only after the SMTP send call succeeds.
- RabbitMQ event delivery is best effort after database commit. The current
  implementation does not use an outbox or retry queue; broker failure is
  logged without failing the committed business operation.
