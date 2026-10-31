package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real POI output assertions: sort only names inside existing blocks, never their layout or scope. */
class RollCallSortTest {
    private final PersonnelService people = mock(PersonnelService.class);
    private final DepartmentService departments = mock(DepartmentService.class);
    private final JdbcTemplate jdbc = RollCallExcelTest.metadataJdbc();
    private final RollCallExcelService service = new RollCallExcelService(people, departments, jdbc);
    private long nextId = 100;

    RollCallSortTest() throws Exception {
        RollCallExcelTest.metadata(jdbc, dept(1L, "营部"));
        // Fixture setup is not an export data access (invalid-sort tests assert no interactions).
        clearInvocations(jdbc);
    }

    @AfterEach void clearScope() { DataScope.clear(); RollCallExcelTest.closeJdbc(jdbc); }

    private Personnel person(String name) {
        Personnel p = new Personnel();
        p.setId(nextId--); // Deliberately descending: implicit ID tie-breaking must not change the order.
        p.setName(name);
        p.setStatus("在位");
        p.setDepartmentId(1L);
        return p;
    }

    private static Department dept(long id, String name, Department... children) {
        Department d = new Department(); d.setId(id); d.setName(name);
        d.setChildren(Arrays.asList(children));
        return d;
    }

    private void rows(List<Personnel> values) throws Exception {
        when(people.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(values);
    }

    private XSSFWorkbook export(String sort, List<Personnel> values) throws Exception {
        Map<String, String> q = new HashMap<String, String>();
        if (sort != null) q.put("sort", sort);
        return export(q, values);
    }

    private XSSFWorkbook export(Map<String, String> q, List<Personnel> values) throws Exception {
        rows(values);
        return new XSSFWorkbook(new ByteArrayInputStream(service.build(q)));
    }

    private static String cell(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        Cell c = r == null ? null : r.getCell(col);
        return c == null ? "" : new DataFormatter().formatCellValue(c);
    }

    private static List<String> names(Sheet sheet, String heading, int count) {
        Cell unit = findCell(sheet, heading);
        int row = RollCallExcelTest.nameStartRow(sheet, unit), col = unit.getColumnIndex();
        CellRangeAddress unitSpan = RollCallExcelTest.headerRegion(sheet, unit);
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            result.add(cell(sheet, row + i, col));
            Cell name = sheet.getRow(row + i).getCell(col);
            CellRangeAddress nameSpan = RollCallExcelTest.headerRegion(sheet, name);
            assertEquals(row + i, nameSpan.getFirstRow()); assertEquals(row + i, nameSpan.getLastRow());
            assertEquals(unitSpan.getFirstColumn(), nameSpan.getFirstColumn());
            assertEquals(unitSpan.getLastColumn(), nameSpan.getLastColumn(), "Sorted names retain the full unit width");
        }
        return result;
    }

    private static List<String> labels(List<Personnel> rows) {
        List<String> result = new ArrayList<String>();
        for (Personnel p : rows) result.add(p.getName() == null ? "" : p.getName());
        return result;
    }

    private static Cell findCell(Sheet sheet, String startsWith) {
        for (Row row : sheet) for (Cell c : row) {
            if (cell(sheet, row.getRowNum(), c.getColumnIndex()).startsWith(startsWith)) return c;
        }
        fail("Missing cell: " + startsWith); return null;
    }

