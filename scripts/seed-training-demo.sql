\set ON_ERROR_STOP on

INSERT INTO exercises (
    id, name, description, instructions, primary_muscle_group, difficulty,
    equipment_category, active, created_at, updated_at, version
) VALUES
    ('10000000-0000-4000-8000-000000000001', 'Bodyweight squat',
     'A bodyweight lower-body movement.',
     'Stand with feet about shoulder-width apart. Sit back under control, then stand tall.',
     'QUADRICEPS', 'BEGINNER', 'BODYWEIGHT', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('10000000-0000-4000-8000-000000000002', 'Push-up',
     'A bodyweight horizontal press.',
     'Keep a straight body line, lower your chest under control, and press back up.',
     'CHEST', 'BEGINNER', 'BODYWEIGHT', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('10000000-0000-4000-8000-000000000003', 'Dumbbell row',
     'A dumbbell pulling exercise.',
     'Brace your torso and pull the dumbbell toward your hip without rotating your trunk.',
     'BACK', 'BEGINNER', 'DUMBBELL', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('10000000-0000-4000-8000-000000000004', 'Front plank',
     'A timed bodyweight core hold.',
     'Support your body on your forearms, brace gently, and keep a straight body line.',
     'CORE', 'BEGINNER', 'BODYWEIGHT', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    instructions = EXCLUDED.instructions,
    primary_muscle_group = EXCLUDED.primary_muscle_group,
    difficulty = EXCLUDED.difficulty,
    equipment_category = EXCLUDED.equipment_category,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO training_programmes (
    id, member_id, coach_id, title, goal, experience_level, duration_weeks,
    status, origin, revision, approved_revision, source_ai_job_id,
    supersedes_programme_id, created_by_subject, created_at, updated_at, version
) VALUES
    ('20000000-0000-4000-8000-000000000001', :'member_a_id'::uuid, :'coach_id'::uuid,
     'Foundations: Full Body', 'Build a consistent full-body training routine',
     'BEGINNER', 4, 'PUBLISHED', 'MANUAL', 1, 1, NULL, NULL,
     :'coach_subject', CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '2 days', 0),
    ('20000000-0000-4000-8000-000000000002', :'member_a_id'::uuid, :'coach_id'::uuid,
     'Strength Progression', 'Practice foundational strength movements',
     'BEGINNER', 6, 'PENDING_REVIEW', 'MANUAL', 1, NULL, NULL, NULL,
     :'coach_subject', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '1 day', 0),
    ('20000000-0000-4000-8000-000000000003', :'member_a_id'::uuid, :'coach_id'::uuid,
     'Mobility and Core Draft', 'Build a simple core and movement routine',
     'BEGINNER', 3, 'DRAFT', 'MANUAL', 1, NULL, NULL, NULL,
     :'coach_subject', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('20000000-0000-4000-8000-000000000004', :'member_b_id'::uuid, :'coach_id'::uuid,
     'Member B: Starter Plan', 'Learn basic full-body movements',
     'BEGINNER', 4, 'PUBLISHED', 'MANUAL', 1, 1, NULL, NULL,
     :'coach_subject', CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '1 day', 0)
ON CONFLICT (id) DO UPDATE SET
    member_id = EXCLUDED.member_id,
    coach_id = EXCLUDED.coach_id,
    title = EXCLUDED.title,
    goal = EXCLUDED.goal,
    experience_level = EXCLUDED.experience_level,
    duration_weeks = EXCLUDED.duration_weeks,
    status = EXCLUDED.status,
    origin = EXCLUDED.origin,
    revision = EXCLUDED.revision,
    approved_revision = EXCLUDED.approved_revision,
    supersedes_programme_id = EXCLUDED.supersedes_programme_id,
    created_by_subject = EXCLUDED.created_by_subject,
    updated_at = EXCLUDED.updated_at;

INSERT INTO programme_sessions (id, programme_id, title, day_order, notes) VALUES
    ('30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', 'Workout A', 1, 'Rest between sets as needed.'),
    ('30000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001', 'Workout B', 2, 'Move with control.'),
    ('30000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000002', 'Strength Day', 1, 'Awaiting coach review.'),
    ('30000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000003', 'Core Draft', 1, 'Draft preview for coach.'),
    ('30000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000004', 'Starter Workout', 1, 'Member B plan.')
ON CONFLICT (id) DO UPDATE SET
    programme_id = EXCLUDED.programme_id,
    title = EXCLUDED.title,
    day_order = EXCLUDED.day_order,
    notes = EXCLUDED.notes;

INSERT INTO programme_items (
    id, session_id, exercise_id, order_index, sets, repetitions, duration_seconds,
    rest_seconds, suggested_load_kg, notes
) VALUES
    ('40000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 1, 3, 10, NULL, 60, NULL, 'Bodyweight'),
    ('40000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', 2, 3, 8, NULL, 60, NULL, NULL),
    ('40000000-0000-4000-8000-000000000003', '30000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000003', 1, 3, 10, NULL, 75, 8.00, 'Per hand'),
    ('40000000-0000-4000-8000-000000000004', '30000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000004', 2, 3, NULL, 30, 45, NULL, 'Timed hold'),
    ('40000000-0000-4000-8000-000000000005', '30000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 1, 4, 8, NULL, 90, NULL, NULL),
    ('40000000-0000-4000-8000-000000000006', '30000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000003', 2, 3, 8, NULL, 90, 10.00, NULL),
    ('40000000-0000-4000-8000-000000000007', '30000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000004', 1, 3, NULL, 30, 45, NULL, 'Timed hold'),
    ('40000000-0000-4000-8000-000000000008', '30000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000002', 1, 3, 8, NULL, 60, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    session_id = EXCLUDED.session_id,
    exercise_id = EXCLUDED.exercise_id,
    order_index = EXCLUDED.order_index,
    sets = EXCLUDED.sets,
    repetitions = EXCLUDED.repetitions,
    duration_seconds = EXCLUDED.duration_seconds,
    rest_seconds = EXCLUDED.rest_seconds,
    suggested_load_kg = EXCLUDED.suggested_load_kg,
    notes = EXCLUDED.notes;

INSERT INTO coach_reviews (
    id, programme_id, reviewed_revision, coach_id, decision, comment, reviewed_at
) VALUES
    ('50000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
     1, :'coach_id'::uuid, 'APPROVED', 'Demo review: ready for the member.', CURRENT_TIMESTAMP - INTERVAL '2 days')
ON CONFLICT (id) DO UPDATE SET
    programme_id = EXCLUDED.programme_id,
    reviewed_revision = EXCLUDED.reviewed_revision,
    coach_id = EXCLUDED.coach_id,
    decision = EXCLUDED.decision,
    comment = EXCLUDED.comment,
    reviewed_at = EXCLUDED.reviewed_at;

INSERT INTO workout_logs (
    id, member_id, programme_id, programme_session_id, performed_at, notes,
    created_at, updated_at, version
) VALUES
    ('60000000-0000-4000-8000-000000000001', :'member_a_id'::uuid,
     '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000001',
     CURRENT_TIMESTAMP - INTERVAL '10 days', 'Demo workout: first session.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('60000000-0000-4000-8000-000000000002', :'member_a_id'::uuid,
     '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000002',
     CURRENT_TIMESTAMP - INTERVAL '6 days', 'Demo workout: steady progress.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('60000000-0000-4000-8000-000000000003', :'member_a_id'::uuid,
     NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '2 days', 'Demo standalone workout.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO UPDATE SET
    member_id = EXCLUDED.member_id,
    programme_id = EXCLUDED.programme_id,
    programme_session_id = EXCLUDED.programme_session_id,
    performed_at = EXCLUDED.performed_at,
    notes = EXCLUDED.notes,
    updated_at = EXCLUDED.updated_at;

INSERT INTO workout_sets (
    id, workout_log_id, exercise_id, set_number, repetitions, duration_seconds, load_kg, notes
) VALUES
    ('70000000-0000-4000-8000-000000000001', '60000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 1, 10, NULL, NULL, NULL),
    ('70000000-0000-4000-8000-000000000002', '60000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 2, 10, NULL, NULL, NULL),
    ('70000000-0000-4000-8000-000000000003', '60000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', 3, 8, NULL, NULL, NULL),
    ('70000000-0000-4000-8000-000000000004', '60000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000003', 1, 10, NULL, 8.00, 'Per hand'),
    ('70000000-0000-4000-8000-000000000005', '60000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000004', 2, NULL, 30, NULL, 'Timed hold'),
    ('70000000-0000-4000-8000-000000000006', '60000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000003', 1, 12, NULL, 10.00, NULL)
ON CONFLICT (id) DO UPDATE SET
    workout_log_id = EXCLUDED.workout_log_id,
    exercise_id = EXCLUDED.exercise_id,
    set_number = EXCLUDED.set_number,
    repetitions = EXCLUDED.repetitions,
    duration_seconds = EXCLUDED.duration_seconds,
    load_kg = EXCLUDED.load_kg,
    notes = EXCLUDED.notes;
