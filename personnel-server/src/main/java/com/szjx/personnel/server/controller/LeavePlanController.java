package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.DataScope;

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
            "SELECT lp.*, p.name AS personnel_name, p.id_card, p.department_id AS p_department_id FROM leave_plan lp " +
                    "LEFT JOIN personnel p ON lp.personnel_id = p.id ";

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String personnelName,
                             @RequestParam(required = false) String idCard,
                             @RequestParam(required = false) String year,
                             @RequestParam(required = false) String leaveType,
                             @RequestParam(required = false) String month,
                             @RequestParam(required = false) String startDate,
                             @RequestParam(required = false) String endDate,
                             @RequestParam(required = false) Long personnelId,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 20;
        }
        StringBuilder where = new StringBuilder(" WHERE ").append(DataScope.personSql("lp.personnel_id"));
        List<Object> args = new ArrayList<Object>();
        if (personnelName != null && !personnelName.trim().isEmpty()) {
            where.append(" AND p.name LIKE ?");
            args.add("%" + personnelName.trim() + "%");
        }
        if (idCard != null && !idCard.trim().isEmpty()) {
            where.append(" AND p.id_card LIKE ?");
            args.add("%" + idCard.trim() + "%");
        }
        if (year != null && !year.trim().isEmpty()) {
            where.append(" AND lp.year = ?");
            args.add(year.trim());
        }
        if (leaveType != null && !leaveType.trim().isEmpty()) {
            where.append(" AND lp.leave_type = ?");
            args.add(leaveType.trim());
        }
        if (month != null && !month.trim().isEmpty()) {
            String selectedMonth = month.trim();
            if (!selectedMonth.matches("\\d{4}-\\d{2}") || Integer.parseInt(selectedMonth.substring(5)) < 1
                    || Integer.parseInt(selectedMonth.substring(5)) > 12) {
                throw new IllegalArgumentException("月份格式应为 YYYY-MM");
            }
            where.append(" AND lp.start_date <= ? AND lp.end_date >= ?");
            args.add(selectedMonth + "-31");
            args.add(selectedMonth + "-01");
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            where.append(" AND lp.start_date >= ?");
            args.add(startDate.trim());
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            where.append(" AND lp.end_date <= ?");
            args.add(endDate.trim());
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
        // 列表附带人员编制单位完整路径(无斜杠层级;snake_case 与本接口字段风格一致)
        com.szjx.personnel.server.util.DepartmentHierarchy.fillPath(jdbc, list, "p_department_id", "department_path");
        return ApiResponse.ok(new PageResult<Map<String, Object>>(total, list));
    }

    @PostMapping
    public ApiResponse create(@RequestBody Map<String, Object> m) {
        validate(m);
        Long id = jdbc.queryForObject("INSERT INTO leave_plan(personnel_id, year, leave_type, start_date, end_date, days, remark) VALUES(?,?,?,?,?,?,?) RETURNING id",
                Long.class, lng(m.get("personnelId")), str(m.get("year")), str(m.get("leaveType")),
                str(m.get("startDate")), str(m.get("endDate")), num(m.get("days")), str(m.get("remark")));
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
        DataScope.requireRecord(jdbc, "leave_plan", "personnel_id", id);
        jdbc.update("DELETE FROM leave_plan WHERE id=?", id);
        return ApiResponse.ok();
    }

    private Map<String, Object> findOne(Long id) {
        DataScope.requireRecord(jdbc, "leave_plan", "personnel_id", id);
        List<Map<String, Object>> rows = jdbc.queryForList(BASE + " WHERE lp.id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void validate(Map<String, Object> m) {
        Long pid = lng(m.get("personnelId"));
        DataScope.requirePerson(jdbc, pid);
        if (pid <= 0) {
            throw new IllegalArgumentException("请先选择人员");
        }
        if (jdbc.queryForList("SELECT id FROM personnel WHERE id=?", pid).isEmpty()) {
            throw new IllegalArgumentException("所选人员不存在");
        }
        String year = str(m.get("year"));
        if (year.isEmpty()) {
            throw new IllegalArgumentException("请填写年度");
        }
        if (!year.matches("[0-9]{4}") || Integer.parseInt(year) < 1900) {
            throw new IllegalArgumentException("年度应为1900至9999的4位数字");
        }
        if (str(m.get("leaveType")).isEmpty()) {
            throw new IllegalArgumentException("请选择休假类型");
        }
        String startDate = str(m.get("startDate"));
        String endDate = str(m.get("endDate"));
        if (startDate.isEmpty() || endDate.isEmpty()) {
            throw new IllegalArgumentException("请填写计划的开始与结束日期");
        }
        java.time.LocalDate start = parseDate(startDate, "开始");
        java.time.LocalDate end = parseDate(endDate, "结束");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        // 严格解析通过后统一写回 yyyy-MM-dd 规范格式,避免接口层入库带空格或臆造格式
        m.put("startDate", start.toString());
        m.put("endDate", end.toString());
        double days;
        try {
            days = Double.parseDouble(str(m.get("days")));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("天数必须为数字");
        }
        if (!Double.isFinite(days)) {
            throw new IllegalArgumentException("天数必须为有限数字");
        }
        if (days < 0) {
            throw new IllegalArgumentException("天数不能为负");
        }
    }

    /** 与休假登记同口径:严格按 yyyy-MM-dd 解析真实存在的日期 */
    private static java.time.LocalDate parseDate(String date, String label) {
        if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException(label + "日期格式应为yyyy-MM-dd");
        }
        try {
            return java.time.LocalDate.parse(date);
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException(label + "日期不存在");
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
