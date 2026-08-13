@echo off
setlocal EnableExtensions

set "PROJECT_DIR=%~dp0"
set "ASSET_DIR=src\main\resources\assets\ships\heavy_corvette\heavy_corvette_white_01"
set "BASE_REL=%ASSET_DIR%\heavy_corvette_white_01_base.png"
set "EMISSIVE_REL=%ASSET_DIR%\heavy_corvette_white_01_emissive.png"
set "DAMAGE_REL=%ASSET_DIR%\heavy_corvette_white_01_damage.png"
set "IDLE_REL=%ASSET_DIR%\heavy_corvette_white_01_engine_idle.png"
set "THRUST_REL=%ASSET_DIR%\heavy_corvette_white_01_engine_thrust.png"

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

echo ============================================================
echo Star Empires - Stage 8.5 Graphics Validation
echo ============================================================
echo.
echo Heavy Corvette asset pack:
call :asset_status "BASE" "%BASE_REL%"
call :asset_status "EMISSIVE" "%EMISSIVE_REL%"
call :asset_status "DAMAGE" "%DAMAGE_REL%"
call :asset_status "ENGINE IDLE" "%IDLE_REL%"
call :asset_status "ENGINE THRUST" "%THRUST_REL%"
echo.
echo Canonical folder:
echo   %ASSET_DIR%
echo.
echo Controls inside the validation window:
echo   1 - Representative performance scene
echo   2 - Tactical scale/readability review
echo   3 - Close-up heavy-corvette inspection
echo   E - Heavy-corvette engine OFF / IDLE / THRUST
echo   D - Toggle authored damage overlay
echo   L - Toggle authored emissive layer
echo   H - Toggle hardpoint markers
echo   R - Toggle preview rotation
echo ESC - Exit
echo.
echo Representative keeps 50 ships / 500 asteroids / 2000 procedural particles.
echo Hero exhaust uses the approved authored asset; hero procedural particles are redistributed.
echo Runtime ship-forward convention: RIGHT.
echo The heavy-corvette source art faces LEFT and is normalized automatically.
echo.
echo [1/2] Building the validation JAR and running package-phase tests...
call "%PROJECT_DIR%mvnw.cmd" --batch-mode --no-transfer-progress clean package
if errorlevel 1 (
    echo.
    echo [ERROR] Build or tests failed. Graphics validation was not started.
    goto :failure
)

set "APP_JAR="
for /f "delims=" %%F in ('dir /b /a-d /o-d "target\star-empires-*-all.jar" 2^>nul') do if not defined APP_JAR set "APP_JAR=target\%%F"

if not defined APP_JAR (
    echo [ERROR] The executable JAR with the -all suffix was not found.
    goto :failure
)

if /I "%~1"=="--build-only" (
    echo.
    echo Build completed: %APP_JAR%
    popd
    exit /b 0
)

echo.
echo [2/2] Starting Stage 8.5 graphics validation...
echo.
"%JAVA_EXE%" -jar "%APP_JAR%" --graphics-spike
set "APP_EXIT_CODE=%ERRORLEVEL%"

if not "%APP_EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] Graphics validation exited with code %APP_EXIT_CODE%.
    pause
)

popd
exit /b %APP_EXIT_CODE%

:asset_status
if exist "%PROJECT_DIR%%~2" (
    echo [OK]      %~1 - %~2
) else (
    echo [MISSING] %~1 - %~2
)
exit /b 0

:failure
echo.
pause
popd
exit /b 1
