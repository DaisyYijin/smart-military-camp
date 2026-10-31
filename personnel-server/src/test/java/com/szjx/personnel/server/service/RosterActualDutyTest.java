package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 花名册“编制+实际在岗”列与筛选:实际单位列取值、onDuty/actualDepartmentId
 * 通过 PersonnelExtraFilters 一次收窄,以及权限隔离与 jdbc 缺失时的显式报错。
 * 人员服务为测试替身;extras 筛选读隔离 SQLite 的 personnel 表,不触正式数据。
 */
class RosterActualDutyTest {
    private final List<JdbcTemplate> databases = new ArrayList<JdbcTemplate>();
    private JdbcTemplate jdbc;

    @AfterEach void clearScopeAndClose() {
        DataScope.clear();
        for (JdbcTemplate db : databases) ((SingleConnectionDataSource) db.getDataSource()).destroy();
        databases.clear();
        jdbc = null;
    }

    /** 返回新花名册服务:人员来自替身,extras 筛选读隔离库 personnel(镜像候选人的单位与实际单位)。 */
    private RosterExcelService service(List<Department> tree, Personnel... rows) {
        SingleConnectionDataSource source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        jdbc = new JdbcTemplate(source);
        databases.add(jdbc);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT DEFAULT '', department_id INTEGER DEFAULT 0, "
                + "actual_department_id INTEGER DEFAULT 0, actual_post TEXT DEFAULT '', actual_post_date TEXT DEFAULT '')");
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
        when(departments.tree()).thenReturn(tree);
        return new RosterExcelService(people, departments, jdbc);
    }

    /** actual_post/actual_post_date 为历史库列,建表保留但业务已不读写。 */
    private static Personnel person(long id, String name, long organic, Long actual) {
        Personnel p = new Personnel();
        p.setId(id); p.setName(name); p.setGender("男");
        p.setDepartmentId(organic); p.setStatus("在职");
        if (actual != null) p.setActualDepartmentId(actual);
        return p;
    }

    private static Department dept(long id, String name, Long parent) {
        Department d = new Department();
        d.setId(id); d.setName(name); d.setParentId(parent); d.setSort(0);
        d.setChildren(new ArrayList<Department>());
        return d;
    }

    private static Map<String, String> query(String... pairs) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }

    private static String text(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row); Cell cell = r == null ? null : r.getCell(col);
        return cell == null ? "" : new DataFormatter().formatCellValue(cell);
    }

    private static int column(Sheet sheet, String label) {
        for (Cell cell : sheet.getRow(3)) if (label.equals(new DataFormatter().formatCellValue(cell))) return cell.getColumnIndex();
        fail("Missing column: " + label); return -1;
    }

    private static String all(RosterExcelService roster, Map<String, String> q) throws Exception {
        StringBuilder value = new StringBuilder();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(roster.build(q)))) {
            for (Row row : wb.getSheetAt(0)) for (Cell cell : row)
                value.append(new DataFormatter().formatCellValue(cell)).append('\n');
        }
        return value.toString();
    }

    /** 姓名列出现的行(按传入名单),同一人绝不允许拆成多行。 */
    private static Map<String, Integer> peopleIn(Sheet sheet, String... names) {
        Map<String, Integer> rows = new LinkedHashMap<String, Integer>();
        int name = column(sheet, "姓名");
        for (int r = 4; r <= sheet.getLastRowNum(); r++) {
            String value = text(sheet, r, name);
            if (Arrays.asList(names).contains(value)) assertNull(rows.put(value, r), value + " must stay a single row");
        }
        for (String n : names) assertTrue(rows.containsKey(n), "Missing person: " + n);
        return rows;
    }

    private static long retainReads(JdbcTemplate db) {
        return mockingDetails(db).getInvocations().stream().filter(call -> call.getMethod().getName().equals("queryForList")
                && call.getArguments().length > 0 && String.valueOf(call.getArguments()[0]).startsWith("SELECT p.id")).count();
    }

    @Test void metadataExposesActualUnitFieldWhileDefaultColumnsStayUnchanged() throws Exception {
        RosterExcelService roster = service(Collections.<Department>emptyList());
        Map<String, String> labels = new LinkedHashMap<String, String>();
        for (Map<String, String> field : roster.fieldOptions()) labels.put(field.get("key"), field.get("label"));
        assertEquals("实际单位", labels.get("actualUnit"));
        assertFalse(labels.containsKey("actualPost"), "实际岗位字段已删除");
        assertFalse(labels.containsKey("actualPostDate"), "实际在岗开始时间字段已删除");
        String output = all(roster, Collections.<String, String>emptyMap()); // 未传 fields → 默认列
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(roster.build(Collections.<String, String>emptyMap())))) {
            Sheet sheet = wb.getSheetAt(0);
            String[] defaults = {"序号", "姓名", "性别", "身份证号", "编制单位", "所在点位", "军衔/职级", "职务", "状态", "入伍日期", "手机号"};
            assertEquals(11, sheet.getRow(3).getLastCellNum());
            for (int c = 0; c < defaults.length; c++) assertEquals(defaults[c], text(sheet, 3, c));
        }
        assertFalse(output.contains("实际单位"));
    }

    @Test void actualUnitColumnRendersPathWithEmptyFallback() throws Exception {
        Personnel borrowed = person(1, "借岗甲", 1L, 2L);
        borrowed.setActualDepartmentPath("总队 / 一营 / 一连");
        Personnel organic = person(2, "在编乙", 2L, null);
        RosterExcelService roster = service(Arrays.asList(dept(1L, "营部", 0L), dept(2L, "一连", 1L)), borrowed, organic);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(
                roster.build(query("fields", "name,actualUnit"))))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("实际单位", text(sheet, 3, 2));
            Map<String, Integer> rows = peopleIn(sheet, "借岗甲", "在编乙");
            int first = rows.get("借岗甲"), second = rows.get("在编乙");
            assertEquals("总队 / 一营 / 一连", text(sheet, first, column(sheet, "实际单位")));
            assertEquals("", text(sheet, second, column(sheet, "实际单位")));
        }
        assertTrue(all(roster, query("fields", "name")).contains("合计:2人"));
    }

    @Test void onDutyAndActualDepartmentFiltersNarrowRowsAndTotals() throws Exception {
        // 甲:借岗(编制营部,实际一连);乙:在编在岗;丙:借岗到一连;丁:实际=编制
        Personnel jia = person(1, "借岗甲", 1L, 2L);
        Personnel yi = person(2, "在编乙", 1L, 0L);
        Personnel bing = person(3, "借岗丙", 3L, 2L);
        Personnel ding = person(4, "同岗丁", 3L, 3L);
        RosterExcelService roster = service(Arrays.asList(dept(1L, "营部", 0L), dept(2L, "一连", 0L), dept(3L, "二连", 0L)), jia, yi, bing, ding);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(roster.build(query("fields", "name", "onDuty", "yes"))))) {
            assertEquals(new HashSet<String>(Arrays.asList("在编乙", "同岗丁")), peopleIn(wb.getSheetAt(0), "在编乙", "同岗丁").keySet());
        }
        assertTrue(all(roster, query("fields", "name", "onDuty", "yes")).contains("合计:2人"));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(roster.build(query("fields", "name", "onDuty", "no"))))) {
            assertEquals(new HashSet<String>(Arrays.asList("借岗甲", "借岗丙")), peopleIn(wb.getSheetAt(0), "借岗甲", "借岗丙").keySet());
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(roster.build(query("fields", "name", "actualDepartmentId", "2"))))) {
            assertEquals(new HashSet<String>(Arrays.asList("借岗甲", "借岗丙")), peopleIn(wb.getSheetAt(0), "借岗甲", "借岗丙").keySet());
        }
        assertTrue(all(roster, query("fields", "name", "actualDepartmentId", "2")).contains("合计:2人"));
        // 组合驾驶证+实际在岗:一次 retainMatching 同时收窄
        jdbc.execute("CREATE TABLE driver_license(id INTEGER PRIMARY KEY, personnel_id INTEGER, license_type TEXT, license_no TEXT, vehicle_class TEXT, issue_date TEXT, expiry_date TEXT, license_status TEXT, review_date TEXT, issuing_auth TEXT)");
        jdbc.update("INSERT INTO driver_license(id,personnel_id,license_type,license_no,license_status) VALUES(1,1,'军本','0001','有效')");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(
                roster.build(query("fields", "name", "driverLicense", "yes", "onDuty", "no"))))) {
            assertEquals(Collections.singleton("借岗甲"), peopleIn(wb.getSheetAt(0), "借岗甲").keySet());
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(
                roster.build(query("fields", "name", "driverLicense", "no", "onDuty", "no"))))) {
            assertTrue(peopleIn(wb.getSheetAt(0)).isEmpty());
        }
    }

    @Test void restrictedScopesNeverExpandActualDutyExportsOrFilters() throws Exception {
        Personnel jia = person(1, "借岗甲", 1L, 2L);
        Personnel bing = person(3, "借岗丙", 3L, 2L);
        RosterExcelService roster = service(Arrays.asList(dept(1L, "营部", 0L), dept(2L, "一连", 0L), dept(3L, "二连", 0L)), jia, bing);
        DataScope.set(new DataScope.Context(10L, "child", false, new HashSet<Long>(Arrays.asList(1L))));
        String output = all(roster, query("fields", "name,actualUnit", "onDuty", "no"));
        assertTrue(output.contains("借岗甲"));
        assertFalse(output.contains("借岗丙"));
        assertTrue(output.contains("合计:1人"));
        DataScope.set(new DataScope.Context(10L, "empty", false, Collections.<Long>emptySet()));
        String none = all(roster, query("fields", "name", "onDuty", "no"));
        assertFalse(none.contains("借岗甲"));
        assertFalse(none.contains("借岗丙"));
        assertTrue(none.contains("合计:0人"));
    }

    @Test void extrasNeedJdbcExplicitlyAndEmptyCandidatesIssueNoQueries() throws Exception {
        Personnel jia = person(1, "借岗甲", 1L, 2L);
        service(Collections.<Department>emptyList(), jia);
        PersonnelService emptyPeople = mock(PersonnelService.class);
        when(emptyPeople.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<Personnel>());
        DepartmentService departments = mock(DepartmentService.class);
        when(departments.tree()).thenReturn(Collections.<Department>emptyList());
        RosterExcelService legacy = new RosterExcelService(emptyPeople, departments);
        assertNotNull(legacy.build(query("fields", "name"))); // 无extras、无驾驶证列:旧两参构造仍可用
        assertThrows(IllegalStateException.class, () -> legacy.build(query("fields", "name", "onDuty", "yes")));
        assertThrows(IllegalStateException.class, () -> legacy.build(query("fields", "name", "actualDepartmentId", "2")));
        assertThrows(IllegalStateException.class, () -> legacy.build(query("fields", "name", "driverLicense", "yes")));
        // 空候选:extras 非空也不触发任何筛选查询
        JdbcTemplate observed = spy(jdbc);
        RosterExcelService checked = new RosterExcelService(emptyPeople, departments, observed);
        checked.build(query("fields", "name", "onDuty", "yes"));
        assertEquals(0L, retainReads(observed));
    }
}
