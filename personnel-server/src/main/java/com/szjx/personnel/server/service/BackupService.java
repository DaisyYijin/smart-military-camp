package com.szjx.personnel.server.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szjx.personnel.server.util.ItemLoanSnapshots;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static com.szjx.personnel.server.service.BackupArchiveGuard.*;

/**
 * 备份恢复服务。
 *
 * 备份文件为 zip(data/backup/ 下):
 *   meta.json                元数据(时间/包含模块及行数)
 *   data/<模块>/<表>.json     各表全部行数据
 *   photos/<文件名>           人员照片目录中的直属文件
 *   photos/personnel-trash/<文件名> 人员删除撤回记录引用的照片
 *   photos/<资产模块>/<文件名>  单照片字段引用的装备/物资/车辆照片;不包含 trash
 *   config/<文件名>          系统配置文件(自动备份设置、启动配置)
 *
 * 数据表读取使用同一 SQLite 事务快照;照片文件在该事务内读取,但文件系统不具备数据库式快照。
 * 备份与恢复均可按模块选择;恢复请求使用单事务与按模块照片 stage/rollback。
 * 字典模块的账号和单位授权成对恢复;缺少授权的旧包保留本地账号与授权。
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final File dir;
    private final File rootDir;
    private final File photosDir;
    private final File settingsFile;
    private static final String[] FITNESS_SETTINGS = {"fitness_grade_rule", "bmi_standard"};
    private static final List<String> PHOTO_MODULES = Arrays.asList("personnel", "equipment", "material", "vehicle");
    private static final List<String> CONFIG_FILES = Arrays.asList("backup-settings.json", "config.json");
    private static final String PERSONNEL_TRASH_PREFIX = "photos/personnel-trash/";
    // 校验限额常量与ZIP安全校验工具已拆至 BackupArchiveGuard(static导入复用)
    public static final long MAX_UPLOAD_BYTES = 100L * 1024 * 1024;
    // 固定升级前的完整12项,不可按数量或未来的动态模块清单推断用户全选。
    private static final Set<String> PRE_DOUBLE_EXCELLENCE_MODULES = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList("personnel", "department", "dict", "dashboard", "equipment",
                    "material", "item-loans", "vehicle", "leave", "fitness", "phone", "military")));
    // 固定年度考评加入前的完整13项(双争已含);历史默认清单同样静态固定,不从动态清单推导。
    private static final Set<String> PRE_ANNUAL_APPRAISAL_MODULES = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList("personnel", "department", "dict", "dashboard", "equipment",
                    "material", "item-loans", "vehicle", "leave", "fitness", "phone", "military", "double-excellence")));

    /** 模块 → 数据表(模块 key 与网页端菜单一致) */
    public static final Map<String, String[]> MODULE_TABLES = new LinkedHashMap<String, String[]>();

    static {
        MODULE_TABLES.put("personnel", new String[]{"personnel", "position_history", "reward_punish", "child_info", "family_member", "driver_license", "personal_card", "private_vehicle", "housing_info", "delete_undo"});
        MODULE_TABLES.put("department", new String[]{"department"});
        MODULE_TABLES.put("dict", new String[]{"sys_dict", "sys_setting", "holiday_calendar", "admin_user", "op_log"});
        MODULE_TABLES.put("dashboard", new String[]{"dash_setting"});
        MODULE_TABLES.put("equipment", new String[]{"equipment"});
        MODULE_TABLES.put("material", new String[]{"material"});
        MODULE_TABLES.put("item-loans", new String[]{"item_loan"});
        MODULE_TABLES.put("vehicle", new String[]{"vehicle", "vehicle_fault_history"});
        MODULE_TABLES.put("leave", new String[]{"leave_record", "leave_plan"});
        MODULE_TABLES.put("fitness", new String[]{"fitness_record", "fitness_matrix"});
        MODULE_TABLES.put("phone", new String[]{"phone_device"});
        MODULE_TABLES.put("military", new String[]{"military_assessment", "mil_scheme", "mil_scheme_item"});
        MODULE_TABLES.put("double-excellence", new String[]{"double_excellence_record"});
        MODULE_TABLES.put("annual-appraisal", new String[]{"annual_appraisal_record"});
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper = new ObjectMapper();

    public BackupService(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this(jdbc, txManager, new File("data"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BackupService(JdbcTemplate jdbc, PlatformTransactionManager txManager,
                         @org.springframework.beans.factory.annotation.Value("${app.data-dir:data}") String dataDir) {
        this(jdbc, txManager, new File(dataDir));
    }

    /** 隔离测试可显式指定临时根目录,不触及业务 data。 */
    public BackupService(JdbcTemplate jdbc, PlatformTransactionManager txManager, File root) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.rootDir = root;
        this.dir = new File(root, "backup");
        this.photosDir = new File(root, "photos");
        this.settingsFile = new File(root, "backup-settings.json");
    }

    private List<String> tablesFor(String module) {
        List<String> tables = new ArrayList<String>(Arrays.asList(MODULE_TABLES.get(module)));
        // 账号与授权绑定必须作为一个兼容单元备份;旧库可能尚无授权表。
        if ("dict".equals(module) && tableExists("account_department")) {
            tables.add("account_department");
        }
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
        private final Map<String, Object> extraSettings = new LinkedHashMap<String, Object>();

        // 兼容已保存的location等扩展项;调度器后续保存运行时间也不能丢失这些值。
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void setExtraSetting(String key, Object value) { extraSettings.put(key, value); }

        @com.fasterxml.jackson.annotation.JsonAnyGetter
        public Map<String, Object> getExtraSettings() { return extraSettings; }
    }

    public static List<String> allModuleKeys() {
        return new ArrayList<String>(MODULE_TABLES.keySet());
    }

    public synchronized Settings loadSettings() {
        if (!settingsFile.exists()) { return new Settings(); }
        Settings settings;
        com.fasterxml.jackson.databind.JsonNode document;
        try {
            document = mapper.readTree(settingsFile);
            settings = mapper.treeToValue(document, Settings.class);
        } catch (Exception e) {
            log.warn("备份设置读取失败,使用默认: {}", e.getMessage());
            return new Settings();
        }
        if (settings == null) { return new Settings(); }
        if (settings.modules == null) { settings.modules = allModuleKeys(); }
        // 旧版本默认清单升级(双争/年度考评加入前保存的设置):精确匹配已知历史清单后补齐到当前全部模块,
        // 首次读取即持久化,包括自动备份关闭/尚未到期时;只改原JSON的modules。
        // 不调用saveSettings,避免改写其它设置值或补齐原文件未声明的默认字段;用户显式缩小过的清单不受影响。
        Set<String> selected = new LinkedHashSet<String>(settings.modules);
        if (selected.size() < allModuleKeys().size()
                && (PRE_DOUBLE_EXCELLENCE_MODULES.equals(selected) || PRE_ANNUAL_APPRAISAL_MODULES.equals(selected))) {
            settings.modules = new ArrayList<String>(allModuleKeys());
            ((com.fasterxml.jackson.databind.node.ObjectNode) document).set("modules", mapper.valueToTree(settings.modules));
            writeSettings(document);
        }
        return settings;
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
        writeSettings(s);
    }

    private void writeSettings(Object settings) {
        try {
            File parent = settingsFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile(parent.toPath(), "settings-", ".tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), settings);
                java.nio.file.Files.move(tmp, settingsFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally { java.nio.file.Files.deleteIfExists(tmp); }
        } catch (Exception e) {
            throw new RuntimeException("备份设置保存失败: " + e.getMessage(), e);
        }
    }

    /* ================= 备份 ================= */

    /** 备份文件超过上传恢复上限(100MB)时生成提示文本;未超限返回 null。上传入口会拒绝超限文件,本机与文件迁移恢复不受影响。 */
    static String oversizedWarning(long size) {
        if (size <= MAX_UPLOAD_BYTES) {
            return null;
        }
        return "该备份 " + (size / 1024 / 1024) + "MB 已超过上传恢复上限(" + (MAX_UPLOAD_BYTES / 1024 / 1024)
                + "MB):本机与文件迁移恢复可用,上传入口将拒绝该文件,请改用文件拷贝迁移";
    }

    public synchronized Map<String, Object> createBackup(List<String> modules, boolean auto) {
        List<String> selected = checkModules(modules);
        // JdbcTemplate shares the transaction-bound connection: all tables use one SQLite read snapshot.
        return tx.execute(status -> createBackupSnapshot(selected, auto));
    }

    private Map<String, Object> createBackupSnapshot(List<String> selected, boolean auto) {
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
            Map<String, java.util.Set<String>> assetPhotos = new LinkedHashMap<String, java.util.Set<String>>();
            for (String module : selected) {
                Map<String, byte[]> tablesData = new LinkedHashMap<String, byte[]>();
                Map<String, Object> tables = new LinkedHashMap<String, Object>();
                for (String table : tablesFor(module)) {
                    List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + table);
                    if ("item_loan".equals(table)) {
                        rows = convertItemLoans(rows, null);
                    }
                    if (PHOTO_MODULES.contains(module) && !"personnel".equals(module) && module.equals(table)) {
                        assetPhotos.put(module, photoNames(rows));
                    }
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

            // 系统配置文件随“数据字典/系统数据”模块备份,用于完整灾备。
            Map<String, byte[]> configData = new LinkedHashMap<String, byte[]>();
            List<String> configFiles = new ArrayList<String>();
            if (selected.contains("dict")) {
                for (String name : CONFIG_FILES) {
                    File config = new File(rootDir, name);
                    if (!config.isFile()) { continue; }
                    rejectSymlink(config.toPath());
                    if (config.length() > MAX_CONFIG_BYTES) {
                        throw new IllegalArgumentException("系统配置文件过大: " + name);
                    }
                    configData.put(name, Files.readAllBytes(config.toPath()));
                    configFiles.add(name);
                }
            }

            Map<String, Object> meta = new LinkedHashMap<String, Object>();
            meta.put("version", 4);
            List<String> photoModules = new ArrayList<String>(selected);
            photoModules.retainAll(PHOTO_MODULES);
            meta.put("photoModules", photoModules);
            meta.put("photoSnapshot", selected.contains("personnel"));
            meta.put("personnelTrash", selected.contains("personnel"));
            meta.put("configFiles", configFiles);
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
            for (Map.Entry<String, byte[]> config : configData.entrySet()) {
                writeEntry(zip, "config/" + config.getKey(), config.getValue());
            }

            int photoCount = 0;
            for (String module : photoModules) {
                File root = photoDirectory(module);
                rejectSymlink(root.toPath());
                if ("personnel".equals(module)) {
                    File[] photos = root.listFiles();
                    if (photos == null && root.exists()) { throw new IllegalStateException("无法读取照片目录: " + root); }
                    if (photos != null) {
                        for (File photo : photos) {
                            rejectSymlink(photo.toPath());
                            if (photo.isFile()) { writePhoto(zip, module, photo); photoCount++; }
                        }
                    }
                    // 撤回记录引用的照片位于 photos/trash,单独命名空间保存。
                    for (String name : referencedTrashPhotos()) {
                        File photo = new File(new File(photosDir, "trash"), name);
                        if (!photo.isFile()) { throw new IllegalStateException("撤回照片缺失: " + name); }
                        writePhotoEntry(zip, PERSONNEL_TRASH_PREFIX + name, photo);
                        photoCount++;
                    }
                } else {
                    // Each asset stores a single filename; unrelated/orphan files and trash are never archived.
                    for (String name : assetPhotos.get(module)) {
                        File photo = new File(root, name);
                        if (!photo.isFile()) { throw new IllegalStateException("资产照片缺失: " + module + "/" + name); }
                        writePhoto(zip, module, photo); photoCount++;
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
            String warning = oversizedWarning(file.length());
            if (warning != null) {
                result.put("oversizedForUpload", Boolean.TRUE);
                result.put("warning", warning);
                log.warn("备份 {} 大小 {}MB 超过上传恢复上限,上传入口将拒绝该文件", file.getName(), file.length() / 1024 / 1024);
            }
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
            list.add(backupItem(f, readMeta(f)));
        }
        return list;
    }

    private Map<String, Object> backupItem(File file, Map<String, Object> meta) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("name", file.getName());
        item.put("size", file.length());
        item.put("createdText", meta != null && meta.get("createdText") instanceof String
                ? meta.get("createdText") : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file.lastModified())));
        item.put("modules", meta == null ? null : meta.get("modules"));
        item.put("auto", meta == null ? file.getName().startsWith("auto_") : Boolean.TRUE.equals(meta.get("auto")));
        if (meta != null) {
            item.put("version", meta.get("version"));
            item.put("photoModules", meta.get("photoModules"));
        }
        return item;
    }

    /** Upload validates archive contents only: no SQL, photo staging, config installation or restore. */
    public synchronized Map<String, Object> upload(MultipartFile file) {
        if (file == null || file.isEmpty()) { throw new IllegalArgumentException("请选择本系统ZIP备份文件,最多100MB"); }
        if (file.getSize() > MAX_UPLOAD_BYTES) { throw new IllegalArgumentException("备份上传文件最多100MB"); }
        java.nio.file.Path pending = null;
        try {
            rejectSymlink(dir.toPath());
            Files.createDirectories(dir.toPath());
            pending = Files.createTempFile(dir.toPath(), ".backup-upload-", ".tmp");
            try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(pending)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int n;
                // Do not trust MultipartFile.getSize(), Content-Length, the name or the MIME type.
                while ((n = in.read(buffer, 0, (int) Math.min(buffer.length, MAX_UPLOAD_BYTES - total + 1))) != -1) {
                    total += n;
                    if (total > MAX_UPLOAD_BYTES) { throw new IllegalArgumentException("备份上传文件最多100MB"); }
                    out.write(buffer, 0, n);
                }
                if (total == 0) { throw new IllegalArgumentException("备份文件为空"); }
            }
            Map<String, Object> meta = validateUpload(pending.toFile());
            // No REPLACE_EXISTING / ATOMIC_MOVE (the latter can overwrite on some providers).
            // A same-directory move publishes only a fully validated file; collisions never replace old backups.
            java.nio.file.Path target;
            do {
                target = dir.toPath().resolve("upload_" + java.util.UUID.randomUUID().toString() + ".zip");
                try { Files.move(pending, target); break; }
                catch (java.nio.file.FileAlreadyExistsException collision) { /* choose another server name */ }
            } while (true);
            pending = null;
            return backupItem(target.toFile(), meta);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("备份上传失败: ZIP无效、损坏或无法读写", e);
        } finally {
            if (pending != null) {
                try { Files.deleteIfExists(pending); }
                catch (java.io.IOException e) { throw new IllegalStateException("上传暂存文件清理失败", e); }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validateUpload(File file) throws java.io.IOException {
        try (ZipFile zip = new ZipFile(file)) {
            Map<String, ZipEntry> entries = new LinkedHashMap<String, ZipEntry>();
            java.util.Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                String path = entry.getName();
                checkZipPath(path);
                if (entries.size() >= MAX_ZIP_ENTRIES) { throw new IllegalArgumentException("ZIP条目最多10000个"); }
                if (entries.put(path, entry) != null) { throw new IllegalArgumentException("重复ZIP条目: " + path); }
            }
            long[] expanded = {0};
            Map<String, Object> meta = readArchiveMeta(zip, expanded);
            Object versionValue = meta.get("version");
            if (!(versionValue instanceof Number) || ((Number) versionValue).doubleValue() < 1
                    || ((Number) versionValue).doubleValue() > 4
                    || ((Number) versionValue).doubleValue() != ((Number) versionValue).intValue()) {
                throw new IllegalArgumentException("缺少版本或不支持的备份版本(支持v1-4)");
            }
            int version = ((Number) versionValue).intValue();
            if (!(meta.get("modules") instanceof Map) || ((Map<?, ?>) meta.get("modules")).isEmpty()) {
                throw new IllegalArgumentException("备份缺少有效已知模块");
            }
            Map<String, Object> modules = (Map<String, Object>) meta.get("modules");
            Set<String> allowed = new LinkedHashSet<String>();
            Set<String> directories = new LinkedHashSet<String>(Arrays.asList("data/", "photos/", "config/"));
            Map<String, Number> counts = new LinkedHashMap<String, Number>();
            allowed.add("meta.json");
            for (Map.Entry<String, Object> module : modules.entrySet()) {
                String key = module.getKey();
                if (!MODULE_TABLES.containsKey(key) || !(module.getValue() instanceof Map)) {
                    throw new IllegalArgumentException("非法备份模块: " + key);
                }
                Map<String, Object> declared = (Map<String, Object>) module.getValue();
                List<String> tables = new ArrayList<String>(Arrays.asList(MODULE_TABLES.get(key)));
                List<String> required = new ArrayList<String>(tables);
                if ("dict".equals(key)) {
                    tables.add("account_department");
                    required.removeAll(Arrays.asList("admin_user", "op_log"));
                    if (declared.containsKey("account_department") && !declared.containsKey("admin_user")) {
                        throw new IllegalArgumentException("账号授权表缺少配对账号表");
                    }
                }
                if ("personnel".equals(key) && !(version >= 4 && Boolean.TRUE.equals(meta.get("personnelTrash")))) {
                    required.remove("delete_undo");
                }
                if ("vehicle".equals(key)) { required.remove("vehicle_fault_history"); }
                // v1/v2 predate leave plans and military scheme tables; still verify any such declared files.
                if (version <= 2 && "leave".equals(key)) { required.remove("leave_plan"); }
                if (version <= 2 && "military".equals(key)) { required.removeAll(Arrays.asList("mil_scheme", "mil_scheme_item")); }
                if ("fitness".equals(key)) {
                    tables.addAll(Arrays.asList("sys_setting", "fitness_standard"));
                    if (version >= 2) { required.add("sys_setting"); }
                }
                if (!declared.keySet().containsAll(required)) { throw new IllegalArgumentException("模块缺少必需数据表声明: " + key); }
                for (Map.Entry<String, Object> table : declared.entrySet()) {
                    Object count = table.getValue();
                    if (!tables.contains(table.getKey()) || !(count instanceof Number)
                            || ((Number) count).longValue() < 0 || ((Number) count).longValue() > Integer.MAX_VALUE
                            || ((Number) count).doubleValue() != ((Number) count).longValue()) {
                        throw new IllegalArgumentException("非法表声明: " + key + "/" + table.getKey());
                    }
                    String path = "data/" + key + "/" + table.getKey() + ".json";
                    requireEntry(entries, path);
                    allowed.add(path);
                    counts.put(path, (Number) count);
                }
                directories.add("data/" + key + "/");
            }
            for (String flag : Arrays.asList("auto", "photoSnapshot", "personnelTrash")) {
                if (meta.get(flag) != null && !(meta.get(flag) instanceof Boolean)) { throw new IllegalArgumentException("非法元数据: " + flag); }
            }
            List<String> snapshots = new ArrayList<String>();
            if (version >= 3 || meta.get("photoModules") != null) {
                if (!(meta.get("photoModules") instanceof List)) { throw new IllegalArgumentException("缺少照片模块清单"); }
                for (Object module : (List<?>) meta.get("photoModules")) {
                    if (!(module instanceof String) || !PHOTO_MODULES.contains(module) || !modules.containsKey(module)
                            || snapshots.contains(module)) { throw new IllegalArgumentException("非法照片模块清单"); }
                    snapshots.add((String) module);
                }
            }
            Set<String> configNames = new LinkedHashSet<String>();
            if (meta.get("configFiles") != null) {
                if (!(meta.get("configFiles") instanceof List)) { throw new IllegalArgumentException("非法系统配置文件清单"); }
                List<?> declaredConfigs = (List<?>) meta.get("configFiles");
                if (!declaredConfigs.isEmpty() && !modules.containsKey("dict")) { throw new IllegalArgumentException("非法系统配置文件清单"); }
                for (Object name : declaredConfigs) {
                    if (!(name instanceof String) || !CONFIG_FILES.contains(name) || !configNames.add((String) name)) {
                        throw new IllegalArgumentException("非法系统配置文件清单");
                    }
                    requireEntry(entries, "config/" + name);
                    allowed.add("config/" + name);
                }
            }
            boolean personnelTrash = version >= 4 && Boolean.TRUE.equals(meta.get("personnelTrash")) && modules.containsKey("personnel");
            if (Boolean.TRUE.equals(meta.get("personnelTrash")) && !personnelTrash) { throw new IllegalArgumentException("非法人员撤回照片声明"); }
            for (String module : PHOTO_MODULES) {
                if (modules.containsKey(module) && (!"personnel".equals(module))) { directories.add("photos/" + module + "/"); }
            }
            if (personnelTrash) { directories.add(PERSONNEL_TRASH_PREFIX); }
            for (ZipEntry entry : entries.values()) {
                String path = entry.getName();
                long limit;
                if (entry.isDirectory()) {
                    if (!directories.contains(path)) { throw new IllegalArgumentException("非法ZIP目录: " + path); }
                    limit = 0;
                } else if (allowed.contains(path)) {
                    limit = path.equals("meta.json") ? MAX_META_BYTES : path.startsWith("config/") ? MAX_CONFIG_BYTES : MAX_DATA_BYTES;
                } else if (path.startsWith("photos/")) {
                    if (path.startsWith(PERSONNEL_TRASH_PREFIX)) {
                        safePhotoName(path.substring(PERSONNEL_TRASH_PREFIX.length()));
                        if (!personnelTrash) { throw new IllegalArgumentException("撤回照片不在声明范围内"); }
                    } else {
                        String module = photoModule(path);
                        if (!modules.containsKey(module) || (version >= 3 ? !snapshots.contains(module) : !"personnel".equals(module))) {
                            throw new IllegalArgumentException("照片不在声明范围内");
                        }
                    }
                    limit = MAX_PHOTO_BYTES;
                } else { throw new IllegalArgumentException("不允许的ZIP条目: " + path); }
                if ("meta.json".equals(path)) { continue; } // Already read and CRC-checked, counted once.
                try (InputStream in = checkedEntry(zip, entry, limit, expanded)) {
                    if (counts.containsKey(path)) { validateTableJson(in, path, counts.get(path).intValue()); }
                    else if (path.startsWith("config/") && !entry.isDirectory()) {
                        validateConfigFile(path.substring("config/".length()), readLimited(in, MAX_CONFIG_BYTES));
                    } else {
                        byte[] buffer = new byte[8192];
                        while (in.read(buffer) != -1) { /* bounded drain verifies CRC, including unselected photos */ }
                    }
                }
            }
            return meta;
        }
    }

    private void validateTableJson(InputStream in, String path, int expected) throws java.io.IOException {
        try (JsonParser parser = mapper.getFactory().createParser(in).disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) { throw new IllegalArgumentException("表文件必须是数组: " + path); }
            int rows = 0;
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                if (token != JsonToken.START_OBJECT) { throw new IllegalArgumentException("非法数据行: " + path); }
                int depth = 1;
                boolean hasField = false;
                while (depth > 0) {
                    token = parser.nextToken();
                    if (token == null) { throw new IllegalArgumentException("不完整的表JSON: " + path); }
                    if (token == JsonToken.FIELD_NAME && depth == 1) { hasField = true; }
                    if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                        if (++depth > 100) { throw new IllegalArgumentException("JSON嵌套过深"); }
                    } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) { depth--; }
                }
                if (!hasField) { throw new IllegalArgumentException("非法空数据行: " + path); }
                if (++rows > expected) { throw new IllegalArgumentException("表行数与元数据不一致: " + path); }
            }
            if (rows != expected || parser.nextToken() != null) { throw new IllegalArgumentException("表行数或JSON格式与元数据不一致: " + path); }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readArchiveMeta(ZipFile zip, long[] expanded) throws java.io.IOException {
        ZipEntry entry = zip.getEntry("meta.json");
        if (entry == null || entry.isDirectory()) { throw new IllegalArgumentException("备份缺少meta.json"); }
        try (InputStream in = checkedEntry(zip, entry, MAX_META_BYTES, expanded);
             JsonParser parser = mapper.getFactory().createParser(readLimited(in, MAX_META_BYTES))
                     .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)) {
            Map<String, Object> meta = mapper.readerFor(Map.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(parser);
            if (meta == null) { throw new IllegalArgumentException("备份缺少有效元数据"); }
            return meta;
        }
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
        if (version < 1 || version > 4) { throw new IllegalArgumentException("不支持的备份版本: " + version); }
        Map<String, List<Map<String, Object>>> data = new LinkedHashMap<String, List<Map<String, Object>>>();
        List<String> warnings = new ArrayList<String>();
        List<Map<String, Object>> restored = new ArrayList<Map<String, Object>>();
        Map<String, byte[]> configData = new LinkedHashMap<String, byte[]>();
        final List<PhotoSwap> photoSwaps = new ArrayList<PhotoSwap>();
        boolean clearPersonnelUndo = false;
        boolean committed = false;
        int photoCount = 0;
        Map<String, Object> safety;
        try (ZipFile zip = new ZipFile(f)) {
            java.util.Set<String> entries = new java.util.HashSet<String>();
            java.util.Enumeration<? extends ZipEntry> all = zip.entries();
            List<ZipEntry> photos = new ArrayList<ZipEntry>();
            List<ZipEntry> trashPhotos = new ArrayList<ZipEntry>();
            while (all.hasMoreElements()) {
                ZipEntry e = all.nextElement(); String path = e.getName();
                if (!entries.add(path) || path.contains("\\") || path.startsWith("/") || path.contains(":")
                        || Arrays.asList(path.split("/")).contains("..")) { throw new IllegalArgumentException("危险或重复路径: " + path); }
                if (path.startsWith(PERSONNEL_TRASH_PREFIX) && !e.isDirectory()) {
                    safePhotoName(path.substring(PERSONNEL_TRASH_PREFIX.length()));
                    trashPhotos.add(e);
                } else if (path.startsWith("photos/") && !e.isDirectory()) {
                    photoModule(path); // Accept only direct personnel files or one explicit asset namespace.
                    photos.add(e);
                }
            }
            if (selected.contains("dict")) {
                Object declaredConfig = meta.get("configFiles");
                if (declaredConfig instanceof List) {
                    Set<String> seenConfig = new java.util.HashSet<String>();
                    for (Object item : (List<?>) declaredConfig) {
                        if (!(item instanceof String) || !CONFIG_FILES.contains(item) || !seenConfig.add((String) item)) {
                            throw new IllegalArgumentException("非法系统配置文件清单");
                        }
                        String configName = (String) item;
                        ZipEntry entry = zip.getEntry("config/" + configName);
                        if (entry == null || entry.isDirectory()) {
                            throw new IllegalArgumentException("缺少系统配置文件: " + configName);
                        }
                        if (entry.getSize() > MAX_CONFIG_BYTES) {
                            throw new IllegalArgumentException("系统配置文件过大: " + configName);
                        }
                        byte[] bytes;
                        try (InputStream in = zip.getInputStream(entry)) { bytes = readLimited(in, MAX_CONFIG_BYTES); }
                        validateConfigFile(configName, bytes);
                        configData.put(configName, bytes);
                    }
                } else if (declaredConfig != null) {
                    throw new IllegalArgumentException("非法系统配置文件清单");
                } else {
                    warnings.add("旧备份未包含系统配置文件,保留当前系统配置");
                }
            }
            for (String module : selected) {
                if (!(available.get(module) instanceof Map)) { throw new IllegalArgumentException("所选模块不在备份中: " + module); }
                Map<String, Object> declared = (Map<String, Object>) available.get(module);
                List<String> tables = new ArrayList<String>(Arrays.asList(MODULE_TABLES.get(module)));
                if ("dict".equals(module)) {
                    // 兼容旧包缺少后来加入的数据表;账号与授权必须成对恢复。
                    for (String optional : Arrays.asList("admin_user", "op_log")) {
                        if (!declared.containsKey(optional)) { tables.remove(optional); }
                    }
                    if (declared.containsKey("account_department")) {
                        if (!declared.containsKey("admin_user")) { throw new IllegalArgumentException("账号授权表缺少配对账号表"); }
                        tables.add("account_department");
                    } else if (declared.containsKey("admin_user")) {
                        tables.remove("admin_user");
                        warnings.add("旧备份未包含账号单位授权表,为保护现有账号和授权,跳过账号恢复");
                    }
                }
                if ("personnel".equals(module)) {
                    boolean fullUndoSnapshot = version >= 4 && Boolean.TRUE.equals(meta.get("personnelTrash"))
                            && declared.containsKey("delete_undo");
                    if (!fullUndoSnapshot) {
                        tables.remove("delete_undo");
                        clearPersonnelUndo = true;
                        warnings.add("备份未包含完整的人员撤回记录或撤回照片,已清空本地撤回缓存");
                    }
                }
                if ("vehicle".equals(module) && !declared.containsKey("vehicle_fault_history")) {
                    tables.remove("vehicle_fault_history");
                    warnings.add("旧车辆备份未包含历史故障表,本次仅恢复车辆台账");
                }
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
                    // Historical loan columns are completed only after every selected dependency has been read.
                    if (!"item_loan".equals(table)) { validateRows(table, rows); }
                    if ("fitness".equals(module) && "sys_setting".equals(table)) {
                        for (Map<String, Object> row : rows) {
                            if (!Arrays.asList(FITNESS_SETTINGS).contains(row.get("skey"))) { throw new IllegalArgumentException("体能标准含非体能设置键"); }
                        }
                        data.put("fitness_settings", rows);
                    } else { data.put(table, rows); }
                    counts.put(table, rows.size());
                }
                if ("dict".equals(module) && data.containsKey("admin_user")) {
                    try { validateAccounts(data.get("admin_user")); }
                    catch (IllegalArgumentException unsafe) {
                        if (version >= 3) { throw unsafe; }
                        data.remove("admin_user"); data.remove("account_department");
                        counts.remove("admin_user"); counts.remove("account_department");
                        warnings.add("旧备份账号层级或状态不完整,保留本地账号及授权: " + unsafe.getMessage());
                    }
                }
                Map<String, Object> r = new LinkedHashMap<String, Object>();
                r.put("module", module); r.put("tables", counts); restored.add(r);
            }
            // Both dict and fitness carry these keys; restore the scoped fitness copy exactly once.
            if (data.containsKey("sys_setting") && data.containsKey("fitness_settings")) {
                data.get("sys_setting").removeIf(row -> Arrays.asList(FITNESS_SETTINGS).contains(row.get("skey")));
            }
            final List<Map<String, Object>> originalLoans = data.get("item_loan");
            prepareItemLoans(data, originalLoans);
            validateDependencies(data);
            List<String> snapshots = new ArrayList<String>();
            if (!trashPhotos.isEmpty() && (version < 4 || !Boolean.TRUE.equals(meta.get("personnelTrash"))
                    || !available.containsKey("personnel"))) {
                throw new IllegalArgumentException("撤回照片不在声明范围内");
            }
            if (version >= 3) {
                if (!(meta.get("photoModules") instanceof List)) { throw new IllegalArgumentException("缺少照片模块清单"); }
                for (Object module : (List<?>) meta.get("photoModules")) {
                    if (!(module instanceof String) || !PHOTO_MODULES.contains(module) || !available.containsKey(module)
                            || snapshots.contains(module)) { throw new IllegalArgumentException("非法照片模块清单"); }
                    snapshots.add((String) module);
                }
                for (ZipEntry photo : photos) {
                    if (!snapshots.contains(photoModule(photo.getName()))) { throw new IllegalArgumentException("照片不在声明范围内"); }
                }
            } else {
                if (Boolean.TRUE.equals(meta.get("photoSnapshot")) || photos.stream().anyMatch(e -> "personnel".equals(photoModule(e.getName())))) {
                    snapshots.add("personnel");
                }
                for (String module : selected) {
                    if (PHOTO_MODULES.contains(module) && !"personnel".equals(module)) {
                        warnings.add("旧备份未包含" + module + "照片快照,保留本地该模块照片");
                    }
                }
            }
            snapshots.retainAll(selected);
            for (String module : snapshots) {
                PhotoSwap swap = new PhotoSwap(photoDirectory(module));
                photoSwaps.add(swap);
                java.util.Set<String> stagedNames = new java.util.HashSet<String>();
                for (ZipEntry e : photos) {
                    if (!module.equals(photoModule(e.getName()))) { continue; }
                    String filename = e.getName().substring(e.getName().lastIndexOf('/') + 1);
                    if (!stagedNames.add(filename)) { throw new IllegalArgumentException("重复照片: " + filename); }
                    if (e.getSize() > MAX_PHOTO_BYTES) { throw new IllegalArgumentException("单张图片最多 100MB"); }
                    try (InputStream in = zip.getInputStream(e);
                         OutputStream out = java.nio.file.Files.newOutputStream(swap.stage.resolve(filename))) {
                        copyPhoto(in, out);
                    }
                    photoCount++;
                }
                if (!"personnel".equals(module) && !stagedNames.equals(photoNames(data.get(module)))) {
                    throw new IllegalArgumentException("资产照片与数据引用不一致: " + module);
                }
            }
            if (selected.contains("personnel") && version >= 4 && Boolean.TRUE.equals(meta.get("personnelTrash"))) {
                if (!data.containsKey("delete_undo")) { throw new IllegalArgumentException("缺少人员删除撤回表"); }
                PhotoSwap trashSwap = new PhotoSwap(new File(photosDir, "trash"));
                photoSwaps.add(trashSwap);
                Set<String> stagedTrash = new LinkedHashSet<String>();
                for (ZipEntry e : trashPhotos) {
                    String filename = e.getName().substring(PERSONNEL_TRASH_PREFIX.length());
                    if (!stagedTrash.add(filename)) { throw new IllegalArgumentException("重复撤回照片: " + filename); }
                    if (e.getSize() > MAX_PHOTO_BYTES) { throw new IllegalArgumentException("单张图片最多 100MB"); }
                    try (InputStream in = zip.getInputStream(e);
                         OutputStream out = Files.newOutputStream(trashSwap.stage.resolve(filename))) {
                        copyPhoto(in, out);
                    }
                    photoCount++;
                }
                validateUndoTrashRefs(data.get("delete_undo"), stagedTrash);
            }
            safety = createBackup(allModuleKeys(), false);
            final boolean clearUndoOnRestore = clearPersonnelUndo;
            try {
                tx.executeWithoutResult(status -> {
                    jdbc.execute("PRAGMA defer_foreign_keys=ON");
                    // Reserve SQLite's writer before rechecking unselected/local dependency rows.
                    jdbc.update("UPDATE sys_setting SET svalue=svalue WHERE 1=0");
                    // Reconvert the original legacy rows against the transaction's effective post-restore sets.
                    prepareItemLoans(data, originalLoans);
                    validateDependencies(data);
                    if (clearUndoOnRestore && tableExists("delete_undo")) {
                        jdbc.update("DELETE FROM delete_undo");
                    }
                    for (String table : data.keySet()) {
                        if ("fitness_settings".equals(table)) { jdbc.update("DELETE FROM sys_setting WHERE skey IN (?,?)", (Object[]) FITNESS_SETTINGS); }
                        else { jdbc.update("DELETE FROM " + table); }
                    }
                    for (Map.Entry<String, List<Map<String, Object>>> e : data.entrySet()) {
                        String table = "fitness_settings".equals(e.getKey()) ? "sys_setting" : e.getKey();
                        for (Map<String, Object> row : e.getValue()) { insertRow(table, row); }
                    }
                    if (!jdbc.queryForList("PRAGMA foreign_key_check").isEmpty()) { throw new IllegalArgumentException("恢复外键冲突"); }
                    for (PhotoSwap swap : photoSwaps) { swap.install(); }
                });
                committed = true;
            } catch (RuntimeException | Error e) {
                for (int i = photoSwaps.size() - 1; i >= 0; i--) { photoSwaps.get(i).rollback(e); }
                throw e;
            }
        } catch (RuntimeException e) {
            // Preserve transaction failures and their suppressed compensation errors for callers.
            throw e;
        } catch (Exception e) { throw new RuntimeException("恢复失败: " + e.getMessage(), e);
        } finally {
            for (PhotoSwap swap : photoSwaps) {
                deleteTree(swap.stage);
                // Retain the original files for manual recovery if compensation itself failed.
                if (committed || swap.rollbackComplete) { deleteTree(swap.previous); }
            }
        }
        boolean configRestored = false;
        if (!configData.isEmpty()) {
            try {
                installConfigFiles(configData);
                configRestored = true;
                warnings.add("系统配置文件已恢复" + (configData.containsKey("config.json")
                        ? ";启动配置中的端口等变更将在下次重启后生效" : ""));
            } catch (Exception e) {
                warnings.add("系统配置文件恢复失败,已保留原配置: " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("restored", restored); result.put("photos", photoCount);
        result.put("configFiles", new ArrayList<String>(configData.keySet()));
        result.put("requiresRestart", configRestored && configData.containsKey("config.json"));
        result.put("beforeRestoreBackup", safety.get("name")); result.put("warnings", warnings);
        // Controller/session integration must revoke only after this method returns successfully.
        result.put("securityStateRestored", data.containsKey("admin_user") || data.containsKey("account_department") || data.containsKey("department"));
        return result;
    }

    @SuppressWarnings("unchecked")
    private void validateConfigFile(String name, byte[] bytes) throws java.io.IOException {
        if ("backup-settings.json".equals(name)) {
            Settings settings = mapper.readerFor(Settings.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
            if (settings == null || settings.intervalHours < 1 || settings.keepCount < 1 || settings.keepCount > 200
                    || settings.autoLastRun < 0) {
                throw new IllegalArgumentException("自动备份设置内容不合法");
            }
            if (settings.modules != null) {
                for (String module : settings.modules) {
                    if (!MODULE_TABLES.containsKey(module)) { throw new IllegalArgumentException("自动备份设置含未知模块"); }
                }
            }
            if (settings.enabled && (settings.modules == null || settings.modules.isEmpty())) {
                throw new IllegalArgumentException("启用自动备份但未选择模块");
            }
            return;
        }
        if ("config.json".equals(name)) {
            Map<String, Object> config = mapper.readerFor(Map.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
            if (config == null) { throw new IllegalArgumentException("启动配置内容不合法"); }
            for (String key : config.keySet()) {
                if (!Arrays.asList("port", "setupDone", "adminUser").contains(key)) {
                    throw new IllegalArgumentException("启动配置含未知字段: " + key);
                }
            }
            Object port = config.get("port");
            if (port != null && (!(port instanceof Number)
                    || ((Number) port).intValue() < 1 || ((Number) port).intValue() > 65535)) {
                throw new IllegalArgumentException("启动配置端口不合法");
            }
            Object setup = config.get("setupDone");
            if (setup != null && !(setup instanceof Boolean)) { throw new IllegalArgumentException("启动配置状态不合法"); }
            Object admin = config.get("adminUser");
            if (admin != null && (!(admin instanceof String)
                    || ((String) admin).trim().isEmpty() || ((String) admin).trim().length() > 64)) {
                throw new IllegalArgumentException("启动配置管理员账号不合法");
            }
            return;
        }
        throw new IllegalArgumentException("未知系统配置文件: " + name);
    }

    /** 数据库提交成功后原子替换配置文件；失败时恢复原文件。 */
    private void installConfigFiles(Map<String, byte[]> files) throws Exception {
        Files.createDirectories(rootDir.toPath());
        Map<File, File> previous = new LinkedHashMap<File, File>();
        List<File> temps = new ArrayList<File>();
        List<File> targets = new ArrayList<File>();
        try {
            long unique = System.nanoTime();
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                File target = new File(rootDir, entry.getKey());
                if (target.exists()) { rejectSymlink(target.toPath()); }
                java.nio.file.Path temp = Files.createTempFile(rootDir.toPath(), ".config-restore-", ".tmp");
                Files.write(temp, entry.getValue());
                temps.add(temp.toFile());
                targets.add(target);
            }
            int index = 0;
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                File target = targets.get(index);
                if (target.exists()) {
                    File old = new File(rootDir, ".config-old-" + entry.getKey() + "-" + unique + "-" + index);
                    Files.move(target.toPath(), old.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    previous.put(target, old);
                }
                Files.move(temps.get(index).toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                index++;
            }
            for (File old : previous.values()) {
                try { Files.deleteIfExists(old.toPath()); }
                catch (Exception e) { log.warn("旧配置文件清理失败: {}", old); }
            }
        } catch (Exception e) {
            for (File target : targets) {
                try { Files.deleteIfExists(target.toPath()); } catch (Exception ignore) { /* 继续恢复旧文件 */ }
            }
            for (Map.Entry<File, File> entry : previous.entrySet()) {
                try {
                    Files.move(entry.getValue().toPath(), entry.getKey().toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception rollback) { e.addSuppressed(rollback); }
            }
            throw e;
        } finally {
            for (File temp : temps) {
                try { Files.deleteIfExists(temp.toPath()); } catch (Exception ignore) { /* 临时文件清理失败不影响结果 */ }
            }
        }
    }

    private void validateUndoTrashRefs(List<Map<String, Object>> rows, Set<String> stagedTrash) {
        if (rows == null) { throw new IllegalArgumentException("缺少人员删除撤回表"); }
        for (Map<String, Object> row : rows) {
            Object value = row.get("photo_trash");
            if (value == null) { continue; }
            if (!(value instanceof String)) { throw new IllegalArgumentException("撤回照片引用不合法"); }
            String name = (String) value;
            if (name.isEmpty()) { continue; }
            safePhotoName(name);
            if (!stagedTrash.contains(name)) { throw new IllegalArgumentException("撤回照片缺失: " + name); }
        }
    }

    private void prepareItemLoans(Map<String, List<Map<String, Object>>> data, List<Map<String, Object>> original) {
        if (original == null) { return; }
        List<Map<String, Object>> converted = convertItemLoans(original, data);
        normalizeLoanOwners(converted, data);
        validateRows("item_loan", converted);
        data.put("item_loan", converted);
    }

    /** Preserve ownership only when the paired account snapshot proves who that ID names. */
    private void normalizeLoanOwners(List<Map<String, Object>> loans, Map<String, List<Map<String, Object>>> data) {
        List<Map<String, Object>> archivedAccounts = data == null ? null : data.get("admin_user");
        java.util.Set<String> restorableAccountIds = new java.util.HashSet<String>();
        if (archivedAccounts != null) {
            for (Map<String, Object> account : archivedAccounts) {
                Object id = account.get("id");
                if (id instanceof Number && ((Number) id).longValue() > 0) {
                    restorableAccountIds.add(String.valueOf(((Number) id).longValue()));
                }
            }
        }
        for (Map<String, Object> loan : loans) {
            Object owner = loan.get("owner_user_id");
            String ownerId = owner == null ? "" : String.valueOf(owner);
            if (ownerId.matches("[1-9][0-9]*") && restorableAccountIds.contains(ownerId)) {
                loan.put("owner_user_id", Long.valueOf(ownerId));
            } else {
                // Missing account mapping, malformed IDs, and owner 0 all remain admin-only.
                loan.put("owner_user_id", 0L);
            }
        }
    }

    private List<Map<String, Object>> convertItemLoans(List<Map<String, Object>> rows,
            Map<String, List<Map<String, Object>>> data) {
        List<Map<String, Object>> personnel = null, departments = null;
        List<Map<String, Object>> converted = new ArrayList<Map<String, Object>>();
        for (Object value : rows) {
            if (!(value instanceof Map) || ((Map<?, ?>) value).isEmpty()) {
                throw new IllegalArgumentException("非法数据行: item_loan");
            }
            @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) value;
            if ((!"1".equals(String.valueOf(row.get("borrower_migrated")))
                    || !row.containsKey("registration_department")) && personnel == null) {
                personnel = effectiveRows(data, "personnel");
                departments = effectiveRows(data, "department");
            }
            converted.add(ItemLoanSnapshots.convertLegacy(row,
                    personnel == null ? Collections.<Map<String, Object>>emptyList() : personnel,
                    departments == null ? Collections.<Map<String, Object>>emptyList() : departments));
        }
        return converted;
    }

    private List<Map<String, Object>> effectiveRows(Map<String, List<Map<String, Object>>> data, String table) {
        if (data != null && data.containsKey(table)) { return data.get(table); }
        return tableExists(table) ? jdbc.queryForList("SELECT * FROM " + table) : Collections.<Map<String, Object>>emptyList();
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
        refs.add(new String[]{"vehicle_fault_history", "vehicle_id", "vehicle"});
        refs.add(new String[]{"account_department", "user_id", "admin_user"});
        refs.add(new String[]{"account_department", "department_id", "department"});
        for (String table : Arrays.asList("leave_record", "fitness_record", "phone_device", "position_history", "reward_punish", "child_info", "family_member", "driver_license", "personal_card", "private_vehicle", "housing_info", "double_excellence_record", "annual_appraisal_record")) {
            refs.add(new String[]{table, "personnel_id", "personnel"});
        }
        for (String[] ref : refs) {
            if ("account_department".equals(ref[0]) && !tableExists("account_department")) { continue; }
            if (!data.containsKey(ref[0]) && !data.containsKey(ref[2])) { continue; }
            List<Map<String, Object>> sources = data.containsKey(ref[0]) ? data.get(ref[0]) : jdbc.queryForList("SELECT * FROM " + ref[0]);
            List<Map<String, Object>> targets = data.containsKey(ref[2]) ? data.get(ref[2]) : jdbc.queryForList("SELECT id FROM " + ref[2]);
            java.util.Set<Long> ids = new java.util.HashSet<Long>();
            for (Map<String, Object> row : targets) { if (row.get("id") instanceof Number) { ids.add(((Number) row.get("id")).longValue()); } }
            for (Map<String, Object> row : sources) {
                Object value = row.get(ref[1]);
                if (value == null || "0".equals(String.valueOf(value)) || "".equals(value)) {
                    if ("double_excellence_record".equals(ref[0]) || "annual_appraisal_record".equals(ref[0])) {
                        throw new IllegalArgumentException("非法依赖ID: " + ref[0] + "." + ref[1]);
                    }
                    continue;
                }
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

    private static void validateAccounts(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> accounts = new LinkedHashMap<Long, Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            if (!(row.get("id") instanceof Number) || !(row.get("parent_id") instanceof Number)
                    || !(row.get("enabled") instanceof Number) || row.get("username") == null
                    || row.get("password_hash") == null) {
                throw new IllegalArgumentException("账号快照缺少身份、层级或启用状态,拒绝不安全账号恢复");
            }
            long id = ((Number) row.get("id")).longValue();
            int enabled = ((Number) row.get("enabled")).intValue();
            if (id <= 0 || (enabled != 0 && enabled != 1) || accounts.put(id, row) != null) {
                throw new IllegalArgumentException("账号快照身份或状态无效");
            }
        }
        for (Long id : accounts.keySet()) {
            java.util.Set<Long> visited = new java.util.HashSet<Long>();
            long next = id;
            while (next != 0) {
                Map<String, Object> row = accounts.get(next);
                if (row == null || !visited.add(next)) { throw new IllegalArgumentException("账号快照含缺失祖先或层级环"); }
                next = ((Number) row.get("parent_id")).longValue();
            }
        }
    }

    private File photoDirectory(String module) {
        return "personnel".equals(module) ? photosDir : new File(photosDir, module);
    }

    /** 删除撤回记录引用的回收站照片；缺失时备份失败,避免生成无法完整还原的快照。 */
    private Set<String> referencedTrashPhotos() {
        Set<String> names = new LinkedHashSet<String>();
        if (!tableExists("delete_undo")) { return names; }
        for (Map<String, Object> row : jdbc.queryForList("SELECT photo_trash FROM delete_undo")) {
            Object value = row.get("photo_trash");
            if (value == null) { continue; }
            String name = String.valueOf(value);
            if (name.isEmpty()) { continue; }
            safePhotoName(name);
            names.add(name);
        }
        return names;
    }

    private static java.util.Set<String> photoNames(List<Map<String, Object>> rows) {
        java.util.Set<String> names = new java.util.LinkedHashSet<String>();
        for (Map<String, Object> row : rows) {
            Object value = row.get("photo");
            if (value == null || "".equals(value)) { continue; }
            if (!(value instanceof String)) { throw new IllegalArgumentException("每资产只允许一个照片文件名"); }
            String name = (String) value;
            safePhotoName(name);
            names.add(name);
        }
        return names;
    }

    private static String photoModule(String entry) {
        String relative = entry.substring("photos/".length());
        int slash = relative.indexOf('/');
        if (slash < 0) { safePhotoName(relative); return "personnel"; }
        String module = relative.substring(0, slash);
        if (!PHOTO_MODULES.contains(module) || "personnel".equals(module)) { throw new IllegalArgumentException("非法照片范围: " + entry); }
        safePhotoName(relative.substring(slash + 1));
        return module;
    }

    private void writePhoto(ZipOutputStream zip, String module, File photo) throws java.io.IOException {
        writePhotoEntry(zip, "photos/" + ("personnel".equals(module) ? "" : module + "/") + photo.getName(), photo);
    }

    private void writePhotoEntry(ZipOutputStream zip, String entryName, File photo) throws java.io.IOException {
        safePhotoName(photo.getName()); rejectSymlink(photo.toPath());
        if (photo.length() > MAX_PHOTO_BYTES) { throw new IllegalArgumentException("单张图片最多 100MB"); }
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = new FileInputStream(photo)) { copyPhoto(in, zip); }
        zip.closeEntry();
    }

    private static void copyPhoto(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buffer = new byte[8192]; long total = 0; int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > MAX_PHOTO_BYTES) { throw new IllegalArgumentException("单张图片最多 100MB"); }
            out.write(buffer, 0, n);
        }
    }

    /**
     * 受限目录下兼容移动:部分环境(麒麟安全策略/特殊挂载如U盘、网络盘)对 rename 返回
     * "不允许的操作",但普通读写是允许的(备份写入即成功)。此时把"移动"降级为"复制+删除"。
     */
    static void moveFileCompat(java.nio.file.Path src, java.nio.file.Path dst) throws java.io.IOException {
        try {
            java.nio.file.Files.move(src, dst);
            return;
        } catch (java.nio.file.FileSystemException e) {
            if (!moveFallbackAllowed(e)) { throw e; }
        }
        copyAndDelete(src, dst);
    }

    /** 仅对"操作被系统拒绝"类错误降级;目标冲突/源缺失等照常抛出,避免误覆盖。 */
    static boolean moveFallbackAllowed(java.nio.file.FileSystemException e) {
        if (e instanceof java.nio.file.AccessDeniedException) { return true; }
        String reason = e.getReason() == null ? "" : e.getReason();
        String low = reason.toLowerCase(java.util.Locale.ROOT);
        return low.contains("not permitted") || low.contains("denied") || reason.contains("不允许") || reason.contains("权限");
    }

    /** 复制后删除源;若删除也被拒绝,源文件留存于临时目录(不影响恢复正确性,照片仍可事后手动找回)。 */
    static void copyAndDelete(java.nio.file.Path src, java.nio.file.Path dst) throws java.io.IOException {
        java.nio.file.Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try {
            java.nio.file.Files.delete(src);
        } catch (java.io.IOException e) {
            log.warn("受限目录下已复制 {} 至 {},但源文件删除失败(留存于原临时目录)", src, dst);
        }
    }

    /** Only direct files are switched, so nested asset folders and trash survive personnel restore. */
    private static class PhotoSwap {
        final java.nio.file.Path root, stage, previous;
        final List<String> moved = new ArrayList<String>(), installed = new ArrayList<String>();
        boolean rollbackComplete = true;

        PhotoSwap(File rootFile) throws java.io.IOException {
            root = rootFile.toPath().toAbsolutePath();
            rejectSymlink(root);
            java.nio.file.Path parent = root.getParent();
            java.nio.file.Files.createDirectories(parent);
            stage = java.nio.file.Files.createTempDirectory(parent, "restore-photos-");
            java.nio.file.Path old;
            try { old = java.nio.file.Files.createTempDirectory(parent, "restore-old-photos-"); }
            catch (java.io.IOException e) { deleteTree(stage); throw e; }
            previous = old;
        }

        void install() {
            try {
                rejectSymlink(root);
                java.nio.file.Files.createDirectories(root);
                try (java.nio.file.DirectoryStream<java.nio.file.Path> files = java.nio.file.Files.newDirectoryStream(root)) {
                    for (java.nio.file.Path file : files) {
                        rejectSymlink(file);
                        if (java.nio.file.Files.isRegularFile(file)) {
                            String name = file.getFileName().toString();
                            moveFileCompat(file, previous.resolve(name)); moved.add(name);
                        }
                    }
                }
                try (java.nio.file.DirectoryStream<java.nio.file.Path> files = java.nio.file.Files.newDirectoryStream(stage)) {
                    for (java.nio.file.Path file : files) {
                        String name = file.getFileName().toString();
                        moveFileCompat(file, root.resolve(name)); installed.add(name);
                    }
                }
            } catch (Exception e) { throw new IllegalStateException("照片替换失败", e); }
        }

        void rollback(Throwable failure) {
            for (String name : installed) {
                try { java.nio.file.Files.deleteIfExists(root.resolve(name)); }
                catch (Exception e) { rollbackComplete = false; failure.addSuppressed(e); }
            }
            for (String name : moved) {
                try { moveFileCompat(previous.resolve(name), root.resolve(name)); }
                catch (Exception e) { rollbackComplete = false; failure.addSuppressed(e); }
            }
            if (!rollbackComplete) { log.error("照片回滚不完整,原文件保留于 {}", previous); }
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
            return readArchiveMeta(zip, new long[]{0});
        } catch (Exception e) {
            return null;
        }
    }

    private void writeEntry(ZipOutputStream zip, String path, byte[] bytes) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }

}
