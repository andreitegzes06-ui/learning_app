CREATE TABLE schema_version (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL);
CREATE TABLE user_profile (
    slot INTEGER PRIMARY KEY CHECK (slot = 1),
    id TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL CHECK (length(trim(display_name)) BETWEEN 1 AND 60),
    created_at TEXT NOT NULL,
    zone_id TEXT NOT NULL
);
CREATE TABLE study_session (
    id TEXT PRIMARY KEY,
    profile_id TEXT NOT NULL REFERENCES user_profile(id),
    started_at TEXT NOT NULL,
    ended_at TEXT,
    active_seconds INTEGER NOT NULL CHECK (active_seconds >= 0),
    zone_id TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE','PAUSED','COMPLETED','INTERRUPTED')),
    CHECK ((status IN ('ACTIVE','PAUSED') AND ended_at IS NULL)
        OR (status IN ('COMPLETED','INTERRUPTED') AND ended_at IS NOT NULL))
);
CREATE UNIQUE INDEX one_unfinished_session ON study_session(profile_id) WHERE ended_at IS NULL;
CREATE INDEX session_start ON study_session(started_at);
