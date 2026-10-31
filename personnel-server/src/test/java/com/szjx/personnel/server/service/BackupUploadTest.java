package com.szjx.personnel.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Only @TempDir paths and mock DB objects: archive validation cannot read or write a business DB. */
class BackupUploadTest {
    @TempDir Path temporary;
    BackupService service;
    JdbcTemplate jdbc;
    PlatformTransactionManager transactions;
    final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setup() {
        jdbc = mock(JdbcTemplate.class);
        transactions = mock(PlatformTransactionManager.class);
        service = new BackupService(jdbc, transactions, temporary.toFile());
    }

    @AfterEach void uploadNeverTouchesDatabaseAndLeavesNoPendingFiles() throws Exception {
        verifyNoInteractions(jdbc, transactions);
        try (java.util.stream.Stream<Path> files = Files.walk(temporary)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".backup-upload-")));
        }
    }

    @Test void repeatedOriginalNamesAndUserPathsNeverOverwriteOrChooseDestination() throws Exception {
        byte[] bytes = archive(entries(meta(4)));
        Map<String, Object> first = service.upload(file("same.zip", bytes));
        Map<String, Object> second = service.upload(file("same.zip", bytes));
        Map<String, Object> third = service.upload(file("C:\\outside\\..\\same.zip", bytes));
        assertEquals(3, service.listBackups().size());
        assertNotEquals(first.get("name"), second.get("name"));
        assertNotEquals(second.get("name"), third.get("name"));
        for (Map<String, Object> result : Arrays.asList(first, second, third)) {
            String name = (String) result.get("name");
            assertTrue(name.matches("upload_[a-f0-9-]+\\.zip"));
            assertArrayEquals(bytes, Files.readAllBytes(service.getBackupFile(name).toPath()));
            assertEquals(result, service.listBackups().stream().filter(row -> name.equals(row.get("name"))).findFirst().get());
            assertEquals("2026-10-18 12:00:00", result.get("createdText"));
            assertEquals(Collections.singletonMap("equipment", Collections.singletonMap("equipment", 0)), result.get("modules"));
        }
        assertFalse(Files.exists(temporary.resolve("same.zip")));
    }

    @Test void acceptsVersionsOneThroughFourWithHistoricalOptionalTables() throws Exception {
        for (int version = 1; version <= 4; version++) {
            Map<String, Object> meta = meta(version);
            Map<String, Object> modules = new LinkedHashMap<>();
            modules.put("dict", counts("sys_dict", "sys_setting", "holiday_calendar"));
            modules.put("personnel", counts("personnel", "position_history", "reward_punish", "child_info",
                    "family_member", "driver_license", "personal_card", "private_vehicle", "housing_info"));
            modules.put("vehicle", counts("vehicle"));
            Map<String, Object> fitness = counts("fitness_record", "fitness_matrix", "fitness_standard");
            if (version >= 2) fitness.put("sys_setting", 0);
            modules.put("fitness", fitness);
            if (version <= 2) {
                modules.put("leave", counts("leave_record"));
                modules.put("military", counts("military_assessment"));
            }
            meta.put("modules", modules);
            meta.put("photoModules", Collections.emptyList());
            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put("meta.json", mapper.writeValueAsBytes(meta));
            for (Map.Entry<String, Object> module : modules.entrySet()) {
                for (Object table : ((Map<?, ?>) module.getValue()).keySet()) {
                    entries.put("data/" + module.getKey() + "/" + table + ".json", utf8("[]"));
                }
            }
            assertEquals(version, service.upload(file("legacy.zip", archive(entries))).get("version"));
        }
    }

    @Test void emptyConfigManifestIsValidWithoutDictButNonemptyOrMalformedManifestsAreRejected() throws Exception {
        Map<String,Object> meta = meta(4);
        for (Object invalid : Arrays.asList(Collections.singletonList("config.json"), "[]", Collections.emptyMap())) {
            meta.put("configFiles", invalid);
            assertRejected(archive(entries(meta)));
        }
        meta.put("configFiles", Collections.emptyList());
        assertNotNull(service.upload(file("empty-config-list.zip", archive(entries(meta)))).get("name"));
    }

    @Test void doubleExcellenceUploadUsesExistingV4TableValidationAndCrcWithoutDatabaseAccess() throws Exception {
        Map<String,Object> meta = meta(4);
        meta.put("modules", Collections.singletonMap("double-excellence", Collections.singletonMap("double_excellence_record", 1)));
        meta.put("photoModules", Collections.emptyList());
        String path = "data/double-excellence/double_excellence_record.json";
        Map<String,byte[]> entries = new LinkedHashMap<>();
        entries.put("meta.json", mapper.writeValueAsBytes(meta));
        assertRejected(archive(entries)); // Declared table is required, even when nothing is restored yet.
        entries.put(path, utf8("[]"));
        assertRejected(archive(entries)); // Declared count still applies to the new module.
        entries.put(path, utf8("[{\"id\":10,\"personnel_id\":1,\"year\":2026,\"quarter\":3,\"rating\":\"好\"," +
                "\"rating_date\":\"2026-09-30\",\"created_at\":\"创建\",\"updated_at\":\"更新\"}]"));
        byte[] bytes = archive(entries);
        byte[] corrupt = bytes.clone();
        corrupt[centralOffset(corrupt, path) + 16] ^= 1;
        assertRejected(corrupt);
        Map<String,Object> result = service.upload(file("double-excellence.zip", bytes));
        assertEquals(4, result.get("version"));
        assertEquals(meta.get("modules"), result.get("modules"));
        assertArrayEquals(bytes, Files.readAllBytes(service.getBackupFile((String) result.get("name")).toPath()));
    }

    @Test void rejectsEmptyNonZipMissingMetaInvalidMetaAndUnsupportedVersions() throws Exception {
        assertRejected(new byte[0]);
        assertRejected(utf8("not a zip"));
        assertRejected(archive(Collections.singletonMap("other.json", utf8("{}"))));
        for (Object version : Arrays.asList(0, 5, 1.5, "4")) {
            Map<String, Object> meta = meta(4);
            meta.put("version", version);
            assertRejected(archive(entries(meta)));
        }
        Map<String, Object> missingVersion = meta(4);
        missingVersion.remove("version");
        assertRejected(archive(entries(missingVersion)));
        for (Object modules : Arrays.asList(Collections.emptyMap(), Collections.singletonMap("unknown", counts("unknown")),
                Collections.singletonMap("equipment", Collections.emptyMap()), Collections.singletonList("equipment"))) {
            Map<String, Object> meta = meta(4);
            meta.put("modules", modules);
            assertRejected(archive(entries(meta)));
        }
        for (String badMeta : Arrays.asList("null", "[]", "{", "{} {}", "{\"version\":1,\"version\":4}")) {
            Map<String, byte[]> entries = entries(meta(4));
            entries.put("meta.json", utf8(badMeta));
            assertRejected(archive(entries));
        }
    }

    @Test void rejectsMissingDeclaredTablesAndInvalidJsonRowsCountsOrTrailingContent() throws Exception {
        Map<String, byte[]> entries = entries(meta(4));
        entries.remove("data/equipment/equipment.json");
        assertRejected(archive(entries));
        for (String value : Arrays.asList("{}", "null", "[1]", "[null]", "[{}]", "[", "[] {}", "[] garbage")) {
            entries = entries(meta(4));
            entries.put("data/equipment/equipment.json", utf8(value));
            assertRejected(archive(entries));
        }
        for (Object count : Arrays.asList(-1, 0.5, "0")) {
            Map<String, Object> meta = meta(4);
            meta.put("modules", Collections.singletonMap("equipment", Collections.singletonMap("equipment", count)));
            assertRejected(archive(entries(meta)));
        }
    }

    @Test void invalidUploadsPreserveExistingBackupsAndOneRowTablesRejectEmptyOrDuplicateRows() throws Exception {
        Map<String, Object> meta = meta(4);
        meta.put("modules", Collections.singletonMap("equipment", Collections.singletonMap("equipment", 1)));
        for (String json : Arrays.asList("[]", "[{}]", "[{\"id\":1,\"id\":2}]")) {
            Map<String, byte[]> entries = entries(meta);
            entries.put("data/equipment/equipment.json", utf8(json));
            assertRejected(archive(entries));
        }
        byte[] valid = archive(entries(meta(4)));
        String name = (String) service.upload(file("backup.zip", valid)).get("name");
        assertThrows(IllegalArgumentException.class, () -> service.upload(file(name, utf8("invalid zip"))));
        assertEquals(1, service.listBackups().size());
        assertArrayEquals(valid, Files.readAllBytes(service.getBackupFile(name).toPath()));
    }

    @Test void rejectsTraversalUnknownNamespacesUnsafePhotosUndeclaredPhotosAndConfigs() throws Exception {
        for (String path : Arrays.asList("../outside", "/absolute", "C:/outside", "data/../outside", "data/./equipment/x",
                "data//equipment/x", "photos\\outside", "photos/equipment/nested/photo.jpg", "photos/trash/a.jpg",
                "photos/personnel/a.jpg", "photos/equipment/CON.jpg", "photos/material/a.jpg", "photos/personnel-trash/a.jpg",
                "data/equipment/unknown.json", "config/config.json", "unknown/", "photos/equipment/../outside")) {
            Map<String, byte[]> entries = entries(meta(4));
            entries.put(path, utf8("[]"));
            assertRejected(archive(entries));
        }
        assertFalse(Files.exists(temporary.resolve("outside")));
        Map<String, Object> meta = meta(4);
        meta.put("photoModules", Collections.emptyList());
        Map<String, byte[]> entries = entries(meta);
        entries.put("photos/equipment/a.jpg", new byte[]{1});
        assertRejected(archive(entries));
    }

    @Test void rejectsDuplicateEntryNamesAndCorruptCrcIncludingUnselectedPhoto() throws Exception {
        Map<String, byte[]> entries = entries(meta(4));
        entries.put("data/equipment/equipment.jsox", utf8("[]"));
        byte[] duplicate = archive(entries);
        replaceAscii(duplicate, "equipment.jsox", "equipment.json");
        assertRejected(duplicate);
        entries = entries(meta(4));
        entries.put("photos/equipment/a.jpg", new byte[]{9, 8, 7});
        byte[] corrupt = archive(entries);
        int central = centralOffset(corrupt, "photos/equipment/a.jpg");
        corrupt[central + 16] ^= 1; // Central CRC; ZipFile alone does not check it.
        assertRejected(corrupt);
    }

    @Test void metadataAndConfigHaveOneMiBLimitsAndEntryCountIsBounded() throws Exception {
        Map<String, byte[]> entries = entries(meta(4));
        entries.put("meta.json", new byte[1024 * 1024 + 1]);
        assertRejected(archive(entries));
        Map<String, Object> meta = meta(4);
        meta.put("modules", Collections.singletonMap("dict", counts("sys_dict", "sys_setting", "holiday_calendar")));
        meta.put("photoModules", Collections.emptyList());
        meta.put("configFiles", Collections.singletonList("config.json"));
        entries = new LinkedHashMap<>();
        entries.put("meta.json", mapper.writeValueAsBytes(meta));
        for (String table : Arrays.asList("sys_dict", "sys_setting", "holiday_calendar")) entries.put("data/dict/" + table + ".json", utf8("[]"));
        entries.put("config/config.json", new byte[1024 * 1024 + 1]);
        assertRejected(archive(entries));
        entries = entries(meta(4));
        for (int i = 0; i < 9999; i++) entries.put("photos/equipment/" + i + ".jpg", new byte[0]);
        assertRejected(archive(entries)); // 10001 including meta and table.
    }

    @Test void inflatedPhotoAndDataAreBoundedEvenIfCentralSizeLies() throws Exception {
        for (String path : Arrays.asList("photos/equipment/huge.jpg", "data/equipment/equipment.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> entry : entries(meta(4)).entrySet()) {
                    if (!path.equals(entry.getKey())) add(zip, entry.getKey(), entry.getValue());
                }
                zip.putNextEntry(new ZipEntry(path));
                byte[] spaces = new byte[8192];
                Arrays.fill(spaces, (byte) ' ');
                for (int i = 0; i < 12800; i++) zip.write(spaces); // Exactly 100MiB.
                zip.write(utf8("[]"));
                zip.closeEntry();
            }
            byte[] archive = bytes.toByteArray();
            assertTrue(archive.length < 1024 * 1024);
            assertRejected(archive);
            int offset = centralOffset(archive, path);
            Arrays.fill(archive, offset + 24, offset + 28, (byte) 0); // Forge uncompressed size to bypass header precheck.
            assertRejected(archive); // Actual stream limit still catches the expansion.
        }
    }

    @Test void aggregateInflatedLimitUsesActualBytesNotCentralSizes() throws Exception {
        Path zipPath = temporary.resolve("limit-test.zip");
        Files.write(zipPath, archive(Collections.singletonMap("photos/equipment/a.jpg", new byte[]{1, 2})));
        java.lang.reflect.Method checked = BackupArchiveGuard.class.getDeclaredMethod("checkedEntry", ZipFile.class, ZipEntry.class, long.class, long[].class);
        checked.setAccessible(true);
        try (ZipFile zip = new ZipFile(zipPath.toFile());
             InputStream in = (InputStream) checked.invoke(null, zip, zip.getEntry("photos/equipment/a.jpg"),
                     BackupService.MAX_UPLOAD_BYTES, new long[]{2L * 1024 * 1024 * 1024 - 1})) {
            assertThrows(IllegalArgumentException.class, () -> in.read(new byte[8192]));
        }
    }

    @Test void reportedOversizeIsRejectedBeforeOpeningStream() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(BackupService.MAX_UPLOAD_BYTES + 1);
        assertThrows(IllegalArgumentException.class, () -> service.upload(file));
        verify(file, never()).getInputStream();
        assertTrue(service.listBackups().isEmpty());
    }

    @Test void streamingUploadLimitClosesInputAndRemovesPartialFileEvenWhenSizeLies() throws Exception {
        AtomicLong bytesRead = new AtomicLong();
        AtomicBoolean closed = new AtomicBoolean();
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(new InputStream() {
            @Override public int read() { bytesRead.incrementAndGet(); return 0; }
            @Override public int read(byte[] buffer, int off, int len) {
                Arrays.fill(buffer, off, off + len, (byte) 0);
                bytesRead.addAndGet(len);
                return len;
            }
            @Override public void close() { closed.set(true); }
        });
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> service.upload(file));
        assertTrue(failure.getMessage().contains("100MB"));
        assertEquals(BackupService.MAX_UPLOAD_BYTES + 1, bytesRead.get());
        assertTrue(closed.get());
        assertTrue(service.listBackups().isEmpty());
    }

    @Test void failedReadAndInvalidArchiveCloseStreamsAndRemovePartialFiles() throws Exception {
        for (boolean ioFailure : Arrays.asList(true, false)) {
            AtomicBoolean closed = new AtomicBoolean();
            MultipartFile file = mock(MultipartFile.class);
            when(file.getSize()).thenReturn(1L);
            when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[]{1}) {
                @Override public synchronized int read(byte[] b, int off, int len) {
                    if (ioFailure) throw new IllegalStateException("interrupted upload");
                    return super.read(b, off, len);
                }
                @Override public void close() throws IOException { closed.set(true); super.close(); }
            });
            assertThrows(RuntimeException.class, () -> service.upload(file));
            assertTrue(closed.get());
            assertTrue(service.listBackups().isEmpty());
        }
    }

    private Map<String, Object> meta(int version) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("version", version);
        meta.put("createdText", "2026-10-18 12:00:00");
        meta.put("modules", Collections.singletonMap("equipment", counts("equipment")));
        if (version >= 3) meta.put("photoModules", Collections.singletonList("equipment"));
        return meta;
    }

    private Map<String, Object> counts(String... tables) {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String table : tables) counts.put(table, 0);
        return counts;
    }

    private Map<String, byte[]> entries(Map<String, Object> meta) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("meta.json", mapper.writeValueAsBytes(meta));
        entries.put("data/equipment/equipment.json", utf8("[]"));
        return entries;
    }

    private byte[] archive(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) add(zip, entry.getKey(), entry.getValue());
        }
        return bytes.toByteArray();
    }

    private void add(ZipOutputStream zip, String path, byte[] value) throws IOException {
        zip.putNextEntry(new ZipEntry(path)); zip.write(value); zip.closeEntry();
    }

    private MockMultipartFile file(String name, byte[] bytes) { return new MockMultipartFile("file", name, "application/zip", bytes); }
    private byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private void assertRejected(byte[] bytes) {
        assertThrows(IllegalArgumentException.class, () -> service.upload(file("backup.zip", bytes)));
        assertTrue(service.listBackups().isEmpty());
    }

    private void replaceAscii(byte[] bytes, String from, String to) {
        byte[] needle = utf8(from), replacement = utf8(to);
        assertEquals(needle.length, replacement.length);
        for (int i = 0; i <= bytes.length - needle.length; i++) {
            if (matches(bytes, i, needle)) System.arraycopy(replacement, 0, bytes, i, replacement.length);
        }
    }

    private int centralOffset(byte[] bytes, String path) {
        byte[] name = utf8(path);
        for (int i = 0; i <= bytes.length - 46 - name.length; i++) {
            if (bytes[i] == 0x50 && bytes[i + 1] == 0x4b && bytes[i + 2] == 1 && bytes[i + 3] == 2 && matches(bytes, i + 46, name)) return i;
        }
        throw new AssertionError("missing central entry " + path);
    }

    private boolean matches(byte[] bytes, int offset, byte[] value) {
        for (int j = 0; j < value.length; j++) if (bytes[offset + j] != value[j]) return false;
        return true;
    }
}
