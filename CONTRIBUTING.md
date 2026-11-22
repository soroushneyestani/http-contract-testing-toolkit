# Contributing

Use JDK 17 or newer and run `pwsh -NoProfile -File scripts/verify.ps1` from the repository root. Windows PowerShell 5.1 is supported through `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify.ps1`. The verifier compiles the Java 17 target with warnings treated as errors and writes JUnit XML under `bin/verification-*/reports/`.

Keep fixtures synthetic. Use loopback when a test needs actual HTTP behavior. When changing an interface, update the specification and cover the compatible case, the breaking case and relevant misuse cases. Keep the existing CLI forms and exit codes usable by their callers. Include the command and outcome when recording a check. [Verification contract](docs/SPEC.md)
