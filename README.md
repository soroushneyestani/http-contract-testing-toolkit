# Contract Testing Toolkit

Contract Testing Toolkit is a lightweight Java toolkit for verifying the response contract of a single HTTP interaction. Compare the two sides' TSV declarations offline, or send a GET to a synthetic local provider and verify the response received over HTTP. Version v0.1.0 includes a demo of a breaking change, a compatible addition and recovery. [Documentation index](docs/README.md)

## Verify

Use JDK 17 or newer and PowerShell 7 or Windows PowerShell 5.1. The CI definition pins Eclipse Temurin `25.0.5+8`, and compilation targets Java 17. The verifier selects the JDK from `-JavaHome`, `JAVA_HOME`, the project-local portable JDK, or PATH. It obtains Jackson Core `2.21.8` and JUnit Console `6.1.3` from Maven Central if needed and checks their pinned SHA-256 digests before use. Jackson Core parses JSON at runtime. JUnit is used for tests. [Temurin downloads](https://adoptium.net/temurin/releases/) · [JUnit Console documentation](https://docs.junit.org/6.1.3/running-tests/console-launcher.html)

```powershell
pwsh -NoProfile -File scripts/verify.ps1
```

On Windows PowerShell 5.1, use `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify.ps1`. The script compiles the application and tests, runs JUnit, and exercises the CLI with synthetic fixtures. Each run writes classes under `bin/verification-*/` and stores the JUnit XML and `observed-demo.txt` in that run's `reports/` directory.

The verifier exits zero when the checks pass, including when an expected breaking case returns its expected nonzero CLI exit. Unexpected results, failed builds and failed tests fail the verifier.

## Compare declared contracts

From the repository root after a successful verification, set the application classpath. The examples assume that `java` is available on PATH. If you selected a JDK with `-JavaHome`, use its `bin/java` executable for the CLI commands.

```powershell
$run = Get-ChildItem -LiteralPath bin -Directory -Filter 'verification-*' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
$classes = Join-Path $run.FullName 'classes'
$jackson = (Resolve-Path -LiteralPath 'bin/tools/jackson-core-2.21.8.jar').Path
$classpath = $classes + [IO.Path]::PathSeparator + $jackson

java -cp $classpath toolkit.Main fixtures/consumer.tsv fixtures/provider-compatible.tsv
$LASTEXITCODE # 0
java -cp $classpath toolkit.Main fixtures/consumer.tsv fixtures/provider-breaking.tsv
$LASTEXITCODE # 1
```

Both fixtures describe `GET /items`. The compatible declaration guarantees an integer `price` where the consumer accepts a number. The breaking declaration changes the type of `id` and removes `price`.

## Verify an observed HTTP response

Run the complete local demonstration with the same classpath. The demo checks baseline, breaking, additive and recovery responses in sequence.

```powershell
java -cp $classpath toolkit.Main --demo fixtures/consumer-http.tsv
```

To inspect one provider state, run `java -cp $classpath toolkit.SampleProvider baseline` in one shell. Leave it running and copy the printed HTTP origin. In a second shell with the same classpath, run the verifier with that origin.

```powershell
$origin = Read-Host 'Paste the loopback HTTP origin printed by SampleProvider'
java -cp $classpath toolkit.Main --http fixtures/consumer-http.tsv $origin
$LASTEXITCODE # 0 for the baseline
```

Stop the sample provider with Ctrl+C. You can start it again in the `breaking` or `additive` state. Each invocation prints its own port. The HTTP verifier accepts only the explicit `http://127.0.0.1:<port>` origin and sends the contract's GET. It checks status and a UTF-8 JSON object, including the types of optional fields when present. [Verification contract](docs/SPEC.md)

| CLI exit | Meaning |
| --- | --- |
| `0` | Compatible response or declaration. The demo also returns zero when its expected outcomes are observed. |
| `1` | Incompatible response or declaration. Findings go to stdout. |
| `2` | Usage, input, or transport error. Diagnostics go to stderr. |

The HTTP mode verifies one GET on IPv4 loopback and top-level fields of a UTF-8 JSON object. The contract format is TSV. Nested schemas, deployed endpoints, native Pact contract files and Broker workflows are outside this scope. Pact is an independent design reference. The toolkit demonstrates selected contract-testing techniques without claiming complete consumer-driven contract testing coverage. [Design decision](docs/adr/0002-local-http-verification.md)

## Source package

The release source ZIP contains code, tests, synthetic fixtures and documentation. Compare the ZIP SHA-256 with its `SHA256SUMS.txt` before extracting it and running the verifier. In a Git checkout, pass the full reviewed commit ID to the packager with Python and Git available.

```powershell
$commit = Read-Host 'Full reviewed Git commit ID'
python scripts/package_source.py $commit
```

The script writes the ZIP and manifest under `bin/release/v0.1.0/<commit>/` and checks the ZIP entries against the commit. [Release notes](docs/releases/v0.1.0.md) · [Release evidence](docs/RELEASE_EVIDENCE.md) · [License](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md)
