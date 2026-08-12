@echo off
setlocal EnableExtensions

set "PROJECT_DIR=%~dp0"
set "SPRITE_REL=src\main\resources\assets\ships\heavy_corvette\white_heavy_corvette_01\white_heavy_corvette_01_base.png"

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

if exist "%PROJECT_DIR%%SPRITE_REL%" (
    echo [ASSET] Heavy corvette sprite found:
    echo         %SPRITE_REL%
) else (
    echo [WARNING] Heavy corvette sprite is NOT present.
    echo           The test will use its procedural fallback.
    echo           Put the PNG here with the exact filename:
    echo           %SPRITE_REL%
)

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
echo [2/2] Starting Stage 8.5 representative graphics test...
echo       ESC closes the validation scene.
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

:failure
echo.
pause
popd
exit /b 1
