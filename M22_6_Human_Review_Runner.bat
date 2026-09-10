@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul 2>&1
title StarEmpires M22.6 Human Review

set "RC_SHA=5fb4c523cf3d169677c602638b4f62726b915272"
set "RC_BRANCH=stage22-6-core-pair-balance-freeze"
set "FREEZE_MANIFEST=stage22.core_pair_freeze_manifest.v3"
set "FREEZE_FINGERPRINT=6705d39d21d234335d55a33d22460e6750941cf8a57719c24b88c1e4a659d6d4"
set "REPO_ROOT="
set "RC_DIR="
set "EVIDENCE_DIR=%USERPROFILE%\Documents\StarEmpires-M22.6-HumanReview-5fb4c523-art-2f4215e8"
set "PS_TOOL=%~dp0tools\human-review\M22_6_Human_Review_Tools.ps1"

call :find_repo "%~1"
if errorlevel 1 goto :fatal
for %%P in ("%REPO_ROOT%\..") do set "REPO_PARENT=%%~fP"
set "RC_DIR=%REPO_PARENT%\StarEmpires-M22.6-review-5fb4c523"
call :ensure_evidence
if errorlevel 1 goto :fatal

goto :menu

:menu
cls
echo ============================================================================
echo StarEmpires M22.6 Human Review Runner v3 - art 2f4215e8
echo ============================================================================
echo Frozen SHA : %RC_SHA%
echo Manifest   : %FREEZE_MANIFEST%
echo Fingerprint: %FREEZE_FINGERPRINT%
echo RC worktree: %RC_DIR%
echo Evidence   : %EVIDENCE_DIR%
echo.
echo B18: BLOCKED until a formal pre-frozen blinded task packet exists.
echo B19: READY through pinned-art blinded grayscale packet generation.
echo B20: BLOCKED until actual reviewed RC character renders/manifest exist.
echo Machine checks do NOT satisfy human B18/B19/B20.
echo.
echo [1] Prepare / verify exact frozen RC worktree
echo [2] Run quick M22.6 machine preflight
echo [3] Run full clean verify
echo [4] Build / refresh blinded human-review packet
echo [5] Start NEW B19 human review session and begin review
echo [6] Run / resume interactive B19 review
echo [7] Finish B19 session and calculate result
echo [8] Open B18/B20 blockers and canonical runbook
echo [9] Open evidence folder and exact identity
echo [0] Exit
echo.
set "CHOICE="
set /p "CHOICE=Select: "
if "%CHOICE%"=="1" goto :menu_prepare
if "%CHOICE%"=="2" goto :menu_preflight
if "%CHOICE%"=="3" goto :menu_verify
if "%CHOICE%"=="4" goto :menu_build_packet
if "%CHOICE%"=="5" goto :menu_start_b19
if "%CHOICE%"=="6" goto :menu_run_b19
if "%CHOICE%"=="7" goto :menu_finish_b19
if "%CHOICE%"=="8" goto :menu_blockers
if "%CHOICE%"=="9" goto :menu_evidence
if "%CHOICE%"=="0" exit /b 0
echo Unknown menu item.
pause
goto :menu

:menu_prepare
call :prepare_rc
pause
goto :menu

:menu_preflight
call :quick_preflight
pause
goto :menu

:menu_verify
call :full_verify
pause
goto :menu

:menu_build_packet
call :build_packet
pause
goto :menu

:menu_start_b19
call :start_b19
pause
goto :menu

:menu_run_b19
call :run_b19
pause
goto :menu

:menu_finish_b19
call :finish_b19
pause
goto :menu

:menu_blockers
call :open_blockers
pause
goto :menu

:menu_evidence
call :open_evidence
pause
goto :menu

:find_repo
where.exe git >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Git was not found in PATH.
  exit /b 1
)
set "CANDIDATE=%~1"
if defined CANDIDATE for /f "delims=" %%R in ('git -C "%CANDIDATE%" rev-parse --show-toplevel 2^>nul') do set "REPO_ROOT=%%R"
if not defined REPO_ROOT for /f "delims=" %%R in ('git -C "%~dp0" rev-parse --show-toplevel 2^>nul') do set "REPO_ROOT=%%R"
if not defined REPO_ROOT (
  echo BAT is not inside a Git clone. Enter the local StarEmpires path.
  set /p "REPO_INPUT=Repository path: "
  if not defined REPO_INPUT exit /b 1
  for /f "delims=" %%R in ('git -C "!REPO_INPUT!" rev-parse --show-toplevel 2^>nul') do set "REPO_ROOT=%%R"
)
if not defined REPO_ROOT exit /b 1
exit /b 0

