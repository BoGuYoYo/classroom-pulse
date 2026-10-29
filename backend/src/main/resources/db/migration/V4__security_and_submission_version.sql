-- Token invalidation after password changes and submission optimistic versioning.
ALTER TABLE users
    ADD COLUMN auth_version INT NOT NULL DEFAULT 0;

ALTER TABLE submissions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
