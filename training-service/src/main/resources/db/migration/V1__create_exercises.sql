CREATE TABLE exercises (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1200),
    instructions VARCHAR(3000) NOT NULL,
    primary_muscle_group VARCHAR(32) NOT NULL CHECK (primary_muscle_group IN (
        'CHEST', 'BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'CORE', 'QUADRICEPS',
        'HAMSTRINGS', 'GLUTES', 'CALVES', 'FULL_BODY', 'OTHER'
    )),
    difficulty VARCHAR(24) NOT NULL CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    equipment_category VARCHAR(32) NOT NULL CHECK (equipment_category IN (
        'BODYWEIGHT', 'DUMBBELL', 'BARBELL', 'KETTLEBELL', 'CABLE', 'MACHINE',
        'RESISTANCE_BAND', 'OTHER'
    )),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT exercises_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT exercises_instructions_not_blank CHECK (length(trim(instructions)) > 0)
);

CREATE INDEX exercises_active_name_idx ON exercises (active, name);
CREATE INDEX exercises_muscle_group_idx ON exercises (primary_muscle_group);
CREATE INDEX exercises_difficulty_idx ON exercises (difficulty);
CREATE INDEX exercises_equipment_category_idx ON exercises (equipment_category);
