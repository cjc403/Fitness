@echo off
set "PROJECT_ROOT=%~dp0..\"
set "JAVA_HOME=%PROJECT_ROOT%.tools\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PROJECT_ROOT%.tools\maven\bin;%PATH%"
cd /d "%PROJECT_ROOT%fitness-backend"
echo [FitChain] Backend starting at http://localhost:8080
call "%PROJECT_ROOT%.tools\maven\bin\mvn.cmd" spring-boot:run
