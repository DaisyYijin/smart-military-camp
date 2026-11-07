package com.szjx.personnel.server.util;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库方言自适应工具(多数据库支持的地基):
 *  - id():          插入并取自增主键 —— SQLite/PG/金仓原生执行 "...RETURNING id";
 *                   MySQL/达梦不支持 RETURNING,剥掉后经 getGeneratedKeys 回取。
 *  - columns():     表列清单 —— SQLite 用 PRAGMA table_info;达梦用 USER_TAB_COLUMNS;
 *                   MySQL/PG/金仓用 information_schema。返回行的键统一为小写(name)。
 *  - tableExists(): 表存在性判断(建表幂等)。
 *  - rewrite():     DML 方言改写 —— datetime/date/strftime 时间函数与年份拼接按库转换;
 *                   SQLite 直通零开销。
 *  - rewriteDdl():  建表方言改写 —— 自增主键/TEXT 类型与时间默认值按库映射;达梦剥 IF NOT EXISTS。
 *  - createTable(): 建表入口(内部走 rewriteDdl;达梦先探存在再建)。
 *  - insertIgnore():幂等插入 —— MySQL 转 INSERT IGNORE;PG/金仓剥 OR IGNORE 尾挂 ON CONFLICT DO NOTHING;
 *                   达梦剥掉后捕获唯一冲突忽略。
 *  dialect(): 当前方言(sqlite|mysql|postgresql|kingbase|dm),由启动配置注入,默认 sqlite。
 */
public final class Db {

    private static volatile String dialect = System.getProperty("app.db.dialect", "sqlite");

    public static void setDialect(String d) {
        dialect = d == null || d.trim().isEmpty() ? "sqlite" : d.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static String dialect() { return dialect; }

    public static boolean sqlite() { return "sqlite".equals(dialect); }

    private static boolean mysqlLike() { return "mysql".equals(dialect) || "dm".equals(dialect); }

    /* ================= 插入取主键 ================= */

    /** 插入并返回自增主键。sql 传带 "RETURNING id" 尾缀的语句(各库统一写法)。 */
    public static long id(JdbcTemplate jdbc, String sql, Object... args) {
        if (!mysqlLike()) {
            Long v = jdbc.queryForObject(sql, Long.class, args);
            return v == null ? 0L : v.longValue();
        }
        final String stripped = sql.replaceFirst("(?i)\\s*RETURNING\\s+id\\s*$", "");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(stripped, PreparedStatement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, kh);
        Number k = kh.getKey();
        return k == null ? 0L : k.longValue();
    }

    /* ================= 元信息 ================= */

    /** 表的列信息清单:行的键统一转小写(PRAGMA 返回 name;标准库返回 column_name 别名 name)。 */
    public static List<Map<String, Object>> columns(JdbcTemplate jdbc, String table) {
        List<Map<String, Object>> raw;
        if (sqlite()) {
            raw = jdbc.queryForList("PRAGMA table_info(" + table + ")");
        } else if ("dm".equals(dialect)) {
            raw = jdbc.queryForList("SELECT COLUMN_NAME AS name FROM USER_TAB_COLUMNS WHERE TABLE_NAME = UPPER(?)", table);
        } else {
            raw = jdbc.queryForList(
                    "SELECT column_name AS name FROM information_schema.columns WHERE lower(table_name)=lower(?)", table);
        }
        return normalizeKeys(raw);
    }

    /** 表是否存在(建表幂等判断;达梦不支持 IF NOT EXISTS 时使用)。 */
    public static boolean tableExists(JdbcTemplate jdbc, String table) {
        if (sqlite()) {
            Long n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", Long.class, table);
            return n != null && n > 0;
        }
        if ("dm".equals(dialect)) {
            Long n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME = UPPER(?)", Long.class, table);
            return n != null && n > 0;
        }
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name)=lower(?)", Long.class, table);
        return n != null && n > 0;
    }

