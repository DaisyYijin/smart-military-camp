package com.szjx.personnel.server.service;

import com.szjx.personnel.server.config.SchemaInitializer;
import com.szjx.personnel.server.dao.AdminUserDao;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SchemaMigrationSafetyTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private SchemaInitializer initializer;

    @BeforeEach void setup() {
        jdbc = AccountServiceTest.database(temp.resolve("migration.sqlite"));
        jdbc.execute("CREATE TABLE sys_dict(id INTEGER PRIMARY KEY,dict_type TEXT NOT NULL,dict_label TEXT NOT NULL,sort INTEGER,enabled INTEGER,remark TEXT,parent_id INTEGER,created_at TEXT,updated_at TEXT,UNIQUE(dict_type,dict_label))");
        jdbc.update("INSERT INTO sys_dict VALUES(1,'test','preserve',0,1,'note',0,'created','updated')");
        initializer = new SchemaInitializer(jdbc, new AdminUserDao(jdbc));
    }

    @Test void successfulDictionaryMigrationIsIdempotentAndKeepsRowsAndIds() {
        Object dictSeed = ReflectionTestUtils.getField(initializer, "dictSeed");
        ReflectionTestUtils.invokeMethod(dictSeed, "migrateDictUnique");
        ReflectionTestUtils.invokeMethod(dictSeed, "migrateDictUnique");
        assertEquals("note", jdbc.queryForObject("SELECT remark FROM sys_dict WHERE id=1", String.class));
        jdbc.update("INSERT INTO sys_dict(id,dict_type,dict_label,parent_id) VALUES(2,'test','preserve',10)");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict", Integer.class));
    }

    @Test void eduFormUpgradesQuanRiZhiAndKeepsFallback() {
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, edu_form TEXT DEFAULT '')");
        jdbc.update("INSERT INTO sys_dict(id,dict_type,dict_label,sort,parent_id) VALUES(20,'edu_form','全日制',1,0)");
        jdbc.update("INSERT INTO sys_dict(id,dict_type,dict_label,sort,parent_id) VALUES(21,'edu_form','非全日制',2,0)");
        jdbc.update("INSERT INTO personnel(name,edu_form) VALUES('张三','全日制')");
        jdbc.update("INSERT INTO personnel(name,edu_form) VALUES('李四','非全日制')");
        Object dictSeed = ReflectionTestUtils.getField(initializer, "dictSeed");
        ReflectionTestUtils.invokeMethod(dictSeed, "migrateEduForm");
        ReflectionTestUtils.invokeMethod(dictSeed, "migrateEduForm"); // 幂等
        assertEquals("普通全日制", jdbc.queryForObject("SELECT dict_label FROM sys_dict WHERE id=20", String.class), "旧项沿用 id 改名");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict WHERE dict_type='edu_form' AND dict_label='全日制'", Integer.class));
        assertEquals("普通全日制", jdbc.queryForObject("SELECT edu_form FROM personnel WHERE name='张三'", String.class), "人员历史值同步");
        assertEquals("非全日制", jdbc.queryForObject("SELECT edu_form FROM personnel WHERE name='李四'", String.class), "非全日制兜底保留");
    }

    @Test void vehiclePriceMillionToYuanMigrationIsIdempotent() {
        jdbc.execute("CREATE TABLE private_vehicle(id INTEGER PRIMARY KEY AUTOINCREMENT, price TEXT DEFAULT '')");
        jdbc.update("INSERT INTO private_vehicle(price) VALUES('15')");
        jdbc.update("INSERT INTO private_vehicle(price) VALUES('12.5')");
        jdbc.update("INSERT INTO private_vehicle(price) VALUES('150000')");
        jdbc.update("INSERT INTO private_vehicle(price) VALUES('')");
        String sql = "UPDATE private_vehicle SET price = CAST(CAST(price AS REAL) * 10000 AS INTEGER) " +
                "WHERE TRIM(IFNULL(price,'')) <> '' AND CAST(price AS REAL) > 0 AND CAST(price AS REAL) < 1000";
        jdbc.update(sql);
        jdbc.update(sql);
        assertEquals("150000", jdbc.queryForObject("SELECT price FROM private_vehicle WHERE id=1", String.class));
        assertEquals("125000", jdbc.queryForObject("SELECT price FROM private_vehicle WHERE id=2", String.class));
        assertEquals("150000", jdbc.queryForObject("SELECT price FROM private_vehicle WHERE id=3", String.class), "已是元的值不受影响");
        assertEquals("", jdbc.queryForObject("SELECT price FROM private_vehicle WHERE id=4", String.class));
    }

    @Test void bloodTypeUpgradeKeepsDictIdMigratesPersonnelAndIsIdempotent() {
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, blood_type TEXT DEFAULT '')");
        jdbc.update("INSERT INTO sys_dict(id,dict_type,dict_label,sort,parent_id) VALUES(10,'blood_type','A',2,0)");
        jdbc.update("INSERT INTO sys_dict(id,dict_type,dict_label,sort,parent_id) VALUES(11,'blood_type','B',3,0)");
        jdbc.update("INSERT INTO sys_dict(id,dict_type,dict_label,sort,parent_id) VALUES(12,'blood_type','AB',4,0)");
        jdbc.update("INSERT INTO sys_dict(id,dict_type,dict_label,sort,parent_id) VALUES(13,'blood_type','O',5,0)");
        jdbc.update("INSERT INTO personnel(name,blood_type) VALUES('张三','A')");
        jdbc.update("INSERT INTO personnel(name,blood_type) VALUES('李四','O')");
        Object dictSeed = ReflectionTestUtils.getField(initializer, "dictSeed");
        ReflectionTestUtils.invokeMethod(dictSeed, "migrateBloodTypeRh");
        ReflectionTestUtils.invokeMethod(dictSeed, "migrateBloodTypeRh"); // 重复执行必须无副作用
        // 旧字典项沿用原 id 改名为 RH+ 表述
        assertEquals("A型RH+", jdbc.queryForObject("SELECT dict_label FROM sys_dict WHERE id=10", String.class));
        assertEquals("O型RH+", jdbc.queryForObject("SELECT dict_label FROM sys_dict WHERE id=13", String.class));
        // 旧值全部消失,不再出现裸 ABO 简称
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict WHERE dict_type='blood_type' AND dict_label IN ('A','B','AB','O')", Integer.class));
        // 人员历史值同步为 RH 阳性新称
        assertEquals("A型RH+", jdbc.queryForObject("SELECT blood_type FROM personnel WHERE name='张三'", String.class));
        assertEquals("O型RH+", jdbc.queryForObject("SELECT blood_type FROM personnel WHERE name='李四'", String.class));
    }

    @Test void failureAfterCopyRollsBackDictionaryAndTemporaryTable() {
        JdbcTemplate failing = new JdbcTemplate(jdbc.getDataSource()) {
            @Override public void execute(String sql) {
                if ("ALTER TABLE sys_dict_mig RENAME TO sys_dict".equals(sql)) throw new IllegalStateException("simulated rename failure");
                super.execute(sql);
            }
        };
        Object dictSeed = ReflectionTestUtils.getField(new SchemaInitializer(failing, new AdminUserDao(failing)), "dictSeed");
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(dictSeed, "migrateDictUnique"));
        assertEquals("preserve", jdbc.queryForObject("SELECT dict_label FROM sys_dict WHERE id=1", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE name='sys_dict_mig'", Integer.class));
    }

    @Test void leaveLocationColumnMigrationPreservesLegacyRowsAndIsIdempotent() {
        jdbc.execute("CREATE TABLE leave_record(id INTEGER PRIMARY KEY, remark TEXT DEFAULT '')");
        jdbc.update("INSERT INTO leave_record(id,remark) VALUES(7,'保留记录')");
        ReflectionTestUtils.invokeMethod(initializer, "ensureColumn", "leave_record", "leave_location", "leave_location TEXT DEFAULT ''");
        ReflectionTestUtils.invokeMethod(initializer, "ensureColumn", "leave_record", "leave_location", "leave_location TEXT DEFAULT ''");
        assertEquals("保留记录", jdbc.queryForObject("SELECT remark FROM leave_record WHERE id=7", String.class));
        assertEquals("", jdbc.queryForObject("SELECT leave_location FROM leave_record WHERE id=7", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM leave_record", Integer.class));
    }

    @Test void militaryIdentityMigrationLeavesOldRowsEmptyAndAllowsOldRestoreWithoutFabricatingValues() {
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY,name TEXT,id_card TEXT)");
        jdbc.update("INSERT INTO personnel VALUES(7,'旧人员','原身份证')");
        for (int i = 0; i < 2; i++) {
            ReflectionTestUtils.invokeMethod(initializer, "ensureColumn", "personnel", "military_id_no", "military_id_no TEXT DEFAULT ''");
        }
        assertEquals("", jdbc.queryForObject("SELECT military_id_no FROM personnel WHERE id=7", String.class));
        assertEquals("原身份证", jdbc.queryForObject("SELECT id_card FROM personnel WHERE id=7", String.class));
        jdbc.update("INSERT INTO personnel(id,name,id_card) VALUES(8,'旧备份恢复','另一个身份证')");
        assertEquals("", jdbc.queryForObject("SELECT military_id_no FROM personnel WHERE id=8", String.class));
        jdbc.update("UPDATE personnel SET military_id_no='000文本军证' WHERE id=7");
        ReflectionTestUtils.invokeMethod(initializer, "ensureColumn", "personnel", "military_id_no", "military_id_no TEXT DEFAULT ''");
        assertEquals("000文本军证", jdbc.queryForObject("SELECT military_id_no FROM personnel WHERE id=7", String.class));
        assertFalse(jdbc.queryForObject("SELECT sql FROM sqlite_master WHERE name='personnel'", String.class).toUpperCase(java.util.Locale.ROOT).contains("CHECK"));
    }

    @Test void vehicleFaultHistoryTableMigrationIsIdempotent() {
        jdbc.execute("CREATE TABLE vehicle(id INTEGER PRIMARY KEY, driver_id INTEGER DEFAULT 0)");
        jdbc.update("INSERT INTO vehicle(id,driver_id) VALUES(7,0)");
        ReflectionTestUtils.invokeMethod(initializer, "initializeVehicleFaultHistory");
        ReflectionTestUtils.invokeMethod(initializer, "initializeVehicleFaultHistory");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vehicle", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='vehicle_fault_history'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_vehicle_fault_history_vehicle'", Integer.class));
    }

    @Test void doubleExcellenceMigrationIsIdempotentSoftLinkedAndPreservesExistingTables() {
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY,name TEXT,id_card TEXT)");
        jdbc.update("INSERT INTO personnel VALUES(7,'原人员','原身份证')");
        ReflectionTestUtils.invokeMethod(initializer,"initializeDoubleExcellence");
        jdbc.update("INSERT INTO double_excellence_record(id,personnel_id,year,quarter,rating,rating_date) VALUES(9,7,2026,1,'好','2026-10-18')");
        ReflectionTestUtils.invokeMethod(initializer,"initializeDoubleExcellence");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM double_excellence_record",Integer.class));
        assertEquals("原身份证",jdbc.queryForObject("SELECT id_card FROM personnel WHERE id=7",String.class));
        assertTrue(jdbc.queryForList("PRAGMA foreign_key_list(double_excellence_record)").isEmpty());
        assertThrows(RuntimeException.class, () -> jdbc.update("INSERT INTO double_excellence_record(personnel_id,year,quarter,rating,rating_date) VALUES(7,2026,1,'中','2026-10-19')"));
        assertThrows(RuntimeException.class, () -> jdbc.update("INSERT INTO double_excellence_record(personnel_id,year,quarter,rating,rating_date) VALUES(7,2026,5,'中','2026-10-19')"));
        assertThrows(RuntimeException.class, () -> jdbc.update("INSERT INTO double_excellence_record(personnel_id,year,quarter,rating,rating_date) VALUES(7,1899,1,'中','2026-10-19')"));
        // 评定合法值改由服务层按数据字典(de_rating)校验:表不再带固定评级 CHECK,自定义/历史评级字符串在库层可留存
        jdbc.update("INSERT INTO double_excellence_record(personnel_id,year,quarter,rating,rating_date) VALUES(7,2026,2,'自定义评级','2026-10-19')");
        jdbc.update("DELETE FROM double_excellence_record WHERE personnel_id=7 AND quarter=2");
        jdbc.update("DELETE FROM personnel WHERE id=7");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM double_excellence_record",Integer.class),"SQL-only partial restore must never cascade-delete ratings");
    }

    @Test void requiredColumnMigrationFailureIsNotSilentlyIgnored() {
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(initializer,
                "ensureColumn", "sys_dict", "broken", "broken INTEGER UNIQUE"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict", Integer.class));
    }
}
