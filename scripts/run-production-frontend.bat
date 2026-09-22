@echo off
set "PROJECT_ROOT=%~dp0.."
if not exist "%PROJECT_ROOT%\logs" mkdir "%PROJECT_ROOT%\logs"
cd /d "%PROJECT_ROOT%\fitness-frontend"
set "VITE_DEMO_MODE=false"
echo.>> "%PROJECT_ROOT%\logs\frontend.log"
echo ===== FitChain frontend launch =====>> "%PROJECT_ROOT%\logs\frontend.log"
echo [FitChain] Production frontend starting at http://localhost:5173 >> "%PROJECT_ROOT%\logs\frontend.log"
call npm run dev >> "%PROJECT_ROOT%\logs\frontend.log" 2>&1
exit /b %errorlevel%
