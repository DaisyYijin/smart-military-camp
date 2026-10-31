package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.BorderStyle;
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
import java.util.List;

import static com.szjx.personnel.server.service.RollCallExcelTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Full-width unit names precede child headings; only leaf names remain single-column. */
class RollCallDirectNamesTest {
    private final JdbcTemplate jdbc = metadataJdbc();
    private final PersonnelService people = mock(PersonnelService.class);
    private final RollCallExcelService service = new RollCallExcelService(people, mock(DepartmentService.class), jdbc);
    private long nextId = 1;

    @AfterEach void close() { DataScope.clear(); closeJdbc(jdbc); }

    private Personnel person(String name, long department) {
        Personnel p = new Personnel();
        p.setId(nextId++); p.setName(name); p.setDepartmentId(department); p.setStatus("在位");
        return p;
    }

    private XSSFWorkbook export(Personnel... rows) throws Exception {
        when(people.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(rows));
        return new XSSFWorkbook(new ByteArrayInputStream(service.build(Collections.emptyMap())));
    }

    private static void startsBelow(Sheet sheet, String title, int row, int col, String... names) {
        Cell heading = headingCell(sheet, title);
        assertEquals(row - 1, heading.getRowIndex()); assertEquals(col, heading.getColumnIndex());
        assertEquals(row, nameStartRow(sheet, heading));
        CellRangeAddress unitSpan = headerRegion(sheet, heading);
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], cell(sheet, row + i, col));
            CellRangeAddress nameSpan = headerRegion(sheet, sheet.getRow(row + i).getCell(col));
            assertEquals(row + i, nameSpan.getFirstRow()); assertEquals(row + i, nameSpan.getLastRow());
            assertEquals(unitSpan.getFirstColumn(), nameSpan.getFirstColumn());
            assertEquals(unitSpan.getLastColumn(), nameSpan.getLastColumn(), "Names must span their whole actual unit");
        }
    }

    private static void assertNoLabelsOrFillsAndUniqueNames(XSSFWorkbook wb, Personnel... people) {
        Sheet sheet = wb.getSheetAt(0);
        List<String> values = new ArrayList<String>();
        for (int i = 0; i < wb.getNumCellStyles(); i++)
            assertEquals(FillPatternType.NO_FILL, wb.getCellStyleAt(i).getFillPattern(), "Every workbook style must be unfilled");
        for (Row row : sheet) for (Cell c : row) {
            String text = cell(sheet, row.getRowNum(), c.getColumnIndex());
            values.add(text);
            assertFalse(text.contains("直属人员"), "No virtual personnel label at " + c.getAddress());
            assertEquals(FillPatternType.NO_FILL, c.getCellStyle().getFillPattern(), c.getAddress().toString());
            if (text.contains("(在位") && text.endsWith("人)")) {
                CellRangeAddress region = headerRegion(sheet, c);
                assertEquals(c.getRowIndex(), region.getFirstRow());
                assertEquals(c.getRowIndex(), region.getLastRow(), "Unit headings may only merge horizontally");
            }
        }
        // Include border/placeholder cells inside every horizontal or absent-reason merge.
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                    Cell mergedCell = sheet.getRow(r).getCell(c);
                    assertNotNull(mergedCell, region.formatAsString());
                    assertEquals(FillPatternType.NO_FILL, mergedCell.getCellStyle().getFillPattern(), mergedCell.getAddress().toString());
                }
            }
        }
        for (Personnel p : people) {
            assertEquals(1, Collections.frequency(values, p.getName()), p.getName() + " must occur exactly once");
            Cell name = headingCell(sheet, p.getName());
            CellRangeAddress span = headerRegion(sheet, name);
            assertEquals(name.getRowIndex(), span.getFirstRow());
            assertEquals(name.getRowIndex(), span.getLastRow(), "Names must never be merged vertically");
        }
    }

    @Test void guYuxuanAndCompanyPeopleUseFullUnitWidthBeforeDeeperUnits() throws Exception {
        jdbc.update("INSERT INTO department VALUES(1,'一营',0,0),(2,'XX连',1,0),(3,'三排',2,0),(4,'二排',2,1),(5,'一班',4,0),(6,'浅连',1,1)");
        Personnel root = person("营级人员", 1), company = person("连级人员", 2), gu = person("顾宇轩", 3);
        Personnel squad = person("班级人员", 5), shallowA = person("浅连甲", 6), shallowB = person("浅连乙", 6);
        try (XSSFWorkbook wb = export(root, company, gu, squad, shallowA, shallowB)) {
            Sheet sheet = wb.getSheetAt(0);
            startsBelow(sheet, "一营(在位6人)", 5, 0, root.getName());
            startsBelow(sheet, "XX连(在位3人)", 7, 0, company.getName());
            startsBelow(sheet, "三排(在位1人)", 9, 0, "顾宇轩");
            startsBelow(sheet, "一班(在位1人)", 10, 1, squad.getName());
            startsBelow(sheet, "浅连(在位2人)", 7, 2, shallowA.getName(), shallowB.getName());
            assertEquals("A5:C5", headerRegion(sheet, headingCell(sheet, "一营(在位6人)")).formatAsString());
            assertEquals("A7:B7", headerRegion(sheet, headingCell(sheet, "XX连(在位3人)")).formatAsString());
            assertEquals("A9", headerRegion(sheet, headingCell(sheet, "三排(在位1人)")).formatAsString());
            assertEquals("C7", headerRegion(sheet, headingCell(sheet, "浅连(在位2人)")).formatAsString());
            assertEquals(8, headingRow(sheet, "二排(在位1人)"));
            assertNoLabelsOrFillsAndUniqueNames(wb, root, company, gu, squad, shallowA, shallowB);
        }
    }

    @Test void longParentColumnFinishesBeforeAllChildHeadersAndAbsentArea() throws Exception {
        jdbc.update("INSERT INTO department VALUES(1,'一营',0,0),(2,'一连',1,0),(3,'一排',2,0),(4,'一班',3,0)");
        Personnel[] rows = new Personnel[12];
        for (int i = 0; i < 8; i++) rows[i] = person("营级第" + i + "人", 1);
        rows[8] = person("连级人员", 2); rows[9] = person("排级人员", 3); rows[10] = person("班级人员", 4);
        rows[11] = person("外出人员", 4); rows[11].setStatus("出差");
        try (XSSFWorkbook wb = export(rows)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 0; i < 8; i++) assertEquals(rows[i].getName(), cell(sheet, 5 + i, 0));
            startsBelow(sheet, "一连(在位3人)", 14, 0, rows[8].getName());
            startsBelow(sheet, "一排(在位2人)", 16, 0, rows[9].getName());
            startsBelow(sheet, "一班(在位1人)", 18, 0, rows[10].getName());
            // Root height = (1+8)+(1+1)+(1+1)+(1+1)=15; row 19 is blank, title row 20.
            for (int c = 0; c < 6; c++) assertEquals("", cell(sheet, 19, c));
            assertEquals(20, headingRow(sheet, "不在位人员(共1人:出差1人)"));
            assertEquals("出差(1人)", cell(sheet, 21, 0));
            assertEquals(rows[11].getName(), cell(sheet, 21, 1));
            for (int r = 5; r < 19; r++) for (int c = 1; c < 6; c++) {
                Cell grid = sheet.getRow(r).getCell(c);
                assertEquals("", cell(sheet, r, c));
                assertEquals(BorderStyle.THIN, grid.getCellStyle().getBorderBottom());
            }
            for (int r = 5; r < 13; r++) assertEquals(22f, sheet.getRow(r).getHeightInPoints(), 0.01f);
            assertNoLabelsOrFillsAndUniqueNames(wb, rows);
        }
    }

    @Test void longHeaderHeightsSurviveParentListsWithoutOverlappingChildHeaders() throws Exception {
        String longCompany = String.join("", Collections.nCopies(12, "超长连级名称"));
        String longPlatoon = String.join("", Collections.nCopies(10, "超长排级名称"));
        String longSquad = String.join("", Collections.nCopies(8, "超长班级名称"));
        jdbc.update("INSERT INTO department VALUES(1,'一营',0,0),(2,?,1,0),(3,?,2,0),(4,?,3,0)", longCompany, longPlatoon, longSquad);
        Personnel rootA = person("营甲", 1), rootB = person("营乙", 1), rootC = person("营丙", 1);
        Personnel companyA = person("连甲", 2), companyB = person("连乙", 2), platoon = person("顾宇轩", 3), squad = person("班甲", 4);
        try (XSSFWorkbook wb = export(rootA, rootB, rootC, companyA, companyB, platoon, squad)) {
            Sheet sheet = wb.getSheetAt(0);
            startsBelow(sheet, "一营(在位7人)", 5, 0, "营甲", "营乙", "营丙");
            startsBelow(sheet, longCompany + "(在位4人)", 9, 0, "连甲", "连乙");
            startsBelow(sheet, longPlatoon + "(在位2人)", 12, 0, "顾宇轩");
            startsBelow(sheet, longSquad + "(在位1人)", 14, 0, "班甲");
            for (int r : new int[]{8, 11, 13})
                assertTrue(sheet.getRow(r).getHeightInPoints() >= 36, "Long heading at row " + r + " must retain its height");
            assertEquals(22f, sheet.getRow(14).getHeightInPoints(), 0.01f);
            assertTrue(headingCell(sheet, longCompany + "(在位4人)").getCellStyle().getWrapText());
            assertNoLabelsOrFillsAndUniqueNames(wb, rootA, rootB, rootC, companyA, companyB, platoon, squad);
        }
    }
}
