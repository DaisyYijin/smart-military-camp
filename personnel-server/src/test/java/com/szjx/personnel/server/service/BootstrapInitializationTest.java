package com.szjx.personnel.server.service;

import com.szjx.personnel.server.config.SchemaInitializer;
import com.szjx.personnel.server.dao.AdminUserDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BootstrapInitializationTest {
    @TempDir Path temp;
    private AdminUserDao users;
    private SchemaInitializer initializer;
    private MockEnvironment environment;

    @BeforeEach void setup() {
        JdbcTemplate jdbc = AccountServiceTest.database(temp.resolve("bootstrap.sqlite"));
        jdbc.execute("CREATE TABLE admin_user(id INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT UNIQUE,password_hash TEXT,display_name TEXT,parent_id INTEGER DEFAULT 0,enabled INTEGER DEFAULT 1)");
        users = new AdminUserDao(jdbc);
        initializer = new SchemaInitializer(jdbc, users);
        environment = new MockEnvironment();
        initializer.setEnvironment(environment);
    }

    @Test void emptyDatabaseWithoutExplicitPasswordDoesNotCreateAnyAccount() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, initializer::initializeAdministrator);
        assertTrue(failure.getMessage().contains("APP_BOOTSTRAP_PASSWORD"));
        assertEquals(0, users.count());
        assertFalse(initializer.isAdministratorCreated());
    }

    @Test void explicitPasswordIsHashedWithoutTrimmingAndCreatesOnlyOneRoot() {
        environment.setProperty("app.bootstrap.username", " site.owner ");
        environment.setProperty("app.bootstrap.password", " pass phrase ");
        initializer.initializeAdministrator();
        assertTrue(initializer.isAdministratorCreated());
        assertEquals(1, users.count());
        AdminUserDao.User owner = users.findByUsername("site.owner");
        assertEquals(0L, owner.parentId);
        assertTrue(owner.enabled);
        assertTrue(PasswordUtil.verify(" pass phrase ", owner.passwordHash));
        assertFalse(PasswordUtil.verify("pass phrase", owner.passwordHash));
        assertNotEquals(" pass phrase ", owner.passwordHash);
    }

    @Test void environmentNeverResetsExistingIdentityOrPassword() {
        users.insertAdmin("existing", "preserved-hash", "original");
        environment.setProperty("app.bootstrap.username", "new-owner");
        environment.setProperty("app.bootstrap.password", "different-password");
        initializer.initializeAdministrator();
        assertEquals(1, users.count());
        assertEquals("preserved-hash", users.findPasswordHash("existing"));
        assertNull(users.findByUsername("new-owner"));
        assertFalse(initializer.isAdministratorCreated());
    }

    @Test void invalidBootstrapInputDoesNotLeavePartialAccount() {
        for (String password : new String[]{"short", "      ", new String(new char[257]).replace('\0', 'a')}) {
            environment.setProperty("app.bootstrap.password", password);
            assertThrows(IllegalStateException.class, initializer::initializeAdministrator);
            assertEquals(0, users.count());
        }
        environment.setProperty("app.bootstrap.password", "valid-password");
        environment.setProperty("app.bootstrap.username", "   ");
        assertThrows(IllegalStateException.class, initializer::initializeAdministrator);
        assertEquals(0, users.count());
    }

    @Test void repeatedInitializationPreservesOriginalPassword() {
        environment.setProperty("app.bootstrap.password", "first-password");
        initializer.initializeAdministrator();
        String hash = users.findPasswordHash("admin");
        environment.setProperty("app.bootstrap.password", "second-password");
        initializer.initializeAdministrator();
        assertEquals(hash, users.findPasswordHash("admin"));
    }
}
