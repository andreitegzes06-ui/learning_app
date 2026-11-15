package com.studymode.persistence.sqlite;

import com.studymode.application.port.AchievementStore;
import com.studymode.domain.reward.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static com.studymode.persistence.sqlite.SqliteStatements.*;

/** Persists definitions and awards only. No XP calculation or automatic award engine. */
public final class SqliteAchievementStore implements AchievementStore {
    private final SqliteDatabase database;
    public SqliteAchievementStore(SqliteDatabase database) { this.database = database; }
    @Override public List<Achievement> definitions() { return database.transaction("load achievement definitions", SqliteAchievementStore::readDefinitions); }
    @Override public List<AchievementAward> awards() { return database.transaction("load saved achievement awards", SqliteAchievementStore::readAwards); }
    @Override public void save(Achievement a) { database.transaction("save achievement definition", c -> { write(c, a); return null; }); }
    @Override public void save(AchievementAward a) { database.transaction("save achievement award", c -> { write(c, a); return null; }); }
    @Override public boolean deleteDefinition(String code) { return database.transaction("delete achievement definition (remove its awards first)", c -> update(c, "DELETE FROM achievement WHERE code=?", code) == 1); }
    @Override public boolean deleteAward(UUID profile, String code) { return database.transaction("delete achievement award", c -> update(c, "DELETE FROM achievement_award WHERE profile_id=? AND achievement_code=?", profile, code) == 1); }
    static List<Achievement> readDefinitions(Connection c) throws SQLException { return query(c, "SELECT * FROM achievement ORDER BY code", r -> new Achievement(r.getString("code"), r.getString("title"), r.getString("description"))); }
    static List<AchievementAward> readAwards(Connection c) throws SQLException { return query(c, "SELECT * FROM achievement_award ORDER BY profile_id,achievement_code", r -> new AchievementAward(UUID.fromString(r.getString("profile_id")), r.getString("achievement_code"), Instant.parse(r.getString("awarded_at")))); }
    static void write(Connection c, Achievement a) throws SQLException { update(c, "INSERT INTO achievement VALUES (?,?,?) ON CONFLICT(code) DO UPDATE SET title=excluded.title,description=excluded.description", a.code(), a.title(), a.description()); }
    static void write(Connection c, AchievementAward a) throws SQLException { update(c, "INSERT INTO achievement_award VALUES (?,?,?) ON CONFLICT(profile_id,achievement_code) DO UPDATE SET awarded_at=excluded.awarded_at", a.profileId(), a.achievementCode(), a.awardedAt()); }
}
