package com.szjx.personnel.server.service;

import com.szjx.personnel.server.util.ItemLoanSnapshots;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemLoanSnapshotsTest {
    @TempDir Path temp;

    @Test void migrationRunsOnceAndNeverChangesBusinessFieldsOrManualSnapshots() {
        JdbcTemplate jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY,name TEXT,id_card TEXT,phone TEXT,department_id INTEGER)");
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT)");
        PersonnelIntegrityTest.itemLoanSchema(jdbc);
        jdbc.update("INSERT INTO department VALUES(7,'原单位')");
        jdbc.update("INSERT INTO personnel VALUES(9,'原姓名','原证件','原电话',7)");
        jdbc.update("INSERT INTO item_loan(id,item_name,personnel_id,borrow_date,expected_return_date,actual_return_date,returned," +
                "return_condition,return_remark,remark,created_at,updated_at) " +
                "VALUES(42,'工具',9,'2024-02-29','2024-03-02','2024-03-01',0,'完好','验收','备注','创建','更新')");
        jdbc.update("UPDATE item_loan SET handler_id=9,handler_name='原经手快照',handler_id_card='原经手证件'");
        Map<String,Object> before = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=42");
        ItemLoanSnapshots.migrateExisting(jdbc);
        Map<String,Object> first = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=42");
        assertEquals("原姓名", first.get("borrower_name"));
        assertEquals("原证件", first.get("borrower_id_card"));
        assertEquals("原电话", first.get("borrower_phone"));
        assertEquals("原单位", first.get("borrower_unit"));
        assertEquals(7L, ((Number) first.get("department_id")).longValue());
        assertEquals(0L, ((Number) first.get("personnel_id")).longValue());
        assertEquals(0L, ((Number) first.get("owner_user_id")).longValue());
        for (String field : new String[]{"id","item_name","quantity","handler_id","handler_name","handler_id_card","returned","borrow_date","expected_return_date","actual_return_date",
                "return_condition","return_remark","remark","created_at","updated_at"}) assertEquals(before.get(field), first.get(field), field);
        jdbc.update("UPDATE item_loan SET borrower_name='手填姓名',borrower_id_card='手填证件',borrower_phone='手填电话',borrower_unit='手填单位' WHERE id=42");
        jdbc.update("UPDATE personnel SET name='后改姓名',department_id=99");
        jdbc.update("DELETE FROM department");
        Map<String,Object> manual = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=42");
        ItemLoanSnapshots.migrateExisting(jdbc); ItemLoanSnapshots.migrateExisting(jdbc);
        assertEquals(manual, jdbc.queryForMap("SELECT * FROM item_loan WHERE id=42"));
    }

    @Test void missingPersonnelOrDepartmentPreservesRecordAndAvailableTextWithZeroOwnership() {
        JdbcTemplate jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY,name TEXT,id_card TEXT,phone TEXT,department_id INTEGER)");
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT)");
        PersonnelIntegrityTest.itemLoanSchema(jdbc);
        jdbc.update("INSERT INTO personnel VALUES(1,'可保留姓名','证件','电话',99)");
        jdbc.update("INSERT INTO item_loan(id,item_name,personnel_id,borrow_date,borrower_unit) VALUES(1,'缺单位',1,'2024-02-29','保留单位')");
        jdbc.update("INSERT INTO item_loan(id,item_name,personnel_id,borrow_date,borrower_name,borrower_phone) VALUES(2,'缺人员',88,'2024-02-29','旧姓名','旧电话')");
        ItemLoanSnapshots.migrateExisting(jdbc);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM item_loan WHERE department_id=0 AND personnel_id=0 AND borrower_migrated=1", Integer.class));
        assertEquals("可保留姓名", jdbc.queryForObject("SELECT borrower_name FROM item_loan WHERE id=1", String.class));
        assertEquals("保留单位", jdbc.queryForObject("SELECT borrower_unit FROM item_loan WHERE id=1", String.class));
        assertEquals("旧姓名", jdbc.queryForObject("SELECT borrower_name FROM item_loan WHERE id=2", String.class));
        assertEquals("旧电话", jdbc.queryForObject("SELECT borrower_phone FROM item_loan WHERE id=2", String.class));
    }

    @Test void conversionReturnsCopyAndMarkerOneDoesNotUsePersonnelAgain() {
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("id", 4); row.put("personnel_id", 8); row.put("borrower_migrated", 1);
        row.put("borrower_name", ""); row.put("borrower_id_card", "手填"); row.put("department_id", 0);
        row.put("created_at", "创建"); row.put("updated_at", "更新");
        row.put("registration_department", "外单位手填");
        Map<String,Object> converted = ItemLoanSnapshots.convertLegacy(row, Collections.emptyList(), Collections.emptyList());
        Map<String,Object> expected = new LinkedHashMap<>(row); expected.put("owner_user_id", 0L); expected.put("returned", 0);
        expected.put("handler_id", 0L); expected.put("handler_name", ""); expected.put("handler_id_card", "");
        assertNotSame(row, converted); assertEquals(expected, converted);
        Map<String,Object> legacy = new LinkedHashMap<>(row); legacy.remove("borrower_migrated");
        Map<String,Object> original = new LinkedHashMap<>(legacy);
        converted = ItemLoanSnapshots.convertLegacy(legacy, Collections.emptyList(), Collections.emptyList());
        assertEquals(original, legacy);
        assertEquals(0L, converted.get("personnel_id"));
        assertEquals(0L, converted.get("owner_user_id"));
        assertEquals(1, converted.get("borrower_migrated"));
        legacy.put("actual_return_date", "2024-03-01");
        converted = ItemLoanSnapshots.convertLegacy(legacy, Collections.emptyList(), Collections.emptyList());
        assertEquals(1, converted.get("returned"));
        assertEquals("2024-03-01", converted.get("actual_return_date"));
    }

    @Test void handlerSnapshotsSupplementOnlyMissingFieldsAndNeverConsultPersonnel() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String,Object> person = new LinkedHashMap<>();
        person.put("id", 9); person.put("name", "不可推断的人员姓名");
        for (int marker : new int[]{0, 1}) {
            for (Map<String,Object> handler : java.util.Arrays.asList(
                    Collections.<String,Object>emptyMap(),
                    Collections.<String,Object>singletonMap("handler_id", 9),
                    Collections.<String,Object>singletonMap("handler_name", "仅有快照名"),
                    handlerSnapshot(0, ""), handlerSnapshot(999, "人员已不存在的原名"),
                    handlerSnapshot(9, "与现人员不同的快照名", "原经手证件"))) {
                Map<String,Object> row = new LinkedHashMap<>(handler);
                row.put("personnel_id", 9); row.put("borrower_migrated", marker);
                row.put("owner_user_id", 9); row.put("returned", 0);
                row.put("borrow_date", "2024-02-29"); row.put("actual_return_date", "2024-03-01");
                Map<String,Object> decoded = mapper.readValue(mapper.writeValueAsBytes(row),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() { });
                Map<String,Object> before = new LinkedHashMap<>(decoded);
                Map<String,Object> converted = ItemLoanSnapshots.convertLegacy(decoded,
                        Collections.singletonList(person), Collections.emptyList());
                assertEquals(handler.getOrDefault("handler_id", 0L), converted.get("handler_id"));
                assertEquals(handler.getOrDefault("handler_name", ""), converted.get("handler_name"));
                assertEquals(handler.getOrDefault("handler_id_card", ""), converted.get("handler_id_card"));
                assertEquals(0, converted.get("returned"));
                assertEquals("2024-02-29", converted.get("borrow_date"));
                assertEquals("2024-03-01", converted.get("actual_return_date"));
                assertEquals(before, decoded);
                assertEquals(converted, ItemLoanSnapshots.convertLegacy(converted, Collections.emptyList(), Collections.emptyList()));
            }
        }
    }

    private static Map<String,Object> handlerSnapshot(int id, String name) {
        return handlerSnapshot(id, name, "");
    }

    private static Map<String,Object> handlerSnapshot(int id, String name, String idCard) {
        Map<String,Object> snapshot = new LinkedHashMap<>();
        snapshot.put("handler_id", id); snapshot.put("handler_name", name); snapshot.put("handler_id_card", idCard);
        return snapshot;
    }

    @Test void jsonSnapshotsMigrateMissingReturnStateButPreserveExplicitStateAndLegacyDates() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (Object marker : new Object[]{null, 0, 1}) {
            for (Object actualDate : new Object[]{null, "", "2024-03-01"}) {
                for (Object explicit : new Object[]{null, 0, 1, "0", "1"}) {
                    Map<String,Object> row = new LinkedHashMap<>();
                    row.put("id", 42); row.put("personnel_id", 0);
                    row.put("borrow_date", "2024-02-29"); row.put("expected_return_date", "2024-03-02");
                    row.put("actual_return_date", actualDate);
                    row.put("return_condition", "完好"); row.put("return_remark", "验收");
                    row.put("created_at", "创建"); row.put("updated_at", "更新");
                    if (marker != null) row.put("borrower_migrated", marker);
                    if (explicit != null) row.put("returned", explicit);
                    Map<String,Object> decoded = mapper.readValue(mapper.writeValueAsBytes(row),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() { });
                    Map<String,Object> before = new LinkedHashMap<>(decoded);
                    Map<String,Object> converted = ItemLoanSnapshots.convertLegacy(decoded, Collections.emptyList(), Collections.emptyList());
                    Object expected = explicit != null ? explicit : actualDate == null || "".equals(actualDate) ? 0 : 1;
                    assertEquals(expected, converted.get("returned"), row.toString());
                    for (String field : new String[]{"borrow_date","expected_return_date","actual_return_date",
                            "return_condition","return_remark","created_at","updated_at"}) {
                        assertEquals(before.get(field), converted.get(field), field);
                    }
                    assertEquals(before, decoded, "source JSON snapshot must not be mutated");
                    assertEquals(converted, ItemLoanSnapshots.convertLegacy(converted, Collections.emptyList(), Collections.emptyList()));
                }
            }
        }
        Map<String,Object> missingDates = new LinkedHashMap<>();
        missingDates.put("borrower_migrated", 1);
        Map<String,Object> converted = ItemLoanSnapshots.convertLegacy(missingDates, Collections.emptyList(), Collections.emptyList());
        assertEquals(0, converted.get("returned"));
        assertFalse(converted.containsKey("actual_return_date"));
        assertFalse(converted.containsKey("expected_return_date"));
    }

    @Test void oldIndependentSnapshotSupplementsOnlyMissingRegistrationWithoutRebindingBorrower() {
        Map<String,Object> department = new LinkedHashMap<>();
        department.put("id", 7); department.put("name", "恢复后的管理单位");
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("borrower_migrated", 1); row.put("personnel_id", 0); row.put("department_id", 7);
        row.put("borrower_name", "手填姓名"); row.put("created_at", "创建"); row.put("updated_at", "更新");
        Map<String,Object> converted = ItemLoanSnapshots.convertLegacy(row, Collections.emptyList(), Collections.singletonList(department));
        assertFalse(row.containsKey("registration_department"));
        assertEquals("恢复后的管理单位", converted.remove("registration_department"));
        Map<String,Object> expected = new LinkedHashMap<>(row); expected.put("owner_user_id", 0L); expected.put("returned", 0);
        expected.put("handler_id", 0L); expected.put("handler_name", ""); expected.put("handler_id_card", "");
        assertEquals(expected, converted);
        for (String explicit : new String[]{"", "任意外单位"}) {
            row.put("registration_department", explicit);
            expected = new LinkedHashMap<>(row); expected.put("owner_user_id", 0L); expected.put("returned", 0);
            expected.put("handler_id", 0L); expected.put("handler_name", ""); expected.put("handler_id_card", "");
            assertEquals(expected, ItemLoanSnapshots.convertLegacy(row, Collections.emptyList(), Collections.singletonList(department)));
        }
        row.remove("registration_department"); row.put("department_id", 99);
        assertEquals("", ItemLoanSnapshots.convertLegacy(row, Collections.emptyList(), Collections.singletonList(department)).get("registration_department"));
        row.put("borrower_migrated", 0); row.put("personnel_id", 9);
        Map<String,Object> person = new LinkedHashMap<>(); person.put("id", 9); person.put("department_id", 7); person.put("name", "旧姓名");
        assertEquals("恢复后的管理单位", ItemLoanSnapshots.convertLegacy(row, Collections.singletonList(person), Collections.singletonList(department)).get("registration_department"));
    }

    @Test void absentLoanTableIsNoOpButUnupgradedOrBrokenSchemaIsNotSilenced() {
        JdbcTemplate jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        assertDoesNotThrow(() -> ItemLoanSnapshots.migrateExisting(jdbc));
        jdbc.execute("CREATE TABLE item_loan(id INTEGER PRIMARY KEY)");
        assertThrows(DataAccessException.class, () -> ItemLoanSnapshots.migrateExisting(jdbc));
    }

    @Test void loanMigrationWorksWhenOldIsolatedSchemaHasNoPersonnelTables() {
        JdbcTemplate jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        PersonnelIntegrityTest.itemLoanSchema(jdbc);
        jdbc.update("INSERT INTO item_loan(id,item_name,personnel_id,borrow_date) VALUES(1,'旧隔离记录',99,'2024-02-29')");
        assertDoesNotThrow(() -> ItemLoanSnapshots.migrateExisting(jdbc));
        assertEquals(1, jdbc.queryForObject("SELECT borrower_migrated FROM item_loan", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT department_id FROM item_loan", Integer.class));
    }
}
