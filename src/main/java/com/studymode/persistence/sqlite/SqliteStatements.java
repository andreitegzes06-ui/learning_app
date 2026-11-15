package com.studymode.persistence.sqlite;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Parameter binding and resource closure only; contains no entity or transaction policy. */
final class SqliteStatements {
    private SqliteStatements() { }
    @FunctionalInterface interface RowReader<T> { T read(ResultSet row) throws SQLException; }
    static int update(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) { bind(statement, values); return statement.executeUpdate(); }
    }
    static <T> List<T> query(Connection connection, String sql, RowReader<T> reader, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (var rows = statement.executeQuery()) {
                List<T> result = new ArrayList<>(); while (rows.next()) result.add(reader.read(rows)); return List.copyOf(result);
            }
        }
    }
    private static void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            statement.setObject(i + 1, value == null || value instanceof Number ? value : value.toString());
        }
    }
}
