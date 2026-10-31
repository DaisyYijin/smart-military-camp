package com.szjx.personnel.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.dao.AdminUserDao;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Independent temporary SQLite fixture; no application, migrations or production database. */
class AccountScopePreviewTest {
    @TempDir Path temporary;
    JdbcTemplate jdbc;
    AccountService service;
    AdminUserDao users;
    TokenManager tokens;

    @BeforeEach void setup() {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:sqlite:" + temporary.resolve("scope.db"));
        source.setDriverClassName("org.sqlite.JDBC");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT,parent_id INTEGER)");
        jdbc.update("INSERT INTO department VALUES(1,'一部',0),(2,'二处',1),(3,'三科',2),(4,'四部',0)");
        jdbc.execute("CREATE TABLE admin_user(id INTEGER PRIMARY KEY,username TEXT,display_name TEXT,password_hash TEXT,parent_id INTEGER,enabled INTEGER)");
        jdbc.update("INSERT INTO admin_user VALUES(1,'root','根','secret-hash',0,1),(2,'child','子','secret-hash',1,1),(3,'sibling','同级','secret-hash',1,0),(4,'grandchild','孙','secret-hash',2,1),(5,'other-root','另根','secret-hash',0,1)");
        jdbc.execute("CREATE TABLE account_department(user_id INTEGER,department_id INTEGER)");
        jdbc.update("INSERT INTO account_department VALUES(2,1),(2,4),(3,2),(4,1),(4,4)");
        users = new AdminUserDao(jdbc); tokens = new TokenManager();
        service = new AccountService(users, jdbc, tokens);
        DataScope.set(service.authenticateScope("root"));
    }

    @AfterEach void cleanup() { DataScope.clear(); }
    Set<Long> ids(Long... values) { return new LinkedHashSet<Long>(Arrays.asList(values)); }

    @SuppressWarnings("unchecked")
    @Test void expandsEffectiveTreeAndWarnsAboutDisabledSiblingWithoutWritingOrRevoking() throws Exception {
        String token = tokens.create("child");
        Map<String, Object> result = service.scopePreview(2L, null, false);
        assertEquals(4, result.get("effectiveDepartmentCount"));
        List<Map<String, Object>> tree = (List<Map<String, Object>>) result.get("effectiveTree");
        assertEquals(2, tree.size());
        assertEquals(1L, tree.get(0).get("id"));
        List<Map<String, Object>> overlap = (List<Map<String, Object>>) result.get("overlaps");
        assertEquals(1, overlap.size());
        assertEquals("sibling", overlap.get(0).get("username"));
        assertEquals(false, overlap.get(0).get("enabled"));
        assertEquals(2, overlap.get(0).get("departmentCount"));
        assertEquals(ids(1L, 4L), users.departmentIds(2L));
        assertEquals("child", tokens.validate(token));
        String json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("secret-hash")); assertFalse(json.contains("password"));
        assertTrue(json.contains("共享同一业务数据"));
    }

    @Test void parentShrinkIntersectsStoredGrantsWhileProposedScopeMustBeValid() {
        users.replaceDepartments(2L, ids(2L));
        DataScope.set(service.authenticateScope("child"));
        Map<String, Object> result = service.scopePreview(4L, null, false);
        assertEquals(2, result.get("effectiveDepartmentCount"));
        assertEquals(ids(1L, 4L), result.get("departmentIds"));
        assertEquals(0, service.scopePreview(4L, null, true).get("effectiveDepartmentCount"));
        assertEquals(1, service.scopePreview(4L, ids(3L), true).get("effectiveDepartmentCount"));
        assertThrows(ForbiddenException.class, () -> service.scopePreview(4L, ids(1L), true));
        assertThrows(IllegalArgumentException.class, () -> service.scopePreview(4L, ids(999L), true));
        assertEquals(ids(1L, 4L), users.departmentIds(4L));
    }

    @Test void refusesSelfParentSiblingGrandchildAnotherRootAndRevalidatesActor() {
        assertThrows(ForbiddenException.class, () -> service.scopePreview(4L, null, false));
        assertThrows(ForbiddenException.class, () -> service.scopePreview(1L, null, false));
        assertThrows(ForbiddenException.class, () -> service.scopePreview(5L, null, false));
        DataScope.set(service.authenticateScope("child"));
        for (long id : new long[]{1, 2, 3, 5, 999}) assertThrows(ForbiddenException.class, () -> service.scopePreview(id, null, false));
        jdbc.update("UPDATE admin_user SET enabled=0 WHERE id=2");
        assertThrows(ForbiddenException.class, () -> service.scopePreview(4L, null, false));
        DataScope.clear(); assertThrows(ForbiddenException.class, () -> service.scopePreview(null, ids(2L), true));
    }

    @Test void malformedDepartmentCycleStillSerializesAsFiniteTree() throws Exception {
        jdbc.update("UPDATE department SET parent_id=3 WHERE id=1");
        Map<String, Object> result = service.scopePreview(2L, null, false);
        assertEquals(4, result.get("effectiveDepartmentCount"));
        assertDoesNotThrow(() -> new ObjectMapper().writeValueAsString(result));
    }
}
