package toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HttpCliTest {
    private static final String FIXTURE = "fixtures/consumer-http.tsv";

    @Test
    void realHttpModeSeparatesCompatibilityFromFailure() throws IOException {
        try (var baseline = SampleProvider.start(SampleProvider.State.BASELINE);
             var breaking = SampleProvider.start(SampleProvider.State.BREAKING)) {
            var compatible = run("--http", FIXTURE, baseline.origin().toString());
            assertEquals(0, compatible.exitCode());
            assertTrue(compatible.stdout().contains("Compatible."));
            assertEquals("", compatible.stderr());

            var incompatible = run("--http", FIXTURE, breaking.origin().toString());
            assertEquals(1, incompatible.exitCode());
            assertTrue(incompatible.stdout().contains("Incompatible."));
            assertTrue(incompatible.stdout().contains("field 'id' type"));
            assertTrue(incompatible.stdout().contains("field 'price' is missing"));
            assertEquals("", incompatible.stderr());
        }
    }

    @Test
    void rejectsInvalidFixtureOriginAndUsageAtExitTwo() {
        for (var invalid : new String[][] {
                {"--http", FIXTURE, "https://127.0.0.1:12345"},
                {"--http", FIXTURE, "not a URI"},
                {"--http", "fixtures/nonexistent.tsv", "http://127.0.0.1:12345"},
                {"--http", FIXTURE},
                {"--demo"},
                {"--demo", FIXTURE, "extra"}
        }) {
            var result = run(invalid);
            assertEquals(2, result.exitCode(), result.stderr());
            assertEquals("", result.stdout());
            assertFalse(result.stderr().isBlank());
        }
    }

    @Test
    void demoMakesExpectedBreakingBehaviorPartOfItsSuccess() {
        var result = run("--demo", FIXTURE);
        assertEquals(0, result.exitCode(), result.stderr());
        assertEquals("", result.stderr());
        assertEquals(
                "Baseline: compatible (expected)\n"
                        + "Breaking: incompatible (expected)\n"
                        + "  field 'id' type mismatch: expected STRING, got INTEGER\n"
                        + "  field 'price' is missing\n"
                        + "Additive: compatible (expected)\n"
                        + "Recovery: compatible (expected)\n",
                result.stdout().replace("\r\n", "\n"));
    }

    private static Result run(String... arguments) {
        var standard = new ByteArrayOutputStream();
        var error = new ByteArrayOutputStream();
        int code;
        try (var output = new PrintStream(standard, false, StandardCharsets.UTF_8);
             var diagnostics = new PrintStream(error, false, StandardCharsets.UTF_8)) {
            code = Main.run(arguments, output, diagnostics);
        }
        return new Result(code, standard.toString(StandardCharsets.UTF_8), error.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String stdout, String stderr) {}
}
