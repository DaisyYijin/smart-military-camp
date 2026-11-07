package com.szjx.personnel.server.auth;

import com.szjx.personnel.server.service.AccountService;
import com.szjx.personnel.server.service.TokenManager;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.regex.Pattern;

/** Username tokens remain compatible, but no authorization data is trusted from a token. */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String HEADER_TOKEN = "X-Auth-Token";
    private final TokenManager tokenManager;
    private final AccountService accounts;

    public AuthInterceptor(TokenManager tokenManager, AccountService accounts) {
        this.tokenManager = tokenManager;
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // This also protects reused servlet workers when the previous request aborted early.
        DataScope.clear();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String token = request.getHeader(HEADER_TOKEN);
        String username = tokenManager.validate(token);
        if (username == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
            return false;
        }
        try {
            DataScope.Context context = accounts.authenticateScope(username);
            // A revocation during the database read must not admit this request.
            if (!username.equals(tokenManager.validate(token))) throw new ForbiddenException("会话已撤销，请重新登录");
            DataScope.set(context);
            if (DataScope.restricted() && !allowedRestricted(request)) {
                throw new ForbiddenException("子账号无权访问此接口");
            }
            request.setAttribute("authUser", username);
            return true;
        } catch (Exception e) {
            // MVC does not call afterCompletion on an interceptor whose preHandle throws.
            DataScope.clear();
            throw e;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        DataScope.clear();
    }

    private boolean matches(String value, String expression) { return Pattern.matches(expression, value); }

    /**
     * Positive route/method allowlist: new APIs require an explicit data-scope review.
     * Global report/backup/log, personnel undo, dict Excel and shared configuration writes
     * intentionally have no allow rule. OPTIONS cannot execute business handlers.
     */
    private boolean allowedRestricted(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()) path = path.substring(contextPath.length());
        // Refuse ambiguous encodings/path parameters rather than risk disagreement with MVC routing.
        if (path.contains("%") || path.contains(";") || path.contains("\\") || path.contains("//") ||
                path.contains("/./") || path.contains("/../")) return false;
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        String method = request.getMethod().toUpperCase(java.util.Locale.ROOT);
        if ("HEAD".equals(method)) method = "GET";
        String route = method + " " + path;
        if (matches(route, "GET /api/auth/me|POST /api/auth/(logout|change-password|change-username)")) return true;
        if (matches(route, "(GET|POST) /api/accounts|PUT /api/accounts/[0-9]+|GET /api/accounts/scope-preview")) return true;
        if (matches(route, "(GET|PUT) /api/preferences/(dashWidgets|dashStatCards)")) return true;
        if (matches(route, "GET /api/setting/(public|fitness_grade_rule|bmi_standard|dashWidgets|dashStatCards|siteName|siteTitle|siteSubtitle|siteLogo|siteFavicon|themeColor|pageSize)")) return true;
        // Dictionary data is shared, but no template/export/import operation is shared with children.
        if (matches(route, "GET /api/dict/(?!export$|template$|import-excel$)[A-Za-z0-9_-]+")) return true;
        if (matches(route, "GET /api/fitness-matrix(/items)?|GET /api/fitness-std/(matrix|weight)/template|GET /api/military/schemes")) return true;
        if (matches(route, "GET /api/stats/(summary|dashboard)|GET /api/dashboard/events")) return true;
        if (matches(route, "(GET|POST) /api/departments|(PUT|DELETE) /api/departments/[0-9]+")) return true;
        if (matches(route, "(GET|POST) /api/organization|(PUT|DELETE) /api/organization/[0-9]+|GET|POST|PUT|DELETE /api/organization/[0-9]+/(members(/|[0-9]+/?)?|columns|roster/excel)")) return true;
        if (matches(route, "(GET|POST) /api/personnel|(GET|PUT|DELETE) /api/personnel/[0-9]+")) return true;
        if (matches(route, "GET /api/personnel/([0-9]+/(age|photo)|by-idcard|options|rollcall/excel|roster/(fields|excel))")) return true;
        if (matches(route, "(GET|POST) /api/item-loans|(PUT|DELETE) /api/item-loans/[0-9]+|POST /api/item-loans/[0-9]+/return")) return true;
        if (matches(route, "(GET|POST) /api/(equipment|material|vehicle|phone|leave|leave-plan|fitness|military)")) return true;
        if (matches(route, "(PUT|DELETE) /api/(equipment|material|vehicle|phone|leave|leave-plan|fitness|military)/[0-9]+")) return true;
        if (matches(route, "GET /api/(equipment|material|vehicle)/[0-9]+/photo|GET /api/personnel/[0-9]+/transfers|GET /api/phone/by-person|GET /api/fitness/(stats|summary)|GET /api/leave/(check-travel|find-person|summary|holidays)")) return true;
        if (matches(route, "(GET|POST) /api/double-excellence|(GET|PUT|DELETE) /api/double-excellence/[0-9]+|GET /api/double-excellence/(stats|template|export)|POST /api/double-excellence/import-excel")) return true;
        if (matches(route, "(GET|POST) /api/annual-appraisal|(GET|PUT|DELETE) /api/annual-appraisal/[0-9]+|GET /api/annual-appraisal/(stats|template|export)|POST /api/annual-appraisal/import-excel")) return true;
        String kinds = "(position-history|reward-punish|children|family|driver|card|pvehicle|housing|fitness)";
        if (matches(route, "(GET|POST) /api/sub/" + kinds + "|(PUT|DELETE) /api/sub/" + kinds + "/[0-9]+")) return true;
        String modules = "(personnel|equipment|equipment-gun|material|vehicle|leave|leave-plan|phone|fitness|military)";
        return matches(route, "GET /api/" + modules + "/(export|template)|POST /api/" + modules + "/import-excel");
    }
}
