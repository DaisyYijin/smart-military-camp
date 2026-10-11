@echo off
rem =============================================
rem  ShuZhiJunYing Personnel Management System
rem  This window closes itself. Wait a few seconds
rem  for the setup wizard / browser to appear.
rem  To STOP the system, use the tray icon at the
rem  bottom-right corner of the screen.
rem =============================================
cd /d "%~dp0"

set "JAVA_EXE=jre\bin\java.exe"
if not exist "%JAVA_EXE%" (
  echo [WARN] bundled jre not found, trying system Java...
  set "JAVA_EXE=java"
  where java >nul 2>nul
  if errorlevel 1 (
    echo.
    echo [ERROR] No Java runtime found!
    echo.
    echo Fix 1: extract the WHOLE zip package to a folder first,
    echo        then double-click this bat INSIDE that folder.
    echo Fix 2: check if antivirus software blocked jre\bin\java.exe
    echo        then restore it and run again.
    echo See the text file in this folder for full instructions.
    echo.
    pause
    exit /b 1
  )
)

start "personnel-server" /min "%JAVA_EXE%" -Xmx512m -jar personnel-server.jar
exit /b 0
