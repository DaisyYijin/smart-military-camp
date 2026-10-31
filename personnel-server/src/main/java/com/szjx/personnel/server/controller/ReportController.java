package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据上报(下级 → 上级):
 *   下级: GET  /api/report/export        导出本单位数据包(JSON: 单位名+人员+子表+休假+体能)
 *   上级: POST /api/report/check         预检(解析包,检索本单位同名单位)
 *         POST /api/report/import       导入(选定单位或新建,按身份证合并,子表替换,休假/体能去重)
 */
@RestController
@RequestMapping("/api/report")
public class ReportController extends com.szjx.personnel.server.service.ReportService {

    /** 合法表名白名单(防SQL注入) */

    private final org.springframework.transaction.support.TransactionTemplate txTpl;

    public ReportController(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager txManager) {
        super(jdbc);
        this.txTpl = new org.springframework.transaction.support.TransactionTemplate(txManager);
    }

    /* ================= 导入进度(前端轮询) ================= */
    @GetMapping("/progress")
    public ApiResponse progress(@RequestParam String id) { return ApiResponse.ok(getProgress(id)); }

    /* ================= 下级导出 ================= */

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String scope,
                                         @RequestParam(required = false) String depts) throws Exception {
        // 模块选择:personnel(含子表)/leave/fitness,逗号分隔;默认全部
        java.util.Set<String> s = new java.util.HashSet<String>();
        if (scope == null || scope.trim().isEmpty()) {
            s.add("personnel"); s.add("leave"); s.add("fitness");
            s.add("equipment"); s.add("material"); s.add("vehicle"); s.add("phone"); s.add("military");
        } else {
            for (String x : scope.split(",")) {
                if (!x.trim().isEmpty()) { s.add(x.trim()); }
            }
        }
        for (String module : s) {
            if (!java.util.Arrays.asList("personnel", "leave", "fitness", "phone", "military", "equipment", "material", "vehicle").contains(module)) {
                throw new IllegalArgumentException("未知上报模块: " + module);
            }
        }
        Map<String, Object> pkg = new LinkedHashMap<String, Object>();
        pkg.put("scope", new ArrayList<String>(s));
        // 单位名: 系统设置的网站名称(下级单位把系统名称设为本单位名,如"XX班")
        String unit = "";
        try {
            String v = jdbc.queryForObject("SELECT svalue FROM sys_setting WHERE skey='siteName'", String.class);
            if (v != null && v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length() - 1);
            }
            if (v != null && !v.trim().isEmpty()) {
                unit = v.trim();
            }
        } catch (Exception ignore) {
            // 无设置
        }
        if (unit.isEmpty()) {
            unit = "未命名单位";
        }
        Map<String, Object> meta = new LinkedHashMap<String, Object>();
        meta.put("unit", unit);
        meta.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        meta.put("version", 2);
        pkg.put("meta", meta);

        // 单位过滤:depts=单位ID逗号串(空=全部);选父单位自动含其全部下级
        StringBuilder deptWhere = new StringBuilder();
        List<Object> deptArgs = new ArrayList<Object>();
        if (depts != null && !depts.trim().isEmpty() ) {
            java.util.Set<Long> picked = new java.util.HashSet<Long>();
            for (String d : depts.split(",")) {
                try {
                    picked.add(Long.parseLong(d.trim()));
                } catch (NumberFormatException invalid) { throw new IllegalArgumentException("非法单位ID: " + d); }
            }
            if (!picked.isEmpty()) {
                // 自底向上:任何祖先被选中即视为选中(等效于父级向下扩展)
                Map<Long, Long> parentOf = new HashMap<Long, Long>();
                for (Map<String, Object> d : jdbc.queryForList("SELECT id, parent_id FROM department")) {
                    parentOf.put(((Number) d.get("id")).longValue(),
                            d.get("parent_id") == null ? 0L : ((Number) d.get("parent_id")).longValue());
                }
                java.util.Set<Long> eff = new java.util.HashSet<Long>(picked);
                for (Long id : parentOf.keySet()) {
                    Long cur = id;
                    int guard = 0;
                    while (cur != null && cur != 0L && guard++ < 20) {
                        if (picked.contains(cur)) { eff.add(id); break; }
                        cur = parentOf.get(cur);
                    }
                }
                StringBuilder in = new StringBuilder();
                for (Long v : eff) {
                    if (in.length() > 0) { in.append(","); }
                    in.append("?");
                    deptArgs.add(v);
                }
                if (in.length() > 0) { deptWhere.append(" AND department_id IN (").append(in).append(")"); }
            }
        }
        List<Map<String, Object>> identities = jdbc.queryForList(
                "SELECT id, name, id_card, department_id, updated_at FROM personnel WHERE 1=1" + deptWhere + " ORDER BY id", deptArgs.toArray());
        List<Map<String, Object>> people = s.contains("personnel")
                ? jdbc.queryForList("SELECT * FROM personnel WHERE 1=1" + deptWhere + " ORDER BY id", deptArgs.toArray())
                : new ArrayList<Map<String, Object>>();
        pkg.put("personnel", people);
        pkg.put("identities", identities);
        Map<Long, String> cardOf = new HashMap<Long, String>();
        for (Map<String, Object> p : identities) { cardOf.put(((Number) p.get("id")).longValue(), text(p.get("id_card"))); }
        Map<String, Object> subs = new LinkedHashMap<String, Object>();
        if (s.contains("personnel")) { for (String t : SUB_TABLES) { subs.put(t, rowsWithCard(t, cardOf)); } }
        pkg.put("subs", subs);
        pkg.put("leave", s.contains("leave") ? rowsWithCard("leave_record", cardOf) : new ArrayList<Map<String, Object>>());
        pkg.put("leavePlan", s.contains("leave") ? rowsWithCard("leave_plan", cardOf) : new ArrayList<Map<String, Object>>());
        pkg.put("fitness", s.contains("fitness") ? rowsWithCard("fitness_record", cardOf) : new ArrayList<Map<String, Object>>());
        pkg.put("phone", s.contains("phone") ? rowsWithCard("phone_device", cardOf) : new ArrayList<Map<String, Object>>());
        pkg.put("military", s.contains("military") ? rowsWithCard("military_assessment", cardOf) : new ArrayList<Map<String, Object>>());
        // 资产责任人稳定映射,单位过滤按 keeper/driver 所属人员执行。
        boolean filtered = depts != null && !depts.trim().isEmpty();
        for (String table : java.util.Arrays.asList("equipment", "material", "vehicle")) {
            List<Map<String, Object>> assets = new ArrayList<Map<String, Object>>();
            if (s.contains(table)) {
                String ref = "vehicle".equals(table) ? "driver_id" : "keeper_id";
                for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id")) {
                    Object value = row.get(ref);
                    long personId = value instanceof Number ? ((Number) value).longValue() : 0;
                    if (filtered && !cardOf.containsKey(personId)) { continue; }
                    row.put("vehicle".equals(table) ? "_driver_card" : "_keeper_card", cardOf.getOrDefault(personId, ""));
                    assets.add(row);
                }
            }
            pkg.put(table, assets);
        }

        byte[] body = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(pkg);
        String fileName = "上报数据_" + unit + "_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".json";
        String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private List<Map<String, Object>> rowsWithCard(String table, Map<Long, String> cardOf) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT * FROM " + safeTable(table) + " ORDER BY id")) {
            Object pid = r.get("personnel_id");
            String card = "";
            if (pid instanceof Number) {
                String c = cardOf.get(((Number) pid).longValue());
                card = c == null ? "" : c;
            }
            if (!(pid instanceof Number) || !cardOf.containsKey(((Number) pid).longValue())) {
                continue;   // 不在所选人员范围(或人员缺失)的行不进包
            }
            r.put("_card", card);
            out.add(r);
        }
        return out;
    }

    /* ================= 上级预检 ================= */

    @PostMapping({"/check", "/preview"})
    public ApiResponse check(@RequestParam("file") MultipartFile file) throws Exception {
        Map<String, Object> pkg = mapper.readValue(file.getInputStream(), Map.class);
        pkg = normalizePackage(pkg);
        Map<String, Object> meta = (Map<String, Object>) pkg.get("meta");
        String unit = text(meta.get("unit"));
        List<Map<String, Object>> people = rows(pkg, "personnel");
        Map<String, Object> out = previewPackage(pkg);
        out.put("unit", unit); out.put("exportedAt", meta.get("exportedAt"));
        out.put("personnelCount", people.size());
        int subCount = 0;
        for (Object value : ((Map<?, ?>) pkg.get("subs")).values()) { subCount += ((List<?>) value).size(); }
        out.put("subCount", subCount);
        for (String module : java.util.Arrays.asList("leave", "leavePlan", "fitness", "phone", "military", "equipment", "material", "vehicle")) {
            out.put(module + "Count", rows(pkg, module).size());
        }
        // 检索本单位同名单位(精确 → 模糊)
        List<Map<String, Object>> matched = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> d : jdbc.queryForList("SELECT id, name FROM department WHERE name = ?", unit)) {
            matched.add(d);
        }
        if (matched.isEmpty()) {
            for (Map<String, Object> d : jdbc.queryForList(
                    "SELECT id, name FROM department WHERE name LIKE ? AND name <> ?", "%" + unit + "%", unit)) {
                matched.add(d);
            }
        }
        out.put("matchedDepts", matched);
        // 身份证重复情况(本单位已存在的人数)
        int exist = 0;
        if (people != null) {
            for (Map<String, Object> p : people) {
                String c = String.valueOf(p.get("id_card") == null ? "" : p.get("id_card")).trim();
                if (!c.isEmpty()) {
                    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE id_card = ?", Long.class, c);
                    if (n != null && n > 0) {
                        exist++;
                    }
                }
            }
        }
        out.put("existCount", exist);
        out.put("newCount", (people == null ? 0 : people.size()) - exist);
        return ApiResponse.ok(out);
    }

    /* ================= 上级导入 ================= */

    /**
     * @param deptId    选定的现有单位ID(与 createNew 二选一)
     * @param createNew true=新建同名单位挂到 parentId
     */
    @PostMapping("/import")
    public ApiResponse importPackage(@RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) Long deptId,
                                     @RequestParam(required = false, defaultValue = "false") boolean createNew,
                                     @RequestParam(required = false) Long parentId,
                                     @RequestParam(required = false) String progressId) throws Exception {
        try {
            // 全程单事务:几千条语句一次提交,失败整体回滚(中途出错不会留半截数据)
            return txTpl.execute(new org.springframework.transaction.support.TransactionCallback<ApiResponse>() {
                @Override
                public ApiResponse doInTransaction(org.springframework.transaction.TransactionStatus status) {
                    try {
                        return doImport(file, deptId, createNew, parentId, progressId);
                    } catch (IllegalArgumentException e) {
                        status.setRollbackOnly();
                        throw e;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        throw new IllegalArgumentException("导入失败:" + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            prog(progressId, 100, "导入失败", e.getMessage());
            throw e;
        }
    }

    private ApiResponse doImport(MultipartFile file, Long deptId, boolean createNew, Long parentId, String pid) throws Exception {
        return ApiResponse.ok(receivePackage(normalizePackage(mapper.readValue(file.getInputStream(), Map.class)),
                deptId, createNew, parentId, pid));
    }
}
