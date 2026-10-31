package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.config.GlobalExceptionHandler;
import com.szjx.personnel.server.controller.DoubleExcellenceExcelController;
import com.szjx.personnel.server.controller.ModuleExcelController;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** This class owns its SQLite schema and never starts an application or touches shared fixtures/real data. */
class DoubleExcellenceExcelTest {
    private static final String CARD = "110105199001011234";
    private static final String OTHER_CARD = "110105199001011235";
    private static final String EXTERNAL_CARD = "110105199001011236";
    private static final String[] HEADERS = {"年份", "季度", "姓名", "身份证号码", "评定", "评比日期"};
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private DoubleExcellenceService core;
    private DoubleExcellenceExcelService excel;

    @BeforeEach void setup() {
        DataScope.clear();
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.sqlite.JDBC");
        source.setUrl("jdbc:sqlite:" + temp.resolve("double-excellence-excel.db").toAbsolutePath());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT,parent_id INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY,name TEXT,id_card TEXT,department_id INTEGER,birthday TEXT)");
        jdbc.execute("CREATE TABLE double_excellence_record(id INTEGER PRIMARY KEY AUTOINCREMENT,personnel_id INTEGER NOT NULL,year INTEGER NOT NULL,quarter INTEGER NOT NULL CHECK(quarter BETWEEN 1 AND 4),rating TEXT NOT NULL,rating_date TEXT NOT NULL,created_at TEXT DEFAULT CURRENT_TIMESTAMP,updated_at TEXT DEFAULT CURRENT_TIMESTAMP,UNIQUE(personnel_id,year,quarter))");
        // 导入校验与模板下拉读数据字典 de_rating(与生产一致,空字典时服务回退默认三档)
        jdbc.execute("CREATE TABLE sys_dict(id INTEGER PRIMARY KEY AUTOINCREMENT,dict_type TEXT NOT NULL,dict_label TEXT NOT NULL,sort INTEGER DEFAULT 0,enabled INTEGER DEFAULT 1,parent_id INTEGER DEFAULT 0)");
        jdbc.update("INSERT INTO department(id,name,parent_id) VALUES(1,'一单位',0),(2,'子单位',1),(3,'外单位',0)");
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id,birthday) VALUES(1,'同名人员',?,1,'1990-01-01'),(2,'同名人员',?,2,'1990-01-01'),(3,'外单位人员',?,3,'1990-01-01')", CARD, OTHER_CARD, EXTERNAL_CARD);
        core = new DoubleExcellenceService(jdbc);
        excel = new DoubleExcellenceExcelService(core, jdbc);
    }

    @AfterEach void clearScope() { DataScope.clear(); }

    @Test void templateHasExactlySixOrderedHeadersAndNoPersonnelExamples() throws Exception {
        try (Workbook workbook = read(excel.template())) {
            Sheet sheet = workbook.getSheetAt(0);
            assertHeaders(sheet);
            assertEquals(1, sheet.getLastRowNum());
            for (Cell cell : sheet.getRow(1)) assertEquals(CellType.BLANK, cell.getCellType());
            assertEquals(3, sheet.getDataValidations().size());
            assertEquals("@", sheet.getColumnStyle(3).getDataFormatString());
            assertTrue(workbook.getSheet("填写说明").getRow(0).getCell(0).getStringCellValue().contains("更新"));
            assertFalse(workbook.getSheet("填写说明").getRow(0).getCell(0).getStringCellValue().contains(CARD));
        }
        counts(importBytes(excel.template()), 0, 0, 0);
    }

    private enum DateEncoding {
        COMPACT_TEXT, ISO_TEXT, DOT_TEXT, SLASH_TEXT, CHINESE_TEXT,
        COMPACT_NUMBER, STYLED_COMPACT_NUMBER, NATIVE_DATE, DATE_FORMULA, TEXT_FORMULA, NUMBER_FORMULA;
        void write(Cell cell) {
            switch (this) {
                case COMPACT_TEXT: cell.setCellValue("20160901"); break;
                case ISO_TEXT: cell.setCellValue("2016-09-01"); break;
                case DOT_TEXT: cell.setCellValue("2016.9.1"); break;
                case SLASH_TEXT: cell.setCellValue("2016/9/1"); break;
                case CHINESE_TEXT: cell.setCellValue("2016年9月1日"); break;
                case COMPACT_NUMBER: cell.setCellValue(20160901d); break;
                case STYLED_COMPACT_NUMBER: cell.setCellValue(20160901d); dateStyle(cell); break;
                case NATIVE_DATE: cell.setCellValue(LocalDateTime.of(2016, 9, 1, 0, 0)); dateStyle(cell); break;
                case DATE_FORMULA: cell.setCellFormula("DATE(2016,9,1)"); dateStyle(cell); break;
                case TEXT_FORMULA: cell.setCellFormula("\"2016年9月1日\""); break;
                case NUMBER_FORMULA: cell.setCellFormula("20160000+901"); break;
                default: throw new AssertionError(this);
            }
        }
    }

    @ParameterizedTest @EnumSource(DateEncoding.class)
    void everySupportedDateEncodingUsesSharedReaderAndPersistsIso(DateEncoding encoding) throws Exception {
        try (Workbook workbook = book(row("2026", "Q1", "同名人员", CARD, "好", "ignored"))) {
            encoding.write(workbook.getSheetAt(0).getRow(1).getCell(5));
            counts(importBytes(bytes(workbook)), 1, 0, 0);
        }
        assertEquals("2016-09-01", jdbc.queryForObject("SELECT rating_date FROM double_excellence_record", String.class));
        // A supplemental rating date is not required to fall inside the assessment year/quarter.
        assertEquals(2026, jdbc.queryForObject("SELECT year FROM double_excellence_record", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT quarter FROM double_excellence_record", Integer.class));
    }

    @Test void native1904AndFormulaBoundariesRemainThoseOfExcelDates() throws Exception {
        try (XSSFWorkbook workbook = book(valid("1"), valid("2"), valid("3"), valid("4"))) {
            if (!workbook.getCTWorkbook().isSetWorkbookPr()) workbook.getCTWorkbook().addNewWorkbookPr();
            workbook.getCTWorkbook().getWorkbookPr().setDate1904(true);
            Sheet sheet = workbook.getSheetAt(0);
            sheet.getRow(1).getCell(5).setCellValue(0d);
            dateStyle(sheet.getRow(1).getCell(5));
            sheet.getRow(2).getCell(5).setCellFormula("DATE(2016,9,1)");
            dateStyle(sheet.getRow(2).getCell(5));
            sheet.getRow(3).getCell(5).setCellFormula("\"2016-09-01\"");
            sheet.getRow(4).getCell(5).setCellFormula("1/0");
            Map<String, Object> result = importBytes(bytes(workbook));
            counts(result, 2, 0, 2);
            assertTrue(result.get("errors").toString().contains("第3行"));
            assertTrue(result.get("errors").toString().contains("第5行"));
        }
        assertEquals("1904-01-01", jdbc.queryForObject("SELECT rating_date FROM double_excellence_record WHERE quarter=1", String.class));
        assertEquals("2016-09-01", jdbc.queryForObject("SELECT rating_date FROM double_excellence_record WHERE quarter=3", String.class));
    }

    @Test void invalidDateRowsContinueWithoutGuessingPlainSerialsOrImpossibleLeapDays() throws Exception {
        try (Workbook workbook = book(valid("1"), valid("2"), valid("3"), valid("4"), valid("4"))) {
            Sheet sheet = workbook.getSheetAt(0);
            sheet.getRow(2).getCell(5).setCellValue("20260230");
            sheet.getRow(3).getCell(5).setCellValue(60d);
            dateStyle(sheet.getRow(3).getCell(5));
            sheet.getRow(4).getCell(5).setCellValue(45000d);
            counts(importBytes(bytes(workbook)), 2, 0, 3);
        }
        assertEquals(2, recordCount());
    }

    @Test void legacyXlsIsReadByWorkbookFactory() throws Exception {
        try (Workbook workbook = new HSSFWorkbook()) {
            writeRows(workbook.createSheet("双争评比"), valid("1"));
            counts(importBytes(bytes(workbook)), 1, 0, 0);
        }
    }

    @Test void duplicateKeysAndReimportUpdateInsteadOfAppending() throws Exception {
        byte[] uploaded;
        try (Workbook workbook = book(valid("一季度"), row("2026", "Q1", "同名人员", CARD, "一般", "2026-10-18"))) {
            uploaded = bytes(workbook);
        }
        Map<String, Object> first = importBytes(uploaded);
        counts(first, 1, 1, 0);
        assertTrue(first.get("rule").toString().contains("更新"));
        counts(importBytes(uploaded), 0, 2, 0);
        assertEquals(1, recordCount());
        assertEquals("一般", jdbc.queryForObject("SELECT rating FROM double_excellence_record", String.class));
        assertEquals("2026-10-18", jdbc.queryForObject("SELECT rating_date FROM double_excellence_record", String.class));
    }

    @Test void invalidRowsRollbackButLaterRowsContinueAndNeverOverwriteGoodValues() throws Exception {
        try (Workbook workbook = book(valid("1"), row("2026", "1", "错误姓名", CARD, "一般", "2026-09-01"),
                row("2026", "2", "同名人员", CARD, "优秀", "2026-09-01"), valid("3"))) {
            Map<String, Object> result = importBytes(bytes(workbook));
            counts(result, 2, 0, 2);
            assertTrue(result.get("errors").toString().contains("第3行"));
            assertTrue(result.get("errors").toString().contains("第4行"));
        }
        assertEquals("好", jdbc.queryForObject("SELECT rating FROM double_excellence_record WHERE quarter=1", String.class));
    }

    @Test void rowTransactionsRollbackEvenAWriteFollowedByCoreFailure() throws Exception {
        DoubleExcellenceService failing = new DoubleExcellenceService(jdbc) {
            @Override public Map<String, Object> importRecord(Map<String, String> value) {
                Map<String, Object> saved = super.importRecord(value);
                if ("2".equals(value.get("quarter"))) throw new IllegalArgumentException("模拟写入后校验失败");
                return saved;
            }
        };
        excel = new DoubleExcellenceExcelService(failing, jdbc);
        try (Workbook workbook = book(valid("1"), valid("2"), valid("3"))) {
            counts(importBytes(bytes(workbook)), 2, 0, 1);
        }
        assertEquals(2, recordCount());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM double_excellence_record WHERE quarter=2", Integer.class));
    }

    @Test void missingOrDuplicateRequiredHeadersRejectBeforeAnyWrites() throws Exception {
        for (String invalid : Arrays.asList("缺少评定", "姓名")) {
            try (Workbook workbook = book(valid("1"))) {
                workbook.getSheetAt(0).getRow(0).getCell(4).setCellValue(invalid);
                assertThrows(IllegalArgumentException.class, () -> importBytes(bytes(workbook)));
                assertEquals(0, recordCount());
            }
        }
    }

    @Test void dimensionsAreValidatedAcrossWholeWorkbookBeforeAnyWrites() throws Exception {
        try (Workbook workbook = book(valid("1"))) {
            workbook.createSheet("隐藏多余列").createRow(0).createCell(DoubleExcellenceExcelService.MAX_COLUMNS).setCellValue("超限");
            assertThrows(IllegalArgumentException.class, () -> importBytes(bytes(workbook)));
        }
        try (Workbook workbook = book(valid("1"))) {
            workbook.getSheetAt(0).createRow(DoubleExcellenceExcelService.MAX_ROWS + 1).createCell(0).setCellValue("超限");
            assertThrows(IllegalArgumentException.class, () -> importBytes(bytes(workbook)));
        }
        assertEquals(0, recordCount());
    }

    @ParameterizedTest @ValueSource(strings = {"1", "2", "3", "4", "Q1", "q2", "一季度", "第二季度", "三季度", "第四季度", "第1季度", "4季度"})
    void quarterCommonSpellingsAreCanonicalAndRoundTrip(String quarter) throws Exception {
        try (Workbook workbook = book(valid(quarter))) {
            counts(importBytes(bytes(workbook)), 1, 0, 0);
        }
        int expected = Integer.parseInt(DoubleExcellenceExcelService.parseQuarter(quarter));
        assertEquals(expected, jdbc.queryForObject("SELECT quarter FROM double_excellence_record", Integer.class));
        byte[] exported = excel.export(null, Collections.emptyMap());
        try (Workbook workbook = read(exported)) {
            assertEquals("第" + expected + "季度", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
        }
        counts(importBytes(exported), 0, 1, 0);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "5", "1.5", "1.0", "Q0", "Q5", "第五季度", "2026-01-01", "", "第一季"})
    void invalidQuarterFailsOnlyItsRow(String quarter) throws Exception {
        try (Workbook workbook = book(valid(quarter), valid("2"))) {
            counts(importBytes(bytes(workbook)), 1, 0, 1);
        }
        assertEquals(2, jdbc.queryForObject("SELECT quarter FROM double_excellence_record", Integer.class));
    }

    @Test void yearAndQuarterAreIntegersNotDateColumns() throws Exception {
        try (Workbook workbook = book(valid("1"), valid("2"), valid("3"), valid("4"), valid("4"))) {
            Sheet sheet = workbook.getSheetAt(0);
            sheet.getRow(1).getCell(0).setCellValue(2026d);
            dateStyle(sheet.getRow(1).getCell(0));
            sheet.getRow(1).getCell(1).setCellValue(1d);
            sheet.getRow(2).getCell(0).setCellValue("1899");
            sheet.getRow(3).getCell(0).setCellValue(10000d);
            sheet.getRow(4).getCell(0).setCellValue(2026.5d);
            sheet.getRow(5).getCell(1).setCellValue(1.5d);
            counts(importBytes(bytes(workbook)), 1, 0, 4);
        }
        assertEquals(2026, jdbc.queryForObject("SELECT year FROM double_excellence_record", Integer.class));
    }

    @Test void exactCardDisambiguatesNamesAndMismatchUnknownAndAmbiguousCardsDoNotWrite() throws Exception {
        try (Workbook workbook = book(valid("1"), row("2026", "1", "同名人员", OTHER_CARD, "中", "2026-09-01"),
                row("2026", "2", "不匹配姓名", CARD, "一般", "2026-09-01"),
                row("2026", "3", "同名人员", "000000000000000000", "好", "2026-09-01"))) {
            counts(importBytes(bytes(workbook)), 2, 0, 2);
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM double_excellence_record WHERE personnel_id=2", Integer.class));
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(4,'同名人员',?,1)", CARD);
        try (Workbook workbook = book(valid("4"))) {
            counts(importBytes(bytes(workbook)), 0, 0, 1);
        }
        assertEquals(2, recordCount());
    }

    @Test void longIdentitiesKeepExactTextAndNumericOrScientificIdentitiesFail() throws Exception {
        String longCard = "000012345678901234567890123456";
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(4,'文本人员',?,1)", longCard);
        try (Workbook workbook = book(row("2026", "1", "文本人员", longCard, "好", "2026-09-01"), valid("2"), valid("3"))) {
            workbook.getSheetAt(0).getRow(2).getCell(3).setCellValue(110105199001011234d);
            workbook.getSheetAt(0).getRow(3).getCell(3).setCellValue("1.10105199001011234E17");
            counts(importBytes(bytes(workbook)), 1, 0, 2);
        }
        byte[] exported = excel.export(null, Collections.emptyMap());
        try (Workbook workbook = read(exported)) {
            Cell cell = workbook.getSheetAt(0).getRow(1).getCell(3);
            assertEquals(CellType.STRING, cell.getCellType());
            assertEquals(longCard, cell.getStringCellValue());
        }
        counts(importBytes(exported), 0, 1, 0);
    }

    @Test void restrictedImportFailsUnauthorizedRowsWithoutChangingExistingExternalRecord() throws Exception {
        insert(103, 3, 2026, 1, "好", "2026-09-01");
        scoped(1L);
        try (Workbook workbook = book(row("2026", "1", "外单位人员", EXTERNAL_CARD, "一般", "2026-10-18"), valid("1"))) {
            Map<String, Object> result = importBytes(bytes(workbook));
            counts(result, 1, 0, 1);
            assertTrue(result.get("errors").toString().contains("第2行"));
            assertFalse(result.get("errors").toString().contains(EXTERNAL_CARD));
        }
        assertEquals("好", jdbc.queryForObject("SELECT rating FROM double_excellence_record WHERE id=103", String.class));
        assertEquals(2, recordCount());
    }

    @Test void unselectedExportAppliesAllCurrentFiltersAndIgnoresUiPaging() throws Exception {
        insert(101, 1, 2026, 1, "好", "2026-09-01");
        insert(102, 1, 2026, 2, "中", "2026-10-01");
        insert(103, 2, 2026, 1, "好", "2026-09-15");
        insert(104, 3, 2026, 1, "好", "2026-09-15");
        insert(105, 1, 2025, 1, "好", "2025-09-01");
        Map<String, String> filter = values("year", "2026", "quarter", "Q1", "personnelName", "同名", "idCard", "11010519900101",
                "departmentId", "1", "rating", "好", "ratingDateStart", "2026-09-01", "ratingDateEnd", "2026-09-30", "page", "99", "size", "1");
        try (Workbook workbook = read(excel.export(null, filter))) {
            assertHeaders(workbook.getSheetAt(0));
            assertEquals(3, workbook.getSheetAt(0).getPhysicalNumberOfRows());
            List<String> cards = Arrays.asList(workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue(), workbook.getSheetAt(0).getRow(2).getCell(3).getStringCellValue());
            assertTrue(cards.contains(CARD));
            assertTrue(cards.contains(OTHER_CARD));
            assertFalse(cards.contains(EXTERNAL_CARD));
        }
        filter.put("ratingDateStart", "2026-09-10");
        try (Workbook workbook = read(excel.export(null, filter))) {
            assertEquals(2, workbook.getSheetAt(0).getPhysicalNumberOfRows());
            assertEquals(OTHER_CARD, workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue());
        }
        scoped(1L);
        try (Workbook workbook = read(excel.export(null, Collections.emptyMap()))) {
            assertEquals(4, workbook.getSheetAt(0).getPhysicalNumberOfRows());
            for (int i = 1; i <= 3; i++) assertEquals(CARD, workbook.getSheetAt(0).getRow(i).getCell(3).getStringCellValue());
        }
    }

    @Test void selectedExportUsesRecordIdsAndChecksEverySelectionIncludingMissingAndUnauthorized() throws Exception {
        insert(101, 1, 2026, 1, "好", "2026-09-01");
        insert(103, 3, 2026, 1, "中", "2026-09-01");
        try (Workbook workbook = read(excel.export("103,101,103", Collections.emptyMap()))) {
            assertEquals(3, workbook.getSheetAt(0).getPhysicalNumberOfRows());
            assertEquals(EXTERNAL_CARD, workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue());
            assertEquals(CARD, workbook.getSheetAt(0).getRow(2).getCell(3).getStringCellValue());
        }
        assertThrows(IllegalArgumentException.class, () -> excel.export("101,999", Collections.emptyMap()));
        for (String invalid : Arrays.asList("", "0", "-1", "+1", "1.5", "1,", ",1", "101 OR 1=1", "9223372036854775808")) {
            assertThrows(IllegalArgumentException.class, () -> excel.export(invalid, Collections.emptyMap()), invalid);
        }
        scoped(1L);
        assertThrows(ForbiddenException.class, () -> excel.export("101,103", Collections.emptyMap()));
        mvc().perform(get("/api/double-excellence/export").param("ids", "101,103"))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().doesNotExist("Content-Disposition"));
        assertEquals(2, recordCount());
    }

    @Test void exportWritesLiteralStringsInsteadOfFormulas() throws Exception {
        jdbc.update("UPDATE personnel SET name='=HYPERLINK(\"https://invalid.test\")' WHERE id=1");
        insert(101, 1, 2026, 1, "好", "2026-09-01");
        try (Workbook workbook = read(excel.export("101", Collections.emptyMap()))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            for (Cell cell : row) assertEquals(CellType.STRING, cell.getCellType());
            assertTrue(row.getCell(2).getStringCellValue().startsWith("="));
        }
    }

    @Test void exportPaginatesAllRowsAndRejectsOverLimitBeforeWorkbookCreation() throws Exception {
        DoubleExcellenceService source = mock(DoubleExcellenceService.class);
        when(source.query(anyMap())).thenReturn(page(50001, Collections.emptyList()));
        DoubleExcellenceExcelService exporter = new DoubleExcellenceExcelService(source, jdbc);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> exporter.export(null, Collections.emptyMap()));
        assertTrue(failure.getMessage().contains("50000"));
        verify(source, times(1)).query(anyMap());
        List<com.szjx.personnel.server.model.DoubleExcellenceRecord> batch = new ArrayList<>();
        insert(101, 1, 2026, 1, "好", "2026-09-01");
        for (int i = 0; i < 1000; i++) batch.add(core.get(101L));
        when(source.query(anyMap())).thenAnswer(invocation -> {
            Map<String, String> query = invocation.getArgument(0);
            assertEquals("1000", query.get("size"));
            return "1".equals(query.get("page")) ? page(1001, batch) : page(1001, Collections.singletonList(batch.get(0)));
        });
        try (Workbook workbook = read(exporter.export(null, values("page", "88", "size", "1")))) {
            assertEquals(1002, workbook.getSheetAt(0).getPhysicalNumberOfRows());
        }
    }

    @Test void uploadChecksEmptyWrongSuffixFakeWorkbookAndSizeWithoutWrites() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(multipart("/api/double-excellence/import-excel").file(new MockMultipartFile("file", "空.xlsx", "application/octet-stream", new byte[0])))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/double-excellence/import-excel").file(new MockMultipartFile("file", "伪装.xlsx", "application/octet-stream", "not a workbook".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/double-excellence/import-excel").file(new MockMultipartFile("file", "错误.txt", "application/octet-stream", new byte[]{1})))
                .andExpect(status().isBadRequest());
        assertThrows(IllegalArgumentException.class, () -> importBytes(new byte[DoubleExcellenceExcelService.MAX_BYTES + 1]));
        assertEquals(0, recordCount());
    }

    @Test void literalExcelRoutesWinOverGenericAndReturnUtf8DownloadsAndImportCounts() throws Exception {
        ModuleExcelService generic = mock(ModuleExcelService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DoubleExcellenceExcelController(excel), new ModuleExcelController(generic, null))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        MvcResult template = mvc.perform(get("/api/double-excellence/template"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                .andReturn();
        try (Workbook workbook = read(template.getResponse().getContentAsByteArray())) { assertHeaders(workbook.getSheetAt(0)); }
        byte[] uploaded;
        try (Workbook workbook = book(valid("1"))) { uploaded = bytes(workbook); }
        mvc.perform(multipart("/api/double-excellence/import-excel").file(new MockMultipartFile("file", "评比.xlsx", "application/octet-stream", uploaded)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.added").value(1)).andExpect(jsonPath("$.data.updated").value(0));
        mvc.perform(get("/api/double-excellence/export").param("year", "2026"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename=\"double-excellence-")));
        verifyNoInteractions(generic);
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new DoubleExcellenceExcelController(excel)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private void scoped(Long... departments) {
        DataScope.set(new DataScope.Context(7L, "excel-test", false, new LinkedHashSet<Long>(Arrays.asList(departments))));
    }

    private void insert(long id, long person, int year, int quarter, String rating, String date) {
        jdbc.update("INSERT INTO double_excellence_record(id,personnel_id,year,quarter,rating,rating_date) VALUES(?,?,?,?,?,?)", id, person, year, quarter, rating, date);
    }

    private int recordCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM double_excellence_record", Integer.class); }

    private Map<String, Object> importBytes(byte[] uploaded) throws Exception { return excel.importExcel(new ByteArrayInputStream(uploaded)); }

    private static Map<String, Object> page(long total, List<?> rows) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("total", total); result.put("list", rows); return result;
    }

    private static Map<String, String> values(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }

    private static void counts(Map<String, Object> result, int added, int updated, int failed) {
        assertEquals(added, result.get("added"), result.toString());
        assertEquals(updated, result.get("updated"), result.toString());
        assertEquals(failed, result.get("failed"), result.toString());
        assertEquals(failed, ((List<?>) result.get("errors")).size());
    }

    private static String[] valid(String quarter) { return row("2026", quarter, "同名人员", CARD, "好", "2026-09-01"); }
    private static String[] row(String... values) { return values; }

    private static XSSFWorkbook book(String[]... rows) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        writeRows(workbook.createSheet("双争评比"), rows);
        return workbook;
    }

    private static void writeRows(Sheet sheet, String[]... rows) {
        Row header = sheet.createRow(0);
        for (int c = 0; c < HEADERS.length; c++) header.createCell(c).setCellValue(HEADERS[c]);
        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
        }
    }

    private static void dateStyle(Cell cell) {
        Workbook workbook = cell.getSheet().getWorkbook();
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("m/d/yy"));
        cell.setCellStyle(style);
    }

    private static void assertHeaders(Sheet sheet) {
        Row header = sheet.getRow(0);
        assertEquals(6, header.getLastCellNum());
        for (int c = 0; c < HEADERS.length; c++) {
            assertEquals(HEADERS[c], header.getCell(c).getStringCellValue());
            assertEquals(FillPatternType.NO_FILL, header.getCell(c).getCellStyle().getFillPattern());
        }
    }

    private static Workbook read(byte[] bytes) throws Exception { return WorkbookFactory.create(new ByteArrayInputStream(bytes)); }

    private static byte[] bytes(Workbook workbook) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); workbook.write(output); return output.toByteArray();
    }
}
