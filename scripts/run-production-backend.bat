@echo off
set "PROJECT_ROOT=%~dp0.."
if not exist "%PROJECT_ROOT%\logs" mkdir "%PROJECT_ROOT%\logs"
cd /d "%PROJECT_ROOT%"
echo.>> "%PROJECT_ROOT%\logs\backend-launcher.log"
echo ===== FitChain backend launch =====>> "%PROJECT_ROOT%\logs\backend-launcher.log"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-production.ps1" >> "%PROJECT_ROOT%\logs\backend-launcher.log" 2>&1
exit /b %errorlevel%
