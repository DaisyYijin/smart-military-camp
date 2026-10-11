@echo off
rem =============================================
rem  数智军营人员管理系统 - 服务端启动脚本(Win7)
rem  前置:已安装 JRE8(建议 32 位)
rem =============================================
chcp 65001 >nul
cd /d %~dp0

if not exist jre8 (
  echo [错误] 未找到 jre8 目录。
  echo 请安装 JRE8 后,将 jre8 目录放到本脚本同级,或将本脚本中的 jre8\bin\java 改为 java。
  pause
  exit /b 1
)

echo 正在启动数智军营人员管理系统...
echo 首次运行会弹出设置向导(选端口、设管理员密码),之后自动打开浏览器。
echo 关闭方式:右下角托盘图标右键 - 退出。
start "personnel-server" /min jre8\bin\java -Xmx512m -jar personnel-server.jar
pause
