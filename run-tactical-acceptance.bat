@echo off
setlocal EnableExtensions

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

echo [1/2] Building the project and running tests...
call "%PROJECT_DIR%mvnw.cmd" --batch-mode --no-transfer-progress clean package
if errorlevel 1 (
    echo.
    echo [ERROR] The build or tests failed.
    goto :failure
)

set "APP_JAR="
for /f "delims=" %%F in ('dir /b /a-d /o-d "target\star-empires-*-all.jar" 2^>nul') do if not defined APP_JAR set "APP_JAR=target\%%F"

if not defined APP_JAR (
    echo [ERROR] The executable JAR with the -all suffix was not found.
    goto :failure
)

echo.
echo [2/2] Starting Stage 17.5I tactical acceptance viewer...
echo Controls: SPACE play/pause, LEFT/P previous, RIGHT/N next, R reset, ESC exit.
echo.
"%JAVA_EXE%" -jar "%APP_JAR%" --tactical-acceptance
set "APP_EXIT_CODE=%ERRORLEVEL%"

if not "%APP_EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] Tactical acceptance viewer exited with code %APP_EXIT_CODE%.
    pause
)

popd
exit /b %APP_EXIT_CODE%

:failure
echo.
pause
popd
exit /b 1
