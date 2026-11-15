ALTER TABLE classroom ADD COLUMN color TEXT NOT NULL DEFAULT '#7569EF' CHECK(length(color)=7 AND color GLOB '#[0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]');
ALTER TABLE classroom ADD COLUMN notes TEXT NOT NULL DEFAULT '' CHECK(length(notes)<=2000);
ALTER TABLE study_goal ADD COLUMN classroom_id TEXT REFERENCES classroom(id) ON DELETE RESTRICT;
CREATE TABLE workspace_shortcut (
    workspace_id TEXT NOT NULL REFERENCES focus_workspace(id) ON DELETE CASCADE,
    path_key TEXT NOT NULL,
    executable_path TEXT NOT NULL,
    display_name TEXT NOT NULL CHECK(length(trim(display_name)) BETWEEN 1 AND 100),
    PRIMARY KEY(workspace_id,path_key)
);
CREATE INDEX goal_classroom ON study_goal(classroom_id);
