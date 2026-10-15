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
 * 休假计划(年度休假安排):与实际休假记录互补,状态 计划中/已执行/已取消。
 */
@RestController
@RequestMapping("/api/leave-plan")
public class LeavePlanController {

    private final JdbcTemplate jdbc;

    public LeavePlanController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String BASE =
            "SELECT lp.*, p.name AS personnel_name, p.id_card FROM leave_plan lp " +
                    "LEFT JOIN personnel p ON lp.personnel_id = p.id ";

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String personnelName,
                             @RequestParam(required = false) String year,
                             @RequestParam(required = false) Long personnelId,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 20;
        }
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        if (personnelName != null && !personnelName.trim().isEmpty()) {
            where.append(" AND p.name LIKE ?");
            args.add("%" + personnelName.trim() + "%");
        }
        if (year != null && !year.trim().isEmpty()) {
            where.append(" AND lp.year = ?");
            args.add(year.trim());
        }
        if (personnelId != null && personnelId > 0) {
            where.append(" AND lp.personnel_id = ?");
            args.add(personnelId);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM leave_plan lp LEFT JOIN personnel p ON lp.personnel_id = p.id" + where,
                Long.class, args.toArray());
        StringBuilder sql = new StringBuilder(BASE);
        sql.append(where).append(" ORDER BY lp.year DESC, lp.start_date, lp.id LIMIT ? OFFSET ?");
        args.add(size);
        args.add((long) (page - 1) * size);
        List<Map<String, Object>> list = jdbc.queryForList(sql.toString(), args.toArray());
        return ApiResponse.ok(new PageResult<Map<String, Object>>(total, list));
    }

    @PostMapping
    public ApiResponse create(@RequestBody Map<String, Object> m) {
        validate(m);
        jdbc.update("INSERT INTO leave_plan(personnel_id, year, leave_type, start_date, end_date, days, remark) VALUES(?,?,?,?,?,?,?)",
                lng(m.get("personnelId")), str(m.get("year")), str(m.get("leaveType")),
                str(m.get("startDate")), str(m.get("endDate")), num(m.get("days")), str(m.get("remark")));
        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        return ApiResponse.ok(findOne(id));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Map<String, Object> m) {
        if (findOne(id) == null) {
            return ApiResponse.error("计划不存在");
        }
        validate(m);
        jdbc.update("UPDATE leave_plan SET personnel_id=?, year=?, leave_type=?, start_date=?, end_date=?, days=?, remark=?," +
                        " updated_at=datetime('now','localtime') WHERE id=?",
                lng(m.get("personnelId")), str(m.get("year")), str(m.get("leaveType")),
                str(m.get("startDate")), str(m.get("endDate")), num(m.get("days")), str(m.get("remark")), id);
        return ApiResponse.ok(findOne(id));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM leave_plan WHERE id=?", id);
        return ApiResponse.ok();
    }

    private Map<String, Object> findOne(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(BASE + " WHERE lp.id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void validate(Map<String, Object> m) {
        Long pid = lng(m.get("personnelId"));
        if (pid <= 0) {
            throw new IllegalArgumentException("请先选择人员");
        }
        if (jdbc.queryForList("SELECT id FROM personnel WHERE id=?", pid).isEmpty()) {
            throw new IllegalArgumentException("所选人员不存在");
        }
        if (str(m.get("startDate")).isEmpty() || str(m.get("endDate")).isEmpty()) {
            throw new IllegalArgumentException("请填写计划的开始与结束日期");
        }
        if (str(m.get("endDate")).compareTo(str(m.get("startDate"))) < 0) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        double days = num(m.get("days"));
        if (days < 0) {
            throw new IllegalArgumentException("天数不能为负");
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static double num(Object o) {
        if (o == null || String.valueOf(o).trim().isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Long lng(Object o) {
        if (o == null || String.valueOf(o).trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(String.valueOf(o).trim());
            } catch (NumberFormatException e2) {
                return 0L;
            }
        }
    }
}
