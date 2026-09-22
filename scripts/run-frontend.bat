@echo off
set "PROJECT_ROOT=%~dp0..\"
cd /d "%PROJECT_ROOT%fitness-frontend"
set "VITE_DEMO_MODE=true"
echo [FitChain] Frontend starting at http://localhost:5173
call npm run dev
