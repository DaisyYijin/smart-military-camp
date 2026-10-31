package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.config.GlobalExceptionHandler;
import com.szjx.personnel.server.service.ModuleExcelService;
import com.szjx.personnel.server.service.PersonnelFullExcel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ModuleExcelPermissionTest {
    private ModuleExcelService service;
    private PersonnelFullExcel personnel;
    private MockMvc mvc;

    @BeforeEach void setup() {
        service = mock(ModuleExcelService.class);
        personnel = mock(PersonnelFullExcel.class);
        when(service.supports(anyString())).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new ModuleExcelController(service, personnel))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void forbiddenExportsUse403InsteadOf500Or400() throws Exception {
        when(service.export(eq("equipment"), anyList())).thenThrow(new ForbiddenException("禁止跨域"));
        when(personnel.export(anyList())).thenThrow(new ForbiddenException("禁止跨域"));
        mvc.perform(get("/api/equipment/export").param("ids", "1,2")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/api/personnel/export").param("ids", "1,2")).andExpect(status().isForbidden());
    }

    @Test void dictionaryImportForbiddenExceptionReachesGlobalHandler() throws Exception {
        when(service.importExcel(eq("dict"), any())).thenThrow(new ForbiddenException("仅管理员"));
        mvc.perform(multipart("/api/dict/import-excel").file(new MockMultipartFile("file", "dict.xlsx", "application/octet-stream", new byte[]{1})))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }

    @Test void invalidIdsCannotBecomeAnUnfilteredPlanExport() throws Exception {
        for (String ids : new String[]{"invalid", "1,invalid", "0", "-1", "1,"}) {
            mvc.perform(get("/api/leave-plan/export").param("ids", ids)).andExpect(status().isBadRequest());
        }
        verify(service, never()).export(anyString(), anyList());
    }
}
