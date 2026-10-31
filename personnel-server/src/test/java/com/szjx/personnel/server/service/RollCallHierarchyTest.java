package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.szjx.personnel.server.service.RollCallExcelTest.cell;
import static com.szjx.personnel.server.service.RollCallExcelTest.headerRegion;
import static com.szjx.personnel.server.service.RollCallExcelTest.headingCell;
import static com.szjx.personnel.server.service.RollCallExcelTest.headingRow;
import static com.szjx.personnel.server.service.RollCallExcelTest.nameStartRow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Isolated SQLite metadata + actual POI full-width parent names before child headers. */
class RollCallHierarchyTest {
    private final JdbcTemplate jdbc = RollCallExcelTest.metadataJdbc();
    private final PersonnelService people = mock(PersonnelService.class);
    private final DepartmentService departments = mock(DepartmentService.class);
    private final RollCallExcelService service = new RollCallExcelService(people, departments, jdbc);
    private long nextId = 1;

    @AfterEach void cleanUp() { DataScope.clear(); RollCallExcelTest.closeJdbc(jdbc); }

    private static Department dept(long id, String name, Long parent, int sort) {
        Department d = new Department(); d.setId(id); d.setName(name); d.setParentId(parent); d.setSort(sort);
        d.setTotal(99999L); // Export counts must not use the model's full-database total.
        return d;
    }

    private void metadata(Department... rows) { RollCallExcelTest.metadata(jdbc, rows); }

    private Personnel person(String name, Long department) {
        Personnel p = new Personnel(); p.setId(nextId++); p.setName(name); p.setDepartmentId(department); p.setStatus("在位");
        return p;
    }

    private XSSFWorkbook export(Map<String, String> query, Personnel... rows) throws Exception {
        when(people.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(rows));
        return new XSSFWorkbook(new ByteArrayInputStream(service.build(query)));
    }

    private static List<String> cells(Sheet sheet) {
        List<String> result = new ArrayList<String>();
        for (Row row : sheet) for (Cell c : row) result.add(cell(sheet, row.getRowNum(), c.getColumnIndex()));
        return result;
    }

    private static void exactlyOnce(Sheet sheet, Personnel... rows) {
        List<String> values = cells(sheet);
        for (Personnel p : rows) {
            assertEquals(1, Collections.frequency(values, p.getName()), p.getName());
            Cell name = headingCell(sheet, p.getName());
            org.apache.poi.ss.util.CellRangeAddress region = headerRegion(sheet, name);
            assertEquals(name.getRowIndex(), region.getFirstRow(), p.getName());
            assertEquals(name.getRowIndex(), region.getLastRow(), "Names may merge horizontally, never vertically");
        }
    }

    @Test void fourLevelsPutEachParentNamesBeforeChildHeadersAndConserveTotals() throws Exception {
        metadata(dept(1, "一营", 0L, 0), dept(2, "一连", 1L, 0), dept(3, "一排", 2L, 0), dept(4, "一班", 3L, 0));
        Personnel battalion = person("营直属甲", 1L); battalion.setStatus("在职");
        Personnel company = person("连直属乙", 2L), platoon = person("排直属丙", 3L), squad = person("班直属丁", 4L);
        Personnel absent = person("休假人员", 4L); absent.setStatus("休假");
        try (XSSFWorkbook wb = export(Collections.emptyMap(), squad, platoon, company, absent, battalion)) {
            Sheet sheet = wb.getSheetAt(0);
            Cell root = headingCell(sheet, "一营(在位4人)"), co = headingCell(sheet, "一连(在位3人)");
            Cell pl = headingCell(sheet, "一排(在位2人)"), sq = headingCell(sheet, "一班(在位1人)");
            assertEquals("A5", headerRegion(sheet, root).formatAsString());
            assertEquals("A7", headerRegion(sheet, co).formatAsString());
            assertEquals("A9", headerRegion(sheet, pl).formatAsString());
            assertEquals(10, sq.getRowIndex()); assertEquals(0, sq.getColumnIndex());
            Cell[] headings = {root, co, pl, sq};
            String[] directNames = {battalion.getName(), company.getName(), platoon.getName(), squad.getName()};
            for (int i = 0; i < directNames.length; i++) {
                int start = nameStartRow(sheet, headings[i]);
                assertEquals(5 + 2 * i, start);
                assertEquals(0, headings[i].getColumnIndex());
                assertEquals(directNames[i], cell(sheet, start, 0));
            }
            assertTrue(cells(sheet).stream().noneMatch(value -> value.contains("直属人员")));
            exactlyOnce(sheet, battalion, company, platoon, squad, absent);
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:5人    在位:4人    不在位:1人"));
            assertTrue(wb.getPrintArea(0).endsWith("$F$" + (sheet.getLastRowNum() + 1)));
            assertTrue(sheet.getPrintSetup().getLandscape()); assertEquals(9, sheet.getPrintSetup().getPaperSize());
            assertEquals(1, sheet.getPrintSetup().getFitWidth()); assertEquals(1, sheet.getPrintSetup().getFitHeight());
        }
        verifyNoInteractions(departments);
    }

