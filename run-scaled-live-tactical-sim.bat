@echo off
setlocal EnableExtensions

echo [COMPATIBILITY] run-scaled-live-tactical-sim.bat now delegates to the Stage 19J unified launcher.
echo Scenario: 16v16 Saturation
call "%~dp0run-tactical-sim.bat" saturation
exit /b %ERRORLEVEL%
