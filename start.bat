@echo off
chcp 65001 >nul
title FitChain Startup
set "PROJECT_ROOT=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PROJECT_ROOT%scripts\start-fitchain.ps1"
if errorlevel 1 (
  echo.
  echo [ERROR] FitChain failed to start. See the message above.
  pause
  exit /b 1
)
