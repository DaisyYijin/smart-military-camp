#!/bin/sh
# 生成桌面快捷方式(麒麟/统信桌面环境)
cd "$(dirname "$0")"
DIR="$(pwd)"
if [ -d "$HOME/桌面" ] && [ ! -d "$HOME/Desktop" ]; then
  DESKTOP_DIR="$HOME/桌面"
else
  DESKTOP_DIR="$HOME/Desktop"
fi
mkdir -p "$DESKTOP_DIR" || exit 1
DESKTOP="$DESKTOP_DIR/数智军营人员管理系统.desktop"
ESCAPED_DIR=$(printf '%s' "$DIR" | sed 's/[\\&|]/\\&/g')
sed "s|@DIR@|$ESCAPED_DIR|g" 数智军营人员管理系统.desktop > "$DESKTOP"
chmod +x "$DESKTOP"
echo "已创建桌面图标:$DESKTOP"
