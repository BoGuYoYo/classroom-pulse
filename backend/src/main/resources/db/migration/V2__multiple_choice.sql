-- Add multiple-choice questions while keeping existing single/fill questions valid.
ALTER TABLE questions DROP CHECK chk_questions_type;
ALTER TABLE questions
    ADD CONSTRAINT chk_questions_type
    CHECK (type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'FILL_BLANK'));
