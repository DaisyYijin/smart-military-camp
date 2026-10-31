package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.dao.FitnessDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 宽行(一人一行)导入/导出/汇总的隔离验证:真实POI+临时SQLite,不启动应用。 */
class FitnessWideExcelTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    FitnessWideExcelService wide;
    ModuleExcelService service;
    FitnessService fitnessService;

    static final String[] WIDE_HEADERS = {"姓名", "身份证号码", "考核日期", "仰卧起坐", "引体向上", "3000米跑"};

    @BeforeEach void setup() {
        DataScope.clear();
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY, name TEXT, parent_id INTEGER DEFAULT 0, sort INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, id_card TEXT, department_id INTEGER, birthday TEXT, gender TEXT, height REAL)");
        jdbc.execute("CREATE TABLE fitness_record(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, item TEXT, score TEXT, points REAL DEFAULT 0, grade TEXT DEFAULT '', test_date TEXT, tester TEXT DEFAULT '', remark TEXT DEFAULT '', created_at TEXT DEFAULT '', updated_at TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE fitness_matrix(age_group TEXT, score REAL, value REAL, unit TEXT, item TEXT)");
        jdbc.execute("CREATE TABLE sys_setting(skey TEXT PRIMARY KEY, svalue TEXT)");
        jdbc.execute("CREATE TABLE sys_dict(id INTEGER PRIMARY KEY AUTOINCREMENT, dict_type TEXT, dict_label TEXT, sort INTEGER DEFAULT 0, enabled INTEGER DEFAULT 1)");
        jdbc.update("INSERT INTO department(id,name) VALUES(1,'一连'),(2,'二连')");
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id,birthday,gender,height) VALUES"
                + "(1,'周明','110105199001010011',1,'1990-01-01','男',175),"
                + "(2,'李宁','110105199001010029',1,'1990-01-01','男',180),"
                + "(3,'陈晓','110105199001010037',2,'1990-01-01','女',165)");
        jdbc.update("INSERT INTO fitness_matrix VALUES('18-60',100,60,'count','仰卧起坐'),('18-60',60,40,'count','仰卧起坐')");
        jdbc.update("INSERT INTO fitness_matrix VALUES('18-60',100,12,'count','引体向上'),('18-60',60,8,'count','引体向上')");
        jdbc.update("INSERT INTO sys_setting VALUES('fitness_grade_rule','[{\"min\":90,\"label\":\"优秀\",\"op\":\">=\"},{\"min\":0,\"label\":\"不及格\",\"op\":\">=\"}]')");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,sort,enabled) VALUES"
                + "('fitness_item','仰卧起坐',1,1),('fitness_item','引体向上',2,1),('fitness_item','3000米跑',3,1),('fitness_item','停用项目',4,0)");
        fitnessService = new FitnessService(new FitnessDao(jdbc), new PersonnelDao(jdbc), jdbc, new FitnessScoringService(jdbc));
        wide = new FitnessWideExcelService(jdbc, fitnessService);
        service = new ModuleExcelService(jdbc, null, null);
        service.setWideExcel(wide);
    }

    @AfterEach void clear() { DataScope.clear(); }

    private static java.io.InputStream stream(String[][] rows) throws Exception {
        return new ByteArrayInputStream(bytesOf(rows));
    }

    private static byte[] bytesOf(String[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void assertCounts(Map<String, Object> result, int added, int updated, int failed) {
        assertEquals(added, ((Number) result.get("added")).intValue(), String.valueOf(result));
        assertEquals(updated, ((Number) result.get("updated")).intValue(), String.valueOf(result));
        assertEquals(failed, ((Number) result.get("failed")).intValue(), String.valueOf(result));
    }

    @Test void templateUsesDynamicEnabledItemsInDictionaryOrder() throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(wide.template()))) {
            Row head = wb.getSheetAt(0).getRow(0);
            StringBuilder headers = new StringBuilder();
            for (int c = 0; c < head.getLastCellNum(); c++) headers.append(head.getCell(c) == null ? "" : head.getCell(c).toString()).append('|');
            assertEquals("姓名|身份证号码|考核日期|仰卧起坐|引体向上|3000米跑|", headers.toString());
            assertTrue(wb.getNumberOfSheets() >= 2, "应有填写说明页");
            Sheet notes = wb.getSheetAt(1);
            String text = String.valueOf(notes.getRow(0).getCell(0));
            assertTrue(text.contains("一人一行"), text);
        }
    }

    @Test void wideImportCreatesOneRecordPerFilledItemAndSkipsBlanks() throws Exception {
        assertCounts(service.importExcel("fitness", stream(new String[][]{
                WIDE_HEADERS,
                {"周明", "110105199001010011", "2026-04-10", "60", "12", ""},
                {"李宁", "110105199001010029", "2026-04-10", "", "9", ""}})), 3, 0, 0);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.name AS name, f.item AS item, f.score AS score, f.points AS points, f.grade AS grade"
                        + " FROM fitness_record f JOIN personnel p ON p.id=f.personnel_id ORDER BY p.name, f.item");
        assertEquals(3, rows.size());
        assertEquals("仰卧起坐", rows.get(0).get("item"));
        assertEquals("60", rows.get(0).get("score"));
        assertEquals(100.0, ((Number) rows.get(0).get("points")).doubleValue(), 0.001);
        assertEquals("优秀", rows.get(0).get("grade"));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM fitness_record f JOIN personnel p ON p.id=f.personnel_id WHERE p.name='周明' AND f.item='3000米跑'", Integer.class) == 0,
                "空白项目不应生成记录");
    }

    @Test void reimportOverwritesSamePersonItemDateAndRecalculates() throws Exception {
        service.importExcel("fitness", stream(new String[][]{WIDE_HEADERS, {"周明", "110105199001010011", "2026-04-10", "60", "12", ""}}));
        assertCounts(service.importExcel("fitness", stream(new String[][]{WIDE_HEADERS, {"周明", "110105199001010011", "2026-04-10", "45", "5", ""}})), 0, 2, 0);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT item,score,points FROM fitness_record ORDER BY item");
        assertEquals(2, rows.size());
        assertEquals("45", rows.get(0).get("score"));
        assertEquals(60.0, ((Number) rows.get(0).get("points")).doubleValue(), 0.001);
        assertEquals("5", rows.get(1).get("score"));
        assertTrue(((Number) rows.get(1).get("points")).doubleValue() < 60, "低于最低标准的引体向上不应达到及格分");
        assertTrue(rows.get(1).get("grade") == null || "不及格".equals(rows.get(1).get("grade")), String.valueOf(rows.get(1)));
    }

    @Test void rowLevelFailureReportsRowAndItemAndKeepsGoodRows() throws Exception {
        Map<String, Object> result = service.importExcel("fitness", stream(new String[][]{
                WIDE_HEADERS,
                {"周明", "110105199001010011", "2026-04-10", "60", "", ""},
                {"李宁", "110105199001010029", "2026-04-10", "错误成绩", "", ""}}));
        assertCounts(result, 1, 0, 1);
        List<?> errors = (List<?>) result.get("errors");
        assertEquals(1, errors.size());
        assertTrue(String.valueOf(errors.get(0)).contains("第3行"), String.valueOf(errors.get(0)));
        assertTrue(String.valueOf(errors.get(0)).contains("仰卧起坐"), String.valueOf(errors.get(0)));
    }

    @Test void unknownColumnHeaderRejectsWholeFileBeforeAnyWrite() throws Exception {
        Map<String, Object> result = service.importExcel("fitness", stream(new String[][]{
                {"姓名", "身份证号码", "考核日期", "仰卧起坐", "不存在的项目"}, {"周明", "110105199001010011", "2026-04-10", "60", "1"}}));
        assertEquals(0, ((Number) result.get("added")).intValue(), String.valueOf(result));
        assertTrue(String.valueOf(result.get("error")).contains("不支持的项目列"), String.valueOf(result));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM fitness_record", Integer.class));
    }

    @Test void legacyOneItemPerRowFormatIsAutoDetectedAndAlsoUpserts() throws Exception {
        assertCounts(service.importExcel("fitness", stream(new String[][]{
                {"姓名", "考核日期", "考核项目", "成绩"},
                {"周明", "2026-04-10", "仰卧起坐", "60"}})), 1, 0, 0);
        assertCounts(service.importExcel("fitness", stream(new String[][]{
                {"姓名", "考核日期", "考核项目", "成绩"},
                {"周明", "2026-04-10", "仰卧起坐", "45"},
                {"周明", "2026-05-20", "引体向上", "10"}})), 1, 1, 0);
        assertEquals("45", jdbc.queryForObject("SELECT score FROM fitness_record WHERE item='仰卧起坐'", String.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM fitness_record", Integer.class));
    }

    @Test void exportWideGroupsByPersonAndDateWithScorePointsGrade() throws Exception {
        service.importExcel("fitness", stream(new String[][]{WIDE_HEADERS,
                {"周明", "110105199001010011", "2026-04-10", "60", "12", ""},
                {"李宁", "110105199001010029", "2026-05-01", "40", "", ""}}));
        FitnessWideExcelService.Filters filters = new FitnessWideExcelService.Filters();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(wide.exportWide(null, filters)))) {
            DataFormatter fmt = new DataFormatter();
            StringBuilder text = new StringBuilder();
            for (Row row : wb.getSheetAt(0)) for (Cell cell : row) text.append(fmt.formatCellValue(cell)).append('\n');
            String all = text.toString();
            assertTrue(all.contains("60（100分/优秀）"), all);
            assertTrue(all.contains("40（60分/不及格）"), all);
            assertTrue(all.contains("2026-04-10"), all);
            assertEquals(3 /* 表头+两组 */, wb.getSheetAt(0).getPhysicalNumberOfRows());
        }
        Long zhou = jdbc.queryForObject("SELECT f.id FROM fitness_record f JOIN personnel p ON p.id=f.personnel_id WHERE p.name='周明' AND f.item='引体向上'", Long.class);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(wide.exportWide(Arrays.asList(zhou), filters)))) {
            DataFormatter fmt = new DataFormatter();
            StringBuilder text = new StringBuilder();
            for (Row row : wb.getSheetAt(0)) for (Cell cell : row) text.append(fmt.formatCellValue(cell)).append('\n');
            assertTrue(text.toString().contains("12（100分/优秀）"));
            assertFalse(text.toString().contains("40（"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test void summaryGroupsDistinctPersonAndDateWithOrderedItemsAndPaging() throws Exception {
        service.importExcel("fitness", stream(new String[][]{WIDE_HEADERS,
                {"周明", "110105199001010011", "2026-04-10", "60", "12", ""},
                {"周明", "110105199001010011", "2026-05-01", "", "9", ""},
                {"李宁", "110105199001010029", "2026-04-10", "50", "", ""}}));
        Map<String, Object> summary = fitnessService.summary(null, null, null, null, null, null, null, null, null, null, 1, 20);
        assertEquals(3L, ((Number) summary.get("total")).longValue());
        assertEquals(Arrays.asList("仰卧起坐", "引体向上", "3000米跑"), (List<String>) summary.get("items"));
        List<Map<String, Object>> list = (List<Map<String, Object>>) summary.get("list");
        assertEquals(3, list.size());
        Map<String, Object> aprilZhou = list.stream()
                .filter(row -> "周明".equals(row.get("personnelName")) && "2026-04-10".equals(row.get("testDate"))).findFirst().get();
        Map<String, Map<String, Object>> items = (Map<String, Map<String, Object>>) aprilZhou.get("items");
        assertEquals(2, items.size());
        assertEquals("60", items.get("仰卧起坐").get("score"));
        assertEquals("优秀", items.get("仰卧起坐").get("grade"));
        assertFalse(items.containsKey("3000米跑"));
        Map<String, Object> paged = fitnessService.summary(null, null, null, null, null, null, null, null, null, null, 1, 2);
        assertEquals(3L, ((Number) paged.get("total")).longValue());
        assertEquals(2, ((List<?>) paged.get("list")).size());
    }

    @Test void childScopeCannotSeeForeignPeopleInSummaryOrWideExport() throws Exception {
        service.importExcel("fitness", stream(new String[][]{WIDE_HEADERS,
                {"周明", "110105199001010011", "2026-04-10", "60", "", ""},
                {"陈晓", "110105199001010037", "2026-04-10", "40", "", ""}}));
        DataScope.set(new DataScope.Context(9L, "child", false, new HashSet<Long>(Collections.singletonList(1L))));
        Map<String, Object> summary = fitnessService.summary(null, null, null, null, null, null, null, null, null, null, 1, 20);
        assertEquals(1L, ((Number) summary.get("total")).longValue());
        List<?> list = (List<?>) summary.get("list");
        assertEquals("周明", ((Map<?, ?>) list.get(0)).get("personnelName"));
        FitnessWideExcelService.Filters filters = new FitnessWideExcelService.Filters();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(wide.exportWide(null, filters)))) {
            DataFormatter fmt = new DataFormatter();
            StringBuilder text = new StringBuilder();
            for (Row row : wb.getSheetAt(0)) for (Cell cell : row) text.append(fmt.formatCellValue(cell)).append('\n');
            assertFalse(text.toString().contains("陈晓"));
            assertTrue(text.toString().contains("周明"));
        }
    }
}
