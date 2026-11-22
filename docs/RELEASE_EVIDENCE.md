# 0.1.0 verification

The verifier compiles source and tests with `javac --release 17 -Xlint:all -Werror`. Windows PowerShell with Eclipse Temurin 25.0.5+8 passed all 29 JUnit tests with zero failures or skips. The verifier also exercised the legacy declaration examples and the four-state loopback demo. [Run verification](../README.md#verify) · [Verification script](../scripts/verify.ps1)

| Scenario | Observed outcome |
| --- | --- |
| Baseline | HTTP 200 includes the required string `id` and integer `price`. The consumer's numeric expectation passes even when optional `currency` is absent. |
| Breaking | HTTP 200 changes `id` to an integer and removes `price`. Both findings are printed. The HTTP check exits 1 and the demo recognizes this as the expected test result. |
| Additive | The provider adds optional `currency` as a string and an unconstrained `label`. Compatibility passes. |
| Recovery | A fresh provider serves the baseline state again. Compatibility passes. |

The tests also check absence versus explicit null, numeric narrowing, malformed fixtures and JSON, an unexpected status, a redirect, a failed connection, invalid origins and the response size limit. All provider cases run on IPv4 loopback with synthetic data. [Contract rules](SPEC.md) · [HTTP tests](../src/test/java/toolkit/HttpContractTest.java) · [CLI tests](../src/test/java/toolkit/HttpCliTest.java)

The Windows and Ubuntu jobs use read-only contents access and save JUnit XML and the demonstration transcript. Ubuntu also verifies a source ZIP from the checked-out commit and scans reachable history. The GitHub Release records the exact source SHA, CI results and archive checksum. [CI workflow](../.github/workflows/ci.yml) · [Release notes](releases/v0.1.0.md)

The versioned format models one interaction. The observed HTTP mode verifies a GET on loopback, exact status, JSON syntax and top-level field types. It leaves nested schema constraints, value matching and broader protocol semantics to another design. [HTTP verification decision](adr/0002-local-http-verification.md)
