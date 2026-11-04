package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.service.AccountServiceTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 休假/在外月度汇总:跨月按区间交集计天,无数据人员不出现 */
class ReportLeaveAbsenceTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private ReportController controller;

    @BeforeEach void setup() {
        jdbc = AccountServiceTest.database(temp.resolve("report-la.sqlite"));
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY, name TEXT)");
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, department_id INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE leave_record(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, start_date TEXT, end_date TEXT)");
        jdbc.execute("CREATE TABLE absence_record(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, leave_date TEXT, expected_return_date TEXT DEFAULT '', actual_return_date TEXT DEFAULT '')");
        controller = new ReportController(jdbc,
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        DataScope.set(new DataScope.Context(9L, "admin", true, Collections.<Long>emptySet()));
    }

    @AfterEach void clear() { DataScope.clear(); }

    @Test void summaryCountsOverlappingDaysForTheMonthOnly() {
        jdbc.update("INSERT INTO department(id,name) VALUES(1,'一中队')");
        jdbc.update("INSERT INTO personnel(id,name,department_id) VALUES(1,'张三',1)");
        jdbc.update("INSERT INTO personnel(id,name,department_id) VALUES(2,'李四',1)");
        jdbc.update("INSERT INTO personnel(id,name,department_id) VALUES(3,'王五',1)");
        // 张三:休假跨月(11-28~12-03,11月只算 28~30 共 3 天);在外 11-05~11-07 已归(3 天)
        jdbc.update("INSERT INTO leave_record(personnel_id,start_date,end_date) VALUES(1,'2026-11-28','2026-12-03')");
        jdbc.update("INSERT INTO absence_record(personnel_id,leave_date,expected_return_date,actual_return_date) VALUES(1,'2026-11-05','2026-11-07','2026-11-07')");
        // 李四:休假整段在 12 月(11 月不应出现)
        jdbc.update("INSERT INTO leave_record(personnel_id,start_date,end_date) VALUES(2,'2026-12-01','2026-12-05')");
        // 王五:11 月整月休假(30 天)
        jdbc.update("INSERT INTO leave_record(personnel_id,start_date,end_date) VALUES(3,'2026-11-01','2026-11-30')");
        List<Map<String, Object>> rows = controller.leaveAbsenceRows(2026, 11);
        Map<String, Object> zhang = rows.stream().filter(r -> "张三".equals(r.get("name"))).findFirst().get();
        Map<String, Object> wang = rows.stream().filter(r -> "王五".equals(r.get("name"))).findFirst().get();
        assertEquals(3, ((Number) zhang.get("leaveDays")).intValue(), "跨月休假只算当月交集天数");
        assertEquals(3, ((Number) zhang.get("absenceDays")).intValue());
        assertEquals(30, ((Number) wang.get("leaveDays")).intValue());
        assertEquals(2, rows.size(), "12 月休假的李四不应出现在 11 月汇总");
    }
}
