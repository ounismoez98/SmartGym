CREATE UNIQUE INDEX uq_subscription_one_active_per_member
    ON subscription (member_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_subscription_plan_code_ci
    ON subscription_plan (LOWER(code));
