package toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import toolkit.Contract.FieldType;

class ContractVerifierTest {
    private static final String HEADER = "version\t1\ninteraction\tGET\t/items\t200\n";
    private Path fixtures;

    @BeforeEach
    void createWorkspaceFixtures() throws IOException {
        Path directory = Path.of("bin").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        fixtures = Files.createTempDirectory(directory, "contract-fixtures-");
    }

    @AfterEach
    void removeWorkspaceFixtures() throws IOException {
        if (fixtures == null) {
            return;
        }
        try (var children = Files.newDirectoryStream(fixtures)) {
            for (Path child : children) {
                Files.delete(child);
            }
        }
        Files.delete(fixtures);
    }

    @Test
    void parsesEveryFieldTypeAndCrLfWithoutFinalTerminator() throws IOException {
        String route = "/caf\u00e9";
        Path document = fixture("consumer contract.tsv", String.join("\r\n", List.of(
                "version\t1", "interaction\tGET\t" + route + "\t200",
                "field\tid\tstring", "field\tID\tinteger", "field\tprice\tnumber",
                "field\tavailable\tboolean", "field\tnullable\tnull", "field\tmetadata\tobject",
                "field\ttags\tarray")));

        var expected = new Contract("GET", route, 200, Map.of(
                "id", FieldType.STRING, "ID", FieldType.INTEGER, "price", FieldType.NUMBER,
                "available", FieldType.BOOLEAN, "nullable", FieldType.NULL,
                "metadata", FieldType.OBJECT, "tags", FieldType.ARRAY));

        assertEquals(expected, Contract.read(document));
        assertEquals(Map.of(), Contract.read(fixture("no fields.tsv", HEADER)).fields());
    }

    @Test
    void snapshotsCallerOwnedFieldMap() {
        var fields = new HashMap<>(Map.of("id", FieldType.STRING));
        var contract = new Contract("GET", "/items", 200, fields);
        fields.put("id", FieldType.NUMBER);
        fields.put("extra", FieldType.ARRAY);

        assertEquals(Map.of("id", FieldType.STRING), contract.fields());
        assertThrows(UnsupportedOperationException.class, () -> contract.fields().put("id", FieldType.NUMBER));
    }

