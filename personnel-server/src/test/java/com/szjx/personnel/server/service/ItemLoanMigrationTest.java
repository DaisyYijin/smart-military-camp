package com.szjx.personnel.server.service;

import com.szjx.personnel.server.config.SchemaInitializer;
import com.szjx.personnel.server.dao.AdminUserDao;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemLoanMigrationTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    SchemaInitializer schema;

    @BeforeEach void setup() {
        jdbc=AccountServiceTest.database(temp.resolve("item-loan-migration.sqlite"));
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT)");
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY,name TEXT,id_card TEXT,phone TEXT,department_id INTEGER)");
        jdbc.update("INSERT INTO department VALUES(8,'历史单位')");
        schema=new SchemaInitializer(jdbc,new AdminUserDao(jdbc));
    }
    void migrate() { ReflectionTestUtils.invokeMethod(schema,"initializeItemLoans"); }

    @Test void freshSchemaAddsNonNullDefaultOwnerAndKeepsLegacyDepartmentColumn() {
        migrate(); migrate();
        assertHandlerColumns();
        Map<String,Object> owner=column("owner_user_id");
        assertEquals(1,((Number)owner.get("notnull")).intValue());
        assertEquals("0",String.valueOf(owner.get("dflt_value")));
        assertTrue(hasColumn("department_id"));
        assertTrue(hasColumn("owner_user_id"));
        assertTrue(hasColumn("expected_return_date"));
        assertTrue(hasColumn("actual_return_date"));
        Map<String,Object> returned=column("returned");
        assertEquals("INTEGER",returned.get("type"));
        assertEquals(1,((Number)returned.get("notnull")).intValue());
        assertEquals("0",String.valueOf(returned.get("dflt_value")));
        jdbc.update("INSERT INTO item_loan(item_name,quantity,unit,borrow_date) VALUES('legacy','1','件','2024-01-01')");
        assertEquals(0L,jdbc.queryForObject("SELECT owner_user_id FROM item_loan",Long.class));
        assertEquals(0L,jdbc.queryForObject("SELECT handler_id FROM item_loan",Long.class));
        assertEquals("",jdbc.queryForObject("SELECT handler_name FROM item_loan",String.class));
        assertEquals("",jdbc.queryForObject("SELECT handler_id_card FROM item_loan",String.class));
        for (String field : new String[]{"handler_id", "handler_name", "handler_id_card"}) {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                    "INSERT INTO item_loan(item_name,quantity,unit,borrow_date," + field + ") VALUES('null','1','件','2024-01-01',NULL)"));
        }
        assertEquals(0,jdbc.queryForObject("SELECT returned FROM item_loan",Integer.class));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "INSERT INTO item_loan(item_name,quantity,unit,borrow_date,returned) VALUES('null','1','件','2024-01-01',NULL)"));
    }

    @Test void upgradingExistingRecordsDoesNotInferOwnerFromLegacyDepartmentOrModifyRows() {
        jdbc.execute("CREATE TABLE item_loan(id INTEGER PRIMARY KEY,item_name TEXT,quantity TEXT,unit TEXT,"+
                "personnel_id INTEGER DEFAULT 0,borrow_date TEXT,expected_return_date TEXT,actual_return_date TEXT,"+
                "department_id INTEGER NOT NULL DEFAULT 0,registration_department TEXT NOT NULL DEFAULT '',created_at TEXT,updated_at TEXT)");
        jdbc.update("INSERT INTO item_loan VALUES(7,'old','2.00','件',0,'2024-02-29','2024-03-02','2024-03-01',8,'业务登记','created','updated')");
        migrate();
        assertEquals(0L,jdbc.queryForObject("SELECT owner_user_id FROM item_loan WHERE id=7",Long.class));
        assertEquals(8L,jdbc.queryForObject("SELECT department_id FROM item_loan WHERE id=7",Long.class));
        assertEquals("业务登记",jdbc.queryForObject("SELECT registration_department FROM item_loan WHERE id=7",String.class));
        assertEquals("2024-03-02",jdbc.queryForObject("SELECT expected_return_date FROM item_loan WHERE id=7",String.class));
        assertEquals("2024-03-01",jdbc.queryForObject("SELECT actual_return_date FROM item_loan WHERE id=7",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT returned FROM item_loan WHERE id=7",Integer.class));
        assertEquals("created",jdbc.queryForObject("SELECT created_at FROM item_loan WHERE id=7",String.class));
        assertEquals("updated",jdbc.queryForObject("SELECT updated_at FROM item_loan WHERE id=7",String.class));
        Map<String,Object> migrated = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=7");
        migrate(); migrate();
        assertEquals(migrated,jdbc.queryForMap("SELECT * FROM item_loan WHERE id=7"));
        jdbc.update("UPDATE item_loan SET returned=0 WHERE id=7");
        Map<String,Object> explicitUnreturned = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=7");
        migrate(); migrate();
        assertEquals(explicitUnreturned,jdbc.queryForMap("SELECT * FROM item_loan WHERE id=7"));
    }

    @Test void legacyReturnStateUsesOnlyNonemptyActualDateAndPreservesEveryLegacyValue() {
        jdbc.execute("CREATE TABLE item_loan(id INTEGER PRIMARY KEY,item_name TEXT,borrow_date TEXT," +
                "expected_return_date TEXT,actual_return_date TEXT,created_at TEXT,updated_at TEXT)");
        Object[] actualDates = {null, "", "2024-03-01", " "};
        for (int i=0;i<actualDates.length;i++) {
            jdbc.update("INSERT INTO item_loan VALUES(?,?,'borrowed','expected',?,'created','updated')",
                    i+1,"legacy-"+i,actualDates[i]);
        }
        java.util.List<Map<String,Object>> before = jdbc.queryForList(
                "SELECT id,item_name,borrow_date,expected_return_date,actual_return_date,created_at,updated_at FROM item_loan ORDER BY id");
        migrate();
        assertEquals(java.util.Arrays.asList(0,0,1,1),jdbc.queryForList("SELECT returned FROM item_loan ORDER BY id",Integer.class));
        assertEquals(before,jdbc.queryForList(
                "SELECT id,item_name,borrow_date,expected_return_date,actual_return_date,created_at,updated_at FROM item_loan ORDER BY id"));
        assertEquals("INTEGER",column("returned").get("type"));
        assertEquals(1,((Number)column("returned").get("notnull")).intValue());
        assertEquals("0",String.valueOf(column("returned").get("dflt_value")));
        java.util.List<Map<String,Object>> migrated = jdbc.queryForList("SELECT * FROM item_loan ORDER BY id");
        migrate(); migrate();
        assertEquals(migrated,jdbc.queryForList("SELECT * FROM item_loan ORDER BY id"));
    }

    @Test void existingExplicitReturnStatesNeverConsultContradictoryLegacyDates() {
        PersonnelIntegrityTest.itemLoanSchema(jdbc);
        jdbc.update("INSERT INTO item_loan(id,item_name,borrow_date,expected_return_date,actual_return_date,returned,borrower_migrated) " +
                "VALUES(1,'explicit-zero','borrowed','expected','2024-03-01',0,1),(2,'explicit-one','borrowed','expected','',1,1)");
        java.util.List<Map<String,Object>> before = jdbc.queryForList("SELECT * FROM item_loan ORDER BY id");
        migrate(); migrate();
        assertEquals(before,jdbc.queryForList("SELECT * FROM item_loan ORDER BY id"));
    }

    @Test void failedBackfillRollsBackNewColumnSoRetryStillMigratesLegacyState() {
        jdbc.execute("CREATE TABLE item_loan(id INTEGER PRIMARY KEY,item_name TEXT,actual_return_date TEXT)");
        jdbc.update("INSERT INTO item_loan VALUES(1,'legacy','2024-03-01')");
        jdbc.execute("CREATE TRIGGER deny_return_migration BEFORE UPDATE ON item_loan " +
                "BEGIN SELECT RAISE(ABORT,'injected migration failure'); END");
        assertThrows(RuntimeException.class,this::migrate);
        assertFalse(hasColumn("returned"));
        assertEquals("2024-03-01",jdbc.queryForObject("SELECT actual_return_date FROM item_loan",String.class));
        jdbc.execute("DROP TRIGGER deny_return_migration");
        migrate();
        assertEquals(1,jdbc.queryForObject("SELECT returned FROM item_loan",Integer.class));
        migrate();
        assertEquals(1,jdbc.queryForObject("SELECT returned FROM item_loan",Integer.class));
    }

    @Test void legacyHandlerMigrationLeavesHistoryEmptyWithoutInferringFromBorrowerOrOwner() {
        jdbc.update("INSERT INTO personnel VALUES(9,'历史借用人','证件','电话',8)");
        jdbc.execute("CREATE TABLE item_loan(id INTEGER PRIMARY KEY,item_name TEXT,personnel_id INTEGER," +
                "owner_user_id INTEGER NOT NULL DEFAULT 0,borrow_date TEXT,actual_return_date TEXT,returned INTEGER NOT NULL DEFAULT 0)");
        jdbc.update("INSERT INTO item_loan VALUES(1,'历史物品',9,9,'2024-02-29','2024-03-01',0)");
        migrate(); migrate();
        assertHandlerColumns();
        Map<String,Object> migrated = jdbc.queryForMap("SELECT * FROM item_loan");
        assertEquals(0L,((Number)migrated.get("handler_id")).longValue());
        assertEquals("",migrated.get("handler_name"));
        assertEquals("",migrated.get("handler_id_card"));
        assertEquals("历史借用人",migrated.get("borrower_name"));
        assertEquals(9L,((Number)migrated.get("owner_user_id")).longValue());
        assertEquals(0,((Number)migrated.get("returned")).intValue());
        assertEquals("2024-02-29",migrated.get("borrow_date"));
        assertEquals("2024-03-01",migrated.get("actual_return_date"));
        jdbc.update("UPDATE item_loan SET handler_id=999,handler_name='已保存经手人',handler_id_card='历史证件'");
        Map<String,Object> explicit = jdbc.queryForMap("SELECT * FROM item_loan");
        migrate(); migrate();
        assertEquals(explicit,jdbc.queryForMap("SELECT * FROM item_loan"));
    }

    private void assertHandlerColumns() {
        Map<String,Object> id = column("handler_id");
        assertEquals("INTEGER",id.get("type"));
        assertEquals(1,((Number)id.get("notnull")).intValue());
        assertEquals("0",String.valueOf(id.get("dflt_value")));
        Map<String,Object> name = column("handler_name");
        assertEquals("TEXT",name.get("type"));
        assertEquals(1,((Number)name.get("notnull")).intValue());
        assertEquals("''",String.valueOf(name.get("dflt_value")));
        Map<String,Object> card = column("handler_id_card");
        assertEquals("TEXT",card.get("type"));
        assertEquals(1,((Number)card.get("notnull")).intValue());
        assertEquals("''",String.valueOf(card.get("dflt_value")));
        assertTrue(jdbc.queryForList("PRAGMA foreign_key_list(item_loan)").isEmpty());
    }

    private boolean hasColumn(String name) {
        return jdbc.queryForList("PRAGMA table_info(item_loan)").stream().anyMatch(c->name.equals(c.get("name")));
    }
    private Map<String,Object> column(String name) {
        return jdbc.queryForList("PRAGMA table_info(item_loan)").stream().filter(c->name.equals(c.get("name"))).findFirst().orElseThrow(AssertionError::new);
    }
}
