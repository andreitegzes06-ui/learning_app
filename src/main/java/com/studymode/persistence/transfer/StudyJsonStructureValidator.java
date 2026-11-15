package com.studymode.persistence.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.studymode.application.data.StudyDataSnapshot;
import com.studymode.application.error.DataImportException;
import com.studymode.domain.classroom.Classroom;
import com.studymode.domain.planning.StudyTask;
import com.studymode.domain.study.StudySession;
import com.studymode.domain.study.SessionContext;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Checks versioned JSON tokens before Jackson can coerce values or construct domain records. */
final class StudyJsonStructureValidator {
    // All properties must be present. These values alone may explicitly be null.
    private static final Map<Class<?>, Set<String>> NULLABLE = Map.of(
            StudySession.class, Set.of("endedAt"),
            StudyTask.class, Set.of("classroomId", "goalId", "dueDate", "completedAt"),
            Classroom.class, Set.of("description"), SessionContext.class, Set.of("classroomId", "goalId"));

    void validate(JsonNode root) {
        JsonNode version = root == null ? null : root.path("formatVersion");
        boolean legacy = version != null && version.isIntegralNumber() && version.canConvertToInt() && version.intValue() == 1;
        validateValue(root, StudyDataSnapshot.class, "$", false, new HashMap<>(), legacy);
    }

    private void validateValue(JsonNode node, Type type, String path, boolean nullable,
                               Map<Class<?>, Map<String, RecordComponent>> records, boolean legacy) {
        if (node == null || node.isMissingNode()) fail(path, "required property is missing");
        if (node.isNull()) {
            if (nullable) return;
            fail(path, "null is not allowed");
        }
        if (type instanceof ParameterizedType list && list.getRawType() == List.class) {
            require(node.isArray(), path, "an array", node);
            for (int i = 0; i < node.size(); i++) {
                validateValue(node.get(i), list.getActualTypeArguments()[0], path + "[" + i + "]", false, records, legacy);
            }
            return;
        }
        if (!(type instanceof Class<?>)) fail(path, "unsupported field type in this format");
        Class<?> target = (Class<?>) type;
        if (target.isRecord()) {
            require(node.isObject(), path, "an object", node);
            Map<String, RecordComponent> fields = records.computeIfAbsent(target, record -> {
                Map<String, RecordComponent> components = new LinkedHashMap<>();
                for (RecordComponent component : record.getRecordComponents()) {
                    if (legacy && record == StudySession.class && Set.of("context", "checkpointAt").contains(component.getName())) continue;
                    components.put(component.getName(), component);
                }
                return components;
            });
            var names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!fields.containsKey(name)) fail(path + "." + name, "unknown property in this format");
            }
            for (RecordComponent field : fields.values()) {
                validateValue(node.get(field.getName()), field.getGenericType(), path + "." + field.getName(),
                        NULLABLE.getOrDefault(target, Set.of()).contains(field.getName()), records, legacy);
            }
        } else if (target == int.class) {
            require(node.isIntegralNumber() && node.canConvertToInt(), path, "a 32-bit JSON integer", node);
        } else if (target == long.class) {
            require(node.isIntegralNumber() && node.canConvertToLong(), path, "a 64-bit JSON integer", node);
        } else if (target == boolean.class) {
            require(node.isBoolean(), path, "a JSON boolean", node);
        } else if (target == String.class || target == UUID.class || target == URI.class || target == Instant.class
                || target == ZoneId.class || target == LocalDate.class || target.isEnum()) {
            require(node.isTextual(), path, "a JSON string", node);
        } else {
            // Fail closed when the Java format evolves until its new token types are explicitly reviewed.
            fail(path, "unsupported field type in format 1");
        }
    }

    private void require(boolean valid, String path, String expected, JsonNode actual) {
        if (!valid) fail(path, "expected " + expected + "; found " + actual.getNodeType().name().toLowerCase(Locale.ROOT));
    }
    private void fail(String path, String reason) {
        throw new DataImportException("Invalid study JSON at " + path + ": " + reason + ". No records were changed.");
    }
}
