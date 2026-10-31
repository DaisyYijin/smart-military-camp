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
BUILD_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
DIST="$PKG/dist/$BUILD_ID"
STAGE=""
JAR="$ROOT/personnel-server/target/personnel-server.jar"
ZULU8="$ROOT/.tools/zulu8.84.0.15-ca-jdk8.0.442-win_x64"

echo "==> [1/4] 构建 jar"
if [ "${SKIP_BUILD:-0}" != "1" ]; then
  export JAVA_HOME="$ZULU8"
  # 质量门禁:默认先跑后端与前端测试,失败即中止;确需跳过必须显式 SKIP_TESTS=1
  if [ "${SKIP_TESTS:-0}" != "1" ]; then
    echo "    [质量门禁] 运行后端与前端测试（显式跳过: SKIP_TESTS=1）"
    "$ROOT/.tools/apache-maven-3.9.9/bin/mvn" -q -pl personnel-server test || {
      echo "[错误] 后端测试未通过，已中止发行包构建；确需跳过请显式设置 SKIP_TESTS=1"
      exit 1
    }
    node --test "$ROOT"/personnel-server/src/test/frontend/*.test.cjs > /dev/null 2>&1 || {
      echo "[错误] 前端测试未通过，已中止发行包构建；详情可运行: node --test personnel-server/src/test/frontend/*.test.cjs；确需跳过请显式设置 SKIP_TESTS=1"
      exit 1
    }
  else
    echo "    [警告] SKIP_TESTS=1 已显式设置，本次发行包未经过测试验证"
  fi
  "$ROOT/.tools/apache-maven-3.9.9/bin/mvn" -q -pl personnel-server package -DskipTests
fi
[ -f "$JAR" ] || { echo "[错误] jar 不存在:$JAR"; exit 1; }

mkdir -p "$DIST"
STAGE="$(mktemp -d "$PKG/.stage.XXXXXX")"
trap 'if [ -n "$STAGE" ] && [ -d "$STAGE" ]; then rm -rf -- "$STAGE"; fi' EXIT
printf '本次发行目录: %s\n仅本目录中的文件属于本次构建；旧目录不删除、不混入。\n' "$DIST"

copy_security_docs() {
  cp "$PKG/templates/tls.example.yml" "$1/"
  cp "$ROOT/docs/部署安全与恢复说明.md" "$1/"
}

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
targz_one() {
  (cd "$(dirname "$1")" && tar -czf "$2" --transform='s/\.exe$//' --owner=0 --group=0 "$(basename "$1")")
}

# Git Bash 挂载无 ACL:chmod 无效,但 Cygwin 规则给 .exe 后缀文件授予执行位。
# 借此恢复 Linux JRE 的可执行权限:暂存副本改名加 .exe,归档时 --transform 去掉。
fix_exec_bits() { # $1 = 暂存区 jre 目录
  find "$1/bin" -maxdepth 1 -type f ! -name "*.*" | while read -r f; do
    mv "$f" "$f.exe"
  done
  for f in "$1/lib/"*/jspawnhelper "$1/lib/jexec"; do
    [ -f "$f" ] && mv "$f" "$f.exe"
  done
}

echo "==> [2/4] Windows x64 免安装包(内置 JRE)"
if [ -d "$ZULU8/jre" ]; then
  W="$STAGE/数智军营-win64"
  mkdir -p "$W"
  cp "$JAR" "$W/"
  copy_security_docs "$W"
  cp "$PKG/templates/win/启动系统.bat" "$W/"
  # 说明文本转 GBK:中文 Windows 记事本的默认编码,Win7/Win10 均正常显示
  iconv -f UTF-8 -t GBK "$PKG/templates/win/使用说明.txt" > "$W/使用说明.txt" 2>/dev/null || cp "$PKG/templates/win/使用说明.txt" "$W/"
  cp -r "$ZULU8/jre" "$W/jre"
  zip_one "$W" "$DIST/数智军营-win64.zip"
  echo "    -> $DIST/数智军营-win64.zip"
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
    copy_security_docs "$L"
    cp "$PKG/templates/linux/start.sh" "$L/"
    cp "$PKG/templates/linux/README.txt" "$L/"
    cp "$PKG/templates/linux/创建桌面图标.sh" "$L/"
    cp "$PKG/templates/linux/数智军营人员管理系统.desktop" "$L/"
    chmod +x "$L/start.sh" "$L/创建桌面图标.sh"
    cp -r "$JR" "$L/jre"
    fix_exec_bits "$L/jre"
    targz_one "$L" "$DIST/数智军营-linux-$arch.tar.gz"
    echo "    -> $DIST/数智军营-linux-$arch.tar.gz"
  else
    echo "    [跳过 linux-$arch] 未找到 $JR(把对应架构 JRE8 解压到该目录后重跑脚本即可)"
  fi
done

echo "==> [4/4] Linux 通用包(不含 JRE)"
L="$STAGE/数智军营-linux"
mkdir -p "$L"
cp "$JAR" "$L/"
copy_security_docs "$L"
cp "$PKG/templates/linux/start.sh" "$L/"
cp "$PKG/templates/linux/README.txt" "$L/"
cp "$PKG/templates/linux/创建桌面图标.sh" "$L/"
cp "$PKG/templates/linux/数智军营人员管理系统.desktop" "$L/"
chmod +x "$L/start.sh" "$L/创建桌面图标.sh"
targz_one "$L" "$DIST/数智军营-linux.tar.gz"
echo "    -> $DIST/数智军营-linux.tar.gz"

rm -rf "$STAGE"
printf '==> 完成！本次发行包输出在 %s\n' "$DIST"
( cd "$DIST" && shopt -s nullglob && archives=(*.zip *.tar.gz) && sha256sum "${archives[@]}" > SHA256SUMS )
ls -lh "$DIST"
