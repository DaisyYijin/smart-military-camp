package com.szjx.personnel.server.service;

import com.szjx.personnel.server.util.Db;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szjx.personnel.server.model.FitnessRecord;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 数据上报业务逻辑:导出/预检/导入合并。
 * 从 ReportController 抽取,Controller 只做 HTTP 层。
 */
@Service
/**
 * 通用报表 SQL 构建:[受白名单保护] 所有动态拼接的表名/列名均经过 safeTable()/safeCol() 白名单校验,
 * 不会拼接用户原始输入,不存在 SQL 注入风险。
 */
public class ReportService {

    public final JdbcTemplate jdbc;
    public final ObjectMapper mapper = new ObjectMapper();

    /** 合法表名白名单(防SQL注入) */
    public static final java.util.Set<String> SAFE_TABLES = new java.util.HashSet<String>(java.util.Arrays.asList(
            "personnel", "department", "equipment", "material", "vehicle",
            "leave_record", "leave_plan", "fitness_record", "military_assessment",
            "position_history", "reward_punish",
            "child_info", "family_member", "driver_license", "personal_card",
            "private_vehicle", "housing_info", "phone_device"));

    public static String safeTable(String t) {
        if (t == null || !SAFE_TABLES.contains(t)) {
            throw new IllegalArgumentException("非法表名: " + t);
        }
        return t;
    }

    public static final String[] SUB_TABLES = {
            "position_history", "reward_punish", "child_info", "family_member",
            "driver_license", "personal_card", "private_vehicle", "housing_info"};

    /** 导入进度注册表 */
    protected static final java.util.concurrent.ConcurrentHashMap<String, Map<String, Object>> PROGRESS =
            new java.util.concurrent.ConcurrentHashMap<String, Map<String, Object>>();

