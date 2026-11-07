package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.AbsenceRecord;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.LeaveRecord;
import com.szjx.personnel.server.service.AbsenceService;
import com.szjx.personnel.server.service.LeaveService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 审批工作流(休假/在外申请):提交→管理员审批(通过/驳回+意见)。
 * 通过后自动创建休假/在外登记(复用各 Service 的完整校验与人员状态联动);
 * 提交时通知管理员,审批结果通知申请人,全程站内信留痕。
 */
@RestController
@RequestMapping("/api/approval")
public class ApprovalController {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final JdbcTemplate jdbc;
    private final LeaveService leaveService;
    private final AbsenceService absenceService;

    public ApprovalController(JdbcTemplate jdbc, LeaveService leaveService, AbsenceService absenceService) {
        this.jdbc = jdbc;
        this.leaveService = leaveService;
        this.absenceService = absenceService;
    }

    /** 列表:管理员看全部,子账号只看自己提交的 */
    @GetMapping
    public ApiResponse list(@RequestParam(required = false) String status) {
        DataScope.Context me = DataScope.get();
        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.type, a.applicant_id AS applicantId, a.payload, a.status, " +
                "a.approve_comment AS approveComment, a.decided_at AS decidedAt, a.created_at AS createdAt, " +
                "p.name AS applicantName, u.username AS createdByName FROM approval_request a " +
                "LEFT JOIN personnel p ON a.applicant_id=p.id " +
                "LEFT JOIN admin_user u ON a.created_by=u.id WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        if (!me.admin) {
            sql.append(" AND a.created_by=?");
            args.add(me.userId);
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND a.status=?");
            args.add(status.trim());
        }
        sql.append(" ORDER BY a.id DESC LIMIT 200");
        return ApiResponse.ok(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    /** 提交申请:type=leave|out, payload 为业务字段 JSON */
    @PostMapping
    @Transactional
    public ApiResponse submit(@RequestBody Map<String, Object> body) {
        DataScope.Context me = DataScope.get();
        String type = str(body.get("type"));
        long applicantId = longVal(body.get("applicantId"));
        if (!"leave".equals(type) && !"out".equals(type)) { return ApiResponse.error("申请类型不合法"); }
        if (applicantId <= 0) { return ApiResponse.error("请选择申请人"); }
        List<Map<String, Object>> person = jdbc.queryForList("SELECT id, name FROM personnel WHERE id=?", applicantId);
        if (person.isEmpty()) { return ApiResponse.error("所选人员不存在"); }
        String payload;
        try {
            payload = MAPPER.writeValueAsString(body.get("payload") == null ? Collections.emptyMap() : body.get("payload"));
        } catch (Exception e) { return ApiResponse.error("申请内容无效"); }
        jdbc.update("INSERT INTO approval_request(type, applicant_id, created_by, payload, status) VALUES(?,?,?,?,'pending')",
                type, applicantId, me.userId, payload);
        String label = "leave".equals(type) ? "休假" : "在外";
        // 通知管理员有待审批申请
        for (Map<String, Object> admin : jdbc.queryForList("SELECT id FROM admin_user WHERE username='admin'")) {
            MessageController.sendSystem(jdbc, ((Number) admin.get("id")).longValue(),
                    "新" + label + "申请",
                    String.valueOf(person.get(0).get("name")) + " 的" + label + "申请待审批,请到「审批中心」处理。");
        }
        return ApiResponse.ok();
    }

    /** 审批通过:自动创建休假/在外登记(校验失败则整体回滚,审批保持待定) */
    @PutMapping("/{id:\\d+}/approve")
    @Transactional
    public ApiResponse approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        DataScope.requireAdmin();
        DataScope.Context me = DataScope.get();
        Map<String, Object> row = find(id);
        if (row == null) { return ApiResponse.error("申请不存在"); }
        if (!"pending".equals(String.valueOf(row.get("status")))) { return ApiResponse.error("该申请已处理过"); }
        long applicantId = ((Number) row.get("applicant_id")).longValue();
        long createdBy = ((Number) row.get("created_by")).longValue();
        String type = String.valueOf(row.get("type"));
        String comment = body == null ? "" : str(body.get("comment"));
        Map<String, Object> p;
        try {
            p = MAPPER.readValue(String.valueOf(row.get("payload")), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) { return ApiResponse.error("申请内容解析失败"); }
        if ("leave".equals(type)) {
            LeaveRecord lr = new LeaveRecord();
            lr.setPersonnelId(applicantId);
            lr.setLeaveType(str(p.get("leaveType")));
            lr.setStartDate(str(p.get("startDate")));
            lr.setEndDate(str(p.get("endDate")));
            lr.setDays(p.get("days") == null || String.valueOf(p.get("days")).trim().isEmpty() ? null
                    : Double.valueOf(String.valueOf(p.get("days"))));
            lr.setRemark("审批通过自动登记" + (str(p.get("remark")).isEmpty() ? "" : ";" + str(p.get("remark"))));
            leaveService.create(lr);
        } else {
            AbsenceRecord ar = new AbsenceRecord();
            ar.setPersonnelId(applicantId);
            ar.setReason(str(p.get("reason")));
            ar.setLeaveDate(str(p.get("leaveDate")));
            ar.setExpectedReturnDate(str(p.get("expectedReturnDate")));
            ar.setRemark("审批通过自动登记" + (str(p.get("remark")).isEmpty() ? "" : ";" + str(p.get("remark"))));
            absenceService.create(ar);
        }
        jdbc.update("UPDATE approval_request SET status='approved', approver_id=?, approve_comment=?, " +
                "decided_at=datetime('now','localtime') WHERE id=? AND status='pending'", me.userId, comment, id);
        MessageController.sendSystem(jdbc, createdBy, "申请已通过",
                "你的" + ("leave".equals(type) ? "休假" : "在外") + "申请已通过并自动登记。"
                        + (comment.isEmpty() ? "" : "意见:" + comment));
        return ApiResponse.ok();
    }

    /** 驳回 */
    @PutMapping("/{id:\\d+}/reject")
    @Transactional
    public ApiResponse reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        DataScope.requireAdmin();
        DataScope.Context me = DataScope.get();
        Map<String, Object> row = find(id);
        if (row == null) { return ApiResponse.error("申请不存在"); }
        if (!"pending".equals(String.valueOf(row.get("status")))) { return ApiResponse.error("该申请已处理过"); }
        String comment = str(body.get("comment"));
        jdbc.update("UPDATE approval_request SET status='rejected', approver_id=?, approve_comment=?, " +
                "decided_at=datetime('now','localtime') WHERE id=? AND status='pending'",
                me.userId, comment, id);
        String type = String.valueOf(row.get("type"));
        MessageController.sendSystem(jdbc, ((Number) row.get("created_by")).longValue(), "申请被驳回",
                "你的" + ("leave".equals(type) ? "休假" : "在外") + "申请未通过。"
                        + (comment.isEmpty() ? "" : "意见:" + comment));
        return ApiResponse.ok();
    }

    /** 撤回自己 pending 的申请 */
    @DeleteMapping("/{id:\\d+}")
    @Transactional
    public ApiResponse withdraw(@PathVariable Long id) {
        DataScope.Context me = DataScope.get();
        int n = jdbc.update("DELETE FROM approval_request WHERE id=? AND created_by=? AND status='pending'",
                id, me.userId);
        if (n == 0) { return ApiResponse.error("只能撤回自己待审批的申请"); }
        return ApiResponse.ok();
    }

    private Map<String, Object> find(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM approval_request WHERE id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static long longVal(Object v) {
        if (v == null || String.valueOf(v).trim().isEmpty()) { return 0; }
        try { return Long.parseLong(String.valueOf(v).trim()); } catch (NumberFormatException e) { return 0; }
    }
}
