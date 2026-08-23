# Album site one-click pack: frontend build -> static -> maven -> desktop deploy
# Usage: double-click pack-album.bat in repo root

param(
    [switch]$SkipFrontend,
    [switch]$SkipMaven,
    [switch]$SkipDeploy,
    [switch]$KeepDeployLib,
    [switch]$NoPause,
    [string]$DeployDir = ""
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Write-Ok([string]$msg) {
    Write-Host "[OK] $msg" -ForegroundColor Green
}

function Write-Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
}

function Ensure-Command([string]$name, [string]$hint) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Command not found: $name. $hint"
    }
}

function Invoke-Checked {
    param(
        [string]$FilePath,
        [string[]]$ArgumentList
    )
    $display = if ($ArgumentList) { "$FilePath $($ArgumentList -join ' ')" } else { $FilePath }
    Write-Host "    $display"
    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed (exit $LASTEXITCODE): $display"
    }
}

function Remove-DeployJars([string]$dir) {
    $jars = Get-ChildItem -LiteralPath $dir -Filter "*.jar" -File -ErrorAction SilentlyContinue
    foreach ($jar in $jars) {
        try {
            Remove-Item -LiteralPath $jar.FullName -Force
        } catch {
            throw "Cannot update $($jar.Name): file is locked. Stop album site (close start.bat window) and run pack again."
        }
    }
}

function Test-DeployBusinessJar([string]$dir) {
    $bizJar = Get-ChildItem -LiteralPath $dir -Filter "sq-business-*.jar" -File -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $bizJar) {
        throw "Deploy verification failed: sq-business jar missing in $dir"
    }
    $listing = & jar tf $bizJar.FullName
    if ($listing -notcontains "com/sq/bus/service/IVideoProxyService.class") {
        throw "Deploy verification failed: $($bizJar.Name) is outdated (missing IVideoProxyService). Stop running server and pack again."
    }
}

function Copy-DeployJar([string]$source, [string]$destDir) {
    $name = Split-Path -Leaf $source
    $dest = Join-Path $destDir $name
    try {
        Copy-Item -LiteralPath $source -Destination $dest -Force
    } catch {
        throw "Cannot copy $name to deploy lib: file may be locked. Stop album site and pack again."
    }
}

$root = Split-Path -Parent $PSScriptRoot
$uiDir = Join-Path $root "sq-ui-vue3"
$serverDir = Join-Path $root "sq-admin-server"
$staticDir = Join-Path $serverDir "src\main\resources\static"
$distDir = Join-Path $uiDir "dist"
$targetDir = Join-Path $serverDir "target"
$jarPath = Join-Path $targetDir "sq-admin-server.jar"
$libDir = Join-Path $targetDir "lib"

if (-not $DeployDir) {
    $desktop = [Environment]::GetFolderPath("Desktop")
    $DeployDir = Join-Path $desktop ([char]0x76f8 + [char]0x518c + [char]0x7f51 + [char]0x7ad9 + "-" + [char]0x90e8 + [char]0x7f72 + [char]0x5305)
}
$deployLib = Join-Path $DeployDir "lib"
$deployBin = Join-Path $DeployDir "bin"
$templateBin = Join-Path $root "scripts\deploy-template\bin"

Write-Host "Album site pack" -ForegroundColor Yellow
Write-Host "Repo: $root"
Write-Host "Deploy: $DeployDir"

