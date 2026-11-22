package toolkit;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/** Command-line entry point for declared and observed contract compatibility. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream stdout, PrintStream stderr) {
        try {
            if (args.length == 3 && args[0].equals("--http")) {
                return report(HttpVerification.verify(readContract(args[1]), readOrigin(args[2])), stdout);
            }
            if (args.length == 2 && args[0].equals("--demo")) {
                return demo(readContract(args[1]), stdout);
            }
            if (args.length == 2 && !args[0].equals("--http")) {
                return report(Compatibility.check(readContract(args[0]), readContract(args[1])), stdout);
            }
            stderr.println("Usage: java toolkit.Main <consumer.tsv> <provider.tsv>");
            stderr.println("       java toolkit.Main --http <consumer.tsv> <http://127.0.0.1:port>");
            stderr.println("       java toolkit.Main --demo <consumer.tsv>");
            return 2;
        } catch (IOException | IllegalArgumentException failure) {
            stderr.println("Cannot verify contract: " + failure.getMessage());
            return 2;
        }
    }

    private static int report(List<String> failures, PrintStream output) {
        if (failures.isEmpty()) {
            output.println("Compatible.");
            return 0;
        }
        output.println("Incompatible.");
        failures.forEach(output::println);
        return 1;
    }

    private static int demo(Contract consumer, PrintStream output) throws IOException {
        var scenarios = List.of(
                new Scenario("Baseline", SampleProvider.State.BASELINE, true),
                new Scenario("Breaking", SampleProvider.State.BREAKING, false),
                new Scenario("Additive", SampleProvider.State.ADDITIVE, true),
                new Scenario("Recovery", SampleProvider.State.BASELINE, true));
        boolean correct = true;
        for (Scenario scenario : scenarios) {
            try (var provider = SampleProvider.start(scenario.state())) {
                var findings = HttpVerification.verify(consumer, provider.origin());
                boolean passes = findings.isEmpty();
                String observed = passes ? "compatible" : "incompatible";
                boolean asExpected = passes == scenario.compatible();
                output.println(scenario.name() + ": " + observed
                        + (asExpected ? " (expected)" : " (unexpected)"));
                findings.forEach(finding -> output.println("  " + finding));
                correct &= asExpected;
            }
        }
        return correct ? 0 : 1;
    }

    private static URI readOrigin(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid HTTP origin");
        }
    }

    private static Contract readContract(String source) {
        try {
            return Contract.read(Path.of(source));
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalArgumentException(source + ": " + failure.getMessage(), failure);
        }
    }

    private record Scenario(String name, SampleProvider.State state, boolean compatible) {}
}
