INSERT INTO gym_policy (id, policy_key, title, content, validated) VALUES
    (
        '30000000-0000-0000-0000-000000000001',
        'GYM_HOURS_AND_HOLIDAYS',
        'Opening hours and holiday closures',
        'The gym is open Monday through Friday from 06:00 to 22:00. On Saturday and Sunday, it is open from 06:00 to 14:00 and closes at 14:00. The gym is closed on Tunisian religious public holidays officially observed by the Tunisian authorities. Religious holiday dates can vary by year and are subject to official announcement; do not infer or promise a specific holiday opening. Check the gym''s current notice for any exceptional schedule.',
        TRUE
    ),
    (
        '30000000-0000-0000-0000-000000000002',
        'GYM_CONDUCT',
        'Gym conduct and facility policy',
        'Members and visitors must follow staff instructions, use equipment safely and for its intended purpose, and return equipment after use. Respect other people''s privacy and personal space, and keep shared areas clean. Do not harass, threaten, or discriminate against others, damage equipment, or use facilities while access is suspended. Report hazards, injuries, or damaged equipment to staff. Staff may ask a person to stop unsafe or disruptive conduct and may refer repeated or serious incidents to gym management. This policy does not replace emergency procedures or applicable law.',
        TRUE
    ),
    (
        '30000000-0000-0000-0000-000000000003',
        'SUBSCRIPTION_RENEWAL',
        'Subscription renewal policy',
        'Members should contact gym administration before their subscription expires to request renewal. Renewal is subject to the active subscription plans and prices shown by the gym at the time of renewal; the member must confirm the selected plan and price with administration. Administration confirms the renewal and its effective dates. Ask administration whether any paperwork is required; no specific document or automatic renewal is assumed by this policy.',
        TRUE
    ),
    (
        '30000000-0000-0000-0000-000000000004',
        'LATE_PAYMENT',
        'Late subscription payment policy',
        'If a subscription payment is overdue, the member should contact gym administration promptly to confirm the outstanding amount and arrange payment. Any amount due must be confirmed against the member''s active plan and account; this policy does not create a late fee, interest, or penalty. Administration will communicate any effect on facility access and any applicable grace period before taking action, in accordance with the member''s agreement and applicable law. Do not assume automatic cancellation or suspension.',
        TRUE
    )
ON CONFLICT (policy_key) DO NOTHING;
