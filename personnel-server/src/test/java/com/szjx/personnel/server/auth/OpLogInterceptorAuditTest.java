package com.szjx.personnel.server.auth;

import com.szjx.personnel.server.service.LogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/** 审计拦截器:导出路由覆盖、操作结果标记、来源IP仅信任配置代理 */
class OpLogInterceptorAuditTest {
    private LogService logs;
    private OpLogInterceptor interceptor;
    private OpLogInterceptor withProxy;

    @BeforeEach
    void setUp() {
        logs = mock(LogService.class);
        interceptor = new OpLogInterceptor(logs, mock(JdbcTemplate.class));
        withProxy = new OpLogInterceptor(logs, mock(JdbcTemplate.class), "10.1.1.9, 127.0.0.1");
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setAttribute("authUser", "admin");
        return request;
    }

    @Test
    void recordsRollcallAndRosterExcelExports() {
        interceptor.afterCompletion(request("GET", "/api/personnel/rollcall/excel"),
                new MockHttpServletResponse(), null, null);
        verify(logs).record("admin", "导出点名册", "Excel导出", "127.0.0.1");
        interceptor.afterCompletion(request("GET", "/api/personnel/roster/excel"),
                new MockHttpServletResponse(), null, null);
        verify(logs).record("admin", "导出花名册", "Excel导出", "127.0.0.1");
    }

    @Test
    void recordsModuleExportByPath() {
        interceptor.afterCompletion(request("GET", "/api/fitness/export"),
                new MockHttpServletResponse(), null, null);
        verify(logs).record("admin", "导出体能考核", "Excel导出", "127.0.0.1");
    }

    @Test
    void marksFailedWriteWithHttpStatus() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        interceptor.afterCompletion(request("POST", "/api/personnel"), response, null, null);
        verify(logs).record("admin", "新增人员管理(未成功)", "HTTP 500", "127.0.0.1");
    }

    @Test
    void successWriteHasNoFailureMark() {
        interceptor.afterCompletion(request("POST", "/api/personnel"),
                new MockHttpServletResponse(), null, null);
        verify(logs).record("admin", "新增人员管理", "", "127.0.0.1");
    }

    @Test
    void rejectedExportAlsoMarked() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(403);
        interceptor.afterCompletion(request("GET", "/api/personnel/rollcall/excel"), response, null, null);
        verify(logs).record(eq("admin"), eq("导出点名册(未成功)"), startsWith("HTTP 403"), eq("127.0.0.1"));
    }

    @Test
    void clientIpIgnoresForwardedHeaderWithoutTrustedProxy() {
        MockHttpServletRequest request = request("GET", "/api/personnel/rollcall/excel");
        request.addHeader("X-Forwarded-For", "6.6.6.6");
        assertEquals("127.0.0.1", interceptor.clientIp(request),
                "未配置可信代理:即使带 X-Forwarded-For 也必须用直连地址,防伪造");
    }

    @Test
    void clientIpTrustsForwardedHeaderOnlyFromConfiguredProxy() {
        MockHttpServletRequest request = request("GET", "/api/personnel/rollcall/excel");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 10.1.1.9");
        assertEquals("6.6.6.6", withProxy.clientIp(request),
                "直连方127.0.0.1在信任列表内:取XFF首项");
    }

    @Test
    void clientIpFallsBackToRemoteWhenForwardedBlankOrUnknown() {
        MockHttpServletRequest unknown = request("GET", "/api/fitness/export");
        unknown.addHeader("X-Forwarded-For", "unknown");
        assertEquals("127.0.0.1", withProxy.clientIp(unknown));
        MockHttpServletRequest empty = request("GET", "/api/fitness/export");
        empty.addHeader("X-Forwarded-For", "  ");
        assertEquals("127.0.0.1", withProxy.clientIp(empty));
    }

    @Test
    void clientIpHandlesCommaChainFromTrustedHop() {
        MockHttpServletRequest request = request("GET", "/api/fitness/export");
        request.setRemoteAddr("10.1.1.9");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 10.1.1.9");
        assertEquals("6.6.6.6", withProxy.clientIp(request));
    }

    @Test
    void queryWithoutExportOrWriteIsNotLogged() {
        interceptor.afterCompletion(request("GET", "/api/personnel"),
                new MockHttpServletResponse(), null, null);
        verifyNoInteractions(logs);
    }
}
