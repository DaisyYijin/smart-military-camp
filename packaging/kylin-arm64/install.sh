#!/usr/bin/env bash
# 数智军营人员管理系统 - arm64 麒麟系统服务端安装脚本
# 用法:sudo bash install.sh <personnel-server.jar 路径>
# 依赖:系统已安装 JDK8 (aarch64),可通过 银河麒麟应用商店 / sudo apt install openjdk-8-jre-headless 安装

set -e

APP_NAME="personnel-server"
INSTALL_DIR="/opt/${APP_NAME}"
JAR_SOURCE="$1"

if [ -z "$JAR_SOURCE" ] || [ ! -f "$JAR_SOURCE" ]; then
    echo "用法: sudo bash install.sh <personnel-server.jar 路径>"
    echo "示例: sudo bash install.sh personnel-server.jar"
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "错误:未检测到 java,请先安装 JDK8:"
    echo "  sudo apt update && sudo apt install -y openjdk-8-jre-headless"
    exit 1
fi

echo "==> 创建安装目录 ${INSTALL_DIR}"
mkdir -p ${INSTALL_DIR}
cp "$JAR_SOURCE" ${INSTALL_DIR}/${APP_NAME}.jar

echo "==> 创建数据目录(数据库与照片)"
mkdir -p ${INSTALL_DIR}/data
mkdir -p ${INSTALL_DIR}/logs

echo "==> 安装 systemd 服务"
cat > /etc/systemd/system/${APP_NAME}.service <<EOF
[Unit]
Description=数智军营人员管理系统服务端
After=network.target

[Service]
Type=simple
WorkingDirectory=${INSTALL_DIR}
ExecStart=$(command -v java) -Xmx512m -jar ${INSTALL_DIR}/${APP_NAME}.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable ${APP_NAME}
systemctl restart ${APP_NAME}

echo "==> 安装完成"
sleep 2
systemctl status ${APP_NAME} --no-pager || true

IP=$(hostname -I 2>/dev/null | awk '{print $1}')
echo ""
echo "======================================================"
echo "  服务已启动,默认端口 8080"
echo "  本机访问:   http://127.0.0.1:8080"
if [ -n "$IP" ]; then
    echo "  局域网访问: http://${IP}:8080"
fi
echo "  默认管理员: admin / admin123 (登录后请立即修改密码)"
echo "  常用命令:"
echo "    启动/停止/重启: systemctl start|stop|restart ${APP_NAME}"
echo "    查看日志:      tail -f /opt/${APP_NAME}/logs/personnel-server.log"
echo "    卸载:          sudo bash uninstall.sh"
echo "======================================================"
