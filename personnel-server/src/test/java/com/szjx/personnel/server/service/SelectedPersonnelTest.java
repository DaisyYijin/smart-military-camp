package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.config.GlobalExceptionHandler;
import com.szjx.personnel.server.controller.DoubleExcellenceController;
import com.szjx.personnel.server.controller.DoubleExcellenceExcelController;
import com.szjx.personnel.server.model.DoubleExcellenceRecord;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Own temporary SQLite schema only; no application startup, shared fixture edits or real data. */
class SelectedPersonnelTest {
    @TempDir Path directory;
    private JdbcTemplate jdbc;
    private DoubleExcellenceService core;
    private DoubleExcellenceExcelService excel;

    @BeforeEach void setup() {
        DataScope.clear();
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.sqlite.JDBC");
        source.setUrl("jdbc:sqlite:" + directory.resolve("selected-personnel.db").toAbsolutePath());
        jdbc = spy(new JdbcTemplate(source));
        // sort 列与生产库一致：stats 的“单位”列经 DepartmentHierarchy 读取排序。
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT,parent_id INTEGER DEFAULT 0,sort INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY,name TEXT,id_card TEXT,department_id INTEGER)");
        jdbc.execute("CREATE TABLE double_excellence_record(id INTEGER PRIMARY KEY,personnel_id INTEGER NOT NULL,year INTEGER NOT NULL,quarter INTEGER NOT NULL,rating TEXT NOT NULL,rating_date TEXT NOT NULL,created_at TEXT,updated_at TEXT,UNIQUE(personnel_id,year,quarter))");
        // 核心服务的评级校验查询 sys_dict(de_rating),隔离库需要同表同数据
        jdbc.execute("CREATE TABLE sys_dict(id INTEGER PRIMARY KEY AUTOINCREMENT,dict_type TEXT NOT NULL,dict_label TEXT NOT NULL,sort INTEGER DEFAULT 0,enabled INTEGER DEFAULT 1,parent_id INTEGER DEFAULT 0)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,sort,enabled,parent_id) VALUES('de_rating','好',1,1,0),('de_rating','中',2,1,0),('de_rating','一般',3,1,0)");
        jdbc.update("INSERT INTO department(id,name,parent_id) VALUES(1,'本部',0),(2,'下级',1),(3,'外部',0)");
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(1,'张三','CARD1',1),(2,'李四','CARD2',2),(3,'外部姓名','CARD3',3),(4,'无评级','CARD4',1)");
        insert(101, 1, 2026, 1, "好");
        insert(102, 1, 2026, 2, "中");
        insert(103, 2, 2026, 1, "一般");
        insert(104, 3, 2026, 1, "好");
        insert(105, 1, 2025, 1, "一般");
        core = new DoubleExcellenceService(jdbc);
        excel = new DoubleExcellenceExcelService(core, jdbc);
    }

    @AfterEach void clearScope() { DataScope.clear(); }

    @Test void exactSingleAndMultipleSelectionsApplyToRecordsStatsTotalsAndEveryPage() {
        Map<String,Object> single = core.query(values("personnelIds", "1"));
        assertEquals(3, total(single));
        assertEquals(Collections.singleton(1L), recordPeople(single));
        Map<String,Object> singleStats = core.stats(values("personnelIds", "2", "year", "2026"));
        assertEquals(1, total(singleStats));
        assertEquals(Collections.singleton(2L), statsPeople(singleStats));
        assertEquals(1L, quarters(singleStats).get(0).get("general"));

        Map<String,String> selected = values("personnelIds", "1,2,4", "year", "2026", "size", "1");
        Map<String,Object> firstRecords = core.query(selected);
        assertEquals(3, total(firstRecords));
        assertEquals(1, ((List<?>) firstRecords.get("list")).size());
        Map<String,Object> first = core.stats(selected);
        assertEquals(3, total(first));
        assertEquals(Collections.singleton(1L), statsPeople(first));
        assertEquals(1L, quarters(first).get(0).get("good"));
        assertEquals(1L, quarters(first).get(0).get("general"));
        assertEquals(1L, quarters(first).get(0).get("unrated"));
        assertEquals(1L, quarters(first).get(1).get("middle"));
        assertEquals(2L, quarters(first).get(1).get("unrated"));
        assertEquals(3L, quarters(first).get(3).get("unrated"));
        selected.put("page", "3");
        Map<String,Object> third = core.stats(selected);
        assertEquals(Collections.singleton(4L), statsPeople(third));
        for (int q = 1; q <= 4; q++) assertNull(statsRows(third).get(0).get("q" + q));
        assertEquals(quarters(first), quarters(third));
        selected.put("page", "99");
        Map<String,Object> later = core.stats(selected);
        assertEquals(3, total(later));
        assertTrue(statsRows(later).isEmpty());
        assertEquals(quarters(first), quarters(later));
        Map<String,Object> laterRecords = core.query(selected);
        assertEquals(3, total(laterRecords));
        assertTrue(((List<?>) laterRecords.get("list")).isEmpty());
    }

    @Test void selectedPeopleWithoutRecordsRemainVisibleAsUnrated() {
        Map<String,String> filters = values("personnelIds", "4", "year", "2026", "quarter", "1", "rating", "unrated");
        Map<String,Object> result = core.stats(filters);
        assertEquals(Collections.singleton(4L), statsPeople(result));
        assertEquals(1, total(result));
        for (Map<String,Object> quarter : quarters(result)) assertEquals(1L, quarter.get("unrated"));
        assertEquals(0, total(core.query(values("personnelIds", "4"))));
        assertEquals(0, total(core.stats(values("personnelIds", "4", "year", "2026", "rating", "好"))));
    }

    @Test void existingDepartmentNameIdentityYearQuarterGradeAndDateFiltersIntersectTheSelection() {
        scoped(1L, 2L);
        Map<String,String> filters = values("personnelIds", "1,2,4", "departmentId", "1", "personnelName", "李", "idCard", "CARD2", "year", "2026", "quarter", "1", "rating", "一般");
        assertEquals(Collections.singleton(2L), recordPeople(core.query(filters)));
        assertEquals(Collections.singleton(2L), statsPeople(core.stats(filters)));
        filters.put("departmentId", "3");
        assertEquals(0, total(core.query(filters)));
        assertEquals(0, total(core.stats(filters)));
        filters.put("departmentId", "1");
        filters.put("personnelIds", "1,4");
        assertEquals(0, total(core.query(filters)));
        assertEquals(0, total(core.stats(filters)));
        assertEquals(1, total(core.query(values("personnelIds", "1,2", "year", "2025", "rating", "一般"))));
        assertEquals(Collections.singleton(1L), statsPeople(core.stats(values("personnelIds", "1,2", "year", "2025", "rating", "一般"))));
        assertEquals(0, total(core.query(values("personnelIds", "2", "ratingDateStart", "2026-10-19"))));
        assertEquals(1, total(core.query(values("personnelIds", "2", "ratingDateStart", "2026-10-18", "ratingDateEnd", "2026-10-18"))));
    }

    @Test void badCsvFailsBeforeAnyJdbcReadIncludingEmptyComponentsOverflowAndInjection() {
        JdbcTemplate unused = mock(JdbcTemplate.class);
        DoubleExcellenceService boundary = new DoubleExcellenceService(unused);
        for (String value : invalidCsv()) {
            Map<String,String> filters = values("personnelIds", value);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> boundary.selectedPersonnel(filters), value).getMessage().contains("personnelIds"));
            assertThrows(IllegalArgumentException.class, () -> boundary.query(filters), value);
            assertThrows(IllegalArgumentException.class, () -> boundary.stats(filters), value);
        }
        assertThrows(IllegalArgumentException.class, () -> boundary.selectedPersonnel(values("personnelIds", null)));
        assertThrows(IllegalArgumentException.class, () -> boundary.query(values("personnelIds", null)));
        assertThrows(IllegalArgumentException.class, () -> boundary.stats(values("personnelIds", null)));
        verifyNoInteractions(unused);
    }

    @Test void duplicatesAreDeduplicatedButRawLimitIs200AndValidationUsesOneParameterizedLookup() {
        clearInvocations(jdbc);
        assertEquals(new LinkedHashSet<>(Arrays.asList(2L, 1L)), core.selectedPersonnel(values("personnelIds", " 2,1,2 ,1 ")));
        List<org.mockito.invocation.Invocation> lookups = new ArrayList<>();
        for (org.mockito.invocation.Invocation invocation : mockingDetails(jdbc).getInvocations()) {
            if ("queryForList".equals(invocation.getMethod().getName())) lookups.add(invocation);
        }
        assertEquals(1, lookups.size());
        org.mockito.invocation.Invocation lookup = lookups.get(0);
        assertEquals("SELECT id,department_id FROM personnel WHERE id IN (?,?)", lookup.getArgument(0));
        assertFalse(((String) lookup.getArgument(0)).contains("2,1"));
        String repeated = String.join(",", Collections.nCopies(200, "1"));
        assertEquals(Collections.singleton(1L), core.selectedPersonnel(values("personnelIds", repeated)));
        assertEquals(3, total(core.query(values("personnelIds", repeated))));
        assertEquals(1, total(core.stats(values("personnelIds", repeated, "year", "2026"))));
        String excessive = repeated + ",1";
        clearInvocations(jdbc);
        assertThrows(IllegalArgumentException.class, () -> core.selectedPersonnel(values("personnelIds", excessive)));
        verifyNoInteractions(jdbc);
    }

    @Test void exactly200DistinctPeopleAndMaximumPositiveLongAreSupported() {
        List<String> ids = new ArrayList<>();
        for (long id = 10; id < 210; id++) {
            jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(?,?,?,1)", id, "人员" + id, "CARD" + id);
            ids.add(String.valueOf(id));
        }
        String csv = String.join(",", ids);
        assertEquals(200, core.selectedPersonnel(values("personnelIds", csv)).size());
        assertEquals(0, total(core.query(values("personnelIds", csv))));
        Map<String,Object> result = core.stats(values("personnelIds", csv, "year", "2026", "size", "1"));
        assertEquals(200, total(result));
        for (Map<String,Object> quarter : quarters(result)) assertEquals(200L, quarter.get("unrated"));
        assertThrows(IllegalArgumentException.class, () -> core.stats(values("personnelIds", csv + ",1")));
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(?,'边界','MAX',1)", Long.MAX_VALUE);
        assertEquals(Collections.singleton(Long.MAX_VALUE), core.selectedPersonnel(values("personnelIds", String.valueOf(Long.MAX_VALUE))));
    }

    @Test void everySelectedPersonMustExistAndBeAccessibleEvenWhenOtherFiltersMatchNothing() {
        scoped(1L, 2L);
        Map<String,String> denied = values("personnelIds", "1,3", "personnelName", "绝不匹配", "year", "2024");
        assertThrows(ForbiddenException.class, () -> core.query(denied));
        assertThrows(ForbiddenException.class, () -> core.stats(denied));
        for (String ids : Arrays.asList("999", "1,999", "4,999")) {
            assertThrows(IllegalArgumentException.class, () -> core.query(values("personnelIds", ids)));
            assertThrows(IllegalArgumentException.class, () -> core.stats(values("personnelIds", ids)));
        }
        scoped();
        assertThrows(ForbiddenException.class, () -> core.selectedPersonnel(values("personnelIds", "1")));
        DataScope.clear();
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(5,'未分配','CARD5',NULL)");
        assertEquals(Collections.singleton(5L), core.selectedPersonnel(values("personnelIds", "5")));
        scoped(1L);
        assertThrows(ForbiddenException.class, () -> core.selectedPersonnel(values("personnelIds", "5")));
    }

    @Test void absentSelectionPreservesLegacyScopeAndRecordIdsAreNotPersonnelAliases() throws Exception {
        assertNull(core.selectedPersonnel(null));
        assertNull(core.selectedPersonnel(Collections.emptyMap()));
        assertEquals(5, total(core.query(values("ids", "101"))));
        assertEquals(4, total(core.stats(values("year", "2026", "ids", "101"))));
        assertEquals(5, exportedCards(excel.export(null, null)).size());
        DataScope.set(new DataScope.Context(1L, "admin", true, Collections.emptySet()));
        assertEquals(5, total(core.query(null)));
        assertEquals(4, total(core.stats(values("year", "2026"))));
        scoped(1L, 2L);
        assertEquals(4, total(core.query(null)));
        assertEquals(3, total(core.stats(values("year", "2026"))));
        assertEquals(4, exportedCards(excel.export(null, Collections.emptyMap())).size());
        scoped();
        assertEquals(0, total(core.query(null)));
        assertEquals(0, total(core.stats(null)));
        assertTrue(exportedCards(excel.export(null, Collections.emptyMap())).isEmpty());
    }

    @Test void filteredExportContainsOnlySelectedPeopleAndRetainsAllOtherFilters() throws Exception {
        Map<String,String> filters = values("personnelIds", "2", "year", "2026", "quarter", "Q1", "departmentId", "1", "personnelName", "李", "idCard", "CARD2", "rating", "一般", "ratingDateStart", "2026-10-18", "ratingDateEnd", "2026-10-18", "page", "99", "size", "1");
        assertEquals(Collections.singletonList("CARD2"), exportedCards(excel.export(null, filters)));
        assertEquals(4, exportedCards(excel.export(null, values("personnelIds", "1,2,4"))).size());
        assertTrue(exportedCards(excel.export(null, values("personnelIds", "4"))).isEmpty());
        filters.put("personnelIds", "1,4");
        assertTrue(exportedCards(excel.export(null, filters)).isEmpty());
    }

    @Test void checkedRecordIdsCannotEscapePersonnelWhitelistAndBothKindsOfIdsRemainDistinct() throws Exception {
        assertEquals(Arrays.asList("CARD2", "CARD1"), exportedCards(excel.export("103,101,103", values("personnelIds", "1,2"))));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> excel.export("101,103", values("personnelIds", "1")));
        assertEquals("所选记录不属于当前人员范围", failure.getMessage());
        assertThrows(IllegalArgumentException.class, () -> excel.export("101", values("personnelIds", "101")));
        assertThrows(IllegalArgumentException.class, () -> excel.export("101", values("personnelIds", "1,999")));
        scoped(1L);
        assertThrows(ForbiddenException.class, () -> excel.export("101", values("personnelIds", "1,3")));
        assertThrows(ForbiddenException.class, () -> excel.export("104", values("personnelIds", "1")));
        assertThrows(ForbiddenException.class, () -> excel.export(null, values("personnelIds", "1,3")));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM double_excellence_record", Integer.class));
    }

    @Test void invalidPersonnelOrRecordCsvNeverReachesRecordReadsOrWrites() throws Exception {
        DoubleExcellenceService boundary = spy(core);
        DoubleExcellenceExcelService exporter = new DoubleExcellenceExcelService(boundary, jdbc);
        clearInvocations(jdbc);
        for (String invalid : invalidCsv()) {
            assertThrows(IllegalArgumentException.class, () -> exporter.export("101", values("personnelIds", invalid)));
            assertThrows(IllegalArgumentException.class, () -> exporter.export(null, values("personnelIds", invalid)));
        }
        verify(boundary, never()).get(any());
        verify(boundary, never()).importRecord(anyMap());
        verifyNoInteractions(jdbc);
        clearInvocations(boundary);
        for (String invalid : Arrays.asList("", "0", "101,", "101,1 OR 1=1", "9223372036854775808")) {
            assertThrows(IllegalArgumentException.class, () -> exporter.export(invalid, values("personnelIds", "1")));
        }
        verifyNoInteractions(boundary);
        verifyNoInteractions(jdbc);
    }

    @Test void httpBadSelectionsAre400AndScopeViolationsAre403WithoutDataOrDownloadHeaders() throws Exception {
        MockMvc mvc = mvc();
        for (String route : Arrays.asList("/api/double-excellence", "/api/double-excellence/stats", "/api/double-excellence/export")) {
            for (String invalid : invalidCsv()) {
                mvc.perform(get(route).param("personnelIds", invalid))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
                        .andExpect(jsonPath("$.data").doesNotExist()).andExpect(header().doesNotExist("Content-Disposition"));
            }
            mvc.perform(get(route).param("personnelIds", "1,999"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data").doesNotExist());
            scoped(1L, 2L);
            mvc.perform(get(route).param("personnelIds", "1,3"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.data").doesNotExist()).andExpect(header().doesNotExist("Content-Disposition"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("CARD3"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("外部姓名"))));
            DataScope.clear();
        }
        mvc.perform(get("/api/double-excellence/export").param("ids", "101,103").param("personnelIds", "1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("所选记录不属于当前人员范围"))
                .andExpect(header().doesNotExist("Content-Disposition"));
        mvc.perform(get("/api/double-excellence").param("personnelIds", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].personnelId").value(2));
        mvc.perform(get("/api/double-excellence/stats").param("personnelIds", "4").param("year", "2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].personnelId").value(4));
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new DoubleExcellenceController(core), new DoubleExcellenceExcelController(excel))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    private void scoped(Long... departments) {
        DataScope.set(new DataScope.Context(7L, "selected-test", false, new LinkedHashSet<>(Arrays.asList(departments))));
    }
    private void insert(long id, long person, int year, int quarter, String rating) {
        jdbc.update("INSERT INTO double_excellence_record(id,personnel_id,year,quarter,rating,rating_date) VALUES(?,?,?,?,?,'2026-10-18')", id, person, year, quarter, rating);
    }
    private static List<String> invalidCsv() {
        return Arrays.asList("", " ", "\t\r\n", ",", "1,", ",1", "1,,2", "1, ,2", "0", "-1", "+1", "01", "1.0", "1e0", "0x1", "１", "9223372036854775808", "1 OR 1=1", "1); DROP TABLE personnel;--", "1,2 UNION SELECT 3", String.join(",", Collections.nCopies(201, "1")));
    }
    private static Map<String,String> values(String... pairs) {
        Map<String,String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }
    private static long total(Map<String,Object> result) { return ((Number) result.get("total")).longValue(); }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> statsRows(Map<String,Object> result) { return (List<Map<String,Object>>) result.get("list"); }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> quarters(Map<String,Object> result) { return (List<Map<String,Object>>) result.get("quarters"); }
    private static Set<Long> recordPeople(Map<String,Object> result) {
        Set<Long> people = new LinkedHashSet<>();
        for (Object row : (List<?>) result.get("list")) people.add(((DoubleExcellenceRecord) row).getPersonnelId());
        return people;
    }
    private static Set<Long> statsPeople(Map<String,Object> result) {
        Set<Long> people = new LinkedHashSet<>();
        for (Map<String,Object> row : statsRows(result)) people.add(((Number) row.get("personnelId")).longValue());
        return people;
    }
    private static List<String> exportedCards(byte[] bytes) throws Exception {
        List<String> cards = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(6, sheet.getRow(0).getLastCellNum());
            for (int row = 1; row <= sheet.getLastRowNum(); row++) cards.add(sheet.getRow(row).getCell(3).getStringCellValue());
        }
        return cards;
    }
}
