@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "PROJECT_DIR=%~dp0"
set "WORLD_SEED=%~1"
if not defined WORLD_SEED set "WORLD_SEED=1"

pushd "%PROJECT_DIR%" >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Cannot open the project directory: %PROJECT_DIR%
    pause
    exit /b 1
)

rem Star Empires currently targets Java 17. Do not trust the user's global JAVA_HOME/PATH:
rem IntelliJ can keep a newer runtime there while the project SDK is a downloaded JDK 17.
rem Probe each candidate's JDK "release" metadata instead of executing nested quoted java
rem commands; this is reliable for paths containing spaces and avoids cmd.exe parse errors.
set "JAVA17_HOME="
set "JAVA17_VERSION="

if defined JAVA_HOME call :probe_java_home "%JAVA_HOME%"

if not defined JAVA17_HOME if exist "%USERPROFILE%\.jdks" (
    for /d %%D in ("%USERPROFILE%\.jdks\*") do if not defined JAVA17_HOME call :probe_java_home "%%~fD"
)

if not defined JAVA17_HOME if exist "%LOCALAPPDATA%\Programs\Eclipse Adoptium" (
    for /d %%D in ("%LOCALAPPDATA%\Programs\Eclipse Adoptium\*") do if not defined JAVA17_HOME call :probe_java_home "%%~fD"
)

if not defined JAVA17_HOME if exist "%ProgramFiles%\Eclipse Adoptium" (
    for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\*") do if not defined JAVA17_HOME call :probe_java_home "%%~fD"
)

if not defined JAVA17_HOME if exist "%ProgramFiles%\Microsoft" (
    for /d %%D in ("%ProgramFiles%\Microsoft\*") do if not defined JAVA17_HOME call :probe_java_home "%%~fD"
)

if not defined JAVA17_HOME if exist "%ProgramFiles%\Java" (
    for /d %%D in ("%ProgramFiles%\Java\*") do if not defined JAVA17_HOME call :probe_java_home "%%~fD"
)

if not defined JAVA17_HOME (
    for /f "delims=" %%J in ('where.exe java 2^>nul') do if not defined JAVA17_HOME (
        for %%D in ("%%~dpJ..") do call :probe_java_home "%%~fD"
    )
)

if not defined JAVA17_HOME (
    echo [ERROR] Star Empires requires a JDK 17 desktop runtime, but no JDK 17 installation was found.
    echo.
    echo Searched:
    echo   JAVA_HOME
    echo   %%USERPROFILE%%\.jdks\*   ^(IntelliJ IDEA downloaded JDKs^)
    echo   %%LOCALAPPDATA%%\Programs\Eclipse Adoptium\*
    echo   %%ProgramFiles%%\Eclipse Adoptium\*
    echo   %%ProgramFiles%%\Microsoft\*
    echo   %%ProgramFiles%%\Java\*
    echo   java.exe entries on PATH
    echo.
    echo IntelliJ may use JDK 17 for the project even when global JAVA_HOME points at a newer JDK.
    echo If Project Structure shows JDK 17, it should normally be discovered under %%USERPROFILE%%\.jdks.
    goto :failure
)

set "JAVA_HOME=%JAVA17_HOME%"
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Using JDK %JAVA17_VERSION%
echo Java home: %JAVA_HOME%

if not exist "%PROJECT_DIR%mvnw.cmd" (
    echo [ERROR] Maven Wrapper was not found: %PROJECT_DIR%mvnw.cmd
    goto :failure
)

echo [1/2] Building Star Empires generated-world client...
echo Interactive launch skips tests; GitHub CI remains the authoritative verification gate.
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
echo [2/2] Starting integrated generated campaign with seed %WORLD_SEED%...
echo This is the production generated-world path: one campaign coordinator composes the accepted Stage-20 runtime with the final Stage-21 authorities and save state.
echo A new seed bootstraps that campaign; F8/F9 save and restore the same composed runtime without regeneration.
echo.
echo Controls:
echo   F1 / F2 / F3 / F4 / F5   System / Galaxy / Factions / Military / Logistics
echo   Left mouse button         Select an object, system, faction or fleet
echo   Double left click         Focus a logistics or military ship
echo   Mouse wheel over map      Zoom at cursor
echo   Hold middle mouse button  Pan the system or galaxy camera
echo   Mouse wheel over panels   Scroll lists and the information inspector
echo   SPACE               Pause / resume simulation
echo   1 / 2 / 3 / 4       Time scale 1x / 2x / 4x / 8x
echo   F8 / F9                   Save / load integrated campaign runtime
echo   ESC                 Exit
echo.
"%JAVA_EXE%" -jar "%APP_JAR%" --generated-world --world-seed=%WORLD_SEED%
set "APP_EXIT_CODE=%ERRORLEVEL%"

if not "%APP_EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] Generated-world client exited with code %APP_EXIT_CODE%.
    pause
)

popd
exit /b %APP_EXIT_CODE%

:probe_java_home
if defined JAVA17_HOME goto :eof
set "CANDIDATE_HOME=%~1"
set "CANDIDATE_VERSION="
set "CANDIDATE_MAJOR="
if not defined CANDIDATE_HOME goto :eof
if not exist "%CANDIDATE_HOME%\bin\java.exe" goto :eof
if not exist "%CANDIDATE_HOME%\bin\javac.exe" goto :eof
if not exist "%CANDIDATE_HOME%\release" goto :eof
for /f "tokens=2 delims==" %%V in ('%SystemRoot%\System32\findstr.exe /b /c:"JAVA_VERSION=" "%CANDIDATE_HOME%\release" 2^>nul') do if not defined CANDIDATE_VERSION set "CANDIDATE_VERSION=%%~V"
if not defined CANDIDATE_VERSION goto :eof
for /f "tokens=1 delims=." %%M in ("%CANDIDATE_VERSION%") do set "CANDIDATE_MAJOR=%%M"
if "%CANDIDATE_MAJOR%"=="17" (
    set "JAVA17_HOME=%CANDIDATE_HOME%"
    set "JAVA17_VERSION=%CANDIDATE_VERSION%"
)
goto :eof

:failure
echo.
pause
popd
exit /b 1
