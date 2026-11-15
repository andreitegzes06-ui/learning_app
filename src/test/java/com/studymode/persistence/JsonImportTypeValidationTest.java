package com.studymode.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.studymode.application.data.StudyDataSnapshot;
import com.studymode.application.error.DataImportException;
import com.studymode.persistence.sqlite.*;
import com.studymode.persistence.transfer.JsonStudyDataTransfer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Black-box format tests mutate actual exported JSON and check the durable database after rejection. */
class JsonImportTypeValidationTest {
    @TempDir Path root;
    private final ObjectMapper documents = new ObjectMapper();
    private JsonStudyDataTransfer transfer;
    private SqliteStudyDataArchive archive;
    private StudyDataSnapshot original;
    private JsonNode exported;
    private byte[] databaseBefore;
    private Path databaseFile;
    private static final Set<String> OPTIONAL_NULLS = Set.of("/classrooms/0/description", "/tasks/0/classroomId",
            "/tasks/0/goalId", "/tasks/0/dueDate", "/tasks/0/completedAt", "/sessions/0/context/classroomId", "/sessions/0/context/goalId");

    @BeforeEach void setup() throws Exception {
        databaseFile = root.resolve("data/study.db");
        var database = new SqliteDatabase(databaseFile);
        original = StudyDataFixture.populate(database);
        archive = new SqliteStudyDataArchive(database, StudyDataFixture.CLOCK);
        transfer = new JsonStudyDataTransfer(archive, root.resolve("data"));
        Path file = root.resolve("valid.json"); transfer.exportTo(file);
        exported = documents.readTree(file.toFile());
        databaseBefore = Files.readAllBytes(databaseFile);
    }

    @Test void rejectsNumericDisplayNameWithAUsefulFieldError() throws Exception {
        var error = rejected(replace(exported, "/profiles/0/displayName", IntNode.valueOf(123)));
        assertTrue(error.getMessage().contains("$.profiles[0].displayName"));
        assertTrue(error.getMessage().contains("expected a JSON string"));
    }
    @Test void rejectsBooleanDisplayNameWithAUsefulFieldError() throws Exception {
        var error = rejected(replace(exported, "/profiles/0/displayName", BooleanNode.TRUE));
        assertTrue(error.getMessage().contains("$.profiles[0].displayName"));
        assertTrue(error.getMessage().contains("expected a JSON string"));
    }

    @TestFactory Stream<DynamicTest> rejectsWrongTypesThroughoutTheExportedFormat() {
        JsonNode full = withOptionalValues();
        List<Map.Entry<String, JsonNode>> locations = new ArrayList<>(); collect(full, "", locations);
        List<DynamicTest> tests = new ArrayList<>();
        for (var location : locations) {
            for (JsonNode wrong : wrongTypes(location.getValue())) {
                if (wrong.isNull() && OPTIONAL_NULLS.contains(location.getKey())) continue;
                String pointer = location.getKey();
                tests.add(DynamicTest.dynamicTest((pointer.isEmpty() ? "/" : pointer) + " rejects " + wrong,
                        () -> rejected(replace(full, pointer, wrong))));
            }
        }
        return tests.stream();
    }

    @TestFactory Stream<DynamicTest> rejectsEveryMissingPropertyIncludingExplicitlyNullableProperties() {
        JsonNode full = withOptionalValues();
        List<Map.Entry<String, JsonNode>> locations = new ArrayList<>(); collect(full, "", locations);
        List<DynamicTest> tests = new ArrayList<>();
        for (var location : locations) {
            if (!location.getValue().isObject()) continue;
            location.getValue().fieldNames().forEachRemaining(name -> {
                String pointer = location.getKey();
                tests.add(DynamicTest.dynamicTest("missing " + pointer + "/" + name, () -> {
                    JsonNode altered = full.deepCopy(); ((ObjectNode) altered.at(pointer)).remove(name);
                    var error = rejected(altered); assertTrue(error.getMessage().contains("required property is missing"));
                }));
            });
        }
        return tests.stream();
    }

