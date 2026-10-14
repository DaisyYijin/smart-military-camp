package com.szjx.personnel.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 数据上报业务逻辑:导出/预检/导入合并。
 * 从 ReportController 抽取,Controller 只做 HTTP 层。
 */
@Service
public class ReportService {

    public final JdbcTemplate jdbc;
    public final ObjectMapper mapper = new ObjectMapper();

    /** 合法表名白名单(防SQL注入) */
    public static final java.util.Set<String> SAFE_TABLES = new java.util.HashSet<String>(java.util.Arrays.asList(
            "personnel", "department", "equipment", "material", "vehicle",
            "leave_record", "fitness_record", "position_history", "reward_punish",
            "child_info", "family_member", "driver_license", "personal_card",
            "private_vehicle", "housing_info"));

    public static String safeTable(String t) {
        if (t == null || !SAFE_TABLES.contains(t)) {
            throw new IllegalArgumentException("非法表名: " + t);
        }
        return t;
    }

    public static final String[] SUB_TABLES = {
            "position_history", "reward_punish", "child_info", "family_member",
            "driver_license", "personal_card", "private_vehicle", "housing_info"};

    /** 导入进度注册表 */
    protected static final java.util.concurrent.ConcurrentHashMap<String, Map<String, Object>> PROGRESS =
            new java.util.concurrent.ConcurrentHashMap<String, Map<String, Object>>();

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> getProgress(String id) {
        Map<String, Object> m = PROGRESS.get(id);
        return m == null ? new LinkedHashMap<String, Object>() : m;
    }

    public void clearProgress(String id) {
        if (id != null) { PROGRESS.remove(id); }
    }

    protected void prog(String pid, int pct, String stage, String detail) {
        if (pid == null) { return; }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("pct", pct);
        m.put("stage", stage);
        m.put("detail", detail == null ? "" : detail);
        PROGRESS.put(pid, m);
    }

    // === 表中文名 ===
    public static String tableLabel(String table) {
        String[][] zh = {{"position_history", "任职经历"}, {"reward_punish", "奖惩情况"}, {"child_info", "子女信息"},
                {"family_member", "家庭成员"}, {"driver_license", "驾驶证"}, {"personal_card", "个人卡号"},
                {"private_vehicle", "私家车"}, {"housing_info", "住房情况"}, {"equipment", "装备"},
                {"material", "物资"}, {"vehicle", "车辆"}};
        for (String[] z : zh) {
            if (z[0].equals(table)) { return z[1]; }
        }
        return table;
    }

    // === key 辅助 ===
    public static String key(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            if (sb.length() > 0) { sb.append('|'); }
            sb.append(p == null ? "" : String.valueOf(p));
        }
        return sb.toString();
    }

    // === 身份证 → 人员ID ===
    public static Long pidOfCard(Object card, Map<String, Long> pidByCard) {
        if (card == null) { return null; }
        return pidByCard.get(String.valueOf(card).trim().toUpperCase());
    }

    // === 通用列名/占位符 ===
    public static List<String> colsOf(Map<String, Object> row) {
        List<String> out = new ArrayList<String>();
        for (String k : row.keySet()) {
            if (!k.startsWith("_")) { out.add(k); }
        }
        return out;
    }

    public static String joinList(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (String c : cols) {
            if (sb.length() > 0) { sb.append(","); }
            sb.append(c);
        }
        return sb.toString();
    }

    public static String marks(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) { sb.append(","); }
            sb.append("?");
        }
        return sb.toString();
    }

    public static Object[] appendHead(Long head, List<Object> tail) {
        Object[] out = new Object[tail.size() + 1];
        out[0] = head;
        for (int i = 0; i < tail.size(); i++) {
            out[i + 1] = tail.get(i);
        }
        return out;
    }
}
