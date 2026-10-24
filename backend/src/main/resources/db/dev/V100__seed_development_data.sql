INSERT INTO subscription_plan (
    id, code, name, description, duration_days, price_cents, currency
) VALUES
    ('10000000-0000-0000-0000-000000000001', 'MONTHLY', 'Monthly', 'Development sample monthly plan', 30, 5000, 'EUR'),
    ('10000000-0000-0000-0000-000000000002', 'ANNUAL', 'Annual', 'Development sample annual plan', 365, 50000, 'EUR')
ON CONFLICT (code) DO NOTHING;

INSERT INTO gym_policy (id, policy_key, title, content, validated) VALUES
    ('20000000-0000-0000-0000-000000000001', 'DEV_CANCELLATION', 'Development cancellation policy', 'Development sample only: cancellation requires an administrator review.', FALSE)
ON CONFLICT (policy_key) DO NOTHING;
