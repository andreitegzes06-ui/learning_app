package com.studymode.persistence.sqlite;

import com.studymode.application.port.ClassroomCatalog;
import com.studymode.application.error.StudyDataException;
import com.studymode.domain.classroom.*;
import java.net.URI;
import java.sql.*;
import java.util.*;
import static com.studymode.persistence.sqlite.SqliteStatements.*;

public final class SqliteClassroomCatalog implements ClassroomCatalog {
    private final SqliteDatabase database;
    public SqliteClassroomCatalog(SqliteDatabase database) { this.database = database; }
    @Override public List<Classroom> findAll() { return database.transaction("load classrooms", SqliteClassroomCatalog::readAll); }
    @Override public Optional<Classroom> find(UUID id) { return findAll().stream().filter(c -> c.id().equals(id)).findFirst(); }
    @Override public void save(Classroom classroom) { database.transaction("save classroom and resources", c -> { write(c, classroom); return null; }); }
    @Override public boolean delete(UUID id) {
        return database.transaction("delete classroom (check referencing workspaces and session history)", c -> update(c, "DELETE FROM classroom WHERE id=?", id) == 1);
    }
    static List<Classroom> readAll(Connection c) throws SQLException {
        return query(c, "SELECT * FROM classroom ORDER BY name,id", row -> {
            UUID id = UUID.fromString(row.getString("id"));
            var resources = query(c, "SELECT * FROM study_resource WHERE classroom_id=? ORDER BY position,id",
                    r -> new StudyResource(UUID.fromString(r.getString("id")), r.getString("title"), URI.create(r.getString("uri"))), id);
            return new Classroom(id, row.getString("name"), row.getString("description"), resources);
        });
    }
    static void write(Connection c, Classroom classroom) throws SQLException {
        update(c, "INSERT INTO classroom VALUES (?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,description=excluded.description",
                classroom.id(), classroom.name(), classroom.description());
        Set<UUID> retained = new HashSet<>();
        for (int i = 0; i < classroom.resources().size(); i++) {
            StudyResource resource = classroom.resources().get(i); retained.add(resource.id());
            int changed = update(c, """
                    INSERT INTO study_resource(id,classroom_id,title,uri,position) VALUES (?,?,?,?,?)
                    ON CONFLICT(id) DO UPDATE SET title=excluded.title,uri=excluded.uri,position=excluded.position
                    WHERE study_resource.classroom_id=excluded.classroom_id
                    """, resource.id(), classroom.id(), resource.title(), resource.uri(), i);
            if (changed != 1) throw new StudyDataException("A resource ID already belongs to another classroom.");
        }
        for (UUID id : query(c, "SELECT id FROM study_resource WHERE classroom_id=?", r -> UUID.fromString(r.getString(1)), classroom.id())) {
            if (!retained.contains(id)) update(c, "DELETE FROM study_resource WHERE id=?", id);
        }
    }
}
