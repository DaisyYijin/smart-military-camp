@echo off
rem =============================================
rem  CODE REVIEW: run SpotBugs static analysis
rem  on personnel-server source code.
rem
rem  Output report (HTML, open in browser):
rem    personnel-server\target\spotbugs.html
rem  XML report:
rem    personnel-server\target\spotbugsXml.xml
rem
rem  Notes:
rem   - "mvn verify" (not used by daily scripts)
rem     also fails on high-confidence defects.
rem   - First run needs internet to download the
rem     plugin; it is now cached in the local
rem     Maven repo, so offline runs work too.
rem =============================================
cd /d "%~dp0"
set "JAVA_HOME=%~dp0.tools\zulu8.84.0.15-ca-jdk8.0.442-win_x64"

echo [1/1] Running SpotBugs code review (1-2 min)...
call "%~dp0.tools\apache-maven-3.9.9\bin\mvn.cmd" -q -pl personnel-server compile spotbugs:spotbugs "-Dspotbugs.threshold=Medium" "-Dspotbugs.htmlOutput=true"
if errorlevel 1 (
  echo.
  echo [ERROR] Review failed. See messages above.
  pause
  exit /b 1
)

echo.
echo [OK] Review finished.
echo      HTML report: personnel-server\target\spotbugs.html
pause
