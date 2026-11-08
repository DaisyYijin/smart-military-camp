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
                              @RequestParam(value = "endDate", required = false) String endDate,
                              @RequestParam(value = "departmentId", required = false) Long departmentId) {
        if (date == null || date.isEmpty()) { date = java.time.LocalDate.now().toString(); }
        final String dStart = date;
        /* endDate 为空 = 单日模式(与 date 相同);否则区间模式:区间内任一天命中即计入,详情聚合所有命中段 */
        final String dEnd = (endDate == null || endDate.isEmpty()) ? date : endDate;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.id, p.name, p.id_card, p.department_id, p.status AS current_status, p.rank AS rank, " +
                "p.category AS category, p.phone AS phone, p.gender AS gender, p.political_status AS political_status, " +
                "p.post AS post, p.nation AS nation, p.education AS education, p.entry_date AS entry_date, " +
                "p.native_place AS native_place, p.marital_status AS marital_status, d.name AS dept_name " +
                "FROM personnel p LEFT JOIN department d ON p.department_id = d.id " +
                "WHERE " + DataScope.departmentSql("p.department_id") + " " +
                "ORDER BY p.name");
        /* 单位完整层级路径:全表读 Map,沿 parent 链向上拼接(如"一营/一连/一排/一班") */
        Map<Long, String> deptNames = new HashMap<Long, String>();
        Map<Long, Long> deptParents = new HashMap<Long, Long>();
        for (Map<String, Object> r0 : jdbc.queryForList("SELECT id, name, parent_id FROM department")) {
            deptNames.put(((Number) r0.get("id")).longValue(), String.valueOf(r0.get("name")));
            Object pid0 = r0.get("parent_id");
            deptParents.put(((Number) r0.get("id")).longValue(), pid0 == null ? 0L : ((Number) pid0).longValue());
        }
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
        /* 休假名单(与筛选区间相交即命中;多人多段聚合展示) */
        Map<Long, String> leaveDetail = new HashMap<Long, String>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT personnel_id, leave_type, start_date, end_date FROM leave_record " +
                "WHERE start_date<=? AND end_date>=? AND personnel_id>0 ORDER BY personnel_id, start_date", dEnd, dStart)) {
            long pid = ((Number) r.get("personnel_id")).longValue();
            String s = str(r.get("start_date")), e = str(r.get("end_date"));
            String brief = s.length() >= 10 ? s.substring(5, 10) : s;
            String briefE = e.length() >= 10 ? e.substring(5, 10) : e;
            String seg = str(r.get("leave_type")) + " " + brief + "~" + briefE;
            leaveDetail.put(pid, leaveDetail.containsKey(pid) ? leaveDetail.get(pid) + "、" + seg : seg);
        }
        /* 在外名单(未归队且与筛选区间相交;多人多段聚合) */
        Map<Long, String> absenceReason = new HashMap<Long, String>();
        Map<Long, String> absenceDetail = new HashMap<Long, String>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT personnel_id, reason, leave_date, expected_return_date FROM absence_record " +
                "WHERE leave_date<=? AND (actual_return_date='' OR actual_return_date>=?) AND personnel_id>0 ORDER BY personnel_id, leave_date", dEnd, dStart)) {
            long pid = ((Number) r.get("personnel_id")).longValue();
            String s = str(r.get("leave_date")), e = str(r.get("expected_return_date"));
            String brief = s.length() >= 10 ? s.substring(5, 10) : s;
            String briefE = e.isEmpty() ? "?" : (e.length() >= 10 ? e.substring(5, 10) : e);
            String seg = brief + "~" + briefE;
            if (absenceDetail.containsKey(pid)) {
                absenceDetail.put(pid, absenceDetail.get(pid) + "、" + seg);
            } else {
                absenceReason.put(pid, str(r.get("reason")));
                absenceDetail.put(pid, seg);
            }
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
            item.put("category", r.get("category"));
            item.put("phone", r.get("phone"));
            item.put("gender", r.get("gender"));
            item.put("politicalStatus", r.get("political_status"));
            item.put("post", r.get("post"));
            item.put("nation", r.get("nation"));
            item.put("education", r.get("education"));
            item.put("entryDate", r.get("entry_date"));
            item.put("nativePlace", r.get("native_place"));
            item.put("maritalStatus", r.get("marital_status"));
            StringBuilder deptPath = new StringBuilder();
            Long did = r.get("department_id") == null ? null : ((Number) r.get("department_id")).longValue();
            while (did != null && did > 0 && deptNames.containsKey(did)) {
                if (deptPath.length() > 0) { deptPath.insert(0, "/"); }
                deptPath.insert(0, deptNames.get(did));
                did = deptParents.get(did);
            }
            item.put("deptName", deptPath.length() > 0 ? deptPath.toString() : String.valueOf(r.get("dept_name") == null ? "" : r.get("dept_name")));
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

    /** 按当前筛选导出在位情况 Excel(复用 status 数据组装,10 基础列) */
    @GetMapping("/export")
    @SuppressWarnings("unchecked")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "departmentId", required = false) Long departmentId) throws Exception {
        ApiResponse resp = status(date, endDate, departmentId);
        Object data = resp == null ? null : resp.getData();
        List<Map<String, Object>> rows = data instanceof List ? (List<Map<String, Object>>) data : new ArrayList<Map<String, Object>>();
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sh = wb.createSheet("在位情况");
            String[] heads = {"序号", "姓名", "军衔", "类别", "身份证号", "单位", "当日状态", "详情", "档案状态", "联系电话"};
            org.apache.poi.ss.usermodel.Row hr = sh.createRow(0);
            for (int i = 0; i < heads.length; i++) { hr.createCell(i).setCellValue(heads[i]); }
            int r = 1;
            for (Map<String, Object> row : rows) {
                org.apache.poi.ss.usermodel.Row xr = sh.createRow(r++);
                xr.createCell(0).setCellValue(r - 1);
                xr.createCell(1).setCellValue(str(row.get("name")));
                xr.createCell(2).setCellValue(str(row.get("rank")));
                xr.createCell(3).setCellValue(str(row.get("category")));
                xr.createCell(4).setCellValue(str(row.get("idCard")));
                xr.createCell(5).setCellValue(str(row.get("deptName")));
                xr.createCell(6).setCellValue(str(row.get("dayStatus")));
                xr.createCell(7).setCellValue(str(row.get("detail")));
                xr.createCell(8).setCellValue(str(row.get("currentStatus")));
                xr.createCell(9).setCellValue(str(row.get("phone")));
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            wb.write(bos);
            String d = (date == null || date.isEmpty()) ? java.time.LocalDate.now().toString() : date;
            String label = d + ((endDate != null && !endDate.isEmpty()) ? "至" + endDate : "");
            String fname = java.net.URLEncoder.encode("在位情况-" + label + ".xlsx", "UTF-8").replace("+", "%20");
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename*=UTF-8''" + fname)
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(bos.toByteArray());
        }
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
