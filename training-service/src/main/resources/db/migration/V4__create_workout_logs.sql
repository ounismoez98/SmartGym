CREATE TABLE workout_logs (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL,
    programme_id UUID REFERENCES training_programmes(id),
    programme_session_id UUID REFERENCES programme_sessions(id),
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    notes VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT workout_log_session_requires_programme CHECK (
        programme_session_id IS NULL OR programme_id IS NOT NULL
    )
);

CREATE TABLE workout_sets (
    id UUID PRIMARY KEY,
    workout_log_id UUID NOT NULL REFERENCES workout_logs(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES exercises(id),
    set_number INTEGER NOT NULL CHECK (set_number > 0),
    repetitions INTEGER,
    duration_seconds INTEGER,
    load_kg NUMERIC(7, 2) CHECK (load_kg > 0),
    notes VARCHAR(500),
    CONSTRAINT workout_set_prescription CHECK (
        (repetitions IS NOT NULL AND repetitions BETWEEN 1 AND 100 AND duration_seconds IS NULL)
        OR (repetitions IS NULL AND duration_seconds IS NOT NULL AND duration_seconds BETWEEN 1 AND 7200)
    ),
    CONSTRAINT workout_set_number_unique UNIQUE (workout_log_id, set_number)
);

CREATE INDEX workout_logs_member_performed_idx ON workout_logs (member_id, performed_at DESC);
CREATE INDEX workout_logs_programme_idx ON workout_logs (programme_id);
CREATE INDEX workout_sets_exercise_idx ON workout_sets (exercise_id);
