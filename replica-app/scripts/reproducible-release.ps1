<#
.SYNOPSIS
    Builds the release twice from clean checkouts, signs it, and records what produced it.

.DESCRIPTION
    Two unsigned builds proving they hash the same says the build is deterministic. It says
    nothing about what a user installs, so this also signs the artifact, verifies the signature
    with apksigner, and writes a provenance record naming everything that fed the build: the
    commit and whether the tree was dirty, the machine, the toolchain versions, the SDK, the
    dependency-verification state, the exact invocation, and the hashes and signer of the result.

    A mismatch keeps both unsigned APKs so they can be handed straight to diffoscope.
#>
[CmdletBinding()]
param(
    [string]$SourceRoot,
    [string]$OutputRoot = (Join-Path ([System.IO.Path]::GetTempPath()) ("nono-repro-" + [Guid]::NewGuid().ToString('N'))),
    [long]$SourceDateEpoch = 0,
    # Skips signing so a machine without the keystore can still check determinism.
    [switch]$SkipSigning
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

function Get-CatalogVersion([string]$catalog, [string]$key) {
    $match = Select-String -Path $catalog -Pattern "^\s*$key\s*=\s*`"([^`"]+)`"" | Select-Object -First 1
    if ($match) { $match.Matches[0].Groups[1].Value } else { $null }
}

<#
.SYNOPSIS
    Runs a native command and returns its combined output as plain strings.

.DESCRIPTION
    Common.ps1 spells out why 2>&1 cannot be used here: under Windows PowerShell 5.1 a redirected
    native stderr line becomes an ErrorRecord, and this script sets $ErrorActionPreference = Stop,
    which turns that into a terminating error. java -version writes its banner to stderr, and
    apksigner on JDK 21 writes restricted-method warnings there, so either would abort the run
    after both builds had already been paid for. Redirecting through files avoids the pipeline.
#>
function Invoke-NativeCapture {
    param([Parameter(Mandatory = $true)][string]$FilePath, [string[]]$Arguments = @())
    $out = [System.IO.Path]::GetTempFileName()
    $err = [System.IO.Path]::GetTempFileName()
    try {
        $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments -NoNewWindow -Wait -PassThru `
            -RedirectStandardOutput $out -RedirectStandardError $err
        $lines = @()
        $lines += @(Get-Content -LiteralPath $out -ErrorAction SilentlyContinue)
        $lines += @(Get-Content -LiteralPath $err -ErrorAction SilentlyContinue)
        [pscustomobject]@{ ExitCode = $process.ExitCode; Lines = @($lines | Where-Object { $null -ne $_ }) }
    } finally {
        Remove-Item -LiteralPath $out, $err -Force -ErrorAction SilentlyContinue
    }
}

function Get-LatestBuildTools {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $dir = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (-not $dir) { throw 'No Android build-tools found. Install one through the SDK manager.' }
    $dir
}

Ensure-Directory $OutputRoot
$checkouts = @()
$hashes = @()
$unsignedApks = @()

$catalog = Join-Path $root 'gradle\libs.versions.toml'
$commit = (& git -C $root rev-parse HEAD).Trim()
$treeState = (& git -C $root status --porcelain)
$buildTools = Get-LatestBuildTools

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

    # The signing credentials must not vary between the two builds, and the unsigned artifact is
    # what is compared, so the copy builds without them.
    Remove-Item -LiteralPath (Join-Path $checkout 'keystore.properties') -Force -ErrorAction SilentlyContinue

    Push-Location $checkout
    try {
        # The copies build without credentials on purpose: the unsigned artifact is what is
        # compared, and signing must not vary between the two.
        & (Join-Path $checkout 'gradlew.bat') --no-daemon --max-workers=1 assemble --console=plain -PallowUnsignedRelease=true
        if ($LASTEXITCODE -ne 0) { throw "Release build failed in checkout $index." }
    } finally {
        Pop-Location
    }
    $apk = Join-Path $checkout 'app\build\outputs\apk\release\app-release-unsigned.apk'
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "Missing unsigned release APK for checkout $index." }
    $unsignedApks += $apk
    $hashes += (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
}

$reproducible = ($hashes.Count -eq 2 -and $hashes[0] -eq $hashes[1])

$signed = $null
$signerSha256 = $null
$signedHash = $null
if ($reproducible -and -not $SkipSigning) {
    $keystoreProperties = Join-Path $root 'keystore.properties'
    if (-not (Test-Path -LiteralPath $keystoreProperties -PathType Leaf)) {
        throw "Release signing is not configured: $keystoreProperties is missing. Pass -SkipSigning to check determinism only."
    }
    $props = @{}
    Get-Content -LiteralPath $keystoreProperties | ForEach-Object {
        if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $props[$Matches[1]] = $Matches[2] }
    }

    $signed = Join-Path $OutputRoot 'NoNo-release-signed.apk'
    Copy-Item -LiteralPath $unsignedApks[0] -Destination $signed -Force
    $apksigner = Join-Path $buildTools.FullName 'apksigner.bat'

    & $apksigner sign --ks $props['storeFile'] --ks-pass "pass:$($props['storePassword'])" `
        --ks-key-alias $props['keyAlias'] --key-pass "pass:$($props['keyPassword'])" `
        --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true $signed
    if ($LASTEXITCODE -ne 0) { throw 'apksigner sign failed.' }

    $verify = Invoke-NativeCapture $apksigner @('verify', '--print-certs', '--verbose', $signed)
    if ($verify.ExitCode -ne 0) { throw "apksigner verify failed:`n$($verify.Lines -join [Environment]::NewLine)" }
    # apksigner labels this "Signer #1" for v1/v2 and "V3.0 Signer" for a v3-only signature.
    $certLine = $verify.Lines | Select-String -Pattern 'Signer.*certificate SHA-256 digest: ([0-9a-f]+)' | Select-Object -First 1
    if (-not $certLine) { throw "apksigner verify printed no signer certificate:`n$($verify.Lines -join [Environment]::NewLine)" }
    $signerSha256 = $certLine.Matches[0].Groups[1].Value
    $signedHash = (Get-FileHash -LiteralPath $signed -Algorithm SHA256).Hash.ToLowerInvariant()

    # An artifact that says it is debuggable is not a release, whatever it is signed with.
    $manifestDump = Invoke-NativeCapture (Join-Path $buildTools.FullName 'aapt2.exe') @('dump', 'badging', $signed)
    if ($manifestDump.Lines -match 'application-debuggable') { throw 'The signed release APK is debuggable.' }
}

$provenance = [ordered]@{
    generated_at_utc = (Get-Date).ToUniversalTime().ToString('o')
    source = [ordered]@{
        root = $root
        commit = $commit
        tree_dirty = [bool]$treeState
        dirty_paths = @($treeState)
        source_date_epoch = $SourceDateEpoch
    }
    machine = [ordered]@{
        os = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
        architecture = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
        powershell = $PSVersionTable.PSVersion.ToString()
    }
    toolchain = [ordered]@{
        java_home = $env:JAVA_HOME
        java_version = (Invoke-NativeCapture (Join-Path $env:JAVA_HOME 'bin\java.exe') @('-version')).Lines |
            Select-Object -First 1
        gradle = (Get-Content (Join-Path $root 'gradle\wrapper\gradle-wrapper.properties') |
            Select-String 'gradle-([0-9.]+)-bin' | ForEach-Object { $_.Matches[0].Groups[1].Value })
        agp = Get-CatalogVersion $catalog 'agp'
        kotlin = Get-CatalogVersion $catalog 'kotlin'
        ksp = Get-CatalogVersion $catalog 'ksp'
        build_tools = $buildTools.Name
        compile_sdk = (Select-String -Path (Join-Path $root 'app\build.gradle') -Pattern 'compileSdk\s+(\d+)' |
            Select-Object -First 1).Matches[0].Groups[1].Value
    }
    dependency_verification = [ordered]@{
        metadata_present = Test-Path -LiteralPath (Join-Path $root 'gradle\verification-metadata.xml')
        verified_components = @(Select-String -Path (Join-Path $root 'gradle\verification-metadata.xml') -Pattern '<component ' -ErrorAction SilentlyContinue).Count
    }
    invocation = [ordered]@{
        command = 'gradlew.bat --no-daemon --max-workers=1 assemble -PallowUnsignedRelease=true'
        checkouts = $checkouts
    }
    artifact = [ordered]@{
        unsigned_path = 'app/build/outputs/apk/release/app-release-unsigned.apk'
        unsigned_hashes = $hashes
        reproducible = $reproducible
        signed_sha256 = $signedHash
        signer_certificate_sha256 = $signerSha256
        signed_path = $signed
        signing_skipped = [bool]$SkipSigning
    }
}

$resultPath = Join-Path $OutputRoot 'reproducibility.json'
$provenance | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $resultPath -Encoding utf8

if (-not $reproducible) {
    throw "Release hashes differ. Both unsigned APKs were kept for diffoscope:`n  $($unsignedApks[0])`n  $($unsignedApks[1])`nProvenance: $resultPath"
}

if ($signed) {
    # One build, one artifact. Leaving older APKs beside the current one makes it impossible to
    # tell from the directory which binary SHA256SUMS.txt describes.
    $versionName = (Get-Content -LiteralPath (Join-Path $checkouts[0] 'app\build\outputs\apk\release\output-metadata.json') -Raw |
        ConvertFrom-Json).elements[0].versionName
    $dist = Join-Path $root 'dist'
    Ensure-Directory $dist
    Get-ChildItem -LiteralPath $dist -Filter '*.apk' | Remove-Item -Force
    $distApkName = "NoNo-v$versionName.apk"
    Copy-Item -LiteralPath $signed -Destination (Join-Path $dist $distApkName) -Force
    [System.IO.File]::WriteAllText(
        (Join-Path $dist 'SHA256SUMS.txt'),
        "$($signedHash.ToUpperInvariant())  $distApkName`n",
        (New-Object System.Text.UTF8Encoding($false))
    )
    Copy-Item -LiteralPath $resultPath -Destination (Join-Path $dist "reproducibility-$versionName.json") -Force
}

Write-Host "Reproducible unsigned release: $($hashes[0])"
if ($signed) {
    Write-Host "Signed artifact: $signed"
    Write-Host "Signed SHA-256:  $signedHash"
    Write-Host "Signer SHA-256:  $signerSha256"
}
Write-Host "Provenance: $resultPath"
