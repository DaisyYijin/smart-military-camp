package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.service.PersonnelIntegrityTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FitnessMatrixAtomicityTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    FitnessMatrixController controller;

    @BeforeEach void setup() {
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE fitness_matrix(id INTEGER PRIMARY KEY AUTOINCREMENT, item TEXT, age_group TEXT, score INTEGER, value REAL, unit TEXT, updated_at TEXT)");
        jdbc.update("INSERT INTO fitness_matrix(item,age_group,score,value,unit) VALUES('目标','0-100',90,15,'count')");
        controller = PersonnelIntegrityTest.transactional(new FitnessMatrixController(jdbc), jdbc);
    }

    Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("item", "目标"); body.put("unit", "count");
        body.put("ageGroups", Arrays.asList("0-24", "25-100"));
        body.put("scores", Arrays.asList(100, 80)); return body;
    }

    Map<String, Object> copyBody() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("source", "源"); body.put("target", "目标"); return body;
    }

    List<Map<String, Object>> target() { return jdbc.queryForList("SELECT * FROM fitness_matrix WHERE item='目标' ORDER BY id"); }
    void seedSource() {
        jdbc.update("INSERT INTO fitness_matrix(item,age_group,score,value,unit) VALUES('源','0-100',100,20,'count')");
        jdbc.update("INSERT INTO fitness_matrix(item,age_group,score,value,unit) VALUES('源','0-100',80,10,'count')");
    }

    @Test void initValidatesEveryElementBeforeDeletingAnything() {
        List<Map<String, Object>> before = target();
        List<Map<String, Object>> invalid = new ArrayList<Map<String, Object>>();
        Object[][] mutations = {
                {"unit", "bad"}, {"ageGroups", "not-array"}, {"ageGroups", Arrays.asList("0-24", null)},
                {"ageGroups", Arrays.asList("0-24", 25)}, {"ageGroups", Arrays.asList("0-24", " ")},
                {"ageGroups", Arrays.asList("0-24", " 0-24 ")}, {"scores", "not-array"},
                {"scores", Arrays.asList(100, null)}, {"scores", Arrays.asList(100, "80")},
                {"scores", Arrays.asList(100, 80.5)}, {"scores", Arrays.asList(100, -1)},
                {"scores", Arrays.asList(100, Double.NaN)}, {"scores", Arrays.asList(100, Double.POSITIVE_INFINITY)},
                {"scores", Arrays.asList(100, 101)}, {"scores", Arrays.asList(100, 100)}
        };
        for (Object[] mutation : mutations) {
            Map<String, Object> body = body(); body.put((String) mutation[0], mutation[1]); invalid.add(body);
        }
        for (Map<String, Object> body : invalid) {
            ApiResponse result = controller.init(body);
            assertNotEquals(0, result.getCode(), body.toString());
            assertEquals(before, target(), body.toString());
        }
    }

    @Test void initSqlFailureRollsBackDeleteAndEarlierInserts() {
        List<Map<String, Object>> before = target();
        failSecondTargetInsert();
        assertThrows(RuntimeException.class, () -> controller.init(body()));
        assertEquals(before, target());
    }

    @Test void copyValidatesSourceAndAllItsRowsBeforeReplacingTarget() {
        seedSource();
        List<Map<String, Object>> before = target();
        for (String sql : new String[]{
                "UPDATE fitness_matrix SET value='NaN' WHERE item='源' AND score=80",
                "UPDATE fitness_matrix SET value=10, unit='invalid' WHERE item='源' AND score=80",
                "UPDATE fitness_matrix SET unit='count', age_group='' WHERE item='源' AND score=80",
                "UPDATE fitness_matrix SET age_group='0-100', score=100 WHERE item='源' AND score=80"}) {
            jdbc.update(sql);
            assertNotEquals(0, controller.copy(copyBody()).getCode());
            assertEquals(before, target());
        }
        Map<String, Object> same = copyBody(); same.put("source", "目标");
        assertNotEquals(0, controller.copy(same).getCode());
        assertEquals(before, target());
    }

    @Test void copySqlFailureRollsBackDeleteAndEarlierInserts() {
        seedSource();
        List<Map<String, Object>> before = target();
        List<Map<String, Object>> source = jdbc.queryForList("SELECT * FROM fitness_matrix WHERE item='源' ORDER BY id");
        failSecondTargetInsert();
        assertThrows(RuntimeException.class, () -> controller.copy(copyBody()));
        assertEquals(before, target());
        assertEquals(source, jdbc.queryForList("SELECT * FROM fitness_matrix WHERE item='源' ORDER BY id"));
    }

    @Test void validInitAndCopyStillReplaceEntireTarget() {
        assertEquals(0, controller.init(body()).getCode());
        assertEquals(4, target().size());
        seedSource();
        assertEquals(0, controller.copy(copyBody()).getCode());
        assertEquals(2, target().size());
        assertEquals(20d, jdbc.queryForObject("SELECT value FROM fitness_matrix WHERE item='目标' AND score=100", Double.class));
    }

    void failSecondTargetInsert() {
        jdbc.execute("CREATE TRIGGER deny_matrix BEFORE INSERT ON fitness_matrix WHEN NEW.item='目标' AND NEW.score=80 BEGIN SELECT RAISE(ABORT,'fixture failure'); END");
    }
}
