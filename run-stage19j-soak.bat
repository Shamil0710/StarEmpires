@echo off
setlocal
cd /d "%~dp0"

echo Star Empires - Stage 19J long tactical soak
echo.
echo This headless acceptance run executes all six canonical tactical scenarios.
echo The 16v16 saturation scenario runs for at least 600 simulated seconds / 12000 fixed ticks.
echo.

call mvnw.cmd --batch-mode --no-transfer-progress -Dstage19j.soak=true -Dtest=Stage19JLongSoakTest test
if errorlevel 1 (
    echo.
    echo Stage 19J soak FAILED.
    exit /b 1
)

echo.
echo Stage 19J soak PASSED.
exit /b 0
