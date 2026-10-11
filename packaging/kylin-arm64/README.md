# 数智军营人员管理系统 - arm64 麒麟部署说明

## 一、服务端(装在麒麟服务器/办公电脑上)

### 1. 安装 JDK8(aarch64)
```bash
sudo apt update
sudo apt install -y openjdk-8-jre-headless
java -version   # 确认输出 aarch64 字样
```

### 2. 安装服务
```bash
sudo bash install.sh /路径/personnel-server.jar
```
脚本会自动:
- 复制 jar 到 `/opt/personnel-server/`
- 创建数据目录(数据库、照片、日志)
- 注册为 systemd 服务并开机自启

### 3. 验证
```bash
systemctl status personnel-server
tail -f /opt/personnel-server/logs/personnel-server.log
curl http://127.0.0.1:8080/api/departments   # 未带令牌会返回 401,属正常
```

## 二、客户端(麒麟)

```bash
# 客户端为纯 jar,需 JDK8
java -jar personnel-client.jar
```
启动后填写服务端地址,如 `http://192.168.1.100:8080`,用 `admin/admin123` 登录。

## 三、默认账号与安全
- 默认管理员:`admin / admin123`,**首次登录后请在"修改密码"中立即更换**
- 服务端重启后客户端需重新登录(令牌保存在内存)

## 四、备份数据
数据库与照片都在 `/opt/personnel-server/data/`,备份时整体拷贝即可:
```bash
tar czf backup.tar.gz /opt/personnel-server/data
```

## 五、常见问题
| 问题 | 处理 |
|---|---|
| 客户端连不上服务端 | 检查防火墙:`sudo ufw allow 8080/tcp`;确认客户端填的是服务端 IP 而非 127.0.0.1 |
| 提示"登录已过期" | 服务端重启后需重新登录 |
| 修改端口 | 编辑 `/etc/systemd/system/personnel-server.service` 的 ExecStart 加 `--server.port=9090`,然后 `systemctl daemon-reload && systemctl restart personnel-server` |
