package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Each JdbcTemplate call opens its own isolated fixture connection unless explicitly proxied. */
class ReportReceiveRegressionTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    ReportService report;
    static final String CARD = "11010519900101123X";
    static final String NEXT = "2026-10-16 12:00:00";

    @BeforeEach void setup() {
        DataScope.clear();
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        PersonnelIntegrityTest.personnelSchema(jdbc);
        jdbc.execute("CREATE TABLE equipment(id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, keeper_id INTEGER, photo TEXT DEFAULT '', updated_at TEXT)");
        jdbc.execute("CREATE TABLE material(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, keeper_id INTEGER, photo TEXT DEFAULT '', updated_at TEXT)");
        jdbc.execute("CREATE TABLE vehicle(id INTEGER PRIMARY KEY AUTOINCREMENT, plate_no TEXT, driver_id INTEGER, photo TEXT DEFAULT '', updated_at TEXT)");
        for (String table : ReportService.SUB_TABLES) {
            jdbc.execute("CREATE TABLE " + table + "(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, remark TEXT DEFAULT '')");
        }
        jdbc.execute("CREATE TABLE fitness_record(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, item TEXT, score TEXT, test_date TEXT, points REAL, grade TEXT, updated_at TEXT)");
        jdbc.execute("CREATE TABLE fitness_matrix(id INTEGER PRIMARY KEY, item TEXT, age_group TEXT, score INTEGER, value REAL, unit TEXT)");
        jdbc.execute("CREATE TABLE sys_setting(skey TEXT PRIMARY KEY, svalue TEXT)");
        jdbc.update("INSERT INTO fitness_matrix VALUES(1,'跑步','0-100',100,60,'time')");
        jdbc.update("INSERT INTO fitness_matrix VALUES(2,'跑步','0-100',80,90,'time')");
        jdbc.update("INSERT INTO sys_setting VALUES('fitness_grade_rule',?)", "[{\"min\":90,\"op\":\">=\",\"label\":\"优秀\"},{\"min\":60,\"op\":\">=\",\"label\":\"及格\"}]");
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id,birthday,gender,height,photo,updated_at) VALUES(1,'本地',?,1,'1990-01-01','男',180,'local.jpg','2025-01-01 00:00:00')", CARD);
        report = PersonnelIntegrityTest.transactional(new ReportService(jdbc), jdbc);
    }

    @AfterEach void clearScope() { DataScope.clear(); }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], pairs[i + 1]);
        return out;
    }

    Map<String, Object> pkg() { return map("meta", map("version", 2, "unit", "上报单位")); }
    Map<String, Object> person(String card) { return map("id_card", card, "name", "上报", "updated_at", NEXT); }
    void receive(Map<String, Object> pkg) { report.receivePackage(report.normalizePackage(pkg), 1L, false, null, null); }
    int count(String table, long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE personnel_id=?", Integer.class, id); }

    @Test void ignoresSourcePhotoPathsForNewAndExistingPeopleAndAssets() {
        jdbc.update("INSERT INTO equipment(code,keeper_id,photo,updated_at) VALUES('E1',1,'equipment-local.jpg','2025-01-01 00:00:00')");
        Map<String, Object> existing = person(CARD); existing.put("photo", "../../server.db");
        Map<String, Object> added = person("NEW-CARD"); added.put("photo", "C:\\private\\secret.jpg");
        Map<String, Object> pkg = pkg();
        pkg.put("personnel", Arrays.asList(existing, added));
        pkg.put("equipment", Arrays.asList(map("code", "E1", "_keeper_card", CARD, "photo", "../bad", "updated_at", NEXT),
                map("code", "E2", "photo", "../bad")));
        pkg.put("material", Collections.singletonList(map("name", "M1", "photo", "../bad")));
        pkg.put("vehicle", Collections.singletonList(map("plate_no", "V1", "photo", "../bad")));
        receive(pkg);
        assertEquals("local.jpg", jdbc.queryForObject("SELECT photo FROM personnel WHERE id=1", String.class));
        assertEquals("", jdbc.queryForObject("SELECT photo FROM personnel WHERE id_card='NEW-CARD'", String.class));
        assertEquals("equipment-local.jpg", jdbc.queryForObject("SELECT photo FROM equipment WHERE code='E1'", String.class));
        assertEquals("", jdbc.queryForObject("SELECT photo FROM equipment WHERE code='E2'", String.class));
        assertEquals("", jdbc.queryForObject("SELECT photo FROM material", String.class));
        assertEquals("", jdbc.queryForObject("SELECT photo FROM vehicle", String.class));
    }

    @Test void explicitEmptySubsClearOnlyAcceptedPeopleAndMissingKeysStayMissingAfterNormalization() {
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id,updated_at) VALUES(2,'冲突保留','CONFLICT',1,?)", NEXT);
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id,updated_at) VALUES(3,'未上报','ABSENT',1,?)", NEXT);
        for (long id : new long[]{1, 2, 3}) {
            jdbc.update("INSERT INTO position_history(personnel_id) VALUES(?)", id);
            jdbc.update("INSERT INTO reward_punish(personnel_id) VALUES(?)", id);
        }
        Map<String, Object> pkg = pkg();
        pkg.put("personnel", Arrays.asList(person(" " + CARD.toLowerCase(Locale.ROOT) + " "), person("CONFLICT")));
        Map<String, Object> subs = map("position_history", Collections.emptyList()); pkg.put("subs", subs);
        report.normalizePackage(pkg); report.normalizePackage(pkg);
        assertFalse(subs.containsKey("reward_punish"));
        receive(pkg);
        assertEquals(0, count("position_history", 1));
        assertEquals(1, count("position_history", 2));
        assertEquals(1, count("position_history", 3));
        assertEquals(1, count("reward_punish", 1));
        assertEquals(1, count("reward_punish", 2));
    }

    @Test void nonemptySnapshotAlsoClearsAcceptedPersonWithoutRowsAndNormalizesLegacySourceIds() {
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id,updated_at) VALUES(2,'第二人','SECOND',1,'2025-01-01 00:00:00')");
        jdbc.update("INSERT INTO position_history(personnel_id,remark) VALUES(1,'旧记录')");
        jdbc.update("INSERT INTO position_history(personnel_id,remark) VALUES(2,'应清空')");
        Map<String, Object> first = person(CARD); first.put("id", 999);
        Map<String, Object> pkg = pkg(); pkg.put("personnel", Arrays.asList(first, person("SECOND")));
        pkg.put("subs", map("position_history", Collections.singletonList(map("personnel_id", 999, "remark", "新记录"))));
        receive(pkg);
        assertEquals(1, count("position_history", 1));
        assertEquals("新记录", jdbc.queryForObject("SELECT remark FROM position_history", String.class));
        assertEquals(0, count("position_history", 2));
    }

    @Test void nullSubtableCannotBeMisinterpretedAsExplicitEmptySnapshot() {
        Map<String, Object> pkg = pkg(); pkg.put("subs", map("position_history", null));
        assertThrows(IllegalArgumentException.class, () -> report.normalizePackage(pkg));
    }

    @Test void fitnessAlwaysUsesReceiverScoringAndNormalizesBusinessKeyForUpdates() {
        Map<String, Object> pkg = pkg();
        Map<String, Object> fitness = map("_card", CARD, "item", " 跑步 ", "score", "1:20", "test_date", " 2026-10-16 ",
                "points", 999, "grade", "伪造", "updated_at", "2025-01-01 00:00:00");
        pkg.put("fitness", Collections.singletonList(fitness)); receive(pkg);
        Map<String, Object> stored = jdbc.queryForMap("SELECT * FROM fitness_record");
        assertEquals("1分20秒", stored.get("score"));
        assertEquals("2026-10-16", stored.get("test_date"));
        assertEquals(80d, ((Number) stored.get("points")).doubleValue());
        assertEquals("及格", stored.get("grade"));
        fitness.put("score", "59秒"); fitness.put("updated_at", NEXT); receive(pkg);
        assertEquals(1, count("fitness_record", 1));
        assertEquals(100d, jdbc.queryForObject("SELECT points FROM fitness_record", Double.class));
        assertEquals("优秀", jdbc.queryForObject("SELECT grade FROM fitness_record", String.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-1", "1:99", "不是成绩"})
    void rejectsInvalidFitnessPerformanceAndRollsBackPersonnelChanges(String bad) {
        Map<String, Object> pkg = pkg(); pkg.put("personnel", Collections.singletonList(person(CARD)));
        pkg.put("fitness", Collections.singletonList(map("_card", CARD, "item", "跑步", "score", bad, "test_date", "2026-10-16")));
        assertThrows(IllegalArgumentException.class, () -> receive(pkg));
        assertEquals("本地", jdbc.queryForObject("SELECT name FROM personnel WHERE id=1", String.class));
        assertEquals(0, count("fitness_record", 1));
    }

    @Test void rejectsNonexistentFitnessDateWithoutPartialImport() {
        Map<String, Object> pkg = pkg(); pkg.put("personnel", Collections.singletonList(person(CARD)));
        pkg.put("fitness", Collections.singletonList(map("_card", CARD, "item", "跑步", "score", "60", "test_date", "2026-02-30")));
        assertThrows(IllegalArgumentException.class, () -> receive(pkg));
        assertEquals("本地", jdbc.queryForObject("SELECT name FROM personnel WHERE id=1", String.class));
        assertEquals(0, count("fitness_record", 1));
    }

    @Test void generatedIdsRemainCorrectAcrossIndependentConnectionsWithoutAmbientTransaction() {
        // DriverManagerDataSource does not retain a connection-local last_insert_rowid between calls.
        ReportService direct = new ReportService(jdbc);
        Map<String, Object> pkg = pkg(); pkg.put("personnel", Collections.singletonList(person("NEW")));
        pkg.put("subs", map("position_history", Collections.singletonList(map("_card", "NEW", "remark", "新任职"))));
        direct.receivePackage(direct.normalizePackage(pkg), null, true, 0L, null);
        Long dept = jdbc.queryForObject("SELECT id FROM department WHERE name='上报单位'", Long.class);
        Long person = jdbc.queryForObject("SELECT id FROM personnel WHERE id_card='NEW'", Long.class);
        assertTrue(dept > 1); assertTrue(person > 1);
        assertEquals(dept, jdbc.queryForObject("SELECT department_id FROM personnel WHERE id=?", Long.class, person));
        assertEquals(person, jdbc.queryForObject("SELECT personnel_id FROM position_history", Long.class));
    }
}
