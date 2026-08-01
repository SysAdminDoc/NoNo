[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$env:JAVA_HOME = Resolve-JavaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Push-Location $root
try {
    & (Join-Path $root 'gradlew.bat') --no-daemon lintDebug
    if ($LASTEXITCODE -ne 0) { throw "Android lint failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }
Write-Host "Android lint passed. HTML report: $(Join-Path $root 'app\build\reports\lint-results-debug.html')"
