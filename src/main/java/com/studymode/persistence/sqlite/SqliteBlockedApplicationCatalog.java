package com.studymode.persistence.sqlite;

import com.studymode.application.port.BlockedApplicationCatalog;
import com.studymode.domain.workspace.BlockedApplication;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import static com.studymode.persistence.sqlite.SqliteStatements.*;

public final class SqliteBlockedApplicationCatalog implements BlockedApplicationCatalog {
    private final SqliteDatabase database;
    public SqliteBlockedApplicationCatalog(SqliteDatabase database) { this.database = database; }
    public static String key(Path path) { return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT); }
    @Override public List<BlockedApplication> findAll() { return database.transaction("load application selections", SqliteBlockedApplicationCatalog::readAll); }
    @Override public Optional<BlockedApplication> find(Path path) { return findAll().stream().filter(app -> key(app.executablePath()).equals(key(path))).findFirst(); }
    @Override public void save(BlockedApplication app) { database.transaction("save application selection", c -> { write(c, app); return null; }); }
    @Override public boolean delete(Path path) {
        return database.transaction("delete application selection (remove it from workspaces first)", c -> update(c, "DELETE FROM blocked_application WHERE path_key=?", key(path)) == 1);
    }
    static List<BlockedApplication> readAll(Connection c) throws SQLException {
        return query(c, "SELECT * FROM blocked_application ORDER BY path_key", row -> new BlockedApplication(row.getString("display_name"), Path.of(row.getString("executable_path")),row.getInt("enabled")==1));
    }
    static void write(Connection c, BlockedApplication app) throws SQLException {
        update(c, """
                INSERT INTO blocked_application(path_key,executable_path,display_name,enabled) VALUES (?,?,?,?) ON CONFLICT(path_key)
                DO UPDATE SET executable_path=excluded.executable_path,display_name=excluded.display_name,enabled=excluded.enabled
                """, key(app.executablePath()), app.executablePath(), app.displayName(),app.enabled()?1:0);
    }
}
