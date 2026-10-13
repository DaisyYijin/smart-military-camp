package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 休假计划接口:预先安排的休假意向。
 * 天数由服务端按 开始/结束日期 重算(end - start + 1),不信任前端传值。
 */
@RestController
@RequestMapping("/api/leave-plan")
public class LeavePlanController {

    private final JdbcTemplate jdbc;

    public LeavePlanController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String personnelName,
                             @RequestParam(required = false) String idCard,
                             @RequestParam(required = false) String year,
                             @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "50") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 50;
        StringBuilder sql = new StringBuilder(
                "SELECT l.id, l.personnel_id AS personnelId, p.name AS personnelName, p.id_card AS idCard, " +
                "l.year, l.start_date AS startDate, l.end_date AS endDate, CAST(l.days AS REAL) AS days, " +
                "l.status, l.remark, l.created_at AS createdAt, l.updated_at AS updatedAt " +
                "FROM leave_plan l LEFT JOIN personnel p ON l.personnel_id = p.id WHERE 1=1");
        StringBuilder csql = new StringBuilder(
                "SELECT COUNT(*) FROM leave_plan l LEFT JOIN personnel p ON l.personnel_id = p.id WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        List<Object> cargs = new ArrayList<Object>();
        if (personnelName != null && !personnelName.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ?"); args.add("%" + personnelName.trim() + "%");
            csql.append(" AND p.name LIKE ?"); cargs.add("%" + personnelName.trim() + "%");
        }
        if (idCard != null && !idCard.trim().isEmpty()) {
            sql.append(" AND p.id_card LIKE ?"); args.add("%" + idCard.trim() + "%");
            csql.append(" AND p.id_card LIKE ?"); cargs.add("%" + idCard.trim() + "%");
        }
        if (year != null && !year.trim().isEmpty()) {
            sql.append(" AND CAST(l.year AS TEXT) = ?"); args.add(year.trim());
            csql.append(" AND CAST(l.year AS TEXT) = ?"); cargs.add(year.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND l.status = ?"); args.add(status.trim());
            csql.append(" AND l.status = ?"); cargs.add(status.trim());
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());
        sql.append(" ORDER BY l.start_date DESC, l.id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((page - 1) * size);
        List<Map<String, Object>> list = jdbc.queryForList(sql.toString(), args.toArray());
        return ApiResponse.ok(new PageResult<Map<String, Object>>(total == null ? 0 : total, list));
    }

    @PostMapping
    public ApiResponse create(@RequestBody Map<String, Object> r) {
        normDates(r);
        String err = validate(r);
        if (err != null) {
            return ApiResponse.error(err);
        }
        jdbc.update("INSERT INTO leave_plan(personnel_id, year, start_date, end_date, days, status, remark) VALUES(?,?,?,?,?,?,?)",
                lng(r.get("personnelId")), str(r.get("year")), str(r.get("startDate")), str(r.get("endDate")),
                calcDays(r), statusOf(r), str(r.get("remark")));
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Map<String, Object> r) {
        normDates(r);
        String err = validate(r);
        if (err != null) {
            return ApiResponse.error(err);
        }
        int n = jdbc.update("UPDATE leave_plan SET personnel_id=?, year=?, start_date=?, end_date=?, days=?, status=?, remark=?, " +
                        "updated_at=datetime('now','localtime') WHERE id=?",
                lng(r.get("personnelId")), str(r.get("year")), str(r.get("startDate")), str(r.get("endDate")),
                calcDays(r), statusOf(r), str(r.get("remark")), id);
        if (n == 0) {
            return ApiResponse.error("记录不存在或已被删除");
        }
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM leave_plan WHERE id=?", id);
        return ApiResponse.ok();
    }

    /** 日期规范:输入到月(yyyy-MM)的按当月 1 号处理 */
    private static void normDates(Map<String, Object> r) {
        String[] keys = {"startDate", "endDate"};
        for (String k : keys) {
            String v = str(r.get(k));
            if (v.length() == 7) {
                r.put(k, v + "-01");
            }
        }
    }

    /** 校验并规范:人员必填、日期必填且结束>=开始;返回错误信息或 null */
    private String validate(Map<String, Object> r) {
        if (lng(r.get("personnelId")) == 0) {
            return "请先填写身份证号带出人员";
        }
        String s = str(r.get("startDate"));
        String e = str(r.get("endDate"));
        if (s.isEmpty() || e.isEmpty()) {
            return "计划开始日期和结束日期都必填";
        }
        try {
            java.time.LocalDate d1 = java.time.LocalDate.parse(s.trim());
            java.time.LocalDate d2 = java.time.LocalDate.parse(e.trim());
            if (d2.isBefore(d1)) {
                return "计划结束日期不能早于开始日期";
            }
        } catch (Exception ex) {
            return "日期格式不正确(应为 yyyy-MM-dd)";
        }
        return null;
    }

    private double calcDays(Map<String, Object> r) {
        java.time.LocalDate d1 = java.time.LocalDate.parse(str(r.get("startDate")).trim());
        java.time.LocalDate d2 = java.time.LocalDate.parse(str(r.get("endDate")).trim());
        return java.time.temporal.ChronoUnit.DAYS.between(d1, d2) + 1;
    }

    private String statusOf(Map<String, Object> r) {
        String s = str(r.get("status"));
        return s.isEmpty() ? "计划中" : s;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Long lng(Object v) {
        if (v == null || String.valueOf(v).trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
