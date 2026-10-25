ALTER TABLE reminder_draft
    DROP CONSTRAINT reminder_draft_status_check;

UPDATE reminder_draft
SET status = 'SENT'
WHERE status = 'CONFIRMED';

ALTER TABLE reminder_draft
    ADD CONSTRAINT reminder_draft_status_check
        CHECK (status IN ('DRAFT', 'SENT', 'REJECTED'));

ALTER TABLE reminder_draft
    ADD COLUMN sent_at TIMESTAMPTZ;
