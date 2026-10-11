# 数智军营人员管理系统 - Win7 部署说明

## 一、部署方式

本系统为 C/S 架构:
- **服务端**(Spring Boot + SQLite)部署在 arm64 麒麟电脑上(见 `packaging/kylin-arm64/README.md`)
- **客户端**(Swing 桌面程序)安装在 Win7 电脑上,通过网络连接服务端

Win7 上只需安装客户端。若单位只有 Win7 且无麒麟机器,服务端也可在 Win7 上运行(见下文"服务端在 Win7 运行")。

## 二、Win7 客户端安装

### 1. 安装 JRE8(必需)
- 推荐 **32 位 JRE8**,兼容性最好(与 arm64 无关,Win7 桌面程序用 x86 版)
- 下载:Oracle JDK8 官方存档,或 Eclipse Temurin / Azul Zulu 的 **Windows x86** 版
- 安装后请确认 `java -version` 可用

### 2. 部署客户端
把以下文件放到同一个文件夹(例如 `D:\人员管理系统\`):
```
personnel-client.jar
start-client.bat
```
双击 `start-client.bat` 启动,填写服务端地址(如 `http://192.168.1.100:8080`),
账号 `admin`,初始密码 `admin123`。

### 3. 可选:做成 exe 快捷方式
用 Launch4j(Windows 工具)可将 `personnel-client.jar` 打包为 `人员管理系统.exe`:
- 配置示例见 `launch4j-client.xml`(需按实际路径修改)
- 或用 `jre8` 目录 + bat 方式,无需额外工具

## 三、服务端在 Win7 运行(无麒麟机器时)
```
personnel-server.jar
start-server.bat
jre8\            <- 把 JRE8 目录复制到这里
```
双击 `start-server.bat` 即可启动服务端(端口 8080)。
> 注意:Win7 版 JRE8 最高为 8u202(Oracle 免费版),可改用 Temurin/Zulu 的 8u422 或更高。

## 四、Win7 客户端连接不上服务端排查
1. 服务端防火墙放行 8080:
   - Win7:`控制面板 → Windows 防火墙 → 高级设置 → 入站规则 → 新建规则 → 端口 → TCP 8080`
   - 麒麟:`sudo ufw allow 8080/tcp`
2. 确认客户端填写的地址是服务端机器 IP(局域网内),不是 127.0.0.1
3. `ping 服务端IP` 与 `telnet 服务端IP 8080` 逐层排查网络

## 五、数据备份
服务端数据(数据库+照片)位于服务端运行目录的 `data/` 文件夹,整体拷贝即可备份。
