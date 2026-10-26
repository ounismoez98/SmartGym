CREATE TABLE courses (
    course_id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(2000),
    difficulty_level VARCHAR(30) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    required_equipment VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE course_sessions (
    session_id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses (course_id),
    session_date DATE NOT NULL,
    start_time TIME WITHOUT TIME ZONE NOT NULL,
    end_time TIME WITHOUT TIME ZONE NOT NULL,
    coach_subject VARCHAR(64) NOT NULL,
    room_name VARCHAR(100) NOT NULL,
    max_capacity INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_course_sessions_course_date_time_room
        UNIQUE (course_id, session_date, start_time, room_name)
);

CREATE INDEX ix_course_sessions_date_start
    ON course_sessions (session_date, start_time);
CREATE INDEX ix_course_sessions_coach_date
    ON course_sessions (coach_subject, session_date);
CREATE INDEX ix_course_sessions_room_date
    ON course_sessions (room_name, session_date);

CREATE TABLE reservations (
    reservation_id UUID PRIMARY KEY,
    member_subject VARCHAR(64) NOT NULL,
    session_id UUID NOT NULL REFERENCES course_sessions (session_id),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_reservations_member_session UNIQUE (member_subject, session_id)
);

CREATE INDEX ix_reservations_session_created
    ON reservations (session_id, created_at DESC);
CREATE INDEX ix_reservations_member_created
    ON reservations (member_subject, created_at DESC);

CREATE TABLE equipment (
    equipment_id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    equipment_type VARCHAR(60) NOT NULL,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    maintenance_required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE maintenance_tickets (
    ticket_id UUID PRIMARY KEY,
    equipment_id UUID NOT NULL REFERENCES equipment (equipment_id),
    issue VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reported_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ
);

CREATE INDEX ix_maintenance_tickets_equipment
    ON maintenance_tickets (equipment_id);
