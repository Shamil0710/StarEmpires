param(
    [ValidateSet('BuildPacket','StartSession','RunB19','FinishSession','Validate')]
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
$PacketVersion = 'm22.6-human-review-packet.v3-art-refresh'
$VisualSha = '2f4215e8881cf8b02df35eef92a667c917b47e80'
$ToolRepo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$VisualDir = Join-Path (Split-Path -Parent $ToolRepo) 'StarEmpires-B19-art-2f4215e8'

function Assert-VisualSource {
    & git -C $ToolRepo cat-file -e $VisualSha 2>$null
    if ($LASTEXITCODE -ne 0) {
        & git -C $ToolRepo fetch origin main
        if ($LASTEXITCODE -ne 0) { throw 'Unable to fetch pinned visual source.' }
    }
    if (-not (Test-Path $VisualDir)) {
        & git -C $ToolRepo worktree add --detach $VisualDir $VisualSha
        if ($LASTEXITCODE -ne 0) { throw 'Unable to prepare visual source worktree.' }
    }
    $visualHead = & git -C $VisualDir rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $visualHead -ne $VisualSha) { throw 'Visual source SHA mismatch.' }
    $visualDirty = @(& git -C $VisualDir status --porcelain)
    if ($LASTEXITCODE -ne 0 -or $visualDirty.Count -gt 0) { throw 'Visual source worktree is dirty.' }
}

function Assert-PacketIdentity {
    $identity = Read-Identity
    if ($identity['visualBuildSha'] -ne $VisualSha -or $identity['reviewPacketVersion'] -ne $PacketVersion) {
        throw 'Stale visual review identity. Build a new packet in the new evidence directory.'
    }
}

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
    [System.IO.File]::WriteAllText($Path, $Text, [System.Text.UTF8Encoding]::new($false))
}

function Get-UtcNow { [DateTime]::UtcNow.ToString('o') }

function Assert-ExactRc {
    if (-not (Test-Path $RcDir)) { throw "RC directory does not exist: $RcDir" }
    $head = (& git -C $RcDir rev-parse HEAD 2>$null).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -ne $RcSha) { throw "Exact RC SHA mismatch. Expected $RcSha, got $head" }
    $dirty = @(& git -C $RcDir status --porcelain 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect RC worktree status.' }
    if ($dirty.Count -gt 0) { throw 'Exact RC worktree is dirty. Human packet construction is refused.' }
}

function Read-Identity {
    $map = [ordered]@{}
    if (Test-Path $IdentityPath) {
        foreach ($line in [IO.File]::ReadAllLines($IdentityPath)) {
            $i = $line.IndexOf('=')
            if ($i -gt 0) { $map[$line.Substring(0,$i)] = $line.Substring($i+1) }
        }
    }
    $map
}

function Save-Identity($map) {
    $keys = @('buildSha','visualBuildSha','freezeManifestVersion','freezeFingerprint','scenarioSuiteVersion','reviewPacketVersion','reviewerAnonymousId','reviewStartedAtUtc','reviewCompletedAtUtc')
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
    $m['visualBuildSha'] = $VisualSha
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
        -join ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') })
    } finally { $sha.Dispose() }
}

