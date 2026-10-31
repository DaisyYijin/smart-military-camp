package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.config.SchemaInitializer;
import com.szjx.personnel.server.dao.AdminUserDao;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** File-isolated real SQLite tests, no application launch, scheduler or production database. */
public class AccountServiceTest {
    @TempDir Path temporary;
    JdbcTemplate jdbc;
    AdminUserDao dao;
    AccountService service;
    TokenManager tokens;
    long a, b, c, d;

    public static JdbcTemplate database(Path path) {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.sqlite.JDBC");
        source.setUrl("jdbc:sqlite:" + path.toAbsolutePath());
        return new JdbcTemplate(source);
    }

    public static void legacySchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE admin_user(id INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT NOT NULL UNIQUE,password_hash TEXT NOT NULL,display_name TEXT DEFAULT '',created_at TEXT)");
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT,parent_id INTEGER DEFAULT 0)");
        jdbc.update("INSERT INTO department VALUES(1,'一部',0),(2,'一部二处',1),(3,'一部二处三科',2),(4,'四部',0),(5,'四部五处',4)");
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY,department_id INTEGER)");
    }

    public static AccountService transactional(AccountService target, JdbcTemplate jdbc) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(jdbc.getDataSource()),
                new AnnotationTransactionAttributeSource()));
        return (AccountService) proxy.getProxy();
    }

    public static AccountService.AccountRequest request(String username, Long... departments) {
        AccountService.AccountRequest value = new AccountService.AccountRequest();
        value.username = username;
        value.displayName = username + "显示名";
        value.password = "secret123";
        value.enabled = true;
        value.departmentIds = new LinkedHashSet<Long>(Arrays.asList(departments));
        return value;
    }

    @BeforeEach void setup() {
        DataScope.clear();
        jdbc = database(temporary.resolve("accounts.sqlite"));
        legacySchema(jdbc);
        dao = new AdminUserDao(jdbc);
        dao.insertAdmin("A", PasswordUtil.hash("secret123"), "旧管理员A");
        new SchemaInitializer(jdbc, dao).migrateAccounts();
        tokens = new TokenManager();
        service = transactional(new AccountService(dao, jdbc, tokens), jdbc);
        a = dao.findByUsername("A").id;
        as("A");
        b = id(service.create(request("B", 1L, 4L)));
        c = id(service.create(request("C", 4L)));
        as("B");
        d = id(service.create(request("D", 2L, 4L)));
    }

    @AfterEach void cleanup() { DataScope.clear(); }
    long id(Map<String, Object> profile) { return ((Number) profile.get("userId")).longValue(); }
    void as(String user) { DataScope.set(service.authenticateScope(user)); }
    Set<Long> ids(Long... ids) { return new LinkedHashSet<Long>(Arrays.asList(ids)); }

    @Test void changeUsernameVerifiesPasswordChecksUniquenessAndRevokesTree() {
        as("B");
        service.create(request("E", 2L));
        Map<String, String> sessions = new HashMap<String, String>();
        for (String user : Arrays.asList("A","B","C","D","E")) sessions.put(user, tokens.create(user));
        assertThrows(IllegalArgumentException.class, () -> service.changeUsername("B", "wrong", "newB"));
        assertThrows(IllegalArgumentException.class, () -> service.changeUsername("B", "secret123", "A"));
        assertThrows(IllegalArgumentException.class, () -> service.changeUsername("B", "secret123", "B"));
        for (String invalid : Arrays.asList("含中文", "with space", "", "x!")) {
            assertThrows(IllegalArgumentException.class, () -> service.changeUsername("B", "secret123", invalid), invalid);
        }
        Map<String, Object> profile = service.changeUsername("B", "secret123", "newB");
        assertEquals("newB", profile.get("username"));
        assertEquals("B显示名", profile.get("displayName"));
        assertNull(dao.findByUsername("B"));
        assertEquals(b, dao.findByUsername("newB").id);
        for (String user : Arrays.asList("B","D","E")) assertNull(tokens.validate(sessions.get(user)), user);
        assertEquals("A", tokens.validate(sessions.get("A")));
        assertEquals("C", tokens.validate(sessions.get("C")));
        as("newB");
        assertEquals(2, service.listChildren().size());
        as("A");
        assertEquals("newB", service.listChildren().get(0).get("username"));
    }

    @Test void oldAccountsRemainEnabledRootAndMigrationIsIdempotent() {
        String hash = dao.findPasswordHash("A");
        new SchemaInitializer(jdbc, dao).migrateAccounts();
        new SchemaInitializer(jdbc, dao).migrateAccounts();
        assertEquals(0L, dao.findByUsername("A").parentId);
        assertTrue(service.authenticateScope("A").admin);
        assertEquals(hash, dao.findPasswordHash("A"));
        assertEquals(a, dao.findByUsername("B").parentId);
        assertFalse(service.authenticateScope("B").admin);
        assertEquals(ids(1L, 4L), dao.departmentIds(b));
    }

    @Test void multirootExpansionAndEveryAncestorIntersectionAreRecomputed() {
        assertEquals(ids(1L, 2L, 3L, 4L, 5L), service.authenticateScope("B").departmentIds);
        assertEquals(ids(2L, 3L, 4L, 5L), service.authenticateScope("D").departmentIds);
        as("D");
        long e = id(service.create(request("E", 3L, 5L)));
        as("A");
        AccountService.AccountRequest shrink = request("B", 2L); shrink.password = "";
        service.update(b, shrink);
        assertEquals(ids(2L, 3L), service.authenticateScope("B").departmentIds);
        assertEquals(ids(2L, 3L), service.authenticateScope("D").departmentIds);
        assertEquals(ids(3L), service.authenticateScope("E").departmentIds);
        assertEquals(ids(3L, 5L), dao.departmentIds(e)); // stored grants are roots, not silently rewritten
        jdbc.update("DELETE FROM account_department WHERE user_id=?", b);
        assertTrue(service.authenticateScope("E").departmentIds.isEmpty());
    }

    @Test void disabledAncestorBlocksAllDescendantsButNotSiblingAndMalformedChainFailsClosed() {
        jdbc.update("UPDATE admin_user SET enabled=0 WHERE id=?", b);
        assertThrows(ForbiddenException.class, () -> service.authenticateScope("B"));
        assertThrows(ForbiddenException.class, () -> service.authenticateScope("D"));
        assertFalse(service.authenticateScope("C").admin);
        jdbc.update("UPDATE admin_user SET enabled=1,parent_id=? WHERE id=?", d, b);
        assertThrows(ForbiddenException.class, () -> service.authenticateScope("D"));
        jdbc.update("UPDATE admin_user SET parent_id=99999 WHERE id=?", b);
        assertThrows(ForbiddenException.class, () -> service.authenticateScope("D"));
    }

    @Test void rootDisabledBlocksEveryDescendant() {
        jdbc.update("UPDATE admin_user SET enabled=0 WHERE id=?", a);
        for (String user : Arrays.asList("A", "B", "C", "D"))
            assertThrows(ForbiddenException.class, () -> service.authenticateScope(user));
    }

    @Test void cannotManageSelfParentSiblingGrandchildOrAnotherRoot() {
        as("B");
        for (long target : new long[]{a, b, c})
            assertThrows(ForbiddenException.class, () -> service.update(target, request("B", 1L)));
        as("A");
        assertThrows(ForbiddenException.class, () -> service.update(d, request("D", 2L)));
        dao.insertAdmin("other-root", "hash", "另一根");
        assertThrows(ForbiddenException.class, () -> service.update(dao.findByUsername("other-root").id, request("other-root", 1L)));
        assertEquals(2, service.listChildren().size());
        as("B");
        assertEquals(1, service.listChildren().size());
    }

    @Test void childCannotRegrantLostAuthorizationAndWholeProposedSubtreeMustBeAllowed() {
        as("A");
        AccountService.AccountRequest shrink = request("B", 2L); shrink.password = "";
        service.update(b, shrink);
        // Leave stale request-local identity deliberately: service must reload the actor.
        DataScope.set(new DataScope.Context(b, "B", false, ids(1L,2L,3L,4L,5L)));
        assertThrows(ForbiddenException.class, () -> service.create(request("outside", 4L)));
        assertThrows(ForbiddenException.class, () -> service.create(request("too-broad", 1L)));
        assertThrows(ForbiddenException.class, () -> service.update(d, request("D", 2L, 4L)));
        assertNotNull(service.create(request("inside", 3L)));
        assertNull(dao.findByUsername("outside"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "disable", "enable", "departments"})
    void securityChangesRevokeTargetAndAllDescendantsOnlyAfterCommit(String operation) {
        as("D"); service.create(request("E", 3L));
        if ("enable".equals(operation)) jdbc.update("UPDATE admin_user SET enabled=0 WHERE id=?", b);
        Map<String, String> sessions = new HashMap<String, String>();
        for (String user : Arrays.asList("A","B","C","D","E")) sessions.put(user, tokens.create(user));
        long oldVersion = tokens.version("B");
        as("A");
        AccountService.AccountRequest change = request("B", 1L, 4L);
        change.password = "";
        if ("password".equals(operation)) change.password = "newpass123";
        if ("disable".equals(operation)) change.enabled = false;
        if ("departments".equals(operation)) change.departmentIds = ids(2L);
        service.update(b, change);
        for (String user : Arrays.asList("B","D","E")) assertNull(tokens.validate(sessions.get(user)));
        assertEquals("A", tokens.validate(sessions.get("A")));
        assertEquals("C", tokens.validate(sessions.get("C")));
        assertThrows(ForbiddenException.class, () -> tokens.create("B", oldVersion));
    }

    @Test void displayNameAndEmptyPasswordDoNotRevokeOrAlterUsernameOrHash() {
        String token = tokens.create("B"), hash = dao.findPasswordHash("B");
        as("A");
        AccountService.AccountRequest change = request("B", 1L, 4L);
        change.password = ""; change.displayName = "O'Reilly; --";
        service.update(b, change);
        assertEquals("B", tokens.validate(token));
        assertEquals(hash, dao.findPasswordHash("B"));
        assertEquals("O'Reilly; --", dao.findDisplayName("B"));
        change.username = "renamed";
        assertThrows(IllegalArgumentException.class, () -> service.update(b, change));
        assertNotNull(dao.findByUsername("B"));
    }

    @Test void transactionRollsBackPartialCreateAndUpdateAndDoesNotRevokeOnRollback() {
        jdbc.execute("CREATE TRIGGER fail_grant BEFORE INSERT ON account_department WHEN NEW.department_id=3 BEGIN SELECT RAISE(ABORT,'test failure'); END");
        as("A");
        assertThrows(RuntimeException.class, () -> service.create(request("rolledback", 3L)));
        assertNull(dao.findByUsername("rolledback"));
        String session = tokens.create("B"), oldHash = dao.findPasswordHash("B");
        assertThrows(RuntimeException.class, () -> service.update(b, request("B", 3L)));
        assertEquals(ids(1L,4L), dao.departmentIds(b));
        assertEquals(oldHash, dao.findPasswordHash("B"));
        assertEquals("B", tokens.validate(session));
    }

    @Test void outerTransactionRollbackKeepsExistingSessionsAndOriginalAuthorization() {
        as("A");
        String token = tokens.create("B");
        String oldHash = dao.findPasswordHash("B");
        org.springframework.transaction.support.TransactionTemplate transaction =
                new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.execute(status -> {
            service.update(b, request("B", 3L));
            assertEquals("B", tokens.validate(token)); // revoke callback must not run before commit
            status.setRollbackOnly();
            return null;
        });
        assertEquals("B", tokens.validate(token));
        assertEquals(oldHash, dao.findPasswordHash("B"));
        assertEquals(ids(1L, 4L), dao.departmentIds(b));
    }

    @Test void failedSecurityMigrationRollsBackColumnsAndDoesNotSilentlyContinue() {
        JdbcTemplate broken = database(temporary.resolve("broken.sqlite"));
        legacySchema(broken);
        broken.execute("CREATE TABLE account_department(user_id INTEGER)");
        SchemaInitializer migration = new SchemaInitializer(broken, new AdminUserDao(broken));
        assertThrows(RuntimeException.class, migration::migrateAccounts);
        for (Map<String, Object> column : broken.queryForList("PRAGMA table_info(admin_user)")) {
            assertNotEquals("parent_id", column.get("name"));
            assertNotEquals("enabled", column.get("name"));
        }
    }

    @Test void ownPasswordChangeAlsoRevokesDescendants() {
        as("B"); String bToken = tokens.create("B"), dToken = tokens.create("D"), cToken = tokens.create("C");
        service.changePassword("B", "secret123", "updated123");
        assertTrue(PasswordUtil.verify("updated123", dao.findPasswordHash("B")));
        assertNull(tokens.validate(bToken)); assertNull(tokens.validate(dToken));
        assertEquals("C", tokens.validate(cToken));
    }

    @Test void validatesPasswordUsernameDepartmentAndAllowsAnExplicitEmptyScope() {
        as("A");
        AccountService.AccountRequest invalid = request("bad", 1L); invalid.password = "short";
        assertThrows(IllegalArgumentException.class, () -> service.create(invalid));
        invalid.password = "secret123"; invalid.username = "x' OR 1=1--";
        assertThrows(IllegalArgumentException.class, () -> service.create(invalid));
        invalid.username = "bad"; invalid.departmentIds = ids(9999L);
        assertThrows(IllegalArgumentException.class, () -> service.create(invalid));
        invalid.departmentIds = ids((Long) null);
        assertThrows(IllegalArgumentException.class, () -> service.create(invalid));
        invalid.departmentIds = ids(0L);
        assertThrows(IllegalArgumentException.class, () -> service.create(invalid));
        assertNotNull(service.create(request("empty")));
        assertTrue(service.authenticateScope("empty").departmentIds.isEmpty());
        assertFalse(service.authenticateScope("empty").admin);
        assertFalse(service.profile("B").containsKey("passwordHash"));
        assertFalse(service.profile("B").containsKey("password_hash"));
    }
}
