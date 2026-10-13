package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.LeaveRecord;
import com.szjx.personnel.server.model.PageResult;
import com.szjx.personnel.server.service.LeaveService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 休假接口
 */
@RestController
@RequestMapping("/api/leave")
public class LeaveController {

    private final LeaveService leaveService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public LeaveController(LeaveService leaveService,
                           org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.leaveService = leaveService;
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String personnelName,
                             @RequestParam(required = false) String idCard,
                             @RequestParam(required = false) String year,
                             @RequestParam(required = false) String leaveType,
                             @RequestParam(required = false) String startDate,
                             @RequestParam(required = false) String endDate,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "50") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 50;
        StringBuilder sql = new StringBuilder(
            "SELECT l.id, l.personnel_id AS personnelId, p.name AS personnelName, p.id_card AS idCard, " +
            "l.year, l.leave_type AS leaveType, l.start_date AS startDate, l.end_date AS endDate, l.days, " +
            "CAST(l.travel_days AS REAL) AS travelDays, CAST(l.holiday_days AS REAL) AS holidayDays, " +
            "l.holiday_name AS holidayName, " +
            "l.reason, l.remark, l.created_at AS createdAt, l.updated_at AS updatedAt " +
            "FROM leave_record l LEFT JOIN personnel p ON l.personnel_id = p.id WHERE 1=1");
        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM leave_record l LEFT JOIN personnel p ON l.personnel_id = p.id WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<Object>();
        java.util.List<Object> cargs = new java.util.ArrayList<Object>();
        if (personnelName != null && !personnelName.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ?"); args.add("%" + personnelName.trim() + "%");
            csql.append(" AND p.name LIKE ?"); cargs.add("%" + personnelName.trim() + "%");
        }
        if (idCard != null && !idCard.trim().isEmpty()) {
            sql.append(" AND p.id_card = ?"); args.add(idCard.trim());
            csql.append(" AND p.id_card = ?"); cargs.add(idCard.trim());
        }
        if (year != null && !year.trim().isEmpty()) {
            sql.append(" AND CAST(l.year AS TEXT) = ?"); args.add(year.trim());
            csql.append(" AND CAST(l.year AS TEXT) = ?"); cargs.add(year.trim());
        }
        if (leaveType != null && !leaveType.trim().isEmpty()) {
            sql.append(" AND l.leave_type = ?"); args.add(leaveType.trim());
            csql.append(" AND l.leave_type = ?"); cargs.add(leaveType.trim());
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND l.start_date >= ?"); args.add(startDate.trim());
            csql.append(" AND l.start_date >= ?"); cargs.add(startDate.trim());
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND l.end_date <= ?"); args.add(endDate.trim());
            csql.append(" AND l.end_date <= ?"); cargs.add(endDate.trim());
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());
        sql.append(" ORDER BY l.start_date DESC, l.id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((page - 1) * size);
        java.util.List<java.util.Map<String, Object>> list = jdbc.queryForList(sql.toString(), args.toArray());
        return ApiResponse.ok(new PageResult<java.util.Map<String, Object>>(total == null ? 0 : total, list));
    }

    /** 检查某人某年度是否已使用过路途天数 */
    @GetMapping("/check-travel")
    public ApiResponse checkTravel(@RequestParam Long personnelId, @RequestParam String year) {
        java.util.List<java.util.Map<String, Object>> used = jdbc.queryForList(
            "SELECT id, start_date, days FROM leave_record WHERE personnel_id=? AND CAST(year AS TEXT)=? AND CAST(travel_days AS REAL) > 0 ORDER BY start_date DESC",
            personnelId, year);
        return ApiResponse.ok(used != null && !used.isEmpty() ? used : null);
    }

    /** 按身份证号精确查找人员(休假表单自动带出姓名用) */
    @GetMapping("/find-person")
    public ApiResponse findPerson(@RequestParam String idCard) {
        return ApiResponse.ok(jdbc.queryForList(
            "SELECT p.id, p.name, p.id_card AS idCard, d.name AS departmentName FROM personnel p " +
            "LEFT JOIN department d ON p.department_id = d.id WHERE p.id_card = ? COLLATE NOCASE",
            idCard == null ? "" : idCard.trim()));
    }

    /** 某人某年度休假汇总:不含指定记录的基本+路途+节假日总天数 */
    @GetMapping("/summary")
    public ApiResponse summary(@RequestParam Long personnelId, @RequestParam String year,
                               @RequestParam(required = false) Long excludeId) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) AS cnt, IFNULL(SUM(IFNULL(CAST(days AS REAL),0) + IFNULL(CAST(travel_days AS REAL),0) + IFNULL(CAST(holiday_days AS REAL),0)), 0) AS totalDays " +
            "FROM leave_record WHERE personnel_id=? AND CAST(year AS TEXT)=?");
        java.util.List<Object> args = new java.util.ArrayList<Object>();
        args.add(personnelId);
        args.add(year == null ? "" : year.trim());
        if (excludeId != null) {
            sql.append(" AND id <> ?");
            args.add(excludeId);
        }
        java.util.Map<String, Object> m = jdbc.queryForMap(sql.toString(), args.toArray());
        return ApiResponse.ok(m);
    }

    /** 获取某年度的节假日列表(前端自动计算用) */
    @GetMapping("/holidays")
    public ApiResponse holidays(@RequestParam(required = false) String year) {
        if (year != null && !year.trim().isEmpty()) {
            return ApiResponse.ok(jdbc.queryForList(
                "SELECT holiday_date, holiday_name FROM holiday_calendar WHERE holiday_date LIKE ? ORDER BY holiday_date",
                year.trim() + "%"));
        }
        return ApiResponse.ok(jdbc.queryForList("SELECT holiday_date, holiday_name FROM holiday_calendar ORDER BY holiday_date"));
    }

    @PostMapping
    public ApiResponse create(@RequestBody LeaveRecord r) {
        return ApiResponse.ok(leaveService.create(r));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody LeaveRecord r) {
        return ApiResponse.ok(leaveService.update(id, r));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        leaveService.delete(id);
        return ApiResponse.ok();
    }
}