    private final FitnessScoringService fitnessScoring;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        // 保留原构造签名,包括继承此服务的 Controller 和直接构造的隔离测试。
        this.fitnessScoring = new FitnessScoringService(jdbc);
    }

    public Map<String, Object> getProgress(String id) {
        Map<String, Object> m = PROGRESS.get(id);
        return m == null ? new LinkedHashMap<String, Object>() : m;
    }

    public void clearProgress(String id) {
        if (id != null) { PROGRESS.remove(id); }
    }

    protected void prog(String pid, int pct, String stage, String detail) {
        if (pid == null) { return; }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("pct", pct);
        m.put("stage", stage);
        m.put("detail", detail == null ? "" : detail);
        m.put("time", System.currentTimeMillis());
        PROGRESS.entrySet().removeIf(e -> e.getValue().get("time") instanceof Number
                && System.currentTimeMillis() - ((Number) e.getValue().get("time")).longValue() > 600_000L);
        PROGRESS.put(pid, m);
    }

    // === 表中文名 ===
    public static String tableLabel(String table) {
        String[][] zh = {{"position_history", "任职经历"}, {"reward_punish", "奖惩情况"}, {"child_info", "子女信息"},
                {"family_member", "家庭成员"}, {"driver_license", "驾驶证"}, {"personal_card", "个人卡号"},
                {"private_vehicle", "私家车"}, {"housing_info", "住房情况"}, {"equipment", "装备"},
                {"material", "物资"}, {"vehicle", "车辆"}, {"leave_record", "休假"},
                {"leave_plan", "休假计划"}, {"fitness_record", "体能"}, {"military_assessment", "军事考核"},
                {"phone_device", "手机"}};
        for (String[] z : zh) {
            if (z[0].equals(table)) { return z[1]; }
        }
        return table;
    }

    // === key 辅助 ===
    public static String key(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            if (sb.length() > 0) { sb.append('|'); }
            sb.append(p == null ? "" : String.valueOf(p));
        }
        return sb.toString();
    }

    // === 身份证 → 人员ID ===
    public static Long pidOfCard(Object card, Map<String, Long> pidByCard) {
        if (card == null) { return null; }
        return pidByCard.get(String.valueOf(card).trim().toUpperCase());
    }

    // === 通用列名/占位符 ===
    public static List<String> colsOf(Map<String, Object> row) {
        List<String> out = new ArrayList<String>();
        for (String k : row.keySet()) {
            if (!k.startsWith("_")) {
                if (!k.matches("[A-Za-z][A-Za-z0-9_]*")) { throw new IllegalArgumentException("非法列名: " + k); }
                out.add(k);
            }
        }
        return out;
    }

    public static String joinList(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (String c : cols) {
            if (sb.length() > 0) { sb.append(","); }
            sb.append(c);
        }
        return sb.toString();
    }

    public static String marks(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) { sb.append(","); }
            sb.append("?");
        }
        return sb.toString();
    }

    public static Object[] appendHead(Long head, List<Object> tail) {
        Object[] out = new Object[tail.size() + 1];
        out[0] = head;
        for (int i = 0; i < tail.size(); i++) {
            out[i + 1] = tail.get(i);
        }
        return out;
    }
    public static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    public static String card(Object value) { return text(value).toUpperCase(java.util.Locale.ROOT); }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> rows(Map<String, Object> pkg, String name) {
        Object value = pkg.get(name);
        if (value == null) { return new ArrayList<Map<String, Object>>(); }
        if (!(value instanceof List)) { throw new IllegalArgumentException("上报字段必须为数组: " + name); }
        for (Object row : (List<?>) value) {
            if (!(row instanceof Map)) { throw new IllegalArgumentException("非法数据行: " + name); }
        }
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> normalizePackage(Map<String, Object> pkg) {
        if (pkg == null || !(pkg.get("meta") instanceof Map)) { throw new IllegalArgumentException("缺少上报元数据"); }
        Map<String, Object> meta = (Map<String, Object>) pkg.get("meta");
        int version = meta.get("version") instanceof Number ? ((Number) meta.get("version")).intValue() : 1;
        if (version < 1 || version > 2) { throw new IllegalArgumentException("不支持上报包版本: " + version); }
        for (String module : Arrays.asList("personnel", "identities", "leave", "leavePlan", "fitness", "phone", "military", "equipment", "material", "vehicle")) {
            pkg.put(module, rows(pkg, module));
        }
        if (pkg.get("scope") != null && !(pkg.get("scope") instanceof List)) { throw new IllegalArgumentException("scope必须为数组"); }
        if (pkg.get("subs") == null) { pkg.put("subs", new LinkedHashMap<String, Object>()); }
        if (!(pkg.get("subs") instanceof Map)) { throw new IllegalArgumentException("subs必须为对象"); }
        Map<String, Object> subs = (Map<String, Object>) pkg.get("subs");
        for (String table : subs.keySet()) {
            if (!Arrays.asList(SUB_TABLES).contains(table)) { throw new IllegalArgumentException("非法上报子表: " + table); }
            if (subs.get(table) == null) { throw new IllegalArgumentException("上报子表必须为数组: " + table); }
            rows(subs, table);
        }
        // 旧包可从人员清单恢复卡号,绝不将源端本地ID直接用到接收库。
        Map<Long, String> sourceCards = new HashMap<Long, String>();
        for (Map<String, Object> p : identityRows(pkg)) {
            if (p.get("id") instanceof Number) { sourceCards.put(((Number) p.get("id")).longValue(), card(p.get("id_card"))); }
        }
        for (String module : Arrays.asList("leave", "leavePlan", "fitness", "phone", "military")) { attachCards(rows(pkg, module), sourceCards, "personnel_id", "_card"); }
        for (String table : subs.keySet()) { attachCards(rows(subs, table), sourceCards, "personnel_id", "_card"); }
        for (String module : Arrays.asList("equipment", "material", "vehicle")) {
            attachCards(rows(pkg, module), sourceCards, "vehicle".equals(module) ? "driver_id" : "keeper_id",
                    "vehicle".equals(module) ? "_driver_card" : "_keeper_card");
        }
        return pkg;
    }

    private void attachCards(List<Map<String, Object>> rows, Map<Long, String> source, String ref, String attr) {
        for (Map<String, Object> row : rows) {
            if (!card(row.get(attr)).isEmpty()) { continue; }
            Object id = row.get(ref);
            if (id instanceof Number && source.containsKey(((Number) id).longValue())) {
                row.put(attr, source.get(((Number) id).longValue()));
            }
        }
    }

    private List<Map<String, Object>> identityRows(Map<String, Object> pkg) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rows(pkg, "identities"));
        result.addAll(rows(pkg, "personnel")); return result;
    }

    private Map<String, Map<String, Object>> localPeople() {
        Map<String, Map<String, Object>> result = new HashMap<String, Map<String, Object>>();
        for (Map<String, Object> p : jdbc.queryForList("SELECT * FROM personnel")) {
            String c = card(p.get("id_card"));
            if (c.isEmpty()) { continue; }
            if (result.put(c, p) != null) { throw new IllegalArgumentException("接收库身份证重复,请先处理: " + c); }
        }
        return result;
    }

    /** 无可靠更新版本、旧/同版本的修改均不得静默覆盖;保留接收库并反馈。 */
    private boolean mayReplace(String table, Map<String, Object> incoming, Map<String, Object> local, List<String> warnings) {
        String remoteVersion = text(incoming.get("updated_at"));
        String localVersion = text(local.get("updated_at"));
        if (validVersion(remoteVersion) && validVersion(localVersion) && remoteVersion.compareTo(localVersion) > 0) { return true; }
        warnings.add(tableLabel(table) + "版本冲突:源版本=" + remoteVersion + ",本地版本=" + localVersion + ";保留本地,请核对后重新导出");
        return false;
    }

    private boolean validVersion(String version) {
        if (!version.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")) { return false; }
        try { java.time.LocalDateTime.parse(version, java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT)); return true; }
        catch (Exception e) { return false; }
    }

    private Map<String, Object> persistentRow(String table, Map<String, Object> incoming, String... excluded) {
        safeTable(table);
        Set<String> allowed = new HashSet<String>();
        for (Map<String, Object> col : jdbc.queryForList("PRAGMA table_info(" + table + ")")) { allowed.add(text(col.get("name"))); }
        Set<String> ignore = new HashSet<String>(Arrays.asList(excluded));
        // JSON 上报不携带照片二进制,源端路径不能替换本地照片或成为本地文件引用。
        ignore.add("photo");
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (String col : colsOf(incoming)) {
            if (!allowed.contains(col)) { throw new IllegalArgumentException("上报列与schema不兼容: " + table + "." + col); }
            if (!ignore.contains(col)) { row.put(col, incoming.get(col)); }
        }
        return row;
    }

    private long insert(String table, Map<String, Object> row) {
        List<String> cols = new ArrayList<String>(row.keySet());
        return Db.id(jdbc, "INSERT INTO " + safeTable(table) + "(" + joinList(cols) + ") VALUES(" + marks(cols.size()) + ") RETURNING id",
                row.values().toArray());
    }

    private void update(String table, long id, Map<String, Object> row) {
        if (row.isEmpty()) { return; }
        List<Object> args = new ArrayList<Object>(row.values()); args.add(id);
        List<String> sets = new ArrayList<String>(); for (String col : row.keySet()) { sets.add(col + "=?"); }
        jdbc.update("UPDATE " + safeTable(table) + " SET " + String.join(",", sets) + " WHERE id=?", args.toArray());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> previewPackage(Map<String, Object> pkg) {
        List<String> warnings = new ArrayList<String>();
        Map<String, Map<String, Object>> local = localPeople();
        Set<String> source = new HashSet<String>();
        for (Map<String, Object> p : identityRows(pkg)) { if (!card(p.get("id_card")).isEmpty()) { source.add(card(p.get("id_card"))); } }
        for (Map<String, Object> p : rows(pkg, "personnel")) {
            String c = card(p.get("id_card"));
            if (c.isEmpty()) { warnings.add("人员缺身份证,跳过且不使用源端ID绑定"); }
            else if (local.containsKey(c)) { mayReplace("personnel", p, local.get(c), warnings); }
        }
        List<List<Map<String, Object>>> collections = new ArrayList<List<Map<String, Object>>>();
        for (String module : Arrays.asList("leave", "leavePlan", "fitness", "phone", "military")) { collections.add(rows(pkg, module)); }
        Map<String, Object> subs = (Map<String, Object>) pkg.get("subs");
        for (String table : subs.keySet()) { collections.add(rows(subs, table)); }
        for (List<Map<String, Object>> list : collections) {
            for (Map<String, Object> row : list) {
                String c = card(row.get("_card"));
                if (c.isEmpty() || (!local.containsKey(c) && !source.contains(c))) { warnings.add("人员关联缺稳定身份证或接收人员不存在,导入将拒绝:源ID=" + row.get("personnel_id")); }
            }
        }
        for (String table : Arrays.asList("equipment", "material", "vehicle")) {
            String ref = "vehicle".equals(table) ? "driver_id" : "keeper_id";
            String attr = "vehicle".equals(table) ? "_driver_card" : "_keeper_card";
            Map<String, Map<String, Object>> existing = assetIndex(table);
            Set<String> seen = new HashSet<String>();
            for (Map<String, Object> row : rows(pkg, table)) {
                String c = card(row.get(attr));
                if ((!text(row.get(ref)).isEmpty() && !"0".equals(text(row.get(ref)))) && (c.isEmpty() || (!source.contains(c) && !local.containsKey(c)))) {
                    warnings.add(tableLabel(table) + "责任人无法稳定映射,将解除绑定而非套用源端本地ID");
                }
                String k = assetKey(table, row);
                if (!seen.add(k)) { warnings.add(tableLabel(table) + "包内重复业务键将仅接收一次: " + k); }
                if (existing.containsKey(k)) { mayReplace(table, row, existing.get(k), warnings); }
            }
        }
        if (pkg.get("scope") instanceof List && !((List<?>) pkg.get("scope")).contains("personnel")) {
            warnings.add("独立模块只匹配已存在身份证人员,不创建或更新人员;请先接收人员包");
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("warnings", warnings); out.put("versionPolicy", "updated_at必须为明确且严格较新的版本;旧/同/缺版本保留本地");
        return out;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> receivePackage(Map<String, Object> pkg, Long deptId, boolean createNew, Long parentId, String progressId) {
        List<String> warnings = new ArrayList<String>();
        Map<String, Map<String, Object>> local = localPeople();
        Map<String, Long> ids = new HashMap<String, Long>();
        for (Map.Entry<String, Map<String, Object>> e : local.entrySet()) { ids.put(e.getKey(), ((Number) e.getValue().get("id")).longValue()); }
        Map<String, Object> meta = (Map<String, Object>) pkg.get("meta");
        String unit = text(meta.get("unit"));
        String deptName;
        long targetDept;
        if (createNew) {
            long parent = parentId == null ? 0 : parentId;
            if (parent != 0 && jdbc.queryForList("SELECT id FROM department WHERE id=?", parent).isEmpty()) { throw new IllegalArgumentException("上级单位不存在"); }
            targetDept = Db.id(jdbc, "INSERT INTO department(name,parent_id,sort,remark) VALUES(?,?,50,'下级上报自动创建') RETURNING id", unit, parent); deptName = unit;
        } else {
            if (deptId == null) { throw new IllegalArgumentException("请选择接收单位"); }
            targetDept = deptId; deptName = jdbc.queryForObject("SELECT name FROM department WHERE id=?", String.class, deptId);
        }
        prog(progressId, 12, "合并人员", null);
        int added = 0, updated = 0, skipped = 0;
        Set<String> accepted = new LinkedHashSet<String>();
        Set<String> seenPeople = new HashSet<String>();
        for (Map<String, Object> p : rows(pkg, "personnel")) {
            String c = card(p.get("id_card"));
            if (c.isEmpty()) { skipped++; warnings.add("人员缺身份证,跳过且不使用源端ID绑定"); continue; }
            if (!seenPeople.add(c)) { skipped++; warnings.add("包内重复身份证仅接收一次: " + c); continue; }
            Map<String, Object> row = persistentRow("personnel", p, "id", "created_at", "department_id"); row.put("id_card", c);
            if (local.containsKey(c)) {
                if (!mayReplace("personnel", p, local.get(c), warnings)) { skipped++; continue; }
                update("personnel", ids.get(c), row); updated++;
            } else {
                row.put("department_id", targetDept); long id = insert("personnel", row); ids.put(c, id); added++;
            }
            accepted.add(c);
        }
        int subRows = replaceSubs(pkg, ids, accepted, warnings);
        prog(progressId, 65, "合并休假体能手机", null);
        int leaveAdded = mergeRecords("leave_record", rows(pkg, "leave"), ids, new String[]{"leave_type", "start_date", "end_date"}, warnings);
        int leavePlanAdded = mergeRecords("leave_plan", rows(pkg, "leavePlan"), ids, new String[]{"year", "leave_type", "start_date", "end_date"}, warnings);
        int fitnessAdded = mergeRecords("fitness_record", rows(pkg, "fitness"), ids, new String[]{"item", "test_date"}, warnings);
        int militaryAdded = mergeRecords("military_assessment", rows(pkg, "military"), ids, new String[]{"category", "item", "score", "test_date"}, warnings);
        int phoneAdded = mergeRecords("phone_device", rows(pkg, "phone"), ids, new String[]{"device_type", "serial_no", "imei1", "imei2", "phone_number"}, warnings);
        if (!rows(pkg, "phone").isEmpty()) {
            Set<Long> phonePeople = new HashSet<Long>();
            for (Map<String, Object> row : rows(pkg, "phone")) { phonePeople.add(requiredPerson(row, ids)); }
            for (Long personId : phonePeople) {
                List<String> numbers = jdbc.queryForList("SELECT phone_number FROM phone_device WHERE personnel_id=? AND TRIM(COALESCE(phone_number,''))<>'' ORDER BY id DESC LIMIT 1", String.class, personId);
                jdbc.update("UPDATE personnel SET phone=? WHERE id=?", numbers.isEmpty() ? "" : numbers.get(0), personId);
            }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (String table : Arrays.asList("equipment", "material", "vehicle")) {
            int[] counts = mergeAssets(table, rows(pkg, table), ids, warnings);
            result.put(table + "Added", counts[0]); result.put(table + "Updated", counts[1]); result.put(table + "Total", rows(pkg, table).size());
        }
        result.put("deptName", deptName); result.put("created", createNew);
        result.put("added", added); result.put("updated", updated); result.put("skipped", skipped);
        result.put("subRows", subRows); result.put("leaveAdded", leaveAdded); result.put("fitnessAdded", fitnessAdded); result.put("phoneAdded", phoneAdded);
        result.put("leavePlanAdded", leavePlanAdded); result.put("militaryAdded", militaryAdded);
        result.put("leaveTotal", rows(pkg, "leave").size()); result.put("fitnessTotal", rows(pkg, "fitness").size()); result.put("phoneTotal", rows(pkg, "phone").size());
        result.put("leavePlanTotal", rows(pkg, "leavePlan").size()); result.put("militaryTotal", rows(pkg, "military").size());
        result.put("warnings", warnings);
        prog(progressId, 100, "导入完成", null); return result;
    }

    // === 资产业务键:装备=编号,物资=名称,车辆=车牌 ===
    private static String assetKeyColumn(String table) {
        if ("equipment".equals(table)) { return "code"; }
        if ("material".equals(table)) { return "name"; }
        if ("vehicle".equals(table)) { return "plate_no"; }
        throw new IllegalArgumentException("未知资产表: " + table);
    }

    private static String assetKey(String table, Map<String, Object> row) {
        return text(row.get(assetKeyColumn(table)));
    }

    /** 本库已有资产按业务键索引;业务键为空/重复的行不参与匹配,避免误更新 */
    private Map<String, Map<String, Object>> assetIndex(String table) {
        Map<String, Map<String, Object>> index = new HashMap<String, Map<String, Object>>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM " + safeTable(table))) {
            String k = assetKey(table, row);
            if (k.isEmpty() || index.put(k, row) != null) { index.remove(k); }
        }
        return index;
    }

    /** 人员关联必须能稳定映射到接收库,否则拒绝该行 */
    private Long requiredPerson(Map<String, Object> row, Map<String, Long> ids) {
        String c = card(row.get("_card"));
        Long id = c.isEmpty() ? null : ids.get(c);
        if (id == null) {
            throw new IllegalArgumentException("人员关联缺稳定身份证或接收人员不存在,已拒绝:源ID=" + row.get("personnel_id"));
        }
        return id;
    }

    /** 子表替换:仅替换本批次接收的人员,该人员包内没有的旧记录一并清除(下级为准) */
    @SuppressWarnings("unchecked")
    private int replaceSubs(Map<String, Object> pkg, Map<String, Long> ids, Set<String> accepted, List<String> warnings) {
        int count = 0;
        Map<String, Object> subs = (Map<String, Object>) pkg.get("subs");
        for (String table : SUB_TABLES) {
            // 缺失键表示未上报该类;显式数组(即使为空)表示接受人员的该类完整快照。
            if (!subs.containsKey(table)) { continue; }
            Map<Long, List<Map<String, Object>>> byPerson = new HashMap<Long, List<Map<String, Object>>>();
            for (String c : accepted) {
                Long id = ids.get(c);
                if (id != null) { byPerson.put(id, new ArrayList<Map<String, Object>>()); }
            }
            for (Map<String, Object> row : rows(subs, table)) {
                String c = card(row.get("_card"));
                if (c.isEmpty() || !accepted.contains(c)) { continue; }
                Long id = ids.get(c);
                if (id == null) { continue; }
                (byPerson.computeIfAbsent(id, k -> new ArrayList<Map<String, Object>>())).add(row);
            }
            for (Map.Entry<Long, List<Map<String, Object>>> entry : byPerson.entrySet()) {
                long personId = entry.getKey();
                jdbc.update("DELETE FROM " + safeTable(table) + " WHERE personnel_id=?", personId);
                for (Map<String, Object> row : entry.getValue()) {
                    Map<String, Object> insert = persistentRow(table, row, "id", "created_at");
                    insert.put("personnel_id", personId);
                    insert(table, insert);
                    count++;
                }
            }
        }
        return count;
    }

    /** 休假/体能/手机:按人员+业务键去重(库内与本批次内都去重),已存在则按版本策略更新 */
    private int mergeRecords(String table, List<Map<String, Object>> incoming, Map<String, Long> ids,
                             String[] keyCols, List<String> warnings) {
        int added = 0;
        Set<String> seenInBatch = new HashSet<String>();
        for (Map<String, Object> row : incoming) {
            Long personId;
            try { personId = requiredPerson(row, ids); }
            catch (IllegalArgumentException e) { warnings.add(tableLabel(table) + ":" + e.getMessage()); continue; }
            if ("fitness_record".equals(table)) {
                FitnessRecord record = new FitnessRecord();
                record.setPersonnelId(personId);
                record.setItem(text(row.get("item")));
                record.setTestDate(text(row.get("test_date")));
                record.setScore(text(row.get("score")));
                fitnessScoring.score(record);
                row = new LinkedHashMap<String, Object>(row);
                row.put("item", record.getItem());
                row.put("test_date", record.getTestDate());
                row.put("score", record.getScore());
                row.put("points", record.getPoints());
                row.put("grade", record.getGrade());
            }
            List<String> keyValues = new ArrayList<String>();
            for (String col : keyCols) { keyValues.add(text(row.get(col))); }
            String batchKey = key(personId, (Object[]) keyValues.toArray(new String[0]));
            if (!seenInBatch.add(batchKey)) { warnings.add(tableLabel(table) + "包内重复记录仅接收一次: " + keyValues); continue; }
            StringBuilder where = new StringBuilder("personnel_id=?");
            List<Object> args = new ArrayList<Object>(Collections.singleton(personId));
            for (int i = 0; i < keyCols.length; i++) {
                where.append(" AND TRIM(COALESCE(").append(keyCols[i]).append(",''))=?");
                args.add(keyValues.get(i));
            }
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT * FROM " + safeTable(table) + " WHERE " + where + " LIMIT 2", args.toArray());
            Map<String, Object> insert = persistentRow(table, row, "id", "created_at");
            insert.put("personnel_id", personId);
            if (existing.isEmpty()) { insert(table, insert); added++; }
            else {
                if (existing.size() > 1) { warnings.add(tableLabel(table) + "本库存在重复记录,仅更新其一: " + keyValues); }
                if (mayReplace(table, row, existing.get(0), warnings)) {
                    update(table, ((Number) existing.get(0).get("id")).longValue(), insert);
                }
            }
        }
        return added;
    }

    /** 资产合并:按业务键(空键视为新记录);责任人/驾驶员按身份证重映射,无法映射则解除绑定 */
    private int[] mergeAssets(String table, List<Map<String, Object>> incoming, Map<String, Long> ids, List<String> warnings) {
        String ref = "vehicle".equals(table) ? "driver_id" : "keeper_id";
        String attr = "vehicle".equals(table) ? "_driver_card" : "_keeper_card";
        Map<String, Map<String, Object>> index = assetIndex(table);
        Set<String> seen = new HashSet<String>();
        int added = 0, updated = 0;
        for (Map<String, Object> row : incoming) {
            String k = assetKey(table, row);
            if (!k.isEmpty() && !seen.add(k)) { warnings.add(tableLabel(table) + "包内重复业务键仅接收一次: " + k); continue; }
            Map<String, Object> local = k.isEmpty() ? null : index.get(k);
            if (local != null && !mayReplace(table, row, local, warnings)) { continue; }
            Map<String, Object> save = persistentRow(table, row, "id", "created_at");
            String cardNo = card(row.get(attr));
            Long mapped = cardNo.isEmpty() ? null : ids.get(cardNo);
            boolean hadRef = !text(row.get(ref)).isEmpty() && !"0".equals(text(row.get(ref)));
            if (mapped != null) { save.put(ref, mapped); }
            else {
                if (hadRef) { warnings.add(tableLabel(table) + "责任人无法稳定映射,已解除绑定: " + (k.isEmpty() ? "(无业务键)" : k)); }
                save.put(ref, 0L);
            }
            if (local == null) { insert(table, save); added++; index.put(k, save); }
            else { update(table, ((Number) local.get("id")).longValue(), save); updated++; }
        }
        return new int[]{added, updated};
    }

}
