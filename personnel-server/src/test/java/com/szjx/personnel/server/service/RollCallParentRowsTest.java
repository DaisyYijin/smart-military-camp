package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.szjx.personnel.server.service.RollCallExcelTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/** Parent names occupy whole unit rows before the next generation of unit headings. */
class RollCallParentRowsTest {
    private final JdbcTemplate jdbc = metadataJdbc();
    private final PersonnelService people = mock(PersonnelService.class);
    private final RollCallExcelService service = new RollCallExcelService(people, mock(DepartmentService.class), jdbc);
    private long nextId = 1;

    @AfterEach void close() { DataScope.clear(); closeJdbc(jdbc); }

    private Personnel person(String name, long department) {
        Personnel p = new Personnel(); p.setId(nextId++); p.setName(name);
        p.setDepartmentId(department); p.setStatus("在位"); return p;
    }

    private XSSFWorkbook export(Map<String, String> query, Personnel... rows) throws Exception {
        when(people.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(rows));
        return new XSSFWorkbook(new ByteArrayInputStream(service.build(query)));
    }

    private static void unitNames(Sheet sheet, String title, int headingRow, int firstColumn, int lastColumn, String... names) {
        Cell heading = headingCell(sheet, title);
        CellRangeAddress span = headerRegion(sheet, heading);
        assertEquals(headingRow, heading.getRowIndex());
        assertEquals(headingRow, span.getFirstRow()); assertEquals(headingRow, span.getLastRow());
        assertEquals(firstColumn, span.getFirstColumn()); assertEquals(lastColumn, span.getLastColumn());
        int start = nameStartRow(sheet, heading);
        for (int i = 0; i < names.length; i++) {
            int row = start + i;
            assertEquals(names[i], cell(sheet, row, firstColumn));
            CellRangeAddress nameSpan = headerRegion(sheet, sheet.getRow(row).getCell(firstColumn));
            assertEquals(row, nameSpan.getFirstRow()); assertEquals(row, nameSpan.getLastRow());
            assertEquals(firstColumn, nameSpan.getFirstColumn()); assertEquals(lastColumn, nameSpan.getLastColumn());
            for (int col = firstColumn + 1; col <= lastColumn; col++)
                assertEquals("", cell(sheet, row, col), "Only the first cell of a merged name row contains text");
        }
    }

    private static void plainAndUnique(XSSFWorkbook wb, String... names) {
        Sheet sheet = wb.getSheetAt(0); List<String> values = new ArrayList<String>();
        for (int i = 0; i < wb.getNumCellStyles(); i++)
            assertEquals(FillPatternType.NO_FILL, wb.getCellStyleAt(i).getFillPattern());
        for (Row row : sheet) for (Cell c : row) {
            String text = cell(sheet, c.getRowIndex(), c.getColumnIndex()); values.add(text);
            assertFalse(text.contains("直属人员"));
            assertEquals(FillPatternType.NO_FILL, c.getCellStyle().getFillPattern(), c.getAddress().toString());
        }
        for (String name : names) assertEquals(1, Collections.frequency(values, name), name);
        // Overlapping merges could hide child headings or a previous parent's names.
        sheet.validateMergedRegions();
    }

    @Test void threeColumnPlatoonPutsGuYuxuanAcrossAllThreeCellsBeforeSquadHeadings() throws Exception {
        jdbc.update("INSERT INTO department VALUES(1,'XX连',0,0),(2,'三排',1,0),(3,'二排',1,1),(11,'一班',2,0),(12,'二班',2,1),(13,'三班',2,2)");
        Personnel coA = person("连级甲", 1), coB = person("连级乙", 1), gu = person("顾宇轩", 2);
        Personnel a = person("一班甲", 11), b = person("二班甲", 12), c = person("三班甲", 13), other = person("二排甲", 3);
        try (XSSFWorkbook wb = export(Collections.emptyMap(), coA, coB, gu, a, b, c, other)) {
            Sheet sheet = wb.getSheetAt(0);
            unitNames(sheet, "XX连(在位7人)", 4, 0, 3, "连级甲", "连级乙");
            unitNames(sheet, "三排(在位4人)", 7, 0, 2, "顾宇轩");
            unitNames(sheet, "二排(在位1人)", 7, 3, 3, "二排甲");
            unitNames(sheet, "一班(在位1人)", 9, 0, 0, "一班甲");
            unitNames(sheet, "二班(在位1人)", 9, 1, 1, "二班甲");
            unitNames(sheet, "三班(在位1人)", 9, 2, 2, "三班甲");
            assertEquals("A9:C9", headerRegion(sheet, headingCell(sheet, "顾宇轩")).formatAsString());
            assertEquals(12, headingRow(sheet, "不在位人员(共0人)"));
            plainAndUnique(wb, "连级甲", "连级乙", "顾宇轩", "一班甲", "二班甲", "三班甲", "二排甲");
        }
    }

