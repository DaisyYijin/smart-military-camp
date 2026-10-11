#!/bin/sh
# 生成桌面快捷方式(麒麟/统信桌面环境)
cd "$(dirname "$0")"
DIR="$(pwd)"
mkdir -p "$HOME/Desktop" 2>/dev/null
DESKTOP="$HOME/Desktop/数智军营人员管理系统.desktop"
if [ -d "$HOME/桌面" ] && [ ! -d "$HOME/Desktop" ]; then
  DESKTOP="$HOME/桌面/数智军营人员管理系统.desktop"
fi
sed "s|@DIR@|$DIR|g" 数智军营人员管理系统.desktop > "$DESKTOP"
chmod +x "$DESKTOP"
echo "已创建桌面图标:$DESKTOP"
