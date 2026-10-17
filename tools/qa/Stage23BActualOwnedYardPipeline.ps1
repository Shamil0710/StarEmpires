param(
    [Parameter(Mandatory = $true)][int]$JourneyProcessId,
    [string]$JavaExecutable = 'C:/Users/Shalim/.jdks/temurin-17.0.20.1/bin/java.exe'
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $workspace
$purchased = 'target/stage23b-ordinary-station-purchase.s25.purchased.s25'
$classpath = 'target/qa;target/star-empires-1.0-SNAPSHOT-all.jar'

function Invoke-ActualProbe {
    param([string]$ProbeClass, [string]$LogName, [string[]]$JvmOptions, [string[]]$ProbeArguments)
    Write-Output "START $ProbeClass; log=target/$LogName"
    # Windows PowerShell treats native stderr as an error record. Retain the complete diagnostic,
    # then decide success from the actual process exit code.
    $ErrorActionPreference = 'Continue'
    & $JavaExecutable '-Xmx5g' @JvmOptions '-cp' $classpath $ProbeClass @ProbeArguments *> "target/$LogName"
    $probeExitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($probeExitCode -ne 0) {
        throw "$ProbeClass failed with exit code $probeExitCode; inspect target/$LogName; no success is claimed"
    }
    Write-Output "FINISHED $ProbeClass"
}

Write-Output "Waiting for actual station journey process $JourneyProcessId; no campaign ticks are advanced by this waiter"
while (Get-Process -Id $JourneyProcessId -ErrorAction SilentlyContinue) {
    Start-Sleep -Seconds 15
}
if (!(Test-Path -LiteralPath $purchased)) {
    throw 'The journey did not produce a purchased checkpoint; no ownership or stock will be supplied'
}
Invoke-ActualProbe 'Stage23BActualOwnedYardInspect' 'stage23b-actual-owned-yard-inspect.log' @() @($purchased, 'industry.2')
$graphicsOptions = @(
    "-Dstage23b.ownedYardCheckpoint=$purchased",
    '-Dstage23b.ownedYardStation=industry.2',
    "-Djava.io.tmpdir=$workspace/target"
)
Invoke-ActualProbe 'Stage23BSoftwareGraphicsSmoke' 'stage23b-actual-owned-yard-graphics.log' $graphicsOptions @()
$cargoCheckpoint = 'target/stage23b-ordinary-owned-yard.s25'
Invoke-ActualProbe 'Stage23BActualOwnedYardJourney' 'stage23b-actual-owned-yard-journey.log' @() @($purchased, $cargoCheckpoint, 'industry.2')
Invoke-ActualProbe 'Stage23BActualOwnedYardJourney' 'stage23b-actual-owned-yard-used-mining.log' @('-Dstage23b.yard.restoreMining=true') @(
    $cargoCheckpoint, 'target/stage23b-ordinary-owned-yard-restored-mining.s25', 'industry.2'
)
Write-Output 'ACTUAL OWNED YARD PIPELINE PASS; broader Stage 23B completion and human acceptance are not claimed'
