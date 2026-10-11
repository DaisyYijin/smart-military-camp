# 数智军营人员管理系统

面向 **Windows 7+ / Linux(x86 与 arm64 麒麟)** 的内网离线人员管理系统。
**免安装、双击即用、浏览器访问**,数据全部保存在本机。

## 技术架构

```
┌──────────────────────┐   HTTP/JSON(Token)   ┌───────────────────────────┐
│  浏览器(任意电脑)    │ ────────────────────▶ │  单文件服务端(内嵌网页端) │
│  Win7/10/麒麟 自带    │                       │  Spring Boot + SQLite     │
│  浏览器即可,零安装    │                       │  双击运行:向导→托盘→浏览器│
└──────────────────────┘                       └───────────────────────────┘
                                               数据:data/personnel.db + photos/
```

| 组件 | 技术 | 说明 |
|---|---|---|
| 服务端 | Spring Boot 2.7 + SQLite(JDK8,Win7/麒麟兼容) | 单 jar 51MB,含启动向导/托盘/网页端 |
| 网页端 | 原生 HTML/JS/CSS,**零第三方依赖** | 离线内网可用,不做 npm 构建 |

> 旧版 Swing 客户端与 systemd 部署脚本已在清理时移除,如需找回见 git 历史(存档提交 `539f748`)。

## 功能

- 双击运行:首次设置向导(选端口/设管理员密码)→ 自动开浏览器 → 托盘常驻
- 端口冲突处理:显示占用进程名,从空闲端口列表一键改用,选定端口持久化
- 管理员密码:首次向导设置;托盘菜单"修改管理员密码"随时重置,不怕遗忘
- 人员管理:档案(30+ 字段)、照片、部门、状态;Excel 导入导出;组合检索分页
- 装备 / 物资(库存预警)/ 车辆 / 休假 / 体能考核 五大业务模块
- 部门树管理、数据字典(军衔/状态/类别等下拉项可自定义)
- 统计报表:总人数、部门人数、性别/状态分布

## 快速开始

### 方式一:发行包(推荐给最终用户)
到 `packaging/dist/`(由 `bash packaging/build-release.sh` 生成)取对应包:
- `数智军营-win64.zip`:解压 → 双击 `启动系统.bat`,**无需安装任何软件**
- `数智军营-linux-*.tar.gz`:解压 → `./start.sh`(内置 JRE 的版本同样零依赖)
- 详见包内《使用说明.txt / README.txt》

### 方式二:源码构建(JDK8 + Maven,仓库 `.tools/` 内自带)
```bash
bash packaging/build-release.sh          # 构建并产出全部发行包
# 或仅构建 jar:
mvn -DskipTests package                  # 产出 personnel-server/target/personnel-server.jar
java -jar personnel-server.jar           # 双击运行:向导 → 浏览器 → 托盘
```
命令行参数:`--no-ui`(无显示器服务器)、`--port=8080`、`--setup`(重开设置向导)。

### 网页端使用
浏览器打开 `http://本机IP:端口`,账号 `admin`,密码为向导中设置的密码。
局域网其他电脑(含 Win7 + Chrome)直接访问同样地址,无需装任何客户端。

## 打包说明(packaging/)

```
build-release.sh              一键打包脚本 → packaging/dist/
templates/win/                Windows 启动脚本与使用说明模板
templates/linux/              Linux 启动脚本、桌面图标生成、说明模板
jres/linux-x64|linux-arm64/   (可选)放入对应架构 JRE8 后,打包时自动内置
```
Windows 包自动使用 `.tools/` 里的 Zulu JRE8;Linux 内置 JRE 包需先准备好 JRE
(Temurin/Zulu 均提供 x64 与 aarch64 的 JRE8),放入 `packaging/jres/` 后重跑脚本。

## 安全提示
- 首次运行向导会强制设置管理员密码;`--no-ui` 无界面模式跳过向导时才保留默认密码 `admin123`,此时务必登录后修改(托盘菜单"修改管理员密码"也可随时重置,避免遗忘)
- 密码 PBKDF2 加密存储,连续输错有锁定保护
- 服务端令牌有效期默认 24 小时,可改 `application.yml` 中 `app.auth.token-expire-hours`
- 照片存放于服务端 `data/photos/`,数据库为 `data/personnel.db`;备份/迁移 = 拷贝整个程序文件夹

## 开发环境要求
- JDK 8(编译目标 1.8,保证 Win7 / 麒麟 JRE8 可运行;仓库 `.tools/` 已自带)
- Maven 3.6+(仓库 `.tools/` 已自带);网页端无构建依赖
