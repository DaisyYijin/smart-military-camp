package com.szjx.personnel.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 备份恢复服务。
 *
 * 备份文件为 zip(data/backup/ 下):
 *   meta.json                元数据(时间/包含模块及行数)
 *   data/<模块>/<表>.json     各表全部行数据
 *   photos/<文件名>           人员照片(勾选人员模块时)
 *
 * 备份与恢复均可按模块选择;恢复采用"先清空对应表再写入"的方式,
 * 整个恢复请求使用单事务并暂存照片,失败整体回滚。管理员账号(admin_user)不参与备份恢复。
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final File dir;
    private final File photosDir;
    private final File settingsFile;
    private static final String[] FITNESS_SETTINGS = {"fitness_grade_rule", "bmi_standard"};

    /** 模块 → 数据表(模块 key 与网页端菜单一致) */
    public static final Map<String, String[]> MODULE_TABLES = new LinkedHashMap<String, String[]>();

    static {
        MODULE_TABLES.put("personnel", new String[]{"personnel", "position_history", "reward_punish", "child_info", "family_member", "driver_license", "personal_card", "private_vehicle", "housing_info"});
        MODULE_TABLES.put("department", new String[]{"department"});
        MODULE_TABLES.put("dict", new String[]{"sys_dict", "sys_setting", "holiday_calendar"});
        MODULE_TABLES.put("equipment", new String[]{"equipment"});
        MODULE_TABLES.put("material", new String[]{"material"});
        MODULE_TABLES.put("vehicle", new String[]{"vehicle"});
        MODULE_TABLES.put("leave", new String[]{"leave_record"});
        MODULE_TABLES.put("fitness", new String[]{"fitness_record", "fitness_matrix"});
        MODULE_TABLES.put("phone", new String[]{"phone_device"});
        MODULE_TABLES.put("military", new String[]{"military_assessment"});
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    public BackupService(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this(jdbc, txManager, new File("data"));
    }

    /** 隔离测试可显式指定临时根目录,不触及业务 data。 */
    public BackupService(JdbcTemplate jdbc, PlatformTransactionManager txManager, File root) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.dir = new File(root, "backup");
        this.photosDir = new File(root, "photos");
        this.settingsFile = new File(root, "backup-settings.json");
    }

    private List<String> tablesFor(String module) {
        List<String> tables = new ArrayList<String>(Arrays.asList(MODULE_TABLES.get(module)));
        // 旧库若实际保留历史标准表则原样备份,新库绝不查询不存在的表。
        if ("fitness".equals(module) && tableExists("fitness_standard")) {
            tables.add("fitness_standard");
        }
        return tables;
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", Integer.class, table);
        return count != null && count > 0;
    }

    /* ================= 设置(自动备份) ================= */

    public static class Settings {
        public boolean enabled = false;
        public int intervalHours = 24;
        public int keepCount = 10;
        public List<String> modules = allModuleKeys();
        public long autoLastRun = 0;
    }

    public static List<String> allModuleKeys() {
        return new ArrayList<String>(MODULE_TABLES.keySet());
    }

    public synchronized Settings loadSettings() {
        if (settingsFile.exists()) {
            try {
                Settings s = mapper.readValue(settingsFile, Settings.class);
                if (s != null) {
                    if (s.modules == null) {
                        s.modules = allModuleKeys();
                    }
                    return s;
                }
            } catch (Exception e) {
                log.warn("备份设置读取失败,使用默认: {}", e.getMessage());
            }
        }
        return new Settings();
    }

    public synchronized void saveSettings(Settings s) {
        if (s.intervalHours < 1) {
            s.intervalHours = 1;
        }
        if (s.keepCount < 1) {
            s.keepCount = 1;
        }
        if (s.keepCount > 200) {
            s.keepCount = 200;
        }
        if (s.modules == null) {
            s.modules = allModuleKeys();
        } else {
            for (String m : s.modules) {
                if (!MODULE_TABLES.containsKey(m)) {
                    throw new IllegalArgumentException("未知模块: " + m);
                }
            }
        }
        if (s.enabled && s.modules.isEmpty()) { throw new IllegalArgumentException("启用自动备份需至少选择一个模块"); }
        try {
            File parent = settingsFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile(parent.toPath(), "settings-", ".tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), s);
                java.nio.file.Files.move(tmp, settingsFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally { java.nio.file.Files.deleteIfExists(tmp); }
        } catch (Exception e) {
            throw new RuntimeException("备份设置保存失败: " + e.getMessage(), e);
        }
    }

    /* ================= 备份 ================= */

    public synchronized Map<String, Object> createBackup(List<String> modules, boolean auto) {
        List<String> selected = checkModules(modules);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prefix = auto ? "auto_" : "";
        File file = new File(dir, prefix + "backup_" + stamp + ".zip");
        int n = 1;
        while (file.exists()) {
            file = new File(dir, prefix + "backup_" + stamp + "_" + (n++) + ".zip");
        }

        Map<String, Object> moduleSummary = new LinkedHashMap<String, Object>();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {

            // 先收集各模块数据(同时统计行数),meta 才能带上准确的模块清单
            Map<String, Map<String, byte[]>> moduleData = new LinkedHashMap<String, Map<String, byte[]>>();
            for (String module : selected) {
                Map<String, byte[]> tablesData = new LinkedHashMap<String, byte[]>();
                Map<String, Object> tables = new LinkedHashMap<String, Object>();
                for (String table : tablesFor(module)) {
                    List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + table);
                    tablesData.put(table, mapper.writeValueAsBytes(rows));
                    tables.put(table, rows.size());
                }
                if ("fitness".equals(module)) {
                    List<Map<String, Object>> standards = jdbc.queryForList("SELECT * FROM sys_setting WHERE skey IN (?,?)", (Object[]) FITNESS_SETTINGS);
                    tablesData.put("sys_setting", mapper.writeValueAsBytes(standards));
                    tables.put("sys_setting", standards.size());
                }
                moduleData.put(module, tablesData);
                moduleSummary.put(module, tables);
            }

            Map<String, Object> meta = new LinkedHashMap<String, Object>();
            meta.put("version", 2);
            meta.put("photoSnapshot", selected.contains("personnel"));
            meta.put("fitnessSettings", selected.contains("fitness"));
            meta.put("auto", auto);
            meta.put("created", System.currentTimeMillis());
            meta.put("createdText", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            meta.put("modules", moduleSummary);
            writeEntry(zip, "meta.json", mapper.writeValueAsBytes(meta));

            for (String module : selected) {
                Map<String, byte[]> tablesData = moduleData.get(module);
                for (String table : tablesData.keySet()) {
                    writeEntry(zip, "data/" + module + "/" + table + ".json", tablesData.get(table));
                }
            }

            // 人员模块附带照片
            int photoCount = 0;
            if (selected.contains("personnel") && photosDir.exists()) {
                File[] photos = photosDir.listFiles();
                if (photos != null) {
                    for (File p : photos) {
                        if (p.isFile()) {
                            writeEntry(zip, "photos/" + p.getName(), readBytes(p));
                            photoCount++;
                        }
                    }
                }
            }
            zip.close();

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("name", file.getName());
            result.put("size", file.length());
            result.put("auto", auto);
            result.put("modules", moduleSummary);
            result.put("photos", photoCount);
            log.info("已创建备份 {} (模块 {} 张,照片 {})", file.getName(), selected.size(), photoCount);
            return result;
        } catch (Exception e) {
            file.delete();
            throw new RuntimeException("备份创建失败: " + e.getMessage(), e);
        }
    }

    /* ================= 列表 / 删除 ================= */

    public synchronized List<Map<String, Object>> listBackups() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        File[] files = dir == null || !dir.exists() ? null : dir.listFiles();
        if (files == null) {
            return list;
        }
        Arrays.sort(files);
        for (int i = files.length - 1; i >= 0; i--) {
            File f = files[i];
            if (!f.getName().endsWith(".zip")) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", f.getName());
            item.put("size", f.length());
            Map<String, Object> meta = readMeta(f);
            if (meta != null) {
                item.put("createdText", meta.get("createdText"));
                Object mods = meta.get("modules");
                item.put("modules", mods);
                Boolean auto = (Boolean) meta.get("auto");
                item.put("auto", auto != null && auto);
            } else {
                item.put("createdText", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new Date(f.lastModified())));
                item.put("modules", null);
                item.put("auto", f.getName().startsWith("auto_"));
            }
            list.add(item);
        }
        return list;
    }

    public synchronized void delete(String name) {
        File f = safeFile(name);
        if (f.exists() && !f.delete()) {
            throw new IllegalStateException("备份文件删除失败: " + name);
        }
    }

    /** 校验文件名并返回备份目录下的文件(供下载用) */
    public synchronized File getBackupFile(String name) {
        File f = safeFile(name);
        if (!f.exists()) {
            throw new IllegalArgumentException("备份文件不存在: " + name);
        }
        return f;
    }

    /* ================= 恢复 ================= */

    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> restore(String name, List<String> requestedModules) {
        File f = getBackupFile(name);
        List<String> selected = checkModules(requestedModules);
        Map<String, Object> meta = readMeta(f);
        if (meta == null || !(meta.get("modules") instanceof Map)) { throw new IllegalArgumentException("备份缺少有效元数据"); }
        Map<String, Object> available = (Map<String, Object>) meta.get("modules");
        int version = meta.get("version") instanceof Number ? ((Number) meta.get("version")).intValue() : 1;
        if (version < 1 || version > 2) { throw new IllegalArgumentException("不支持的备份版本: " + version); }
        Map<String, List<Map<String, Object>>> data = new LinkedHashMap<String, List<Map<String, Object>>>();
        List<String> warnings = new ArrayList<String>();
        List<Map<String, Object>> restored = new ArrayList<Map<String, Object>>();
        java.nio.file.Path stage = null, oldPhotos = null;
        final boolean[] switched = {false}, oldMoved = {false};
        boolean committed = false;
        int photoCount = 0;
        Map<String, Object> safety;
        try (ZipFile zip = new ZipFile(f)) {
            java.util.Set<String> entries = new java.util.HashSet<String>();
            java.util.Enumeration<? extends ZipEntry> all = zip.entries();
            List<ZipEntry> photos = new ArrayList<ZipEntry>();
            while (all.hasMoreElements()) {
                ZipEntry e = all.nextElement(); String path = e.getName();
                if (!entries.add(path) || path.contains("\\") || path.startsWith("/") || path.contains(":")
                        || Arrays.asList(path.split("/")).contains("..")) { throw new IllegalArgumentException("危险或重复路径: " + path); }
                if (path.startsWith("photos/") && !e.isDirectory()) { safePhotoName(path.substring(7)); photos.add(e); }
            }
            for (String module : selected) {
                if (!(available.get(module) instanceof Map)) { throw new IllegalArgumentException("所选模块不在备份中: " + module); }
                Map<String, Object> declared = (Map<String, Object>) available.get(module);
                List<String> tables = new ArrayList<String>(Arrays.asList(MODULE_TABLES.get(module)));
                if ("fitness".equals(module)) {
                    if (version >= 2 || declared.containsKey("sys_setting")) { tables.add("sys_setting"); }
                    if (declared.containsKey("fitness_standard")) { tables.add("fitness_standard"); }
                }
                Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
                for (String table : tables) {
                    String entryName = "data/" + module + "/" + table + ".json";
                    ZipEntry entry = zip.getEntry(entryName);
                    if (entry == null || entry.isDirectory()) { throw new IllegalArgumentException("缺少必需表文件: " + entryName); }
                    List<Map<String, Object>> rows;
                    try (InputStream in = zip.getInputStream(entry)) {
                        Object value = mapper.readValue(in, Object.class);
                        if (!(value instanceof List)) { throw new IllegalArgumentException("表文件必须是数组: " + table); }
                        rows = (List<Map<String, Object>>) value;
                    }
                    if (declared.get(table) instanceof Number && ((Number) declared.get(table)).intValue() != rows.size()) {
                        throw new IllegalArgumentException("表行数与元数据不一致: " + table);
                    }
                    if ("fitness_standard".equals(table) && !tableExists(table)) {
                        if (!rows.isEmpty()) { throw new IllegalArgumentException("旧体能标准非空且接收库缺兼容schema,需转换旧标准"); }
                        warnings.add("忽略旧包空fitness_standard;当前标准位于fitness_matrix/sys_setting"); continue;
                    }
                    validateRows(table, rows);
                    if ("fitness".equals(module) && "sys_setting".equals(table)) {
                        for (Map<String, Object> row : rows) {
                            if (!Arrays.asList(FITNESS_SETTINGS).contains(row.get("skey"))) { throw new IllegalArgumentException("体能标准含非体能设置键"); }
                        }
                        data.put("fitness_settings", rows);
                    } else { data.put(table, rows); }
                    counts.put(table, rows.size());
                }
                Map<String, Object> r = new LinkedHashMap<String, Object>();
                r.put("module", module); r.put("tables", counts); restored.add(r);
            }
            validateDependencies(data);
            boolean replace = selected.contains("personnel") && (Boolean.TRUE.equals(meta.get("photoSnapshot")) || !photos.isEmpty());
            if (replace) {
                java.nio.file.Path parent = photosDir.toPath().toAbsolutePath().getParent();
                rejectSymlink(photosDir.toPath()); java.nio.file.Files.createDirectories(parent);
                stage = java.nio.file.Files.createTempDirectory(parent, "restore-photos-");
                oldPhotos = parent.resolve("restore-old-" + java.util.UUID.randomUUID());
                for (ZipEntry e : photos) {
                    try (InputStream in = zip.getInputStream(e)) { java.nio.file.Files.copy(in, stage.resolve(e.getName().substring(7))); }
                    photoCount++;
                }
            }
            safety = createBackup(allModuleKeys(), false);
            final java.nio.file.Path staged = stage, previous = oldPhotos;
            final boolean swap = replace;
            try {
                tx.executeWithoutResult(status -> {
                    jdbc.execute("PRAGMA defer_foreign_keys=ON");
                    for (String table : data.keySet()) {
                        if ("fitness_settings".equals(table)) { jdbc.update("DELETE FROM sys_setting WHERE skey IN (?,?)", (Object[]) FITNESS_SETTINGS); }
                        else { jdbc.update("DELETE FROM " + table); }
                    }
                    for (Map.Entry<String, List<Map<String, Object>>> e : data.entrySet()) {
                        String table = "fitness_settings".equals(e.getKey()) ? "sys_setting" : e.getKey();
                        for (Map<String, Object> row : e.getValue()) { insertRow(table, row); }
                    }
                    if (!jdbc.queryForList("PRAGMA foreign_key_check").isEmpty()) { throw new IllegalArgumentException("恢复外键冲突"); }
                    if (swap) {
                        try {
                            if (java.nio.file.Files.exists(photosDir.toPath())) { java.nio.file.Files.move(photosDir.toPath(), previous); oldMoved[0] = true; }
                            java.nio.file.Files.move(staged, photosDir.toPath()); switched[0] = true;
                        } catch (Exception e) { throw new IllegalStateException("照片替换失败", e); }
                    }
                });
                committed = true;
            } catch (RuntimeException e) {
                if (switched[0]) { deleteTree(photosDir.toPath()); }
                if (oldMoved[0]) {
                    try { java.nio.file.Files.move(previous, photosDir.toPath()); }
                    catch (Exception rollback) { e.addSuppressed(rollback); }
                }
                throw e;
            }
        } catch (IllegalArgumentException | IllegalStateException e) { throw e;
        } catch (Exception e) { throw new RuntimeException("恢复失败: " + e.getMessage(), e);
        } finally {
            if (stage != null) { deleteTree(stage); }
            if (oldPhotos != null && committed) { deleteTree(oldPhotos); }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("restored", restored); result.put("photos", photoCount);
        result.put("beforeRestoreBackup", safety.get("name")); result.put("warnings", warnings);
        return result;
    }

    private void validateRows(String table, List<Map<String, Object>> rows) {
        List<Map<String, Object>> info = jdbc.queryForList("PRAGMA table_info(" + table + ")");
        if (info.isEmpty()) { throw new IllegalArgumentException("接收库缺少表: " + table); }
        java.util.Set<String> cols = new java.util.HashSet<String>();
        for (Map<String, Object> col : info) { cols.add(String.valueOf(col.get("name"))); }
        for (Object value : rows) {
            if (!(value instanceof Map)) { throw new IllegalArgumentException("非法数据行: " + table); }
            Map<?, ?> row = (Map<?, ?>) value;
            if (row.isEmpty() || !cols.containsAll(row.keySet())) { throw new IllegalArgumentException("表列与schema不兼容: " + table); }
            for (Map<String, Object> col : info) {
                if ("1".equals(String.valueOf(col.get("notnull"))) && col.get("dflt_value") == null && row.get(col.get("name")) == null) {
                    throw new IllegalArgumentException("缺少必需字段: " + table + "." + col.get("name"));
                }
            }
        }
    }

    private void validateDependencies(Map<String, List<Map<String, Object>>> data) {
        List<String[]> refs = new ArrayList<String[]>();
        refs.add(new String[]{"personnel", "department_id", "department"});
        refs.add(new String[]{"department", "parent_id", "department"});
        refs.add(new String[]{"department", "leader_id", "personnel"});
        refs.add(new String[]{"sys_dict", "parent_id", "sys_dict"});
        refs.add(new String[]{"equipment", "keeper_id", "personnel"});
        refs.add(new String[]{"material", "keeper_id", "personnel"});
        refs.add(new String[]{"vehicle", "driver_id", "personnel"});
        for (String table : Arrays.asList("leave_record", "fitness_record", "phone_device", "position_history", "reward_punish", "child_info", "family_member", "driver_license", "personal_card", "private_vehicle", "housing_info")) {
            refs.add(new String[]{table, "personnel_id", "personnel"});
        }
        for (String[] ref : refs) {
            if (!data.containsKey(ref[0]) && !data.containsKey(ref[2])) { continue; }
            List<Map<String, Object>> sources = data.containsKey(ref[0]) ? data.get(ref[0]) : jdbc.queryForList("SELECT * FROM " + ref[0]);
            List<Map<String, Object>> targets = data.containsKey(ref[2]) ? data.get(ref[2]) : jdbc.queryForList("SELECT id FROM " + ref[2]);
            java.util.Set<Long> ids = new java.util.HashSet<Long>();
            for (Map<String, Object> row : targets) { if (row.get("id") instanceof Number) { ids.add(((Number) row.get("id")).longValue()); } }
            for (Map<String, Object> row : sources) {
                Object value = row.get(ref[1]);
                if (value == null || "0".equals(String.valueOf(value)) || "".equals(value)) { continue; }
                long id;
                try { id = Long.parseLong(String.valueOf(value)); }
                catch (Exception e) { throw new IllegalArgumentException("非法依赖ID: " + ref[0] + "." + ref[1]); }
                if (!ids.contains(id)) { throw new IllegalArgumentException("恢复依赖不完整: " + ref[0] + "." + ref[1] + "=" + id + ",请同时恢复依赖模块"); }
            }
        }
    }

    private void insertRow(String table, Map<String, Object> row) {
        List<String> cols = new ArrayList<String>(row.keySet());
        StringBuilder names = new StringBuilder(), marks = new StringBuilder();
        for (String col : cols) {
            if (names.length() > 0) { names.append(","); marks.append(","); }
            names.append('"').append(col.replace("\"", "\"\"")).append('"'); marks.append("?");
        }
        Object[] args = new Object[cols.size()];
        for (int i = 0; i < cols.size(); i++) { args[i] = row.get(cols.get(i)); }
        jdbc.update("INSERT INTO " + table + "(" + names + ") VALUES(" + marks + ")", args);
    }

    private static void safePhotoName(String name) {
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains(":")
                || name.endsWith(".") || name.endsWith(" ") || name.contains("..")
                || name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            throw new IllegalArgumentException("非法照片文件名: " + name);
        }
    }

    private static void rejectSymlink(java.nio.file.Path path) throws java.io.IOException {
        java.nio.file.Path current = path.toAbsolutePath();
        while (current != null) {
            if (java.nio.file.Files.isSymbolicLink(current)) { throw new IllegalArgumentException("不允许符号链接路径"); }
            current = current.getParent();
        }
    }

    private static void deleteTree(java.nio.file.Path path) {
        if (!java.nio.file.Files.exists(path)) { return; }
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(path)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { java.nio.file.Files.deleteIfExists(p); } catch (Exception e) { log.warn("暂存清理失败: {}", p); }
            });
        } catch (Exception e) { log.warn("暂存目录清理失败: {}", path); }
    }

    /* ================= 自动备份调度 ================= */

    /** 到期则执行一次自动备份并清理超额的旧自动备份;由定时器周期调用 */
    public synchronized void runAutoIfDue() {
        Settings s = loadSettings();
        if (!s.enabled) {
            return;
        }
        long interval = s.intervalHours * 3600L * 1000L;
        if (System.currentTimeMillis() - s.autoLastRun < interval) {
            return;
        }
        try {
            createBackup(s.modules, true);
            s.autoLastRun = System.currentTimeMillis();
            saveSettings(s);
            pruneAutoBackups(s.keepCount);
        } catch (Exception e) {
            // 失败不伪造成功时间、不轮转掉最后可用的备份。
            log.error("自动备份失败: {}", e.getMessage());
        }
    }

    private void pruneAutoBackups(int keepCount) {
        File[] files = dir == null || !dir.exists() ? null : dir.listFiles();
        if (files == null) {
            return;
        }
        List<File> autos = new ArrayList<File>();
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith("auto_") && f.getName().endsWith(".zip")) {
                autos.add(f);
            }
        }
        if (autos.size() <= keepCount) {
            return;
        }
        Collections.sort(autos);
        for (int i = 0; i < autos.size() - keepCount; i++) {
            autos.get(i).delete();
        }
    }

    /* ================= 内部工具 ================= */

    private List<String> checkModules(List<String> modules) {
        if (modules == null || modules.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个要备份的模块");
        }
        List<String> out = new ArrayList<String>();
        for (String m : modules) {
            if (!MODULE_TABLES.containsKey(m)) {
                throw new IllegalArgumentException("未知模块: " + m);
            }
            if (!out.contains(m)) {
                out.add(m);
            }
        }
        return out;
    }

    private File safeFile(String name) {
        if (name == null || !name.matches("[\\w\\-.]+\\.zip") || name.contains("..")) {
            throw new IllegalArgumentException("非法的备份文件名");
        }
        File file = new File(dir, name);
        try { rejectSymlink(file.toPath()); } catch (java.io.IOException e) { throw new IllegalArgumentException("非法备份路径", e); }
        return file;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMeta(File zipFile) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry meta = zip.getEntry("meta.json");
            if (meta == null) {
                return null;
            }
            return mapper.readValue(zip.getInputStream(meta), Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeEntry(ZipOutputStream zip, String path, byte[] bytes) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }

    private byte[] readBytes(File f) throws java.io.IOException {
        byte[] data = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
        }
        return data;
    }
}
