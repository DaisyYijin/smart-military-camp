package com.szjx.personnel.server.service;

import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 点名册身份标志:党员☭/团员✪前缀、驾驶员⊛后缀及按需开关。 */
public class RollCallMarksTest {
    private final List<JdbcTemplate> databases = new ArrayList<JdbcTemplate>();
    private JdbcTemplate newJdbc() {
        JdbcTemplate jdbc = RollCallExcelTest.metadataJdbc(); databases.add(jdbc); return jdbc;
    }
    @AfterEach void closeDatabases() { for (JdbcTemplate jdbc : databases) RollCallExcelTest.closeJdbc(jdbc); }

    private static Department dept(long id, String name, Long parent) {
        Department d = new Department();
        d.setId(id);
        d.setName(name);
        d.setParentId(parent);
        return d;
    }

    private static Personnel person(long id, String name, String status, Long deptId, String political) {
        Personnel p = new Personnel();
        p.setId(id);
        p.setName(name);
        p.setStatus(status);
        p.setDepartmentId(deptId);
        p.setPoliticalStatus(political);
        return p;
    }

    private static List<String> allCells(byte[] data) throws Exception {
        List<String> values = new ArrayList<String>();
        XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(data));
        try {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    String v = cell.toString();
                    if (v != null && !v.isEmpty()) values.add(v);
                }
            }
        } finally {
            wb.close();
        }
        return values;
    }

    private RollCallExcelService serviceWithPeople(JdbcTemplate jdbc, List<Personnel> rows,
                                                   Map<String, Object> leaveReturn, List<Map<String, Object>> driverRows) throws Exception {
        PersonnelService personnelService = mock(PersonnelService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        Department hq = dept(1, "营部", 0L);
        Department l1 = dept(2, "一连", 0L);
        Department l1s1 = dept(4, "一班", 2L);
        l1.setChildren(new ArrayList<Department>(Collections.singletonList(l1s1)));
        RollCallExcelTest.metadata(jdbc, hq, l1);
        when(personnelService.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(rows);
        org.mockito.Mockito.doReturn(leaveReturn == null ? Collections.<Map<String, Object>>emptyList() : Collections.singletonList(leaveReturn))
                .when(jdbc).queryForList(contains("leave_record"));
        org.mockito.Mockito.doReturn(driverRows).when(jdbc).queryForList(contains("driver_license"));
        return new RollCallExcelService(personnelService, departmentService, jdbc);
    }

    @Test
    public void marksAddPrefixSuffixAndLegendOnlyWhenRequested() throws Exception {
        JdbcTemplate jdbc = newJdbc();
        List<Personnel> rows = Arrays.asList(
                person(1, "王建国", "在位", 1L, "中共党员"),
                person(2, "李卫东", "在位", 1L, "共青团员"),
                person(3, "孙德胜", "在位", 4L, "群众"),
                person(4, "周小勇", "在位", 4L, "中共党员"),
                person(5, "郑国栋", "休假", 1L, "共青团员"));
        Map<String, Object> leaveReturn = new HashMap<String, Object>();
        leaveReturn.put("personnel_id", 5L);
        leaveReturn.put("ed", "2026-10-22");
        List<Map<String, Object>> drivers = new ArrayList<Map<String, Object>>();
        Map<String, Object> d3 = new HashMap<String, Object>(); d3.put("personnel_id", 3L); drivers.add(d3);
        Map<String, Object> d4 = new HashMap<String, Object>(); d4.put("personnel_id", 4L); drivers.add(d4);
        RollCallExcelService service = serviceWithPeople(jdbc, rows, leaveReturn, drivers);

        Map<String, String> q = new HashMap<String, String>();
        q.put("marks", "party,league,driver");
        List<String> cells = allCells(service.build(q));

        assertTrue(cells.contains("☭王建国"), "党员姓名前加党徽");
        assertTrue(cells.contains("✪李卫东"), "团员姓名前加团徽");
        assertTrue(cells.contains("孙德胜⊛"), "驾驶员姓名后加方向盘标志");
        assertTrue(cells.contains("☭周小勇⊛"), "党员兼驾驶员两个标志都加");
        assertTrue(cells.contains("✪郑国栋\n归队:2026年10月22日"), "不在位团员同样带标志并保留归队时间");
        assertFalse(cells.toString().contains("标志说明"), "不打印图例行");
        // 标志黑白显示:姓名单元格不携带富文本染色或加粗字体
        byte[] again = service.build(q);
        XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(again));
        try {
            boolean anyRich = false, anyBold = false;
            for (org.apache.poi.ss.usermodel.Row row : wb.getSheetAt(0)) {
                for (org.apache.poi.ss.usermodel.Cell cell : row) {
                    if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                            && cell.getStringCellValue().matches("[☭✪].*|.*⊛")) {
                        org.apache.poi.xssf.usermodel.XSSFRichTextString rich =
                                (org.apache.poi.xssf.usermodel.XSSFRichTextString) cell.getRichStringCellValue();
                        if (rich.numFormattingRuns() > 0) {
                            anyRich = true;
                        }
                        if (wb.getFontAt(cell.getCellStyle().getFontIndexAsInt()).getBold()) {
                            anyBold = true;
                        }
                    }
                }
            }
            assertFalse(anyRich, "标志不使用富文本染色");
            assertFalse(anyBold, "标志单元格不加粗");
        } finally {
            wb.close();
        }
        verify(jdbc, times(2)).queryForList(contains("driver_license"));

        // 不请求标志时:保持旧版输出,不查询驾驶员、无图例
        JdbcTemplate jdbc2 = newJdbc();
        RollCallExcelService plain = serviceWithPeople(jdbc2, rows, leaveReturn, drivers);
        List<String> plainCells = allCells(plain.build(new HashMap<String, String>()));
        assertTrue(plainCells.contains("王建国"));
        assertTrue(plainCells.contains("郑国栋\n归队:2026年10月22日"));
        for (String cell : plainCells) {
            assertFalse(cell.contains("☭") || cell.contains("✪") || cell.contains("⊛"), "无标志: " + cell);
        }
        verify(jdbc2, times(0)).queryForList(contains("driver_license"));
    }

    @Test
    public void partialMarksAndUnknownValuesAreIgnored() throws Exception {
        JdbcTemplate jdbc = newJdbc();
        List<Personnel> rows = Arrays.asList(
                person(1, "王建国", "在位", 1L, "中共党员"),
                person(2, "李卫东", "在位", 1L, "共青团员"),
                person(3, "孙德胜", "在位", 4L, "群众"));
        List<Map<String, Object>> drivers = new ArrayList<Map<String, Object>>();
        Map<String, Object> d1 = new HashMap<String, Object>(); d1.put("personnel_id", 1L); drivers.add(d1);
        RollCallExcelService service = serviceWithPeople(jdbc, rows, null, drivers);

        Map<String, String> q = new HashMap<String, String>();
        q.put("marks", "driver,黑客,party-off"); // 未知项忽略;仅 driver 生效
        List<String> cells = allCells(service.build(q));
        assertTrue(cells.contains("王建国⊛"), "仅驾驶员标志:党员身份不启用党徽,姓名后照常加方向盘标志");
        assertTrue(cells.contains("孙德胜"), "非驾驶员无后缀");
        assertFalse(cells.toString().contains("标志说明"), "部分启用时同样不打印图例");
        for (String cell : cells) {
            assertFalse(cell.contains("☭") || cell.contains("★"), "未启用的标志不应出现");
        }
    }
}
