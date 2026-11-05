package com.szjx.personnel.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 备份设置存储:backup-settings.json 读写与历史默认清单升级(自 BackupService 下沉)。
 * 线程同步由外层 BackupService 的 synchronized 保证,本类不做内部锁。
 */
public class BackupSettingsStore {

    private static final Logger log = LoggerFactory.getLogger(BackupSettingsStore.class);

    // 固定升级前的完整12项,不可按数量或未来的动态模块清单推断用户全选。
    private static final Set<String> PRE_DOUBLE_EXCELLENCE_MODULES = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList("personnel", "department", "dict", "dashboard", "equipment",
                    "material", "item-loans", "vehicle", "leave", "fitness", "phone", "military")));
    // 固定年度考评加入前的完整13项(双争已含);历史默认清单同样静态固定,不从动态清单推导。
    private static final Set<String> PRE_ANNUAL_APPRAISAL_MODULES = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList("personnel", "department", "dict", "dashboard", "equipment",
                    "material", "item-loans", "vehicle", "leave", "fitness", "phone", "military", "double-excellence")));

    private final ObjectMapper mapper;
    private final File settingsFile;

    public BackupSettingsStore(ObjectMapper mapper, File settingsFile) {
        this.mapper = mapper;
        this.settingsFile = settingsFile;
    }

    public BackupService.Settings load() {
        if (!settingsFile.exists()) { return new BackupService.Settings(); }
        BackupService.Settings settings;
        com.fasterxml.jackson.databind.JsonNode document;
        try {
            document = mapper.readTree(settingsFile);
            settings = mapper.treeToValue(document, BackupService.Settings.class);
        } catch (Exception e) {
            log.warn("备份设置读取失败,使用默认: {}", e.getMessage());
            return new BackupService.Settings();
        }
        if (settings == null) { return new BackupService.Settings(); }
        if (settings.modules == null) { settings.modules = BackupService.allModuleKeys(); }
        // 旧版本默认清单升级(双争/年度考评加入前保存的设置):精确匹配已知历史清单后补齐到当前全部模块,
        // 首次读取即持久化,包括自动备份关闭/尚未到期时;只改原JSON的modules。
        // 不调用save,避免改写其它设置值或补齐原文件未声明的默认字段;用户显式缩小过的清单不受影响。
        Set<String> selected = new LinkedHashSet<String>(settings.modules);
        if (selected.size() < BackupService.allModuleKeys().size()
                && (PRE_DOUBLE_EXCELLENCE_MODULES.equals(selected) || PRE_ANNUAL_APPRAISAL_MODULES.equals(selected))) {
            settings.modules = new ArrayList<String>(BackupService.allModuleKeys());
            ((com.fasterxml.jackson.databind.node.ObjectNode) document).set("modules", mapper.valueToTree(settings.modules));
            write(document);
        }
        return settings;
    }

    public void save(BackupService.Settings s) {
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
            s.modules = BackupService.allModuleKeys();
        } else {
            for (String m : s.modules) {
                if (!BackupService.MODULE_TABLES.containsKey(m)) {
                    throw new IllegalArgumentException("未知模块: " + m);
                }
            }
        }
        if (s.enabled && s.modules.isEmpty()) { throw new IllegalArgumentException("启用自动备份需至少选择一个模块"); }
        write(s);
    }

    private void write(Object settings) {
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

    @SuppressWarnings("unused")
    private static List<String> unusedImportsAnchor() { return null; }
}
