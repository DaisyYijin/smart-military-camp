package com.szjx.personnel.server.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 外部数据库连接配置(data/db-config.json):
 *  - 单机默认 SQLite:文件不存在或 dialect=sqlite 时走内嵌库(零配置,行为与之前完全一致)。
 *  - 局域网/服务器可选 mysql|postgresql|kingbase|dm:向导写入配置,驱动 jar 放运行目录 lib/ 下自动加载
 *    (离线内网无 Maven 仓库,所有非 SQLite 驱动一律外置,发行包不捆绑)。
 */
public final class DbProfile {

    public String dialect = "sqlite";
    public String host = "";
    public String port = "";
    public String database = "";
    public String user = "";
    public String password = "";

    private static final String[] DRIVER_HINTS = {
            "com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver",               // MySQL/MariaDB
            "org.postgresql.Driver",                                            // PostgreSQL
            "com.kingbase8.Driver",                                             // 人大金仓
            "dm.jdbc.driver.DmDriver"                                           // 达梦
    };

    public static File file() {
        return new File("data/db-config.json");
    }

    public static DbProfile load() {
        try {
            if (file().exists()) {
                DbProfile p = new ObjectMapper().readValue(file(), DbProfile.class);
                if (p.dialect != null && !p.dialect.trim().isEmpty()) { return p; }
            }
        } catch (Exception ignore) { /* 配置损坏按默认 SQLite */ }
        return new DbProfile();
    }

    public void save() throws java.io.IOException {
        new File("data").mkdirs();
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file(), this);
    }

    public boolean external() {
        return !"sqlite".equals(dialect);
    }

    /** 按方言构造 JDBC URL(外部库)。 */
    public String url() {
        String h = host == null ? "" : host.trim();
        String p = port == null ? "" : port.trim();
        String d = database == null ? "" : database.trim();
        if ("mysql".equals(dialect)) {
            return "jdbc:mysql://" + h + ":" + p + "/" + d
                    + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        }
        if ("postgresql".equals(dialect)) {
            return "jdbc:postgresql://" + h + ":" + p + "/" + d;
        }
        if ("kingbase".equals(dialect)) {
            return "jdbc:kingbase8://" + h + ":" + p + "/" + d;
        }
        if ("dm".equals(dialect)) {
            return "jdbc:dm://" + h + ":" + p + "?schema=" + d;
        }
        return "";
    }

    /** 加载运行目录 lib/ 下的全部驱动 jar 并注册(幂等;SQLite 内嵌驱动无需外置)。 */
    public static void loadExternalDrivers() {
        File lib = new File("lib");
        File[] jars = lib.isDirectory() ? lib.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar")) : null;
        if (jars == null || jars.length == 0) { return; }
        try {
            URL[] urls = new URL[jars.length];
            for (int i = 0; i < jars.length; i++) { urls[i] = jars[i].toURI().toURL(); }
            URLClassLoader loader = new URLClassLoader(urls, DbProfile.class.getClassLoader());
            for (String hint : DRIVER_HINTS) {
                try { Class.forName(hint, true, loader); } catch (ClassNotFoundException ignore) { /* 该 jar 不在 lib */ }
            }
        } catch (Exception ignore) { /* 驱动加载失败由后续连接报错呈现 */ }
    }

    /** 测试连接(向导"测试连接"按钮):返回 null=成功,否则为错误信息。 */
    public String testConnection() {
        if (!external()) { return null; }
        try {
            loadExternalDrivers();
            Connection c = DriverManager.getConnection(url(), user, password);
            c.close();
            return null;
        } catch (Exception e) {
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    /** 供序列化与向导展示:脱敏摘要。 */
    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("dialect", dialect);
        m.put("host", host);
        m.put("port", port);
        m.put("database", database);
        m.put("user", user);
        return m;
    }

    private DbProfile() { }
}
