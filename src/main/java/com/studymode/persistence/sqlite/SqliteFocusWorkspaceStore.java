package com.studymode.persistence.sqlite;

import com.studymode.application.port.FocusWorkspaceStore;
import com.studymode.domain.workspace.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import static com.studymode.persistence.sqlite.SqliteStatements.*;

public final class SqliteFocusWorkspaceStore implements FocusWorkspaceStore {
    private final SqliteDatabase database;
    public SqliteFocusWorkspaceStore(SqliteDatabase database) { this.database = database; }
    @Override public List<FocusWorkspace> findAll() { return database.transaction("load workspaces", SqliteFocusWorkspaceStore::readAll); }
    @Override public Optional<FocusWorkspace> find(UUID id) { return findAll().stream().filter(w -> w.id().equals(id)).findFirst(); }
    @Override public void save(FocusWorkspace workspace) { database.transaction("save workspace (check its classroom, resources and application selections)", c -> { write(c, workspace); return null; }); }
    @Override public boolean delete(UUID id) { return database.transaction("delete workspace", c -> update(c, "DELETE FROM focus_workspace WHERE id=?", id) == 1); }
    static List<FocusWorkspace> readAll(Connection c) throws SQLException {
        return query(c, "SELECT * FROM focus_workspace ORDER BY name,id", row -> {
            UUID id = UUID.fromString(row.getString("id"));
            var resources = query(c, "SELECT resource_id FROM workspace_resource WHERE workspace_id=?", r -> UUID.fromString(r.getString(1)), id);
            var apps = query(c, """
                    SELECT a.* FROM blocked_application a JOIN workspace_application w ON a.path_key=w.path_key
                    WHERE w.workspace_id=? ORDER BY a.path_key
                    """, r -> new BlockedApplication(r.getString("display_name"), Path.of(r.getString("executable_path")),r.getInt("enabled")==1), id);
            var shortcuts = query(c, "SELECT * FROM workspace_shortcut WHERE workspace_id=? ORDER BY path_key",
                    r -> new BlockedApplication(r.getString("display_name"), Path.of(r.getString("executable_path"))), id);
            return new FocusWorkspace(id, row.getString("name"), UUID.fromString(row.getString("classroom_id")),
                    new FocusPreferences(row.getInt("focus_minutes"), row.getInt("short_break"), row.getInt("long_break"), row.getInt("long_break_every")), Set.copyOf(resources), Set.copyOf(apps), Set.copyOf(shortcuts));
        });
    }
    static void write(Connection c, FocusWorkspace workspace) throws SQLException {
        if (!query(c, "SELECT id FROM focus_workspace WHERE lower(trim(name))=lower(trim(?)) AND id<>?", r -> r.getString(1), workspace.name(), workspace.id()).isEmpty())
            throw new com.studymode.application.error.StudyDataException("A workspace with this name already exists.");
        var classroom = SqliteClassroomCatalog.readAll(c).stream().filter(v -> v.id().equals(workspace.classroomId())).findFirst()
                .orElseThrow(() -> new com.studymode.application.error.StudyDataException("The workspace classroom no longer exists."));
        workspace.validateAgainst(classroom);
        update(c, "DELETE FROM workspace_resource WHERE workspace_id=?", workspace.id());
        update(c, "DELETE FROM workspace_application WHERE workspace_id=?", workspace.id());
        update(c, "DELETE FROM workspace_shortcut WHERE workspace_id=?", workspace.id());
        var p = workspace.preferences();
        update(c, """
                INSERT INTO focus_workspace VALUES (?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET
                classroom_id=excluded.classroom_id,name=excluded.name,focus_minutes=excluded.focus_minutes,
                short_break=excluded.short_break,long_break=excluded.long_break,long_break_every=excluded.long_break_every
                """, workspace.id(), workspace.classroomId(), workspace.name(), p.focusMinutes(), p.shortBreakMinutes(), p.longBreakMinutes(), p.longBreakEvery());
        for (UUID resource : workspace.resourceIds()) update(c, "INSERT INTO workspace_resource VALUES (?,?,?)", workspace.id(), resource, workspace.classroomId());
        for (BlockedApplication app : workspace.blocklist()) {
            // Never create/modify application selections implicitly; the catalog owns their metadata.
            update(c, "INSERT INTO workspace_application VALUES (?,?)", workspace.id(), SqliteBlockedApplicationCatalog.key(app.executablePath()));
        }
        for (BlockedApplication app : workspace.shortcuts()) update(c, "INSERT INTO workspace_shortcut VALUES (?,?,?,?)",
                workspace.id(), SqliteBlockedApplicationCatalog.key(app.executablePath()), app.executablePath(), app.displayName());
    }
}
