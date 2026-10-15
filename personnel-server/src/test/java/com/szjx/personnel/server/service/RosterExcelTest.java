package com.szjx.personnel.server.service;

import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.model.Personnel;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 花名册 Excel 生成:验证勾选字段、部门分组与默认字段回退 */
public class RosterExcelTest {

    @Test
    public void buildsRosterWithSelectedFields() throws Exception {
        PersonnelService personnelService = mock(PersonnelService.class);
        DepartmentService departmentService = mock(DepartmentService.class);

        Department hq = new Department();
        hq.setId(1L);
        hq.setName("营部");
        hq.setParentId(0L);
        Department l1 = new Department();
        l1.setId(2L);
        l1.setName("一连");
        l1.setParentId(0L);
        Department s1 = new Department();
        s1.setId(4L);
        s1.setName("一班");
        s1.setParentId(2L);
        s1.setChildren(new ArrayList<Department>());
        l1.setChildren(new ArrayList<Department>(Arrays.asList(s1)));
        hq.setChildren(new ArrayList<Department>());
        when(departmentService.tree()).thenReturn(Arrays.asList(hq, l1));

        Personnel p1 = new Personnel();
        p1.setId(1L);
        p1.setName("王建国");
        p1.setGender("男");
        p1.setIdCard("11010119850312013X");
        p1.setDepartmentId(1L);
        p1.setDepartmentName("营部");
        p1.setStatus("在位");
        p1.setLocationPoint("机关");
        Personnel p2 = new Personnel();
        p2.setId(2L);
        p2.setName("孙德胜");
        p2.setGender("男");
        p2.setDepartmentId(4L);
        p2.setDepartmentName("一班");
        p2.setStatus("在位");
        when(personnelService.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<Personnel>(Arrays.asList(p1, p2)));

        RosterExcelService service = new RosterExcelService(personnelService, departmentService, mock(JdbcTemplate.class));

        Map<String, String> q = new HashMap<String, String>();
        q.put("fields", "name,idCard,department,locationPoint");
        byte[] data = service.build(q);
        assertNotNull(data);
        assertTrue(data.length > 2000);
        assertEquals(0x50, data[0] & 0xFF);
        assertEquals(0x4B, data[1] & 0xFF);
    }

    @Test
    public void emptyFieldsFallBackToDefault() throws Exception {
        PersonnelService personnelService = mock(PersonnelService.class);
        DepartmentService departmentService = mock(DepartmentService.class);
        when(departmentService.tree()).thenReturn(new ArrayList<Department>());
        when(personnelService.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<Personnel>());

        RosterExcelService service = new RosterExcelService(personnelService, departmentService, mock(JdbcTemplate.class));
        byte[] data = service.build(new HashMap<String, String>()); // 未传 fields → 默认列
        assertNotNull(data);
        assertTrue(data.length > 1500);
        assertEquals(0x50, data[0] & 0xFF);
    }
}
