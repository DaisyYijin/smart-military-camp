========================================
 数智军营人员管理系统 - Linux 版使用说明
========================================

【启动】
方式一(桌面环境):双击 start.sh(或解压后运行 ./start.sh)
方式二(命令行):  ./start.sh
方式三(服务器,无显示器):./start.sh --no-ui --port=8080

每次启动会先弹出设置页(桌面环境):确认/修改端口与管理员账号,
密码留空表示不修改(忘记密码在这里重置),点"启动系统"后浏览器自动打开。

【日常使用】
- 浏览器访问 http://127.0.0.1:端口 或 http://本机IP:端口
- 托盘图标:打开系统 / 复制局域网地址 / 修改管理员密码 / 退出
- --no-ui 模式提示走控制台,Ctrl+C 停止
- 首次空库须在受控进程环境中设置 APP_BOOTSTRAP_PASSWORD（可选 APP_BOOTSTRAP_USERNAME），
  没有密码将拒绝启动，初始化变量不会覆盖已有账号。不要在脚本或命令行中保存密码。
- 默认 HTTP 不加密密码/令牌，正式局域网请配置可信 HTTPS，详见随包
  tls.example.yml 与部署安全与恢复说明.md。不要关闭证书校验。

【数据与备份】
默认数据在本目录 data/ 下。在线备份请用系统备份入口。
复制整个目录迁移必须先完全停止服务，不能运行中只复制 .db 文件。
修改过路径时，须同时核对 spring.datasource.url 和 app.data-dir。

【未内置 JRE 的包】
若本包不含 jre/ 目录,需要系统已安装 Java 8(arm64 麒麟可用
Temurin/Zulu 的 aarch64 JRE8)。将 JRE 解压到本目录并重命名为 jre 即可内置。

【防火墙】
其他电脑无法访问时,请放行所选端口:
  sudo firewall-cmd --add-port=8080/tcp --permanent && sudo firewall-cmd --reload
或 Ubuntu: sudo ufw allow 8080
