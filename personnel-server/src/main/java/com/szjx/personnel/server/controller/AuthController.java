package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.AuthInterceptor;
import com.szjx.personnel.server.dao.AdminUserDao;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.auth.OpLogInterceptor;
import com.szjx.personnel.server.service.LogService;
import com.szjx.personnel.server.service.LoginGuard;
import com.szjx.personnel.server.service.PasswordUtil;
import com.szjx.personnel.server.service.TokenManager;
import com.szjx.personnel.server.service.AccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
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
    private final LogService logService;
    private final AccountService accounts;
    private final OpLogInterceptor opLog;

    @Value("${app.auth.token-expire-hours:24}")
    private int tokenExpireHours;

    public AuthController(AdminUserDao adminUserDao, TokenManager tokenManager,
                          LoginGuard loginGuard, LogService logService, AccountService accounts,
                          OpLogInterceptor opLog) {
        this.adminUserDao = adminUserDao;
        this.tokenManager = tokenManager;
        this.loginGuard = loginGuard;
        this.logService = logService;
        this.accounts = accounts;
        this.opLog = opLog;
    }

    @PostMapping("/login")
    public ApiResponse login(@RequestBody(required = false) Map<String, String> body,
                             javax.servlet.http.HttpServletRequest request) {
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
        long tokenVersion = tokenManager.version(key);
        String hash = adminUserDao.findPasswordHash(key);
        if (hash == null || !PasswordUtil.verify(password, hash)) {
            loginGuard.recordFailure(key);
            logService.record(key, "登录失败", "密码错误或账号不存在",
                    opLog.clientIp(request));
            return ApiResponse.error("用户名或密码错误");
        }
        // Re-read the full ancestor chain before issuing any session.
        Map<String, Object> data = accounts.profile(key);
        loginGuard.clear(key);
        tokenManager.setExpireMillis(tokenExpireHours * 3600L * 1000L);
        String token = tokenManager.create(key, tokenVersion);
        data.put("token", token);
        logService.record(key, "登录成功", "网页登录",
                opLog.clientIp(request));
        return ApiResponse.ok(data);
    }

    @GetMapping("/me")
    public ApiResponse me(HttpServletRequest request) {
        return ApiResponse.ok(accounts.profile((String) request.getAttribute("authUser")));
    }

    @PostMapping("/logout")
    public ApiResponse logout(@RequestHeader(value = AuthInterceptor.HEADER_TOKEN, required = false) String token,
                              javax.servlet.http.HttpServletRequest request) {
        String username = tokenManager.validate(token);
        tokenManager.remove(token);
        logService.record(username, "退出登录", "", opLog.clientIp(request));
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
        accounts.changePassword(username, oldPassword, newPassword);
        return ApiResponse.ok();
    }

    @PostMapping("/change-username")
    public ApiResponse changeUsername(@RequestBody(required = false) Map<String, String> body,
                                      HttpServletRequest request) {
        if (body == null) {
            return ApiResponse.error(400, "请求体不能为空");
        }
        String username = (String) request.getAttribute("authUser");
        String oldPassword = body.get("oldPassword");
        String newUsername = body.get("newUsername");
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newUsername)) {
            return ApiResponse.error("当前密码和新账号不能为空");
        }
        accounts.changeUsername(username, oldPassword, newUsername);
        return ApiResponse.ok();
    }
}
