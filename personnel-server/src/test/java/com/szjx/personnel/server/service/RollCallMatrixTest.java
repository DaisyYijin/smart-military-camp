package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.Cell;
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
import java.util.Map;

import static com.szjx.personnel.server.service.RollCallExcelTest.cell;
import static com.szjx.personnel.server.service.RollCallExcelTest.headerRegion;
import static com.szjx.personnel.server.service.RollCallExcelTest.headingCell;
import static com.szjx.personnel.server.service.RollCallExcelTest.nameStartRow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RollCallMatrixTest {
    private final JdbcTemplate jdbc = RollCallExcelTest.metadataJdbc();
    private final PersonnelService people = mock(PersonnelService.class);
    private final RollCallExcelService service = new RollCallExcelService(people, mock(DepartmentService.class), jdbc);

    @AfterEach void close() { DataScope.clear(); RollCallExcelTest.closeJdbc(jdbc); }

    private Personnel person(long id, String name, long department) {
        Personnel person = new Personnel();
        person.setId(id); person.setName(name); person.setDepartmentId(department); person.setStatus("在职");
        return person;
    }

    private void rows(List<Personnel> rows) {
        when(people.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(rows);
    }

    private XSSFWorkbook workbook(Personnel... rows) throws Exception {
        rows(Arrays.asList(rows));
        return new XSSFWorkbook(new ByteArrayInputStream(service.build(Collections.emptyMap())));
    }

    @Test void platoonsAndSquadsAreSideBySideWhileEachSquadNamesRunDownOneColumn() throws Exception {
        jdbc.update("INSERT INTO department VALUES(1,'一连',0,0),(11,'一排',1,1),(12,'二排',1,2),(111,'一班',11,1),(112,'二班',11,2),(121,'三班',12,1),(122,'四班',12,2)");
        try (XSSFWorkbook wb = workbook(person(1,"一班甲",111),person(2,"一班乙",111),
                person(3,"二班甲",112),person(4,"二班乙",112),person(5,"三班甲",121),person(6,"三班乙",121),
                person(7,"四班甲",122),person(8,"四班乙",122))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell p1 = headingCell(sheet,"一排(在位4人)"), p2 = headingCell(sheet,"二排(在位4人)");
            assertEquals(p1.getRowIndex(),p2.getRowIndex());
            assertTrue(p1.getColumnIndex() < p2.getColumnIndex());
            assertEquals("A6:B6",headerRegion(sheet,p1).formatAsString());
            assertEquals("C6:D6",headerRegion(sheet,p2).formatAsString());
            Cell company = headingCell(sheet,"一连(在位8人)");
            assertEquals("A5:D5",headerRegion(sheet,company).formatAsString());
            String[] squads = {"一班","二班","三班","四班"};
            for (int column=0; column<squads.length; column++) {
                Cell heading = headingCell(sheet,squads[column]+"(在位2人)");
                int names = nameStartRow(sheet, heading); assertEquals(7,names);
                assertEquals(6,heading.getRowIndex());
                assertEquals(column,heading.getColumnIndex());
                assertEquals(squads[column]+"甲",cell(sheet,names,column));
                assertEquals(squads[column]+"乙",cell(sheet,names+1,column));
            }
            assertTrue(cell(sheet,sheet.getLastRowNum(),0).contains("应到:8人    在位:8人    不在位:0人"));
        }
    }

    @Test void parentNamesSpanTheirHeaderThenChildrenStartBelowTheWholeParentList() throws Exception {
        jdbc.update("INSERT INTO department VALUES(1,'营',0,0),(2,'连',1,1),(3,'排',2,1),(4,'班',3,1),(5,'直属小组',1,2)");
        try (XSSFWorkbook wb = workbook(person(1,"营甲",1),person(2,"营乙",1),person(3,"连甲",2),
                person(4,"排甲",3),person(5,"班甲",4),person(6,"班乙",4),person(7,"小组甲",5))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(5,nameStartRow(sheet,headingCell(sheet,"营(在位7人)")));
            assertEquals(8,nameStartRow(sheet,headingCell(sheet,"连(在位4人)")));
            assertEquals(10,nameStartRow(sheet,headingCell(sheet,"排(在位3人)")));
            assertEquals(12,nameStartRow(sheet,headingCell(sheet,"班(在位2人)")));
            assertEquals(8,nameStartRow(sheet,headingCell(sheet,"直属小组(在位1人)")));
            assertEquals("营甲",cell(sheet,5,0));
            assertEquals("营乙",cell(sheet,6,0));
            assertEquals("A6:B6",headerRegion(sheet,headingCell(sheet,"营甲")).formatAsString());
            assertEquals("A7:B7",headerRegion(sheet,headingCell(sheet,"营乙")).formatAsString());
            assertEquals("连甲",cell(sheet,8,0));
            assertEquals("排甲",cell(sheet,10,0));
            assertEquals("班甲",cell(sheet,12,0));
            assertEquals("班乙",cell(sheet,13,0));
            assertEquals("小组甲",cell(sheet,8,1));
            assertEquals("B8",headerRegion(sheet,headingCell(sheet,"直属小组(在位1人)")).formatAsString());
            CellRangeAddress root = headerRegion(sheet,headingCell(sheet,"营(在位7人)"));
            assertEquals("A5:B5",root.formatAsString());
        }
    }

    @Test void moreThanSixUnitsContinueToTheRightWithoutWrappingIntoAnotherNameGrid() throws Exception {
        jdbc.update("INSERT INTO department VALUES(1,'连',0,0)");
        List<Personnel> rows = new ArrayList<Personnel>();
        for (int i=0; i<8; i++) {
            jdbc.update("INSERT INTO department VALUES(?,?,1,?)",10+i,"班"+i,i);
            rows.add(person(i*2+1,"甲"+i,10+i));
            rows.add(person(i*2+2,"乙"+i,10+i));
        }
        rows(rows);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(service.build(Collections.emptyMap())))) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i=0; i<8; i++) {
                Cell heading = headingCell(sheet,"班"+i+"(在位2人)");
                int start = nameStartRow(sheet, heading);
                assertEquals(5,heading.getRowIndex());
                assertEquals(i,heading.getColumnIndex());
                assertEquals("甲"+i,cell(sheet,start,i));
                assertEquals("乙"+i,cell(sheet,start+1,i));
            }
            assertEquals("A5:H5",headerRegion(sheet,headingCell(sheet,"连(在位16人)")).formatAsString());
            assertTrue(wb.getPrintArea(0).contains("$H$"));
        }
    }

    @Test void longUnitNamesReserveHeightForWrappedMergedHeaders() throws Exception {
        String longName = "用于检查多级单位列宽的超长一班名称";
        jdbc.update("INSERT INTO department VALUES(1,'连',0,0),(11,?,1,1),(12,'二班',1,2)",longName);
        try (XSSFWorkbook wb = workbook(person(1,"甲",11),person(2,"乙",12))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell heading = headingCell(sheet,longName+"(在位1人)");
            assertTrue(heading.getCellStyle().getWrapText());
            assertTrue(sheet.getRow(heading.getRowIndex()).getHeightInPoints() >= 36);
            assertEquals("甲",cell(sheet,nameStartRow(sheet,heading),0));
            assertEquals("乙",cell(sheet,nameStartRow(sheet,headingCell(sheet,"二班(在位1人)")),1));
        }
    }

    @Test void exceedingExcelColumnLimitIsRejectedBeforeWorkbookAllocation() {
        List<Personnel> rows = new ArrayList<Personnel>();
        for (int i=0; i<16385; i++) rows.add(person(i+1,"人员"+i,100000L+i));
        rows(rows);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.build(Collections.emptyMap()));
        assertTrue(error.getMessage().contains("16384"));
        assertTrue(error.getMessage().contains("缩小筛选范围"));
    }

    @Test void emptySelectionHasNoInventedDirectPersonnelColumnAndKeepsPrintSettings() throws Exception {
        try (XSSFWorkbook wb = workbook()) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("在位人员(共0人)",cell(sheet,3,0));
            assertEquals("无",cell(sheet,4,0));
            assertTrue(sheet.getPrintSetup().getLandscape());
            assertEquals(9,sheet.getPrintSetup().getPaperSize());
            assertEquals(1,sheet.getPrintSetup().getFitWidth());
            assertEquals(1,sheet.getPrintSetup().getFitHeight());
        }
    }
}
