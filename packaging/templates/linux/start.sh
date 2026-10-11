#!/bin/sh
# =============================================
# 数智军营人员管理系统 - 双击/命令行启动
# 本文件夹自带运行环境时无需安装任何软件
# =============================================
cd "$(dirname "$0")"
if [ -x ./jre/bin/java ]; then
  exec ./jre/bin/java -Xmx512m -jar personnel-server.jar "$@"
else
  echo "[提示] 未找到内置 jre 目录,尝试使用系统 Java..."
  exec java -Xmx512m -jar personnel-server.jar "$@"
fi
