package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.dao.LeavePlanDao;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LeavePlanFilterTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private LeavePlanController controller;

    @BeforeEach
    void setUp() {
        jdbc = com.szjx.personnel.server.service.PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, id_card TEXT, department_id INTEGER)");
        // 列表附带单位路径,需单位表供层级查询
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY, name TEXT, parent_id INTEGER DEFAULT 0, sort INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE leave_plan(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, year TEXT, leave_type TEXT, start_date TEXT, end_date TEXT, days REAL, status TEXT, remark TEXT)");
        jdbc.update("INSERT INTO personnel(id,name,id_card) VALUES(1,'张三','110105199001011234')");
        jdbc.update("INSERT INTO personnel(id,name,id_card) VALUES(2,'李四','320101198805051234')");
        jdbc.update("INSERT INTO leave_plan(personnel_id,year,leave_type,start_date,end_date,days) VALUES(1,'2026','年假','2026-06-10','2026-06-15',5)");
        jdbc.update("INSERT INTO leave_plan(personnel_id,year,leave_type,start_date,end_date,days) VALUES(2,'2026','事假','2026-06-20','2026-07-02',12)");
        jdbc.update("INSERT INTO leave_plan(personnel_id,year,leave_type,start_date,end_date,days) VALUES(1,'2025','年假','2025-06-10','2025-06-15',5)");
        controller = new LeavePlanController(new LeavePlanDao(jdbc));
    }

    @Test
    void filtersByPartialIdCardAndExactDates() {
        ApiResponse response = controller.query(null, "199001", null, null, null,
                "2026-06-10", "2026-06-15", null, 1, 20);
        assertEquals(0, response.getCode());
        PageResult<?> page = (PageResult<?>) response.getData();
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getList().size());
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) page.getList().get(0);
        assertEquals("张三", row.get("personnel_name"));
    }

    @Test
    void filtersByLeaveTypeAndDateRange() {
        ApiResponse response = controller.query(null, null, "2026", "年假", null,
                "2026-06-01", "2026-06-30", null, 1, 20);
        assertEquals(0, response.getCode());
        PageResult<?> page = (PageResult<?>) response.getData();
        assertEquals(1, page.getTotal());
    }

    @Test
    void filtersPlansOverlappingSelectedMonthAndCombinesWithYear() {
        ApiResponse response = controller.query(null, null, "2026", null, "2026-06",
                null, null, null, 1, 20);
        PageResult<?> page = (PageResult<?>) response.getData();
        assertEquals(2, page.getTotal());
        assertEquals(2, page.getList().size());
    }

    @Test
    void rejectsInvalidMonthFormat() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.query(null, null, null, null, "2026-13", null, null, null, 1, 20));
        assertEquals("月份格式应为 YYYY-MM", error.getMessage());
    }
}
