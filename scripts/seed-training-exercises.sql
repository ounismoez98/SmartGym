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
     'BACK', 'BEGINNER', 'DUMBBELL', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO NOTHING;
