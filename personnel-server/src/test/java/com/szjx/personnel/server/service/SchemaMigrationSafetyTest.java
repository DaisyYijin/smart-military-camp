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
