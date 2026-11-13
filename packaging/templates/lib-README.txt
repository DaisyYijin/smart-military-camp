数智军营人员管理系统 · 驱动目录（lib/）

本目录用于放置外部数据库驱动 jar。程序启动时会自动扫描 lib/*.jar 并注册驱动。

SQLite（默认内置）：无需任何驱动，本目录可留空。

如需连接以下数据库，请向单位网管索取对应版本的驱动 jar 放入本目录：

  MySQL         mysql-connector-j-8.x.jar
  PostgreSQL    postgresql-42.x.jar
  人大金仓      kingbase8-8.x.jar
  达梦          DmJdbcDriver18.jar / DmJdbcDriver17.jar

放置后重启程序，在启动设置页「数据库」一节选择对应类型并填写连接信息，
点「测试连接」验证通过后保存即可切换。切回内置库时选「内置 SQLite」。

注意：
- 驱动版本需与目标数据库版本匹配（MySQL 8 建议 Connector/J 8.x）
- 驱动 jar 不会随本发行包提供（许可与版本管理原因），请从官方渠道获取