    private static int findRow(Sheet sheet, String startsWith) { return findCell(sheet, startsWith).getRowIndex(); }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void noSortRetainsOriginalOrderAndDefaultMarks(String sort) throws Exception {
        Personnel first = person("张三"); first.setEntryDate("2024-01-01"); first.setPoliticalStatus("中共党员");
        Personnel second = person("李四"); second.setEntryDate("2001-01-01");
        List<Personnel> input = Arrays.asList(first, second, person("王五"));
        try (XSSFWorkbook wb = export(sort, input)) {
            assertEquals(labels(input), names(wb.getSheetAt(0), "营部(", input.size()));
            assertEquals("应到:3人    在位:3人    不在位:0人    实到:        人    点名人:",
                    cell(wb.getSheetAt(0), wb.getSheetAt(0).getLastRowNum(), 0));
        }
        verify(jdbc, never()).queryForList(contains("driver_license"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"asc", "desc"})
    void serviceYearsUsesEffectiveDatePreciselyAndKeepsUnavailableLast(String direction) throws Exception {
        Personnel equivalent = person("折算优先"); equivalent.setEntryDate("2022-09-01"); equivalent.setEquivEntryDate("2008-09-01");
        Personnel fallback = person("非法折算兜底"); fallback.setEntryDate("2010-06-01"); fallback.setEquivEntryDate("2010-02-30");
        Personnel notMin = person("有效折算不是取两日期最小"); notMin.setEntryDate("2001-01-01"); notMin.setEquivEntryDate("2014-01-01");
        Personnel january = person("同年一月"); january.setEntryDate("2020-01");
        Personnel february = person("同年二月"); february.setEntryDate("2020-02-01");
        Personnel februaryDay = person("同月不同日"); februaryDay.setEntryDate("20200215");
        Personnel futureFallback = person("未来折算兜底"); futureFallback.setEquivEntryDate("9999-12-31"); futureFallback.setEntryDate("2021");
        Personnel today = person("今天有效"); today.setEntryDate(LocalDate.now().toString());
        Personnel future = person("未来无效"); future.setEntryDate("9999-12-31");
        Personnel invalid = person("非法无效"); invalid.setEntryDate("not-a-date");
        Personnel empty = person("空无效");
        List<Personnel> known = new ArrayList<Personnel>(Arrays.asList(equivalent, fallback, notMin, january, february, februaryDay, futureFallback, today));
        List<Personnel> input = Arrays.asList(future, februaryDay, invalid, today, notMin, futureFallback, equivalent, empty, february, fallback, january);
        if ("asc".equals(direction)) Collections.reverse(known);
        List<Personnel> expected = new ArrayList<Personnel>(known);
        expected.addAll(Arrays.asList(future, invalid, empty));
        try (XSSFWorkbook wb = export("serviceYears:" + direction, input)) {
            assertEquals(labels(expected), names(wb.getSheetAt(0), "营部(", input.size()));
        }
        assertEquals("2001-01-01", notMin.getEntryDate());
        assertEquals("2014-01-01", notMin.getEquivEntryDate());
    }

    @Test void partialAndCompactDatesShareStrictStartOfPeriodAndPreserveTies() throws Exception {
        String[] dates = {"2020-01-02", "2020", "2020-01", "2020-01-01", "20200101", "20200229", "2020-03-01"};
        List<Personnel> input = new ArrayList<Personnel>();
        for (String date : dates) { Personnel p = person(date); p.setPostDate(date); input.add(p); }
        try (XSSFWorkbook wb = export("postDate:asc", input)) {
            assertEquals(Arrays.asList("2020", "2020-01", "2020-01-01", "20200101", "2020-01-02", "20200229", "2020-03-01"),
                    names(wb.getSheetAt(0), "营部(", input.size()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2020-02-30", "2019-02-29", "2020-13", "20201301", "0000", "0000-01-01",
            "2020/01/01", "2020-1-1", "2020-01-01junk", "2020-01-01T00:00:00", "+2020-01-01", "2020.01.01"})
    void malformedDatesAreUnavailableAndContinueToSecondarySort(String invalidDate) throws Exception {
        Personnel invalid = person("王五"); invalid.setPostDate(invalidDate);
        Personnel empty = person("李四");
        Personnel valid = person("有效日期"); valid.setPostDate("2020-03-01");
        for (String direction : Arrays.asList("asc", "desc")) {
            try (XSSFWorkbook wb = export("postDate:" + direction + ",name:asc", Arrays.asList(invalid, empty, valid))) {
                assertEquals(Arrays.asList("有效日期", "李四", "王五"), names(wb.getSheetAt(0), "营部(", 3));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"asc", "desc"})
    void ageUsesExactBirthdayRatherThanYearOrText(String direction) throws Exception {
        Personnel older = person("年长"); older.setBirthday("1989-12-31");
        Personnel january = person("一月"); january.setBirthday("1990-01");
        Personnel later = person("二月"); later.setBirthday("19900201");
        Personnel future = person("未来生日"); future.setBirthday("9999");
        Personnel invalid = person("非法生日"); invalid.setBirthday("1990-02-30");
        List<Personnel> input = Arrays.asList(later, future, january, invalid, older);
        List<String> expected = "desc".equals(direction) ? Arrays.asList("年长", "一月", "二月", "未来生日", "非法生日")
                : Arrays.asList("二月", "一月", "年长", "未来生日", "非法生日");
        try (XSSFWorkbook wb = export("age:" + direction, input)) {
            assertEquals(expected, names(wb.getSheetAt(0), "营部(", input.size()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"asc", "desc"})
    void fixedMilitaryRankOrderIncludesOldNewAliasesButNotMixedJobGrades(String direction) throws Exception {
        String[][] highToLow = {{"上将"}, {"中将"}, {"少将"}, {"大校"}, {"上校"}, {"中校"}, {"少校"},
                {"上尉"}, {"中尉"}, {"少尉"}, {"一级军士长"}, {"二级军士长"}, {"三级军士长"},
                {"四级军士长", "一级上士"}, {"上士", "二级上士"}, {"中士"}, {"下士"}, {"上等兵"}, {"列兵"}};
        List<List<Personnel>> groups = new ArrayList<List<Personnel>>();
        for (String[] group : highToLow) {
            List<Personnel> members = new ArrayList<Personnel>();
            for (String rank : group) { Personnel p = person(rank); p.setRank(rank); members.add(p); }
            groups.add(members);
        }
        List<Personnel> unknown = new ArrayList<Personnel>();
        for (String value : Arrays.asList("正团职", "自定义军衔", "三级上士", "一级士官", "无", "", null)) {
            Personnel p = person("未知:" + value); p.setRank(value); unknown.add(p);
        }
        // Low to high, but keep the original order within alias groups.
        Collections.reverse(groups);
        List<Personnel> input = new ArrayList<Personnel>(unknown);
        for (List<Personnel> group : groups) input.addAll(group);
        if ("desc".equals(direction)) Collections.reverse(groups);
        List<Personnel> expected = new ArrayList<Personnel>();
        for (List<Personnel> group : groups) expected.addAll(group);
        expected.addAll(unknown);
        try (XSSFWorkbook wb = export("rank:" + direction, input)) {
            assertEquals(labels(expected), names(wb.getSheetAt(0), "营部(", input.size()));
        }
    }

    @Test void educationUsesKnownLevelsAndEquivalentSecondarySchoolTies() throws Exception {
        String[] lowToHigh = {"小学", "初中", "中专", "职高", "高中", "大专", "本科", "硕士", "博士"};
        List<Personnel> input = new ArrayList<Personnel>();
        Personnel ambiguous = person("含糊研究生"); ambiguous.setEducation("研究生"); input.add(ambiguous);
        for (String value : lowToHigh) { Personnel p = person(value); p.setEducation(value); input.add(p); }
        for (String direction : Arrays.asList("asc", "desc")) {
            try (XSSFWorkbook wb = export("education:" + direction, input)) {
                assertEquals("asc".equals(direction)
                                ? Arrays.asList("小学", "初中", "中专", "职高", "高中", "大专", "本科", "硕士", "博士", "含糊研究生")
                                : Arrays.asList("博士", "硕士", "本科", "大专", "中专", "职高", "高中", "初中", "小学", "含糊研究生"),
                        names(wb.getSheetAt(0), "营部(", input.size()));
            }
        }
    }

    static Stream<Arguments> fieldsAndDirections() {
        return Stream.of("serviceYears", "rank", "age", "name", "entryDate", "postDate", "category", "education", "post")
                .flatMap(field -> Stream.of(Arguments.of(field, "asc"), Arguments.of(field, "desc")));
    }

    private static void setKey(Personnel p, String field, String value) {
        switch (field) {
            case "serviceYears": case "entryDate": p.setEntryDate(value); break;
            case "rank": p.setRank(value); break;
            case "age": p.setBirthday(value); break;
            case "name": p.setName(value); break;
            case "postDate": p.setPostDate(value); break;
            case "category": p.setCategory(value); break;
            case "education": p.setEducation(value); break;
            case "post": p.setPost(value); break;
            default: throw new AssertionError(field);
        }
    }

    @ParameterizedTest
    @MethodSource("fieldsAndDirections")
    void everyFieldKeepsBothNullAndWhitespaceLastInEitherDirection(String field, String direction) throws Exception {
        Personnel a = person("甲"); Personnel b = person("乙"); Personnel empty = person("空值"); Personnel blank = person("空白");
        String aValue = "李四", bValue = "王五";
        if (Arrays.asList("serviceYears", "age", "entryDate", "postDate").contains(field)) {
            aValue = "2001-01-01"; bValue = "2002-01-01";
        } else if ("rank".equals(field)) { aValue = "列兵"; bValue = "少尉"; }
        else if ("education".equals(field)) { aValue = "小学"; bValue = "博士"; }
        setKey(a, field, aValue); setKey(b, field, bValue); setKey(empty, field, null); setKey(blank, field, " ");
        boolean dateInverted = "serviceYears".equals(field) || "age".equals(field);
        boolean aFirst = "asc".equals(direction) != dateInverted;
        List<String> expected = new ArrayList<String>(labels(aFirst ? Arrays.asList(a, b) : Arrays.asList(b, a)));
        expected.addAll(labels(Arrays.asList(empty, blank)));
        try (XSSFWorkbook wb = export(field + ":" + direction, Arrays.asList(empty, b, blank, a))) {
            assertEquals(expected, names(wb.getSheetAt(0), "营部(", 4));
        }
    }

    @Test void changingPrimaryPriorityChangesResultAndTiesKeepOriginalIdsAndSequence() throws Exception {
        Personnel youngHigh = person("青年高衔"); youngHigh.setEntryDate("2020-01-01"); youngHigh.setRank("上校");
        Personnel oldLow = person("老兵低衔"); oldLow.setEntryDate("2010-01-01"); oldLow.setRank("下士");
        Personnel sameFirst = person("王五"); sameFirst.setEntryDate("2010-01-01"); sameFirst.setRank("上校");
        Personnel sameSecond = person("李四"); sameSecond.setEntryDate("2010-01-01"); sameSecond.setRank("上校");
        List<Personnel> input = Arrays.asList(youngHigh, oldLow, sameFirst, sameSecond);
        try (XSSFWorkbook wb = export("serviceYears:desc,rank:desc", input)) {
            assertEquals(Arrays.asList("王五", "李四", "老兵低衔", "青年高衔"), names(wb.getSheetAt(0), "营部(", 4));
        }
        try (XSSFWorkbook wb = export("rank:desc,serviceYears:desc", input)) {
            assertEquals(Arrays.asList("王五", "李四", "青年高衔", "老兵低衔"), names(wb.getSheetAt(0), "营部(", 4));
        }
        try (XSSFWorkbook wb = export("serviceYears:desc,rank:desc,name:asc", input)) {
            assertEquals(Arrays.asList("李四", "王五", "老兵低衔", "青年高衔"), names(wb.getSheetAt(0), "营部(", 4));
        }
        assertEquals(Arrays.asList("青年高衔", "老兵低衔", "王五", "李四"), labels(input), "Do not sort the service query's input list");
    }

    @Test void fiveKeysAreAcceptedAndOnlyFifthBreaksTies() throws Exception {
        Personnel first = person("王五"); Personnel second = person("李四");
        for (Personnel p : Arrays.asList(first, second)) {
            p.setEntryDate("2010-01-01"); p.setRank("上士"); p.setBirthday("1990-01-01"); p.setEducation("本科");
        }
        try (XSSFWorkbook wb = export(" serviceYears:desc , rank:desc , age:desc , education:desc , name:asc ", Arrays.asList(first, second))) {
            assertEquals(Arrays.asList("李四", "王五"), names(wb.getSheetAt(0), "营部(", 2));
        }
    }

    @Test void twoUnavailableMilitaryKeysContinueToNextKeyAndFullTiesRemainStable() throws Exception {
        Personnel first = person("王五"); first.setEntryDate("9999"); first.setRank("正营职");
        Personnel second = person("李四"); second.setEntryDate("invalid"); second.setRank("自定义");
        Personnel duplicate = person("李四"); duplicate.setRank("无");
        List<Personnel> input = Arrays.asList(first, second, duplicate);
        try (XSSFWorkbook wb = export("serviceYears:desc,rank:asc,name:asc", input)) {
            assertEquals(Arrays.asList("李四", "李四", "王五"), names(wb.getSheetAt(0), "营部(", 3));
        }
        Comparator<Personnel> comparator = RollCallExcelService.sortComparator("serviceYears:desc,rank:asc,name:asc", LocalDate.of(2026, 10, 18));
        assertEquals(0, comparator.compare(second, duplicate), "Full ties never append ID or raw unknown rank/date text");
        List<Personnel> copy = new ArrayList<Personnel>(input); copy.sort(comparator);
        assertSame(second, copy.get(0)); assertSame(duplicate, copy.get(1));
    }

    @Test void chineseNamesCategoriesAndPostsUseChineseTextRatherThanUnicodeOrJobLevels() throws Exception {
        for (String field : Arrays.asList("name", "category", "post")) {
            List<Personnel> input = new ArrayList<Personnel>();
            for (String text : Arrays.asList("张三", "王五", "李四")) {
                Personnel p = person(text); setKey(p, field, text); input.add(p);
            }
            try (XSSFWorkbook wb = export(field + ":asc", input)) {
                assertEquals(Arrays.asList("李四", "王五", "张三"), names(wb.getSheetAt(0), "营部(", 3));
            }
            try (XSSFWorkbook wb = export(field + ":desc", input)) {
                assertEquals(Arrays.asList("张三", "王五", "李四"), names(wb.getSheetAt(0), "营部(", 3));
            }
        }
        Personnel commander = person("营长"); commander.setPost("营长");
        Personnel squadLeader = person("班长"); squadLeader.setPost("班长");
        try (XSSFWorkbook wb = export("post:asc", Arrays.asList(commander, squadLeader))) {
            assertEquals(Arrays.asList("班长", "营长"), names(wb.getSheetAt(0), "营部(", 2));
        }
    }

    @Test void blocksReasonsPrintSettingsStatisticsMarksAndReturnDatesStayUnchanged() throws Exception {
        RollCallExcelTest.metadata(jdbc, dept(10L, "一连", dept(11L, "一班"), dept(12L, "二班")), dept(20L, "二连"));
        Personnel b2 = person("二班王"); b2.setDepartmentId(12L);
        Personnel a2 = person("一班王"); a2.setDepartmentId(11L); a2.setPoliticalStatus("中共党员");
        Personnel other = person("二连李"); other.setDepartmentId(20L);
        Personnel b1 = person("二班李"); b1.setDepartmentId(12L);
        Personnel a1 = person("一班李"); a1.setDepartmentId(11L);
        Personnel leave2 = person("休假王"); leave2.setStatus("休假"); leave2.setPoliticalStatus("共青团员");
        Personnel duty2 = person("出差王"); duty2.setStatus("出差");
        Personnel leave1 = person("休假李"); leave1.setStatus("休假");
        Personnel duty1 = person("出差李"); duty1.setStatus("出差");
        List<Personnel> input = Arrays.asList(b2, a2, other, b1, a1, leave2, duty2, leave1, duty1);
        Map<String, Object> returnDate = new HashMap<String, Object>(); returnDate.put("personnel_id", leave2.getId()); returnDate.put("ed", "2099-12-31");
        doReturn(Collections.singletonList(returnDate)).when(jdbc).queryForList(contains("leave_record"));
        Map<String, Object> driver = new HashMap<String, Object>(); driver.put("personnel_id", a2.getId());
        doReturn(Collections.singletonList(driver)).when(jdbc).queryForList(contains("driver_license"));
        Map<String, String> baselineQuery = new HashMap<String, String>(); baselineQuery.put("marks", "party,league,driver");
        Map<String, String> sortedQuery = new HashMap<String, String>(baselineQuery); sortedQuery.put("sort", "name:asc");
        try (XSSFWorkbook original = export(baselineQuery, input); XSSFWorkbook sorted = export(sortedQuery, input)) {
            Sheet before = original.getSheetAt(0), after = sorted.getSheetAt(0);
            Cell unit = findCell(after, "一连("), first = findCell(after, "一班("), second = findCell(after, "二班("), otherUnit = findCell(after, "二连(");
            assertEquals("一连(在位4人)", unit.getStringCellValue());
            assertEquals("二连(在位1人)", otherUnit.getStringCellValue());
            assertEquals("一班(在位2人)", first.getStringCellValue());
            assertEquals("二班(在位2人)", second.getStringCellValue());
            assertEquals(unit.getRowIndex(), otherUnit.getRowIndex());
            assertEquals(first.getRowIndex(), second.getRowIndex());
            assertTrue(unit.getRowIndex() < first.getRowIndex());
            assertTrue(first.getColumnIndex() < second.getColumnIndex() && second.getColumnIndex() < otherUnit.getColumnIndex());
            assertEquals("A5:B5", RollCallExcelTest.headerRegion(after, unit).formatAsString());
            assertEquals("C5", RollCallExcelTest.headerRegion(after, otherUnit).formatAsString());
            assertEquals(Arrays.asList("一班李", "☭一班王⊛"), names(after, "一班(", 2));
            assertEquals(Arrays.asList("二班李", "二班王"), names(after, "二班(", 2));
            assertEquals(Collections.singletonList("二连李"), names(after, "二连(", 1));
            int leaveRow = findRow(after, "休假("); int dutyRow = findRow(after, "出差(");
            assertTrue(leaveRow < dutyRow, "Reason order must stay first-seen, not sorted by reason/name");
            assertEquals("休假李", cell(after, leaveRow, 1));
            assertEquals("✪休假王\n归队:2099年12月31日", cell(after, leaveRow, 2));
            assertEquals("出差李", cell(after, dutyRow, 1)); assertEquals("出差王", cell(after, dutyRow, 2));
            assertEquals(cell(before, findRow(before, "不在位人员"), 0), cell(after, findRow(after, "不在位人员"), 0));
            assertEquals(cell(before, before.getLastRowNum(), 0), cell(after, after.getLastRowNum(), 0));
            assertEquals("应到:9人    在位:5人    不在位:4人    实到:        人    点名人:", cell(after, after.getLastRowNum(), 0));
            assertEquals(original.getPrintArea(0), sorted.getPrintArea(0));
            assertEquals(before.getLastRowNum(), after.getLastRowNum());
            assertEquals(before.getNumMergedRegions(), after.getNumMergedRegions());
            for (int i = 0; i < before.getNumMergedRegions(); i++) {
                CellRangeAddress expected = before.getMergedRegion(i);
                assertEquals(expected.formatAsString(), after.getMergedRegion(i).formatAsString());
            }
            assertTrue(after.getPrintSetup().getLandscape()); assertEquals(9, after.getPrintSetup().getPaperSize());
            assertTrue(after.getFitToPage()); assertTrue(after.getHorizontallyCenter());
            assertEquals(1, after.getPrintSetup().getFitWidth()); assertEquals(1, after.getPrintSetup().getFitHeight());
            for (short margin = 0; margin < 6; margin++) assertEquals(before.getMargin(margin), after.getMargin(margin));
            for (int col = 0; col < 6; col++) assertEquals(before.getColumnWidth(col), after.getColumnWidth(col));
            for (Row row : before) {
                assertEquals(row.getHeight(), after.getRow(row.getRowNum()).getHeight());
                for (Cell c : row) assertEquals(c.getCellStyle().getIndex(), after.getRow(row.getRowNum()).getCell(c.getColumnIndex()).getCellStyle().getIndex());
            }
        }
    }

    @Test void absentNamesSortAcrossWrappedRowsButNeverAcrossReasons() throws Exception {
        List<Personnel> input = new ArrayList<Personnel>();
        for (String name : Arrays.asList("07", "06", "05", "04", "03", "02", "01")) {
            Personnel p = person(name); p.setStatus("休假"); input.add(p);
        }
        Personnel other = person("00"); other.setStatus("培训"); input.add(other);
        try (XSSFWorkbook wb = export("name:asc", input)) {
            Sheet sheet = wb.getSheetAt(0); int at = findRow(sheet, "休假(");
            for (int i = 0; i < 7; i++) assertEquals("0" + (i + 1), cell(sheet, at + i / 5, 1 + i % 5));
            assertEquals("培训(1人)", cell(sheet, at + 2, 0)); assertEquals("00", cell(sheet, at + 2, 1));
        }
    }

    @Test void sortingStillFiltersForeignAndUnassignedPeopleAndPreservesScopedSql() throws Exception {
        Personnel visibleZ = person("张三"); Personnel visibleL = person("李四");
        Personnel foreign = person("秘密外域"); foreign.setDepartmentId(2L); foreign.setPoliticalStatus("中共党员");
        Personnel unassigned = person("秘密无单位"); unassigned.setDepartmentId(null);
        List<Personnel> input = Arrays.asList(visibleZ, foreign, unassigned, visibleL);
        DataScope.set(new DataScope.Context(10L, "sub", false, Collections.singleton(1L)));
        Map<String, String> q = new HashMap<String, String>(); q.put("sort", "name:asc"); q.put("marks", "driver");
        try (XSSFWorkbook wb = export(q, input)) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(Arrays.asList("李四", "张三"), names(sheet, "营部(", 2));
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:2人"));
            for (Row row : sheet) for (Cell cell : row) assertFalse(cell.toString().contains("秘密"));
        }
        verify(jdbc).queryForList(contains("WHERE " + DataScope.personSql("personnel_id") + " AND"));
        verify(jdbc).queryForList(contains("AND " + DataScope.personSql("personnel_id")));
        DataScope.set(new DataScope.Context(10L, "empty", false, Collections.emptySet()));
        try (XSSFWorkbook wb = export("name:asc", input)) {
            Sheet sheet = wb.getSheetAt(0);
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:0人"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"id:asc", "birthday:asc", "Name:asc", "name", "name:", ":asc", "name:up", "name:ASC", "name: desc:asc",
            "name:asc,name:desc", "name:asc, name :asc", "name:asc,", ",name:asc", ",", "name:asc,,rank:desc",
            "name:asc，rank:desc", "name:asc;DROP TABLE personnel", "name desc:asc",
            "name:asc,rank:desc,age:desc,post:asc,entryDate:asc,education:desc"})
    void invalidSortIsRejectedBeforeAnyDataAccess(String invalid) {
        assertThrows(IllegalArgumentException.class, () -> service.build(Collections.singletonMap("sort", invalid)));
        verifyNoInteractions(people, departments, jdbc);
    }

    @Test void comparatorUsesBuildLocalTodayAndHasNoImplicitDefault() {
        LocalDate today = LocalDate.of(2026, 10, 18);
        assertNull(RollCallExcelService.sortComparator(null, today));
        assertNull(RollCallExcelService.sortComparator("  ", today));
        Personnel atBoundary = person("当天"); atBoundary.setEntryDate("2026-10-18");
        Personnel future = person("未来"); future.setEntryDate("2026-10-19");
        Personnel absent = person("空");
        for (String direction : Arrays.asList("asc", "desc")) {
            Comparator<Personnel> order = RollCallExcelService.sortComparator("serviceYears:" + direction, today);
            assertTrue(order.compare(atBoundary, future) < 0);
            assertEquals(0, order.compare(future, absent));
        }
    }
}
