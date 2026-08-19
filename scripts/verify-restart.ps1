$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$e2eRoot = Join-Path $repositoryRoot 'e2e'
$gradleWrapper = Join-Path $e2eRoot 'gradlew.bat'

& $gradleWrapper -p $e2eRoot plugwrightClean
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $gradleWrapper -p $e2eRoot plugwrightTest -PtestFiles=restart-seed -PpreserveState=true
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $gradleWrapper -p $e2eRoot plugwrightTest -PtestFiles=restart-verify -PpreserveState=true
exit $LASTEXITCODE
