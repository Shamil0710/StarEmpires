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
set "EVIDENCE_DIR=%USERPROFILE%\Documents\StarEmpires-M22.6-HumanReview-5fb4c523"

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
echo StarEmpires M22.6 Human Review Runner
echo ============================================================================
echo Frozen SHA : %RC_SHA%
echo Manifest   : %FREEZE_MANIFEST%
echo Fingerprint: %FREEZE_FINGERPRINT%
echo RC worktree: %RC_DIR%
echo Evidence   : %EVIDENCE_DIR%
echo.
echo Machine checks are PREFLIGHT ONLY. They do NOT satisfy human B18/B19/B20.
echo.
echo [1] Prepare / verify exact frozen RC worktree
echo [2] Run quick M22.6 machine preflight
echo [3] Run full clean verify
echo [4] B18: open instructions and launch generated-world client
echo [5] Open canonical B18-B20 human-review runbook
echo [6] Open evidence folder and response CSV files
echo [7] Show exact RC identity/status
echo [0] Exit
echo.
set "CHOICE="
set /p "CHOICE=Select: "
if "%CHOICE%"=="1" goto :menu_prepare
if "%CHOICE%"=="2" goto :menu_preflight
if "%CHOICE%"=="3" goto :menu_verify
if "%CHOICE%"=="4" goto :menu_b18
if "%CHOICE%"=="5" goto :menu_runbook
if "%CHOICE%"=="6" goto :menu_evidence
if "%CHOICE%"=="7" goto :menu_identity
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

:menu_b18
call :b18
goto :menu

:menu_runbook
call :open_runbook
pause
goto :menu

:menu_evidence
call :open_evidence
pause
goto :menu

:menu_identity
call :show_identity
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
if not exist "%RC_DIR%\run-generated-world.bat" exit /b 1
if not exist "%RC_DIR%\docs\factions\stage22_m22_6_human_review_runbook.md" exit /b 1
echo [OK] Exact frozen RC is ready and clean.
exit /b 0

:check_java
java -version >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Java not found. Install/configure JDK 17.
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

:b18
call :prepare_rc
if errorlevel 1 (
  pause
  exit /b 1
)
start "" "%RC_DIR%\docs\factions\stage22_m22_6_human_review_runbook.md"
start "" "%EVIDENCE_DIR%\b18_responses.csv"
echo.
echo Formal B18 requires a pre-frozen blinded task packet and hidden answer key.
set "WORLD_SEED="
set /p "WORLD_SEED=Seed from B18 packet [1]: "
if not defined WORLD_SEED set "WORLD_SEED=1"
pushd "%RC_DIR%" >nul
call run-generated-world.bat "!WORLD_SEED!"
set "CLIENT_RC=!ERRORLEVEL!"
popd >nul
exit /b !CLIENT_RC!

:open_runbook
call :prepare_rc
if errorlevel 1 exit /b 1
start "" "%RC_DIR%\docs\factions\stage22_m22_6_human_review_runbook.md"
start "" explorer.exe "%EVIDENCE_DIR%"
exit /b 0

:open_evidence
call :ensure_evidence
start "" explorer.exe "%EVIDENCE_DIR%"
start "" "%EVIDENCE_DIR%\review_identity.txt"
start "" "%EVIDENCE_DIR%\b18_responses.csv"
start "" "%EVIDENCE_DIR%\b19_responses.csv"
start "" "%EVIDENCE_DIR%\b20_responses.csv"
exit /b 0

:show_identity
call :prepare_rc
if errorlevel 1 exit /b 1
echo.
echo buildSha=%RC_SHA%
echo freezeManifestVersion=%FREEZE_MANIFEST%
echo freezeFingerprint=%FREEZE_FINGERPRINT%
echo reviewWorktree=%RC_DIR%
echo evidenceDir=%EVIDENCE_DIR%
echo.
git -C "%RC_DIR%" status --short --branch
exit /b 0

:fatal
echo.
echo [FATAL] Runner cannot continue.
pause
exit /b 1
