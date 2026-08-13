@echo off
setlocal EnableExtensions

set "PROJECT_DIR=%~dp0"
set "ASSET_DIR=src\main\resources\assets\ships\heavy_corvette\heavy_corvette_white_01"
set "BASE=%ASSET_DIR%\heavy_corvette_white_01_base.png"
set "EMISSIVE=%ASSET_DIR%\heavy_corvette_white_01_emissive.png"
set "DAMAGE=%ASSET_DIR%\heavy_corvette_white_01_damage.png"
set "ENGINE_IDLE=%ASSET_DIR%\heavy_corvette_white_01_engine_idle.png"
set "ENGINE_THRUST=%ASSET_DIR%\heavy_corvette_white_01_engine_thrust.png"

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
echo Star Empires - Heavy Corvette Asset Pack Validation
echo ============================================================
echo.
call :check_asset "BASE" "%BASE%"
call :check_asset "EMISSIVE" "%EMISSIVE%"
call :check_asset "DAMAGE" "%DAMAGE%"
call :check_asset "ENGINE IDLE" "%ENGINE_IDLE%"
call :check_asset "ENGINE THRUST" "%ENGINE_THRUST%"

echo.
echo Controls inside the validation window:
echo   E - Cycle engine OFF ^> IDLE ^> THRUST
echo   D - Toggle damage overlay
echo   L - Toggle emissive layer
echo   H - Toggle hardpoint markers
echo   R - Toggle ship rotation
echo ESC - Exit
echo.
echo The tool validates real PNG canvas sizes and alpha bounds at runtime.
echo Source art remains LEFT-facing; runtime presentation normalizes it to RIGHT.
echo.
echo [1/2] Building validation JAR and running package-phase tests...
call "%PROJECT_DIR%mvnw.cmd" --batch-mode --no-transfer-progress clean package
if errorlevel 1 (
    echo.
    echo [ERROR] Build or tests failed. Asset validation was not started.
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
echo [2/2] Starting heavy-corvette asset-pack validation...
echo.
"%JAVA_EXE%" -jar "%APP_JAR%" --asset-pack-validation
set "APP_EXIT_CODE=%ERRORLEVEL%"

if not "%APP_EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] Asset validation exited with code %APP_EXIT_CODE%.
    pause
)

popd
exit /b %APP_EXIT_CODE%

:check_asset
if exist "%PROJECT_DIR%%~2" (
    echo [OK]      %~1
    echo           %~2
) else (
    echo [MISSING] %~1
    echo           %~2
)
exit /b 0

:failure
echo.
pause
popd
exit /b 1
