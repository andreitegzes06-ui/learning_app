CREATE TABLE classroom (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 100),
    description TEXT NOT NULL CHECK(length(description)<=2000)
);
CREATE TABLE study_resource (
    id TEXT PRIMARY KEY NOT NULL,
    classroom_id TEXT NOT NULL REFERENCES classroom(id) ON DELETE CASCADE,
    title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 100),
    uri TEXT NOT NULL CHECK(length(uri) BETWEEN 1 AND 2048),
    position INTEGER NOT NULL CHECK(position>=0),
    UNIQUE(id,classroom_id)
);
CREATE TABLE blocked_application (
    path_key TEXT PRIMARY KEY NOT NULL,
    executable_path TEXT NOT NULL,
    display_name TEXT NOT NULL CHECK(length(trim(display_name)) BETWEEN 1 AND 100)
);
CREATE TABLE focus_workspace (
    id TEXT PRIMARY KEY NOT NULL,
    classroom_id TEXT NOT NULL REFERENCES classroom(id) ON DELETE RESTRICT,
    name TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 100),
    focus_minutes INTEGER NOT NULL CHECK(focus_minutes BETWEEN 1 AND 240),
    short_break INTEGER NOT NULL CHECK(short_break BETWEEN 1 AND 60),
    long_break INTEGER NOT NULL CHECK(long_break BETWEEN 1 AND 60),
    long_break_every INTEGER NOT NULL CHECK(long_break_every BETWEEN 1 AND 12),
    UNIQUE(id,classroom_id)
);
CREATE TABLE workspace_resource (
    workspace_id TEXT NOT NULL,
    resource_id TEXT NOT NULL,
    classroom_id TEXT NOT NULL,
    PRIMARY KEY(workspace_id,resource_id),
    FOREIGN KEY(workspace_id,classroom_id) REFERENCES focus_workspace(id,classroom_id) ON DELETE CASCADE,
    FOREIGN KEY(resource_id,classroom_id) REFERENCES study_resource(id,classroom_id) ON DELETE RESTRICT
);
CREATE TABLE workspace_application (
    workspace_id TEXT NOT NULL REFERENCES focus_workspace(id) ON DELETE CASCADE,
    path_key TEXT NOT NULL REFERENCES blocked_application(path_key) ON DELETE RESTRICT,
    PRIMARY KEY(workspace_id,path_key)
);
CREATE TABLE study_goal (
    id TEXT PRIMARY KEY NOT NULL,
    profile_id TEXT NOT NULL REFERENCES user_profile(id) ON DELETE RESTRICT,
    text TEXT NOT NULL CHECK(length(trim(text)) BETWEEN 1 AND 200),
    target_minutes INTEGER NOT NULL CHECK(target_minutes BETWEEN 1 AND 10080),
    period TEXT NOT NULL CHECK(period IN ('DAILY','WEEKLY')),
    UNIQUE(id,profile_id)
);
CREATE TABLE study_task (
    id TEXT PRIMARY KEY NOT NULL,
    profile_id TEXT NOT NULL REFERENCES user_profile(id) ON DELETE RESTRICT,
    text TEXT NOT NULL CHECK(length(trim(text)) BETWEEN 1 AND 200),
    classroom_id TEXT REFERENCES classroom(id) ON DELETE SET NULL,
    goal_id TEXT REFERENCES study_goal(id) ON DELETE SET NULL,
    due_date TEXT,
    completed_at TEXT
);
CREATE TABLE achievement (
    code TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 100),
    description TEXT NOT NULL CHECK(length(trim(description)) BETWEEN 1 AND 2000)
);
CREATE TABLE achievement_award (
    profile_id TEXT NOT NULL REFERENCES user_profile(id) ON DELETE RESTRICT,
    achievement_code TEXT NOT NULL REFERENCES achievement(code) ON DELETE RESTRICT,
    awarded_at TEXT NOT NULL,
    PRIMARY KEY(profile_id,achievement_code)
);
CREATE INDEX task_classroom ON study_task(classroom_id);
CREATE INDEX task_goal ON study_task(goal_id);
CREATE INDEX resource_classroom ON study_resource(classroom_id);