try {
    Ensure-Command "java" "Install JDK 8+ and add java to PATH or JAVA_HOME"
    Ensure-Command "mvn" "Install Maven and add mvn to PATH"

    if (-not $SkipFrontend) {
        Write-Step "1/4 Build frontend (sq-ui-vue3)"
        Ensure-Command "node" "Install Node.js (18 LTS recommended)"

        $pkgManager = "npm"
        if (Test-Path (Join-Path $uiDir "pnpm-lock.yaml")) {
            if (Get-Command "pnpm" -ErrorAction SilentlyContinue) {
                $pkgManager = "pnpm"
            } else {
                Write-Host "    pnpm-lock.yaml found, pnpm missing, using npm"
            }
        }

        $nodeVer = (node -v) -replace '^v', ''
        $nodeMajor = [int]($nodeVer.Split('.')[0])
        $nodeOpts = @()
        if ($nodeMajor -ge 17) {
            $nodeOpts += "--openssl-legacy-provider"
            # Avoid vite.config.js.timestamp-*.mjs on Windows (EPERM when writing beside config)
            $nodeOpts += "--experimental-vm-modules"
        }
        if ($nodeOpts.Count -gt 0) {
            $env:NODE_OPTIONS = ($nodeOpts -join ' ')
            Write-Host "    Node $nodeVer : NODE_OPTIONS=$($env:NODE_OPTIONS)"
        }

        Get-ChildItem -LiteralPath $uiDir -Filter "vite.config*.timestamp-*" -File -ErrorAction SilentlyContinue |
            Remove-Item -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath (Join-Path $uiDir "node_modules\.vite") -Recurse -Force -ErrorAction SilentlyContinue

        Push-Location $uiDir
        try {
            if (-not (Test-Path (Join-Path $uiDir "node_modules"))) {
                Write-Host "    Installing dependencies..."
                if ($pkgManager -eq "pnpm") {
                    Invoke-Checked "pnpm" @("install", "--frozen-lockfile")
                } else {
                    if (Test-Path (Join-Path $uiDir "package-lock.json")) {
                        Invoke-Checked "npm" @("ci")
                    } else {
                        Invoke-Checked "npm" @("install")
                    }
                }
            }

            $buildOk = $false
            $lastBuildError = $null
            for ($attempt = 1; $attempt -le 3; $attempt++) {
                try {
                    if ($attempt -gt 1) {
                        Write-Host "    Retry frontend build ($attempt/3)..."
                        Start-Sleep -Seconds 2
                        Get-ChildItem -LiteralPath $uiDir -Filter "vite.config*.timestamp-*" -File -ErrorAction SilentlyContinue |
                            Remove-Item -Force -ErrorAction SilentlyContinue
                    }
                    if ($pkgManager -eq "pnpm") {
                        Invoke-Checked "pnpm" @("run", "build:prod")
                    } else {
                        Invoke-Checked "npm" @("run", "build:prod")
                    }
                    $buildOk = $true
                    break
                } catch {
                    $lastBuildError = $_
                    if ($attempt -eq 3) {
                        throw $lastBuildError
                    }
                }
            }
        } finally {
            Pop-Location
        }

        if (-not (Test-Path (Join-Path $distDir "index.html"))) {
            throw "Frontend build failed: missing $distDir\index.html"
        }
        Write-Ok "Frontend build done"
    } else {
        Write-Step "1/4 Skip frontend build"
        if (-not (Test-Path (Join-Path $distDir "index.html"))) {
            throw "dist/index.html missing. Build frontend first or remove -SkipFrontend"
        }
    }

    Write-Step "2/4 Copy dist to backend static"
    if (Test-Path $staticDir) {
        Remove-Item -LiteralPath $staticDir -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $staticDir | Out-Null
    Copy-Item -Recurse -Force (Join-Path $distDir "*") $staticDir
    Write-Ok "Synced dist -> static"

    if (-not $SkipMaven) {
        Write-Step "3/4 Maven package (mvn clean package -DskipTests)"
        Push-Location $root
        try {
            Invoke-Checked "mvn" @("clean", "package", "-DskipTests")
        } finally {
            Pop-Location
        }

        if (-not (Test-Path $jarPath)) {
            throw "Jar not found: $jarPath"
        }
        if (-not (Test-Path $libDir)) {
            throw "Lib dir not found: $libDir"
        }

        $jarList = & jar tf $jarPath
        $hasIndex = ($jarList | Where-Object { $_ -match '(^|/)static/index\.html$' }).Count -gt 0
        if (-not $hasIndex) {
            throw "Validation failed: static/index.html not found inside jar"
        }
        Write-Ok "Maven package done, static/index.html verified"
    } else {
        Write-Step "3/4 Skip Maven package"
        if (-not (Test-Path $jarPath)) {
            throw "Jar not found: $jarPath"
        }
    }

    if (-not $SkipDeploy) {
        Write-Step "4/4 Copy to deploy package"
        New-Item -ItemType Directory -Force -Path $deployLib | Out-Null

        if (-not $KeepDeployLib) {
            Remove-DeployJars $deployLib
            Write-Host "    Cleared old jars in deploy lib"
        }

        Copy-DeployJar $jarPath $deployLib
        Get-ChildItem -LiteralPath $libDir -Filter "*.jar" -File | ForEach-Object {
            Copy-DeployJar $_.FullName $deployLib
        }
        Test-DeployBusinessJar $deployLib

        if (Test-Path $templateBin) {
            New-Item -ItemType Directory -Force -Path $deployBin | Out-Null
            Get-ChildItem -LiteralPath $templateBin -Filter "*.bat" -File | ForEach-Object {
                $destBat = Join-Path $deployBin $_.Name
                if (-not (Test-Path $destBat)) {
                    Copy-Item -LiteralPath $_.FullName -Destination $destBat -Force
                    Write-Host "    Created $destBat"
                }
            }
        }

        $jarCount = (Get-ChildItem -LiteralPath $deployLib -Filter "*.jar" -File).Count
        Write-Ok "Copied to $deployLib ($jarCount jars)"
    } else {
        Write-Step "4/4 Skip deploy copy"
    }

    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host " Pack finished" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Output:"
    Write-Host "  jar: $jarPath"
    if (-not $SkipDeploy) {
        Write-Host "  deploy: $deployLib"
        Write-Host ""
        Write-Host "Next: run $deployBin\start.bat -> http://127.0.0.1:18080/#/login"
    }
}
catch {
    Write-Host ""
    Write-Fail $_.Exception.Message
    exit 1
}

if (-not $NoPause) {
    Read-Host "Press Enter to exit"
}
