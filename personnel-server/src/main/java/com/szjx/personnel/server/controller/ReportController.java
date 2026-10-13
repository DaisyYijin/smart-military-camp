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
 *   上级: POST /api/report/check         预检(解析包,检索本单位同名部门)
 *         POST /api/report/import       导入(选定部门或新建,按身份证合并,子表替换,休假/体能去重)
 */
@RestController
@RequestMapping("/api/report")
public class ReportController {

    private static final String[] SUB_TABLES = {
            "position_history", "reward_punish", "child_info", "family_member",
            "driver_license", "personal_card", "private_vehicle", "housing_info"};

    private final JdbcTemplate jdbc;
    private final org.springframework.transaction.support.TransactionTemplate txTpl;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReportController(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.txTpl = new org.springframework.transaction.support.TransactionTemplate(txManager);
    }

    /* ================= 导入进度(前端轮询) ================= */
    private static final java.util.concurrent.ConcurrentHashMap<String, Map<String, Object>> PROGRESS =
            new java.util.concurrent.ConcurrentHashMap<String, Map<String, Object>>();

    @GetMapping("/progress")
    public ApiResponse progress(@RequestParam String id) {
        Map<String, Object> m = PROGRESS.get(id);
        return ApiResponse.ok(m == null ? new LinkedHashMap<String, Object>() : m);
    }

    private void prog(String pid, int pct, String stage, String detail) {
        if (pid == null) { return; }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("pct", pct);
        m.put("stage", stage);
        m.put("detail", detail == null ? "" : detail);
        PROGRESS.put(pid, m);
    }

