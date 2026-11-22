[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsDirectory = Join-Path $projectRoot 'bin/tools'
$portableJdk = Join-Path $toolsDirectory 'jdk-25.0.5+8'
if (-not $JavaHome -and (Test-Path -LiteralPath $portableJdk -PathType Container)) {
    $JavaHome = $portableJdk
}
$suffix = if ([Environment]::OSVersion.Platform -eq 'Win32NT') { '.exe' } else { '' }
if ($JavaHome) {
    $java = Join-Path $JavaHome "bin/java$suffix"
    $javac = Join-Path $JavaHome "bin/javac$suffix"
    if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or
        -not (Test-Path -LiteralPath $javac -PathType Leaf)) {
        throw 'JAVA_HOME or -JavaHome must point to a runnable JDK.'
    }
} else {
    $java = (Get-Command java -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
    $javac = (Get-Command javac -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
}

function Get-VerifiedJar([string]$Coordinate, [string]$Digest) {
    $name = $Coordinate.Split('/')[-1]
    $file = Join-Path $toolsDirectory $name
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        New-Item -ItemType Directory -Path $toolsDirectory -Force | Out-Null
        $download = Join-Path $toolsDirectory ("$name-" + [Guid]::NewGuid().ToString('N'))
        try {
            Invoke-WebRequest -UseBasicParsing -Uri "https://repo.maven.apache.org/maven2/$Coordinate" -OutFile $download
            if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash -ne $Digest) {
                throw "$name checksum did not match the pinned digest."
            }
            try {
                [IO.File]::Move($download, $file)
            } catch [IO.IOException] {
                if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw }
            }
        } finally {
            [IO.File]::Delete($download)
        }
    }
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $Digest) {
        throw "$name checksum did not match the pinned digest at $file."
    }
    return $file
}

$junit = Get-VerifiedJar 'org/junit/platform/junit-platform-console-standalone/6.1.3/junit-platform-console-standalone-6.1.3.jar' 'E62B96AC475DBCDE8599EA905D088F65D90778F86E259B856A49FA5C4EA256EC'
$jackson = Get-VerifiedJar 'com/fasterxml/jackson/core/jackson-core/2.21.8/jackson-core-2.21.8.jar' '604EF7765013DAF5CDE192E40BC4C8412C4CA93F4AAF7BF8E9A62FAD1E1026BB'

$runDirectory = Join-Path $projectRoot ('bin/verification-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $runDirectory 'classes'
$testClasses = Join-Path $runDirectory 'test-classes'
$reports = Join-Path $runDirectory 'reports'
New-Item -ItemType Directory -Path $classes, $testClasses, $reports -Force | Out-Null
$mainSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/main/java') -File -Recurse -Filter '*.java')
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src/test/java') -File -Recurse -Filter '*.java')
if ($mainSources.Count -eq 0 -or $testSources.Count -eq 0) {
    throw 'Application sources and JUnit tests are required.'
}
$separator = [IO.Path]::PathSeparator
$appClasspath = $classes, $jackson -join $separator
$compileClasspath = $classes, $jackson, $junit -join $separator
$runtimeClasspath = $classes, $testClasses, $jackson -join $separator

Push-Location -LiteralPath $projectRoot
try {
    & $javac --release 17 -encoding UTF-8 -Xlint:all -Werror -cp $jackson -d $classes @($mainSources.FullName)
    if ($LASTEXITCODE -ne 0) { throw 'Application compilation failed.' }
    & $javac --release 17 -encoding UTF-8 -Xlint:all -Werror -cp $compileClasspath -d $testClasses @($testSources.FullName)
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    & $java -jar $junit execute --disable-ansi-colors --fail-if-no-tests --scan-class-path --include-engine junit-jupiter --class-path $runtimeClasspath --reports-dir $reports
    if ($LASTEXITCODE -ne 0) { throw 'JUnit tests failed.' }
    & $java -cp $appClasspath toolkit.Main fixtures/consumer.tsv fixtures/provider-compatible.tsv
    if ($LASTEXITCODE -ne 0) { throw 'Compatible provider fixture failed.' }
    & $java -cp $appClasspath toolkit.Main fixtures/consumer.tsv fixtures/provider-breaking.tsv
    if ($LASTEXITCODE -ne 1) { throw 'Breaking provider fixture did not return exit 1.' }
    & $java -cp $appClasspath toolkit.Main --demo fixtures/consumer-http.tsv |
        Tee-Object -FilePath (Join-Path $reports 'observed-demo.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Observed provider scenarios did not match the expected outcomes.' }
    Write-Output "Verified declared and observed contracts. Reports: $reports"
} finally {
    Pop-Location
}

exit 0
