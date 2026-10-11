@echo off
rem =============================================
rem  BUILD RELEASE PACKAGES into packaging\dist\
rem  (win64 / linux-x64 / linux-arm64 / linux)
rem  Requires Git Bash (bash) in PATH.
rem =============================================
cd /d "%~dp0"

where bash >nul 2>nul
if errorlevel 1 (
  echo [ERROR] bash not found in PATH.
  echo Please run this from Git Bash, or add Git\bin to PATH.
  pause
  exit /b 1
)

bash packaging/build-release.sh
echo.
pause
