package toolkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** The expected request and required and optional response fields of one HTTP interaction. */
public record Contract(String method, String path, int status, Map<String, FieldType> fields,
                       Map<String, FieldType> optionalFields) {
    private static final Pattern METHOD = Pattern.compile("[A-Z]+");
    private static final Pattern ROUTE = Pattern.compile("/\\S*", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern STATUS_TOKEN = Pattern.compile("[0-9]{3}");
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public enum FieldType {
        STRING, INTEGER, NUMBER, BOOLEAN, NULL, OBJECT, ARRAY
    }

    public Contract {
        validateInteraction(method, path, status);
        fields = snapshotFields(fields, "fields");
        optionalFields = snapshotFields(optionalFields, "optional fields");
        for (String name : optionalFields.keySet()) {
            if (fields.containsKey(name)) {
                throw new IllegalArgumentException("duplicate field '" + name + "'");
            }
        }
    }

    public Contract(String method, String path, int status, Map<String, FieldType> fields) {
        this(method, path, status, fields, Map.of());
    }

    /** Reads a version 1 UTF-8 TSV contract. */
    public static Contract read(Path source) throws IOException {
        List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals("version\t1")) {
            throw invalidLine(1, "expected a version record with version 1");
        }
        if (lines.size() < 2) {
            throw invalidLine(2, "missing interaction record");
        }

        String[] interaction = lines.get(1).split("\t", -1);
        if (interaction.length != 4 || !interaction[0].equals("interaction")) {
            throw invalidLine(2, "expected interaction, method, path, and status columns");
        }
        if (!STATUS_TOKEN.matcher(interaction[3]).matches()) {
            throw invalidLine(2, "status must contain exactly three ASCII digits");
        }
        int status = Integer.parseInt(interaction[3]);
        try {
            validateInteraction(interaction[1], interaction[2], status);
        } catch (IllegalArgumentException error) {
            throw invalidLine(2, error.getMessage());
        }

        Map<String, FieldType> fields = new HashMap<>();
        Map<String, FieldType> optionalFields = new HashMap<>();
        for (int index = 2; index < lines.size(); index++) {
            readField(lines.get(index), index + 1, fields, optionalFields);
        }
        return new Contract(interaction[1], interaction[2], status, fields, optionalFields);
    }

    private static void readField(String line, int lineNumber, Map<String, FieldType> fields,
                                  Map<String, FieldType> optionalFields) {
        String[] columns = line.split("\t", -1);
        boolean optional = columns[0].equals("optional");
        if (columns.length != 3 || (!optional && !columns[0].equals("field"))) {
            throw invalidLine(lineNumber, optional ? "expected optional, name, and type columns"
                    : "expected field, name, and type columns");
        }
        String name = columns[1];
        if (!FIELD_NAME.matcher(name).matches()) {
            throw invalidLine(lineNumber, "invalid field name '" + name + "'");
        }
        FieldType type = readType(columns[2], lineNumber);
        if (fields.containsKey(name) || optionalFields.containsKey(name)) {
            throw invalidLine(lineNumber, "duplicate field '" + name + "'");
        }
        (optional ? optionalFields : fields).put(name, type);
    }

    private static FieldType readType(String token, int lineNumber) {
        for (FieldType type : FieldType.values()) {
            if (type.name().toLowerCase(Locale.ROOT).equals(token)) {
                return type;
            }
        }
        throw invalidLine(lineNumber, "unknown field type '" + token + "'");
    }

    private static void validateInteraction(String method, String path, int status) {
        if (method == null || !METHOD.matcher(method).matches()) {
            throw new IllegalArgumentException("method must use uppercase letters A-Z");
        }
        if (path == null || !ROUTE.matcher(path).matches()) {
            throw new IllegalArgumentException("path must start with '/' and contain no whitespace");
        }
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status must be between 100 and 599");
        }
    }

    private static void validateField(String name, FieldType type) {
        if (name == null || !FIELD_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid field name '" + name + "'");
        }
        if (type == null) {
            throw new IllegalArgumentException("field '" + name + "' must have a type");
        }
    }

    private static Map<String, FieldType> snapshotFields(Map<String, FieldType> fields, String description) {
        if (fields == null) {
            throw new IllegalArgumentException(description + " must be supplied");
        }
        fields.forEach(Contract::validateField);
        return Map.copyOf(fields);
    }

    private static IllegalArgumentException invalidLine(int lineNumber, String detail) {
        return new IllegalArgumentException("line " + lineNumber + ": " + detail);
    }
}
