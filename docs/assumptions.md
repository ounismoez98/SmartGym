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
- LLM prompts do not contain member names, emails, phone numbers, or UUIDs.
  Reminder prompts use `{{FIRST_NAME}}`, which the API renders only after
  validating the generated date and amount; a missing first name renders as
  `Bonjour`. Free-form questions and policy/plan text are scanned for email
  addresses, phone-like number sequences, and UUIDs and those patterns are
  masked. Validated gym policies sent to the provider must remain general
  policy text and must not include personal information; automated masking is
  not a reliable detector for human names.
- If a reminder cannot be validated against its subscription facts after the
  single retry, the system stores an editable `DRAFT` with `[À COMPLÉTER]`
  placeholders rather than sending unverified information.
- MailHog is the local development SMTP sink. Sending a reminder records it as
  `SENT` only after the SMTP send call succeeds.
- RabbitMQ event delivery is best effort after database commit. The current
  implementation does not use an outbox or retry queue; broker failure is
  logged without failing the committed business operation.
- The legacy `business_profiles` table is no longer queried by application
  code. V1 copied its member and employee profile identifiers into the
  module-owned tables, so V7 removes the obsolete table without changing the
  existing migrations.
- The development profile enables Flyway out-of-order execution only to let
  persistent local volumes that already applied the V100 seed receive later
  V4-V13 migrations. Membership migrations retain V1-V7; nutrition and
  feedback migrations use V8-V13 to keep the merged history unique.
  Production does not enable out-of-order migrations.
- A before-each-migrate SQL callback normalizes the legacy coach-assignment
  unique constraint name for databases created before Flyway history existed.
  It only changes the legacy constraint while `ended_at` is absent; the V3
  assignment-history migration replaces it with the active-assignment index.
- The OpenAPI JSON document is intentionally public for API discovery;
  business endpoints remain protected by bearer JWT validation and
  authorization.
