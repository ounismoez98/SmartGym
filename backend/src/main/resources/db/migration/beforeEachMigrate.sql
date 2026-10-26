DO $$
DECLARE
    unique_constraint RECORD;
BEGIN
    IF to_regclass('public.coach_assignments') IS NOT NULL
            AND NOT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'coach_assignments'
                  AND column_name = 'ended_at'
            ) THEN
        FOR unique_constraint IN
            SELECT conname
            FROM pg_constraint
            WHERE conrelid = 'public.coach_assignments'::regclass
              AND contype = 'u'
        LOOP
            EXECUTE format(
                'ALTER TABLE public.coach_assignments DROP CONSTRAINT %I',
                unique_constraint.conname
            );
        END LOOP;

        DROP INDEX IF EXISTS public.uq_coach_assignments_subjects;

        ALTER TABLE public.coach_assignments
            ADD CONSTRAINT uq_coach_assignments_subjects
            UNIQUE (coach_subject, member_subject);
    END IF;
END;
$$;