    @Test void sameNamesInDifferentBranchesStaySeparateAndSiblingsUseSortThenIdLeftToRight() throws Exception {
        metadata(dept(10, "一营", 0L, 20), dept(20, "二营", 0L, 10),
                dept(30, "一连", 10L, 0), dept(40, "一连", 20L, 0),
                dept(31, "一排", 30L, 0), dept(41, "一排", 40L, 0),
                dept(32, "一班", 31L, 0), dept(42, "一班", 41L, 0),
                dept(47, "同序后排", 40L, 5), dept(46, "优先排", 40L, -2), dept(45, "同序先排", 40L, 5));
        Personnel one = person("一营班人员", 32L), two = person("二营班人员", 42L);
        Personnel last = person("同序后人员", 47L), first = person("优先人员", 46L), middle = person("同序先人员", 45L);
        try (XSSFWorkbook wb = export(Collections.emptyMap(), one, last, middle, two, first)) {
            Sheet sheet = wb.getSheetAt(0); List<String> values = cells(sheet);
            Cell secondRoot = headingCell(sheet, "二营(在位4人)"), firstRoot = headingCell(sheet, "一营(在位1人)");
            Cell priority = headingCell(sheet, "优先排(在位1人)"), middleHead = headingCell(sheet, "同序先排(在位1人)");
            Cell lastHead = headingCell(sheet, "同序后排(在位1人)");
            assertEquals(secondRoot.getRowIndex(), firstRoot.getRowIndex());
            assertTrue(secondRoot.getColumnIndex() < firstRoot.getColumnIndex());
            assertEquals(priority.getRowIndex(), middleHead.getRowIndex()); assertEquals(middleHead.getRowIndex(), lastHead.getRowIndex());
            assertTrue(priority.getColumnIndex() < middleHead.getColumnIndex() && middleHead.getColumnIndex() < lastHead.getColumnIndex());
            assertEquals(2, values.stream().filter(v -> v.startsWith("一连(在位")).count());
            assertEquals(2, values.stream().filter(v -> v.equals("一班(在位1人)")).count());
            Cell firstSquad = headingCell(sheet, "一班(在位1人)", 0), secondSquad = headingCell(sheet, "一班(在位1人)", 1);
            assertEquals(firstSquad.getRowIndex(), secondSquad.getRowIndex());
            assertTrue(firstSquad.getColumnIndex() < secondSquad.getColumnIndex());
            assertEquals(two.getName(), cell(sheet, nameStartRow(sheet, firstSquad), firstSquad.getColumnIndex()));
            assertEquals(one.getName(), cell(sheet, nameStartRow(sheet, secondSquad), secondSquad.getColumnIndex()));
            assertEquals("A5:D5", headerRegion(sheet, secondRoot).formatAsString());
            assertEquals("A7", headerRegion(sheet, priority).formatAsString());
            assertEquals(first.getName(), cell(sheet, nameStartRow(sheet, priority), priority.getColumnIndex()));
            assertEquals(middle.getName(), cell(sheet, nameStartRow(sheet, middleHead), middleHead.getColumnIndex()));
            assertEquals(last.getName(), cell(sheet, nameStartRow(sheet, lastHead), lastHead.getColumnIndex()));
            exactlyOnce(sheet, one, two, last, first, middle);
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:5人    在位:5人"));
        }
    }

