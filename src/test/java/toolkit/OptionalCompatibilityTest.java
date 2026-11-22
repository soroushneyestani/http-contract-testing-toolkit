package toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import toolkit.Contract.FieldType;

class OptionalCompatibilityTest {
    private static final String HEADER = "version\t1\ninteraction\tGET\t/items\t200\n";

    @Test
    void parsesRequiredAndOptionalRowsWithoutChangingTheLegacyConstructor() throws IOException {
        Path fixture = writeFixture(HEADER + "optional\tlabel\tstring\nfield\tid\tinteger\n"
                + "optional\tnote\tnull");
        try {
            Contract parsed = Contract.read(fixture);
            assertEquals(Map.of("id", FieldType.INTEGER), parsed.fields());
            assertEquals(Map.of("label", FieldType.STRING, "note", FieldType.NULL), parsed.optionalFields());
            assertEquals(Map.of(), new Contract("GET", "/items", 200, parsed.fields()).optionalFields());
        } finally {
            Files.delete(fixture);
        }
    }

    @Test
    void rejectsDuplicateAndMalformedOptionalRowsWithLineDiagnostics() throws IOException {
        var invalid = Map.of(
                "field\tid\tinteger\noptional\tid\tinteger", 4,
                "optional\tid\tinteger\nfield\tid\tinteger", 4,
                "optional\tid\tinteger\noptional\tid\tinteger", 4,
                "optional\tid", 3,
                "optional\tid\tSTRING", 3);
        for (var example : invalid.entrySet()) {
            Path fixture = writeFixture(HEADER + example.getKey());
            try {
                var failure = assertThrows(IllegalArgumentException.class, () -> Contract.read(fixture));
                assertTrue(failure.getMessage().startsWith("line " + example.getValue() + ": "), failure.getMessage());
            } finally {
                Files.delete(fixture);
            }
        }
    }

    @Test
    void snapshotsOptionalFieldsAndRejectsInvalidMaps() {
        var optional = new HashMap<>(Map.of("id", FieldType.INTEGER));
        var contract = contract(Map.of(), optional);
        optional.put("id", FieldType.STRING);
        assertEquals(Map.of("id", FieldType.INTEGER), contract.optionalFields());
        assertThrows(UnsupportedOperationException.class, () -> contract.optionalFields().clear());
        assertThrows(IllegalArgumentException.class, () -> contract(Map.of(), null));
        assertThrows(IllegalArgumentException.class, () -> contract(Map.of("id", FieldType.INTEGER), optional));
        optional.put("invalid", null);
        assertThrows(IllegalArgumentException.class, () -> contract(Map.of(), optional));
    }

    @Test
    void providerOptionalFieldsCannotSatisfyRequiredConsumerFields() {
        var consumer = contract(Map.of("id", FieldType.INTEGER), Map.of());
        var provider = contract(Map.of(), Map.of("id", FieldType.INTEGER));

        assertEquals(List.of("field 'id' is optional in the provider but required by the consumer"),
                Compatibility.check(consumer, provider));
    }

    @Test
    void consumerOptionalFieldsMayBeAbsentOrNarrowedInEitherProviderMap() {
        var consumer = contract(Map.of("id", FieldType.STRING), Map.of("price", FieldType.NUMBER));
        var absent = contract(Map.of("id", FieldType.STRING, "extra", FieldType.OBJECT), Map.of());
        var presentRequired = contract(Map.of("id", FieldType.STRING, "price", FieldType.INTEGER), Map.of());
        var presentOptional = contract(Map.of("id", FieldType.STRING), Map.of("price", FieldType.INTEGER));
        var tooWide = contract(Map.of("id", FieldType.STRING), Map.of("price", FieldType.STRING));
        var wrongRequiredType = contract(Map.of("id", FieldType.STRING, "price", FieldType.STRING), Map.of());

        assertEquals(List.of(), Compatibility.check(consumer, absent));
        assertEquals(List.of(), Compatibility.check(consumer, presentRequired));
        assertEquals(List.of(), Compatibility.check(consumer, presentOptional));
        assertEquals(List.of("field 'price' type mismatch: expected NUMBER, got STRING"),
                Compatibility.check(consumer, tooWide));
        assertEquals(Compatibility.check(consumer, tooWide), Compatibility.check(consumer, wrongRequiredType));
    }

    @Test
    void observedResponseDistinguishesAnAbsentOptionalFieldFromPresentJsonNull() {
        var consumer = contract(Map.of("id", FieldType.STRING),
                Map.of("note", FieldType.STRING, "deleted", FieldType.NULL, "price", FieldType.NUMBER));

        assertEquals(List.of(), Compatibility.checkResponse(consumer, 200, Map.of("id", FieldType.STRING)));
        assertEquals(List.of(), Compatibility.checkResponse(consumer, 200,
                Map.of("id", FieldType.STRING, "deleted", FieldType.NULL, "price", FieldType.INTEGER,
                        "unknown key", FieldType.ARRAY)));
        assertEquals(List.of("field 'note' type mismatch: expected STRING, got NULL"),
                Compatibility.checkResponse(consumer, 200, Map.of("id", FieldType.STRING, "note", FieldType.NULL)));
    }

    @Test
    void observedResponseReportsStatusAndThenAllFieldMismatchesInNameOrder() {
        var consumer = contract(Map.of("zeta", FieldType.INTEGER), Map.of("alpha", FieldType.STRING));
        var issues = Compatibility.checkResponse(consumer, 201, Map.of("alpha", FieldType.INTEGER));

        assertEquals(List.of("status mismatch: expected 200, got 201",
                "field 'alpha' type mismatch: expected STRING, got INTEGER", "field 'zeta' is missing"), issues);
        assertThrows(UnsupportedOperationException.class, () -> issues.add("another failure"));
        assertEquals(List.of("field 'zeta' type mismatch: expected INTEGER, got NUMBER"),
                Compatibility.checkResponse(consumer, 200, Map.of("zeta", FieldType.NUMBER)));
    }

    @Test
    void observedResponseRequiresAnExplicitFieldTypeForEveryReportedKey() {
        var consumer = contract(Map.of("id", FieldType.STRING), Map.of());
        var fields = new HashMap<String, FieldType>();
        fields.put("id", null);

        assertThrows(NullPointerException.class, () -> Compatibility.checkResponse(null, 200, Map.of()));
        assertThrows(NullPointerException.class, () -> Compatibility.checkResponse(consumer, 200, null));
        assertThrows(NullPointerException.class, () -> Compatibility.checkResponse(consumer, 200, fields));
    }

    private static Contract contract(Map<String, FieldType> required, Map<String, FieldType> optional) {
        return new Contract("GET", "/items", 200, required, optional);
    }

    private static Path writeFixture(String text) throws IOException {
        Path folder = Files.createDirectories(Path.of("bin"));
        Path fixture = Files.createTempFile(folder, "optional-contract-", ".tsv");
        return Files.writeString(fixture, text, StandardCharsets.UTF_8);
    }
}