    @Test void siblingPlatoonsShareHeadingRowButUnequalParentListsStaggerTheirSquads() throws Exception {
        String longSquad = String.join("", Collections.nCopies(8, "超长一班名称"));
        jdbc.update("INSERT INTO department VALUES(1,'一连',0,0),(20,'二排',1,5),(10,'一排',1,-1),(102,'二班',10,0),(101,?,10,0),(201,'三班',20,0)", longSquad);
        Personnel first = person("一排人员", 10), second3 = person("二排03", 20), second1 = person("二排01", 20), second2 = person("二排02", 20);
        Personnel a = person("一班人员", 101), b = person("二班人员", 102), c = person("三班人员", 201);
        try (XSSFWorkbook wb = export(Collections.singletonMap("sort", "name:asc"), second3, c, b, second1, first, second2, a)) {
            Sheet sheet = wb.getSheetAt(0);
            unitNames(sheet, "一连(在位7人)", 4, 0, 2); // Empty parent: children immediately on the next row.
            unitNames(sheet, "一排(在位3人)", 5, 0, 1, "一排人员");
            unitNames(sheet, "二排(在位4人)", 5, 2, 2, "二排01", "二排02", "二排03");
            unitNames(sheet, longSquad + "(在位1人)", 7, 0, 0, "一班人员");
            unitNames(sheet, "二班(在位1人)", 7, 1, 1, "二班人员");
            unitNames(sheet, "三班(在位1人)", 9, 2, 2, "三班人员");
            assertTrue(sheet.getRow(7).getHeightInPoints() >= 36, "Long squad heading sharing a row with another platoon's names must not be flattened");
            assertEquals(12, headingRow(sheet, "不在位人员(共0人)"));
            plainAndUnique(wb, "一排人员", "二排01", "二排02", "二排03", "一班人员", "二班人员", "三班人员");
        }
    }

    @Test void longThreeColumnParentListEndsBeforeSquadsAndAbsenceAndRootsRemainSideBySide() throws Exception {
        jdbc.update("INSERT INTO department VALUES(1,'三排',0,0),(2,'一班',1,0),(3,'二班',1,1),(4,'三班',1,2),(10,'独立连',0,1)");
        Personnel[] rows = new Personnel[13]; String[] parentNames = new String[8];
        for (int i = 0; i < 8; i++) { parentNames[i] = "排级人员" + i; rows[i] = person(parentNames[i], 1); }
        rows[8] = person("一班人员", 2); rows[9] = person("二班人员", 3); rows[10] = person("三班人员", 4);
        rows[11] = person("独立连人员", 10); rows[12] = person("出差人员", 3); rows[12].setStatus("出差");
        try (XSSFWorkbook wb = export(Collections.emptyMap(), rows)) {
            Sheet sheet = wb.getSheetAt(0);
            unitNames(sheet, "三排(在位11人)", 4, 0, 2, parentNames);
            unitNames(sheet, "独立连(在位1人)", 4, 3, 3, "独立连人员");
            unitNames(sheet, "一班(在位1人)", 13, 0, 0, "一班人员");
            unitNames(sheet, "二班(在位1人)", 13, 1, 1, "二班人员");
            unitNames(sheet, "三班(在位1人)", 13, 2, 2, "三班人员");
            for (int col = 0; col < 6; col++) assertEquals("", cell(sheet, 15, col));
            assertEquals(16, headingRow(sheet, "不在位人员(共1人:出差1人)"));
            assertEquals("出差人员", cell(sheet, 17, 1));
            String[] names = Arrays.stream(rows).map(Personnel::getName).toArray(String[]::new);
            plainAndUnique(wb, names);
        }
    }

    @Test void sortedMarksRichStringContentSurvivesFullWidthParentNameMerges() throws Exception {
        jdbc.update("INSERT INTO department VALUES(1,'XX连',0,0),(2,'一排',1,0),(3,'二排',1,1)");
        Personnel low = person("连低", 1); low.setRank("列兵"); low.setPoliticalStatus("中共党员");
        Personnel high = person("连高", 1); high.setRank("上校"); high.setPoliticalStatus("共青团员");
        Personnel childA = person("一排人员", 2), childB = person("二排人员", 3);
        Map<String, Object> driver = new HashMap<String, Object>(); driver.put("personnel_id", low.getId());
        doReturn(Collections.singletonList(driver)).when(jdbc).queryForList(contains("driver_license"));
        Map<String, String> query = new HashMap<String, String>(); query.put("marks", "party,league,driver"); query.put("sort", "rank:desc,name:asc");
        try (XSSFWorkbook wb = export(query, low, childB, high, childA)) {
            Sheet sheet = wb.getSheetAt(0);
            unitNames(sheet, "XX连(在位4人)", 4, 0, 1, "✪连高", "☭连低⊛");
            unitNames(sheet, "一排(在位1人)", 7, 0, 0, "一排人员");
            unitNames(sheet, "二排(在位1人)", 7, 1, 1, "二排人员");
            assertEquals("✪连高", sheet.getRow(5).getCell(0).getRichStringCellValue().getString());
            assertEquals("☭连低⊛", sheet.getRow(6).getCell(0).getRichStringCellValue().getString());
            plainAndUnique(wb, "✪连高", "☭连低⊛", "一排人员", "二排人员");
        }
        verify(jdbc).queryForList(contains("driver_license"));
    }
}