    @Test void departmentFilterKeepsEmptyAncestorHeadersButNotTheirPeopleOrUnmatchedBranches() throws Exception {
        metadata(dept(1, "祖先营", 0L, 0), dept(2, "目标连", 1L, 0), dept(3, "目标排", 2L, 0),
                dept(4, "目标班", 3L, 0), dept(5, "无命中分支", 1L, 1));
        Personnel ancestor = person("不应导出营直属", 1L), direct = person("目标排直属", 3L), child = person("目标班直属", 4L), other = person("不应导出其他", 5L);
        try (XSSFWorkbook wb = export(Collections.singletonMap("departmentId", "3"), ancestor, other, child, direct)) {
            Sheet sheet = wb.getSheetAt(0);
            Cell root = headingCell(sheet, "祖先营(在位2人)"), company = headingCell(sheet, "目标连(在位2人)"), platoon = headingCell(sheet, "目标排(在位2人)");
            assertEquals(root.getRowIndex() + 1, company.getRowIndex()); assertEquals(company.getRowIndex() + 1, platoon.getRowIndex());
            assertEquals("A5", headerRegion(sheet, root).formatAsString());
            assertEquals("A6", headerRegion(sheet, company).formatAsString());
            Cell squad = headingCell(sheet, "目标班(在位1人)");
            int directStart = nameStartRow(sheet, platoon), childStart = nameStartRow(sheet, squad);
            assertEquals(7, directStart); assertEquals(directStart + 2, childStart);
            assertEquals(directStart + 1, squad.getRowIndex());
            assertEquals(direct.getName(), cell(sheet, directStart, 0)); assertEquals(child.getName(), cell(sheet, childStart, 0));
            assertTrue(cells(sheet).stream().noneMatch(value -> value.contains("直属人员")), "No virtual direct personnel headers");
            exactlyOnce(sheet, direct, child); String text = cells(sheet).toString();
            assertFalse(text.contains("不应导出")); assertFalse(text.contains("无命中分支"));
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:2人    在位:2人"));
        }
    }

    @Test void zeroUnknownUnassignedAndOrphanDepartmentsHaveIndependentFallbackColumns() throws Exception {
        metadata(dept(1, "正常单位", 0L, 0), dept(2, "孤儿单位", 777L, 1));
        Personnel zero = person("零单位人员", 0L), assigned = person("正常人员", 1L), orphan = person("孤儿人员", 2L);
        Personnel missingA = person("缺失甲", 999L), missingB = person("缺失乙", 998L), none = person("未分配人员", null);
        missingA.setDepartmentName("相同历史单位"); missingB.setDepartmentName("相同历史单位");
        try (XSSFWorkbook wb = export(Collections.emptyMap(), missingA, none, zero, missingB, orphan, assigned)) {
            Sheet sheet = wb.getSheetAt(0);
            exactlyOnce(sheet, zero, assigned, orphan, missingA, missingB, none);
            String[] titles = {"正常单位(在位1人)", "孤儿单位(在位1人)", "相同历史单位(单位ID:999)(在位1人)",
                    "未分配单位(在位1人)", "缺失单位(单位ID:0)(在位1人)", "相同历史单位(单位ID:998)(在位1人)"};
            Personnel[] expected = {assigned, orphan, missingA, none, zero, missingB};
            for (int col = 0; col < titles.length; col++) {
                Cell heading = headingCell(sheet, titles[col]); assertEquals(col, heading.getColumnIndex());
                assertEquals(4, heading.getRowIndex()); assertEquals(expected[col].getName(), cell(sheet, nameStartRow(sheet, heading), col));
            }
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:6人    在位:6人"));
        }
    }

