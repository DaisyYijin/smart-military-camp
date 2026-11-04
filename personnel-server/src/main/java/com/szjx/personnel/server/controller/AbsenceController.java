package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.AbsenceRecord;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.PageResult;
import com.szjx.personnel.server.service.AbsenceService;
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
 * 人员在外登记接口
 */
@RestController
@RequestMapping("/api/absence")
public class AbsenceController {

    private final AbsenceService absenceService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public AbsenceController(AbsenceService absenceService,
                             org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.absenceService = absenceService;
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String personnelName,
                             @RequestParam(required = false) String idCard,
                             @RequestParam(required = false) String reason,
                             @RequestParam(required = false) String leaveDateFrom,
                             @RequestParam(required = false) String leaveDateTo,
                             @RequestParam(required = false) Integer returned,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 20;
        StringBuilder sql = new StringBuilder(
            "SELECT a.id, a.personnel_id AS personnelId, p.name AS personnelName, p.id_card AS idCard, p.department_id AS pDepartmentId, " +
            "a.reason, a.leave_date AS leaveDate, a.expected_return_date AS expectedReturnDate, " +
            "a.actual_return_date AS actualReturnDate, a.remark, a.created_at AS createdAt, a.updated_at AS updatedAt, " +
            "p.status AS personnelStatus " +
            "FROM absence_record a LEFT JOIN personnel p ON a.personnel_id = p.id WHERE 1=1");
        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM absence_record a LEFT JOIN personnel p ON a.personnel_id = p.id WHERE 1=1");
        sql.append(" AND ").append(DataScope.personSql("a.personnel_id"));
        csql.append(" AND ").append(DataScope.personSql("a.personnel_id"));
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
        if (reason != null && !reason.trim().isEmpty()) {
            sql.append(" AND a.reason = ?"); args.add(reason.trim());
            csql.append(" AND a.reason = ?"); cargs.add(reason.trim());
        }
        if (leaveDateFrom != null && !leaveDateFrom.trim().isEmpty()) {
            sql.append(" AND a.leave_date >= ?"); args.add(leaveDateFrom.trim());
            csql.append(" AND a.leave_date >= ?"); cargs.add(leaveDateFrom.trim());
        }
        if (leaveDateTo != null && !leaveDateTo.trim().isEmpty()) {
            sql.append(" AND a.leave_date <= ?"); args.add(leaveDateTo.trim());
            csql.append(" AND a.leave_date <= ?"); cargs.add(leaveDateTo.trim());
        }
        if (returned != null) {
            String cond = returned == 1 ? " AND a.actual_return_date <> ''" : " AND a.actual_return_date = ''";
            sql.append(cond);
            csql.append(cond);
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());
        sql.append(" ORDER BY (a.actual_return_date=''), a.leave_date DESC, a.id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((long) (page - 1) * size);
        List<Map<String, Object>> list = jdbc.queryForList(sql.toString(), args.toArray());
        // 列表附带人员编制单位完整路径(无斜杠层级)
        com.szjx.personnel.server.util.DepartmentHierarchy.fillPath(jdbc, list, "pDepartmentId", "departmentPath");
        return ApiResponse.ok(new PageResult<Map<String, Object>>(total == null ? 0 : total, list));
    }

    @PostMapping
    public ApiResponse create(@RequestBody AbsenceRecord r) {
        return ApiResponse.ok(absenceService.create(r));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody AbsenceRecord r) {
        return ApiResponse.ok(absenceService.update(id, r));
    }

    /** 行内一键归队:不传 date 默认今天;人员状态恢复"在位"。 */
    @PostMapping("/{id}/return")
    public ApiResponse markReturned(@PathVariable Long id,
                                    @RequestParam(required = false) String date) {
        return ApiResponse.ok(absenceService.markReturned(id, date));
    }

    /** 延期:顺延未归记录的预计返回日期。 */
    @PostMapping("/{id}/postpone")
    public ApiResponse postpone(@PathVariable Long id, @RequestParam String date) {
        return ApiResponse.ok(absenceService.postpone(id, date));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        absenceService.delete(id);
        return ApiResponse.ok();
    }
}