:ensure_evidence
if not exist "%EVIDENCE_DIR%" mkdir "%EVIDENCE_DIR%" >nul 2>&1
if not exist "%EVIDENCE_DIR%" exit /b 1
if not exist "%EVIDENCE_DIR%\review_identity.txt" (
  >"%EVIDENCE_DIR%\review_identity.txt" echo buildSha=%RC_SHA%
  >>"%EVIDENCE_DIR%\review_identity.txt" echo freezeManifestVersion=%FREEZE_MANIFEST%
  >>"%EVIDENCE_DIR%\review_identity.txt" echo freezeFingerprint=%FREEZE_FINGERPRINT%
  >>"%EVIDENCE_DIR%\review_identity.txt" echo scenarioSuiteVersion=FILL_FROM_REVIEW_PACKET
  >>"%EVIDENCE_DIR%\review_identity.txt" echo reviewPacketVersion=FILL_BEFORE_REVIEW
  >>"%EVIDENCE_DIR%\review_identity.txt" echo reviewerAnonymousId=FILL_BEFORE_REVIEW
  >>"%EVIDENCE_DIR%\review_identity.txt" echo reviewStartedAtUtc=FILL_AT_START
  >>"%EVIDENCE_DIR%\review_identity.txt" echo reviewCompletedAtUtc=FILL_AT_END
)
if not exist "%EVIDENCE_DIR%\b18_responses.csv" >"%EVIDENCE_DIR%\b18_responses.csv" echo reviewerAnonymousId,taskId,freeTextPrimaryCause,selectedCauseId,freeTextCounteraction,answeredAtUtc,exclusionReason
if not exist "%EVIDENCE_DIR%\b19_responses.csv" >"%EVIDENCE_DIR%\b19_responses.csv" echo reviewerAnonymousId,sampleId,reportedFactionId,reportedRoleId,confidenceOptional,answeredAtUtc,exclusionReason
if not exist "%EVIDENCE_DIR%\b20_responses.csv" >"%EVIDENCE_DIR%\b20_responses.csv" echo reviewerAnonymousId,sampleId,sharedStylePass,reportedFactionId,reportedRoleId,failureReasonOptional,answeredAtUtc,exclusionReason
exit /b 0

:prepare_rc
echo.
echo [1/4] Checking frozen commit...
git -C "%REPO_ROOT%" cat-file -e "%RC_SHA%" >nul 2>&1
if errorlevel 1 (
  git -C "%REPO_ROOT%" fetch origin "%RC_BRANCH%"
  if errorlevel 1 exit /b 1
)
echo [2/4] Preparing detached worktree...
if not exist "%RC_DIR%" (
  git -C "%REPO_ROOT%" worktree add --detach "%RC_DIR%" "%RC_SHA%"
  if errorlevel 1 exit /b 1
)
set "ACTUAL_SHA="
for /f "delims=" %%H in ('git -C "%RC_DIR%" rev-parse HEAD 2^>nul') do set "ACTUAL_SHA=%%H"
if /I not "!ACTUAL_SHA!"=="%RC_SHA%" (
  echo [ERROR] Worktree SHA mismatch: !ACTUAL_SHA!
  exit /b 1
)
echo [3/4] Checking clean worktree...
set "DIRTY="
for /f "delims=" %%S in ('git -C "%RC_DIR%" status --porcelain 2^>nul') do set "DIRTY=1"
if defined DIRTY (
  echo [ERROR] Exact-RC worktree is dirty.
  git -C "%RC_DIR%" status --short
  exit /b 1
)
echo [4/4] Checking required files...
if not exist "%RC_DIR%\mvnw.cmd" exit /b 1
if not exist "%RC_DIR%\docs\factions\stage22_m22_6_human_review_runbook.md" exit /b 1
if not exist "%PS_TOOL%" (
  echo [ERROR] Human-review PowerShell tool is missing. Update stage22-6-human-review-tools.
  exit /b 1
)
echo [OK] Exact frozen RC is ready and clean.
exit /b 0

:check_java
java -version >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Java not found. Install/configure JDK 17.
  exit /b 1
)
exit /b 0

:check_powershell
where.exe powershell.exe >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Windows PowerShell was not found.
  exit /b 1
)
exit /b 0

:quick_preflight
call :prepare_rc
if errorlevel 1 exit /b 1
call :check_java
if errorlevel 1 exit /b 1
echo.
echo MACHINE PREFLIGHT ONLY - NOT HUMAN EVIDENCE
pushd "%RC_DIR%" >nul
call mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=Stage22CorePairVisualCharacterAcceptanceTest,Stage22CorePairColdStartAssetsFingerprintAcceptanceTest,Stage22FactionProfileAuthorityAcceptanceTest,Stage22CorePairCommittedAuthorityAcceptanceTest" test
set "TEST_RC=!ERRORLEVEL!"
popd >nul
>"%EVIDENCE_DIR%\machine_preflight_result.txt" echo buildSha=%RC_SHA%
>>"%EVIDENCE_DIR%\machine_preflight_result.txt" echo exitCode=!TEST_RC!
>>"%EVIDENCE_DIR%\machine_preflight_result.txt" echo note=PRE-FLIGHT ONLY - NOT HUMAN EVIDENCE
exit /b !TEST_RC!