    @Test void longParentNamesFillDownBeforeChildHeaderAndAbsentArea() throws Exception {
        metadata(dept(1, "一排", 0L, 0), dept(2, "一班", 1L, 0));
        Personnel[] rows = new Personnel[10];
        for (int i = 0; i < 8; i++) rows[i] = person("排直属" + i, 1L);
        rows[8] = person("班直属", 2L);
        rows[9] = person("休假甲", 2L); rows[9].setStatus("休假");
        try (XSSFWorkbook wb = export(Collections.emptyMap(), rows)) {
            Sheet sheet = wb.getSheetAt(0);
            int names = nameStartRow(sheet, headingCell(sheet, "一排(在位9人)")); assertEquals(5, names);
            for (int i = 0; i < 8; i++) assertEquals(rows[i].getName(), cell(sheet, names + i, 0));
            Cell squad = headingCell(sheet, "一班(在位1人)"); assertEquals(0, squad.getColumnIndex());
            assertEquals(names + 8, squad.getRowIndex());
            int squadStart = nameStartRow(sheet, squad); assertEquals(14, squadStart);
            assertEquals(rows[8].getName(), cell(sheet, squadStart, 0));
            for (int row = names; row <= squadStart; row++) for (int col = 1; col < 6; col++) {
                assertEquals("", cell(sheet, row, col));
                assertEquals(org.apache.poi.ss.usermodel.BorderStyle.THIN, sheet.getRow(row).getCell(col).getCellStyle().getBorderBottom());
            }
            assertEquals(16, headingRow(sheet, "不在位人员(共1人:休假1人)"));
            for (int col = 0; col < 6; col++) assertEquals("", cell(sheet, 15, col), "One blank row before absent title");
            assertTrue(headingRow(sheet, "休假(1人)") > 16);
            exactlyOnce(sheet, rows);
        }
    }

    @Test void metadataAncestorsNeverExpandRestrictedPersonnelScopeOrCounts() throws Exception {
        metadata(dept(1, "共同营", 0L, 0), dept(2, "可见连", 1L, 0), dept(3, "不可见连", 1L, 1), dept(4, "空分支", 1L, 2));
        Personnel root = person("上级秘密人员", 1L), foreign = person("其他秘密人员", 3L), none = person("未分配秘密人员", null);
        Personnel visible = person("可见党员", 2L); visible.setPoliticalStatus("中共党员");
        DataScope.set(new DataScope.Context(10L, "sub", false, Collections.singleton(2L)));
        try (XSSFWorkbook wb = export(Collections.emptyMap(), root, foreign, none, visible)) {
            Sheet sheet = wb.getSheetAt(0); String text = cells(sheet).toString();
            headingRow(sheet, "共同营(在位1人)"); Cell child = headingCell(sheet, "可见连(在位1人)");
            exactlyOnce(sheet, visible); assertEquals(visible.getName(), cell(sheet, nameStartRow(sheet, child), child.getColumnIndex()));
            assertFalse(text.contains("秘密")); assertFalse(text.contains("不可见连")); assertFalse(text.contains("空分支"));
            assertFalse(text.contains("☭") || text.contains("✪") || text.contains("⊛"));
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:1人    在位:1人"));
        }
        verify(jdbc, never()).queryForList(contains("driver_license"));
        DataScope.set(new DataScope.Context(10L, "empty", false, Collections.emptySet()));
        try (XSSFWorkbook wb = export(Collections.emptyMap(), root, foreign, none, visible)) {
            Sheet sheet = wb.getSheetAt(0); String text = cells(sheet).toString();
            assertFalse(text.contains("共同营")); assertFalse(text.contains("可见连"));
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:0人    在位:0人"));
        }
    }

