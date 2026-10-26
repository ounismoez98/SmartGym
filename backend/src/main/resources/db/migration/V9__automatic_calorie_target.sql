ALTER TABLE public.nutrition_profiles
    ADD COLUMN IF NOT EXISTS calorie_target_mode character varying(16) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS energy_equation_sex character varying(16);
