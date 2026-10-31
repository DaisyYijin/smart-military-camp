package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 休假计划后端校验:日期严格解析、年度格式、天数有限非负(口径对齐休假登记) */
class LeavePlanControllerValidationTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private LeavePlanController controller;

    @BeforeEach
    void setUp() {
        jdbc = com.szjx.personnel.server.service.PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, id_card TEXT, department_id INTEGER)");
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY, name TEXT, parent_id INTEGER DEFAULT 0, sort INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE leave_plan(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, year TEXT, leave_type TEXT, start_date TEXT, end_date TEXT, days REAL, status TEXT, remark TEXT, updated_at TEXT)");
        jdbc.update("INSERT INTO personnel(id,name,id_card) VALUES(1,'张三','110105199001011234')");
        controller = new LeavePlanController(jdbc);
    }

    private static Map<String, Object> payload(String year, String start, String end, String days) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("personnelId", 1L);
        m.put("year", year);
        m.put("leaveType", "年假");
        m.put("startDate", start);
        m.put("endDate", end);
        m.put("days", days);
        m.put("remark", "");
        return m;
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsValidPlanAndNormalizesDates() {
        ApiResponse r = controller.create(payload("2026", "2026-06-10", "2026-06-15", "6"));
        assertEquals(0, r.getCode());
        Map<String, Object> saved = (Map<String, Object>) r.getData();
        assertEquals("2026-06-10", saved.get("start_date"));
        assertEquals("2026-06-15", saved.get("end_date"));
        assertEquals(1L, ((Number) saved.get("personnel_id")).longValue());
    }

    @Test
    void rejectsNonIsoDateFormat() {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> controller.create(payload("2026", "2026/06/10", "2026-06-15", "6")));
        assertEquals("开始日期格式应为yyyy-MM-dd", e1.getMessage());
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> controller.create(payload("2026", "2026-06-10", "2026-6-15", "6")));
        assertEquals("结束日期格式应为yyyy-MM-dd", e2.getMessage());
    }

    @Test
    void rejectsImpossibleCalendarDate() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> controller.create(payload("2026", "2026-02-30", "2026-03-01", "6")));
        assertEquals("开始日期不存在", e.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> controller.create(payload("2026", "2026-06-10", "2026-06-31", "6")));
    }

    @Test
    void rejectsEndBeforeStart() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> controller.create(payload("2026", "2026-06-15", "2026-06-10", "6")));
        assertEquals("结束日期不能早于开始日期", e.getMessage());
    }

    @Test
    void rejectsMalformedYear() {
        assertEquals("年度应为1900至9999的4位数字",
                assertThrows(IllegalArgumentException.class,
                        () -> controller.create(payload("20260", "2026-06-10", "2026-06-15", "6"))).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> controller.create(payload("26a", "2026-06-10", "2026-06-15", "6")));
        assertThrows(IllegalArgumentException.class,
                () -> controller.create(payload("1899", "2026-06-10", "2026-06-15", "6")));
    }

    @Test
    void rejectsNonFiniteAndNegativeDays() {
        assertEquals("天数必须为有限数字",
                assertThrows(IllegalArgumentException.class,
                        () -> controller.create(payload("2026", "2026-06-10", "2026-06-15", "NaN"))).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> controller.create(payload("2026", "2026-06-10", "2026-06-15", "Infinity")));
        assertThrows(IllegalArgumentException.class,
                () -> controller.create(payload("2026", "2026-06-10", "2026-06-15", "-Infinity")));
        assertEquals("天数不能为负",
                assertThrows(IllegalArgumentException.class,
                        () -> controller.create(payload("2026", "2026-06-10", "2026-06-15", "-1"))).getMessage());
        assertEquals("天数必须为数字",
                assertThrows(IllegalArgumentException.class,
                        () -> controller.create(payload("2026", "2026-06-10", "2026-06-15", "abc"))).getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateRunsSameValidationAndNormalizes() {
        ApiResponse created = controller.create(payload("2026", "2026-06-10", "2026-06-15", "6"));
        Map<String, Object> saved = (Map<String, Object>) created.getData();
        long id = ((Number) saved.get("id")).longValue();
        assertThrows(IllegalArgumentException.class,
                () -> controller.update(id, payload("2026", "2026-13-01", "2026-06-15", "6")));
        ApiResponse updated = controller.update(id, payload("2026", "2026-07-01", "2026-07-05", "5"));
        assertEquals(0, updated.getCode());
        Map<String, Object> row = (Map<String, Object>) updated.getData();
        assertEquals("2026-07-01", row.get("start_date"));
    }
}
