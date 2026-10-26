ALTER TABLE training_programmes
    ADD COLUMN supersedes_programme_id UUID REFERENCES training_programmes(id);

CREATE INDEX training_programmes_supersedes_idx
    ON training_programmes (supersedes_programme_id);
