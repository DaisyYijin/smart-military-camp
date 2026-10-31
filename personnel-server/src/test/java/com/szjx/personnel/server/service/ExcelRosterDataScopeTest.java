package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real temporary SQLite + POI, with an intentionally unfiltered personnel-service test double. */
class ExcelRosterDataScopeTest {
    @TempDir Path temp;
    @AfterEach void clearScope() { DataScope.clear(); }

    @Test void rostersAndReturnDatesNeverIncludeForeignPeopleAndEmptyScopeIsEmpty() throws Exception {
        JdbcTemplate jdbc = spy(PersonnelIntegrityTest.temporaryDatabase(temp));
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY,department_id INTEGER)");
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT,parent_id INTEGER DEFAULT 0,sort INTEGER DEFAULT 0)");
        jdbc.update("INSERT INTO department(id,name) VALUES(1,'本域单位'),(2,'外域单位')");
        jdbc.execute("CREATE TABLE leave_record(personnel_id INTEGER,end_date TEXT)");
        jdbc.update("INSERT INTO personnel VALUES(1,1),(2,2),(3,NULL)");
        String end = java.time.LocalDate.now().plusDays(10).toString();
        jdbc.update("INSERT INTO leave_record VALUES(1,?),(2,'2099-12-31')", end);
        Personnel visible = person(1L, "本域姓名", 1L);
        Personnel foreign = person(2L, "外域秘密姓名", 2L);
        Personnel unassigned = person(3L, "未分配秘密姓名", null);
        PersonnelService people = mock(PersonnelService.class);
        when(people.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(visible, foreign, unassigned));
        DepartmentService departments = mock(DepartmentService.class);
        when(departments.tree()).thenReturn(Collections.emptyList());
        RosterExcelService roster = new RosterExcelService(people, departments);
        RollCallExcelService roll = new RollCallExcelService(people, departments, jdbc);
        DataScope.set(new DataScope.Context(10L, "sub", false, Collections.singleton(1L)));
        String rosterText = text(roster.build(Collections.emptyMap()));
        String rollText = text(roll.build(Collections.emptyMap()));
        assertTrue(rosterText.contains("本域姓名"));
        assertTrue(rosterText.contains("合计:1人"));
        assertTrue(rollText.contains("本域姓名"));
        assertTrue(rollText.contains("归队:"));
        for (String exported : Arrays.asList(rosterText, rollText)) {
            assertFalse(exported.contains("秘密姓名"));
            assertFalse(exported.contains("2099"));
        }
        verify(jdbc).queryForList(contains("WHERE " + DataScope.personSql("personnel_id") + " AND"));
        DataScope.set(new DataScope.Context(10L, "empty", false, Collections.emptySet()));
        assertFalse(text(roster.build(Collections.emptyMap())).contains("本域姓名"));
        assertFalse(text(roll.build(Collections.emptyMap())).contains("本域姓名"));
        DataScope.set(new DataScope.Context(1L, "root", true, Collections.emptySet()));
        assertTrue(text(roster.build(Collections.emptyMap())).contains("外域秘密姓名"));
        assertTrue(text(roll.build(Collections.emptyMap())).contains("外域秘密姓名"));
    }

    private static Personnel person(Long id, String name, Long department) {
        Personnel p = new Personnel(); p.setId(id); p.setName(name); p.setDepartmentId(department); p.setStatus("休假");
        return p;
    }

    private static String text(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : wb) for (Row row : sheet) for (Cell cell : row) result.append(formatter.formatCellValue(cell)).append('\n');
        }
        return result.toString();
    }
}