    @Test
    void rejectsInvalidValuesWhenConstructingAContract() {
        var nullType = new HashMap<String, FieldType>();
        nullType.put("id", null);
        assertThrows(IllegalArgumentException.class, () -> new Contract("get", "/items", 200, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Contract("GET", "items", 200, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Contract("GET", "/items", 99, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Contract("GET", "/items", 200, Map.of("1id", FieldType.STRING)));
        assertThrows(IllegalArgumentException.class, () -> new Contract("GET", "/items", 200, nullType));
    }

    @Test
    void rejectsMalformedDocumentsWithLineDiagnostics() throws IOException {
        var cases = List.of(
                new InvalidFixture("unknown version", "version\t2\ninteraction\tGET\t/items\t200\n", 1),
                new InvalidFixture("spaces instead of tabs", "version 1\ninteraction\tGET\t/items\t200\n", 1),
                new InvalidFixture("absent interaction", "version\t1\n", 2),
                new InvalidFixture("relative path", HEADER.replace("/items", "items"), 2),
                new InvalidFixture("whitespace in path", HEADER.replace("/items", "/some items"), 2),
                new InvalidFixture("nonnumeric status", HEADER.replace("200", "OK"), 2),
                new InvalidFixture("out of range status", HEADER.replace("200", "600"), 2),
                new InvalidFixture("noncanonical status", HEADER.replace("200", "0200"), 2),
                new InvalidFixture("short field", HEADER + "field\tid\n", 3),
                new InvalidFixture("extra column", HEADER + "field\tid\tstring\textra\n", 3),
                new InvalidFixture("unknown record kind", HEADER + "note\tid\tstring\n", 3),
                new InvalidFixture("invalid name", HEADER + "field\tsome-name\tstring\n", 3),
                new InvalidFixture("unknown type", HEADER + "field\tid\tdecimal\n", 3),
                new InvalidFixture("mis-cased type", HEADER + "field\tid\tSTRING\n", 3),
                new InvalidFixture("duplicate name", HEADER + "field\tid\tstring\nfield\tid\tinteger\n", 4));

        for (InvalidFixture example : cases) {
            Path document = fixture("invalid.tsv", example.content());
            var error = assertThrows(IllegalArgumentException.class, () -> Contract.read(document), example.name());
            assertTrue(error.getMessage().contains("line " + example.line() + ":"), error.getMessage());
        }

        assertThrows(IllegalArgumentException.class, () -> Contract.read(fixture("empty.tsv", "")));
    }

    @Test
    void missingFileAndInvalidUtf8AreIoErrors() throws IOException {
        Path malformed = Files.write(fixtures.resolve("invalid-utf8.tsv"), new byte[] {(byte) 0xff});
        assertThrows(IOException.class, () -> Contract.read(malformed));
        assertThrows(IOException.class, () -> Contract.read(fixtures.resolve("missing.tsv")));
    }

    @Test
    void acceptsAdditiveFieldsAndIntegerProviderForNumberConsumer() throws IOException {
        Contract consumer = Contract.read(fixture("consumer.tsv", HEADER + "field\tprice\tnumber\n"));
        Contract provider = Contract.read(fixture("provider.tsv", HEADER
                + "field\tprice\tinteger\nfield\tlabel\tstring\n"));

        assertEquals(List.of(), Compatibility.check(consumer, provider));

        List<String> reverse = Compatibility.check(provider, consumer);
        assertEquals(2, reverse.size());
        assertTrue(reverse.get(0).contains("label"), reverse.toString());
        assertTrue(reverse.get(1).contains("price"), reverse.toString());
    }

    @Test
    void reportsHeaderChangesThenSortedFieldChanges() throws IOException {
        Contract consumer = Contract.read(fixture("consumer.tsv", HEADER
                + "field\tzeta\tboolean\nfield\talpha\tstring\nfield\tomega\tarray\n"));
        Contract provider = Contract.read(fixture("provider.tsv", "version\t1\ninteraction\tPOST\t/others\t201\n"
                + "field\tOmega\tarray\nfield\tzeta\tboolean\nfield\talpha\tinteger\n"));

        List<String> issues = Compatibility.check(consumer, provider);
        assertEquals(5, issues.size(), issues.toString());
        assertDiagnosticMentions(issues.get(0), "method", "GET", "POST");
        assertDiagnosticMentions(issues.get(1), "path", "/items", "/others");
        assertDiagnosticMentions(issues.get(2), "status", "200", "201");
        assertDiagnosticMentions(issues.get(3), "alpha", "mismatch");
        assertDiagnosticMentions(issues.get(4), "omega", "missing");
        assertThrows(UnsupportedOperationException.class, () -> issues.add("other finding"));
    }

    @Test
    void comparisonRequiresBothContracts() {
        var contract = new Contract("GET", "/items", 200, Map.of());
        assertThrows(NullPointerException.class, () -> Compatibility.check(null, contract));
        assertThrows(NullPointerException.class, () -> Compatibility.check(contract, null));
    }

    @Test
    void cliSeparatesCompatibleIncompatibleAndInvalidResults() throws IOException {
        Path consumer = fixture("consumer contract.tsv", HEADER + "field\tid\tstring\n");
        Path compatible = fixture("compatible.tsv", HEADER + "field\tid\tstring\nfield\textra\tarray\n");
        Path breaking = fixture("breaking.tsv", HEADER);
        Path invalid = fixture("invalid.tsv", HEADER + "field\tid\tunsupported\n");

        var accepted = run(consumer.toString(), compatible.toString());
        assertEquals(0, accepted.exitCode(), accepted.stderr());
        assertEquals("", accepted.stderr());

        var rejected = run(consumer.toString(), breaking.toString());
        assertEquals(1, rejected.exitCode(), rejected.stderr());
        assertEquals("", rejected.stderr());
        assertDiagnosticMentions(rejected.stdout(), "id", "missing");

        assertCliError(run(consumer.toString(), invalid.toString()));
        assertCliError(run(consumer.toString(), fixtures.resolve("missing.tsv").toString()));
        assertCliError(run(fixtures.resolve("missing.tsv").toString(), compatible.toString()));
    }

    @Test
    void cliRejectsWrongArgumentCounts() {
        assertCliError(run());
        assertCliError(run("consumer.tsv"));
        assertCliError(run("consumer.tsv", "provider.tsv", "extra"));
    }

    private Path fixture(String name, String text) throws IOException {
        return Files.writeString(fixtures.resolve(name), text, StandardCharsets.UTF_8);
    }

    private static void assertDiagnosticMentions(String diagnostic, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(diagnostic.contains(fragment), "Expected '" + fragment + "' in: " + diagnostic);
        }
    }

    private static CliResult run(String... args) {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        int status;
        try (var out = new PrintStream(stdout, false, StandardCharsets.UTF_8);
             var err = new PrintStream(stderr, false, StandardCharsets.UTF_8)) {
            status = Main.run(args, out, err);
        }
        return new CliResult(status, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private static void assertCliError(CliResult result) {
        assertEquals(2, result.exitCode(), result.stdout() + result.stderr());
        assertEquals("", result.stdout());
        assertFalse(result.stderr().isBlank());
    }

    private record InvalidFixture(String name, String content, int line) { }
    private record CliResult(int exitCode, String stdout, String stderr) { }
}
