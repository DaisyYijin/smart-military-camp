package com.szjx.personnel.server.auth;

import com.szjx.personnel.server.service.LogService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 操作日志拦截器:自动记录 /api/** 上的写操作与导入导出。
 * 登录/登出在 AuthController 里单独记录(那里能拿到成功失败与账号名)。
 */
@Component
public class OpLogInterceptor implements HandlerInterceptor {

    private static final Map<String, String> MODULE_NAMES = new HashMap<String, String>();

    static {
        MODULE_NAMES.put("personnel", "人员管理");
        MODULE_NAMES.put("position-history", "任职经历");
        MODULE_NAMES.put("reward-punish", "奖惩情况");
        MODULE_NAMES.put("children", "子女信息");
        MODULE_NAMES.put("family", "家庭成员");
        MODULE_NAMES.put("driver", "驾驶证");
        MODULE_NAMES.put("card", "个人卡号");
        MODULE_NAMES.put("pvehicle", "私家车");
        MODULE_NAMES.put("housing", "住房情况");
        MODULE_NAMES.put("departments", "部门管理");
        MODULE_NAMES.put("dict", "数据字典");
        MODULE_NAMES.put("equipment", "装备管理");
        MODULE_NAMES.put("material", "物资管理");
        MODULE_NAMES.put("vehicle", "车辆管理");
        MODULE_NAMES.put("leave", "休假管理");
        MODULE_NAMES.put("fitness", "体能考核");
        MODULE_NAMES.put("backup", "备份恢复");
        MODULE_NAMES.put("auth", "账号认证");
    }

    private final LogService logService;

    public OpLogInterceptor(LogService logService) {
        this.logService = logService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            if (uri == null || !uri.startsWith("/api/")) {
                return;
            }
            // 只记录写操作与导出(查询不记录,避免刷屏)
            boolean isWrite = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                    || "DELETE".equalsIgnoreCase(method);
            boolean isExport = "GET".equalsIgnoreCase(method) && uri.contains("/export");
            if (!isWrite && !isExport) {
                return;
            }

            String[] parts = uri.split("/"); // ["", "api", module, sub...]
            String moduleKey = parts.length > 2 ? parts[2] : "";
            String sub = parts.length > 3 ? parts[3] : "";
            if ("sub".equals(moduleKey) && parts.length > 3) {
                // /api/sub/<人员子记录类型>/<id>
                moduleKey = sub;
                sub = parts.length > 4 ? parts[4] : "";
            }
            String moduleName = MODULE_NAMES.containsKey(moduleKey) ? MODULE_NAMES.get(moduleKey) : moduleKey;

            String action;
            String detail;
            if (isExport) {
                action = "导出" + moduleName;
                detail = "Excel导出";
            } else if (sub.equals("import-excel") || sub.equals("import")) {
                action = "导入" + moduleName;
                detail = "Excel导入";
            } else if (moduleKey.equals("backup")) {
                action = backupAction(sub);
                detail = "备份管理";
            } else if ("POST".equalsIgnoreCase(method)) {
                action = "新增" + moduleName;
                detail = sub.isEmpty() ? "" : "ID " + sub;
            } else if ("PUT".equalsIgnoreCase(method)) {
                action = "修改" + moduleName;
                detail = sub.isEmpty() ? "" : "ID " + sub;
            } else {
                action = "删除" + moduleName;
                detail = sub.isEmpty() ? "" : "ID " + sub;
            }

            String username = (String) request.getAttribute("authUser");
            logService.record(username, action, detail, clientIp(request));
        } catch (Exception ignore) {
            // 日志记录绝不影响业务
        }
    }

    private String backupAction(String sub) {
        if (sub.equals("run")) {
            return "手动备份";
        }
        if (sub.equals("restore")) {
            return "恢复备份";
        }
        if (sub.equals("delete")) {
            return "删除备份";
        }
        if (sub.equals("settings")) {
            return "备份设置";
        }
        return "备份操作";
    }

    public static String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        return request.getRemoteAddr();
    }
}