    private static List<Map<String, Object>> normalizeKeys(List<Map<String, Object>> raw) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(raw.size());
        for (Map<String, Object> row : raw) {
            Map<String, Object> normalized = new HashMap<String, Object>();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                normalized.put(String.valueOf(e.getKey()).toLowerCase(java.util.Locale.ROOT), e.getValue());
            }
            out.add(normalized);
        }
        return out;
    }

    /* ================= DML 方言改写 ================= */

    private static final Pattern P_NOW = Pattern.compile("datetime\\('now','localtime'\\)");
    private static final Pattern P_DATE_PLAIN = Pattern.compile("date\\('now','localtime'\\)");
    private static final Pattern P_DATE_MOD = Pattern.compile("date\\('now','localtime','([+-])(\\d+) (day|month|year)s?'\\)");
    private static final Pattern P_STRFTIME_NOW_LIKE = Pattern.compile("strftime\\('%Y','now','localtime'\\) \\|\\| '%'");
    private static final Pattern P_STRFTIME_Y_EXPR = Pattern.compile("strftime\\('%Y',\\s*([A-Za-z0-9_.]+)\\)");
    private static final Pattern P_YEAR_FUNC = Pattern.compile("YEAR\\(CURDATE\\(\\)\\)");

    /** DML 方言改写。SQLite 直通;命中方言特征才做替换。 */
    public static String rewrite(String sql) {
        if (sqlite() || sql == null) { return sql; }
        if (sql.indexOf("datetime('now") < 0 && sql.indexOf("date('now") < 0
                && sql.indexOf("strftime") < 0 && sql.indexOf("COLLATE NOCASE") < 0) {
            return sql;
        }
        String s = sql;
        // 1) 今年前缀 LIKE: strftime('%Y','now','localtime') || '%'
        Matcher m = P_STRFTIME_NOW_LIKE.matcher(s);
        if (m.find()) {
            String rep = mysqlLike()
                    ? "CONCAT(YEAR(CURDATE()),'%')"
                    : ("dm".equals(dialect) ? "TO_CHAR(SYSDATE,'YYYY')||'%'" : "to_char(CURRENT_DATE,'YYYY')||'%'");
            s = m.replaceAll(Matcher.quoteReplacement(rep));
        }
        // 2) strftime('%Y', expr) 取年份
        m = P_STRFTIME_Y_EXPR.matcher(s);
        if (m.find()) {
            String rep = mysqlLike() ? "DATE_FORMAT($1,'%Y')" : "to_char($1,'YYYY')";
            s = m.replaceAll(rep);
        }
        // 3) datetime('now','localtime')
        if (P_NOW.matcher(s).find()) {
            String rep = mysqlLike() ? "NOW()"
                    : ("dm".equals(dialect) ? "TO_CHAR(SYSDATE,'YYYY-MM-DD HH24:MI:SS')"
                        : "to_char(LOCALTIMESTAMP,'YYYY-MM-DD HH24:MI:SS')");
            s = P_NOW.matcher(s).replaceAll(Matcher.quoteReplacement(rep));
        }
        // 4) date('now','localtime','±N unit')
        m = P_DATE_MOD.matcher(s);
        StringBuffer sb = new StringBuffer(); // JDK8 的 appendReplacement 仅接受 StringBuffer
        while (m.find()) {
            String sign = m.group(1);
            int n = Integer.parseInt(m.group(2));
            String unit = m.group(3).toUpperCase(java.util.Locale.ROOT);
            String rep;
            if (mysqlLike()) {
                String fn = "-".equals(sign) ? "DATE_SUB" : "DATE_ADD";
                rep = fn + "(CURDATE(), INTERVAL " + n + " " + unit + ")";
            } else if ("dm".equals(dialect)) {
                rep = "TRUNC(SYSDATE" + ("-".equals(sign) ? "-" : "+") + n + ")";
            } else {
                rep = "CAST(CURRENT_DATE " + ("-".equals(sign) ? "-" : "+") + " INTERVAL '" + n + " " + unit.toLowerCase(java.util.Locale.ROOT) + "' AS TEXT)";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        s = sb.toString();
        // 5) date('now','localtime')
        if (P_DATE_PLAIN.matcher(s).find()) {
            String rep = mysqlLike() ? "CURDATE()"
                    : ("dm".equals(dialect) ? "TRUNC(SYSDATE)" : "CAST(CURRENT_DATE AS TEXT)");
            s = P_DATE_PLAIN.matcher(s).replaceAll(Matcher.quoteReplacement(rep));
        }
        // 6) 剥 SQLite 的大小写不敏感标注(身份证号均为数字,等值比较不受影响)
        s = s.replace(" COLLATE NOCASE", "");
        return s;
    }

    /* ================= DDL 方言改写 ================= */

    private static final Pattern P_PK_AUTOINC = Pattern.compile("INTEGER PRIMARY KEY AUTOINCREMENT");
    private static final Pattern P_TEXT_NOW_DEFAULT = Pattern.compile("TEXT DEFAULT \\(datetime\\('now','localtime'\\)\\)");
    private static final Pattern P_TEXT_DEFAULT = Pattern.compile("TEXT( NOT NULL)?( DEFAULT ('[^']*'|[0-9]+))");

    /** 建表方言改写:自增主键/TEXT 类型与时间默认值映射;达梦剥 IF NOT EXISTS(配合 createTable 先探存在)。 */
    public static String rewriteDdl(String ddl) {
        if (sqlite()) { return ddl; }
        String s = ddl;
        // 0) TEXT DEFAULT (datetime('now','localtime')) → 必须先于通用时间改写(否则 rewrite 会先把
        //    datetime 换成 NOW(),导致本条 DDL 专属映射落空、时间列类型不对)
        if (P_TEXT_NOW_DEFAULT.matcher(s).find()) {
            String rep;
            if ("mysql".equals(dialect)) { rep = "VARCHAR(32) DEFAULT (NOW())"; }
            else if ("dm".equals(dialect)) { rep = "VARCHAR(32) DEFAULT TO_CHAR(SYSDATE,'YYYY-MM-DD HH24:MI:SS')"; }
            else { rep = "TEXT DEFAULT to_char(LOCALTIMESTAMP,'YYYY-MM-DD HH24:MI:SS')"; }
            s = P_TEXT_NOW_DEFAULT.matcher(s).replaceAll(Matcher.quoteReplacement(rep));
        }
        // 1) 自增主键
        if (P_PK_AUTOINC.matcher(s).find()) {
            String pk;
            if ("mysql".equals(dialect)) { pk = "BIGINT AUTO_INCREMENT PRIMARY KEY"; }
            else if ("dm".equals(dialect)) { pk = "BIGINT IDENTITY(1,1) PRIMARY KEY"; }
            else { pk = "BIGSERIAL PRIMARY KEY"; } // pg / kingbase
            s = P_PK_AUTOINC.matcher(s).replaceAll(Matcher.quoteReplacement(pk));
        }
        // 2) TEXT DEFAULT 常量 → MySQL/达梦 VARCHAR(255)(TEXT 列不能带常量默认)
        if (mysqlLike()) {
            s = P_TEXT_DEFAULT.matcher(s).replaceAll("VARCHAR(255)$1$2");
        }
        // 3) 达梦不支持 IF NOT EXISTS(建表前由 createTable 探存在)
        if ("dm".equals(dialect)) {
            s = s.replaceFirst("(?i)CREATE TABLE IF NOT EXISTS", "CREATE TABLE");
        }
        // 4) 兜底:余下如有通用时间函数(理论已无)再走 DML 改写
        return rewrite(s);
    }

    /** 建表入口:统一走 rewriteDdl;达梦先探存在再建(其不支持 IF NOT EXISTS)。 */
    public static void createTable(JdbcTemplate jdbc, String ddl) {
        if ("dm".equals(dialect)) {
            Matcher t = Pattern.compile("(?i)CREATE TABLE IF NOT EXISTS\\s+(\\w+)").matcher(ddl);
            if (t.find() && tableExists(jdbc, t.group(1))) { return; }
        }
        jdbc.execute(rewriteDdl(ddl));
    }

    /* ================= 幂等插入 ================= */

    /** 幂等插入("INSERT OR IGNORE INTO ..."):冲突时静默跳过。MySQL→INSERT IGNORE;
     *  PG/金仓→剥 OR IGNORE 尾挂 ON CONFLICT DO NOTHING;达梦→剥掉后捕获唯一冲突忽略。 */
    public static int insertIgnore(JdbcTemplate jdbc, String sql, Object... args) {
        if (sqlite()) {
            return jdbc.update(sql, args);
        }
        if ("mysql".equals(dialect)) {
            return jdbc.update(sql.replaceFirst("(?i)INSERT OR IGNORE INTO", "INSERT IGNORE INTO"), args);
        }
        String stripped = sql.replaceFirst("(?i)INSERT OR IGNORE INTO", "INSERT INTO");
        if ("dm".equals(dialect)) {
            try {
                return jdbc.update(stripped, args);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                return 0; // 已存在:幂等跳过
            }
        }
        return jdbc.update(stripped + " ON CONFLICT DO NOTHING", args); // pg / kingbase
    }

    /** 设置项 upsert(先改后插,四库通用):命中 1 行即成功,否则插入(并发下重复键时再改一次兜底)。 */
    public static void upsertSetting(JdbcTemplate jdbc, String table, String key, String value) {
        int n = jdbc.update("UPDATE " + table + " SET svalue=? WHERE skey=?", value, key);
        if (n == 0) {
            try {
                jdbc.update("INSERT INTO " + table + "(skey, svalue) VALUES(?,?)", key, value);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                jdbc.update("UPDATE " + table + " SET svalue=? WHERE skey=?", value, key);
            }
        }
    }

    private Db() { }
}
