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
 * 每个模块独立事务,失败只回滚该模块。管理员账号(admin_user)不参与备份恢复。
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private static final File DIR = new File("data", "backup");
    private static final File PHOTOS_DIR = new File("data", "photos");
    private static final File SETTINGS_FILE = new File("data", "backup-settings.json");

    /** 模块 → 数据表(模块 key 与网页端菜单一致) */
    public static final Map<String, String[]> MODULE_TABLES = new LinkedHashMap<String, String[]>();

    static {
        MODULE_TABLES.put("personnel", new String[]{"personnel", "position_history", "reward_punish", "child_info", "family_member", "driver_license", "personal_card", "private_vehicle", "housing_info"});
        MODULE_TABLES.put("department", new String[]{"department"});
        MODULE_TABLES.put("dict", new String[]{"sys_dict"});
        MODULE_TABLES.put("equipment", new String[]{"equipment"});
        MODULE_TABLES.put("material", new String[]{"material"});
        MODULE_TABLES.put("vehicle", new String[]{"vehicle"});
        MODULE_TABLES.put("leave", new String[]{"leave_record"});
        MODULE_TABLES.put("fitness", new String[]{"fitness_record"});
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper = new ObjectMapper();

    public BackupService(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
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
        if (SETTINGS_FILE.exists()) {
            try {
                Settings s = mapper.readValue(SETTINGS_FILE, Settings.class);
                if (s != null) {
                    if (s.modules == null || s.modules.isEmpty()) {
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
        if (s.modules == null || s.modules.isEmpty()) {
            s.modules = allModuleKeys();
        } else {
            for (String m : s.modules) {
                if (!MODULE_TABLES.containsKey(m)) {
                    throw new IllegalArgumentException("未知模块: " + m);
                }
            }
        }
        try {
            File parent = SETTINGS_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(SETTINGS_FILE, s);
        } catch (Exception e) {
            throw new RuntimeException("备份设置保存失败: " + e.getMessage(), e);
        }
    }

    /* ================= 备份 ================= */

    public synchronized Map<String, Object> createBackup(List<String> modules, boolean auto) {
        List<String> selected = checkModules(modules);
        if (!DIR.exists()) {
            DIR.mkdirs();
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prefix = auto ? "auto_" : "";
        File file = new File(DIR, prefix + "backup_" + stamp + ".zip");
        int n = 1;
        while (file.exists()) {
            file = new File(DIR, prefix + "backup_" + stamp + "_" + (n++) + ".zip");
        }

        Map<String, Object> moduleSummary = new LinkedHashMap<String, Object>();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {

            // 先收集各模块数据(同时统计行数),meta 才能带上准确的模块清单
            Map<String, Map<String, byte[]>> moduleData = new LinkedHashMap<String, Map<String, byte[]>>();
            for (String module : selected) {
                Map<String, byte[]> tablesData = new LinkedHashMap<String, byte[]>();
                Map<String, Object> tables = new LinkedHashMap<String, Object>();
                for (String table : MODULE_TABLES.get(module)) {
                    List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + table);
                    tablesData.put(table, mapper.writeValueAsBytes(rows));
                    tables.put(table, rows.size());
                }
                moduleData.put(module, tablesData);
                moduleSummary.put(module, tables);
            }

            Map<String, Object> meta = new LinkedHashMap<String, Object>();
            meta.put("version", 1);
            meta.put("auto", auto);
            meta.put("created", System.currentTimeMillis());
            meta.put("createdText", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            meta.put("modules", moduleSummary);
            writeEntry(zip, "meta.json", mapper.writeValueAsBytes(meta));

            for (String module : selected) {
                Map<String, byte[]> tablesData = moduleData.get(module);
                for (String table : MODULE_TABLES.get(module)) {
                    writeEntry(zip, "data/" + module + "/" + table + ".json", tablesData.get(table));
                }
            }

            // 人员模块附带照片
            int photoCount = 0;
            if (selected.contains("personnel") && PHOTOS_DIR.exists()) {
                File[] photos = PHOTOS_DIR.listFiles();
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
        File[] files = DIR == null || !DIR.exists() ? null : DIR.listFiles();
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
        File f = safeFile(name);
        if (!f.exists()) {
            throw new IllegalArgumentException("备份文件不存在: " + name);
        }
        Map<String, Object> meta = readMeta(f);
        if (meta == null) {
            throw new IllegalArgumentException("不是本系统生成的备份文件(缺少元数据)");
        }
        Map<String, Object> available = (Map<String, Object>) meta.get("modules");
        if (available == null || available.isEmpty()) {
            throw new IllegalArgumentException("备份中没有可恢复的模块数据");
        }

        List<String> toRestore = new ArrayList<String>();
        for (String m : requestedModules) {
            if (available.containsKey(m) && MODULE_TABLES.containsKey(m)) {
                toRestore.add(m);
            }
        }
        if (toRestore.isEmpty()) {
            throw new IllegalArgumentException("所选模块不在该备份中,请重新选择");
        }

        List<Map<String, Object>> restored = new ArrayList<Map<String, Object>>();
        int photoCount = 0;
        try (ZipFile zip = new ZipFile(f)) {
            for (String module : toRestore) {
                final Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
                tx.executeWithoutResult(status -> {
                    try {
                        for (String table : MODULE_TABLES.get(module)) {
                            ZipEntry entry = zip.getEntry("data/" + module + "/" + table + ".json");
                            List<Map<String, Object>> rows = entry == null
                                    ? new ArrayList<Map<String, Object>>()
                                    : mapper.readValue(zip.getInputStream(entry), List.class);
                            jdbc.update("DELETE FROM " + table);
                            if (!rows.isEmpty()) {
                                List<String> cols = new ArrayList<String>(rows.get(0).keySet());
                                StringBuilder colSql = new StringBuilder();
                                StringBuilder markSql = new StringBuilder();
                                for (String c : cols) {
                                    if (colSql.length() > 0) {
                                        colSql.append(",");
                                        markSql.append(",");
                                    }
                                    colSql.append("\"").append(c).append("\"");
                                    markSql.append("?");
                                }
                                String insert = "INSERT INTO " + table + " (" + colSql + ") VALUES ("
                                        + markSql + ")";
                                for (Map<String, Object> row : rows) {
                                    Object[] args = new Object[cols.size()];
                                    for (int i = 0; i < cols.size(); i++) {
                                        args[i] = row.get(cols.get(i));
                                    }
                                    jdbc.update(insert, args);
                                }
                            }
                            counts.put(table, rows.size());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                });
                Map<String, Object> r = new LinkedHashMap<String, Object>();
                r.put("module", module);
                r.put("tables", counts);
                restored.add(r);
            }

            // 人员模块的照片:备份里带了照片就整体替换
            if (toRestore.contains("personnel")) {
                java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                List<ZipEntry> photoEntries = new ArrayList<ZipEntry>();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (e.getName().startsWith("photos/") && !e.isDirectory()) {
                        photoEntries.add(e);
                    }
                }
                if (!photoEntries.isEmpty()) {
                    if (!PHOTOS_DIR.exists()) {
                        PHOTOS_DIR.mkdirs();
                    }
                    File[] old = PHOTOS_DIR.listFiles();
                    if (old != null) {
                        for (File p : old) {
                            if (p.isFile()) {
                                p.delete();
                            }
                        }
                    }
                    for (ZipEntry e : photoEntries) {
                        String fileName = e.getName().substring("photos/".length());
                        if (fileName.contains("/") || fileName.contains("\\") || fileName.isEmpty()) {
                            continue;
                        }
                        try (InputStream in = zip.getInputStream(e);
                             OutputStream out = new FileOutputStream(new File(PHOTOS_DIR, fileName))) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                        }
                        photoCount++;
                    }
                }
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("恢复失败: " + e.getMessage(), e);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("restored", restored);
        result.put("photos", photoCount);
        log.info("已从备份 {} 恢复模块 {} (照片 {})", name, toRestore, photoCount);
        return result;
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
        } catch (Exception e) {
            log.error("自动备份失败: {}", e.getMessage());
        } finally {
            s.autoLastRun = System.currentTimeMillis();
            try {
                saveSettings(s);
            } catch (Exception ignore) {
                // 设置保存失败不影响主流程
            }
            pruneAutoBackups(s.keepCount);
        }
    }

    private void pruneAutoBackups(int keepCount) {
        File[] files = DIR == null || !DIR.exists() ? null : DIR.listFiles();
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
        return new File(DIR, name);
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
