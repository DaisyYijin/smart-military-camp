@echo off
chcp 65001 >nul
rem =============================================
rem  数智军营人员管理系统 - 双击启动
rem  无需安装任何软件:本文件夹自带运行环境
rem =============================================
cd /d %~dp0

if exist jre\bin\java.exe (
  start "数智军营" /min jre\bin\java.exe -Xmx512m -jar personnel-server.jar
) else (
  echo [提示] 未找到内置 jre 目录,尝试使用系统 Java...
  start "数智军营" /min java -Xmx512m -jar personnel-server.jar
)
