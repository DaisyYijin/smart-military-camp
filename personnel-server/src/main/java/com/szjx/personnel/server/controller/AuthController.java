package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.AuthInterceptor;
import com.szjx.personnel.server.dao.AdminUserDao;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.service.LoginGuard;
import com.szjx.personnel.server.service.PasswordUtil;
import com.szjx.personnel.server.service.TokenManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminUserDao adminUserDao;
    private final TokenManager tokenManager;
    private final LoginGuard loginGuard;

    @Value("${app.auth.token-expire-hours:24}")
    private int tokenExpireHours;

    public AuthController(AdminUserDao adminUserDao, TokenManager tokenManager, LoginGuard loginGuard) {
        this.adminUserDao = adminUserDao;
        this.tokenManager = tokenManager;
        this.loginGuard = loginGuard;
    }

    @PostMapping("/login")
    public ApiResponse login(@RequestBody(required = false) Map<String, String> body) {
        if (body == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        String username = body.get("username");
        String password = body.get("password");
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return ApiResponse.error("用户名和密码不能为空");
        }
        String key = username.trim();
        long locked = loginGuard.lockedRemainMillis(key);
        if (locked > 0) {
            return ApiResponse.error("登录失败次数过多,请 " + (locked / 1000 / 60 + 1) + " 分钟后再试");
        }
        String hash = adminUserDao.findPasswordHash(key);
        if (hash == null || !PasswordUtil.verify(password, hash)) {
            loginGuard.recordFailure(key);
            return ApiResponse.error("用户名或密码错误");
        }
        loginGuard.clear(key);
        tokenManager.setExpireMillis(tokenExpireHours * 3600L * 1000L);
        String token = tokenManager.create(key);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("token", token);
        data.put("displayName", adminUserDao.findDisplayName(key));
        data.put("username", key);
        return ApiResponse.ok(data);
    }

    @PostMapping("/logout")
    public ApiResponse logout(@RequestHeader(value = AuthInterceptor.HEADER_TOKEN, required = false) String token) {
        tokenManager.remove(token);
        return ApiResponse.ok();
    }

    @PostMapping("/change-password")
    public ApiResponse changePassword(@RequestBody(required = false) Map<String, String> body,
                                      HttpServletRequest request) {
        if (body == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        String username = (String) request.getAttribute("authUser");
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            return ApiResponse.error("旧密码和新密码不能为空");
        }
        if (newPassword.length() < 6) {
            return ApiResponse.error("新密码长度不能少于 6 位");
        }
        String hash = adminUserDao.findPasswordHash(username);
        if (hash == null || !PasswordUtil.verify(oldPassword, hash)) {
            return ApiResponse.error("旧密码错误");
        }
        adminUserDao.updatePassword(username, PasswordUtil.hash(newPassword));
        return ApiResponse.ok();
    }
}
