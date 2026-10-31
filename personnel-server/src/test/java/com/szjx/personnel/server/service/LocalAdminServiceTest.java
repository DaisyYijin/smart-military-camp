package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.AdminUserDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LocalAdminServiceTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private AdminUserDao users;
    private TokenManager tokens;
    private LocalAdminService service;
    private TransactionTemplate tx;

    @BeforeEach void setup() {
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE admin_user(id INTEGER PRIMARY KEY,username TEXT UNIQUE,password_hash TEXT,display_name TEXT,parent_id INTEGER DEFAULT 0,enabled INTEGER DEFAULT 1)");
        jdbc.update("INSERT INTO admin_user(id,username,password_hash,display_name,parent_id) VALUES(1,'A','original-root','管理员',0),(2,'B','original-child','子账号',1),(3,'C','original-grandchild','下属账号',2)");
        users = new AdminUserDao(jdbc);
        tokens = new TokenManager();
        service = new LocalAdminService(users,tokens);
        tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    @Test void localResetCannotTargetChildOrSilentlySucceedForMissingRoot() {
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->{service.configureRoot("B","B","new-secret");return null;}));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->{service.configureRoot("missing","B","new-secret");return null;}));
        assertEquals("original-child",users.findPasswordHash("B"));
    }

    @Test void renameCollisionDoesNotResetChildPassword() {
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->{service.configureRoot("A","B","new-secret");return null;}));
        assertEquals("original-root",users.findPasswordHash("A"));
        assertEquals("original-child",users.findPasswordHash("B"));
    }

    @Test void successfulRootResetRevokesRootAndDescendantSessionsAfterCommit() {
        String root = tokens.create("A"), child = tokens.create("B"), grandchild = tokens.create("C");
        tx.execute(s->{service.configureRoot("A","A","new-secret");assertEquals("A",tokens.validate(root));return null;});
        assertTrue(PasswordUtil.verify("new-secret",users.findPasswordHash("A")));
        assertEquals("original-child",users.findPasswordHash("B"));
        assertNull(tokens.validate(root));
        assertNull(tokens.validate(child));
        assertNull(tokens.validate(grandchild));
    }

    @Test void rootRenamePreservesStableHierarchyAndRevokesOldIdentity() {
        String old = tokens.create("A");
        tx.execute(s->{service.configureRoot("A","A-new",null);return null;});
        assertNull(users.findByUsername("A"));
        assertEquals(1L,users.findByUsername("A-new").id);
        assertEquals(1L,users.findByUsername("B").parentId);
        assertNull(tokens.validate(old));
    }

    @Test void rollbackDoesNotRevokeSessionsOrChangePassword() {
        String root = tokens.create("A");
        tx.execute(s->{service.configureRoot("A","A","new-secret");s.setRollbackOnly();return null;});
        assertEquals("original-root",users.findPasswordHash("A"));
        assertEquals("A",tokens.validate(root));
    }
}
