[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$env:JAVA_HOME = Resolve-JavaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$summaryPath = Join-Path $root 'validation\reports\unit-test-results.json'
if (Test-Path -LiteralPath $summaryPath) { Remove-Item -LiteralPath $summaryPath -Force }
Push-Location $root
try {
    & (Join-Path $root 'gradlew.bat') --no-daemon testDebugUnitTest
    $exitCode = $LASTEXITCODE
} finally { Pop-Location }
$summary = Get-JUnitSummary -ResultsDirectory (Join-Path $root 'app\build\test-results\testDebugUnitTest') -Suite 'unit'
Save-TestSummary -Summary $summary -Path $summaryPath
if ($exitCode -ne 0) { throw "Unit tests failed with exit code $exitCode." }
if ($summary.status -ne 'PASS') { throw "Unit tests did not produce passing results (status $($summary.status))." }
Write-Host "Unit tests passed. HTML report: $(Join-Path $root 'app\build\reports\tests\testDebugUnitTest\index.html')"
