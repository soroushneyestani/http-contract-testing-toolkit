# Third-party references and tools

Contract Testing Toolkit includes original Java source, synthetic fixtures and documentation under the accompanying MIT license. The source ZIP contains no third-party JARs, JDK distributions or source copies. [Project license](LICENSE)

| Reference or tool | Usage and applicable terms |
| --- | --- |
| Jackson Core 2.21.8 | Runtime streaming JSON parser. The separately downloaded Maven Central JAR retains its Apache License 2.0, `META-INF/NOTICE` and the embedded FastDoubleParser and Schubfach notices. [Upstream license](https://github.com/FasterXML/jackson-core/blob/jackson-core-2.21.8/LICENSE) · [Upstream notice](https://github.com/FasterXML/jackson-core/blob/jackson-core-2.21.8/src/main/resources/META-INF/jackson-core-NOTICE) |
| JUnit Platform Console Standalone 6.1.3 | Test-only tool downloaded from Maven Central. The JAR retains the Eclipse Public License 2.0 and its bundled components' license and notice files. [JUnit license](https://github.com/junit-team/junit-framework/blob/r6.1.3/LICENSE.md) · [JUnit documentation](https://docs.junit.org/6.1.3/running-tests/console-launcher.html) |
| Eclipse Temurin and PowerShell | Separately installed build and runtime tools. Use their supplied notices. [Eclipse Temurin](https://adoptium.net/temurin/) · [PowerShell license](https://github.com/PowerShell/PowerShell/blob/master/LICENSE.txt) |
| GitHub Actions and Gitleaks | The workflow fetches pinned checkout, setup-java, upload-artifact, setup-python and setup-go actions. The Linux job runs pinned Gitleaks. These tools remain separately distributed. [CI workflow](.github/workflows/ci.yml) · [Gitleaks license](https://github.com/gitleaks/gitleaks/blob/v8.30.1/LICENSE) |
| Pact | Design reference for consumer expectations and provider verification. This project defines its own v1 TSV format and matching rules. [Pact documentation](https://docs.pact.io/getting_started/how_pact_works) |

The verification script checks the JUnit and Jackson SHA-256 digests before loading either JAR. Distribute those dependencies under their own terms if you package binaries separately.
