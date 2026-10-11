#!/usr/bin/env bash
# 卸载脚本:sudo bash uninstall.sh [保留数据]
# 默认保留 /opt/personnel-server/data(数据库与照片)
# 如需连数据一起删除:sudo bash uninstall.sh purge

set -e
APP_NAME="personnel-server"
INSTALL_DIR="/opt/${APP_NAME}"

systemctl stop ${APP_NAME} 2>/dev/null || true
systemctl disable ${APP_NAME} 2>/dev/null || true
rm -f /etc/systemd/system/${APP_NAME}.service
systemctl daemon-reload

if [ "$1" = "purge" ]; then
    rm -rf ${INSTALL_DIR}
    echo "已卸载并删除数据目录 ${INSTALL_DIR}"
else
    echo "已卸载服务(数据保留在 ${INSTALL_DIR})"
    echo "如需彻底删除数据:sudo bash uninstall.sh purge"
fi
