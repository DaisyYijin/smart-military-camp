package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.AuthInterceptor;
import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.ApiResponse;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 补课登记:按日期查看人员在位情况(休假/在外/在位),对缺席者登记补课。
 * 人员状态按日期推断——休假查 leave_record 区间,在外查 absence_record 区间,其余看 personnel.status。
 */
@RestController
@RequestMapping("/api/makeup")
public class MakeupController {

    private final JdbcTemplate jdbc;

    public MakeupController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 按日期查全量人员状态 + 已补课标记(管理端主列表) */
    @GetMapping("/status")
    public ApiResponse status(@RequestParam(value = "date", required = false) String date,
                              @RequestParam(value = "departmentId", required = false) Long departmentId) {
        if (date == null || date.isEmpty()) { date = java.time.LocalDate.now().toString(); }
        final String d = date;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.id, p.name, p.id_card, p.department_id, p.status AS current_status, p.rank AS rank, d.name AS dept_name " +
                "FROM personnel p LEFT JOIN department d ON p.department_id = d.id " +
                "WHERE " + DataScope.departmentSql("p.department_id") + " " +
                "ORDER BY p.name");
        if (departmentId != null && departmentId > 0) {
            Set<Long> deptIds = new HashSet<Long>();
            collectDeptIds(departmentId, deptIds);
            List<Map<String, Object>> filtered = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> r : rows) {
                Object did = r.get("department_id");
                if (did != null && deptIds.contains(((Number) did).longValue())) { filtered.add(r); }
            }
            rows = filtered;
        }
        /* 当日休假名单(带类型与区间,展示用) */
        Map<Long, String> leaveDetail = new HashMap<Long, String>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT personnel_id, leave_type, start_date, end_date FROM leave_record " +
                "WHERE start_date<=? AND end_date>=? AND personnel_id>0 ORDER BY id", d, d)) {
            long pid = ((Number) r.get("personnel_id")).longValue();
            String s = str(r.get("start_date")), e = str(r.get("end_date"));
            String brief = s.length() >= 10 ? s.substring(5, 10) : s;
            String briefE = e.length() >= 10 ? e.substring(5, 10) : e;
            leaveDetail.put(pid, str(r.get("leave_type")) + " " + brief + "~" + briefE);
        }
        /* 当日在外名单(未归队的 absence_record,带事由与区间) */
        Map<Long, String> absenceReason = new HashMap<Long, String>();
        Map<Long, String> absenceDetail = new HashMap<Long, String>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT personnel_id, reason, leave_date, expected_return_date FROM absence_record " +
                "WHERE leave_date<=? AND (actual_return_date='' OR actual_return_date>=?) AND personnel_id>0 ORDER BY id", d, d)) {
            long pid = ((Number) r.get("personnel_id")).longValue();
            String s = str(r.get("leave_date")), e = str(r.get("expected_return_date"));
            String brief = s.length() >= 10 ? s.substring(5, 10) : s;
            String briefE = e.isEmpty() ? "?" : (e.length() >= 10 ? e.substring(5, 10) : e);
            absenceReason.put(pid, str(r.get("reason")));
            absenceDetail.put(pid, brief + "~" + briefE);
        }
        /* 当日人员状态组装(纯查看:不再关联补课登记表) */
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new HashMap<String, Object>();
            long pid = ((Number) r.get("id")).longValue();
            item.put("personnelId", pid);
            item.put("name", r.get("name"));
            item.put("idCard", r.get("id_card"));
            item.put("rank", r.get("rank"));
            item.put("deptName", r.get("dept_name"));
            item.put("currentStatus", r.get("current_status"));
            String dayStatus;
            String detail = "";
            if (leaveDetail.containsKey(pid)) {
                dayStatus = "休假";
                detail = leaveDetail.get(pid);
            } else if (absenceReason.containsKey(pid)) {
                dayStatus = String.valueOf(absenceReason.get(pid));
                detail = absenceDetail.get(pid);
            } else {
                dayStatus = String.valueOf(r.get("current_status"));
                if (dayStatus == null || dayStatus.isEmpty() || "null".equals(dayStatus)) { dayStatus = "在位"; }
            }
            item.put("dayStatus", dayStatus);
            item.put("detail", detail);
            result.add(item);
        }
        return ApiResponse.ok(result);
    }

    /** 登记补课(支持单条或批量) */
    @PostMapping
    public ApiResponse create(@RequestBody Map<String, Object> body) {
        List<Number> personnelIds = castIds(body.get("personnelIds"));
        if (personnelIds == null || personnelIds.isEmpty()) { return ApiResponse.error("请选择要补课的人员"); }
        String date = str(body.get("makeupDate"));
        if (date == null || date.isEmpty()) { return ApiResponse.error("请指定补课日期"); }
        String subject = str(body.get("subject"));
        String remark = str(body.get("remark"));
        String createdBy = "";
        DataScope.Context ctx = DataScope.get();
        if (ctx != null && ctx.username != null) { createdBy = ctx.username; }
        int n = 0;
        for (Number pid : personnelIds) {
            DataScope.requirePerson(jdbc, pid.longValue());
            n += jdbc.update("INSERT OR IGNORE INTO makeup_record(personnel_id, makeup_date, subject, remark, created_by) VALUES(?,?,?,?,?)",
                    pid.longValue(), date, subject, remark, createdBy);
        }
        return ApiResponse.ok("已登记 " + n + " 人次补课");
    }

    /** 更新补课记录 */
    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireRecord(id);
        jdbc.update("UPDATE makeup_record SET subject=?, remark=?, updated_at=datetime('now','localtime') WHERE id=?",
                str(body.get("subject")), str(body.get("remark")), id);
        return ApiResponse.ok("已更新");
    }

    /** 删除补课记录 */
    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        requireRecord(id);
        jdbc.update("DELETE FROM makeup_record WHERE id=?", id);
        return ApiResponse.ok("已删除");
    }

    /** 查某人的补课记录(人员详情时间线用) */
    @GetMapping("/person/{personnelId}")
    public ApiResponse byPerson(@PathVariable Long personnelId) {
        DataScope.requirePerson(jdbc, personnelId);
        return ApiResponse.ok(jdbc.queryForList(
                "SELECT id, makeup_date, subject, remark, created_by, created_at FROM makeup_record WHERE personnel_id=? ORDER BY makeup_date DESC", personnelId));
    }

    private void requireRecord(Long id) {
        Long pid = jdbc.queryForObject("SELECT personnel_id FROM makeup_record WHERE id=?", Long.class, id);
        if (pid == null) { throw new IllegalArgumentException("补课记录不存在"); }
        DataScope.requirePerson(jdbc, pid);
    }

    private void collectDeptIds(Long id, Set<Long> acc) {
        acc.add(id);
        for (Map<String, Object> r : jdbc.queryForList("SELECT id FROM department WHERE parent_id=?", id)) {
            collectDeptIds(((Number) r.get("id")).longValue(), acc);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Number> castIds(Object raw) {
        if (raw instanceof List) {
            List<Number> out = new ArrayList<Number>();
            for (Object o : (List<Object>) raw) {
                if (o instanceof Number) { out.add((Number) o); }
                else { try { out.add(Long.parseLong(String.valueOf(o))); } catch (Exception ignore) { /* 跳过非法 */ } }
            }
            return out;
        }
        return null;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
