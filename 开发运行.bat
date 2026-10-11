@echo off
rem =============================================
rem  DEV RUN with DevTools (hot restart)
rem
rem  Frontend (HTML/CSS/JS): edit + F5 = instant
rem  Backend (Java): this window auto-restarts in 2-3s
rem  after you run "compile.bat" in another window.
rem
rem  Workflow:
rem    1. Double-click this bat (keeps running)
rem    2. Edit your code
rem    3. Frontend: just F5 in browser
rem    4. Java: double-click compile.bat (or run
rem       mvn compile in another terminal)
rem    5. This window auto-restarts in seconds
rem =============================================
cd /d "%~dp0"
set "JAVA_HOME=%~dp0.tools\zulu8.84.0.15-ca-jdk8.0.442-win_x64"

echo Starting with DevTools (fast auto-restart)...
call "%~dp0.tools\apache-maven-3.9.9\bin\mvn.cmd" -pl personnel-server spring-boot:run -Dspring-boot.run.arguments="--dev --server.port=8080"
