package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.PageResult;
import com.szjx.personnel.server.service.LeaveService;
import com.szjx.personnel.server.service.PersonnelIntegrityTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class LeaveControllerFilterTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private LeaveController controller;

    @BeforeEach
    void setUp() {
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, id_card TEXT, department_id INTEGER)");
        // 列表附带单位路径,需单位表供层级查询
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY, name TEXT, parent_id INTEGER DEFAULT 0, sort INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE leave_record(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, year TEXT, leave_type TEXT, start_date TEXT, end_date TEXT, days REAL, travel_days REAL DEFAULT 0, holiday_days REAL DEFAULT 0, holiday_name TEXT, leave_location TEXT DEFAULT '', reason TEXT, remark TEXT, created_at TEXT, updated_at TEXT)");
        jdbc.update("INSERT INTO personnel(id,name,id_card) VALUES(1,'张三','110105199001011234')");
        jdbc.update("INSERT INTO personnel(id,name,id_card) VALUES(2,'李四','320101198805051234')");
        jdbc.update("INSERT INTO leave_record(personnel_id,year,leave_type,start_date,end_date,days,leave_location) VALUES(1,'2026','年假','2026-06-10','2026-06-15',5,'北京市')");
        jdbc.update("INSERT INTO leave_record(personnel_id,year,leave_type,start_date,end_date,days) VALUES(2,'2026','事假','2026-06-20','2026-06-22',2)");
        controller = new LeaveController(mock(LeaveService.class), jdbc);
    }

    @Test
    void filtersByPartialIdCard() {
        ApiResponse response = controller.query(null, null, "199001", null, null, null, null, null, null, 1, 20);
        assertEquals(0, response.getCode());
        PageResult<?> page = (PageResult<?>) response.getData();
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getList().size());
        assertEquals("北京市", ((java.util.Map<?, ?>) page.getList().get(0)).get("leaveLocation"));
    }

    @Test
    void filtersByStartDateUpperBound() {
        // startDateMax 收紧开始日期上界:仅 06-10 的年假命中,06-20 的事假被排除(年度下钻口径依赖)
        ApiResponse response = controller.query(null, null, null, null, null, "2026-06-01", "2026-06-12", null, null, 1, 20);
        assertEquals(0, response.getCode());
        PageResult<?> page = (PageResult<?>) response.getData();
        assertEquals(1, page.getTotal());
        assertEquals("年假", ((java.util.Map<?, ?>) page.getList().get(0)).get("leaveType"));
    }
}
