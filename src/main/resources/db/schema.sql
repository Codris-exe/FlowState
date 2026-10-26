-- FlowState PostgreSQL Relational Schema
-- Version: 1.0.0

-- Table: focus_sessions
CREATE TABLE IF NOT EXISTS focus_sessions (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    target_duration_seconds BIGINT NOT NULL DEFAULT 1500,
    actual_duration_seconds BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    interruption_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_focus_sessions_start_time ON focus_sessions (start_time);
CREATE INDEX IF NOT EXISTS idx_focus_sessions_status ON focus_sessions (status);

-- Table: application_activity
CREATE TABLE IF NOT EXISTS application_activity (
    id BIGSERIAL PRIMARY KEY,
    application_name VARCHAR(255) NOT NULL,
    window_title VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    duration_seconds BIGINT NOT NULL DEFAULT 0,
    session_id BIGINT REFERENCES focus_sessions(id) ON DELETE SET NULL,
    activity_category VARCHAR(50) NOT NULL DEFAULT 'NEUTRAL'
);

CREATE INDEX IF NOT EXISTS idx_app_activity_session_id ON application_activity (session_id);
CREATE INDEX IF NOT EXISTS idx_app_activity_started_at ON application_activity (started_at);

-- Table: application_preferences
CREATE TABLE IF NOT EXISTS application_preferences (
    id BIGSERIAL PRIMARY KEY,
    application_identifier VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'NEUTRAL',
    is_excluded BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_app_pref_identifier ON application_preferences (application_identifier);

-- Table: user_settings
CREATE TABLE IF NOT EXISTS user_settings (
    id BIGSERIAL PRIMARY KEY,
    daily_focus_goal_minutes INT NOT NULL DEFAULT 120,
    theme VARCHAR(50) NOT NULL DEFAULT 'DARK',
    tracking_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed default user settings if none exist
INSERT INTO user_settings (id, daily_focus_goal_minutes, theme, tracking_enabled)
SELECT 1, 120, 'DARK', TRUE
WHERE NOT EXISTS (SELECT 1 FROM user_settings WHERE id = 1);
