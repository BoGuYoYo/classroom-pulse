-- Admin console, optimistic versions for admin edits, and server-side sessions.
ALTER TABLE users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE courses
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE admin_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(40) NOT NULL,
    name VARCHAR(60) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64) NOT NULL,
    csrf_token VARCHAR(64) NOT NULL,
    admin_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_sessions_token (token_hash),
    KEY idx_admin_sessions_expiry (expires_at),
    CONSTRAINT fk_admin_sessions_admin FOREIGN KEY (admin_id) REFERENCES admin_users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