    @Test void rejectsBooleanFlagsAndStringBooleanFlagsThatDoNotExistInFormatOne() throws Exception {
        // Format 1 has no boolean-valued properties: completion is a nullable timestamp, not a flag.
        for (JsonNode value : List.of(BooleanNode.TRUE, BooleanNode.FALSE, TextNode.valueOf("true"), TextNode.valueOf("false"))) {
            JsonNode altered = exported.deepCopy(); ((ObjectNode) altered.at("/tasks/0")).set("completed", value);
            var error = rejected(altered); assertTrue(error.getMessage().contains("$.tasks[0].completed"));
            assertTrue(error.getMessage().contains("unknown property"));
        }
    }

    @Test void rejectsMalformedDateIdentifierAndEnumValuesWithFieldContext() throws Exception {
        for (var invalid : Map.of("/profiles/0/createdAt", "not-a-date", "/profiles/0/id", "not-a-uuid", "/goals/0/period", "YEARLY").entrySet()) {
            var error = rejected(replace(exported, invalid.getKey(), TextNode.valueOf(invalid.getValue())));
            assertTrue(error.getMessage().contains("$."));
        }
    }

    @Test void rejectsEmptyStringToNullCoercionForOptionalDatesAndIdentifiers() throws Exception {
        for (String pointer : List.of("/tasks/0/classroomId", "/tasks/0/goalId", "/tasks/0/dueDate", "/tasks/0/completedAt")) {
            for (String empty : List.of("", "   ")) rejected(replace(exported, pointer, TextNode.valueOf(empty)));
        }
    }

    @Test void preservesNumericAndBooleanLookingTextAsGenuineStrings() throws Exception {
        JsonNode valid = exported.deepCopy();
        ((ObjectNode) valid.at("/profiles/0")).put("displayName", "123");
        ((ObjectNode) valid.at("/classrooms/0")).put("name", "true");
        Path file = root.resolve("valid-text.json"); documents.writeValue(file.toFile(), valid);
        var incoming = transfer.inspectImport(file);
        assertEquals("123", incoming.profiles().getFirst().displayName());
        assertEquals("true", incoming.classrooms().getFirst().name());
        transfer.importSnapshot(incoming);
        Path exportedAgain = root.resolve("text-roundtrip.json"); transfer.exportTo(exportedAgain);
        assertEquals(archive.snapshot(), transfer.inspectImport(exportedAgain));
    }

    @Test void preservesEmptyTextNullableValuesAndAnUnfinishedSessionInValidImports() throws Exception {
        JsonNode valid = exported.deepCopy();
        ((ObjectNode) valid.at("/classrooms/0")).put("description", "");
        ((ObjectNode) valid.at("/sessions/0")).put("status", "PAUSED").putNull("endedAt");
        ObjectNode task = (ObjectNode) valid.at("/tasks/0");
        for (String field : List.of("classroomId", "goalId", "dueDate", "completedAt")) task.putNull(field);
        Path file = root.resolve("valid-nullable.json"); documents.writeValue(file.toFile(), valid);
        var incoming = transfer.inspectImport(file);
        assertNull(incoming.tasks().getFirst().dueDate()); assertNull(incoming.sessions().getFirst().endedAt());
        transfer.importSnapshot(incoming);
        assertEquals(com.studymode.domain.study.SessionStatus.INTERRUPTED, archive.snapshot().sessions().getFirst().status());
        Path roundtrip = root.resolve("nullable-roundtrip.json"); transfer.exportTo(roundtrip);
        assertEquals(archive.snapshot(), transfer.inspectImport(roundtrip));
    }

