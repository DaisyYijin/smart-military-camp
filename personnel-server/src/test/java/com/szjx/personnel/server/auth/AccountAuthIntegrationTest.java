package com.szjx.personnel.server.auth;

import com.szjx.personnel.server.config.GlobalExceptionHandler;
import com.szjx.personnel.server.config.SchemaInitializer;
import com.szjx.personnel.server.controller.AccountController;
import com.szjx.personnel.server.controller.AuthController;
import com.szjx.personnel.server.controller.BackupController;
import org.springframework.mock.web.MockMultipartFile;
import com.szjx.personnel.server.dao.AdminUserDao;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JDBC scopes + real MVC exception resolution; no network listener or application startup. */
class AccountAuthIntegrationTest {
    @TempDir Path temporary;
    JdbcTemplate jdbc;
    AdminUserDao dao;
    AccountService accounts;
    TokenManager tokens;
    AuthInterceptor interceptor;
    MockMvc mvc;
    String rootToken, childToken;
    long rootId, childId;

    @RestController
    static class ProbeController {
        @RequestMapping("/api/**")
        public ApiResponse probe() {
            if (DataScope.get() == null) throw new IllegalStateException("scope absent");
            return ApiResponse.ok(DataScope.get().departmentIds);
        }
        @GetMapping("/api/personnel/999")
        public ApiResponse denied() { DataScope.requireDepartment(4L); return ApiResponse.ok(); }
        @GetMapping("/api/personnel/998")
        public ApiResponse failure() { throw new IllegalArgumentException("test bad request"); }
    }

    @BeforeEach void setup() {
        DataScope.clear();
        jdbc = AccountServiceTest.database(temporary.resolve("auth.sqlite"));
        AccountServiceTest.legacySchema(jdbc);
        dao = new AdminUserDao(jdbc);
        String hash = PasswordUtil.hash("secret123");
        dao.insertAdmin("A", hash, "管理员");
        new SchemaInitializer(jdbc, dao).migrateAccounts();
        rootId = dao.findByUsername("A").id;
        childId = dao.insertChild(rootId, "B", hash, "子账号", true);
        dao.replaceDepartments(childId, Collections.singleton(2L));
        tokens = new TokenManager();
        accounts = AccountServiceTest.transactional(new AccountService(dao, jdbc, tokens), jdbc);
        interceptor = new AuthInterceptor(tokens, accounts);
        AuthController auth = new AuthController(dao, tokens, mock(LoginGuard.class), mock(LogService.class), accounts,
                new OpLogInterceptor(mock(LogService.class), mock(org.springframework.jdbc.core.JdbcTemplate.class)));
        ReflectionTestUtils.setField(auth, "tokenExpireHours", 24);
        mvc = MockMvcBuilders.standaloneSetup(auth, new AccountController(accounts), new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new MappedInterceptor(new String[]{"/api/**"},
                        new String[]{"/api/auth/login", "/api/ping", "/api/setting/public"}, interceptor)).build();
        rootToken = tokens.create("A");
        childToken = tokens.create("B");
    }

