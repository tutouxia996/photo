# Download China OSM PBF (Geofabrik) for local railway routing
# Usage:
#   .\scripts\download-china-osm.ps1
#   .\scripts\download-china-osm.ps1 -OutDir "D:\uploadPath\album\osm"

param(
    [string]$OutDir = "D:\uploadPath\album\osm"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$url = "https://download.geofabrik.de/asia/china-latest.osm.pbf"
$outFile = Join-Path $OutDir "china-latest.osm.pbf"
$tmpFile = "$outFile.partial"
$indexFile = Join-Path $OutDir "china-railway-index.bin.gz"

Write-Host "URL: $url"
Write-Host "Out: $outFile"
Write-Host "Size ~1.5GB, please wait..."

if (Test-Path -LiteralPath $indexFile) {
    Write-Host "Old index found. Delete it after download to rebuild:"
    Write-Host "  $indexFile"
}

$ProgressPreference = "SilentlyContinue"
Invoke-WebRequest -Uri $url -OutFile $tmpFile -UseBasicParsing
Move-Item -LiteralPath $tmpFile -Destination $outFile -Force

$len = (Get-Item -LiteralPath $outFile).Length
Write-Host ("Done. Size {0:N1} MB" -f ($len / 1MB))
Write-Host "Next: restart backend. First rail snap builds china-railway-index.bin.gz."