    @Test void multipleSortKeysRemainInsideEachUnitNameBlockAndAbsentReason() throws Exception {
        metadata(dept(1, "一排", 0L, 0), dept(2, "一班", 1L, 0));
        Personnel parentLow = person("李排低", 1L); parentLow.setRank("列兵");
        Personnel parentHigh = person("王排高", 1L); parentHigh.setRank("上校");
        Personnel childLow = person("李班低", 2L); childLow.setRank("列兵");
        Personnel childHigh = person("王班高", 2L); childHigh.setRank("上校");
        Personnel leaveLow = person("李休低", 2L); leaveLow.setStatus("休假"); leaveLow.setRank("列兵");
        Personnel leaveHigh = person("王休高", 1L); leaveHigh.setStatus("休假"); leaveHigh.setRank("上校");
        Personnel dutyLow = person("李差低", 1L); dutyLow.setStatus("出差"); dutyLow.setRank("列兵");
        Personnel dutyHigh = person("王差高", 2L); dutyHigh.setStatus("出差"); dutyHigh.setRank("上校");
        Map<String, String> query = new HashMap<String, String>(); query.put("sort", "rank:desc,name:asc");
        try (XSSFWorkbook wb = export(query, parentLow, childLow, leaveLow, dutyLow, childHigh, parentHigh, dutyHigh, leaveHigh)) {
            Sheet sheet = wb.getSheetAt(0);
            int parentStart = nameStartRow(sheet, headingCell(sheet, "一排(在位4人)"));
            int childStart = nameStartRow(sheet, headingCell(sheet, "一班(在位2人)"));
            assertEquals(5, parentStart); assertEquals(8, childStart);
            assertEquals(parentHigh.getName(), cell(sheet, parentStart, 0)); assertEquals(parentLow.getName(), cell(sheet, parentStart + 1, 0));
            assertEquals(childHigh.getName(), cell(sheet, childStart, 0)); assertEquals(childLow.getName(), cell(sheet, childStart + 1, 0));
            int leave = headingRow(sheet, "休假(2人)"), duty = headingRow(sheet, "出差(2人)"); assertTrue(leave < duty);
            assertEquals(leaveHigh.getName(), cell(sheet, leave, 1)); assertEquals(leaveLow.getName(), cell(sheet, leave, 2));
            assertEquals(dutyHigh.getName(), cell(sheet, duty, 1)); assertEquals(dutyLow.getName(), cell(sheet, duty, 2));
            exactlyOnce(sheet, parentLow, childLow, leaveLow, dutyLow, childHigh, parentHigh, dutyHigh, leaveHigh);
        }
    }

    @Test void allExistingPersonnelFiltersStillReachTheScopedQueryUnchanged() throws Exception {
        metadata(dept(1, "目标单位", 0L, 0));
        Map<String, String> query = new HashMap<String, String>();
        String[] fields = {"keyword", "name", "idCard", "status", "gender", "politicalStatus", "category", "rank", "post",
                "nativePlace", "enlistPlace", "education", "maritalStatus", "nation", "bloodType", "onlyChild", "locationPoint", "entryDateStart", "entryDateEnd"};
        for (String field : fields) query.put(field, " " + field + " ");
        query.put("departmentId", "1"); Personnel visible = person("筛选结果", 1L);
        try (XSSFWorkbook wb = export(query, visible)) { exactlyOnce(wb.getSheetAt(0), visible); }
        verify(people).query("keyword", "name", "idCard", null, "status", "gender", "politicalStatus", "category", "rank", "post",
                "nativePlace", "enlistPlace", "education", "maritalStatus", "nation", "bloodType", "onlyChild", "locationPoint", "entryDateStart", "entryDateEnd", 1, 1000000);
    }