    /* ================= 下级导出 ================= */

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String scope,
                                         @RequestParam(required = false) String depts) throws Exception {
        // 模块选择:personnel(含子表)/leave/fitness,逗号分隔;默认全部
        java.util.Set<String> s = new java.util.HashSet<String>();
        if (scope == null || scope.trim().isEmpty()) {
            s.add("personnel"); s.add("leave"); s.add("fitness");
            s.add("equipment"); s.add("material"); s.add("vehicle");
        } else {
            for (String x : scope.split(",")) {
                if (!x.trim().isEmpty()) { s.add(x.trim()); }
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
        meta.put("version", 1);
        pkg.put("meta", meta);

        // 部门过滤:depts=部门ID逗号串(空=全部);选父单位自动含其全部下级
        StringBuilder deptWhere = new StringBuilder();
        List<Object> deptArgs = new ArrayList<Object>();
        if (depts != null && !depts.trim().isEmpty() && s.contains("personnel")) {
            java.util.Set<Long> picked = new java.util.HashSet<Long>();
            for (String d : depts.split(",")) {
                try {
                    picked.add(Long.parseLong(d.trim()));
                } catch (NumberFormatException ignore) {
                    // 跳过非法id
                }
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
        List<Map<String, Object>> people = s.contains("personnel")
                ? jdbc.queryForList("SELECT * FROM personnel WHERE 1=1" + deptWhere + " ORDER BY id", deptArgs.toArray())
                : new ArrayList<Map<String, Object>>();
        pkg.put("personnel", people);
        Map<Long, String> cardOf = new HashMap<Long, String>();
        for (Map<String, Object> p : people) {
            cardOf.put(((Number) p.get("id")).longValue(), String.valueOf(p.get("id_card") == null ? "" : p.get("id_card")));
        }
        // 子表/休假/体能: 行内附 _card 便于上级按证件归属(未选模块给空表,保持包结构完整)
        Map<String, Object> subs = new LinkedHashMap<String, Object>();
        for (String t : SUB_TABLES) {
            subs.put(t, s.contains("personnel") ? rowsWithCard(t, cardOf) : new ArrayList<Map<String, Object>>());
        }
        pkg.put("subs", subs);
        pkg.put("leave", s.contains("leave") ? rowsWithCard("leave_record", cardOf) : new ArrayList<Map<String, Object>>());
        pkg.put("fitness", s.contains("fitness") ? rowsWithCard("fitness_record", cardOf) : new ArrayList<Map<String, Object>>());
        // 单位资产(装备/物资/车辆,不挂人,按业务键合并)
        pkg.put("equipment", s.contains("equipment") ? jdbc.queryForList("SELECT * FROM equipment ORDER BY id") : new ArrayList<Map<String, Object>>());
        pkg.put("material", s.contains("material") ? jdbc.queryForList("SELECT * FROM material ORDER BY id") : new ArrayList<Map<String, Object>>());
        pkg.put("vehicle", s.contains("vehicle") ? jdbc.queryForList("SELECT * FROM vehicle ORDER BY id") : new ArrayList<Map<String, Object>>());

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
        for (Map<String, Object> r : jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id")) {
            Object pid = r.get("personnel_id");
            String card = "";
            if (pid instanceof Number) {
                String c = cardOf.get(((Number) pid).longValue());
                card = c == null ? "" : c;
            }
            if (card.isEmpty() && !cardOf.isEmpty()) {
                continue;   // 不在所选人员范围(或人员缺失)的行不进包
            }
            r.put("_card", card);
            out.add(r);
        }
        return out;
    }

    /* ================= 上级预检 ================= */

    @PostMapping("/check")
    public ApiResponse check(@RequestParam("file") MultipartFile file) throws Exception {
        Map<String, Object> pkg = mapper.readValue(file.getInputStream(), Map.class);
        Map<String, Object> meta = (Map<String, Object>) pkg.get("meta");
        String unit = meta == null ? "" : String.valueOf(meta.get("unit") == null ? "" : meta.get("unit"));
        List<Map<String, Object>> people = (List<Map<String, Object>>) pkg.get("personnel");
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("unit", unit);
        out.put("exportedAt", meta == null ? "" : String.valueOf(meta.get("exportedAt")));
        out.put("personnelCount", people == null ? 0 : people.size());
        Map<String, Object> subs = (Map<String, Object>) pkg.get("subs");
        int subCount = 0;
        if (subs != null) {
            for (Object v : subs.values()) {
                subCount += ((List<?>) v).size();
            }
        }
        out.put("subCount", subCount);
        out.put("leaveCount", ((List<?>) pkg.get("leave")).size());
        out.put("fitnessCount", ((List<?>) pkg.get("fitness")).size());
        out.put("equipmentCount", ((List<?>) pkg.get("equipment")).size());
        out.put("materialCount", ((List<?>) pkg.get("material")).size());
        out.put("vehicleCount", ((List<?>) pkg.get("vehicle")).size());
        // 检索本单位同名部门(精确 → 模糊)
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
     * @param deptId    选定的现有部门ID(与 createNew 二选一)
     * @param createNew true=新建同名部门挂到 parentId
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
        } finally {
            if (progressId != null) {
                PROGRESS.remove(progressId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ApiResponse doImport(MultipartFile file, Long deptId, boolean createNew, Long parentId, String pid) throws Exception {
        prog(pid, 3, "解析数据包", "读取文件…");
        Map<String, Object> pkg = mapper.readValue(file.getInputStream(), Map.class);
        Map<String, Object> meta = (Map<String, Object>) pkg.get("meta");
        String unit = meta == null ? "未命名单位" : String.valueOf(meta.get("unit") == null ? "未命名单位" : meta.get("unit"));
        List<Map<String, Object>> people = (List<Map<String, Object>>) pkg.get("personnel");
        if (people == null) { people = new ArrayList<Map<String, Object>>(); }

        prog(pid, 8, "准备接收部门", null);
        long targetDept;
        String deptName;
        if (createNew) {
            long parent = parentId == null ? 0 : parentId;
            deptName = unit;
            targetDept = jdbc.queryForObject(
                    "INSERT INTO department(name, parent_id, sort, remark) VALUES(?,?,50,'下级上报自动创建') RETURNING id",
                    Long.class, unit, parent);
        } else {
            if (deptId == null) {
                throw new IllegalArgumentException("请选择接收部门");
            }
            targetDept = deptId;
            deptName = String.valueOf(jdbc.queryForObject("SELECT name FROM department WHERE id=?", String.class, deptId));
        }

        // ---- 人员合并(按身份证;批量更新,新插入需逐条取id) ----
        prog(pid, 12, "合并人员", "0/" + people.size());
        int added = 0, updated = 0, skipped = 0;
        Map<String, Long> localPidByCard = new HashMap<String, Long>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT id, id_card FROM personnel WHERE id_card IS NOT NULL AND id_card != ''")) {
            localPidByCard.put(String.valueOf(row.get("id_card")).trim().toUpperCase(),
                    ((Number) row.get("id")).longValue());
        }
        List<String> peopleCols = people.isEmpty() ? new ArrayList<String>() : colsOf(people.get(0));
        List<String> updCols = new ArrayList<String>();
        for (String c : peopleCols) {
            if (c.equals("id") || c.equals("id_card") || c.equals("department_id")
                    || c.equals("created_at") || c.startsWith("_")) { continue; }
            updCols.add(c);
        }
        List<Object[]> updateBatch = new ArrayList<Object[]>();
        List<Map<String, Object>> toInsert = new ArrayList<Map<String, Object>>();
        int pi = 0;
        for (Map<String, Object> p : people) {
            String card = String.valueOf(p.get("id_card") == null ? "" : p.get("id_card")).trim().toUpperCase();
            pi++;
            if (pi % 25 == 0) { prog(pid, 12 + (int) (30.0 * pi / Math.max(people.size(), 1)), "合并人员", pi + "/" + people.size()); }
            if (card.isEmpty()) { skipped++; continue; }
            Long exist = localPidByCard.get(card);
            if (exist != null) {
                Object[] row = new Object[updCols.size() + 1];
                for (int i = 0; i < updCols.size(); i++) { row[i] = p.get(updCols.get(i)); }
                row[updCols.size()] = exist;
                updateBatch.add(row);
                updated++;
            } else {
                toInsert.add(p);
            }
        }
        if (!updateBatch.isEmpty() && !updCols.isEmpty()) {
            StringBuilder sql = new StringBuilder("UPDATE personnel SET ");
            for (int i = 0; i < updCols.size(); i++) {
                if (i > 0) { sql.append(","); }
                sql.append(updCols.get(i)).append("=?");
            }
            sql.append(", updated_at=datetime('now','localtime') WHERE id=?");
            jdbc.batchUpdate(sql.toString(), updateBatch);
        }
        for (Map<String, Object> p : toInsert) {
            List<String> insCols = new ArrayList<String>();
            List<Object> insArgs = new ArrayList<Object>();
            for (String c : peopleCols) {
                if (c.equals("id") || c.equals("created_at") || c.startsWith("_")) { continue; }
                insCols.add(c);
                insArgs.add(c.equals("department_id") ? targetDept : p.get(c));
            }
            jdbc.update("INSERT INTO personnel(" + join(insCols) + ") VALUES(" + marks(insCols.size()) + ")", insArgs.toArray());
            Long newId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
            localPidByCard.put(String.valueOf(p.get("id_card")).trim().toUpperCase(), newId);
            added++;
        }

        // ---- 子表替换(先批量删后批量插) ----
        Map<String, Object> subs = (Map<String, Object>) pkg.get("subs");
        int subRows = 0;
        if (subs != null) {
            for (Map.Entry<String, Object> e : subs.entrySet()) {
                String table = e.getKey();
                List<Map<String, Object>> rows = (List<Map<String, Object>>) e.getValue();
                if (rows.isEmpty()) { continue; }
                prog(pid, 45, "合并子表", tableLabel(table) + " " + rows.size() + " 条");
                List<Object[]> dels = new ArrayList<Object[]>();
                for (Map<String, Object> r : rows) {
                    Long id2 = pidOfCard(r.get("_card"), localPidByCard);
                    if (id2 != null) { dels.add(new Object[]{id2}); }
                }
                if (!dels.isEmpty()) { jdbc.batchUpdate("DELETE FROM " + table + " WHERE personnel_id=?", dels); }
                List<Object[]> ins = new ArrayList<Object[]>();
                List<String> insCols = new ArrayList<String>();
                for (Map<String, Object> r : rows) {
                    Long personId = pidOfCard(r.get("_card"), localPidByCard);
                    if (personId == null) { continue; }
                    if (insCols.isEmpty()) {
                        for (Map.Entry<String, Object> c : r.entrySet()) {
                            String k = c.getKey();
                            if (k.equals("id") || k.equals("personnel_id") || k.startsWith("_") || k.equals("created_at")) { continue; }
                            insCols.add(k);
                        }
                    }
                    Object[] row = new Object[insCols.size() + 1];
                    row[0] = personId;
                    int i = 1;
                    for (String c : insCols) { row[i++] = r.get(c); }
                    ins.add(row);
                    subRows++;
                }
                if (!ins.isEmpty()) {
                    jdbc.batchUpdate("INSERT INTO " + table + "(personnel_id," + join(insCols) + ") VALUES(?," + marks(insCols.size()) + ")", ins);
                }
            }
        }

        // ---- 休假/体能(预载去重键 + 批量插入) ----
        prog(pid, 65, "合并休假记录", null);
        java.util.Set<String> leaveKeys = new java.util.HashSet<String>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT personnel_id, leave_type, start_date, end_date FROM leave_record")) {
            leaveKeys.add(key(r.get("personnel_id"), r.get("leave_type"), r.get("start_date"), r.get("end_date")));
        }
        int leaveAdded = batchMerge(pid, 65, 72, "合并休假记录", "leave_record",
                (List<Map<String, Object>>) pkg.get("leave"), localPidByCard, leaveKeys,
                new String[]{"leave_type", "start_date", "end_date"});
        prog(pid, 72, "合并体能成绩", null);
        java.util.Set<String> fitKeys = new java.util.HashSet<String>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT personnel_id, item, test_date FROM fitness_record")) {
            fitKeys.add(key(r.get("personnel_id"), r.get("item"), r.get("test_date")));
        }
        int fitnessAdded = batchMerge(pid, 72, 80, "合并体能成绩", "fitness_record",
                (List<Map<String, Object>>) pkg.get("fitness"), localPidByCard, fitKeys,
                new String[]{"item", "test_date"});

        // ---- 资产 ----
        Map<String, Integer> eqR = mergeAssets(pid, 80, 86, "equipment", (List<Map<String, Object>>) pkg.get("equipment"), "code", "name");
        Map<String, Integer> mtR = mergeAssets(pid, 86, 92, "material", (List<Map<String, Object>>) pkg.get("material"), "name", null);
        Map<String, Integer> veR = mergeAssets(pid, 92, 98, "vehicle", (List<Map<String, Object>>) pkg.get("vehicle"), "plate_no", null);

        prog(pid, 100, "导入完成", null);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("deptName", deptName);
        result.put("created", createNew);
        result.put("added", added);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("subRows", subRows);
        result.put("leaveAdded", leaveAdded);
        result.put("leaveTotal", ((List<?>) pkg.get("leave")).size());
        result.put("fitnessAdded", fitnessAdded);
        result.put("fitnessTotal", ((List<?>) pkg.get("fitness")).size());
        result.put("equipmentAdded", eqR.get("added"));
        result.put("equipmentUpdated", eqR.get("updated"));
        result.put("equipmentTotal", ((List<?>) pkg.get("equipment")).size());
        result.put("materialAdded", mtR.get("added"));
        result.put("materialUpdated", mtR.get("updated"));
        result.put("materialTotal", ((List<?>) pkg.get("material")).size());
        result.put("vehicleAdded", veR.get("added"));
        result.put("vehicleUpdated", veR.get("updated"));
        result.put("vehicleTotal", ((List<?>) pkg.get("vehicle")).size());
        return ApiResponse.ok(result);
    }

    /** 批量合并带去重键的表(休假/体能) */
    @SuppressWarnings("unchecked")
    private int batchMerge(String pid, int pctFrom, int pctTo, String stage, String table,
                           List<Map<String, Object>> rows, Map<String, Long> pidByCard,
                           java.util.Set<String> existingKeys, String[] keyCols) {
        if (rows == null || rows.isEmpty()) { return 0; }
        List<Object[]> ins = new ArrayList<Object[]>();
        List<String> insCols = new ArrayList<String>();
        int n = 0;
        int i = 0;
        for (Map<String, Object> r : rows) {
            i++;
            if (i % 50 == 0) { prog(pid, pctFrom + (int) ((pctTo - pctFrom) * i / rows.size()), stage, i + "/" + rows.size()); }
            Long personId = pidOfCard(r.get("_card"), pidByCard);
            if (personId == null) { continue; }
            Object[] kv = new Object[keyCols.length];
            for (int k = 0; k < keyCols.length; k++) { kv[k] = r.get(keyCols[k]); }
            Object[] keyParts = new Object[keyCols.length + 1];
            keyParts[0] = personId;
            System.arraycopy(kv, 0, keyParts, 1, keyCols.length);
            if (existingKeys.contains(key(keyParts))) { continue; }
            if (insCols.isEmpty()) {
                for (Map.Entry<String, Object> c : r.entrySet()) {
                    String k = c.getKey();
                    if (k.equals("id") || k.equals("personnel_id") || k.startsWith("_") || k.equals("created_at")) { continue; }
                    insCols.add(k);
                }
            }
            Object[] row = new Object[insCols.size() + 1];
            row[0] = personId;
            int x = 1;
            for (String c : insCols) { row[x++] = r.get(c); }
            ins.add(row);
            n++;
        }
        if (!ins.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO " + table + "(personnel_id," + join(insCols) + ") VALUES(?," + marks(insCols.size()) + ")", ins);
        }
        return n;
    }

    private static String key(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            if (sb.length() > 0) { sb.append('|'); }
            sb.append(p == null ? "" : String.valueOf(p));
        }
        return sb.toString();
    }

    private static String tableLabel(String table) {
        String[][] zh = {{"position_history", "任职经历"}, {"reward_punish", "奖惩情况"}, {"child_info", "子女信息"},
                {"family_member", "家庭成员"}, {"driver_license", "驾驶证"}, {"personal_card", "个人卡号"},
                {"private_vehicle", "私家车"}, {"housing_info", "住房情况"}, {"equipment", "装备"}, {"material", "物资"}, {"vehicle", "车辆"}};
        for (String[] z : zh) {
            if (z[0].equals(table)) { return z[1]; }
        }
        return table;
    }

    /** 资产表合并:预载业务键表,批量更新/插入 */
    @SuppressWarnings("unchecked")
    private Map<String, Integer> mergeAssets(String pid, int pctFrom, int pctTo, String table,
                                             List<Map<String, Object>> rows, String key1, String key2) {
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        out.put("added", 0);
        out.put("updated", 0);
        if (rows == null || rows.isEmpty()) { return out; }
        prog(pid, pctFrom, "合并" + tableLabel(table), rows.size() + " 项");
        Map<String, Long> byKey = new HashMap<String, Long>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT id, " + key1 + (key2 == null ? "" : "," + key2) + " FROM " + table)) {
            Long id = ((Number) r.get("id")).longValue();
            String k1 = String.valueOf(r.get(key1) == null ? "" : r.get(key1)).trim();
            if (!k1.isEmpty()) { byKey.put(key1 + "=" + k1, id); }
            if (key2 != null) {
                String k2 = String.valueOf(r.get(key2) == null ? "" : r.get(key2)).trim();
                if (!k2.isEmpty()) { byKey.put(key2 + "=" + k2, id); }
            }
        }
        List<Object[]> upd = new ArrayList<Object[]>();
        List<Object[]> ins = new ArrayList<Object[]>();
        List<String> cols0 = new ArrayList<String>();
        int i = 0;
        for (Map<String, Object> r : rows) {
            i++;
            if (i % 20 == 0) { prog(pid, pctFrom + (int) ((pctTo - pctFrom) * i / rows.size()), "合并" + tableLabel(table), i + "/" + rows.size()); }
            String k1 = String.valueOf(r.get(key1) == null ? "" : r.get(key1)).trim();
            String k2 = key2 == null ? "" : String.valueOf(r.get(key2) == null ? "" : r.get(key2)).trim();
            Long existId = null;
            if (!k1.isEmpty()) { existId = byKey.get(key1 + "=" + k1); }
            if (existId == null && !k2.isEmpty()) { existId = byKey.get(key2 + "=" + k2); }
            if (cols0.isEmpty()) {
                for (String k : r.keySet()) {
                    if (k.equals("id") || k.equals("created_at") || k.startsWith("_")) { continue; }
                    cols0.add(k);
                }
            }
            Object[] args = new Object[cols0.size()];
            for (int c = 0; c < cols0.size(); c++) { args[c] = r.get(cols0.get(c)); }
            if (existId != null) {
                Object[] row = new Object[args.length + 1];
                System.arraycopy(args, 0, row, 0, args.length);
                row[args.length] = existId;
                upd.add(row);
                out.put("updated", out.get("updated") + 1);
            } else {
                ins.add(args);
                out.put("added", out.get("added") + 1);
            }
        }
        if (!ins.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO " + table + "(" + join(cols0) + ") VALUES(" + marks(cols0.size()) + ")", ins);
        }
        if (!upd.isEmpty()) {
            StringBuilder sql = new StringBuilder("UPDATE " + table + " SET ");
            for (int c = 0; c < cols0.size(); c++) {
                if (c > 0) { sql.append(","); }
                sql.append(cols0.get(c)).append("=?");
            }
            sql.append(", updated_at=datetime('now','localtime') WHERE id=?");
            jdbc.batchUpdate(sql.toString(), upd);
        }
        return out;
    }

    private static Long pidOfCard(Object card, Map<String, Long> pidByCard) {
        if (card == null) {
            return null;
        }
        return pidByCard.get(String.valueOf(card).trim().toUpperCase());
    }

    private static List<String> colsOf(Map<String, Object> row) {
        List<String> out = new ArrayList<String>();
        for (String k : row.keySet()) {
            if (!k.startsWith("_")) {
                out.add(k);
            }
        }
        return out;
    }

    private static String join(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (String c : cols) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String marks(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("?");
        }
        return sb.toString();
    }

    private static Object[] append(Long head, List<Object> tail) {
        Object[] out = new Object[tail.size() + 1];
        out[0] = head;
        for (int i = 0; i < tail.size(); i++) {
            out[i + 1] = tail.get(i);
        }
        return out;
    }
}
