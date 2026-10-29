-- Course lifecycle, soft-deleted questions, and assignment grading.
ALTER TABLE courses
    ADD COLUMN archived_at DATETIME(6) NULL,
    ADD COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE questions
    ADD COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE submissions
    ADD COLUMN score INT NULL,
    ADD COLUMN feedback TEXT NULL,
    ADD COLUMN graded_at DATETIME(6) NULL,
    ADD CONSTRAINT chk_submissions_score CHECK (score IS NULL OR score BETWEEN 0 AND 100);
