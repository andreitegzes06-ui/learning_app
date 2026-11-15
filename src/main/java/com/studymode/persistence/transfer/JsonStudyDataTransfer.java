package com.studymode.persistence.transfer;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.studymode.application.data.*;
import com.studymode.application.error.DataImportException;
import com.studymode.application.port.*;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

public final class JsonStudyDataTransfer implements StudyDataTransfer {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final StudyDataArchive archive;
    private final Path dataRoot;
    private final SnapshotValidator validator = new SnapshotValidator();
    private final StudyJsonStructureValidator structureValidator = new StudyJsonStructureValidator();
    private final ObjectMapper mapper;

    public JsonStudyDataTransfer(StudyDataArchive archive, Path dataRoot) {
        this.archive = archive; this.dataRoot = dataRoot.toAbsolutePath().normalize();
        JsonFactory factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(4096).maxNumberLength(100).build()).build();
        mapper = JsonMapper.builder(factory).addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS,
                        DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .build();
        // Empty text is valid as text (for example an optional description), but must never become null for an ID/date/object.
        mapper.coercionConfigDefaults().setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyArray, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyObject, CoercionAction.Fail);
        rejectCoercions(LogicalType.Textual, CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean);
        rejectCoercions(LogicalType.Integer, CoercionInputShape.String, CoercionInputShape.EmptyString,
                CoercionInputShape.Float, CoercionInputShape.Boolean);
        rejectCoercions(LogicalType.Boolean, CoercionInputShape.String, CoercionInputShape.EmptyString,
                CoercionInputShape.Integer, CoercionInputShape.Float);
        rejectCoercions(LogicalType.DateTime, CoercionInputShape.Integer, CoercionInputShape.Float,
                CoercionInputShape.Boolean, CoercionInputShape.EmptyString, CoercionInputShape.Array);
    }
    private void rejectCoercions(LogicalType target, CoercionInputShape... inputs) {
        for (CoercionInputShape input : inputs) mapper.coercionConfigFor(target).setCoercion(input, CoercionAction.Fail);
    }
    @Override public void exportTo(Path file) throws IOException {
        Path target = file.toAbsolutePath().normalize();
        if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) throw new IOException("Choose a new file with a .json extension.");
        Path resolved = Files.exists(target) ? target.toRealPath() : target.getParent().toRealPath().resolve(target.getFileName());
        if (resolved.startsWith(dataRoot.toRealPath())) throw new IOException("Choose an export location outside the application data directory.");
        writeNew(target, archive.snapshot());
    }
    @Override public StudyDataSnapshot inspectImport(Path file) throws IOException {
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new DataImportException("Import exceeds the 20 MiB limit.");
        try {
            JsonNode document = mapper.readTree(bytes);
            structureValidator.validate(document);
            if (document.get("formatVersion").intValue() == 1) {
                // Upgrade only after strict legacy validation; the new fields are trusted defaults, not user coercions.
                for (JsonNode session : document.get("sessions")) {
                    var record = (com.fasterxml.jackson.databind.node.ObjectNode) session;
                    record.set("context", mapper.valueToTree(com.studymode.domain.study.SessionContext.legacy()));
                    record.set("checkpointAt", record.get("endedAt").isNull() ? record.get("startedAt") : record.get("endedAt"));
                }
            }
            if (document.get("formatVersion").intValue() == 1 || document.get("formatVersion").intValue() == 2) {
                for (JsonNode classroom : document.get("classrooms")) {
                    var record = (com.fasterxml.jackson.databind.node.ObjectNode) classroom;
                    record.put("color", "#7569EF"); record.put("notes", "");
                }
                for (JsonNode goal : document.get("goals")) ((com.fasterxml.jackson.databind.node.ObjectNode) goal).putNull("classroomId");
                for (JsonNode workspace : document.get("workspaces")) ((com.fasterxml.jackson.databind.node.ObjectNode) workspace).putArray("shortcuts");
                ((com.fasterxml.jackson.databind.node.ObjectNode) document).put("formatVersion", 3);
            }
            StudyDataSnapshot snapshot = mapper.treeToValue(document, StudyDataSnapshot.class);
            validator.validate(snapshot); return snapshot;
        } catch (JsonMappingException e) {
            StringBuilder path = new StringBuilder("$");
            for (JsonMappingException.Reference reference : e.getPath()) {
                if (reference.getFieldName() != null) path.append('.').append(reference.getFieldName());
                else if (reference.getIndex() >= 0) path.append('[').append(reference.getIndex()).append(']');
            }
            throw new DataImportException("Invalid study JSON at " + path + ": invalid value, date, identifier or required property. No records were changed.", e);
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException e) {
            throw new DataImportException("Invalid study JSON. Check its fields, types, dates, IDs and format version. No records were changed.", e);
        }
    }
    @Override public Path importSnapshot(StudyDataSnapshot data) throws IOException {
        validator.validate(data);
        Path backup = dataRoot.resolve("backups").resolve("pre-import-" + UUID.randomUUID() + ".json");
        try {
            archive.replace(data, previous -> {
                try { Files.createDirectories(backup.getParent()); writeNew(backup, previous);
                    // Verify backup is readable and valid before any record is deleted.
                    inspectImport(backup);
                } catch (IOException e) { throw new UncheckedIOException("Could not create the recovery backup. Import cancelled.", e); }
            });
        } catch (UncheckedIOException e) { throw e.getCause(); }
        return backup;
    }
    private void writeNew(Path target, StudyDataSnapshot snapshot) throws IOException {
        validator.validate(snapshot);
        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(snapshot);
        if (json.length > MAX_BYTES) throw new IOException("Snapshot exceeds the 20 MiB transfer limit; no file was replaced.");
        if (Files.exists(target)) throw new FileAlreadyExistsException("Choose a new JSON filename; existing files are never overwritten.");
        Path temporary = Files.createTempFile(target.getParent(), "study-export-", ".tmp");
        try {
            try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var bytes = java.nio.ByteBuffer.wrap(json);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temporary, target); // Same-directory move; no replacement of existing files.
        } finally { Files.deleteIfExists(temporary); }
    }
}
