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
        MODULE_NAMES.put("departments", "单位管理");
        MODULE_NAMES.put("dict", "数据字典");
        MODULE_NAMES.put("equipment", "装备管理");
        MODULE_NAMES.put("material", "物资管理");
        MODULE_NAMES.put("item-loans", "物品借还");
        MODULE_NAMES.put("vehicle", "车辆管理");
        MODULE_NAMES.put("leave", "休假管理");
        MODULE_NAMES.put("leave-plan", "休假计划");
        MODULE_NAMES.put("setting", "系统设置");
        MODULE_NAMES.put("dashboard", "仪表盘");
        MODULE_NAMES.put("fitness", "体能考核");
        MODULE_NAMES.put("double-excellence", "双争评比");
        MODULE_NAMES.put("annual-appraisal", "年度考评");
        MODULE_NAMES.put("backup", "备份恢复");
        MODULE_NAMES.put("auth", "账号认证");
        MODULE_NAMES.put("accounts", "账号管理");
    }

    private final LogService logService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    /** 可信反向代理地址清单;仅当直连来源在清单内才采信 X-Forwarded-For,默认空=一律用直连地址 */
    private final java.util.Set<String> trustedProxies;

    @org.springframework.beans.factory.annotation.Autowired
    public OpLogInterceptor(LogService logService, org.springframework.jdbc.core.JdbcTemplate jdbc,
                            @org.springframework.beans.factory.annotation.Value("${app.auth.trusted-proxies:}") String trustedProxies) {
        this.logService = logService;
        this.jdbc = jdbc;
        java.util.Set<String> proxies = new java.util.HashSet<String>();
        if (trustedProxies != null) {
            for (String piece : trustedProxies.split(",")) {
                if (!piece.trim().isEmpty()) {
                    proxies.add(piece.trim());
                }
            }
        }
        this.trustedProxies = proxies;
    }

    /** 兼容构造:等价于无可信代理(测试与直连部署) */
    public OpLogInterceptor(LogService logService, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this(logService, jdbc, "");
    }

    /** 各模块按 ID 取"人能看懂"的记录名(删除前预取,删后就查不到了) */
    private static final Map<String, String> NAME_SQL = new HashMap<String, String>();

    static {
        NAME_SQL.put("personnel", "SELECT name FROM personnel WHERE id=?");
        NAME_SQL.put("departments", "SELECT name FROM department WHERE id=?");
        NAME_SQL.put("dict", "SELECT dict_type || ' · ' || dict_label FROM sys_dict WHERE id=?");
        NAME_SQL.put("equipment", "SELECT name FROM equipment WHERE id=?");
        NAME_SQL.put("material", "SELECT name FROM material WHERE id=?");
        NAME_SQL.put("item-loans", "SELECT item_name FROM item_loan WHERE id=?");
        NAME_SQL.put("vehicle", "SELECT plate_no FROM vehicle WHERE id=?");
        NAME_SQL.put("leave", "SELECT (SELECT name FROM personnel WHERE id=l.personnel_id) || '(' || l.start_date || '~' || l.end_date || ')' FROM leave_record l WHERE l.id=?");
        NAME_SQL.put("leave-plan", "SELECT (SELECT name FROM personnel WHERE id=l.personnel_id) || '(' || l.start_date || '~' || l.end_date || ')' FROM leave_plan l WHERE l.id=?");
        NAME_SQL.put("fitness", "SELECT (SELECT name FROM personnel WHERE id=f.personnel_id) || ' · ' || f.item FROM fitness_record f WHERE f.id=?");
        NAME_SQL.put("double-excellence", "SELECT p.name || ' · ' || r.year || '年第' || r.quarter || '季度' FROM double_excellence_record r JOIN personnel p ON p.id=r.personnel_id WHERE r.id=?");
        NAME_SQL.put("annual-appraisal", "SELECT p.name || ' · ' || r.year || '年' FROM annual_appraisal_record r JOIN personnel p ON p.id=r.personnel_id WHERE r.id=?");
        // 人员子记录:取所属人员姓名
        String[] subTables = {"position_history", "reward_punish", "child_info", "family_member",
                "driver_license", "personal_card", "private_vehicle", "housing_info"};
        for (String t : subTables) {
            NAME_SQL.put(t, "SELECT (SELECT name FROM personnel WHERE id=x.personnel_id) FROM " + t + " x WHERE x.id=?");
        }
    }

    /** 修改/删除前预取记录名称,挂到请求属性上供 afterCompletion 写日志 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            String method = request.getMethod();
            if (!"PUT".equalsIgnoreCase(method) && !"DELETE".equalsIgnoreCase(method)) {
                return true;
            }
            String uri = request.getRequestURI();
            String[] parts = uri == null ? new String[0] : uri.split("/");
            String moduleKey = parts.length > 2 ? parts[2] : "";
            String sub = parts.length > 3 ? parts[3] : "";
            if ("sub".equals(moduleKey) && parts.length > 4) {
                moduleKey = sub;
                sub = parts[4];
            }
            String sql = NAME_SQL.get(moduleKey);
            if (sql == null || sub == null || !sub.matches("[0-9]+")) {
                return true;
            }
            if ("double-excellence".equals(moduleKey) || "annual-appraisal".equals(moduleKey)) sql += " AND " + DataScope.departmentSql("p.department_id");
            java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList(sql, Long.parseLong(sub));
            if (!rows.isEmpty()) {
                Object v = rows.get(0).values().iterator().next();
                if (v != null && !String.valueOf(v).trim().isEmpty()) {
                    request.setAttribute("opLogName", String.valueOf(v));
                }
            }
        } catch (Exception ignore) {
            // 预取失败不影响业务,日志退回只记 ID
        }
        return true;
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
            boolean isExport = "GET".equalsIgnoreCase(method)
                    && (uri.contains("/export") || uri.endsWith("/excel"));
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
                action = exportAction(moduleKey, sub, moduleName);
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
                detail = recordDetail(request, sub);
            } else {
                action = "删除" + moduleName;
                detail = recordDetail(request, sub);
            }

            // 记录操作结果:失败/被拒绝的写操作与导出不再和成功操作混淆
            boolean success = ex == null && response.getStatus() < 400;
            if (!success) {
                action = action + "(未成功)";
                String status = "HTTP " + response.getStatus() + (ex != null ? " " + ex.getClass().getSimpleName() : "");
                detail = detail == null || detail.isEmpty() ? status : status + " · " + detail;
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

    /** 日志详情:优先带记录名「张三」,退化为 ID n;两者都有最稳妥 */
    private static String recordDetail(HttpServletRequest request, String sub) {
        if (sub == null || sub.isEmpty()) {
            return "";
        }
        String name = (String) request.getAttribute("opLogName");
        if (name != null) {
            return name + "(ID " + sub + ")";
        }
        return "ID " + sub;
    }

    /** 导出动作名:点名册/花名册不以 /export 结尾,单独命名;其余按模块名 */
    private static String exportAction(String moduleKey, String sub, String moduleName) {
        if ("personnel".equals(moduleKey) && "rollcall".equals(sub)) {
            return "导出点名册";
        }
        if ("personnel".equals(moduleKey) && "roster".equals(sub)) {
            return "导出花名册";
        }
        return "导出" + moduleName;
    }

    /**
     * 审计来源地址:默认一律用直连地址;仅当直连方是配置内可信反向代理时才采信
     * X-Forwarded-For 首项(多级链取最外层),避免客户端伪造该头污染审计日志。
     */
    public String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null || !trustedProxies.contains(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isEmpty() || "unknown".equalsIgnoreCase(forwarded)) {
            return remote;
        }
        int comma = forwarded.indexOf(',');
        String first = comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        return first.isEmpty() ? remote : first;
    }
}
