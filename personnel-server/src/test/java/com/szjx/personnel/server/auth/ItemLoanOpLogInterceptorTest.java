package com.szjx.personnel.server.auth;

import com.szjx.personnel.server.service.LogService;
import com.szjx.personnel.server.service.PersonnelIntegrityTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用隔离SQLite验证借还操作日志的中文模块名与删除前名称快照。 */
class ItemLoanOpLogInterceptorTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private LogService logs;
    private OpLogInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY)");
        PersonnelIntegrityTest.itemLoanSchema(jdbc);
        jdbc.update("INSERT INTO personnel(id) VALUES(1)");
        jdbc.update("INSERT INTO item_loan(id,item_name,personnel_id,borrow_date) VALUES(7,'对讲机',1,'2026-10-01')");
        logs = mock(LogService.class);
        interceptor = new OpLogInterceptor(logs, jdbc);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setAttribute("authUser", "admin");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @Test
    void createAndUpdateUseChineseModuleName() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest create = request("POST", "/api/item-loans");
        assertTrue(interceptor.preHandle(create, response, new Object()));
        interceptor.afterCompletion(create, response, new Object(), null);
        verify(logs).record("admin", "新增物品借还", "", "127.0.0.1");

        MockHttpServletRequest update = request("PUT", "/api/item-loans/7");
        assertTrue(interceptor.preHandle(update, response, new Object()));
        interceptor.afterCompletion(update, response, new Object(), null);
        verify(logs).record("admin", "修改物品借还", "对讲机(ID 7)", "127.0.0.1");
    }

    @Test
    void deleteKeepsItemNameCapturedBeforeTheRowWasRemoved() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest delete = request("DELETE", "/api/item-loans/7");
        assertTrue(interceptor.preHandle(delete, response, new Object()));
        jdbc.update("DELETE FROM item_loan WHERE id=7");
        interceptor.afterCompletion(delete, response, new Object(), null);
        verify(logs).record("admin", "删除物品借还", "对讲机(ID 7)", "127.0.0.1");
    }

    @Test
    void missingLegacyTableDoesNotInterruptRequestAndFallsBackToId() {
        jdbc.execute("DROP TABLE item_loan");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest delete = request("DELETE", "/api/item-loans/7");
        assertTrue(interceptor.preHandle(delete, response, new Object()));
        assertDoesNotThrow(() -> interceptor.afterCompletion(delete, response, new Object(), null));
        verify(logs).record("admin", "删除物品借还", "ID 7", "127.0.0.1");
    }
}
