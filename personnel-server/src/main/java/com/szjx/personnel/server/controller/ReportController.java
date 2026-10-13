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
    private final ObjectMapper mapper = new ObjectMapper();

    public ReportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ================= 下级导出 ================= */

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String scope) throws Exception {
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

        List<Map<String, Object>> people = s.contains("personnel")
                ? jdbc.queryForList("SELECT * FROM personnel ORDER BY id")
                : new ArrayList<Map<String, Object>>();
        pkg.put("personnel", people);
        Map<Long, String> cardOf = new HashMap<Long, String>();
        for (Map<String, Object> p : jdbc.queryForList("SELECT id, id_card FROM personnel")) {
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
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id");
        for (Map<String, Object> r : rows) {
            Object pid = r.get("personnel_id");
            r.put("_card", pid == null ? "" : cardOf.get(((Number) pid).longValue()));
        }
        return rows;
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
                                     @RequestParam(required = false) Long parentId) throws Exception {
        Map<String, Object> pkg = mapper.readValue(file.getInputStream(), Map.class);
        Map<String, Object> meta = (Map<String, Object>) pkg.get("meta");
        String unit = meta == null ? "未命名单位" : String.valueOf(meta.get("unit") == null ? "未命名单位" : meta.get("unit"));
        List<Map<String, Object>> people = (List<Map<String, Object>>) pkg.get("personnel");
        if (people == null) { people = new ArrayList<Map<String, Object>>(); }

        // 目标部门
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

        // 人员合并:按身份证 → 已存在则更新基本信息(不改单位),新人员插入目标部门
        int added = 0, updated = 0, skipped = 0;
        Map<String, Long> localPidByCard = new HashMap<String, Long>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT id, id_card FROM personnel WHERE id_card IS NOT NULL AND id_card != ''")) {
            localPidByCard.put(String.valueOf(row.get("id_card")).trim().toUpperCase(),
                    ((Number) row.get("id")).longValue());
        }
        List<String> peopleCols = people.isEmpty() ? new ArrayList<String>() : colsOf(people.get(0));
        for (Map<String, Object> p : people) {
            String card = String.valueOf(p.get("id_card") == null ? "" : p.get("id_card")).trim().toUpperCase();
            if (card.isEmpty()) {
                skipped++;
                continue;
            }
            Long exist = localPidByCard.get(card);
            if (exist != null) {
                // 更新(不改动本地单位归属与主键)
                StringBuilder sql = new StringBuilder("UPDATE personnel SET ");
                List<Object> args = new ArrayList<Object>();
                for (String c : peopleCols) {
                    if (c.equals("id") || c.equals("id_card") || c.equals("department_id")
                            || c.equals("created_at") || c.startsWith("_")) {
                        continue;
                    }
                    if (sql.indexOf("SET ") != sql.length() - 4 && sql.charAt(sql.length() - 1) != ' ') {
                        sql.append(", ");
                    }
                    sql.append(c).append("=?");
                    args.add(p.get(c));
                }
                sql.append(", updated_at=datetime('now','localtime') WHERE id=?");
                args.add(exist);
                jdbc.update(sql.toString(), args.toArray());
                updated++;
            } else {
                List<String> insCols = new ArrayList<String>();
                List<Object> insArgs = new ArrayList<Object>();
                for (String c : peopleCols) {
                    if (c.equals("id") || c.equals("created_at") || c.startsWith("_")) {
                        continue;
                    }
                    insCols.add(c);
                    insArgs.add(c.equals("department_id") ? targetDept : p.get(c));
                }
                jdbc.update("INSERT INTO personnel(" + join(insCols) + ") VALUES(" + marks(insCols.size()) + ")",
                        insArgs.toArray());
                Long newId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
                localPidByCard.put(card, newId);
                added++;
            }
        }

        // 子表替换(该人的本地子记录清空后按包重建)
        Map<String, Object> subs = (Map<String, Object>) pkg.get("subs");
        int subRows = 0;
        if (subs != null) {
            for (Map.Entry<String, Object> e : subs.entrySet()) {
                String table = e.getKey();
                List<Map<String, Object>> rows = (List<Map<String, Object>>) e.getValue();
                for (Map<String, Object> r : rows) {
                    Long pid = pidOfCard(r.get("_card"), localPidByCard);
                    if (pid == null) {
                        continue;
                    }
                    jdbc.update("DELETE FROM " + table + " WHERE personnel_id = ?", pid);
                }
                for (Map<String, Object> r : rows) {
                    Long pid = pidOfCard(r.get("_card"), localPidByCard);
                    if (pid == null) {
                        continue;
                    }
                    List<String> insCols = new ArrayList<String>();
                    List<Object> insArgs = new ArrayList<Object>();
                    for (Map.Entry<String, Object> c : r.entrySet()) {
                        String k = c.getKey();
                        if (k.equals("id") || k.equals("personnel_id") || k.startsWith("_") || k.equals("created_at")) {
                            continue;
                        }
                        insCols.add(k);
                        insArgs.add(c.getValue());
                    }
                    jdbc.update("INSERT INTO " + table + "(personnel_id," + join(insCols) + ") VALUES(?," + marks(insCols.size()) + ")",
                            append(pid, insArgs));
                    subRows++;
                }
            }
        }

        // 休假/体能:去重后新增(同人+同类型+同起止 / 同人+同项目+同日期)
        int leaveAdded = 0, fitnessAdded = 0;
        leaveAdded = mergeRows("leave_record", (List<Map<String, Object>>) pkg.get("leave"), localPidByCard,
                "SELECT COUNT(*) FROM leave_record WHERE personnel_id=? AND leave_type=? AND start_date=? AND end_date=?",
                new String[]{"leave_type", "start_date", "end_date"});
        fitnessAdded = mergeRows("fitness_record", (List<Map<String, Object>>) pkg.get("fitness"), localPidByCard,
                "SELECT COUNT(*) FROM fitness_record WHERE personnel_id=? AND item=? AND test_date=?",
                new String[]{"item", "test_date"});

        // 资产合并
        Map<String, Integer> eqR = mergeAssets("equipment", (List<Map<String, Object>>) pkg.get("equipment"), "装备", "code", "name");
        Map<String, Integer> mtR = mergeAssets("material", (List<Map<String, Object>>) pkg.get("material"), "物资", "name", null);
        Map<String, Integer> veR = mergeAssets("vehicle", (List<Map<String, Object>>) pkg.get("vehicle"), "车辆", "plate_no", null);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("deptName", deptName);
        result.put("created", createNew);
        result.put("added", added);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("subRows", subRows);
        result.put("leaveAdded", leaveAdded);
        result.put("fitnessAdded", fitnessAdded);
        result.put("equipmentAdded", eqR.get("added"));
        result.put("equipmentUpdated", eqR.get("updated"));
        result.put("materialAdded", mtR.get("added"));
        result.put("materialUpdated", mtR.get("updated"));
        result.put("vehicleAdded", veR.get("added"));
        result.put("vehicleUpdated", veR.get("updated"));
        return ApiResponse.ok(result);
    }

    /**
     * 资产表合并:主键在本地存在则整行更新,否则新增。
     * key1 优先业务键(装备编号/车牌号),为空时退回 key2(名称)。
     */
    private Map<String, Integer> mergeAssets(String table, List<Map<String, Object>> rows,
                                             String label, String key1, String key2) {
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        out.put("added", 0);
        out.put("updated", 0);
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        for (Map<String, Object> r : rows) {
            Object v1 = r.get(key1);
            Object v2 = key2 == null ? null : r.get(key2);
            String k1s = v1 == null ? "" : String.valueOf(v1).trim();
            String k2s = v2 == null ? "" : String.valueOf(v2).trim();
            Long existId = null;
            if (!k1s.isEmpty()) {
                List<Map<String, Object>> hit = jdbc.queryForList("SELECT id FROM " + table + " WHERE " + key1 + " = ?", k1s);
                if (!hit.isEmpty()) { existId = ((Number) hit.get(0).get("id")).longValue(); }
            }
            if (existId == null && !k2s.isEmpty()) {
                List<Map<String, Object>> hit = jdbc.queryForList("SELECT id FROM " + table + " WHERE " + key2 + " = ?", k2s);
                if (!hit.isEmpty()) { existId = ((Number) hit.get(0).get("id")).longValue(); }
            }
            List<String> cols = new ArrayList<String>();
            List<Object> args = new ArrayList<Object>();
            for (Map.Entry<String, Object> e : r.entrySet()) {
                String k = e.getKey();
                if (k.equals("id") || k.equals("created_at") || k.startsWith("_")) { continue; }
                cols.add(k);
                args.add(e.getValue());
            }
            if (existId != null) {
                StringBuilder sql = new StringBuilder("UPDATE " + table + " SET ");
                for (int i = 0; i < cols.size(); i++) {
                    if (i > 0) { sql.append(","); }
                    sql.append(cols.get(i)).append("=?");
                }
                sql.append(", updated_at=datetime('now','localtime') WHERE id=?");
                args.add(existId);
                jdbc.update(sql.toString(), args.toArray());
                out.put("updated", out.get("updated") + 1);
            } else {
                jdbc.update("INSERT INTO " + table + "(" + join(cols) + ") VALUES(" + marks(cols.size()) + ")", args.toArray());
                out.put("added", out.get("added") + 1);
            }
        }
        return out;
    }

    private int mergeRows(String table, List<Map<String, Object>> rows, Map<String, Long> pidByCard,
                          String dupSql, String[] dupCols) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Map<String, Object> r : rows) {
            Long pid = pidOfCard(r.get("_card"), pidByCard);
            if (pid == null) {
                continue;
            }
            Object[] dargs = new Object[1 + dupCols.length];
            dargs[0] = pid;
            for (int i = 0; i < dupCols.length; i++) {
                dargs[i + 1] = r.get(dupCols[i]);
            }
            Long c = jdbc.queryForObject(dupSql, Long.class, dargs);
            if (c != null && c > 0) {
                continue;
            }
            List<String> insCols = new ArrayList<String>();
            List<Object> insArgs = new ArrayList<Object>();
            for (Map.Entry<String, Object> e : r.entrySet()) {
                String k = e.getKey();
                if (k.equals("id") || k.equals("personnel_id") || k.startsWith("_") || k.equals("created_at")) {
                    continue;
                }
                insCols.add(k);
                insArgs.add(e.getValue());
            }
            jdbc.update("INSERT INTO " + table + "(personnel_id," + join(insCols) + ") VALUES(?," + marks(insCols.size()) + ")",
                    append(pid, insArgs));
            n++;
        }
        return n;
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
