ALTER TABLE public.nutrition_profiles
    ADD COLUMN calorie_target_mode character varying(16) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN energy_equation_sex character varying(16);
