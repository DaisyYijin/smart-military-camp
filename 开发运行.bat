@echo off
rem =============================================
rem  DEV RUN: build latest code and start the app
rem  in THIS project folder (uses .\data).
rem  Dev mode enabled:
rem    - Web files (HTML/CSS/JS) are read from the
rem      source folder: edit + press F5 in browser,
rem      NO restart needed.
rem    - Java backend changes: re-run this bat.
rem  Stop the app with the tray icon or Ctrl+C.
rem =============================================
cd /d "%~dp0"
set "JAVA_HOME=%~dp0.tools\zulu8.84.0.15-ca-jdk8.0.442-win_x64"

echo [1/2] Building latest code (about 20s)...
rem 旧实例运行时锁定jar,会导致repackage重命名失败:先停止监听8080的进程
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
  echo        Stopping old instance ^(PID %%p^)...
  taskkill /PID %%p /F >nul 2>&1
)
call "%~dp0.tools\apache-maven-3.9.9\bin\mvn.cmd" -q -pl personnel-server package -DskipTests
if errorlevel 1 (
  echo.
  echo [ERROR] Build failed. See messages above.
  pause
  exit /b 1
)

echo [2/2] Starting app (dev mode)...
"%JAVA_HOME%\bin\java.exe" -jar personnel-server\target\personnel-server.jar --dev
