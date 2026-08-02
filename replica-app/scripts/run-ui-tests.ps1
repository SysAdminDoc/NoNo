[CmdletBinding()]
param([string]$Serial = '')

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$target = Get-TargetSerial -Serial $Serial
$env:ANDROID_SERIAL = $target
$env:JAVA_HOME = Resolve-JavaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$summaryPath = Join-Path $root 'validation\reports\instrumentation-test-results.json'
if (Test-Path -LiteralPath $summaryPath) { Remove-Item -LiteralPath $summaryPath -Force }
Push-Location $root
try {
    # Force execution so the recorded evidence always describes this run.
    & (Join-Path $root 'gradlew.bat') --no-daemon connectedDebugAndroidTest --rerun-tasks
    $exitCode = $LASTEXITCODE
} finally { Pop-Location }
$summary = Get-JUnitSummary -ResultsDirectory (Join-Path $root 'app\build\outputs\androidTest-results\connected') -Suite 'instrumentation'
if ($exitCode -eq 0 -and $summary.tests -eq 0) {
    throw 'Gradle reported success but produced no instrumentation results. Refusing to record evidence for a suite that did not run.'
}
$summary.source = "$($summary.source) (device $target)"
Save-TestSummary -Summary $summary -Path $summaryPath
if ($exitCode -ne 0) { throw "Instrumented UI tests failed with exit code $exitCode." }
if ($summary.status -ne 'PASS') { throw "Instrumented UI tests did not produce passing results (status $($summary.status))." }
Write-Host "UI tests passed on $target. Report: $(Join-Path $root 'app\build\reports\androidTests\connected\debug\index.html')"
