package com.szjx.personnel.server.auth;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.service.TokenManager;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录认证拦截器:校验 X-Auth-Token 请求头
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String HEADER_TOKEN = "X-Auth-Token";

    private final TokenManager tokenManager;

    public AuthInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader(HEADER_TOKEN);
        String username = tokenManager.validate(token);
        if (username == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            response.getWriter().write(toJson(ApiResponse.error(401, "未登录或登录已过期")));
            return false;
        }
        request.setAttribute("authUser", username);
        return true;
    }

    private String toJson(ApiResponse r) {
        // 手写简单 JSON,避免引入序列化依赖
        return "{\"code\":" + r.getCode() + ",\"message\":\"" + r.getMessage() + "\",\"data\":" +
                (r.getData() == null ? "null" : String.valueOf(r.getData())) + "}";
    }
}
