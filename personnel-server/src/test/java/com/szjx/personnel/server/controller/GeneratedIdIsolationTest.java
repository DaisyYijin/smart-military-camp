package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.dao.MilitaryAssessmentDao;
import com.szjx.personnel.server.dao.SubRecordDao;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.service.PersonnelIntegrityTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Intentionally no transaction: connection-local IDs must not leak across JdbcTemplate calls. */
class GeneratedIdIsolationTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;

    @BeforeEach void setup() {
        DataScope.clear();
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, id_card TEXT, department_id INTEGER)");
        jdbc.update("INSERT INTO personnel VALUES(1,'测试人员','TEST-CARD',NULL)");
    }
    @AfterEach void clearScope() { DataScope.clear(); }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    @Test void subRecordInsertReturnsItsRealIdOnIndependentConnections() {
        jdbc.execute("CREATE TABLE position_history(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, remark TEXT)");
        SubRecordDao dao = new SubRecordDao(jdbc);
        long first = dao.insert("position_history", 1, Collections.singletonMap("remark", "第一条"), Collections.singletonList("remark"));
        long second = dao.insert("position_history", 1, Collections.singletonMap("remark", "第二条"), Collections.singletonList("remark"));
        assertTrue(first > 0); assertTrue(second > first);
        assertEquals("第一条", dao.findById("position_history", first).get("remark"));
        assertEquals("第二条", dao.findById("position_history", second).get("remark"));
    }

    @Test void leavePlanCreateReturnsTheNewRecordNotNullOrAnotherId() {
        jdbc.execute("CREATE TABLE leave_plan(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, year TEXT, leave_type TEXT, start_date TEXT, end_date TEXT, days REAL, remark TEXT)");
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY, name TEXT, parent_id INTEGER DEFAULT 0, sort INTEGER DEFAULT 0)");
        LeavePlanController controller = new LeavePlanController(jdbc);
        Map<String, Object> body = map("personnelId", 1, "year", "2026", "leaveType", "年假", "startDate", "2026-10-16", "endDate", "2026-10-17", "days", 2);
        ApiResponse first = controller.create(body);
        assertEquals(0, first.getCode());
        Map<?, ?> row = (Map<?, ?>) first.getData();
        assertNotNull(row);
        assertEquals(1L, ((Number) row.get("id")).longValue());
        assertEquals("测试人员", row.get("personnel_name"));
        Map<?, ?> second = (Map<?, ?>) controller.create(body).getData();
        assertEquals(2L, ((Number) second.get("id")).longValue());
    }

    @Test void militarySchemeParentsAndChildrenUseCorrectGeneratedIds() {
        jdbc.execute("CREATE TABLE mil_scheme(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, remark TEXT)");
        jdbc.execute("CREATE TABLE mil_scheme_item(id INTEGER PRIMARY KEY AUTOINCREMENT, scheme_id INTEGER, parent_id INTEGER, name TEXT, weight REAL, sort INTEGER)");
        jdbc.update("INSERT INTO mil_scheme(id,name) VALUES(40,'旧方案')");
        jdbc.update("INSERT INTO mil_scheme_item(id,scheme_id,parent_id,name,weight,sort) VALUES(90,40,0,'旧课目',100,1)");
        MilitaryAssessmentController controller = new MilitaryAssessmentController(new MilitaryAssessmentDao(jdbc), jdbc);
        Map<String, Object> topA = map("name", "课目甲", "weight", 60, "children", Arrays.asList(map("name", "甲一", "weight", 30), map("name", "甲二", "weight", 70)));
        Map<String, Object> topB = map("name", "课目乙", "weight", 40, "children", Collections.singletonList(map("name", "乙一", "weight", 100)));
        ApiResponse response = controller.saveScheme(map("name", "新方案", "items", Arrays.asList(topA, topB)));
        assertEquals(0, response.getCode());
        long scheme = ((Number) ((Map<?, ?>) response.getData()).get("id")).longValue();
        assertEquals(41L, scheme);
        Long parentA = jdbc.queryForObject("SELECT id FROM mil_scheme_item WHERE scheme_id=? AND name='课目甲'", Long.class, scheme);
        Long parentB = jdbc.queryForObject("SELECT id FROM mil_scheme_item WHERE scheme_id=? AND name='课目乙'", Long.class, scheme);
        assertTrue(parentA > 90); assertTrue(parentB > parentA);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mil_scheme_item WHERE scheme_id=? AND parent_id=?", Integer.class, scheme, parentA));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mil_scheme_item WHERE scheme_id=? AND parent_id=?", Integer.class, scheme, parentB));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mil_scheme_item WHERE scheme_id=0 OR (scheme_id=? AND parent_id=0 AND name IN ('甲一','甲二','乙一'))", Integer.class, scheme));
    }
}
