param(
    [ValidateSet('BuildPacket','StartSession','FinishSession','Validate')]
    [string]$Action = 'BuildPacket',
    [Parameter(Mandatory=$true)][string]$RcDir,
    [Parameter(Mandatory=$true)][string]$EvidenceDir,
    [string]$ReviewerId = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$RcSha = '5fb4c523cf3d169677c602638b4f62726b915272'
$FreezeManifest = 'stage22.core_pair_freeze_manifest.v3'
$FreezeFingerprint = '6705d39d21d234335d55a33d22460e6750941cf8a57719c24b88c1e4a659d6d4'
$ScenarioSuiteVersion = 'stage22.core_pair_balance_suite.v1'
$PacketVersion = 'm22.6-human-review-packet.v2'
$IdentityPath = Join-Path $EvidenceDir 'review_identity.txt'
$PacketDir = Join-Path $EvidenceDir 'packet'
$ReviewerDir = Join-Path $PacketDir 'reviewer'
$B19Dir = Join-Path $ReviewerDir 'b19_grayscale'
$PrivateDir = Join-Path $PacketDir 'facilitator_private_DO_NOT_OPEN_BEFORE_REVIEW'
$B19Key = Join-Path $PrivateDir 'b19_answer_key.csv'
$B19Responses = Join-Path $EvidenceDir 'b19_responses.csv'

function Write-Utf8([string]$Path, [string]$Text) {
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    [System.IO.File]::WriteAllText($Path, $Text, (New-Object System.Text.UTF8Encoding($false)))
}

function Get-UtcNow { return [DateTime]::UtcNow.ToString('o') }

function Assert-ExactRc {
    if (-not (Test-Path $RcDir)) { throw "RC directory does not exist: $RcDir" }
    $head = (& git -C $RcDir rev-parse HEAD 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -ne $RcSha) { throw "Exact RC SHA mismatch. Expected $RcSha, got $head" }
    $dirty = @(& git -C $RcDir status --porcelain 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect RC worktree status.' }
    if ($dirty.Count -gt 0) { throw "Exact RC worktree is dirty. Human packet construction is refused." }
}

function Read-Identity {
    $map = [ordered]@{}
    if (Test-Path $IdentityPath) {
        foreach ($line in [IO.File]::ReadAllLines($IdentityPath)) {
            $i = $line.IndexOf('=')
            if ($i -gt 0) { $map[$line.Substring(0,$i)] = $line.Substring($i+1) }
        }
    }
    return $map
}

function Save-Identity($map) {
    $keys = @('buildSha','freezeManifestVersion','freezeFingerprint','scenarioSuiteVersion','reviewPacketVersion','reviewerAnonymousId','reviewStartedAtUtc','reviewCompletedAtUtc')
    $lines = New-Object System.Collections.Generic.List[string]
    foreach ($k in $keys) {
        $v = if ($map.Contains($k)) { [string]$map[$k] } else { '' }
        $lines.Add("$k=$v")
    }
    Write-Utf8 $IdentityPath (($lines -join "`r`n") + "`r`n")
}

function Ensure-Identity {
    if (-not (Test-Path $EvidenceDir)) { New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null }
    $m = Read-Identity
    $m['buildSha'] = $RcSha
    $m['freezeManifestVersion'] = $FreezeManifest
    $m['freezeFingerprint'] = $FreezeFingerprint
    $m['scenarioSuiteVersion'] = $ScenarioSuiteVersion
    $m['reviewPacketVersion'] = $PacketVersion
    if (-not $m.Contains('reviewerAnonymousId') -or [string]$m['reviewerAnonymousId'] -like 'FILL*') { $m['reviewerAnonymousId'] = 'FILL_BEFORE_REVIEW' }
    if (-not $m.Contains('reviewStartedAtUtc') -or [string]$m['reviewStartedAtUtc'] -like 'FILL*') { $m['reviewStartedAtUtc'] = 'FILL_AT_START' }
    if (-not $m.Contains('reviewCompletedAtUtc') -or [string]$m['reviewCompletedAtUtc'] -like 'FILL*') { $m['reviewCompletedAtUtc'] = 'FILL_AT_END' }
    Save-Identity $m
}

function Get-Sha256Text([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
        return -join ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') })
    } finally { $sha.Dispose() }
}

function New-GrayscalePng([string]$Source, [string]$Destination) {
    Add-Type -AssemblyName System.Drawing
    $src = New-Object System.Drawing.Bitmap($Source)
    try {
        $dst = New-Object System.Drawing.Bitmap($src.Width, $src.Height, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $g = [Drawing.Graphics]::FromImage($dst)
            try {
                $ia = New-Object Drawing.Imaging.ImageAttributes
                try {
                    $m = New-Object Drawing.Imaging.ColorMatrix
                    $m.Matrix00 = 0.299; $m.Matrix01 = 0.299; $m.Matrix02 = 0.299
                    $m.Matrix10 = 0.587; $m.Matrix11 = 0.587; $m.Matrix12 = 0.587
                    $m.Matrix20 = 0.114; $m.Matrix21 = 0.114; $m.Matrix22 = 0.114
                    $m.Matrix33 = 1.0; $m.Matrix44 = 1.0
                    $ia.SetColorMatrix($m)
                    $rect = New-Object Drawing.Rectangle(0,0,$src.Width,$src.Height)
                    $g.DrawImage($src,$rect,0,0,$src.Width,$src.Height,[Drawing.GraphicsUnit]::Pixel,$ia)
                } finally { $ia.Dispose() }
            } finally { $g.Dispose() }
            $dst.Save($Destination,[Drawing.Imaging.ImageFormat]::Png)
        } finally { $dst.Dispose() }
    } finally { $src.Dispose() }
}

function Ensure-ResponseHeaders {
    $b18 = Join-Path $EvidenceDir 'b18_responses.csv'
    $b20 = Join-Path $EvidenceDir 'b20_responses.csv'
    if (-not (Test-Path $b18)) { Write-Utf8 $b18 "reviewerAnonymousId,taskId,freeTextPrimaryCause,selectedCauseId,freeTextCounteraction,answeredAtUtc,exclusionReason`r`n" }
    if (-not (Test-Path $b20)) { Write-Utf8 $b20 "reviewerAnonymousId,sampleId,sharedStylePass,reportedFactionId,reportedRoleId,failureReasonOptional,answeredAtUtc,exclusionReason`r`n" }
    if (-not (Test-Path $B19Responses)) { Write-Utf8 $B19Responses "reviewerAnonymousId,sampleId,reportedFactionId,reportedRoleId,confidenceOptional,answeredAtUtc,exclusionReason`r`n" }
}

function Build-Packet {
    Assert-ExactRc
    Ensure-Identity
    Ensure-ResponseHeaders
    New-Item -ItemType Directory -Force -Path $B19Dir,$PrivateDir | Out-Null

    $roles = @('battleship','carrier','corvette','cruiser','destroyer','fleet_support','freight','frigate','tanker')
    $factions = @(
        [pscustomobject]@{ Id='core.empire'; AssetDir='empire' },
        [pscustomobject]@{ Id='core.industrial_union'; AssetDir='industrial_union' }
    )
    $rows = New-Object System.Collections.Generic.List[object]

    foreach ($f in $factions) {
        foreach ($role in $roles) {
            $relative = "src/main/resources/assets/ships/$($f.AssetDir)/production/$role/${role}_base.png"
            $source = Join-Path $RcDir ($relative -replace '/', [IO.Path]::DirectorySeparatorChar)
            if (-not (Test-Path $source)) { throw "Required exact-RC production visual is missing: $relative" }
            $digest = (Get-FileHash -Algorithm SHA256 -Path $source).Hash.ToLowerInvariant()
            $sampleHash = Get-Sha256Text("B19|$RcSha|$($f.Id)|$role|$digest")
            $sampleId = 'B19-' + $sampleHash.Substring(0,12)
            $destination = Join-Path $B19Dir ($sampleId + '.png')
            New-GrayscalePng $source $destination
            $rows.Add([pscustomobject]@{
                sampleId = $sampleId
                assetRef = $relative
                assetFingerprintOrDigest = $digest
                expectedFactionId = $f.Id
                expectedRoleId = $role
            })
        }
    }

    $sorted = @($rows | Sort-Object sampleId)
    $sorted | Export-Csv -Path $B19Key -NoTypeInformation -Encoding UTF8

    $instructions = @"
M22.6 B19 — blinded grayscale ship review

DO NOT open the folder:
  facilitator_private_DO_NOT_OPEN_BEFORE_REVIEW
until the complete B19 response sheet is frozen.

There are exactly 18 production-RC samples: 9 role families x 2 core factions.
Open PNG files in packet\reviewer\b19_grayscale in filename order.
For each sample, fill the matching sampleId row in b19_responses.csv.
Do not inspect the original colored assets while reviewing.
Do not reveal correctness between samples.

Allowed faction IDs:
  core.empire             = Империя
  core.industrial_union   = Индустриальный Союз

Allowed role IDs:
  battleship
  carrier
  corvette
  cruiser
  destroyer
  fleet_support
  freight
  frigate
  tanker

Thresholds:
  faction accuracy >= 90%
  role accuracy    >= 80%

answeredAtUtc must be recorded for every scored row.
If a row must be excluded, keep it and provide a non-empty exclusionReason.
"@
    Write-Utf8 (Join-Path $ReviewerDir 'B19_REVIEW_INSTRUCTIONS.txt') $instructions

    $b18Block = @"
B18 FORMAL REVIEW STATUS: BLOCKED

The frozen RC contains the B18 protocol, but this tools pass did not find or invent a pre-frozen task packet with hidden answer-key rows containing taskId, scenarioId, seed, permutation, start/end checkpoint refs, primaryDependencyId, acceptedEquivalentAnswers and visibleEvidenceRefs.

Launching an arbitrary generated-world seed is NOT formal B18 evidence. The runner intentionally refuses to substitute free play for the canonical blinded task protocol.
"@
    Write-Utf8 (Join-Path $ReviewerDir 'B18_BLOCKED.txt') $b18Block

    $b20Block = @"
B20 FORMAL REVIEW STATUS: BLOCKED

The exact frozen RC has the shared Character Master Prompt and frozen character-lineup fingerprints, but no canonical RC reviewed-character render manifest/sample set is available to this packet builder. A prompt, generated proxy, machine classification or assistant judgment cannot replace actual reviewed RC character samples.

The runner therefore does not manufacture B20 images or count placeholder judgments.
"@
    Write-Utf8 (Join-Path $ReviewerDir 'B20_BLOCKED.txt') $b20Block

    $manifest = [ordered]@{
        buildSha = $RcSha
        freezeManifestVersion = $FreezeManifest
        freezeFingerprint = $FreezeFingerprint
        scenarioSuiteVersion = $ScenarioSuiteVersion
        reviewPacketVersion = $PacketVersion
        generatedAtUtc = Get-UtcNow
        b18 = [ordered]@{ status='BLOCKED'; reason='No pre-frozen blinded task/answer-key packet available; no synthetic substitute allowed.' }
        b19 = [ordered]@{ status='READY'; sampleCount=$sorted.Count; expectedRoleCount=9; expectedFactionCount=2; answerKey='facilitator_private_DO_NOT_OPEN_BEFORE_REVIEW/b19_answer_key.csv' }
        b20 = [ordered]@{ status='BLOCKED'; reason='No canonical reviewed RC character render manifest/sample set available; proxies forbidden.' }
    }
    Write-Utf8 (Join-Path $PacketDir 'packet_manifest.json') (($manifest | ConvertTo-Json -Depth 5) + "`r`n")
    Write-Utf8 (Join-Path $EvidenceDir 'packet_status.txt') "B18=BLOCKED`r`nB19=READY (18 blinded exact-RC grayscale samples)`r`nB20=BLOCKED`r`n"
    Write-Host "[OK] B19 blinded packet created: $B19Dir"
    Write-Host "[BLOCKED] B18 requires a formal pre-frozen task/answer-key packet."
    Write-Host "[BLOCKED] B20 requires actual reviewed RC character renders/manifest."
}

function Start-Session {
    Assert-ExactRc
    if (-not (Test-Path $B19Key)) { throw 'BuildPacket must be run first.' }
    if ([string]::IsNullOrWhiteSpace($ReviewerId)) { throw 'ReviewerId is required for StartSession.' }
    if ($ReviewerId -match '[,\r\n]') { throw 'ReviewerId must not contain comma or line breaks.' }
    $m = Read-Identity
    $m['buildSha']=$RcSha; $m['freezeManifestVersion']=$FreezeManifest; $m['freezeFingerprint']=$FreezeFingerprint
    $m['scenarioSuiteVersion']=$ScenarioSuiteVersion; $m['reviewPacketVersion']=$PacketVersion
    $m['reviewerAnonymousId']=$ReviewerId; $m['reviewStartedAtUtc']=Get-UtcNow; $m['reviewCompletedAtUtc']='FILL_AT_END'
    Save-Identity $m

    $key = @(Import-Csv $B19Key | Sort-Object sampleId)
    $forms = foreach ($k in $key) {
        [pscustomobject]@{ reviewerAnonymousId=$ReviewerId; sampleId=$k.sampleId; reportedFactionId=''; reportedRoleId=''; confidenceOptional=''; answeredAtUtc=''; exclusionReason='' }
    }
    $forms | Export-Csv -Path $B19Responses -NoTypeInformation -Encoding UTF8
    Write-Host "[OK] Human review session started for reviewer '$ReviewerId'."
    Write-Host "B19 response form prepared: $B19Responses"
}

function Finish-Session {
    $m = Read-Identity
    if (-not $m.Contains('reviewerAnonymousId') -or [string]$m['reviewerAnonymousId'] -like 'FILL*') { throw 'StartSession must be run first.' }
    $m['reviewCompletedAtUtc'] = Get-UtcNow
    Save-Identity $m
    Write-Host '[OK] reviewCompletedAtUtc frozen.'
}

function Validate-Evidence {
    Assert-ExactRc
    $m = Read-Identity
    $required = [ordered]@{ buildSha=$RcSha; freezeManifestVersion=$FreezeManifest; freezeFingerprint=$FreezeFingerprint; scenarioSuiteVersion=$ScenarioSuiteVersion; reviewPacketVersion=$PacketVersion }
    foreach ($k in $required.Keys) {
        if (-not $m.Contains($k) -or [string]$m[$k] -ne [string]$required[$k]) { throw "Identity mismatch for $k" }
    }
    foreach ($k in @('reviewerAnonymousId','reviewStartedAtUtc','reviewCompletedAtUtc')) {
        if (-not $m.Contains($k) -or [string]::IsNullOrWhiteSpace([string]$m[$k]) -or [string]$m[$k] -like 'FILL*') { throw "Identity field not finalized: $k" }
    }
    if (-not (Test-Path $B19Key)) { throw 'B19 answer key is missing.' }
    if (-not (Test-Path $B19Responses)) { throw 'B19 responses are missing.' }

    $keyRows = @(Import-Csv $B19Key)
    $responses = @(Import-Csv $B19Responses)
    if ($keyRows.Count -ne 18) { throw "Expected 18 B19 key rows, got $($keyRows.Count)." }
    if ($responses.Count -ne 18) { throw "Expected 18 B19 response rows, got $($responses.Count)." }
    $byId = @{}
    foreach ($r in $responses) {
        if ($byId.ContainsKey($r.sampleId)) { throw "Duplicate B19 sampleId: $($r.sampleId)" }
        $byId[$r.sampleId] = $r
    }
    $factionCorrect=0; $roleCorrect=0; $scored=0; $excluded=0
    $confusion = @{}
    foreach ($k in $keyRows) {
        if (-not $byId.ContainsKey($k.sampleId)) { throw "Missing B19 response for $($k.sampleId)" }
        $r = $byId[$k.sampleId]
        if (-not [string]::IsNullOrWhiteSpace($r.exclusionReason)) { $excluded++; continue }
        if ([string]::IsNullOrWhiteSpace($r.reportedFactionId) -or [string]::IsNullOrWhiteSpace($r.reportedRoleId) -or [string]::IsNullOrWhiteSpace($r.answeredAtUtc)) { throw "Incomplete scored B19 response: $($k.sampleId)" }
        $scored++
        if ($r.reportedFactionId -eq $k.expectedFactionId) { $factionCorrect++ }
        if ($r.reportedRoleId -eq $k.expectedRoleId) { $roleCorrect++ }
        $ck = "$($k.expectedRoleId)->$($r.reportedRoleId)"
        if (-not $confusion.ContainsKey($ck)) { $confusion[$ck]=0 }
        $confusion[$ck]++
    }
    if ($scored -eq 0) { throw 'No scored B19 judgments.' }
    $fa = $factionCorrect / [double]$scored
    $ra = $roleCorrect / [double]$scored
    $pass = ($fa -ge 0.90 -and $ra -ge 0.80)
    $lines = @(
        "buildSha=$RcSha",
        "reviewPacketVersion=$PacketVersion",
        "factionCorrect=$factionCorrect",
        "factionJudgments=$scored",
        ('factionAccuracy={0:F6}' -f $fa),
        "roleCorrect=$roleCorrect",
        "roleJudgments=$scored",
        ('roleAccuracy={0:F6}' -f $ra),
        "excluded=$excluded",
        ('B19=' + $(if($pass){'PASS'}else{'FAIL'})),
        'B18=BLOCKED',
        'B20=BLOCKED',
        'M22.6=OPEN'
    )
    Write-Utf8 (Join-Path $EvidenceDir 'validation_status.txt') (($lines -join "`r`n") + "`r`n")
    $confLines = @('expectedRoleId,reportedRoleId,count')
    foreach ($ck in ($confusion.Keys | Sort-Object)) {
        $parts=$ck.Split('->'); $confLines += "$($parts[0]),$($parts[1]),$($confusion[$ck])"
    }
    Write-Utf8 (Join-Path $EvidenceDir 'b19_role_confusion.csv') (($confLines -join "`r`n") + "`r`n")
    Write-Host ('B19 faction: {0}/{1} = {2:P2}' -f $factionCorrect,$scored,$fa)
    Write-Host ('B19 role:    {0}/{1} = {2:P2}' -f $roleCorrect,$scored,$ra)
    Write-Host ('B19 result:  ' + $(if($pass){'PASS'}else{'FAIL'}))
    Write-Host 'B18/B20 remain BLOCKED; M22.6 cannot be signed off yet.'
}

switch ($Action) {
    'BuildPacket' { Build-Packet }
    'StartSession' { Start-Session }
    'FinishSession' { Finish-Session }
    'Validate' { Validate-Evidence }
}