function New-GrayscalePng([string]$Source, [string]$Destination) {
    Add-Type -AssemblyName System.Drawing
    $src = [System.Drawing.Bitmap]::new($Source)
    try {
        $dst = [System.Drawing.Bitmap]::new($src.Width, $src.Height, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $g = [Drawing.Graphics]::FromImage($dst)
            try {
                $ia = [Drawing.Imaging.ImageAttributes]::new()
                try {
                    $m = [Drawing.Imaging.ColorMatrix]::new()
                    $m.Matrix00 = 0.299; $m.Matrix01 = 0.299; $m.Matrix02 = 0.299
                    $m.Matrix10 = 0.587; $m.Matrix11 = 0.587; $m.Matrix12 = 0.587
                    $m.Matrix20 = 0.114; $m.Matrix21 = 0.114; $m.Matrix22 = 0.114
                    $m.Matrix33 = 1.0; $m.Matrix44 = 1.0
                    $ia.SetColorMatrix($m)
                    $rect = [Drawing.Rectangle]::new(0,0,$src.Width,$src.Height)
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
    Assert-VisualSource
    if (Test-Path $B19Responses) {
        $recorded = @(Import-Csv $B19Responses | Where-Object { $_.answeredAtUtc -or $_.exclusionReason })
        if ($recorded.Count -gt 0) { throw 'Recorded judgments exist. Resume review; do not rebuild its packet.' }
    }
    if (Test-Path $IdentityPath) {
        $old = Read-Identity
        if ($old.Contains('reviewPacketVersion') -and $old['reviewPacketVersion'] -notlike 'FILL*' -and $old['reviewPacketVersion'] -ne $PacketVersion) {
            throw 'Old packet identity detected. Use the new evidence directory.'
        }
    }
    if (Test-Path $B19Dir) { Get-ChildItem $B19Dir -Filter 'B19-*.png' -File | Remove-Item }

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
            $source = Join-Path $VisualDir ($relative -replace '/', [IO.Path]::DirectorySeparatorChar)
            if (-not (Test-Path $source)) { throw "Required exact-RC production visual is missing: $relative" }
            $digest = (Get-FileHash -Algorithm SHA256 -Path $source).Hash.ToLowerInvariant()
            $sampleHash = Get-Sha256Text("B19|$VisualSha|$($f.Id)|$role|$digest")
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

DO NOT open:
  packet\facilitator_private_DO_NOT_OPEN_BEFORE_REVIEW
until menu item 7 has frozen the complete review.

There are exactly 18 samples from pinned visual revision 2f4215e8: 9 role families x 2 core factions.
Use runner menu item 5 to start and item 6 to run/resume the interactive review.
The runner opens one grayscale image at a time and records UTC automatically.
No correctness feedback is shown between samples.
Do not inspect the original colored assets while reviewing.

Faction choices:
  1 = core.empire (Империя)
  2 = core.industrial_union (Индустриальный Союз)

Role choices:
  1 battleship
  2 carrier
  3 corvette
  4 cruiser
  5 destroyer
  6 fleet_support
  7 freight
  8 frigate
  9 tanker

Thresholds:
  faction accuracy >= 90%
  role accuracy    >= 80%
"@
    Write-Utf8 (Join-Path $ReviewerDir 'B19_REVIEW_INSTRUCTIONS.txt') $instructions

    Write-Utf8 (Join-Path $ReviewerDir 'B18_BLOCKED.txt') @"
B18 FORMAL REVIEW STATUS: BLOCKED

The frozen RC contains the B18 protocol, but no formal pre-frozen blinded task/answer-key packet is available with all required taskId, scenarioId, seed, permutation, checkpoint refs, primaryDependencyId, acceptedEquivalentAnswers and visibleEvidenceRefs.

Launching an arbitrary generated-world seed is NOT formal B18 evidence. This runner refuses to invent a task key or substitute free play for the canonical protocol.
"@

    Write-Utf8 (Join-Path $ReviewerDir 'B20_BLOCKED.txt') @"
B20 FORMAL REVIEW STATUS: BLOCKED

The exact frozen RC contains the canonical Character Master Prompt and frozen character-lineup fingerprints, but it does not provide a canonical reviewed-character render manifest/sample set to this packet builder. A prompt, generated proxy, machine classification or assistant judgment cannot replace actual reviewed RC character samples.

This runner therefore refuses to manufacture B20 judgments or images.
"@

    $manifest = [ordered]@{
        buildSha = $RcSha
        visualBuildSha = $VisualSha
        visualReviewScope = 'Art refresh; not evidence for the original RC visual freeze'
        freezeManifestVersion = $FreezeManifest
        freezeFingerprint = $FreezeFingerprint
        scenarioSuiteVersion = $ScenarioSuiteVersion
        reviewPacketVersion = $PacketVersion
        generatedAtUtc = Get-UtcNow
        b18 = [ordered]@{ status='BLOCKED'; reason='No formal pre-frozen blinded task/answer-key packet available; synthetic substitute forbidden.' }
        b19 = [ordered]@{ status='READY'; sampleCount=$sorted.Count; expectedRoleCount=9; expectedFactionCount=2; answerKey='facilitator_private_DO_NOT_OPEN_BEFORE_REVIEW/b19_answer_key.csv' }
        b20 = [ordered]@{ status='BLOCKED'; reason='No canonical reviewed RC character render manifest/sample set available; proxies forbidden.' }
    }
    Write-Utf8 (Join-Path $PacketDir 'packet_manifest.json') (($manifest | ConvertTo-Json -Depth 5) + "`r`n")
    Write-Utf8 (Join-Path $EvidenceDir 'packet_status.txt') "B18=BLOCKED`r`nB19=READY (18 blinded pinned-art grayscale samples)`r`nB20=BLOCKED`r`n"
    Write-Host "[OK] B19 blinded packet created: $B19Dir"
    Write-Host '[BLOCKED] B18: formal task/answer-key packet absent.'
    Write-Host '[BLOCKED] B20: actual reviewed RC character render manifest/samples absent.'
}

function Start-Session {
    Assert-ExactRc
    Assert-VisualSource
    Assert-PacketIdentity
    if (-not (Test-Path $B19Key)) { throw 'BuildPacket must be run first.' }
    if ([string]::IsNullOrWhiteSpace($ReviewerId)) { throw 'ReviewerId is required for StartSession.' }
    if ($ReviewerId -match '[,\r\n]') { throw 'ReviewerId must not contain comma or line breaks.' }

    if (Test-Path $B19Responses) {
        $existing = @(Import-Csv $B19Responses)
        $answered = @($existing | Where-Object { -not [string]::IsNullOrWhiteSpace($_.answeredAtUtc) -or -not [string]::IsNullOrWhiteSpace($_.exclusionReason) })
        if ($answered.Count -gt 0) { throw 'Existing B19 judgments found. Use RunB19 to resume; do not overwrite recorded human evidence.' }
    }

    $m = Read-Identity
    $m['visualBuildSha']=$VisualSha; $m['buildSha']=$RcSha; $m['freezeManifestVersion']=$FreezeManifest; $m['freezeFingerprint']=$FreezeFingerprint
    $m['scenarioSuiteVersion']=$ScenarioSuiteVersion; $m['reviewPacketVersion']=$PacketVersion
    $m['reviewerAnonymousId']=$ReviewerId; $m['reviewStartedAtUtc']=Get-UtcNow; $m['reviewCompletedAtUtc']='FILL_AT_END'
    Save-Identity $m

    $images = @(Get-ChildItem -Path $B19Dir -Filter 'B19-*.png' -File | Sort-Object Name)
    if ($images.Count -ne 18) { throw "Expected 18 blinded samples, got $($images.Count)." }
    $forms = foreach ($img in $images) {
        [pscustomobject]@{ reviewerAnonymousId=$ReviewerId; sampleId=$img.BaseName; reportedFactionId=''; reportedRoleId=''; confidenceOptional=''; answeredAtUtc=''; exclusionReason='' }
    }
    $forms | Export-Csv -Path $B19Responses -NoTypeInformation -Encoding UTF8
    Write-Host "[OK] Human review session started for reviewer '$ReviewerId'."
}

function Save-B19Rows($Rows) { @($Rows) | Export-Csv -Path $B19Responses -NoTypeInformation -Encoding UTF8 }

function Run-B19 {
    Assert-ExactRc
    Assert-VisualSource
    Assert-PacketIdentity
    $identity = Read-Identity
    if (-not $identity.Contains('reviewerAnonymousId') -or [string]$identity['reviewerAnonymousId'] -like 'FILL*') { throw 'StartSession must be run first.' }
    if (-not (Test-Path $B19Responses)) { throw 'B19 response sheet is missing.' }

    $roles = [ordered]@{'1'='battleship';'2'='carrier';'3'='corvette';'4'='cruiser';'5'='destroyer';'6'='fleet_support';'7'='freight';'8'='frigate';'9'='tanker'}
    $factions = [ordered]@{'1'='core.empire';'2'='core.industrial_union'}
    $rows = @(Import-Csv $B19Responses)
    if ($rows.Count -ne 18) { throw "Expected 18 B19 rows, got $($rows.Count)." }

    for ($i=0; $i -lt $rows.Count; $i++) {
        $r = $rows[$i]
        if (-not [string]::IsNullOrWhiteSpace($r.answeredAtUtc) -or -not [string]::IsNullOrWhiteSpace($r.exclusionReason)) { continue }
        $imagePath = Join-Path $B19Dir ($r.sampleId + '.png')
        if (-not (Test-Path $imagePath)) { throw "Missing blinded image: $($r.sampleId)" }
        Start-Process -FilePath $imagePath | Out-Null
        Write-Host ''
        Write-Host ('Sample {0}/18: {1}' -f ($i+1),$r.sampleId) -ForegroundColor Cyan
        Write-Host 'Inspect ONLY the opened grayscale image. Do not open the private answer-key folder.'
        Write-Host 'Faction: 1 Empire | 2 Industrial Union | X exclude'
        do { $fc = (Read-Host 'Faction').Trim().ToUpperInvariant() } until ($fc -in @('1','2','X'))
        if ($fc -eq 'X') {
            do { $reason = (Read-Host 'Explicit exclusion reason').Trim() } until (-not [string]::IsNullOrWhiteSpace($reason))
            $r.exclusionReason = $reason
            $r.answeredAtUtc = Get-UtcNow
            Save-B19Rows $rows
            continue
        }
        Write-Host 'Role: 1 battleship | 2 carrier | 3 corvette | 4 cruiser | 5 destroyer'
        Write-Host '      6 fleet_support | 7 freight | 8 frigate | 9 tanker'
        do { $rc = (Read-Host 'Role').Trim() } until ($roles.Contains($rc))
        $confidence = (Read-Host 'Confidence 1-5 (optional)').Trim()
        if ($confidence -and $confidence -notmatch '^[1-5]$') { $confidence = '' }
        $r.reportedFactionId = $factions[$fc]
        $r.reportedRoleId = $roles[$rc]
        $r.confidenceOptional = $confidence
        $r.answeredAtUtc = Get-UtcNow
        Save-B19Rows $rows
    }
    $remaining = @($rows | Where-Object { [string]::IsNullOrWhiteSpace($_.answeredAtUtc) -and [string]::IsNullOrWhiteSpace($_.exclusionReason) })
    if ($remaining.Count -eq 0) {
        Write-Host ''
        Write-Host '[OK] All 18 B19 samples are frozen. No correctness has been revealed.' -ForegroundColor Green
        Write-Host 'Use runner menu item 7 to finish the session and calculate the aggregate result.'
    } else {
        Write-Host "[INFO] Remaining samples: $($remaining.Count)"
    }
}

function Assert-B19Complete {
    if (-not (Test-Path $B19Responses)) { throw 'B19 responses are missing.' }
    $rows = @(Import-Csv $B19Responses)
    if ($rows.Count -ne 18) { throw "Expected 18 B19 rows, got $($rows.Count)." }
    foreach ($r in $rows) {
        if (-not [string]::IsNullOrWhiteSpace($r.exclusionReason)) { continue }
        if ([string]::IsNullOrWhiteSpace($r.reportedFactionId) -or [string]::IsNullOrWhiteSpace($r.reportedRoleId) -or [string]::IsNullOrWhiteSpace($r.answeredAtUtc)) { throw "B19 sample is incomplete: $($r.sampleId)" }
    }
}

function Finish-Session {
    Assert-PacketIdentity
    Assert-B19Complete
    $m = Read-Identity
    if (-not $m.Contains('reviewerAnonymousId') -or [string]$m['reviewerAnonymousId'] -like 'FILL*') { throw 'StartSession must be run first.' }
    $m['reviewCompletedAtUtc'] = Get-UtcNow
    Save-Identity $m
    Write-Host '[OK] reviewCompletedAtUtc frozen.'
}

function Validate-Evidence {
    Assert-ExactRc
    Assert-VisualSource
    Assert-PacketIdentity
    Assert-B19Complete
    $m = Read-Identity
    $required = [ordered]@{ buildSha=$RcSha; visualBuildSha=$VisualSha; freezeManifestVersion=$FreezeManifest; freezeFingerprint=$FreezeFingerprint; scenarioSuiteVersion=$ScenarioSuiteVersion; reviewPacketVersion=$PacketVersion }
    foreach ($k in $required.Keys) {
        if (-not $m.Contains($k) -or [string]$m[$k] -ne [string]$required[$k]) { throw "Identity mismatch for $k" }
    }
    foreach ($k in @('reviewerAnonymousId','reviewStartedAtUtc','reviewCompletedAtUtc')) {
        if (-not $m.Contains($k) -or [string]::IsNullOrWhiteSpace([string]$m[$k]) -or [string]$m[$k] -like 'FILL*') { throw "Identity field not finalized: $k" }
    }
    if (-not (Test-Path $B19Key)) { throw 'B19 answer key is missing.' }

    $keyRows = @(Import-Csv $B19Key)
    $responses = @(Import-Csv $B19Responses)
    if ($keyRows.Count -ne 18 -or $responses.Count -ne 18) { throw 'B19 requires exactly 18 key and response rows.' }
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
        "visualBuildSha=$VisualSha",
        "visualReviewScope=Art refresh; original RC visual freeze requires reconciliation",
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
    'RunB19' { Run-B19 }
    'FinishSession' { Finish-Session }
    'Validate' { Validate-Evidence }
}
