package com.szjx.personnel.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 设置迁移只读写临时目录,不得连接业务数据库或执行备份。 */
class BackupSettingsTest {
    @TempDir Path temporary;
    private final ObjectMapper mapper = new ObjectMapper();
    private JdbcTemplate jdbc;
    private PlatformTransactionManager transactions;
    private BackupService service;
    private Path settingsFile;

    @BeforeEach void setup() {
        jdbc = mock(JdbcTemplate.class);
        transactions = mock(PlatformTransactionManager.class);
        settingsFile = temporary.resolve("backup-settings.json");
        service = new BackupService(jdbc, transactions, temporary.toFile());
    }

    @AfterEach void noDatabaseAccessOrSettingsTemps() throws Exception {
        verifyNoInteractions(jdbc, transactions);
        try (java.util.stream.Stream<Path> files = Files.list(temporary)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("settings-")));
        }
    }

    private List<String> oldAll() {
        // 不从生产allKeys推导:防止未来模块变更使历史12项判定错误。
        return new ArrayList<>(Arrays.asList("personnel", "department", "dict", "dashboard", "equipment", "material",
                "item-loans", "vehicle", "leave", "fitness", "phone", "military"));
    }

    private ObjectNode originalSettings(List<String> modules) throws Exception {
        ObjectNode document = (ObjectNode) mapper.readTree("{\"enabled\":true,\"intervalHours\":24,\"keepCount\":10," +
                "\"autoLastRun\":1792393348831,\"location\":\"G:/备份 原目录\",\"other\":{\"nested\":[false,7,\"保留\"],\"nullable\":null}}");
        document.set("modules", mapper.valueToTree(modules));
        return document;
    }

    private void write(ObjectNode document) throws Exception {
        mapper.writeValue(settingsFile.toFile(), document);
    }

    @Test void firstLoadMigratesExactlyOldAllAndOnlyChangesModulesInStoredJson() throws Exception {
        ObjectNode original = originalSettings(oldAll());
        write(original);
        BackupService.Settings loaded = service.loadSettings();
        assertEquals(BackupService.allModuleKeys(), loaded.modules); // 历史完整清单升级到当前全部模块(双争+年度考评)
        assertTrue(loaded.enabled);
        assertEquals(24, loaded.intervalHours);
        assertEquals(10, loaded.keepCount);
        assertEquals(1792393348831L, loaded.autoLastRun);
        ObjectNode expected = original.deepCopy();
        expected.set("modules", mapper.valueToTree(BackupService.allModuleKeys()));
        assertEquals(expected, mapper.readTree(settingsFile.toFile()), "迁移不可归一化或丢弃任何其它JSON项");
        byte[] migrated = Files.readAllBytes(settingsFile);
        assertEquals(BackupService.allModuleKeys(), service.loadSettings().modules);
        assertArrayEquals(migrated, Files.readAllBytes(settingsFile), "再次读取不能重复重写设置");
    }

    @Test void reorderedExactOldAllMigratesBySetToCanonicalFullModuleList() throws Exception {
        List<String> reordered = oldAll();
        Collections.reverse(reordered);
        write(originalSettings(reordered));
        assertEquals(BackupService.allModuleKeys(), service.loadSettings().modules);
        assertEquals(mapper.valueToTree(BackupService.allModuleKeys()), mapper.readTree(settingsFile.toFile()).get("modules"));
    }

    @Test void disabledSettingsMigrateBeforeSchedulerReturnsWithoutNormalizingOtherValues() throws Exception {
        ObjectNode original = originalSettings(oldAll());
        original.put("enabled", false);
        // 迁移不是用户保存,即便历史值不在saveSettings范围内也不可擅自归一化。
        original.put("intervalHours", 0);
        original.put("keepCount", 301);
        write(original);
        service.runAutoIfDue();
        ObjectNode expected = original.deepCopy();
        expected.set("modules", mapper.valueToTree(BackupService.allModuleKeys()));
        assertEquals(expected, mapper.readTree(settingsFile.toFile()));
        assertFalse(Files.exists(temporary.resolve("backup")));
    }

    @Test void notDueSchedulerStillPersistsMigrationWithoutAdvancingLastRun() throws Exception {
        ObjectNode original = originalSettings(oldAll());
        original.put("autoLastRun", System.currentTimeMillis());
        write(original);
        service.runAutoIfDue();
        JsonNode after = mapper.readTree(settingsFile.toFile());
        assertEquals(17, after.get("modules").size());
        assertEquals(original.get("autoLastRun"), after.get("autoLastRun"));
        assertFalse(Files.exists(temporary.resolve("backup")));
    }

    @Test void emptyCustomSubsetsDuplicateAndFakeTwelveSelectionsAreNeverExpandedOrRewritten() throws Exception {
        List<String> duplicateTwelve = oldAll();
        duplicateTwelve.set(11, "personnel");
        List<String> unknownTwelve = oldAll();
        unknownTwelve.set(11, "unknown-module");
        List<String> newModuleButMissingOldOne = oldAll();
        newModuleButMissingOldOne.set(11, "double-excellence");
        List<String> preItemLoans = oldAll();
        preItemLoans.remove("item-loans");
        for (List<String> selection : Arrays.asList(Collections.<String>emptyList(), Arrays.asList("personnel", "fitness"),
                Arrays.asList("double-excellence"), preItemLoans, duplicateTwelve, unknownTwelve, newModuleButMissingOldOne,
                BackupService.allModuleKeys())) {
            ObjectNode original = originalSettings(selection);
            original.put("enabled", false);
            write(original);
            byte[] before = Files.readAllBytes(settingsFile);
            BackupService.Settings loaded = service.loadSettings();
            assertEquals(selection, loaded.modules);
            assertEquals(1792393348831L, loaded.autoLastRun);
            assertEquals("G:/备份 原目录", loaded.getExtraSettings().get("location"));
            assertArrayEquals(before, Files.readAllBytes(settingsFile), "自定义modules不可扩大或改写: " + selection);
        }
    }

    @Test void freshMissingAndNullModulesDefaultToAllFourteenWithoutCreatingOrRewritingSettings() throws Exception {
        assertEquals(17, new BackupService.Settings().modules.size());
        assertEquals(BackupService.allModuleKeys(), service.loadSettings().modules);
        assertFalse(Files.exists(settingsFile));
        for (String json : Arrays.asList("{}", "{\"modules\":null}")) {
            Files.write(settingsFile, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] before = Files.readAllBytes(settingsFile);
            assertEquals(BackupService.allModuleKeys(), service.loadSettings().modules);
            assertArrayEquals(before, Files.readAllBytes(settingsFile));
        }
    }

    @Test void migrationDoesNotMaterializeMissingDefaultsAndLaterNormalSavePreservesExtensionValues() throws Exception {
        ObjectNode original = mapper.createObjectNode();
        original.set("modules", mapper.valueToTree(oldAll()));
        original.put("location", "原位置");
        write(original);
        BackupService.Settings migrated = service.loadSettings();
        ObjectNode expected = original.deepCopy();
        expected.set("modules", mapper.valueToTree(BackupService.allModuleKeys()));
        assertEquals(expected, mapper.readTree(settingsFile.toFile()));
        migrated.autoLastRun = 123;
        service.saveSettings(migrated);
        JsonNode saved = mapper.readTree(settingsFile.toFile());
        assertEquals("原位置", saved.get("location").asText());
        assertEquals(123, saved.get("autoLastRun").asInt());
        assertFalse(saved.has("extraSettings"), "扩展设置必须保留原JSON字段位置");
    }
}