    @Test void explicitMissingDepartmentFilterStillKeepsItsDirectPeople() throws Exception {
        Personnel retained = person("缺失单位直属", 999L), unrelated = person("其他未分配", null);
        try (XSSFWorkbook wb = export(Collections.singletonMap("departmentId", "999"), retained, unrelated)) {
            Sheet sheet = wb.getSheetAt(0); exactlyOnce(sheet, retained); assertFalse(cells(sheet).contains(unrelated.getName()));
            Cell heading = headingCell(sheet, "缺失单位(单位ID:999)(在位1人)");
            assertEquals(retained.getName(), cell(sheet, nameStartRow(sheet, heading), heading.getColumnIndex()));
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:1人    在位:1人"));
        }
    }

    @Test void veryDeepTreeUsesIterativeHeadersAndBoundedStyles() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            int depth = 1600; List<Object[]> departments = new ArrayList<Object[]>();
            for (int i = 1; i <= depth; i++) departments.add(new Object[]{i, "层级" + i, i - 1, 0});
            jdbc.batchUpdate("INSERT INTO department(id,name,parent_id,sort) VALUES(?,?,?,?)", departments);
            Personnel deepest = person("最深直属人员", (long) depth);
            try (XSSFWorkbook wb = export(Collections.emptyMap(), deepest)) {
                Sheet sheet = wb.getSheetAt(0); Cell root = headingCell(sheet, "层级1(在位1人)"), leaf = headingCell(sheet, "层级1600(在位1人)");
                assertEquals(depth - 1, leaf.getRowIndex() - root.getRowIndex());
                assertEquals(root.getColumnIndex(), leaf.getColumnIndex());
                assertEquals(leaf.getRowIndex() + 1, nameStartRow(sheet, leaf));
                assertEquals(deepest.getName(), cell(sheet, nameStartRow(sheet, leaf), leaf.getColumnIndex())); exactlyOnce(sheet, deepest);
                assertTrue(wb.getNumCellStyles() < 40, "Do not allocate one style per department");
            }
        });
    }

    @Test void cyclesSelfCyclesAndOrphansTerminateAndKeepAllPeople() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            metadata(dept(1, "环甲", 2L, 0), dept(2, "环乙", 3L, 0), dept(3, "环丙", 1L, 0),
                    dept(4, "自环", 4L, 0), dept(5, "孤儿", 999L, 0));
            Personnel[] rows = {person("甲人员", 1L), person("乙人员", 2L), person("丙人员", 3L), person("自环人员", 4L), person("孤儿人员", 5L)};
            try (XSSFWorkbook wb = export(Collections.emptyMap(), rows)) {
                Sheet sheet = wb.getSheetAt(0); exactlyOnce(sheet, rows);
                for (String name : Arrays.asList("环甲", "环乙", "环丙", "自环", "孤儿"))
                    assertEquals(1, cells(sheet).stream().filter(value -> value.startsWith(name + "(在位")).count());
                String[] titles = {"环甲(在位3人)", "环乙(在位1人)", "环丙(在位2人)", "自环(在位1人)", "孤儿(在位1人)"};
                int[] columns = {0, 0, 0, 1, 2}; // One leaf under 甲 -> 丙 -> 乙; no extra parent columns.
                for (int i = 0; i < titles.length; i++) {
                    Cell heading = headingCell(sheet, titles[i]);
                    assertEquals(columns[i], heading.getColumnIndex());
                    assertEquals(rows[i].getName(), cell(sheet, nameStartRow(sheet, heading), columns[i]));
                }
                assertEquals("A5", headerRegion(sheet, headingCell(sheet, "环甲(在位3人)")).formatAsString());
                assertEquals(4, headingRow(sheet, "环甲(在位3人)"));
                assertEquals(6, headingRow(sheet, "环丙(在位2人)"));
                assertEquals(8, headingRow(sheet, "环乙(在位1人)"));
                assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:5人    在位:5人"));
            }
        });
    }
}
