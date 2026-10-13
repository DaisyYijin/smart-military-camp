package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘近期事件:库存不足、休假归队、休假计划开始、车辆保险/年检到期等。
 * 全部由现有数据实时计算,无需额外维护。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/events")
    public ApiResponse events() {
        LocalDate today = LocalDate.now();
        String t0 = today.toString();
        String minus3 = today.minusDays(3).toString();
        String plus10 = today.plusDays(10).toString();
        String minus7 = today.minusDays(7).toString();
        String plus30 = today.plusDays(30).toString();

        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

        // 物资库存不足(当前数量 < 预警下限)
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT name, quantity, min_quantity FROM material " +
                "WHERE CAST(min_quantity AS REAL) > 0 AND CAST(quantity AS REAL) < CAST(min_quantity AS REAL) " +
                "ORDER BY CAST(quantity AS REAL) - CAST(min_quantity AS REAL) LIMIT 20")) {
            list.add(event("danger", "物资", "「" + r.get("name") + "」库存不足:当前 " + numStr(r.get("quantity"))
                    + " 件,低于预警下限 " + numStr(r.get("min_quantity")) + " 件", t0, 0));
        }

        // 人员休假归队(结束日期 近3天前 ~ 10天后)
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT p.name AS name, l.end_date AS d FROM leave_record l JOIN personnel p ON l.personnel_id = p.id " +
                "WHERE l.end_date >= ? AND l.end_date <= ? ORDER BY l.end_date", minus3, plus10)) {
            String d = String.valueOf(r.get("d"));
            if (d.compareTo(t0) < 0) {
                list.add(event("info", "休假", r.get("name") + " 已休假归队(" + fmtDate(d) + ")", d, 1));
            } else {
                list.add(event("info", "休假", r.get("name") + " 将于 " + fmtDate(d) + " 休假归队", d, 1));
            }
        }

        // 休假计划即将开始(计划中,开始日期 明天 ~ 10天后)
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT p.name AS name, l.start_date AS d FROM leave_plan l JOIN personnel p ON l.personnel_id = p.id " +
                "WHERE l.status = '计划中' AND l.start_date > ? AND l.start_date <= ? ORDER BY l.start_date", t0, plus10)) {
            String d = String.valueOf(r.get("d"));
            list.add(event("info", "计划", r.get("name") + " 的休假计划将于 " + fmtDate(d) + " 开始", d, 2));
        }

        // 车辆保险/年检到期(近7天内已到期 ~ 30天内将到期)
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT plate_no, insurance_expiry FROM vehicle WHERE insurance_expiry >= ? AND insurance_expiry <= ?",
                minus7, plus30)) {
            String d = String.valueOf(r.get("insurance_expiry"));
            boolean overdue = d.compareTo(t0) < 0;
            list.add(event(overdue ? "danger" : "warn", "车辆",
                    r.get("plate_no") + (overdue ? " 保险已到期(" : " 保险将于 ") + fmtDate(d) + (overdue ? ")" : " 到期"),
                    d, 3));
        }
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT plate_no, inspection_expiry FROM vehicle WHERE inspection_expiry >= ? AND inspection_expiry <= ?",
                minus7, plus30)) {
            String d = String.valueOf(r.get("inspection_expiry"));
            boolean overdue = d.compareTo(t0) < 0;
            list.add(event(overdue ? "danger" : "warn", "车辆",
                    r.get("plate_no") + (overdue ? " 年检已到期(" : " 年检将于 ") + fmtDate(d) + (overdue ? ")" : " 到期"),
                    d, 3));
        }

        // 按日期升序(最先发生的排前面),同日按严重程度
        Collections.sort(list, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                int c = String.valueOf(a.get("date")).compareTo(String.valueOf(b.get("date")));
                if (c != 0) {
                    return c;
                }
                return Integer.compare(((Number) a.get("order")).intValue(), ((Number) b.get("order")).intValue());
            }
        });
        if (list.size() > 50) {
            list = new ArrayList<Map<String, Object>>(list.subList(0, 50));
        }
        return ApiResponse.ok(list);
    }

    private static Map<String, Object> event(String type, String tag, String text, String date, int order) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("type", type);
        m.put("tag", tag);
        m.put("text", text);
        m.put("date", date);
        m.put("order", order);
        return m;
    }

    /** 数字显示:整数去掉小数尾巴(17.0 → 17) */
    private static String numStr(Object v) {
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.floor(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        return String.valueOf(v);
    }

    /** yyyy-MM-dd → M月d日 */
    private static String fmtDate(String d) {
        try {
            LocalDate x = LocalDate.parse(d);
            return x.getMonthValue() + "月" + x.getDayOfMonth() + "日";
        } catch (Exception e) {
            return d;
        }
    }
}
