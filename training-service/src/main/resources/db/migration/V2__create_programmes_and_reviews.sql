CREATE TABLE training_programmes (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL,
    coach_id UUID,
    title VARCHAR(120) NOT NULL CHECK (length(trim(title)) > 0),
    goal VARCHAR(500) NOT NULL CHECK (length(trim(goal)) > 0),
    experience_level VARCHAR(24) NOT NULL CHECK (experience_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    duration_weeks INTEGER NOT NULL CHECK (duration_weeks BETWEEN 1 AND 52),
    status VARCHAR(24) NOT NULL CHECK (status IN (
        'DRAFT', 'PENDING_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'PUBLISHED', 'ARCHIVED'
    )),
    origin VARCHAR(16) NOT NULL CHECK (origin IN ('MANUAL', 'AI')),
    revision INTEGER NOT NULL CHECK (revision > 0),
    approved_revision INTEGER,
    source_ai_job_id UUID,
    created_by_subject VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE programme_sessions (
    id UUID PRIMARY KEY,
    programme_id UUID NOT NULL REFERENCES training_programmes(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL CHECK (length(trim(title)) > 0),
    day_order INTEGER NOT NULL CHECK (day_order > 0),
    notes VARCHAR(1000),
    CONSTRAINT programme_session_order_unique UNIQUE (programme_id, day_order)
);

CREATE TABLE programme_items (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES programme_sessions(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES exercises(id),
    order_index INTEGER NOT NULL CHECK (order_index > 0),
    sets INTEGER NOT NULL CHECK (sets BETWEEN 1 AND 20),
    repetitions INTEGER,
    duration_seconds INTEGER,
    rest_seconds INTEGER NOT NULL CHECK (rest_seconds BETWEEN 0 AND 3600),
    suggested_load_kg NUMERIC(7, 2) CHECK (suggested_load_kg > 0),
    notes VARCHAR(500),
    CONSTRAINT programme_item_prescription CHECK (
        (repetitions IS NOT NULL AND repetitions BETWEEN 1 AND 100 AND duration_seconds IS NULL)
        OR (repetitions IS NULL AND duration_seconds IS NOT NULL AND duration_seconds BETWEEN 1 AND 7200)
    ),
    CONSTRAINT programme_item_order_unique UNIQUE (session_id, order_index)
);

CREATE TABLE coach_reviews (
    id UUID PRIMARY KEY,
    programme_id UUID NOT NULL REFERENCES training_programmes(id) ON DELETE CASCADE,
    reviewed_revision INTEGER NOT NULL CHECK (reviewed_revision > 0),
    coach_id UUID NOT NULL,
    decision VARCHAR(24) NOT NULL CHECK (decision IN ('APPROVED', 'CHANGES_REQUESTED')),
    comment VARCHAR(2000),
    reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX training_programmes_member_status_idx ON training_programmes (member_id, status, updated_at DESC);
CREATE INDEX training_programmes_coach_idx ON training_programmes (coach_id, updated_at DESC);
CREATE INDEX programme_sessions_order_idx ON programme_sessions (programme_id, day_order);
CREATE INDEX programme_items_exercise_idx ON programme_items (exercise_id);
CREATE INDEX coach_reviews_programme_revision_idx ON coach_reviews (programme_id, reviewed_revision, reviewed_at DESC);
