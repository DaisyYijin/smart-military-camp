package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.szjx.personnel.server.service.RollCallExcelTest.cell;
import static com.szjx.personnel.server.service.RollCallExcelTest.headerRegion;
import static com.szjx.personnel.server.service.RollCallExcelTest.headingCell;
import static com.szjx.personnel.server.service.RollCallExcelTest.nameStartRow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 点名册 actualUnit=true 排列开关:仅分组归属变化(实际单位>0且在层级内→按实际单位,否则回编制);
 * marks/归队/排序/不在位块/版式与按人统计不变;onDuty/actualDepartmentId 筛选一次收窄;无参行为回归。
 * 人员服务为测试替身;单位层级与筛选读隔离 SQLite,不触正式数据。
 */
class RollCallActualUnitTest {
    private final List<JdbcTemplate> databases = new ArrayList<JdbcTemplate>();

    @AfterEach void closeDatabases() {
        DataScope.clear();
        for (JdbcTemplate jdbc : databases) ((SingleConnectionDataSource) jdbc.getDataSource()).destroy();
        databases.clear();
    }

    /** 单位树:1 营部 → 2 一连 → 3 一班;999 为树外ID。 */
    private RollCallExcelService service(Personnel... rows) {
        SingleConnectionDataSource source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        databases.add(jdbc);
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT,parent_id INTEGER,sort INTEGER)");
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY,name TEXT,department_id INTEGER DEFAULT 0,actual_department_id INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE leave_record(id INTEGER PRIMARY KEY,personnel_id INTEGER,start_date TEXT,end_date TEXT,leave_type TEXT)");
        jdbc.execute("CREATE TABLE driver_license(id INTEGER PRIMARY KEY,personnel_id INTEGER,license_status TEXT)");
        jdbc.update("INSERT INTO department(id,name,parent_id,sort) VALUES(1,'营部',0,0),(2,'一连',1,0),(3,'一班',2,0)");
        for (Personnel p : rows) {
            if (p.getId() == null) continue;
            jdbc.update("INSERT INTO personnel(id,department_id,actual_department_id) VALUES(?,?,?)", p.getId(),
                    p.getDepartmentId() == null ? 0L : p.getDepartmentId(),
                    p.getActualDepartmentId() == null ? 0L : p.getActualDepartmentId());
        }
        PersonnelService people = mock(PersonnelService.class);
        when(people.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<Personnel>(Arrays.asList(rows)));
        DepartmentService departments = mock(DepartmentService.class);
        return new RollCallExcelService(people, departments, jdbc);
    }

    private static Personnel person(long id, String name, String status, Long organic, Long actual) {
        Personnel p = new Personnel();
        p.setId(id); p.setName(name); p.setStatus(status); p.setDepartmentId(organic);
        if (actual != null) p.setActualDepartmentId(actual);
        return p;
    }

    private static Map<String, String> query(String... pairs) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }

    private static List<String> dumpValues(Sheet sheet) {
        List<String> values = new ArrayList<String>();
        for (Row row : sheet) for (Cell c : row) values.add(cell(sheet, row.getRowNum(), c.getColumnIndex()));
        return values;
    }

    private static List<String> dump(Sheet sheet) {
        List<String> values = new ArrayList<String>();
        for (Row row : sheet) for (Cell c : row)
            values.add(row.getRowNum() + ":" + c.getColumnIndex() + "=" + cell(sheet, row.getRowNum(), c.getColumnIndex()));
        return values;
    }

    private static int occurrences(Sheet sheet, String name) {
        return Collections.frequency(dumpValues(sheet), name);
    }

    @Test void borrowedPersonMovesUnderActualUnitTitleExactlyOnce() throws Exception {
        Personnel jia = person(1, "借岗甲", "在位", 1L, 3L);   // 编制营部,实际一班
        Personnel yi = person(2, "连直属乙", "在位", 2L, 0L);  // 无实际
        Personnel bing = person(3, "班直属丙", "在位", 3L, 3L); // 实际=编制
        RollCallExcelService roll = service(jia, yi, bing);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(roll.build(query("actualUnit", "true"))))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell battalion = headingCell(sheet, "营部(在位3人)");
            Cell company = headingCell(sheet, "一连(在位3人)");
            Cell squad = headingCell(sheet, "一班(在位2人)");
            assertEquals("A5", headerRegion(sheet, battalion).formatAsString());
            assertEquals("连直属乙", cell(sheet, nameStartRow(sheet, company), company.getColumnIndex()));
            assertEquals("借岗甲", cell(sheet, nameStartRow(sheet, squad), squad.getColumnIndex()));
            assertEquals("班直属丙", cell(sheet, nameStartRow(sheet, squad) + 1, squad.getColumnIndex()));
            assertEquals(1, occurrences(sheet, "借岗甲"), "借岗者只能在实际单位块出现一次");
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:3人    在位:3人    不在位:0人"));
        }
    }

    @Test void actualEqualToOrganicOrZeroOrOutsideHierarchyFallsBackUnchanged() throws Exception {
        Personnel same = person(1, "同岗甲", "在位", 2L, 2L);     // 实际=编制
        Personnel zero = person(2, "无实际乙", "在位", 2L, 0L);   // actual=0
        Personnel outside = person(3, "树外丙", "在位", 2L, 999L); // 实际单位不在层级内→回编制
        Personnel absent = person(4, "休岗丁", "休假", 3L, 999L); // 不在位者仍按原因归块
        RollCallExcelService roll = service(same, zero, outside, absent);
        try (XSSFWorkbook withSwitch = new XSSFWorkbook(new ByteArrayInputStream(roll.build(query("actualUnit", "true"))));
             XSSFWorkbook withoutSwitch = new XSSFWorkbook(new ByteArrayInputStream(roll.build(Collections.<String, String>emptyMap())))) {
            for (Sheet sheet : Arrays.asList(withSwitch.getSheetAt(0), withoutSwitch.getSheetAt(0))) {
                Cell company = headingCell(sheet, "一连(在位3人)");
                assertEquals("同岗甲", cell(sheet, nameStartRow(sheet, company), company.getColumnIndex()));
                assertEquals("无实际乙", cell(sheet, nameStartRow(sheet, company) + 1, company.getColumnIndex()));
                assertEquals("树外丙", cell(sheet, nameStartRow(sheet, company) + 2, company.getColumnIndex()));
                assertTrue(dumpValues(sheet).stream().noneMatch(v -> v.startsWith("一班(")), "无下属命中时不渲染空块");
                assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:4人    在位:3人    不在位:1人"));
            }
            assertEquals(dump(withoutSwitch.getSheetAt(0)), dump(withSwitch.getSheetAt(0)),
                    "回退场景下 actualUnit 开关不得改变任何版式");
        }
    }

    @Test void withoutActualUnitParamBorrowersStayInOrganicDepartments() throws Exception {
        Personnel jia = person(1, "借岗甲", "在位", 1L, 3L);
        RollCallExcelService roll = service(jia);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(roll.build(Collections.<String, String>emptyMap())))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell battalion = headingCell(sheet, "营部(在位1人)");
            assertEquals("借岗甲", cell(sheet, nameStartRow(sheet, battalion), battalion.getColumnIndex()));
            assertEquals(1, occurrences(sheet, "借岗甲"));
            assertTrue(dumpValues(sheet).stream().noneMatch(v -> v.startsWith("一班(")));
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:1人    在位:1人"));
        }
    }

    @Test void onDutyAndActualDepartmentFiltersNarrowBeforeGrouping() throws Exception {
        Personnel jia = person(1, "借岗甲", "在位", 1L, 2L);
        Personnel yi = person(2, "在编乙", "在位", 1L, 0L);
        Personnel bing = person(3, "借岗丙", "在位", 3L, 2L);
        RollCallExcelService roll = service(jia, yi, bing);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(
                roll.build(query("actualUnit", "true", "onDuty", "no"))))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell company = headingCell(sheet, "一连(在位2人)");
            assertEquals("借岗甲", cell(sheet, nameStartRow(sheet, company), company.getColumnIndex()));
            assertEquals("借岗丙", cell(sheet, nameStartRow(sheet, company) + 1, company.getColumnIndex()));
            assertEquals(0, occurrences(sheet, "在编乙"));
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:2人    在位:2人"));
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(
                roll.build(query("actualUnit", "true", "onDuty", "yes"))))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(1, occurrences(sheet, "在编乙"));
            assertEquals(0, occurrences(sheet, "借岗甲"));
            assertEquals(0, occurrences(sheet, "借岗丙"));
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:1人    在位:1人"));
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(
                roll.build(query("actualUnit", "true", "actualDepartmentId", "2"))))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(1, occurrences(sheet, "借岗甲"));
            assertEquals(1, occurrences(sheet, "借岗丙"));
            assertEquals(0, occurrences(sheet, "在编乙"));
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:2人    在位:2人"));
        }
    }

    @Test void sortingStaysInsideActualGroupsAndStatsConserveByPerson() throws Exception {
        Personnel low = person(1, "列兵借岗甲", "在位", 1L, 3L); low.setRank("列兵");
        Personnel high = person(2, "上校借岗乙", "在位", 2L, 3L); high.setRank("上校");
        Personnel organic = person(3, "连直属丙", "在位", 2L, 0L);
        Personnel absent = person(4, "休假丁", "休假", 3L, 2L); // 不在位:仍按原因归块,与分组开关无关
        RollCallExcelService roll = service(low, high, organic, absent);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(
                roll.build(query("actualUnit", "true", "sort", "rank:desc,name:asc"))))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell squad = headingCell(sheet, "一班(在位2人)");
            int start = nameStartRow(sheet, squad);
            assertEquals("上校借岗乙", cell(sheet, start, squad.getColumnIndex()));
            assertEquals("列兵借岗甲", cell(sheet, start + 1, squad.getColumnIndex()));
            Cell company = headingCell(sheet, "一连(在位3人)");
            assertEquals("连直属丙", cell(sheet, nameStartRow(sheet, company), company.getColumnIndex()));
            assertEquals(1, occurrences(sheet, "休假丁"));
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:4人    在位:3人    不在位:1人"));
        }
    }
}
