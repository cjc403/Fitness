@echo off
chcp 65001 >nul
title FitChain Startup Diagnostics
set "PROJECT_ROOT=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_ROOT%scripts\diagnose-startup.ps1"
echo.
pause
