package com.szjx.personnel.server.service;

import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.model.Personnel;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.junit.jupiter.api.AfterEach;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Deque;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 点名册 Excel 生成:真实临时 SQLite 单位元数据、POI 横向层级表头及不在位归队时间验证。 */
public class RollCallExcelTest {
    private final List<JdbcTemplate> databases = new ArrayList<JdbcTemplate>();

    static JdbcTemplate metadataJdbc() {
        SingleConnectionDataSource source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        JdbcTemplate jdbc = org.mockito.Mockito.spy(new JdbcTemplate(source));
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT,parent_id INTEGER,sort INTEGER)");
        org.mockito.Mockito.doReturn(Collections.emptyList()).when(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("leave_record"));
        org.mockito.Mockito.doReturn(Collections.emptyList()).when(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("driver_license"));
        return jdbc;
    }

    static void closeJdbc(JdbcTemplate jdbc) {
        ((SingleConnectionDataSource) jdbc.getDataSource()).destroy();
    }

    private JdbcTemplate newJdbc() {
        JdbcTemplate jdbc = metadataJdbc(); databases.add(jdbc); return jdbc;
    }

    @AfterEach void closeDatabases() { for (JdbcTemplate jdbc : databases) closeJdbc(jdbc); }

    static void metadata(JdbcTemplate jdbc, Department... roots) {
        jdbc.update("DELETE FROM department");
        Deque<Department> pending = new ArrayDeque<Department>(Arrays.asList(roots));
        while (!pending.isEmpty()) {
            Department d = pending.removeFirst();
            jdbc.update("INSERT INTO department(id,name,parent_id,sort) VALUES(?,?,?,?)",
                    d.getId(), d.getName(), d.getParentId(), d.getSort() == null ? 0 : d.getSort());
            if (d.getChildren() != null) for (Department child : d.getChildren()) {
                child.setParentId(d.getId()); pending.addLast(child);
            }
        }
    }

    static String cell(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row); Cell c = r == null ? null : r.getCell(col);
        return c == null ? "" : new DataFormatter().formatCellValue(c);
    }

    static Cell headingCell(Sheet sheet, String title) { return headingCell(sheet, title, 0); }

    static Cell headingCell(Sheet sheet, String title, int occurrence) {
        for (Row row : sheet) for (Cell c : row) if (cell(sheet, row.getRowNum(), c.getColumnIndex()).equals(title)) {
            if (occurrence-- == 0) return c;
        }
        fail("Missing heading: " + title); return null;
    }

    static int headingRow(Sheet sheet, String title) { return headingCell(sheet, title).getRowIndex(); }

    static CellRangeAddress headerRegion(Sheet sheet, Cell heading) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.isInRange(heading.getRowIndex(), heading.getColumnIndex())) return region;
        }
        return new CellRangeAddress(heading.getRowIndex(), heading.getRowIndex(), heading.getColumnIndex(), heading.getColumnIndex());
    }

    static int nameStartRow(Sheet sheet, Cell heading) {
        CellRangeAddress region = headerRegion(sheet, heading);
        assertEquals(heading.getRowIndex(), region.getFirstRow(), "单位表头只允许本行横向合并");
        assertEquals(heading.getRowIndex(), region.getLastRow(), "单位表头只允许本行横向合并");
        return heading.getRowIndex() + 1;
    }

    static int nameStartRow(Sheet sheet) {
        int bottom = -1;
        for (Row row : sheet) for (Cell c : row) {
            String value = cell(sheet, row.getRowNum(), c.getColumnIndex());
            if (value.contains("(在位") && value.endsWith("人)")) {
                bottom = Math.max(bottom, headerRegion(sheet, c).getLastRow());
            }
        }
        assertTrue(bottom >= 0, "Missing present matrix headers"); return bottom + 1;
    }

    private static Department dept(long id, String name, Long parent) {
        Department d = new Department();
        d.setId(id);
        d.setName(name);
        d.setParentId(parent);
        return d;
    }

    private static Personnel person(long id, String name, String status, Long deptId, String deptName) {
        Personnel p = new Personnel();
        p.setId(id);
        p.setName(name);
        p.setStatus(status);
        p.setDepartmentId(deptId);
        p.setDepartmentName(deptName);
        return p;
    }

    @Test
    public void buildsExcelWithHorizontalHierarchyMatrixAndReturnDates() throws Exception {
        PersonnelService personnelService = mock(PersonnelService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        JdbcTemplate jdbc = newJdbc();

        // 单位树:营部 / 一连(连部、一班) / 二连(一班)
        Department hq = dept(1, "营部", 0L);
        Department l1 = dept(2, "一连", 0L);
        Department l1hq = dept(3, "连部", 2L);
        Department l1s1 = dept(4, "一班", 2L);
        Department l2 = dept(5, "二连", 0L);
        Department l2s1 = dept(6, "一班", 5L);
        l1.setChildren(new ArrayList<Department>(Arrays.asList(l1hq, l1s1)));
        l2.setChildren(new ArrayList<Department>(Collections.singletonList(l2s1)));
        metadata(jdbc, hq, l1, l2);

        List<Personnel> rows = new ArrayList<Personnel>();
        rows.add(person(1, "王建国", "在位", 1L, "营部"));
        rows.add(person(2, "李卫东", "在位", 1L, "营部"));
        rows.add(person(3, "陈志强", "在位", 3L, "连部"));
        rows.add(person(4, "孙德胜", "在位", 4L, "一班"));
        rows.add(person(5, "周小勇", "在位", 4L, "一班"));
        rows.add(person(6, "马啸天", "在位", 6L, "一班"));
        rows.add(person(7, "郑国栋", "休假", 3L, "连部"));
        rows.add(person(8, "何建军", "培训", 1L, "营部"));
        when(personnelService.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(rows);

        Map<String, Object> ret = new HashMap<String, Object>();
        ret.put("personnel_id", 7L);
        ret.put("ed", "2026-10-22");
        org.mockito.Mockito.doReturn(Collections.singletonList(ret)).when(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("leave_record"));

        RollCallExcelService service = new RollCallExcelService(personnelService, departmentService, jdbc);
        byte[] data = service.build(new HashMap<String, String>());

        assertNotNull(data);
        assertTrue(data.length > 2000, "xlsx 应为有效文件,实际 " + data.length + " 字节");
        assertEquals(0x50, data[0] & 0xFF, "zip 头 PK");
        assertEquals(0x4B, data[1] & 0xFF, "zip 头 PK");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell hqHead = headingCell(sheet, "营部(在位2人)"), l1Head = headingCell(sheet, "一连(在位3人)");
            Cell l1hqHead = headingCell(sheet, "连部(在位1人)"), l1s1Head = headingCell(sheet, "一班(在位2人)");
            Cell l2Head = headingCell(sheet, "二连(在位1人)"), l2s1Head = headingCell(sheet, "一班(在位1人)");
            assertEquals(hqHead.getRowIndex(), l1Head.getRowIndex()); assertEquals(l1Head.getRowIndex(), l2Head.getRowIndex());
            assertTrue(hqHead.getColumnIndex() < l1Head.getColumnIndex() && l1Head.getColumnIndex() < l2Head.getColumnIndex());
            assertEquals(l1hqHead.getRowIndex(), l1s1Head.getRowIndex());
            assertTrue(l1hqHead.getColumnIndex() < l1s1Head.getColumnIndex());
            assertEquals("B5:C5", headerRegion(sheet, l1Head).formatAsString());
            assertEquals("A5", headerRegion(sheet, hqHead).formatAsString());
            int hqNames = nameStartRow(sheet, hqHead), names = nameStartRow(sheet, l1hqHead);
            assertEquals(5, hqNames); assertEquals(6, names);
            assertEquals("王建国", cell(sheet, hqNames, hqHead.getColumnIndex()));
            assertEquals("李卫东", cell(sheet, hqNames + 1, hqHead.getColumnIndex()));
            assertEquals("陈志强", cell(sheet, nameStartRow(sheet, l1hqHead), l1hqHead.getColumnIndex()));
            assertEquals("孙德胜", cell(sheet, nameStartRow(sheet, l1s1Head), l1s1Head.getColumnIndex()));
            assertEquals("周小勇", cell(sheet, nameStartRow(sheet, l1s1Head) + 1, l1s1Head.getColumnIndex()));
            assertEquals("马啸天", cell(sheet, nameStartRow(sheet, l2s1Head), l2s1Head.getColumnIndex()));
            boolean returned = false;
            for (Row row : sheet) for (Cell c : row) if (c.toString().equals("郑国栋\n归队:2026年10月22日")) returned = true;
            assertTrue(returned);
            assertTrue(cell(sheet, sheet.getLastRowNum(), 0).startsWith("应到:8人    在位:6人    不在位:2人"));
        }
    }

    @Test
    public void emptyDataStillBuilds() throws Exception {
        PersonnelService personnelService = mock(PersonnelService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        JdbcTemplate jdbc = newJdbc();
        when(personnelService.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<Personnel>());
        org.mockito.Mockito.doReturn(Collections.emptyList()).when(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("leave_record"));

        RollCallExcelService service = new RollCallExcelService(personnelService, departmentService, jdbc);
        byte[] data = service.build(new HashMap<String, String>());
        assertNotNull(data);
        assertTrue(data.length > 1000);
        assertEquals(0x50, data[0] & 0xFF);
    }
}
