ALTER TABLE payment
    ALTER COLUMN subscription_id DROP NOT NULL;

ALTER TABLE payment
    ADD COLUMN member_id UUID;

UPDATE payment payment_record
SET member_id = subscription.member_id
FROM subscription
WHERE payment_record.subscription_id = subscription.id;

ALTER TABLE payment
    ALTER COLUMN member_id SET NOT NULL;

ALTER TABLE payment
    ADD CONSTRAINT fk_payment_member
        FOREIGN KEY (member_id) REFERENCES member (id);

ALTER TABLE payment
    ADD COLUMN paid_at TIMESTAMPTZ;

UPDATE payment
SET paid_at = created_at;

ALTER TABLE payment
    ALTER COLUMN paid_at SET NOT NULL;

ALTER TABLE payment
    ALTER COLUMN paid_at SET DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX ix_payment_member_id ON payment (member_id);
CREATE INDEX ix_payment_paid_at ON payment (paid_at);
