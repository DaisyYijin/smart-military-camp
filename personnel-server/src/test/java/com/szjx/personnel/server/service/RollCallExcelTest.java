package com.szjx.personnel.server.service;

import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.model.Personnel;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 点名册 Excel 生成:隔离验证两级分组矩阵、不在位分组与归队时间注入能正常产出 xlsx */
public class RollCallExcelTest {

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
    public void buildsExcelWithMatrixLayout() throws Exception {
        PersonnelService personnelService = mock(PersonnelService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        // 部门树:营部 / 一连(连部、一班) / 二连(一班)
        Department hq = dept(1, "营部", 0L);
        Department l1 = dept(2, "一连", 0L);
        Department l1hq = dept(3, "连部", 2L);
        Department l1s1 = dept(4, "一班", 2L);
        Department l2 = dept(5, "二连", 0L);
        Department l2s1 = dept(6, "一班", 5L);
        l1.setChildren(new ArrayList<Department>(Arrays.asList(l1hq, l1s1)));
        l2.setChildren(new ArrayList<Department>(Collections.singletonList(l2s1)));
        when(departmentService.tree()).thenReturn(Arrays.asList(hq, l1, l2));

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
        when(jdbc.queryForList(anyString())).thenReturn(Collections.singletonList(ret));

        RollCallExcelService service = new RollCallExcelService(personnelService, departmentService, jdbc);
        byte[] data = service.build(new HashMap<String, String>());

        assertNotNull(data);
        assertTrue(data.length > 2000, "xlsx 应为有效文件,实际 " + data.length + " 字节");
        assertEquals(0x50, data[0] & 0xFF, "zip 头 PK");
        assertEquals(0x4B, data[1] & 0xFF, "zip 头 PK");
    }

    @Test
    public void emptyDataStillBuilds() throws Exception {
        PersonnelService personnelService = mock(PersonnelService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(departmentService.tree()).thenReturn(new ArrayList<Department>());
        when(personnelService.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<Personnel>());
        when(jdbc.queryForList(anyString())).thenReturn(new ArrayList<Map<String, Object>>());

        RollCallExcelService service = new RollCallExcelService(personnelService, departmentService, jdbc);
        byte[] data = service.build(new HashMap<String, String>());
        assertNotNull(data);
        assertTrue(data.length > 1000);
        assertEquals(0x50, data[0] & 0xFF);
    }
}
