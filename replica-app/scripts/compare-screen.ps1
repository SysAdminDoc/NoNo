[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ScreenId,
    [Parameter(Mandatory = $true)][string]$Baseline,
    [Parameter(Mandatory = $true)][string]$Current,
    [Parameter(Mandatory = $true)][double]$Threshold,
    [string]$Mask = '',
    [string]$OutputDirectory = ''
)

. (Join-Path $PSScriptRoot 'Common.ps1')
if (-not (Test-Path -LiteralPath $Baseline -PathType Leaf)) { throw "Baseline screenshot not found: $Baseline" }
if (-not (Test-Path -LiteralPath $Current -PathType Leaf)) { throw "Replica screenshot not found: $Current" }
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) { $OutputDirectory = Join-Path (Get-ProjectRoot) 'validation\diffs' }
Ensure-Directory $OutputDirectory
$python = Resolve-Python
& $python -c 'import PIL, numpy' 2>$null
if ($LASTEXITCODE -ne 0) { throw "Pillow and NumPy are required. Install them with: $python -m pip install -r scripts/requirements.txt" }

$args = @(
    (Join-Path $PSScriptRoot 'compare_images.py'),
    '--baseline', [System.IO.Path]::GetFullPath($Baseline),
    '--current', [System.IO.Path]::GetFullPath($Current),
    '--output-dir', [System.IO.Path]::GetFullPath($OutputDirectory),
    '--screen-id', $ScreenId,
    '--threshold', $Threshold.ToString([System.Globalization.CultureInfo]::InvariantCulture)
)
if (-not [string]::IsNullOrWhiteSpace($Mask)) {
    if (-not (Test-Path -LiteralPath $Mask -PathType Leaf)) { throw "Mask file not found: $Mask" }
    $args += @('--mask', [System.IO.Path]::GetFullPath($Mask))
}
& $python @args
$result = $LASTEXITCODE
$overlaySource = Join-Path $OutputDirectory "$ScreenId-overlay-50.png"
$overlayDirectory = Join-Path (Get-ProjectRoot) 'validation\overlays'
Ensure-Directory $overlayDirectory
if (Test-Path -LiteralPath $overlaySource -PathType Leaf) {
    Copy-Item -LiteralPath $overlaySource -Destination (Join-Path $overlayDirectory "$ScreenId-overlay-50.png") -Force
}
$metrics = Join-Path $OutputDirectory "$ScreenId-metrics.json"
if (Test-Path -LiteralPath $metrics) { Get-Content -LiteralPath $metrics }
if ($result -ne 0) { throw "Visual comparison failed for $ScreenId with result code $result. See $metrics" }