    @Test void preservesOptionalNullDescriptionNormalizationAndLongIntegerValues() throws Exception {
        JsonNode valid = exported.deepCopy(); ((ObjectNode) valid.at("/classrooms/0")).putNull("description");
        ((ObjectNode) valid.at("/sessions/0")).put("activeFocusSeconds", 3_000_000_000L);
        Path file = root.resolve("valid-long.json"); documents.writeValue(file.toFile(), valid);
        var incoming = transfer.inspectImport(file);
        assertEquals("", incoming.classrooms().getFirst().description());
        assertEquals(3_000_000_000L, incoming.sessions().getFirst().activeFocusSeconds());
        assertEquals(original, archive.snapshot());
    }

    private DataImportException rejected(JsonNode document) throws Exception {
        Path file = root.resolve("invalid.json"); documents.writeValue(file.toFile(), document);
        DataImportException error = assertThrows(DataImportException.class, () -> {
            // Exercise the actual inspect-then-replace pipeline; replacement must never be reached.
            transfer.importSnapshot(transfer.inspectImport(file));
        });
        assertTrue(error.getMessage().contains("No records were changed"));
        assertEquals(original, archive.snapshot());
        assertArrayEquals(databaseBefore, Files.readAllBytes(databaseFile));
        assertFalse(Files.exists(root.resolve("data/backups")), "Rejected input must not start replacement or backup");
        return error;
    }
    private JsonNode withOptionalValues() {
        JsonNode full = exported.deepCopy(); ((ObjectNode) full.at("/tasks/0")).put("completedAt", StudyDataFixture.NOW.toString());
        ObjectNode context = (ObjectNode) full.at("/sessions/0/context");
        context.put("classroomId", full.at("/classrooms/0/id").textValue()).put("classroomName", "Saved classroom");
        context.put("goalId", full.at("/goals/0/id").textValue()).put("goalName", "Saved goal");
        return full;
    }
    private JsonNode replace(JsonNode original, String pointer, JsonNode value) {
        if (pointer.isEmpty()) return value.deepCopy();
        JsonNode copy = original.deepCopy(); int slash = pointer.lastIndexOf('/');
        JsonNode parent = copy.at(pointer.substring(0, slash)); String name = pointer.substring(slash + 1);
        if (parent.isArray()) ((ArrayNode) parent).set(Integer.parseInt(name), value.deepCopy());
        else ((ObjectNode) parent).set(name, value.deepCopy());
        return copy;
    }
    private void collect(JsonNode node, String pointer, List<Map.Entry<String, JsonNode>> locations) {
        locations.add(Map.entry(pointer, node));
        if (node.isObject()) node.properties().forEach(entry -> collect(entry.getValue(), pointer + "/" + entry.getKey(), locations));
        else if (node.isArray()) for (int i=0; i<node.size(); i++) collect(node.get(i), pointer + "/" + i, locations);
    }
    private List<JsonNode> wrongTypes(JsonNode valid) {
        List<JsonNode> wrong = new ArrayList<>();
        if (!valid.isTextual()) wrong.add(TextNode.valueOf(valid.isNumber() ? valid.toString() : "wrong type"));
        if (!valid.isNumber()) wrong.add(IntNode.valueOf(123));
        if (!valid.isBoolean()) { wrong.add(BooleanNode.TRUE); wrong.add(BooleanNode.FALSE); }
        else { wrong.add(TextNode.valueOf("true")); wrong.add(TextNode.valueOf("false")); }
        if (!valid.isObject()) wrong.add(documents.createObjectNode());
        if (!valid.isArray()) wrong.add(documents.createArrayNode());
        wrong.add(NullNode.getInstance());
        if (valid.isIntegralNumber()) {
            wrong.add(DecimalNode.valueOf(new BigDecimal("25.0")));
            wrong.add(BigIntegerNode.valueOf(new BigInteger("9223372036854775808")));
        } else if (valid.isTextual()) wrong.add(DecimalNode.valueOf(new BigDecimal("123.5")));
        return wrong;
    }
}
