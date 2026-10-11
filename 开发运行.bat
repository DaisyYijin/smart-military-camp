@echo off
rem =============================================
rem  DEV RUN: build latest code and start the app
rem  in THIS project folder (uses .\data).
rem  Stop the app with the tray icon or Ctrl+C.
rem  No packaging needed - test first, package later.
rem =============================================
cd /d "%~dp0"
set "JAVA_HOME=%~dp0.tools\zulu8.84.0.15-ca-jdk8.0.442-win_x64"

echo [1/2] Building latest code (about 20s)...
call "%~dp0.tools\apache-maven-3.9.9\bin\mvn.cmd" -q -pl personnel-server package -DskipTests
if errorlevel 1 (
  echo.
  echo [ERROR] Build failed. See messages above.
  pause
  exit /b 1
)

echo [2/2] Starting app...
"%JAVA_HOME%\bin\java.exe" -jar personnel-server\target\personnel-server.jar
