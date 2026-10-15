@echo off
chcp 65001 >nul
title FitChain Mobile H5
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-mobile.ps1"
if errorlevel 1 (
  echo [ERROR] Mobile H5 startup failed. See logs\mobile-frontend.log.
  pause
  exit /b 1
)
