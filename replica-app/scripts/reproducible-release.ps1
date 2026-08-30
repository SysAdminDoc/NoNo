[CmdletBinding()]
param(
    [string]$SourceRoot,
    [string]$OutputRoot = (Join-Path ([System.IO.Path]::GetTempPath()) ("nono-repro-" + [Guid]::NewGuid().ToString('N'))),
    [long]$SourceDateEpoch = 0
)

. (Join-Path $PSScriptRoot 'Common.ps1')
$ErrorActionPreference = 'Stop'
$root = if ([string]::IsNullOrWhiteSpace($SourceRoot)) { Get-ProjectRoot } else { [System.IO.Path]::GetFullPath($SourceRoot) }
if (-not (Test-Path -LiteralPath $root -PathType Container)) { throw "Source root does not exist: $root" }

$env:JAVA_HOME = Resolve-JavaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
if ($SourceDateEpoch -le 0) {
    $SourceDateEpoch = [long](& git -C $root log -1 --format=%ct)
    if ($LASTEXITCODE -ne 0 -or $SourceDateEpoch -le 0) { throw 'Unable to determine SOURCE_DATE_EPOCH from git.' }
}
$env:SOURCE_DATE_EPOCH = $SourceDateEpoch.ToString()

Ensure-Directory $OutputRoot
$checkouts = @()
$hashes = @()
try {
    foreach ($index in 1, 2) {
        $checkout = Join-Path $OutputRoot ("checkout-" + $index)
        $checkouts += $checkout
        if (Test-Path -LiteralPath $checkout) {
            Remove-Item -LiteralPath $checkout -Recurse -Force
        }
        Ensure-Directory $checkout
        $excluded = @(
            (Join-Path $root '.git'),
            (Join-Path $root '.gradle'),
            (Join-Path $root 'build'),
            (Join-Path $root 'app\build')
        )
        robocopy $root $checkout /E /COPY:DAT /DCOPY:DAT /R:1 /W:1 /XJ /NFL /NDL /NJH /NJS /NP /XD $excluded | Out-Null
        if ($LASTEXITCODE -ge 8) { throw "Failed to copy clean checkout $index (robocopy $LASTEXITCODE)." }

        Push-Location $checkout
        try {
            & (Join-Path $checkout 'gradlew.bat') --no-daemon --max-workers=1 assembleRelease --console=plain
            if ($LASTEXITCODE -ne 0) { throw "Release build failed in checkout $index." }
        } finally {
            Pop-Location
        }
        $apk = Join-Path $checkout 'app\build\outputs\apk\release\app-release-unsigned.apk'
        if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "Missing unsigned release APK for checkout $index." }
        $hashes += (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    }

    $result = [ordered]@{
        source = $root
        source_date_epoch = $SourceDateEpoch
        java_home = $env:JAVA_HOME
        artifact = 'app/build/outputs/apk/release/app-release-unsigned.apk'
        hashes = $hashes
        reproducible = ($hashes.Count -eq 2 -and $hashes[0] -eq $hashes[1])
    }
    $resultPath = Join-Path $OutputRoot 'reproducibility.json'
    $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $resultPath -Encoding utf8
    if (-not $result.reproducible) { throw "Release hashes differ; see $resultPath." }
    Write-Host "Reproducible release verified: $($hashes[0])"
} finally {
    # Keep the two build directories and JSON report outside the repository for audit review.
}
