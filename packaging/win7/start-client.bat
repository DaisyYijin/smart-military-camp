@echo off
rem =============================================
rem  数智军营人员管理系统 - 客户端启动脚本(Win7)
rem  前置:已安装 JRE8(建议 32 位)
rem =============================================
chcp 65001 >nul
cd /d %~dp0

if exist jre8\bin\javaw.exe (
  start "" jre8\bin\javaw.exe -jar personnel-client.jar
) else (
  start "" javaw -jar personnel-client.jar
)
exit /b 0
