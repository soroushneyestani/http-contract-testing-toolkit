package toolkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import toolkit.Contract.FieldType;

/** Compares a provider's guarantees with a consumer's requirements. */
public final class Compatibility {
    private Compatibility() {
    }

    /** Returns immutable diagnostics, with an empty list indicating compatibility. */
    public static List<String> check(Contract consumer, Contract provider) {
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(provider, "provider");

        List<String> findings = new ArrayList<>();
        addMismatch(findings, "method", consumer.method(), provider.method());
        addMismatch(findings, "path", consumer.path(), provider.path());
        addMismatch(findings, "status", consumer.status(), provider.status());
        compareFields(findings, consumer, provider.fields(), provider.optionalFields());
        return List.copyOf(findings);
    }

    /** Compares an observed HTTP status and its decoded JSON property types with the consumer contract. */
    public static List<String> checkResponse(Contract consumer, int status, Map<String, FieldType> actualFields) {
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(actualFields, "actualFields");

        List<String> findings = new ArrayList<>();
        addMismatch(findings, "status", consumer.status(), status);
        compareFields(findings, consumer, Map.copyOf(actualFields), Map.of());
        return List.copyOf(findings);
    }

    private static void compareFields(List<String> findings, Contract consumer, Map<String, FieldType> requiredFields,
                                      Map<String, FieldType> optionalFields) {
        var names = new TreeSet<>(consumer.fields().keySet());
        names.addAll(consumer.optionalFields().keySet());
        for (String field : names) {
            FieldType required = consumer.fields().get(field);
            FieldType actual = requiredFields.get(field);
            if (actual == null) {
                actual = optionalFields.get(field);
                if (required != null) {
                    findings.add(actual == null ? "field '" + field + "' is missing"
                            : "field '" + field + "' is optional in the provider but required by the consumer");
                    continue;
                }
            }
            FieldType expected = required == null ? consumer.optionalFields().get(field) : required;
            if (actual != null && !isCompatible(expected, actual)) {
                addMismatch(findings, "field '" + field + "' type", expected, actual);
            }
        }
    }

    private static boolean isCompatible(FieldType required, FieldType guaranteed) {
        return required == guaranteed || (required == FieldType.NUMBER && guaranteed == FieldType.INTEGER);
    }

    private static void addMismatch(List<String> findings, String property, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            findings.add(property + " mismatch: expected " + expected + ", got " + actual);
        }
    }
}
