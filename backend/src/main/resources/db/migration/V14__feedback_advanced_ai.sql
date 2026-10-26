ALTER TABLE public.feedbacks
    ADD COLUMN IF NOT EXISTS urgency character varying(20) DEFAULT 'LOW' NOT NULL,
    ADD COLUMN IF NOT EXISTS action_recommendation character varying(2000),
    ADD COLUMN IF NOT EXISTS confidence_score double precision DEFAULT 0.95;
