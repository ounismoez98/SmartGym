CREATE TABLE public.nutrition_meal_plan_generation_contexts (
    id uuid NOT NULL,
    subject character varying(64) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    consumed boolean NOT NULL DEFAULT false,
    CONSTRAINT nutrition_meal_plan_generation_contexts_pkey PRIMARY KEY (id)
);

CREATE TABLE public.nutrition_meal_plan_generation_restrictions (
    generation_context_id uuid NOT NULL,
    restriction character varying(100) NOT NULL,
    CONSTRAINT nutrition_meal_plan_generation_restrictions_pkey
        PRIMARY KEY (generation_context_id, restriction),
    CONSTRAINT fk_nutrition_generation_restrictions_context
        FOREIGN KEY (generation_context_id)
        REFERENCES public.nutrition_meal_plan_generation_contexts (id)
        ON DELETE CASCADE
);

ALTER TABLE public.nutrition_meal_plans
    ADD COLUMN generation_context_id uuid;

ALTER TABLE public.nutrition_meal_plans
    ADD CONSTRAINT fk_nutrition_meal_plan_generation_context
        FOREIGN KEY (generation_context_id)
        REFERENCES public.nutrition_meal_plan_generation_contexts (id);

CREATE INDEX nutrition_meal_plan_generation_contexts_subject_idx
    ON public.nutrition_meal_plan_generation_contexts (subject);
