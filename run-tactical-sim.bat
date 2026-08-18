@echo off
setlocal EnableExtensions

set "SCENARIO=%~1"
if not defined SCENARIO goto :menu
call :validateScenario "%SCENARIO%"
if errorlevel 1 goto :invalidArgument
goto :launch

:menu
cls
echo ============================================================
echo   STAR EMPIRES - STAGE 19J TACTICAL VALIDATION SCENARIOS
echo ============================================================
echo.
echo   [1] 1v1 Legacy Duel
echo   [2] 4v4 Balanced
echo   [3] 8v8 Mixed
echo   [4] 8v8 Damaged / Depleted
echo   [5] 16v16 Mixed
echo   [6] 16v16 Saturation
echo   [Q] Quit
echo.
set "CHOICE="
set /p "CHOICE=Select scenario [1-6]: "
if /I "%CHOICE%"=="Q" exit /b 0
if "%CHOICE%"=="1" set "SCENARIO=duel"& goto :launch
if "%CHOICE%"=="2" set "SCENARIO=4v4"& goto :launch
if "%CHOICE%"=="3" set "SCENARIO=8v8"& goto :launch
if "%CHOICE%"=="4" set "SCENARIO=8v8-damaged"& goto :launch
if "%CHOICE%"=="5" set "SCENARIO=16v16"& goto :launch
if "%CHOICE%"=="6" set "SCENARIO=saturation"& goto :launch
echo.
echo [ERROR] Invalid selection: %CHOICE%
pause
goto :menu

:validateScenario
if /I "%~1"=="duel" set "SCENARIO=duel"& exit /b 0
if /I "%~1"=="4v4" set "SCENARIO=4v4"& exit /b 0
if /I "%~1"=="8v8" set "SCENARIO=8v8"& exit /b 0
if /I "%~1"=="8v8-damaged" set "SCENARIO=8v8-damaged"& exit /b 0
if /I "%~1"=="16v16" set "SCENARIO=16v16"& exit /b 0
if /I "%~1"=="saturation" set "SCENARIO=saturation"& exit /b 0
exit /b 1

:invalidArgument
echo [ERROR] Unknown tactical scenario: %SCENARIO%
echo Valid values: duel, 4v4, 8v8, 8v8-damaged, 16v16, saturation
exit /b 2

:launch
set "PROJECT_DIR=%~dp0"
pushd "%PROJECT_DIR%" >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Cannot open the project directory: %PROJECT_DIR%
    pause
    exit /b 1
)

set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE for /f "delims=" %%J in ('where.exe java 2^>nul') do if not defined JAVA_EXE set "JAVA_EXE=%%J"

if not defined JAVA_EXE (
    echo [ERROR] Java was not found in JAVA_HOME or PATH.
    echo Install JDK 17, configure JAVA_HOME, and restart the terminal.
    goto :failure
)

if not exist "%PROJECT_DIR%mvnw.cmd" (
    echo [ERROR] Maven Wrapper was not found: %PROJECT_DIR%mvnw.cmd
    goto :failure
)

echo [1/2] Building the desktop application...
echo Unit/integration tests are intentionally skipped for interactive launch; CI remains authoritative.
call "%PROJECT_DIR%mvnw.cmd" --batch-mode --no-transfer-progress -DskipTests clean package
if errorlevel 1 (
    echo.
    echo [ERROR] The application build failed.
    goto :failure
)

set "APP_JAR="
for /f "delims=" %%F in ('dir /b /a-d /o-d "target\star-empires-*-all.jar" 2^>nul') do if not defined APP_JAR set "APP_JAR=target\%%F"

if not defined APP_JAR (
    echo [ERROR] The executable JAR with the -all suffix was not found.
    goto :failure
)

echo.
echo [2/2] Starting Stage 19J tactical validation scenario: %SCENARIO%
echo Controls:
echo   SPACE       pause/resume
echo   N or RIGHT  advance exactly one simulation tick while paused
echo   R           reset the CURRENT deterministic scenario
echo   1 / 2 / 4 / 8 simulation speed
echo   UP / DOWN   select combatant for debug HUD
echo   F1          toggle debug HUD
echo   ESC         exit
echo.
"%JAVA_EXE%" -jar "%APP_JAR%" --tactical-sim=%SCENARIO%
set "APP_EXIT_CODE=%ERRORLEVEL%"

if not "%APP_EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] Stage 19J tactical validation exited with code %APP_EXIT_CODE%.
    pause
)

popd
exit /b %APP_EXIT_CODE%

:failure
echo.
pause
popd
exit /b 1
