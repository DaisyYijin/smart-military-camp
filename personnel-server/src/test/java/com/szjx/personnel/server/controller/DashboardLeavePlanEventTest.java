package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.service.PersonnelIntegrityTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仪表盘近期事件:休假计划即将开始提示 */
class DashboardLeavePlanEventTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private DashboardController controller;

    @BeforeEach
    void setUp() {
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, id_card TEXT, birthday TEXT DEFAULT '', party_full_date TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE leave_plan(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, year TEXT, leave_type TEXT, start_date TEXT, end_date TEXT, days REAL, status TEXT, remark TEXT)");
        jdbc.execute("CREATE TABLE leave_record(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, year TEXT, leave_type TEXT, start_date TEXT, end_date TEXT, days REAL)");
        jdbc.execute("CREATE TABLE material(id INTEGER PRIMARY KEY, name TEXT, quantity REAL, min_quantity REAL)");
        jdbc.execute("CREATE TABLE equipment(id INTEGER PRIMARY KEY, name TEXT, status TEXT)");
        jdbc.execute("CREATE TABLE vehicle(id INTEGER PRIMARY KEY, plate_no TEXT, insurance_expiry TEXT, inspection_expiry TEXT)");
        jdbc.execute("CREATE TABLE item_loan(id INTEGER PRIMARY KEY, item_name TEXT, borrower_name TEXT, expected_return_date TEXT, returned INTEGER DEFAULT 0, owner_user_id INTEGER)");
        jdbc.execute("CREATE TABLE absence_record(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, reason TEXT, leave_date TEXT, expected_return_date TEXT DEFAULT '', actual_return_date TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE driver_license(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, license_no TEXT DEFAULT '', expiry_date TEXT DEFAULT '')");
        jdbc.update("INSERT INTO personnel(id,name,id_card) VALUES(1,'张三','110105199001011234')");
        jdbc.update("INSERT INTO personnel(id,name,id_card) VALUES(2,'李四','320101198805051234')");
        controller = new DashboardController(jdbc, new com.szjx.personnel.server.dao.ItemLoanDao(jdbc));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> events() {
        ApiResponse response = controller.events();
        assertEquals(0, response.getCode());
        // 事件返回 {list,total}:list 为截断后事件,total 为窗口内总数
        Map<String, Object> payload = (Map<String, Object>) response.getData();
        return (List<Map<String, Object>>) payload.get("list");
    }

    private static String dateInDays(long days) {
        return LocalDate.now().plusDays(days).toString();
    }

    @Test
    void upcomingLeavePlanWithin10DaysIsListed() {
        jdbc.update("INSERT INTO leave_plan(personnel_id,year,leave_type,start_date,end_date,days) VALUES(1,'2026','年假',?,? ,5)",
                dateInDays(3), dateInDays(7));
        List<Map<String, Object>> list = events();
        boolean found = false;
        for (Map<String, Object> ev : list) {
            if (String.valueOf(ev.get("text")).contains("张三") && String.valueOf(ev.get("text")).contains("按计划")) {
                found = true;
                assertEquals("休假", ev.get("tag"));
                assertEquals(dateInDays(3), ev.get("date"));
            }
        }
        assertTrue(found, "3天后的休假计划应出现在近期事件中");
    }

    @Test
    void leavePlanStartingTodayIsWarned() {
        jdbc.update("INSERT INTO leave_plan(personnel_id,year,leave_type,start_date,end_date,days) VALUES(1,'2026','年假',?,? ,5)",
                LocalDate.now().toString(), dateInDays(4));
        List<Map<String, Object>> list = events();
        boolean found = false;
        for (Map<String, Object> ev : list) {
            if (String.valueOf(ev.get("text")).contains("张三") && String.valueOf(ev.get("text")).contains("按计划今日开始")) {
                assertEquals("warn", ev.get("type"));
                found = true;
            }
        }
        assertTrue(found, "当日开始的休假计划应以 warn 级别提示");
    }

    @Test
    void leavePlanBeyond10DaysOrPastIsExcluded() {
        jdbc.update("INSERT INTO leave_plan(personnel_id,year,leave_type,start_date,end_date,days) VALUES(1,'2026','年假',?,? ,5)",
                dateInDays(20), dateInDays(25));
        jdbc.update("INSERT INTO leave_plan(personnel_id,year,leave_type,start_date,end_date,days) VALUES(2,'2026','年假',?,? ,5)",
                dateInDays(-30), dateInDays(-20));
        for (Map<String, Object> ev : events()) {
            assertTrue(!String.valueOf(ev.get("text")).contains("按计划"), "超出窗口的休假计划不应出现: " + ev.get("text"));
        }
    }
}
