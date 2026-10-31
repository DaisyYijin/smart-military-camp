package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.service.BackupService;
import com.szjx.personnel.server.service.TokenManager;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackupControllerSessionTest {
    private Map<String, Object> request() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "backup.zip");
        body.put("modules", Arrays.asList("dict"));
        return body;
    }

    @Test
    void itemLoansModuleHasUserFacingLabelWithoutPhotoScope() {
        BackupController controller = new BackupController(mock(BackupService.class));
        java.util.List<?> modules = (java.util.List<?>) controller.modules().getData();
        Map<?, ?> itemLoans = modules.stream().map(row -> (Map<?, ?>) row)
                .filter(row -> "item-loans".equals(row.get("key"))).findFirst()
                .orElseThrow(() -> new AssertionError("物品借还备份模块未注册"));
        assertEquals("物品借还", itemLoans.get("label"));
        assertEquals(Boolean.FALSE, itemLoans.get("includesPhotos"));
    }

    @Test
    void personnelPhotoScopeExplainsReferencedTrashAndMultipartLimitMessageIsExplicit() {
        BackupController controller = new BackupController(mock(BackupService.class));
        java.util.List<?> modules = (java.util.List<?>) controller.modules().getData();
        Map<?, ?> personnel = modules.stream().map(row -> (Map<?, ?>) row)
                .filter(row -> "personnel".equals(row.get("key"))).findFirst().get();
        assertTrue(String.valueOf(personnel.get("photoScope")).contains("删除撤回记录引用"));
        assertTrue(String.valueOf(personnel.get("photoScope")).contains("不含资产目录"));
        org.springframework.http.ResponseEntity<ApiResponse> response = new com.szjx.personnel.server.config.GlobalExceptionHandler()
                .handleUploadTooLarge(new org.springframework.web.multipart.MaxUploadSizeExceededException(BackupService.MAX_UPLOAD_BYTES));
        assertEquals(400, response.getStatusCodeValue());
        assertEquals("上传文件过大,单个文件不能超过100MB", response.getBody().getMessage());
    }

    @Test
    void successfulSecurityRestoreRevokesSessionsAndMarksRelogin() {
        BackupService service = mock(BackupService.class);
        TokenManager tokens = new TokenManager();
        String token = tokens.create("root");
        long version = tokens.version("root");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("securityStateRestored", true);
        when(service.restore("backup.zip", Arrays.asList("dict"))).thenReturn(result);
        BackupController controller = new BackupController(service);
        controller.setTokenManager(tokens);
        ApiResponse response = controller.restore(request());
        assertNull(tokens.validate(token));
        assertNotEquals(version, tokens.version("root"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) response.getData()).get("requiresRelogin"));
    }

    @Test
    void failedRestoreDoesNotRevokeSessions() {
        BackupService service = mock(BackupService.class);
        TokenManager tokens = new TokenManager();
        String token = tokens.create("root");
        when(service.restore("backup.zip", Arrays.asList("dict"))).thenThrow(new IllegalStateException("rollback"));
        BackupController controller = new BackupController(service);
        controller.setTokenManager(tokens);
        assertThrows(IllegalStateException.class, () -> controller.restore(request()));
        assertEquals("root", tokens.validate(token));
    }

    @Test
    void legacyAccountSkipKeepsSessionsAndWarnings() {
        BackupService service = mock(BackupService.class);
        TokenManager tokens = new TokenManager();
        String token = tokens.create("root");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("securityStateRestored", false);
        result.put("warnings", Arrays.asList("旧备份未包含账号单位授权表,跳过账号恢复"));
        when(service.restore("backup.zip", Arrays.asList("dict"))).thenReturn(result);
        BackupController controller = new BackupController(service);
        controller.setTokenManager(tokens);
        ApiResponse response = controller.restore(request());
        assertEquals("root", tokens.validate(token));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) response.getData()).get("requiresRelogin"));
        assertEquals(result.get("warnings"), ((Map<?, ?>) response.getData()).get("warnings"));
    }
}
