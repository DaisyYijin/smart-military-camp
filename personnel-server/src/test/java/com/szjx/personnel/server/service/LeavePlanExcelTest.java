package com.szjx.personnel.server.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LeavePlanExcelTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private ModuleExcelService service;

    @BeforeEach
    void setUp() {
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, id_card TEXT)");
        jdbc.execute("CREATE TABLE leave_plan(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER NOT NULL, year TEXT DEFAULT '', leave_type TEXT DEFAULT '', start_date TEXT DEFAULT '', end_date TEXT DEFAULT '', days REAL DEFAULT 0, status TEXT DEFAULT '计划中', remark TEXT DEFAULT '', created_at TEXT DEFAULT '', updated_at TEXT DEFAULT '')");
        jdbc.update("INSERT INTO personnel(id,name,id_card) VALUES(1,?,?)", "张三", "110105199001011234");
        service = new ModuleExcelService(jdbc, null, null);
    }

    @Test
    void templateContainsIdentityAndPlanColumns() throws Exception {
        assertTrue(service.supports("leave-plan"));
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.template("leave-plan")))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            assertEquals("人员姓名", header.getCell(0).getStringCellValue());
            assertEquals("身份证号", header.getCell(1).getStringCellValue());
            assertEquals("年度", header.getCell(2).getStringCellValue());
            assertEquals("计划开始日期", header.getCell(4).getStringCellValue());
        }
    }

    @Test
    void importAppendsMatchingPlanWithoutUpdatingOldPlanAndReportsBadRows() throws Exception {
        byte[] first = workbook(new String[][]{
                {"人员姓名", "身份证号", "年度", "休假类型", "计划开始日期", "计划结束日期", "天数", "备注"},
                {"张三", "110105199001011234", "2026", "年假", "2026-06-01", "2026-06-05", "5", "初始"},
                {"张三", "110105199001011234", "2026", "年假", "2026-06-08", "2026-06-06", "2", "日期错误"}
        });
        Map<String, Object> added = service.importExcel("leave-plan", new ByteArrayInputStream(first));
        assertEquals(1, added.get("added"));
        assertEquals(1, added.get("failed"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM leave_plan", Integer.class));

        byte[] update = workbook(new String[][]{
                {"人员姓名", "身份证号", "年度", "休假类型", "计划开始日期", "计划结束日期", "天数", "备注"},
                {"张三", "110105199001011234", "2026", "年假", "2026-06-01", "2026-06-05", "5", "更新"}
        });
        Map<String, Object> updated = service.importExcel("leave-plan", new ByteArrayInputStream(update));
        assertEquals(1, updated.get("added"), updated.toString());
        assertEquals(0, updated.get("updated"), updated.toString());
        assertEquals(0, updated.get("failed"), updated.toString());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM leave_plan", Integer.class));
        assertEquals("初始", jdbc.queryForObject("SELECT remark FROM leave_plan WHERE id=1", String.class));
        assertEquals("更新", jdbc.queryForObject("SELECT remark FROM leave_plan WHERE id=2", String.class));
    }

    @Test
    void exportIncludesAllPlansAndIdentityWhenNoIdsProvided() throws Exception {
        jdbc.update("INSERT INTO leave_plan(personnel_id,year,leave_type,start_date,end_date,days,remark) VALUES(1,'2026','年假','2026-06-01','2026-06-05',5,'备注')");
        jdbc.update("INSERT INTO leave_plan(personnel_id,year,leave_type,start_date,end_date,days,remark) VALUES(1,'2025','事假','2025-06-01','2025-06-02',2,'其他')");
        byte[] bytes = service.export("leave-plan", Collections.<Long>emptyList());
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("张三", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("110105199001011234", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("2026", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals("2025", sheet.getRow(2).getCell(2).getStringCellValue());
            assertEquals(3, sheet.getPhysicalNumberOfRows());
        }
    }

    private static byte[] workbook(String[][] values) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("休假计划");
            for (int r = 0; r < values.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < values[r].length; c++) row.createCell(c).setCellValue(values[r][c]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
