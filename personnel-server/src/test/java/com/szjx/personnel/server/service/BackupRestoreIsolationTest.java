package com.szjx.personnel.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Regression tests use only an isolated SQLite database and temporary data root. */
class BackupRestoreIsolationTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private BackupService service;
    private Path dataRoot;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(temp.resolve("db"));
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp.resolve("db"));
        dataRoot = temp.resolve("data");
        service = new BackupService(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()), dataRoot.toFile());
        for (String[] tables : BackupService.MODULE_TABLES.values()) {
            for (String table : tables) {
                if ("admin_user".equals(table)) {
                    jdbc.execute("CREATE TABLE admin_user(id INTEGER PRIMARY KEY, username TEXT, password_hash TEXT, display_name TEXT, created_at TEXT, parent_id INTEGER, enabled INTEGER)");
                } else if ("sys_setting".equals(table)) {
                    jdbc.execute("CREATE TABLE sys_setting(skey TEXT PRIMARY KEY, svalue TEXT)");
                } else if ("dash_setting".equals(table)) {
                    jdbc.execute("CREATE TABLE dash_setting(skey TEXT PRIMARY KEY, svalue TEXT)");
                } else if ("item_loan".equals(table)) {
                    PersonnelIntegrityTest.itemLoanSchema(jdbc);
                } else if ("double_excellence_record".equals(table)) {
                    jdbc.execute("CREATE TABLE double_excellence_record(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "personnel_id INTEGER NOT NULL, year INTEGER NOT NULL CHECK(year BETWEEN 1900 AND 9999), " +
                            "quarter INTEGER NOT NULL CHECK(quarter BETWEEN 1 AND 4), " +
                            "rating TEXT NOT NULL CHECK(rating IN ('好','中','一般')), rating_date TEXT NOT NULL, " +
                            "created_at TEXT DEFAULT (datetime('now','localtime')), updated_at TEXT DEFAULT (datetime('now','localtime')), " +
                            "UNIQUE(personnel_id,year,quarter))");
                } else if ("delete_undo".equals(table)) {
                    jdbc.execute("CREATE TABLE delete_undo(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER NOT NULL, name TEXT DEFAULT '', summary TEXT DEFAULT '', payload TEXT NOT NULL, photo_trash TEXT DEFAULT '', created_at TEXT DEFAULT '')");
                } else if ("absence_record".equals(table)) {
                    jdbc.execute("CREATE TABLE absence_record(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "personnel_id INTEGER NOT NULL, reason TEXT DEFAULT '', leave_date TEXT NOT NULL DEFAULT '', " +
                            "expected_return_date TEXT DEFAULT '', actual_return_date TEXT DEFAULT '', remark TEXT DEFAULT '', " +
                            "created_at TEXT DEFAULT (datetime('now','localtime')), updated_at TEXT DEFAULT (datetime('now','localtime')))");
                } else {
                    jdbc.execute("CREATE TABLE " + table + "(id INTEGER PRIMARY KEY)");
                }
            }
        }
        jdbc.execute("CREATE TABLE account_department(user_id INTEGER, department_id INTEGER, PRIMARY KEY(user_id, department_id))");
    }

    @Test
    void personnelRestoreReplacesOnlyPersonnelPhotoFiles() throws Exception {
        Path photos = Files.createDirectories(dataRoot.resolve("photos"));
        Files.write(photos.resolve("old-person.jpg"), new byte[]{1});
        Path vehicle = Files.createDirectories(photos.resolve("vehicle"));
        Files.write(vehicle.resolve("vehicle.jpg"), new byte[]{2});
        Path equipment = Files.createDirectories(photos.resolve("equipment"));
        Files.write(equipment.resolve("equipment.jpg"), new byte[]{3});
        Path material = Files.createDirectories(photos.resolve("material"));
        Files.write(material.resolve("material.jpg"), new byte[]{4});
        Path trash = Files.createDirectories(photos.resolve("trash"));
        Files.write(trash.resolve("undo.jpg"), new byte[]{5});
        jdbc.update("INSERT INTO delete_undo(personnel_id,name,payload,photo_trash) VALUES(10,'旧撤回','{}','undo.jpg')");

        String backup = createArchive("personnel-photos.zip", "personnel", true,
                mapOf("personnel", BackupService.MODULE_TABLES.get("personnel")), "photos/person.jpg", new byte[]{9});
        service.restore(backup, Arrays.asList("personnel"));

        assertFalse(Files.exists(photos.resolve("old-person.jpg")));
        assertArrayEquals(new byte[]{9}, Files.readAllBytes(photos.resolve("person.jpg")));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(vehicle.resolve("vehicle.jpg")));
        assertArrayEquals(new byte[]{3}, Files.readAllBytes(equipment.resolve("equipment.jpg")));
        assertArrayEquals(new byte[]{4}, Files.readAllBytes(material.resolve("material.jpg")));
        assertArrayEquals(new byte[]{5}, Files.readAllBytes(trash.resolve("undo.jpg")), "旧备份不包含撤回快照时保留本地回收站");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM delete_undo", Integer.class));
    }

    @Test
    void personnelBackupRoundTripsUndoRecordsAndReferencedTrashPhotos() throws Exception {
        Path photos = Files.createDirectories(dataRoot.resolve("photos"));
        Path trash = Files.createDirectories(photos.resolve("trash"));
        Files.write(trash.resolve("undo-a.jpg"), new byte[]{7});
        Files.write(trash.resolve("orphan.jpg"), new byte[]{8});
        jdbc.update("INSERT INTO delete_undo(id,personnel_id,name,summary,payload,photo_trash,created_at) " +
                "VALUES(1,10,'张三','删除','{\"person\":{}}','undo-a.jpg','2026-10-01')");

        Map<String, Object> result = service.createBackup(Arrays.asList("personnel"), false);
        String backup = (String) result.get("name");
        try (ZipFile zip = new ZipFile(dataRoot.resolve("backup").resolve(backup).toFile())) {
            assertNotNull(zip.getEntry("data/personnel/delete_undo.json"));
            assertNotNull(zip.getEntry("photos/personnel-trash/undo-a.jpg"));
            assertNull(zip.getEntry("photos/personnel-trash/orphan.jpg"));
            Map<?, ?> meta = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    zip.getInputStream(zip.getEntry("meta.json")), Map.class);
            assertEquals(4, ((Number) meta.get("version")).intValue());
            assertEquals(Boolean.TRUE, meta.get("personnelTrash"));
        }
        jdbc.update("DELETE FROM delete_undo");
        Files.delete(trash.resolve("undo-a.jpg"));
        Files.write(trash.resolve("local-only.jpg"), new byte[]{9});

        service.restore(backup, Arrays.asList("personnel"));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM delete_undo", Integer.class));
        assertEquals("undo-a.jpg", jdbc.queryForObject("SELECT photo_trash FROM delete_undo", String.class));
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(trash.resolve("undo-a.jpg")));
        assertFalse(Files.exists(trash.resolve("orphan.jpg")));
        assertFalse(Files.exists(trash.resolve("local-only.jpg")));
    }

    @Test
    void uploadedFullBackupRoundTripsWithoutRestoringOnUploadAndCanSelectNonPersonnelModules() throws Exception {
        Path photos = Files.createDirectories(dataRoot.resolve("photos"));
        Path trash = Files.createDirectories(photos.resolve("trash"));
        Files.write(photos.resolve("person.jpg"), new byte[]{1});
        Files.write(trash.resolve("undo.jpg"), new byte[]{2});
        jdbc.update("INSERT INTO personnel(id) VALUES(10)");
        jdbc.update("INSERT INTO equipment(id) VALUES(20)");
        jdbc.update("INSERT INTO sys_dict(id) VALUES(30)");
        jdbc.update("INSERT INTO delete_undo(id,personnel_id,payload,photo_trash) VALUES(1,10,'{}','undo.jpg')");
        Files.write(dataRoot.resolve("config.json"), "{\"port\":8090}".getBytes(StandardCharsets.UTF_8));
        String original = (String) service.createBackup(BackupService.allModuleKeys(), false).get("name");
        byte[] archive = Files.readAllBytes(dataRoot.resolve("backup").resolve(original));
        jdbc.update("INSERT INTO personnel(id) VALUES(11)");
        jdbc.update("INSERT INTO equipment(id) VALUES(21)");
        jdbc.update("INSERT INTO sys_dict(id) VALUES(31)");
        jdbc.update("UPDATE delete_undo SET payload='local'");
        Files.write(dataRoot.resolve("config.json"), "{\"port\":8080}".getBytes(StandardCharsets.UTF_8));
        Files.write(photos.resolve("person.jpg"), new byte[]{3});
        Files.write(trash.resolve("undo.jpg"), new byte[]{4});
        Files.write(trash.resolve("local.jpg"), new byte[]{5});
        List<Map<String, Object>> localPersonnel = jdbc.queryForList("SELECT * FROM personnel ORDER BY id");
        List<Map<String, Object>> localUndo = jdbc.queryForList("SELECT * FROM delete_undo");

        Map<String, Object> uploaded = service.upload(new org.springframework.mock.web.MockMultipartFile(
                "file", "../../original.zip", "application/octet-stream", archive));
        String name = (String) uploaded.get("name");
        assertNotEquals(original, name);
        assertEquals(uploaded, service.listBackups().stream().filter(item -> name.equals(item.get("name"))).findFirst().get());
        assertArrayEquals(archive, Files.readAllBytes(service.getBackupFile(name).toPath()));
        assertEquals(localPersonnel, jdbc.queryForList("SELECT * FROM personnel ORDER BY id"));
        assertEquals(localUndo, jdbc.queryForList("SELECT * FROM delete_undo"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict", Integer.class));
        assertEquals("{\"port\":8080}", new String(Files.readAllBytes(dataRoot.resolve("config.json")), StandardCharsets.UTF_8));

        service.restore(name, Arrays.asList("equipment"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict", Integer.class));
        assertEquals(localPersonnel, jdbc.queryForList("SELECT * FROM personnel ORDER BY id"));
        assertEquals(localUndo, jdbc.queryForList("SELECT * FROM delete_undo"));
        assertArrayEquals(new byte[]{3}, Files.readAllBytes(photos.resolve("person.jpg")));
        assertArrayEquals(new byte[]{4}, Files.readAllBytes(trash.resolve("undo.jpg")));
        assertArrayEquals(new byte[]{5}, Files.readAllBytes(trash.resolve("local.jpg")));

        assertEquals("{\"port\":8080}", new String(Files.readAllBytes(dataRoot.resolve("config.json")), StandardCharsets.UTF_8));
        service.restore(name, Arrays.asList("dict"));
        assertEquals("{\"port\":8090}", new String(Files.readAllBytes(dataRoot.resolve("config.json")), StandardCharsets.UTF_8));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict", Integer.class));
        assertEquals(localPersonnel, jdbc.queryForList("SELECT * FROM personnel ORDER BY id"));
        assertEquals(localUndo, jdbc.queryForList("SELECT * FROM delete_undo"));
        assertArrayEquals(new byte[]{3}, Files.readAllBytes(photos.resolve("person.jpg")));
        assertArrayEquals(new byte[]{4}, Files.readAllBytes(trash.resolve("undo.jpg")));
        assertArrayEquals(new byte[]{5}, Files.readAllBytes(trash.resolve("local.jpg")));

        service.restore(name, Arrays.asList("personnel"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
        assertEquals("{}", jdbc.queryForObject("SELECT payload FROM delete_undo", String.class));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(photos.resolve("person.jpg")));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(trash.resolve("undo.jpg")));
        assertFalse(Files.exists(trash.resolve("local.jpg")));
    }

    @Test void personnelMilitaryIdentityBacksUpViaSelectStarAndLegacyArchivesDefaultToEmpty() throws Exception {
        jdbc.execute("ALTER TABLE personnel ADD COLUMN military_id_no TEXT DEFAULT ''");
        jdbc.update("INSERT INTO personnel(id,military_id_no) VALUES(42,'000军证-备份文本')");
        String backup = (String) service.createBackup(Arrays.asList("personnel"), false).get("name");
        try (ZipFile zip = new ZipFile(dataRoot.resolve("backup").resolve(backup).toFile())) {
            List<?> rows = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    zip.getInputStream(zip.getEntry("data/personnel/personnel.json")), List.class);
            Map<?,?> person = (Map<?,?>) rows.get(0);
            assertEquals("000军证-备份文本", person.get("military_id_no"));
            assertFalse(person.containsKey("department_path"));
        }
        jdbc.update("UPDATE personnel SET military_id_no='本地变更'");
        service.restore(backup, Arrays.asList("personnel"));
        assertEquals("000军证-备份文本", jdbc.queryForObject("SELECT military_id_no FROM personnel WHERE id=42", String.class));
        Map<String,Object> legacyPerson = new LinkedHashMap<>(); legacyPerson.put("id", 42);
        String legacy = createLegacyPersonnelArchive("legacy-no-military-id.zip", Arrays.asList(legacyPerson));
        service.restore(legacy, Arrays.asList("personnel"));
        assertEquals("", jdbc.queryForObject("SELECT military_id_no FROM personnel WHERE id=42", String.class));
    }

    @Test
    void personnelBackupFailsWhenReferencedTrashPhotoIsMissing() {
        jdbc.update("INSERT INTO delete_undo(personnel_id,name,payload,photo_trash) VALUES(10,'损坏撤回','{}','missing.jpg')");
        assertThrows(RuntimeException.class, () -> service.createBackup(Arrays.asList("personnel"), false));
    }

    @Test
    void dictBackupRoundTripsSystemConfigFiles() throws Exception {
        Files.createDirectories(dataRoot);
        byte[] settings = "{\"enabled\":true,\"intervalHours\":6,\"keepCount\":5,\"modules\":[\"dict\"],\"autoLastRun\":123}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] appConfig = "{\"port\":8090,\"setupDone\":true,\"adminUser\":\"admin\"}"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(dataRoot.resolve("backup-settings.json"), settings);
        Files.write(dataRoot.resolve("config.json"), appConfig);

        String backup = (String) service.createBackup(Arrays.asList("dict"), false).get("name");
        try (ZipFile zip = new ZipFile(dataRoot.resolve("backup").resolve(backup).toFile())) {
            assertNotNull(zip.getEntry("config/backup-settings.json"));
            assertNotNull(zip.getEntry("config/config.json"));
            Map<?, ?> meta = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    zip.getInputStream(zip.getEntry("meta.json")), Map.class);
            assertEquals(Arrays.asList("backup-settings.json", "config.json"), meta.get("configFiles"));
        }
        Files.write(dataRoot.resolve("backup-settings.json"), "{\"enabled\":false}".getBytes(StandardCharsets.UTF_8));
        Files.write(dataRoot.resolve("config.json"), "{\"port\":8080}".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> restored = service.restore(backup, Arrays.asList("dict"));

        assertArrayEquals(settings, Files.readAllBytes(dataRoot.resolve("backup-settings.json")));
        assertArrayEquals(appConfig, Files.readAllBytes(dataRoot.resolve("config.json")));
        assertEquals(Boolean.TRUE, restored.get("requiresRestart"));
        assertTrue(((List<?>) restored.get("configFiles")).contains("config.json"));
    }

    @Test
    void oldDictBackupWithoutSystemConfigFilesPreservesLocalFiles() throws Exception {
        Files.createDirectories(dataRoot);
        byte[] local = "{\"port\":8080}".getBytes(StandardCharsets.UTF_8);
        Files.write(dataRoot.resolve("config.json"), local);
        String backup = createArchive("legacy-dict-config.zip", "dict", false,
                mapOf("dict", BackupService.MODULE_TABLES.get("dict")));

        service.restore(backup, Arrays.asList("dict"));

        assertArrayEquals(local, Files.readAllBytes(dataRoot.resolve("config.json")));
    }

    @Test
    void dictBackupIncludesAccountDepartmentWhenSchemaExists() throws Exception {
        Map<String, Object> result = service.createBackup(Arrays.asList("dict"), false);
        try (ZipFile zip = new ZipFile(new File(dataRoot.toFile(), "backup/" + result.get("name")))) {
            assertNotNull(zip.getEntry("data/dict/account_department.json"));
        }
    }

    @Test
    void legacyDictBackupWithoutAccountDepartmentPreservesExistingGrants() throws Exception {
        jdbc.update("INSERT INTO admin_user(id,username,password_hash) VALUES(1,'existing','hash')");
        jdbc.update("INSERT INTO account_department(user_id,department_id) VALUES(1,7)");
        jdbc.update("INSERT INTO sys_setting(skey,svalue) VALUES('local_setting','old')");
        List<String> dictTables = Arrays.asList("sys_dict", "sys_setting", "holiday_calendar", "admin_user", "op_log");
        String backup = createArchive("legacy-dict.zip", "dict", false, mapOf("dict", dictTables));

        Map<String, Object> restored = service.restore(backup, Arrays.asList("dict"));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_department WHERE user_id=1 AND department_id=7", Integer.class));
        assertEquals("existing", jdbc.queryForObject("SELECT username FROM admin_user WHERE id=1", String.class));
        assertFalse(((List<?>) restored.get("warnings")).isEmpty());
        assertEquals(Boolean.FALSE, restored.get("securityStateRestored"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_setting", Integer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unsafeLegacyAccountHierarchyIsSkippedButOtherDictTablesRestore() throws Exception {
        jdbc.update("INSERT INTO department(id) VALUES(7)");
        jdbc.update("INSERT INTO admin_user(id,username,password_hash,parent_id,enabled) VALUES(1,'existing','hash',0,1)");
        jdbc.update("INSERT INTO account_department(user_id,department_id) VALUES(1,7)");
        jdbc.update("INSERT INTO sys_dict(id) VALUES(5)");
        List<String> tables = new ArrayList<>(Arrays.asList(BackupService.MODULE_TABLES.get("dict")));
        tables.add("account_department");
        Map<String, Object> declared = metadata(mapOf("dict", tables));
        ((Map<String, Object>) declared.get("dict")).put("admin_user", 1);
        Path backup = Files.createDirectories(dataRoot.resolve("backup")).resolve("unsafe-legacy.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(backup))) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("version", 2); meta.put("modules", declared);
            add(zip, "meta.json", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(meta));
            for (String table : tables) {
                String json = "admin_user".equals(table)
                        ? "[{\"id\":99,\"username\":\"legacy\",\"password_hash\":\"old\"}]" : "[]";
                add(zip, "data/dict/" + table + ".json", json.getBytes(StandardCharsets.UTF_8));
            }
        }
        Map<String, Object> result = service.restore(backup.getFileName().toString(), Arrays.asList("dict"));
        assertEquals("existing", jdbc.queryForObject("SELECT username FROM admin_user WHERE id=1", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_department WHERE user_id=1 AND department_id=7", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict", Integer.class));
        assertFalse(((List<?>) result.get("warnings")).isEmpty());
        assertEquals(Boolean.FALSE, result.get("securityStateRestored"));
    }

    @Test
    void scopedAssetRoundTripKeepsOtherModulesAndTrash() throws Exception {
        for (String module : Arrays.asList("equipment", "material", "vehicle")) {
            jdbc.execute("ALTER TABLE " + module + " ADD COLUMN photo TEXT");
            jdbc.update("INSERT INTO " + module + "(id,photo) VALUES(1,?)", module + ".jpg");
            Path root = Files.createDirectories(dataRoot.resolve("photos/" + module));
            Files.write(root.resolve(module + ".jpg"), new byte[]{1});
            Files.write(root.resolve("orphan.jpg"), new byte[]{2});
        }
        Path photos = dataRoot.resolve("photos");
        Files.write(photos.resolve("person.jpg"), new byte[]{3});
        Path trash = Files.createDirectories(photos.resolve("trash"));
        Files.write(trash.resolve("undo.jpg"), new byte[]{4});
        String backup = (String) service.createBackup(Arrays.asList("equipment"), false).get("name");
        try (ZipFile zip = new ZipFile(dataRoot.resolve("backup").resolve(backup).toFile())) {
            assertNotNull(zip.getEntry("photos/equipment/equipment.jpg"));
            assertNull(zip.getEntry("photos/equipment/orphan.jpg"));
            assertNull(zip.getEntry("photos/person.jpg"));
            assertNull(zip.getEntry("photos/trash/undo.jpg"));
            assertNull(zip.getEntry("photos/vehicle/vehicle.jpg"));
        }
        Files.write(photos.resolve("equipment/equipment.jpg"), new byte[]{9});
        jdbc.update("DELETE FROM equipment");
        Map<String, Object> result = service.restore(backup, Arrays.asList("equipment"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM equipment WHERE photo='equipment.jpg'", Integer.class));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(photos.resolve("equipment/equipment.jpg")));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(photos.resolve("material/material.jpg")));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(photos.resolve("vehicle/vehicle.jpg")));
        assertArrayEquals(new byte[]{3}, Files.readAllBytes(photos.resolve("person.jpg")));
        assertArrayEquals(new byte[]{4}, Files.readAllBytes(trash.resolve("undo.jpg")));
        assertEquals(1, result.get("photos"));
    }

    @Test
    void allAssetSnapshotsCanRestoreOneScopeWithoutTouchingOtherPhotos() throws Exception {
        for (String module : Arrays.asList("equipment", "material", "vehicle")) {
            jdbc.execute("ALTER TABLE " + module + " ADD COLUMN photo TEXT");
            jdbc.update("INSERT INTO " + module + "(id,photo) VALUES(1,?)", module + ".jpg");
            Path root = Files.createDirectories(dataRoot.resolve("photos/" + module));
            Files.write(root.resolve(module + ".jpg"), new byte[]{1});
        }
        String backup = (String) service.createBackup(Arrays.asList("equipment", "material", "vehicle"), false).get("name");
        for (String module : Arrays.asList("equipment", "material", "vehicle")) {
            Files.write(dataRoot.resolve("photos/" + module + "/" + module + ".jpg"), new byte[]{9});
        }
        service.restore(backup, Arrays.asList("vehicle"));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(dataRoot.resolve("photos/vehicle/vehicle.jpg")));
        assertArrayEquals(new byte[]{9}, Files.readAllBytes(dataRoot.resolve("photos/equipment/equipment.jpg")));
        assertArrayEquals(new byte[]{9}, Files.readAllBytes(dataRoot.resolve("photos/material/material.jpg")));
    }

    @Test
    void emptyAssetSnapshotClearsOnlySelectedDirectFiles() throws Exception {
        String backup = (String) service.createBackup(Arrays.asList("material"), false).get("name");
        Path material = Files.createDirectories(dataRoot.resolve("photos/material"));
        Files.write(material.resolve("later.jpg"), new byte[]{3});
        Path trash = Files.createDirectories(material.resolve("trash"));
        Files.write(trash.resolve("undo.jpg"), new byte[]{4});
        service.restore(backup, Arrays.asList("material"));
        assertFalse(Files.exists(material.resolve("later.jpg")));
        assertArrayEquals(new byte[]{4}, Files.readAllBytes(trash.resolve("undo.jpg")));
    }

    @Test
    void accountSnapshotRestoresUsersAndGrantsTogether() throws Exception {
        jdbc.update("INSERT INTO department(id) VALUES(7)");
        jdbc.update("INSERT INTO admin_user(id,username,password_hash,parent_id,enabled) VALUES(1,'root','hash',0,1)");
        jdbc.update("INSERT INTO admin_user(id,username,password_hash,parent_id,enabled) VALUES(2,'child','hash2',1,1)");
        jdbc.update("INSERT INTO account_department(user_id,department_id) VALUES(2,7)");
        String backup = (String) service.createBackup(Arrays.asList("dict"), false).get("name");
        jdbc.update("DELETE FROM account_department");
        jdbc.update("UPDATE admin_user SET enabled=0 WHERE id=2");
        Map<String, Object> result = service.restore(backup, Arrays.asList("dict"));
        assertEquals(1, jdbc.queryForObject("SELECT enabled FROM admin_user WHERE id=2", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_department WHERE user_id=2 AND department_id=7", Integer.class));
        assertEquals(Boolean.TRUE, result.get("securityStateRestored"));
    }

    @Test
    void combinedDictAndFitnessRestoreWritesSharedSettingsOnlyOnce() {
        jdbc.update("INSERT INTO sys_setting(skey,svalue) VALUES('fitness_grade_rule','rule')");
        jdbc.update("INSERT INTO sys_setting(skey,svalue) VALUES('bmi_standard','bmi')");
        jdbc.update("INSERT INTO sys_setting(skey,svalue) VALUES('other','keep')");
        String backup = (String) service.createBackup(Arrays.asList("dict", "fitness"), false).get("name");
        jdbc.update("UPDATE sys_setting SET svalue='changed'");
        service.restore(backup, Arrays.asList("dict", "fitness"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sys_setting", Integer.class));
        assertEquals("rule", jdbc.queryForObject("SELECT svalue FROM sys_setting WHERE skey='fitness_grade_rule'", String.class));
        assertEquals("bmi", jdbc.queryForObject("SELECT svalue FROM sys_setting WHERE skey='bmi_standard'", String.class));
        assertEquals("keep", jdbc.queryForObject("SELECT svalue FROM sys_setting WHERE skey='other'", String.class));
    }

    @Test
    void departmentRestoreCannotOrphanPreservedAccountGrants() throws Exception {
        jdbc.update("INSERT INTO department(id) VALUES(7)");
        jdbc.update("INSERT INTO admin_user(id,username,password_hash,parent_id,enabled) VALUES(1,'root','hash',0,1)");
        jdbc.update("INSERT INTO account_department(user_id,department_id) VALUES(1,7)");
        String backup = createArchive("department-empty.zip", "department", false,
                mapOf("department", BackupService.MODULE_TABLES.get("department")));
        assertThrows(IllegalArgumentException.class, () -> service.restore(backup, Arrays.asList("department")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM department WHERE id=7", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_department", Integer.class));
    }

    @Test
    void photoInstallFailureRollsBackDatabaseAndAlreadyMovedFiles() throws Exception {
        jdbc.update("INSERT INTO personnel(id) VALUES(9)");
        Path photos = Files.createDirectories(dataRoot.resolve("photos"));
        Files.write(photos.resolve("original.jpg"), new byte[]{7});
        Files.createDirectory(photos.resolve("person.jpg")); // Blocks staged photo installation after old files moved.
        String backup = createArchive("photo-failure.zip", "personnel", true,
                mapOf("personnel", BackupService.MODULE_TABLES.get("personnel")), "photos/person.jpg", new byte[]{9});
        assertThrows(IllegalStateException.class, () -> service.restore(backup, Arrays.asList("personnel")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE id=9", Integer.class));
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(photos.resolve("original.jpg")));
        assertTrue(Files.isDirectory(photos.resolve("person.jpg")));
        try (java.util.stream.Stream<Path> files = Files.list(dataRoot)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith("restore-")));
        }
    }

    @Test
    void versionOneWithoutPhotoSnapshotKeepsExistingPersonnelPhotos() throws Exception {
        Path photos = Files.createDirectories(dataRoot.resolve("photos"));
        Files.write(photos.resolve("local.jpg"), new byte[]{5});
        jdbc.update("INSERT INTO personnel(id) VALUES(9)");
        String backup = createArchive(1, "version-one.zip", false,
                mapOf("personnel", BackupService.MODULE_TABLES.get("personnel")), null, null);
        service.restore(backup, Arrays.asList("personnel"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
        assertArrayEquals(new byte[]{5}, Files.readAllBytes(photos.resolve("local.jpg")));
    }

    @Test
    void legacyAssetRestoreWarnsAndKeepsLocalPhoto() throws Exception {
        Path root = Files.createDirectories(dataRoot.resolve("photos/vehicle"));
        Files.write(root.resolve("local.jpg"), new byte[]{5});
        String backup = createArchive("legacy-vehicle.zip", "vehicle", false,
                mapOf("vehicle", BackupService.MODULE_TABLES.get("vehicle")));
        Map<String, Object> result = service.restore(backup, Arrays.asList("vehicle"));
        assertFalse(((List<?>) result.get("warnings")).isEmpty());
        assertArrayEquals(new byte[]{5}, Files.readAllBytes(root.resolve("local.jpg")));
    }

    @Test
    void oversizedBackupPhotoIsRejectedWithoutLeavingPartialZip() throws Exception {
        Path photos = Files.createDirectories(dataRoot.resolve("photos"));
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(photos.resolve("huge.jpg").toFile(), "rw")) {
            file.setLength(100L * 1024 * 1024 + 1);
        }
        assertThrows(RuntimeException.class, () -> service.createBackup(Arrays.asList("personnel"), false));
        assertTrue(service.listBackups().isEmpty());
    }

    @Test
    void oversizedCompressedRestorePhotoIsRejectedBeforeDatabaseChanges() throws Exception {
        jdbc.update("INSERT INTO personnel(id) VALUES(9)");
        Path photos = Files.createDirectories(dataRoot.resolve("photos"));
        Files.write(photos.resolve("original.jpg"), new byte[]{7});
        Path backup = Files.createDirectories(dataRoot.resolve("backup")).resolve("oversized-photo.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(backup))) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("version", 2); meta.put("photoSnapshot", true);
            meta.put("modules", metadata(mapOf("personnel", BackupService.MODULE_TABLES.get("personnel"))));
            add(zip, "meta.json", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(meta));
            for (String table : BackupService.MODULE_TABLES.get("personnel")) {
                add(zip, "data/personnel/" + table + ".json", "[]".getBytes(StandardCharsets.UTF_8));
            }
            zip.putNextEntry(new ZipEntry("photos/huge.jpg"));
            byte[] zeros = new byte[8192];
            for (int i = 0; i < 12800; i++) { zip.write(zeros); }
            zip.write(0); zip.closeEntry();
        }
        assertThrows(IllegalArgumentException.class, () -> service.restore(backup.getFileName().toString(), Arrays.asList("personnel")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE id=9", Integer.class));
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(photos.resolve("original.jpg")));
    }

    @Test
    void commitFailureRestoresDatabaseAndInstalledPhotos() throws Exception {
        jdbc.update("INSERT INTO personnel(id) VALUES(9)");
        Path photos = Files.createDirectories(dataRoot.resolve("photos"));
        Files.write(photos.resolve("original.jpg"), new byte[]{7});
        String backup = createArchive("commit-failure.zip", "personnel", true,
                mapOf("personnel", BackupService.MODULE_TABLES.get("personnel")), "photos/person.jpg", new byte[]{9});
        java.util.concurrent.atomic.AtomicBoolean commitFailureInjected = new java.util.concurrent.atomic.AtomicBoolean();
        DataSourceTransactionManager manager = new DataSourceTransactionManager(jdbc.getDataSource()) {
            @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class) == 0) {
                    // Prove injection happens after attachment installation, not during the safety backup.
                    assertTrue(Files.isRegularFile(photos.resolve("person.jpg")));
                    assertFalse(Files.exists(photos.resolve("original.jpg")));
                    commitFailureInjected.set(true);
                    throw new org.springframework.transaction.TransactionSystemException("injected commit failure");
                }
                super.doCommit(status);
            }
        };
        manager.setRollbackOnCommitFailure(true);
        BackupService failing = new BackupService(jdbc, manager, dataRoot.toFile());
        org.springframework.transaction.TransactionSystemException failure = assertThrows(
                org.springframework.transaction.TransactionSystemException.class,
                () -> failing.restore(backup, Arrays.asList("personnel")));
        assertEquals("injected commit failure", failure.getMessage());
        assertTrue(commitFailureInjected.get());
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE id=9", Integer.class));
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(photos.resolve("original.jpg")));
        assertFalse(Files.exists(photos.resolve("person.jpg")));
        try (java.util.stream.Stream<Path> files = Files.list(dataRoot)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith("restore-")));
        }
    }

    @Test
    void backupTablesShareOneSqliteSnapshotDuringConcurrentWrite() throws Exception {
        jdbc.execute("PRAGMA journal_mode=WAL");
        jdbc.update("INSERT INTO personnel(id) VALUES(1)");
        jdbc.update("INSERT INTO department(id) VALUES(1)");
        JdbcTemplate snapshotJdbc = new JdbcTemplate(jdbc.getDataSource()) {
            private boolean changed;
            @Override public List<Map<String, Object>> queryForList(String sql) {
                List<Map<String, Object>> rows = super.queryForList(sql);
                if (!changed && "SELECT * FROM personnel".equals(sql)) {
                    changed = true;
                    // A dedicated thread gets a distinct connection instead of joining this read snapshot.
                    java.util.concurrent.CompletableFuture.runAsync(() ->
                            jdbc.update("INSERT INTO department(id) VALUES(2)")).join();
                }
                return rows;
            }
        };
        BackupService snapshotService = new BackupService(snapshotJdbc,
                new DataSourceTransactionManager(jdbc.getDataSource()), dataRoot.toFile());
        String backup = (String) snapshotService.createBackup(Arrays.asList("personnel", "department"), false).get("name");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM department", Integer.class));
        try (ZipFile zip = new ZipFile(dataRoot.resolve("backup").resolve(backup).toFile());
             java.io.InputStream in = zip.getInputStream(zip.getEntry("data/department/department.json"))) {
            List<?> rows = new com.fasterxml.jackson.databind.ObjectMapper().readValue(in, List.class);
            assertEquals(1, rows.size());
        }
    }

    @Test
    void configuredDataDirectoryOwnsBackupSettingsAndAutoScheduler() {
        BackupService configured = new BackupService(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()), dataRoot.toString());
        BackupService.Settings settings = new BackupService.Settings();
        settings.enabled = true;
        settings.modules = Arrays.asList("department");
        configured.saveSettings(settings);
        assertTrue(Files.isRegularFile(dataRoot.resolve("backup-settings.json")));
        configured.runAutoIfDue();
        assertEquals(1, configured.listBackups().size());
        assertTrue(configured.loadSettings().autoLastRun > 0);
        configured.runAutoIfDue();
        assertEquals(1, configured.listBackups().size());
    }

    @Test
    void itemLoanModuleRoundTripsAllColumnsWithoutPersonnelDependencies() throws Exception {
        jdbc.update("INSERT INTO department(id) VALUES(7)");
        jdbc.update("INSERT INTO material(id) VALUES(8)");
        jdbc.update("INSERT INTO item_loan(id,item_name,specification,quantity,unit,borrower_name,borrower_id_card," +
                "borrower_phone,borrower_unit,department_id,borrower_migrated,borrow_date,expected_return_date," +
                "actual_return_date,return_condition,return_remark,remark,created_at,updated_at) " +
                "VALUES(10,'对讲机','A型',2,'台','外部姓名','外部证件','外部电话','手填单位',7,1,'2026-10-01','2026-10-20','','','','借出备注','创建时间','更新时间')");
        jdbc.update("INSERT INTO item_loan(id,item_name,borrow_date,actual_return_date,returned,return_condition,return_remark,department_id,borrower_migrated) " +
                "VALUES(11,'工具箱','2026-09-01','2026-09-09',1,'完好','验收备注',7,1)");
        jdbc.update("UPDATE item_loan SET handler_id=999,handler_name='独立经手人原名',handler_id_card='独立经手人证件' WHERE id=10");
        List<Map<String, Object>> expected = jdbc.queryForList("SELECT * FROM item_loan ORDER BY id");
        String backup = (String) service.createBackup(Arrays.asList("item-loans"), false).get("name");
        try (ZipFile zip = new ZipFile(dataRoot.resolve("backup").resolve(backup).toFile())) {
            assertNotNull(zip.getEntry("data/item-loans/item_loan.json"));
            assertNull(zip.getEntry("data/personnel/personnel.json"));
            assertNull(zip.getEntry("data/material/material.json"));
            Map<?, ?> meta = new com.fasterxml.jackson.databind.ObjectMapper().readValue(zip.getInputStream(zip.getEntry("meta.json")), Map.class);
            Map<?, ?> modules = (Map<?, ?>) meta.get("modules");
            assertEquals(1, modules.size());
            assertEquals(2, ((Map<?, ?>) modules.get("item-loans")).get("item_loan"));
        }
        jdbc.update("DELETE FROM item_loan");
        Map<String, Object> restored = service.restore(backup, Arrays.asList("item-loans"));
        assertEquals(expected, jdbc.queryForList("SELECT * FROM item_loan ORDER BY id"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM material WHERE id=8", Integer.class));
        assertEquals(Boolean.FALSE, restored.get("securityStateRestored"));
    }

    @Test
    void handlerSnapshotRestoresBeforePersonnelAndLaterPersonnelRestoreCannotRewriteIt() {
        legacyBorrowerSchema();
        jdbc.update("INSERT INTO personnel(id,name) VALUES(9,'现人员姓名不是快照名')");
        jdbc.update("INSERT INTO item_loan(id,item_name,borrow_date,actual_return_date,returned,borrower_migrated,handler_id,handler_name,handler_id_card) " +
                "VALUES(10,'经手人独立快照','2024-02-29','2024-03-01',0,1,9,'历史经手人快照名','历史经手人证件')");
        List<Map<String,Object>> expected = jdbc.queryForList("SELECT * FROM item_loan");
        String backup = (String) service.createBackup(Arrays.asList("item-loans", "personnel"), false).get("name");
        jdbc.update("DELETE FROM item_loan");
        jdbc.update("DELETE FROM personnel");
        service.restore(backup, Arrays.asList("item-loans"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
        assertEquals(expected, jdbc.queryForList("SELECT * FROM item_loan"));
        service.restore(backup, Arrays.asList("personnel"));
        assertEquals("现人员姓名不是快照名", jdbc.queryForObject("SELECT name FROM personnel WHERE id=9", String.class));
        assertEquals(expected, jdbc.queryForList("SELECT * FROM item_loan"));
    }

    @Test
    void loanOwnerIsPreservedOnlyWhenMatchingAccountSnapshotIsRestored() throws Exception {
        jdbc.update("INSERT INTO admin_user(id,username,password_hash,parent_id,enabled) VALUES(5,'owner','hash',0,1)");
        jdbc.update("INSERT INTO item_loan(id,item_name,borrow_date,department_id,owner_user_id,borrower_migrated) " +
                "VALUES(10,'所有者记录','2026-10-01',999,5,1)");
        String paired = (String) service.createBackup(Arrays.asList("item-loans", "dict"), false).get("name");
        jdbc.update("UPDATE item_loan SET owner_user_id=0");
        jdbc.update("DELETE FROM admin_user");
        service.restore(paired, Arrays.asList("item-loans", "dict"));
        assertEquals(5L, jdbc.queryForObject("SELECT owner_user_id FROM item_loan WHERE id=10", Long.class));
        assertEquals("owner", jdbc.queryForObject("SELECT username FROM admin_user WHERE id=5", String.class));

        Map<String,List<Map<String,Object>>> data = new LinkedHashMap<>();
        data.put("item_loan", Arrays.asList(row("id",11,"item_name","无账号映射","borrow_date","2026-10-02",
                "department_id",999,"borrower_migrated",1,"borrower_unit","不能作为归属",
                "registration_department","也不能作为归属","owner_user_id",5)));
        String unpaired = archiveRows("unpaired-owner.zip", Arrays.asList("item-loans"), data);
        service.restore(unpaired, Arrays.asList("item-loans"));
        Map<String,Object> adminOnly = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=11");
        assertEquals(0L, ((Number) adminOnly.get("owner_user_id")).longValue());
        assertEquals(999L, ((Number) adminOnly.get("department_id")).longValue());
        assertEquals("不能作为归属", adminOnly.get("borrower_unit"));
        assertEquals("也不能作为归属", adminOnly.get("registration_department"));
    }

    @Test
    void itemLoanOnlyRestoreDoesNotRequireTheHistoricalDepartmentToExist() {
        jdbc.update("INSERT INTO department(id) VALUES(7)");
        jdbc.update("INSERT INTO item_loan(id,item_name,borrow_date,department_id,borrower_migrated) VALUES(10,'原记录','2026-10-01',7,1)");
        String backup = (String) service.createBackup(Arrays.asList("item-loans"), false).get("name");
        jdbc.update("DELETE FROM item_loan");
        jdbc.update("DELETE FROM department");
        jdbc.update("INSERT INTO department(id) VALUES(8)");
        service.restore(backup, Arrays.asList("item-loans"));
        assertEquals(7L, jdbc.queryForObject("SELECT department_id FROM item_loan WHERE id=10", Long.class));
        assertEquals(8L, jdbc.queryForObject("SELECT id FROM department", Long.class));
    }

    @Test
    void convertedIncompleteLegacyLoansCanRoundTripForAdminWithoutInventingDepartmentScope() {
        jdbc.update("INSERT INTO item_loan(id,item_name,personnel_id,borrow_date,borrower_name,borrower_unit) " +
                "VALUES(10,'找不到人员也不能丢',99,'2026-10-01','旧姓名','保留单位')");
        String backup = (String) service.createBackup(Arrays.asList("item-loans"), false).get("name");
        jdbc.update("DELETE FROM item_loan");
        service.restore(backup, Arrays.asList("item-loans"));
        Map<String,Object> restored = jdbc.queryForMap("SELECT * FROM item_loan");
        assertEquals(0, ((Number) restored.get("department_id")).intValue());
        assertEquals(0, ((Number) restored.get("personnel_id")).intValue());
        assertEquals(1, ((Number) restored.get("borrower_migrated")).intValue());
        assertEquals("旧姓名", restored.get("borrower_name"));
        assertEquals("保留单位", restored.get("borrower_unit"));
        String newBackup = (String) service.createBackup(Arrays.asList("item-loans"), false).get("name");
        jdbc.update("DELETE FROM item_loan");
        service.restore(newBackup, Arrays.asList("item-loans"));
        assertEquals(restored, jdbc.queryForMap("SELECT * FROM item_loan"));
        com.szjx.personnel.server.auth.DataScope.set(new com.szjx.personnel.server.auth.DataScope.Context(
                2L,"child",false,new java.util.HashSet<Long>(Arrays.asList(7L))));
        try {
            assertThrows(com.szjx.personnel.server.auth.ForbiddenException.class,
                    () -> new com.szjx.personnel.server.dao.ItemLoanDao(jdbc).findById(10L, java.time.LocalDate.now()));
        } finally { com.szjx.personnel.server.auth.DataScope.clear(); }
    }

    @Test
    void departmentAndItemLoansCanRestoreTogetherOrExplicitlyClearBothModules() throws Exception {
        jdbc.update("INSERT INTO department(id) VALUES(7)");
        jdbc.update("INSERT INTO item_loan(id,item_name,borrow_date,department_id,borrower_migrated) VALUES(10,'原记录','2026-10-01',7,1)");
        String backup = (String) service.createBackup(Arrays.asList("department", "item-loans"), false).get("name");
        jdbc.update("DELETE FROM item_loan");
        jdbc.update("DELETE FROM department");
        jdbc.update("INSERT INTO department(id) VALUES(8)");
        jdbc.update("INSERT INTO item_loan(id,item_name,borrow_date,department_id,borrower_migrated) VALUES(20,'本地记录','2026-10-02',8,1)");
        service.restore(backup, Arrays.asList("item-loans", "department"));
        assertEquals(7L, jdbc.queryForObject("SELECT id FROM department", Long.class));
        assertEquals(7L, jdbc.queryForObject("SELECT department_id FROM item_loan WHERE id=10", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT personnel_id FROM item_loan WHERE id=10", Long.class));
        assertTrue(jdbc.queryForList("PRAGMA foreign_key_check").isEmpty());

        Map<String, List<String>> modules = mapOf("department", BackupService.MODULE_TABLES.get("department"));
        modules.put("item-loans", Arrays.asList("item_loan"));
        String empty = createArchive("empty-department-loans.zip", "department", false, modules);
        service.restore(empty, Arrays.asList("department", "item-loans"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM department", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM item_loan", Integer.class));
    }

    @Test
    void legacyPersonnelBackupWithoutItemLoansNeverModifiesLocalIndependentLoans() throws Exception {
        jdbc.update("INSERT INTO personnel(id) VALUES(1)");
        String backup = createLegacyPersonnelArchive("legacy-personnel-with-owner.zip", jdbc.queryForList("SELECT * FROM personnel"));
        jdbc.update("INSERT INTO personnel(id) VALUES(2)");
        jdbc.update("INSERT INTO item_loan(id,item_name,personnel_id,borrow_date) VALUES(10,'保留未归还',1,'2026-10-01')");
        jdbc.update("INSERT INTO item_loan(id,item_name,personnel_id,borrow_date,actual_return_date,return_condition) " +
                "VALUES(11,'保留已归还',1,'2026-09-01','2026-09-09','完好')");
        List<Map<String, Object>> local = jdbc.queryForList("SELECT * FROM item_loan ORDER BY id");
        service.restore(backup, Arrays.asList("personnel"));
        assertEquals(local, jdbc.queryForList("SELECT * FROM item_loan ORDER BY id"));
        assertEquals(1L, jdbc.queryForObject("SELECT id FROM personnel", Long.class));
        assertThrows(IllegalArgumentException.class, () -> service.restore(backup, Arrays.asList("personnel", "item-loans")));
        assertEquals(local, jdbc.queryForList("SELECT * FROM item_loan ORDER BY id"));
    }

    @Test
    void personnelRestoreMayRemoveAllPersonnelWithoutChangingUnselectedLoans() throws Exception {
        jdbc.update("INSERT INTO personnel(id) VALUES(1)");
        jdbc.update("INSERT INTO item_loan(id,item_name,personnel_id,handler_id,handler_name,borrow_date,actual_return_date) " +
                "VALUES(10,'旧personnel_id也不再是依赖',1,1,'恢复人员不应改写的经手人','2026-09-01','2026-09-09')");
        String backup = createArchive("legacy-personnel-empty.zip", "personnel", false,
                mapOf("personnel", BackupService.MODULE_TABLES.get("personnel")));
        List<Map<String, Object>> local = jdbc.queryForList("SELECT * FROM item_loan");
        service.restore(backup, Arrays.asList("personnel"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
        assertEquals(local, jdbc.queryForList("SELECT * FROM item_loan"));
    }

    @Test
    void departmentRestoreDoesNotDependOnUnselectedHistoricalLoans() throws Exception {
        jdbc.update("INSERT INTO department(id) VALUES(7)");
        jdbc.update("INSERT INTO item_loan(id,item_name,borrow_date,actual_return_date,department_id,borrower_migrated) " +
                "VALUES(10,'已归还仍需保留归属','2026-09-01','2026-09-09',7,1)");
        String backup = createArchive("department-without-registered.zip", "department", false,
                mapOf("department", BackupService.MODULE_TABLES.get("department")));
        service.restore(backup, Arrays.asList("department"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM department", Integer.class));
        assertEquals(7L, jdbc.queryForObject("SELECT department_id FROM item_loan WHERE id=10", Long.class));
    }

    @Test
    void departmentRestoreDoesNotRecheckLoanRowsWrittenConcurrently() {
        jdbc.update("INSERT INTO department(id) VALUES(7)");
        String backup = (String) service.createBackup(Arrays.asList("department"), false).get("name");
        jdbc.update("INSERT INTO department(id) VALUES(8)");
        JdbcTemplate concurrentJdbc = new JdbcTemplate(jdbc.getDataSource()) {
            @Override public int update(String sql) {
                if ("UPDATE sys_setting SET svalue=svalue WHERE 1=0".equals(sql)) {
                    java.util.concurrent.CompletableFuture.runAsync(() -> jdbc.update(
                            "INSERT INTO item_loan(id,item_name,borrow_date,department_id,borrower_migrated) VALUES(10,'并发新借还','2026-10-02',8,1)")).join();
                }
                return super.update(sql);
            }
        };
        BackupService concurrent = new BackupService(concurrentJdbc,
                new DataSourceTransactionManager(jdbc.getDataSource()), dataRoot.toFile());
        concurrent.restore(backup, Arrays.asList("department"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM department", Integer.class));
        assertEquals(8L, jdbc.queryForObject("SELECT department_id FROM item_loan WHERE id=10", Long.class));
    }

    @Test
    void legacyLoanArchiveUsesRestoredPersonnelAndDepartmentsNotOverwrittenLocalRows() throws Exception {
        legacyBorrowerSchema();
        jdbc.update("INSERT INTO department(id,name) VALUES(8,'本地旧单位')");
        jdbc.update("INSERT INTO personnel(id,name,id_card,phone,department_id) VALUES(1,'本地旧姓名','本地旧证件','本地旧电话',8)");
        Map<String,List<Map<String,Object>>> data = new LinkedHashMap<>();
        data.put("personnel", Arrays.asList(row("id",1,"name","备份姓名","id_card","备份证件","phone","备份电话","department_id",7)));
        data.put("department", Arrays.asList(row("id",7,"name","备份单位")));
        data.put("item_loan", Arrays.asList(row("id",42,"item_name","旧包物品","personnel_id",1,"borrow_date","2024-02-29",
                "expected_return_date","2024-03-02","actual_return_date","2024-03-01","return_condition","完好","return_remark","验收","created_at","创建","updated_at","更新")));
        String backup = archiveRows("legacy-all-loans.zip", Arrays.asList("item-loans","personnel","department"), data);
        service.restore(backup, Arrays.asList("item-loans","department","personnel"));
        Map<String,Object> restored = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=42");
        assertEquals(0L, ((Number) restored.get("handler_id")).longValue());
        assertEquals("", restored.get("handler_name"));
        assertEquals("", restored.get("handler_id_card"));
        assertEquals("备份姓名", restored.get("borrower_name"));
        assertEquals("备份证件", restored.get("borrower_id_card"));
        assertEquals("备份电话", restored.get("borrower_phone"));
        assertEquals("备份单位", restored.get("borrower_unit"));
        assertEquals(7L, ((Number) restored.get("department_id")).longValue());
        assertEquals(0L, ((Number) restored.get("personnel_id")).longValue());
        assertEquals("2024-02-29", restored.get("borrow_date"));
        assertEquals("2024-03-02", restored.get("expected_return_date"));
        assertEquals("2024-03-01", restored.get("actual_return_date"));
        assertEquals("完好", restored.get("return_condition"));
        assertEquals("验收", restored.get("return_remark"));
        assertEquals(1, ((Number) restored.get("returned")).intValue());
        assertEquals("创建", restored.get("created_at"));
        assertEquals("更新", restored.get("updated_at"));
        jdbc.update("UPDATE personnel SET name='人员后续改名',phone='人员后续电话'");
        String convertedBackup = (String) service.createBackup(Arrays.asList("item-loans"), false).get("name");
        jdbc.update("DELETE FROM item_loan");
        service.restore(convertedBackup, Arrays.asList("item-loans"));
        assertEquals(restored, jdbc.queryForMap("SELECT * FROM item_loan WHERE id=42"));
    }

    @Test
    void legacyZipJsonReturnStatesMigrateOnlyWhenMissingAndSurviveRepeatedRestoreAndBackup() throws Exception {
        List<Map<String,Object>> loans = new ArrayList<>();
        List<Integer> states = new ArrayList<>();
        for (int marker : new int[]{0, 1}) {
            for (Object actualDate : new Object[]{null, "", "2024-03-01"}) {
                for (Integer explicit : new Integer[]{null, 0, 1}) {
                    Map<String,Object> loan = row("id",loans.size()+1,"item_name","兼容状态","personnel_id",0,
                            "borrower_migrated",marker,"borrow_date","2024-02-29","expected_return_date","2024-03-02",
                            "actual_return_date",actualDate,"return_condition","完好","return_remark","验收",
                            "remark","原备注","created_at","创建","updated_at","更新");
                    if (explicit != null) loan.put("returned", explicit);
                    loans.add(loan);
                    states.add(explicit != null ? explicit : actualDate == null || "".equals(actualDate) ? 0 : 1);
                }
            }
        }
        Map<String,List<Map<String,Object>>> data = new LinkedHashMap<>();
        data.put("item_loan", loans);
        String backup = archiveRows("legacy-return-states.zip", Arrays.asList("item-loans"), data);
        List<Map<String,Object>> first = null;
        for (int attempt=0;attempt<2;attempt++) {
            service.restore(backup, Arrays.asList("item-loans"));
            assertEquals(states,jdbc.queryForList("SELECT returned FROM item_loan ORDER BY id",Integer.class));
            for (Map<String,Object> loan : loans) {
                Map<String,Object> restored = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=?",loan.get("id"));
                for (String field : new String[]{"borrow_date","expected_return_date","actual_return_date","return_condition",
                        "return_remark","remark","created_at","updated_at"}) {
                    assertEquals(loan.get(field),restored.get(field),field + " for id=" + loan.get("id"));
                }
                assertEquals(1,((Number)restored.get("borrower_migrated")).intValue());
                assertEquals(0L,((Number)restored.get("owner_user_id")).longValue());
                assertEquals(0L,((Number)restored.get("handler_id")).longValue());
                assertEquals("",restored.get("handler_name"));
                assertEquals("",restored.get("handler_id_card"));
            }
            List<Map<String,Object>> restored = jdbc.queryForList("SELECT * FROM item_loan ORDER BY id");
            if (first == null) first = restored;
            else assertEquals(first,restored);
        }
        String convertedBackup = (String) service.createBackup(Arrays.asList("item-loans"), false).get("name");
        jdbc.update("DELETE FROM item_loan");
        service.restore(convertedBackup, Arrays.asList("item-loans"));
        assertEquals(first,jdbc.queryForList("SELECT * FROM item_loan ORDER BY id"));
    }

    @Test
    void missingPostRestorePersonnelNeverLeaksSoonToBeOverwrittenLocalBorrowerText() throws Exception {
        legacyBorrowerSchema();
        jdbc.update("INSERT INTO department(id,name) VALUES(7,'本地单位')");
        jdbc.update("INSERT INTO personnel(id,name,id_card,phone,department_id) VALUES(1,'不可借用的本地姓名','本地证件','本地电话',7)");
        Map<String,List<Map<String,Object>>> data = new LinkedHashMap<>();
        data.put("personnel", java.util.Collections.emptyList());
        data.put("item_loan", Arrays.asList(row("id",42,"item_name","必须保留的旧包记录","personnel_id",1,"borrow_date","2024-02-29")));
        String backup = archiveRows("legacy-missing-person.zip", Arrays.asList("personnel","item-loans"), data);
        service.restore(backup, Arrays.asList("item-loans","personnel"));
        Map<String,Object> restored = jdbc.queryForMap("SELECT * FROM item_loan");
        assertEquals("", restored.get("borrower_name"));
        assertEquals("", restored.get("borrower_id_card"));
        assertEquals("", restored.get("borrower_phone"));
        assertEquals(0, ((Number) restored.get("department_id")).intValue());
        assertEquals(1, ((Number) restored.get("borrower_migrated")).intValue());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
    }

    @Test
    void legacyLoansAreReconvertedAgainstWriteTransactionPersonnelNotStalePreflightCopies() throws Exception {
        legacyBorrowerSchema();
        jdbc.update("INSERT INTO department(id,name) VALUES(7,'预检单位'),(8,'事务单位')");
        jdbc.update("INSERT INTO personnel(id,name,department_id) VALUES(1,'预检姓名',7)");
        Map<String,List<Map<String,Object>>> data = new LinkedHashMap<>();
        data.put("item_loan", Arrays.asList(row("id",42,"item_name","旧包记录","personnel_id",1,"borrow_date","2024-02-29")));
        String backup = archiveRows("legacy-concurrent-source.zip", Arrays.asList("item-loans"), data);
        JdbcTemplate concurrentJdbc = new JdbcTemplate(jdbc.getDataSource()) {
            @Override public int update(String sql) {
                if ("UPDATE sys_setting SET svalue=svalue WHERE 1=0".equals(sql)) {
                    java.util.concurrent.CompletableFuture.runAsync(() -> jdbc.update(
                            "UPDATE personnel SET name='事务姓名',department_id=8 WHERE id=1")).join();
                }
                return super.update(sql);
            }
        };
        BackupService concurrent = new BackupService(concurrentJdbc,
                new DataSourceTransactionManager(jdbc.getDataSource()), dataRoot.toFile());
        concurrent.restore(backup, Arrays.asList("item-loans"));
        assertEquals("事务姓名", jdbc.queryForObject("SELECT borrower_name FROM item_loan", String.class));
        assertEquals("事务单位", jdbc.queryForObject("SELECT borrower_unit FROM item_loan", String.class));
        assertEquals(8, jdbc.queryForObject("SELECT department_id FROM item_loan", Integer.class));
    }

    @Test
    void legacyDepartmentIdsAreCompatibilityDataNotRestoreDependencies() throws Exception {
        int index = 0;
        for (Object department : Arrays.asList(-1, 999, "not-an-id")) {
            Map<String,List<Map<String,Object>>> data = new LinkedHashMap<>();
            data.put("item_loan", Arrays.asList(row("id",42,"item_name","历史兼容记录","personnel_id",0,
                    "borrow_date","2024-02-29","borrower_migrated",1,"department_id",department)));
            String backup = archiveRows("legacy-department-" + index++ + ".zip", Arrays.asList("item-loans"), data);
            service.restore(backup, Arrays.asList("item-loans"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM item_loan", Integer.class));
            assertEquals(0L, jdbc.queryForObject("SELECT owner_user_id FROM item_loan WHERE id=42", Long.class));
            jdbc.update("DELETE FROM item_loan");
        }
    }

    @Test
    void oldIndependentArchiveSupplementsRegistrationFromRestoredDepartmentWithoutChangingManualBorrower() throws Exception {
        legacyBorrowerSchema();
        jdbc.update("INSERT INTO department(id,name) VALUES(7,'即将被覆盖的本地单位')");
        Map<String,List<Map<String,Object>>> data = new LinkedHashMap<>();
        data.put("department", Arrays.asList(row("id",7,"name","恢复后的管理单位")));
        data.put("item_loan", Arrays.asList(row("id",42,"item_name","上版自由借用人记录","personnel_id",0,
                "borrower_name","手填姓名","borrower_id_card","000X","borrower_phone","+44 001","borrower_unit","外单位",
                "borrow_date","2024-02-29","department_id",7,"borrower_migrated",1,"created_at","创建","updated_at","更新")));
        String backup = archiveRows("old-independent-loans.zip", Arrays.asList("department","item-loans"), data);
        service.restore(backup, Arrays.asList("item-loans","department"));
        Map<String,Object> restored = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=42");
        assertEquals("恢复后的管理单位", restored.get("registration_department"));
        for (Map.Entry<String,Object> entry : data.get("item_loan").get(0).entrySet()) {
            assertEquals(String.valueOf(entry.getValue()), String.valueOf(restored.get(entry.getKey())), entry.getKey());
        }
        for (String explicit : new String[]{"外单位自由登记", ""}) {
            data.get("item_loan").get(0).put("registration_department", explicit);
            String next = archiveRows("explicit-registration-" + service.listBackups().size() + ".zip", Arrays.asList("department","item-loans"), data);
            service.restore(next, Arrays.asList("item-loans","department"));
            assertEquals(explicit, jdbc.queryForObject("SELECT registration_department FROM item_loan WHERE id=42", String.class));
        }
    }

    @Test
    void missingRegistrationInOldIndependentArchiveIsRecomputedInsideWriteTransaction() throws Exception {
        legacyBorrowerSchema();
        jdbc.update("INSERT INTO department(id,name) VALUES(7,'预检名称')");
        Map<String,List<Map<String,Object>>> data = new LinkedHashMap<>();
        data.put("item_loan", Arrays.asList(row("id",42,"item_name","上版记录","personnel_id",0,"borrower_name","手填人",
                "borrow_date","2024-02-29","department_id",7,"borrower_migrated",1)));
        String backup = archiveRows("old-independent-concurrent.zip", Arrays.asList("item-loans"), data);
        JdbcTemplate concurrentJdbc = new JdbcTemplate(jdbc.getDataSource()) {
            @Override public int update(String sql) {
                if ("UPDATE sys_setting SET svalue=svalue WHERE 1=0".equals(sql)) {
                    java.util.concurrent.CompletableFuture.runAsync(() -> jdbc.update("UPDATE department SET name='事务名称' WHERE id=7")).join();
                }
                return super.update(sql);
            }
        };
        new BackupService(concurrentJdbc, new DataSourceTransactionManager(jdbc.getDataSource()), dataRoot.toFile())
                .restore(backup, Arrays.asList("item-loans"));
        assertEquals("事务名称", jdbc.queryForObject("SELECT registration_department FROM item_loan WHERE id=42", String.class));
        assertEquals("手填人", jdbc.queryForObject("SELECT borrower_name FROM item_loan WHERE id=42", String.class));
    }

    @Test
    void doubleExcellenceZipUploadAndRestoreRoundTripEveryActualColumn() throws Exception {
        jdbc.update("INSERT INTO personnel(id) VALUES(1)");
        jdbc.execute("ALTER TABLE double_excellence_record ADD COLUMN future_note TEXT DEFAULT ''");
        jdbc.update("INSERT INTO double_excellence_record(id,personnel_id,year,quarter,rating,rating_date,created_at,updated_at,future_note) " +
                "VALUES(10,1,2026,3,'好','2026-09-30','2026-09-30 08:00:00','2026-10-01 09:00:00','实际schema扩展列')");
        List<Map<String,Object>> expected = jdbc.queryForList("SELECT * FROM double_excellence_record ORDER BY id");
        String backup = (String) service.createBackup(Arrays.asList("double-excellence"), false).get("name");
        try (ZipFile zip = new ZipFile(service.getBackupFile(backup))) {
            Map<?,?> meta = new com.fasterxml.jackson.databind.ObjectMapper().readValue(zip.getInputStream(zip.getEntry("meta.json")), Map.class);
            assertEquals(4, meta.get("version"));
            assertEquals(java.util.Collections.singletonMap("double-excellence", java.util.Collections.singletonMap("double_excellence_record", 1)), meta.get("modules"));
            assertNull(zip.getEntry("data/personnel/personnel.json"));
            List<?> rows = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    zip.getInputStream(zip.getEntry("data/double-excellence/double_excellence_record.json")), List.class);
            assertEquals(expected, rows);
            assertFalse(((Map<?,?>) rows.get(0)).containsKey("name"));
            assertFalse(((Map<?,?>) rows.get(0)).containsKey("id_card"));
        }
        jdbc.update("UPDATE double_excellence_record SET rating='一般',future_note='本地变更'");
        List<Map<String,Object>> beforeUpload = jdbc.queryForList("SELECT * FROM double_excellence_record ORDER BY id");
        byte[] bytes = Files.readAllBytes(service.getBackupFile(backup).toPath());
        String uploaded = (String) service.upload(new org.springframework.mock.web.MockMultipartFile(
                "file", "评比.zip", "application/zip", bytes)).get("name");
        assertEquals(beforeUpload, jdbc.queryForList("SELECT * FROM double_excellence_record ORDER BY id"));
        service.restore(uploaded, Arrays.asList("double-excellence"));
        assertEquals(expected, jdbc.queryForList("SELECT * FROM double_excellence_record ORDER BY id"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
    }

    @Test
    void doubleExcellenceOnlyRestoreRejectsMissingOrInvalidPersonnelAndKeepsOldData() throws Exception {
        jdbc.update("INSERT INTO personnel(id) VALUES(1)");
        insertDoubleExcellence(10, 1);
        List<Map<String,Object>> local = jdbc.queryForList("SELECT * FROM double_excellence_record");
        int index = 0;
        for (Object owner : Arrays.asList(99, 0, -1, "", "not-an-id")) {
            Map<String,List<Map<String,Object>>> data = new LinkedHashMap<>();
            data.put("double_excellence_record", Arrays.asList(row("id",20,"personnel_id",owner,"year",2026,
                    "quarter",3,"rating","中","rating_date","2026-09-30")));
            String backup = archiveRows("invalid-excellence-person-" + index++ + ".zip", Arrays.asList("double-excellence"), data);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> service.restore(backup, Arrays.asList("double-excellence")));
            assertTrue(failure.getMessage().contains("double_excellence_record.personnel_id"));
            assertEquals(local, jdbc.queryForList("SELECT * FROM double_excellence_record"));
            assertEquals(1L, jdbc.queryForObject("SELECT id FROM personnel", Long.class));
        }
    }

    @Test
    void personnelOnlyRestoreRejectsOrphaningUnselectedDoubleExcellence() {
        jdbc.update("INSERT INTO personnel(id) VALUES(1)");
        String backup = (String) service.createBackup(Arrays.asList("personnel"), false).get("name");
        jdbc.update("INSERT INTO personnel(id) VALUES(2)");
        insertDoubleExcellence(10, 2);
        List<Map<String,Object>> local = jdbc.queryForList("SELECT * FROM double_excellence_record");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.restore(backup, Arrays.asList("personnel")));
        assertTrue(failure.getMessage().contains("double_excellence_record.personnel_id=2"));
        assertEquals(Arrays.asList(1L,2L), jdbc.queryForList("SELECT id FROM personnel ORDER BY id", Long.class));
        assertEquals(local, jdbc.queryForList("SELECT * FROM double_excellence_record"));
    }

    @Test
    void personnelRestoreRechecksConcurrentPreservedDoubleExcellenceInsideWriteTransaction() {
        jdbc.update("INSERT INTO personnel(id) VALUES(1)");
        String backup = (String) service.createBackup(Arrays.asList("personnel"), false).get("name");
        jdbc.update("INSERT INTO personnel(id) VALUES(2)");
        jdbc.update("INSERT INTO sys_setting(skey,svalue) VALUES('keep','old')");
        JdbcTemplate concurrentJdbc = new JdbcTemplate(jdbc.getDataSource()) {
            @Override public int update(String sql) {
                if ("UPDATE sys_setting SET svalue=svalue WHERE 1=0".equals(sql)) {
                    java.util.concurrent.CompletableFuture.runAsync(() -> insertDoubleExcellence(10, 2)).join();
                }
                return super.update(sql);
            }
        };
        BackupService concurrent = new BackupService(concurrentJdbc,
                new DataSourceTransactionManager(jdbc.getDataSource()), dataRoot.toFile());
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> concurrent.restore(backup, Arrays.asList("personnel")));
        assertTrue(failure.getMessage().contains("double_excellence_record.personnel_id=2"));
        assertEquals(Arrays.asList(1L,2L), jdbc.queryForList("SELECT id FROM personnel ORDER BY id", Long.class));
        assertEquals(2L, jdbc.queryForObject("SELECT personnel_id FROM double_excellence_record", Long.class));
        assertEquals("old", jdbc.queryForObject("SELECT svalue FROM sys_setting WHERE skey='keep'", String.class));
    }

    @Test
    void combinedPersonnelAndDoubleExcellenceRestoreUsesIncomingPersonnelAndAllowsExplicitClear() throws Exception {
        jdbc.update("INSERT INTO personnel(id) VALUES(1)");
        insertDoubleExcellence(10, 1);
        String backup = (String) service.createBackup(Arrays.asList("personnel", "double-excellence"), false).get("name");
        List<Map<String,Object>> expected = jdbc.queryForList("SELECT * FROM double_excellence_record");
        jdbc.update("DELETE FROM double_excellence_record");
        jdbc.update("DELETE FROM personnel");
        jdbc.update("INSERT INTO personnel(id) VALUES(2)");
        insertDoubleExcellence(20, 2);
        service.restore(backup, Arrays.asList("double-excellence", "personnel"));
        assertEquals(1L, jdbc.queryForObject("SELECT id FROM personnel", Long.class));
        assertEquals(expected, jdbc.queryForList("SELECT * FROM double_excellence_record"));
        String empty = archiveRows("empty-personnel-excellence.zip", Arrays.asList("personnel", "double-excellence"), new LinkedHashMap<>());
        service.restore(empty, Arrays.asList("personnel", "double-excellence"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM double_excellence_record", Integer.class));
    }

    @Test
    void conflictingDoubleExcellenceBusinessKeyRollsBackRestoredPersonnelAndOldRatings() throws Exception {
        jdbc.update("INSERT INTO personnel(id) VALUES(1)");
        insertDoubleExcellence(10, 1);
        List<Map<String,Object>> local = jdbc.queryForList("SELECT * FROM double_excellence_record");
        Map<String,List<Map<String,Object>>> data = new LinkedHashMap<>();
        data.put("personnel", Arrays.asList(row("id",2)));
        data.put("double_excellence_record", Arrays.asList(
                row("id",20,"personnel_id",2,"year",2026,"quarter",3,"rating","好","rating_date","2026-09-30"),
                row("id",21,"personnel_id",2,"year",2026,"quarter",3,"rating","中","rating_date","2026-09-29")));
        String backup = archiveRows("duplicate-excellence-quarter.zip", Arrays.asList("personnel", "double-excellence"), data);
        org.springframework.dao.DataAccessException failure = assertThrows(org.springframework.dao.DataAccessException.class,
                () -> service.restore(backup, Arrays.asList("personnel", "double-excellence")));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("UNIQUE"));
        assertEquals(1L, jdbc.queryForObject("SELECT id FROM personnel", Long.class));
        assertEquals(local, jdbc.queryForList("SELECT * FROM double_excellence_record"));
    }

    @Test
    void oldTwelveModuleUploadRestoresOnlyAvailableIntersectionAndPreservesLocalDoubleExcellence() throws Exception {
        jdbc.update("INSERT INTO personnel(id) VALUES(1)");
        List<String> legacyModules = BackupService.allModuleKeys();
        legacyModules.remove("double-excellence");
        legacyModules.remove("annual-appraisal");
        legacyModules.remove("absence");
        legacyModules.remove("organization"); // 还原双争/年度/在外/组织建设/消息审批加入前的12模块旧备份
        legacyModules.remove("message");
        String backup = (String) service.createBackup(legacyModules, false).get("name");
        byte[] bytes = Files.readAllBytes(service.getBackupFile(backup).toPath());
        insertDoubleExcellence(10, 1);
        jdbc.update("INSERT INTO equipment(id) VALUES(99)");
        List<Map<String,Object>> local = jdbc.queryForList("SELECT * FROM double_excellence_record");
        Map<String,Object> uploaded = service.upload(new org.springframework.mock.web.MockMultipartFile("file", "old12.zip", "application/zip", bytes));
        Map<?,?> available = (Map<?,?>) uploaded.get("modules");
        assertEquals(12, available.size());
        assertFalse(available.containsKey("double-excellence"));
        String name = (String) uploaded.get("name");
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> service.restore(name, BackupService.allModuleKeys()));
        assertTrue(missing.getMessage().contains("所选模块不在备份中"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
        assertEquals(local, jdbc.queryForList("SELECT * FROM double_excellence_record"));
        List<String> selected = BackupService.allModuleKeys();
        selected.retainAll(available.keySet());
        service.restore(name, selected);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
        assertEquals(local, jdbc.queryForList("SELECT * FROM double_excellence_record"));
    }

    @Test
    void fullBackupCoversActualThirtyFiveBusinessTablesIncludingHistoricalFitnessStandard() throws Exception {
        // 本隔离库模拟升级前33张实际业务表+两评比表,不是把静态模块表数当生产库表数。
        jdbc.execute("CREATE TABLE fitness_standard(id INTEGER PRIMARY KEY, standard_text TEXT)");
        jdbc.update("INSERT INTO fitness_standard(id,standard_text) VALUES(1,'历史标准')");
        List<String> actual = jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", String.class);
        assertEquals(42, actual.size());
        String backup = (String) service.createBackup(BackupService.allModuleKeys(), false).get("name");
        try (ZipFile zip = new ZipFile(service.getBackupFile(backup))) {
            Map<?,?> meta = new com.fasterxml.jackson.databind.ObjectMapper().readValue(zip.getInputStream(zip.getEntry("meta.json")), Map.class);
            Map<?,?> modules = (Map<?,?>) meta.get("modules");
            assertEquals(17, modules.size());
            java.util.Set<String> archived = new java.util.HashSet<>();
            for (Map.Entry<?,?> module : modules.entrySet()) {
                for (Object table : ((Map<?,?>) module.getValue()).keySet()) {
                    archived.add(String.valueOf(table));
                    assertNotNull(zip.getEntry("data/" + module.getKey() + "/" + table + ".json"));
                }
            }
            assertEquals(new java.util.HashSet<>(actual), archived);
        }
        jdbc.update("UPDATE fitness_standard SET standard_text='本地'");
        service.restore(backup, BackupService.allModuleKeys());
        assertEquals("历史标准", jdbc.queryForObject("SELECT standard_text FROM fitness_standard WHERE id=1", String.class));
    }

    @Test
    void migratedOldAllSelectionAutomaticallyArchivesDoubleExcellenceOnNextDueRun() throws Exception {
        jdbc.update("INSERT INTO personnel(id) VALUES(1)");
        insertDoubleExcellence(10, 1);
        List<String> oldAll = BackupService.allModuleKeys();
        oldAll.remove("double-excellence");
        oldAll.remove("annual-appraisal");
        oldAll.remove("absence");
        oldAll.remove("organization"); // 双争/年度/在外/组织建设/消息审批加入前的历史默认清单
        oldAll.remove("message");
        Files.createDirectories(dataRoot);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Files.write(dataRoot.resolve("backup-settings.json"), mapper.writeValueAsBytes(row("enabled",true,
                "intervalHours",24,"keepCount",10,"modules",oldAll,"autoLastRun",0,"location","原路径设置")));
        service.runAutoIfDue();
        assertEquals(1, service.listBackups().size());
        Map<String,Object> archive = service.listBackups().get(0);
        assertEquals(Boolean.TRUE, archive.get("auto"));
        assertTrue(((Map<?,?>) archive.get("modules")).containsKey("double-excellence"));
        try (ZipFile zip = new ZipFile(service.getBackupFile((String) archive.get("name")))) {
            assertNotNull(zip.getEntry("data/double-excellence/double_excellence_record.json"));
        }
        BackupService.Settings loaded = service.loadSettings();
        assertEquals(BackupService.allModuleKeys(), loaded.modules);
        assertTrue(loaded.autoLastRun > 0);
        assertEquals("原路径设置", loaded.getExtraSettings().get("location"));
    }

    private void insertDoubleExcellence(long id, long personnelId) {
        jdbc.update("INSERT INTO double_excellence_record(id,personnel_id,year,quarter,rating,rating_date,created_at,updated_at) " +
                "VALUES(?,?,2026,3,'好','2026-09-30','2026-09-30 08:00:00','2026-10-01 09:00:00')", id, personnelId);
    }

    private void legacyBorrowerSchema() {
        jdbc.execute("ALTER TABLE personnel ADD COLUMN name TEXT");
        jdbc.execute("ALTER TABLE personnel ADD COLUMN id_card TEXT");
        jdbc.execute("ALTER TABLE personnel ADD COLUMN phone TEXT");
        jdbc.execute("ALTER TABLE personnel ADD COLUMN department_id INTEGER");
        jdbc.execute("ALTER TABLE department ADD COLUMN name TEXT");
    }

    private static Map<String,Object> row(Object... fields) {
        Map<String,Object> row = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 2) row.put((String) fields[i], fields[i + 1]);
        return row;
    }

    private String archiveRows(String name, List<String> modules, Map<String,List<Map<String,Object>>> data) throws Exception {
        Map<String,Object> declared = new LinkedHashMap<>();
        for (String module : modules) {
            Map<String,Object> counts = new LinkedHashMap<>();
            for (String table : BackupService.MODULE_TABLES.get(module)) counts.put(table, data.getOrDefault(table, java.util.Collections.emptyList()).size());
            declared.put(module, counts);
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Path file = Files.createDirectories(dataRoot.resolve("backup")).resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            add(zip, "meta.json", mapper.writeValueAsBytes(row("version",2,"modules",declared)));
            for (String module : modules) {
                for (String table : BackupService.MODULE_TABLES.get(module)) {
                    add(zip, "data/" + module + "/" + table + ".json", mapper.writeValueAsBytes(data.getOrDefault(table, java.util.Collections.emptyList())));
                }
            }
        }
        return name;
    }

    @Test
    void newAndNullModuleSettingsIncludeItemLoansButLegacyExplicitSelectionsAreNotExpanded() throws Exception {
        assertTrue(new BackupService.Settings().modules.contains("item-loans"));
        assertTrue(service.loadSettings().modules.contains("item-loans"));
        List<String> legacyAll = BackupService.allModuleKeys();
        legacyAll.remove("item-loans");
        for (List<String> selection : Arrays.asList(legacyAll, Arrays.asList("personnel", "material"), java.util.Collections.<String>emptyList())) {
            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("enabled", false);
            settings.put("intervalHours", 72);
            settings.put("keepCount", 17);
            settings.put("autoLastRun", 123456L);
            settings.put("modules", selection);
            Files.createDirectories(dataRoot);
            byte[] original = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(settings);
            Files.write(dataRoot.resolve("backup-settings.json"), original);
            BackupService.Settings loaded = service.loadSettings();
            assertEquals(selection, loaded.modules);
            assertFalse(loaded.enabled);
            assertEquals(72, loaded.intervalHours);
            assertEquals(17, loaded.keepCount);
            assertEquals(123456L, loaded.autoLastRun);
            assertArrayEquals(original, Files.readAllBytes(dataRoot.resolve("backup-settings.json")));
        }
        Files.write(dataRoot.resolve("backup-settings.json"), "{\"modules\":null}".getBytes(StandardCharsets.UTF_8));
        assertTrue(service.loadSettings().modules.contains("item-loans"));
    }

    @Test
    void automaticBackupsIncludeItemLoansAfterExplicitOptInWithoutChangingOtherSettings() throws Exception {
        BackupService.Settings settings = new BackupService.Settings();
        settings.enabled = true;
        settings.intervalHours = 72;
        settings.keepCount = 17;
        settings.modules = new ArrayList<>(Arrays.asList("department"));
        service.saveSettings(settings);
        service.runAutoIfDue();
        assertEquals(Arrays.asList("department"), service.loadSettings().modules);
        assertFalse(((Map<?, ?>) service.listBackups().get(0).get("modules")).containsKey("item-loans"));

        settings = service.loadSettings();
        settings.modules.add("item-loans");
        settings.autoLastRun = 0;
        service.saveSettings(settings);
        service.runAutoIfDue();
        BackupService.Settings loaded = service.loadSettings();
        assertTrue(loaded.enabled);
        assertEquals(72, loaded.intervalHours);
        assertEquals(17, loaded.keepCount);
        assertEquals(Arrays.asList("department", "item-loans"), loaded.modules);
        assertTrue(loaded.autoLastRun > 0);
        boolean included = false;
        for (Map<String, Object> archive : service.listBackups()) {
            if (((Map<?, ?>) archive.get("modules")).containsKey("item-loans")) {
                included = true;
                try (ZipFile zip = new ZipFile(dataRoot.resolve("backup").resolve((String) archive.get("name")).toFile())) {
                    assertNotNull(zip.getEntry("data/item-loans/item_loan.json"));
                }
            }
        }
        assertTrue(included);
    }

    private String createLegacyPersonnelArchive(String name, List<Map<String, Object>> personnel) throws Exception {
        Map<String, Object> modules = metadata(mapOf("personnel", BackupService.MODULE_TABLES.get("personnel")));
        ((Map<String, Object>) modules.get("personnel")).put("personnel", personnel.size());
        Path file = Files.createDirectories(dataRoot.resolve("backup")).resolve(name);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("version", 2);
            meta.put("modules", modules);
            add(zip, "meta.json", mapper.writeValueAsBytes(meta));
            for (String table : BackupService.MODULE_TABLES.get("personnel")) {
                add(zip, "data/personnel/" + table + ".json", mapper.writeValueAsBytes(
                        "personnel".equals(table) ? personnel : java.util.Collections.emptyList()));
            }
        }
        return name;
    }

    private String createArchive(String name, String module, boolean photos, Map<String, List<String>> modules,
                                 String photoEntry, byte[] photo) throws Exception {
        return createArchive(2, name, photos, modules, photoEntry, photo);
    }

    private String createArchive(int version, String name, boolean photos, Map<String, List<String>> modules,
                                 String photoEntry, byte[] photo) throws Exception {
        Path dir = Files.createDirectories(dataRoot.resolve("backup"));
        Path file = dir.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("version", version);
            meta.put("photoSnapshot", photos);
            meta.put("modules", metadata(modules));
            add(zip, "meta.json", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(meta));
            for (Map.Entry<String, List<String>> moduleTables : modules.entrySet()) {
                for (String table : moduleTables.getValue()) {
                    add(zip, "data/" + moduleTables.getKey() + "/" + table + ".json", "[]".getBytes(StandardCharsets.UTF_8));
                }
            }
            if (photoEntry != null) add(zip, photoEntry, photo);
        }
        return name;
    }

    private String createArchive(String name, String module, boolean photos, Map<String, List<String>> modules) throws Exception {
        return createArchive(name, module, photos, modules, null, null);
    }

    private static Map<String, Object> metadata(Map<String, List<String>> modules) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> module : modules.entrySet()) {
            Map<String, Object> tables = new LinkedHashMap<>();
            for (String table : module.getValue()) tables.put(table, 0);
            result.put(module.getKey(), tables);
        }
        return result;
    }

    private static Map<String, List<String>> mapOf(String module, String[] tables) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put(module, new ArrayList<>(Arrays.asList(tables)));
        return result;
    }

    private static Map<String, List<String>> mapOf(String module, List<String> tables) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put(module, tables);
        return result;
    }

    private static void add(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
