param(
    [switch]$Rebuild,
    [string]$UserDir = (Join-Path $PSScriptRoot "starter-userdir"),
    [string]$JdkHome,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ExtraArgs
)

$ErrorActionPreference = "Stop"

$repoRoot = $PSScriptRoot
$launcherCandidates = @(
    (Join-Path $repoRoot "nbbuild\\netbeans\\bin\\netbeans64.exe"),
    (Join-Path $repoRoot "nbbuild\\netbeans\\bin\\netbeans.exe")
)
$netBeansInstallRoot = Join-Path $repoRoot "nbbuild\\netbeans"
$fullClusterMarker = Join-Path $netBeansInstallRoot "rust"
$installedClustersFile = Join-Path $netBeansInstallRoot "etc\\netbeans.clusters"
$mauzModuleProjectDir = Join-Path $repoRoot "o.mauz.netbeans.codex"
$mauzModuleBuildFile = Join-Path $mauzModuleProjectDir "build.xml"
$mauzModuleCluster = Join-Path $mauzModuleProjectDir "build\\cluster"
$mauzModuleClusterMarker = Join-Path $mauzModuleCluster "config\\Modules\\org-mauz-netbeans-codex.xml"

function Get-NetBeansLauncher {
    return $launcherCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}

function Get-AntCommand {
    return (Get-Command ant -ErrorAction Stop).Source
}

function Get-InstalledClusters {
    param(
        [string]$InstallRoot,
        [string]$ClustersFile
    )

    if (-not (Test-Path $ClustersFile)) {
        return @()
    }

    return Get-Content $ClustersFile |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and -not $_.StartsWith("#") } |
        ForEach-Object { Join-Path $InstallRoot $_ } |
        Where-Object { Test-Path $_ }
}

function Test-ProjectOutputsStale {
    param(
        [string]$ProjectDir,
        [string]$OutputMarker
    )

    if (-not (Test-Path $OutputMarker)) {
        return $true
    }

    $outputTimestamp = (Get-Item $OutputMarker).LastWriteTimeUtc
    $latestInput = Get-ChildItem -Path $ProjectDir -Recurse -File |
        Where-Object { $_.FullName -notmatch '\\build\\|\\dist\\|\\nbproject\\private\\' } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1

    return $latestInput -and $latestInput.LastWriteTimeUtc -gt $outputTimestamp
}

$launcher = Get-NetBeansLauncher

Push-Location $repoRoot
try {
    if ($Rebuild -or -not $launcher -or -not (Test-Path $fullClusterMarker)) {
        $ant = Get-AntCommand
        & $ant "-Dcluster.config=full" "build"
        if ($LASTEXITCODE -ne 0) {
            throw "Full NetBeans build failed."
        }
        $launcher = Get-NetBeansLauncher
    }

    if (-not $launcher) {
        throw "NetBeans launcher not found after build."
    }

    if ((Test-Path $mauzModuleBuildFile) -and ($Rebuild -or (Test-ProjectOutputsStale -ProjectDir $mauzModuleProjectDir -OutputMarker $mauzModuleClusterMarker))) {
        $ant = Get-AntCommand
        & $ant "-f" $mauzModuleBuildFile "build"
        if ($LASTEXITCODE -ne 0) {
            throw "MAUZ Codex module build failed."
        }
    }

    $null = New-Item -ItemType Directory -Force -Path $UserDir

    $startupArgs = @(
        "--userdir", $UserDir,
        "--fontsize", "13",
        "-J-Dnetbeans.system.font.family=Hermit Light",
        "-J-Dnetbeans.full.hack=true"
    )

    if (Test-Path $mauzModuleClusterMarker) {
        $installedClusters = Get-InstalledClusters -InstallRoot $netBeansInstallRoot -ClustersFile $installedClustersFile
        if ($installedClusters.Count -eq 0) {
            throw "Installed NetBeans clusters could not be resolved from $installedClustersFile."
        }

        $startupArgs = @("--clusters", (($installedClusters + $mauzModuleCluster) -join ';')) + $startupArgs
    }

    if ($JdkHome) {
        $startupArgs = @("--jdkhome", $JdkHome) + $startupArgs
    }

    if ($ExtraArgs) {
        $startupArgs += $ExtraArgs
    }

    & $launcher @startupArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
