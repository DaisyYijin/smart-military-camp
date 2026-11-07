package com.szjx.personnel.server.service;

import com.szjx.personnel.server.util.Db;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 方言改写层单元验证(不连真库):
 * 以 SQLite 写法的 SQL/DDL 为输入,断言 MySQL/PostgreSQL/达梦 下 Db.rewrite()/rewriteDdl()
 * 的转换结果正确。四库真机冒烟需外部实例(MySQL/PG/金仓/达梦),由部署方按发行说明执行。
 */
class DbRewriteTest {

    @AfterEach
    void reset() {
        Db.setDialect("sqlite");
    }

    @Test
    void sqliteDialectPassesThroughUnchanged() {
        Db.setDialect("sqlite");
        String sql = "UPDATE personnel SET status='在位', updated_at=datetime('now','localtime') WHERE id=?";
        assertEquals(sql, Db.rewrite(sql));
        String ddl = "CREATE TABLE IF NOT EXISTS t(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT '')";
        assertEquals(ddl, Db.rewriteDdl(ddl));
    }

    @Test
    void mysqlRewritesTimeFunctionsAndYearPrefix() {
        Db.setDialect("mysql");
        assertTrue(Db.rewrite("UPDATE personnel SET updated_at=datetime('now','localtime') WHERE id=?")
                .contains("updated_at=NOW()"));
        assertTrue(Db.rewrite("SELECT * FROM leave_record WHERE start_date LIKE strftime('%Y','now','localtime') || '%'")
                .contains("CONCAT(YEAR(CURDATE()),'%')"));
        assertTrue(Db.rewrite("SELECT COUNT(*) FROM t WHERE d <= date('now','localtime','-30 days')")
                .contains("DATE_SUB(CURDATE(), INTERVAL 30 DAY)"));
        assertTrue(Db.rewrite("SELECT COUNT(*) FROM t WHERE d = date('now','localtime')")
                .contains("CURDATE()"));
        assertFalse(Db.rewrite("SELECT id_card FROM personnel WHERE id_card = ? COLLATE NOCASE")
                .contains("COLLATE NOCASE"));
    }

    @Test
    void postgresqlRewritesToToChar() {
        Db.setDialect("postgresql");
        String s = Db.rewrite("UPDATE personnel SET updated_at=datetime('now','localtime') WHERE id=?");
        assertTrue(s.contains("to_char(LOCALTIMESTAMP,'YYYY-MM-DD HH24:MI:SS')"));
        assertTrue(Db.rewrite("SELECT strftime('%Y',p.entry_date) FROM personnel p")
                .contains("to_char(p.entry_date,'YYYY')"));
        assertTrue(Db.rewrite("SELECT * FROM t WHERE d <= date('now','localtime','-1 day')")
                .contains("INTERVAL '1 day'"));
    }

    @Test
    void ddlPrimaryKeyAndTextDefaultsPerDialect() {
        Db.setDialect("mysql");
        String mysqlDdl = Db.rewriteDdl(
                "CREATE TABLE IF NOT EXISTS person(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT '', "
                + "created_at TEXT DEFAULT (datetime('now','localtime')), detail TEXT)");
        assertTrue(mysqlDdl.contains("BIGINT AUTO_INCREMENT PRIMARY KEY"));
        assertTrue(mysqlDdl.contains("name VARCHAR(255) DEFAULT ''"));
        assertTrue(mysqlDdl.contains("VARCHAR(32) DEFAULT (NOW())"));

        Db.setDialect("postgresql");
        String pgDdl = Db.rewriteDdl(
                "CREATE TABLE IF NOT EXISTS person(id INTEGER PRIMARY KEY AUTOINCREMENT, created_at TEXT DEFAULT (datetime('now','localtime')))");
        assertTrue(pgDdl.contains("BIGSERIAL PRIMARY KEY"));
        assertTrue(pgDdl.contains("TEXT DEFAULT to_char(LOCALTIMESTAMP,'YYYY-MM-DD HH24:MI:SS')"));

        Db.setDialect("dm");
        String dmDdl = Db.rewriteDdl("CREATE TABLE IF NOT EXISTS person(id INTEGER PRIMARY KEY AUTOINCREMENT)");
        assertTrue(dmDdl.contains("IDENTITY(1,1) PRIMARY KEY"));
        assertFalse(dmDdl.contains("IF NOT EXISTS"));
    }
}
