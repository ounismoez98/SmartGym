CREATE TABLE public.feedbacks (
    id uuid NOT NULL,
    keycloak_subject character varying(64) NOT NULL,
    member_name character varying(200) NOT NULL,
    rating integer NOT NULL,
    category character varying(30) NOT NULL,
    comment character varying(4000) NOT NULL,
    sentiment character varying(20) NOT NULL,
    summary character varying(2000),
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT feedbacks_pkey PRIMARY KEY (id)
);

CREATE TABLE public.feedback_themes (
    feedback_id uuid NOT NULL,
    theme character varying(100),
    CONSTRAINT fk_feedback_themes_feedback
        FOREIGN KEY (feedback_id) REFERENCES public.feedbacks (id)
);