:full_verify
call :prepare_rc
if errorlevel 1 exit /b 1
call :check_java
if errorlevel 1 exit /b 1
echo.
echo LOCAL MACHINE CHECK ONLY - NOT HUMAN EVIDENCE
pushd "%RC_DIR%" >nul
call mvnw.cmd --batch-mode --no-transfer-progress clean verify
set "VERIFY_RC=!ERRORLEVEL!"
popd >nul
>"%EVIDENCE_DIR%\local_clean_verify_result.txt" echo buildSha=%RC_SHA%
>>"%EVIDENCE_DIR%\local_clean_verify_result.txt" echo exitCode=!VERIFY_RC!
>>"%EVIDENCE_DIR%\local_clean_verify_result.txt" echo note=LOCAL MACHINE CHECK - NOT HUMAN EVIDENCE
exit /b !VERIFY_RC!

:build_packet
call :prepare_rc
if errorlevel 1 exit /b 1
call :check_powershell
if errorlevel 1 exit /b 1
echo.
echo Building B19 from pinned art 2f4215e8; machine RC remains unchanged. No human answers are generated.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_TOOL%" -Action BuildPacket -RcDir "%RC_DIR%" -EvidenceDir "%EVIDENCE_DIR%"
exit /b !ERRORLEVEL!

:start_b19
call :build_packet
if errorlevel 1 exit /b 1
echo.
echo Enter a pseudonymous reviewer ID. Example: reviewer-01
set "REVIEWER_ID="
set /p "REVIEWER_ID=Reviewer ID: "
if not defined REVIEWER_ID (
  echo [ERROR] Reviewer ID is required.
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_TOOL%" -Action StartSession -RcDir "%RC_DIR%" -EvidenceDir "%EVIDENCE_DIR%" -ReviewerId "!REVIEWER_ID!"
if errorlevel 1 exit /b 1
call :run_b19
exit /b !ERRORLEVEL!

:run_b19
call :prepare_rc
if errorlevel 1 exit /b 1
call :check_powershell
if errorlevel 1 exit /b 1
if not exist "%EVIDENCE_DIR%\packet\reviewer\B19_REVIEW_INSTRUCTIONS.txt" (
  echo [ERROR] Build the packet and start a session first.
  exit /b 1
)
start "" "%EVIDENCE_DIR%\packet\reviewer\B19_REVIEW_INSTRUCTIONS.txt"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_TOOL%" -Action RunB19 -RcDir "%RC_DIR%" -EvidenceDir "%EVIDENCE_DIR%"
exit /b !ERRORLEVEL!

:finish_b19
echo.
echo This action freezes reviewCompletedAtUtc and reveals the aggregate B19 score.
echo Run it only after all 18 B19 samples have been answered or explicitly excluded.
set "FINALIZE="
set /p "FINALIZE=Type FINALIZE to continue: "
if /I not "!FINALIZE!"=="FINALIZE" (
  echo Cancelled.
  exit /b 0
)
call :prepare_rc
if errorlevel 1 exit /b 1
call :check_powershell
if errorlevel 1 exit /b 1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_TOOL%" -Action FinishSession -RcDir "%RC_DIR%" -EvidenceDir "%EVIDENCE_DIR%"
if errorlevel 1 exit /b 1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_TOOL%" -Action Validate -RcDir "%RC_DIR%" -EvidenceDir "%EVIDENCE_DIR%"
set "VAL_RC=!ERRORLEVEL!"
if exist "%EVIDENCE_DIR%\validation_status.txt" start "" "%EVIDENCE_DIR%\validation_status.txt"
exit /b !VAL_RC!

:open_blockers
call :build_packet
if errorlevel 1 exit /b 1
start "" "%EVIDENCE_DIR%\packet\reviewer\B18_BLOCKED.txt"
start "" "%EVIDENCE_DIR%\packet\reviewer\B20_BLOCKED.txt"
start "" "%RC_DIR%\docs\factions\stage22_m22_6_human_review_runbook.md"
exit /b 0

:open_evidence
call :ensure_evidence
start "" explorer.exe "%EVIDENCE_DIR%"
start "" "%EVIDENCE_DIR%\review_identity.txt"
if exist "%EVIDENCE_DIR%\packet_status.txt" start "" "%EVIDENCE_DIR%\packet_status.txt"
if exist "%EVIDENCE_DIR%\validation_status.txt" start "" "%EVIDENCE_DIR%\validation_status.txt"
exit /b 0

:fatal
echo.
echo [FATAL] Runner cannot continue.
pause
exit /b 1