    @AfterEach void cleanup() { DataScope.clear(); }
    MockHttpServletRequestBuilder request(String route, String token) {
        String[] parts = route.split(" ", 2);
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(parts[0]), parts[1]).header(AuthInterceptor.HEADER_TOKEN, token);
    }

    @Test void loginAndMeExposeConsistentProfileAndNeverAnyHash() throws Exception {
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"B\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.userId").value(childId))
                .andExpect(jsonPath("$.data.username").value("B"))
                .andExpect(jsonPath("$.data.displayName").value("子账号"))
                .andExpect(jsonPath("$.data.isAdmin").value(false))
                .andExpect(jsonPath("$.data.departmentIds[0]").value(2))
                .andExpect(jsonPath("$.data.departmentNames[0]").value("一部二处"))
                .andExpect(jsonPath("$.data.parentId").value(rootId))
                .andReturn().getResponse().getContentAsString();
        assertFalse(login.contains("password"));
        mvc.perform(get("/api/auth/me").header(AuthInterceptor.HEADER_TOKEN, childToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(childId))
                .andExpect(jsonPath("$.data.departmentIds.length()").value(1));
        mvc.perform(get("/api/auth/me").header(AuthInterceptor.HEADER_TOKEN, rootToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isAdmin").value(true))
                .andExpect(jsonPath("$.data.parentId").value(0));
        assertNull(DataScope.get());
    }

    @Test void accountEndpointsCreateOnlyDirectChildAndRejectRenameAndPrivilegeFields() throws Exception {
        String body = "{\"username\":\"C\",\"displayName\":\"C名称\",\"password\":\"secret123\",\"enabled\":true,\"departmentIds\":[3],\"parentId\":0,\"isAdmin\":true}";
        mvc.perform(post("/api/accounts").header(AuthInterceptor.HEADER_TOKEN, childToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.parentId").value(childId))
                .andExpect(jsonPath("$.data.isAdmin").value(false));
        long c = dao.findByUsername("C").id;
        mvc.perform(get("/api/accounts").header(AuthInterceptor.HEADER_TOKEN, childToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].username").value("C"));
        mvc.perform(put("/api/accounts/" + c).header(AuthInterceptor.HEADER_TOKEN, childToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("\"C\",", "\"renamed\",")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/accounts/" + rootId).header(AuthInterceptor.HEADER_TOKEN, childToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/api/accounts").header(AuthInterceptor.HEADER_TOKEN, childToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("\"C\",", "\"outside\",").replace("[3]", "[4]")))
                .andExpect(status().isForbidden());
        assertNull(DataScope.get());
    }

    @Test void dangerousGroupsWritesExcelUndoAndUnknownRoutesFailClosedButRootsRemainCompatible() throws Exception {
        String[] denied = {
                "GET /api/backup/list", "POST /api/backup/run", "GET /api/backup/download", "POST /api/backup/restore", "POST /api/backup/upload",
                "GET /api/report/progress", "GET /api/report/export", "POST /api/report/check", "POST /api/report/preview", "POST /api/report/import",
                "GET /api/log", "GET /api/setting/backupKeep", "PUT /api/setting/siteName",
                "POST /api/dict", "PUT /api/dict/1", "PUT /api/dict/reorder", "DELETE /api/dict/1",
                "GET /api/dict/export", "GET /api/dict/template", "POST /api/dict/import-excel",
                "POST /api/fitness-matrix", "DELETE /api/fitness-matrix/column", "POST /api/fitness-matrix/init",
                "POST /api/fitness-std/matrix/import", "POST /api/fitness-std/weight/import",
                "POST /api/military/schemes", "DELETE /api/military/schemes/1",
                "GET /api/personnel/undo-list", "POST /api/personnel/undo/1", "POST /api/personnel/undo-discard/1",
                "GET /api/new-feature", "POST /api/personnel/new-bulk-write", "DELETE /api/accounts/3",
                "GET /api/double-excellence/unknown", "POST /api/double-excellence/stats", "PUT /api/double-excellence",
                "DELETE /api/double-excellence", "POST /api/double-excellence/export", "GET /api/double-excellence/import-excel",
                "PUT /api/double-excellence/1/extra", "POST /api/double-excellence/bulk", "GET /api/double-excellence-extra",
                "GET /api/report;ignored/export", "GET /api/backup//list"
        };
        for (String route : denied) {
            mvc.perform(request(route, childToken)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
            assertNull(DataScope.get(), route);
        }
        for (String route : Arrays.asList("GET /api/backup/list", "GET /api/report/export", "GET /api/log", "GET /api/new-feature"))
            mvc.perform(request(route, rootToken)).andExpect(status().isOk());
        assertNull(DataScope.get());
    }

    @Test void multipartBackupUploadUsesRealControllerAndGlobalAdminRestriction() throws Exception {
        BackupService backups = new BackupService(jdbc,
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()),
                temporary.resolve("upload-data").toFile());
        mvc = MockMvcBuilders.standaloneSetup(new BackupController(backups))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new MappedInterceptor(new String[]{"/api/**"}, interceptor)).build();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("meta.json"));
            zip.write(("{\"version\":4,\"createdText\":\"2026-10-18 12:00:00\",\"photoModules\":[\"equipment\"]," +
                    "\"modules\":{\"equipment\":{\"equipment\":0}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("data/equipment/equipment.json"));
            zip.write("[]".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "../../same.zip", "application/zip", bytes.toByteArray());
        mvc.perform(multipart("/api/backup/upload").file(file)).andExpect(status().isUnauthorized());
        assertTrue(backups.listBackups().isEmpty());
        mvc.perform(multipart("/api/backup/upload").file(file).header(AuthInterceptor.HEADER_TOKEN, childToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        assertTrue(backups.listBackups().isEmpty());
        mvc.perform(multipart("/api/backup/upload").file(file).header(AuthInterceptor.HEADER_TOKEN, rootToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value(org.hamcrest.Matchers.startsWith("upload_")))
                .andExpect(jsonPath("$.data.modules.equipment.equipment").value(0))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.createdText").value("2026-10-18 12:00:00"));
        assertEquals(1, backups.listBackups().size());
        assertEquals("A", tokens.validate(rootToken), "upload must not revoke sessions or restore security data");
        mvc.perform(multipart("/api/backup/upload").header(AuthInterceptor.HEADER_TOKEN, rootToken))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/backup/upload").file(new MockMultipartFile("file", "bad.zip", "application/zip", new byte[]{1}))
                        .header(AuthInterceptor.HEADER_TOKEN, rootToken))
                .andExpect(status().isBadRequest());
        MockMultipartFile oversized = new MockMultipartFile("file", "huge.zip", "application/zip", new byte[]{1}) {
            @Override public long getSize() { return BackupService.MAX_UPLOAD_BYTES + 1; }
        };
        mvc.perform(multipart("/api/backup/upload").file(oversized).header(AuthInterceptor.HEADER_TOKEN, rootToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("100MB")));
        assertEquals(1, backups.listBackups().size());
        try (java.util.stream.Stream<Path> paths = java.nio.file.Files.list(temporary.resolve("upload-data/backup"))) {
            assertEquals(1, paths.count(), "failed uploads must leave no temporary files");
        }
        assertNull(DataScope.get());
    }

    @Test void necessaryScopedBusinessRoutesAndReadOnlySharedConfigurationRemainAllowed() throws Exception {
        String[] allowed = {
                "GET /api/personnel", "GET /api/personnel/options", "GET /api/personnel/123/age", "GET /api/personnel/123/photo",
                "GET /api/personnel/roster/excel", "GET /api/personnel/rollcall/excel", "PUT /api/personnel/123",
                "GET /api/equipment", "POST /api/personnel", "POST /api/equipment", "POST /api/material", "POST /api/vehicle",
                "POST /api/phone", "POST /api/leave", "POST /api/fitness", "POST /api/sub/family", "PUT /api/vehicle/1", "GET /api/phone/by-person",
                "GET /api/leave/summary", "POST /api/leave-plan", "GET /api/fitness/stats", "GET /api/fitness/summary", "POST /api/military",
                "GET /api/sub/family", "PUT /api/sub/driver/2", "GET /api/departments", "POST /api/departments",
                "GET /api/stats/summary", "GET /api/dashboard/events", "GET /api/setting/fitness_grade_rule",
                "POST /api/auth/change-username",
                "GET /api/setting/bmi_standard", "GET /api/setting/dashWidgets", "GET /api/setting/dashStatCards",
                "GET /api/dict/types", "GET /api/dict/rank", "GET /api/fitness-matrix/items", "GET /api/fitness-matrix",
                "GET /api/fitness-std/matrix/template", "GET /api/military/schemes", "GET /api/personnel/template",
                "GET /api/equipment-gun/export", "POST /api/personnel/import-excel", "POST /api/leave-plan/import-excel",
                "GET /api/double-excellence", "POST /api/double-excellence", "GET /api/double-excellence/1",
                "PUT /api/double-excellence/1", "DELETE /api/double-excellence/1", "GET /api/double-excellence/stats",
                "GET /api/double-excellence/template", "GET /api/double-excellence/export", "POST /api/double-excellence/import-excel"
        };
        for (String route : allowed) {
            mvc.perform(request(route, childToken)).andExpect(status().isOk());
            assertNull(DataScope.get(), route);
        }
    }

    @Test void everyRequestReloadsAncestorsAndScopeEvenWhenUsernameTokenIsStillValid() throws Exception {
        jdbc.update("UPDATE admin_user SET enabled=0 WHERE id=?", rootId);
        assertEquals("B", tokens.validate(childToken));
        mvc.perform(get("/api/auth/me").header(AuthInterceptor.HEADER_TOKEN, childToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"B\",\"password\":\"secret123\"}"))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE admin_user SET enabled=1 WHERE id=?", rootId);
        dao.replaceDepartments(childId, Collections.singleton(3L));
        mvc.perform(get("/api/personnel").header(AuthInterceptor.HEADER_TOKEN, childToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0]").value(3));
        assertNull(DataScope.get());
    }

    @Test void unauthorizedForbiddenControllerErrorsAndOptionsAlwaysClearScope() throws Exception {
        DataScope.set(new DataScope.Context(99L,"stale",true,Collections.<Long>emptySet()));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        assertNull(DataScope.get());
        mvc.perform(get("/api/personnel/999").header(AuthInterceptor.HEADER_TOKEN, childToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        assertNull(DataScope.get());
        mvc.perform(get("/api/personnel/998").header(AuthInterceptor.HEADER_TOKEN, childToken))
                .andExpect(status().isBadRequest());
        assertNull(DataScope.get());
        DataScope.set(new DataScope.Context(99L,"stale",true,Collections.<Long>emptySet()));
        MockHttpServletRequest options = new MockHttpServletRequest("OPTIONS", "/api/accounts");
        assertTrue(interceptor.preHandle(options, new MockHttpServletResponse(), new Object()));
        assertNull(DataScope.get());
    }

    @Test void revokedSessionsAreUnauthorizedAndResetPasswordCannotIssueRacingStaleLogin() throws Exception {
        long oldVersion = tokens.version("B");
        DataScope.set(accounts.authenticateScope("A"));
        AccountService.AccountRequest update = AccountServiceTest.request("B", 2L); update.password = "updated123";
        accounts.update(childId, update);
        DataScope.clear();
        mvc.perform(get("/api/auth/me").header(AuthInterceptor.HEADER_TOKEN, childToken))
                .andExpect(status().isUnauthorized());
        assertThrows(ForbiddenException.class, () -> tokens.create("B", oldVersion));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"B\",\"password\":\"updated123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isString());
    }
}
