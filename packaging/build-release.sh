#!/usr/bin/env bash
# =============================================
# 数智军营人员管理系统 - 一键生成免安装发行包
# 产出(Packaging/dist/):
#   数智军营-win64.zip          Windows 7+ 64位,内置 JRE,解压双击即用
#   数智军营-linux-x64.tar.gz   Linux x86_64,内置 JRE(需先准备 packaging/jres/linux-x64)
#   数智军营-linux-arm64.tar.gz 麒麟/鲲鹏 arm64,内置 JRE(需先准备 packaging/jres/linux-arm64)
#   数智军营-linux.tar.gz       Linux 通用,不含 JRE(要求系统已装 Java 8)
# =============================================
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG="$ROOT/packaging"
DIST="$PKG/dist"
STAGE="$PKG/.stage"
JAR="$ROOT/personnel-server/target/personnel-server.jar"
ZULU8="$ROOT/.tools/zulu8.84.0.15-ca-jdk8.0.442-win_x64"

echo "==> [1/4] 构建 jar"
if [ "${SKIP_BUILD:-0}" != "1" ]; then
  export JAVA_HOME="$ZULU8"
  "$ROOT/.tools/apache-maven-3.9.9/bin/mvn" -q -pl personnel-server package -DskipTests
fi
[ -f "$JAR" ] || { echo "[错误] jar 不存在:$JAR"; exit 1; }

rm -rf "$STAGE"
mkdir -p "$STAGE" "$DIST"

zip_one() { # $1=待打包目录 $2=zip 输出
  if command -v zip >/dev/null 2>&1; then
    (cd "$(dirname "$1")" && zip -qr "$2" "$(basename "$1")")
  elif [ -x /c/Windows/System32/tar.exe ]; then
    # Windows 自带 bsdtar:生成标准 zip,UTF-8 文件名不乱码
    (cd "$(dirname "$1")" && /c/Windows/System32/tar.exe -a -cf "$2" "$(basename "$1")")
  else
    P1="$1"; P2="$2"
    command -v cygpath >/dev/null 2>&1 && { P1=$(cygpath -w "$1"); P2=$(cygpath -w "$2"); }
    powershell -NoProfile -Command "Compress-Archive -Path '$P1' -DestinationPath '$P2' -Force"
  fi
}
targz_one() { (cd "$(dirname "$1")" && tar -czf "$2" "$(basename "$1")"); }

echo "==> [2/4] Windows x64 免安装包(内置 JRE)"
if [ -d "$ZULU8/jre" ]; then
  W="$STAGE/数智军营-win64"
  mkdir -p "$W"
  cp "$JAR" "$W/"
  cp "$PKG/templates/win/启动系统.bat" "$W/"
  # 说明文本转 GBK:中文 Windows 记事本的默认编码,Win7/Win10 均正常显示
  iconv -f UTF-8 -t GBK "$PKG/templates/win/使用说明.txt" > "$W/使用说明.txt" 2>/dev/null || cp "$PKG/templates/win/使用说明.txt" "$W/"
  cp -r "$ZULU8/jre" "$W/jre"
  zip_one "$W" "$DIST/数智军营-win64.zip"
  echo "    -> packaging/dist/数智军营-win64.zip"
else
  echo "    [跳过] 未找到 $ZULU8/jre"
fi

echo "==> [3/4] Linux x64 / arm64 内置 JRE 包"
for arch in x64 arm64; do
  JR="$PKG/jres/linux-$arch"
  if [ -d "$JR" ]; then
    L="$STAGE/数智军营-linux-$arch"
    mkdir -p "$L"
    cp "$JAR" "$L/"
    cp "$PKG/templates/linux/start.sh" "$L/"
    cp "$PKG/templates/linux/README.txt" "$L/"
    cp "$PKG/templates/linux/创建桌面图标.sh" "$L/"
    cp "$PKG/templates/linux/数智军营人员管理系统.desktop" "$L/"
    chmod +x "$L/start.sh" "$L/创建桌面图标.sh"
    cp -r "$JR" "$L/jre"
    targz_one "$L" "$DIST/数智军营-linux-$arch.tar.gz"
    echo "    -> packaging/dist/数智军营-linux-$arch.tar.gz"
  else
    echo "    [跳过 linux-$arch] 未找到 $JR(把对应架构 JRE8 解压到该目录后重跑脚本即可)"
  fi
done

echo "==> [4/4] Linux 通用包(不含 JRE)"
L="$STAGE/数智军营-linux"
mkdir -p "$L"
cp "$JAR" "$L/"
cp "$PKG/templates/linux/start.sh" "$L/"
cp "$PKG/templates/linux/README.txt" "$L/"
cp "$PKG/templates/linux/创建桌面图标.sh" "$L/"
cp "$PKG/templates/linux/数智军营人员管理系统.desktop" "$L/"
chmod +x "$L/start.sh" "$L/创建桌面图标.sh"
targz_one "$L" "$DIST/数智军营-linux.tar.gz"
echo "    -> packaging/dist/数智军营-linux.tar.gz"

rm -rf "$STAGE"
echo "==> 完成!发行包输出在 packaging/dist/:"
ls -lh "$DIST" | awk 'NR>1{print "    "$5"  "$NF}'
