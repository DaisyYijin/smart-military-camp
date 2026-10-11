@echo off
rem =============================================
rem  Quick compile (triggers DevTools auto-restart)
rem  Run this after editing Java files.
rem =============================================
cd /d "%~dp0"
set "JAVA_HOME=%~dp0.tools\zulu8.84.0.15-ca-jdk8.0.442-win_x64"
call "%~dp0.tools\apache-maven-3.9.9\bin\mvn.cmd" -q -pl personnel-server compile
if errorlevel 1 (
  echo.
  echo [ERROR] Compile failed. Fix the code and retry.
  pause
  exit /b 1
)
echo Compile OK. DevTools will restart the app in seconds.
