package com.szjx.personnel.server.service;

import java.io.InputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 备份归档(ZIP)安全校验工具:路径合法性、声明文件存在性、限额与CRC校验读取、照片文件名校验。
 * 从 BackupService 拆出,上传校验与恢复流程共用;无实例状态,全部静态。
 */
final class BackupArchiveGuard {

    private BackupArchiveGuard() {
    }

    static final long MAX_PHOTO_BYTES = 100L * 1024 * 1024;
    static final long MAX_CONFIG_BYTES = 1024L * 1024;
    static final long MAX_META_BYTES = 1024L * 1024;
    static final long MAX_DATA_BYTES = 100L * 1024 * 1024;
    static final long MAX_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024;
    static final int MAX_ZIP_ENTRIES = 10000;

    static void checkZipPath(String path) {
        if (path.isEmpty() || path.contains("\\") || path.startsWith("/") || path.contains(":")) {
            throw new IllegalArgumentException("危险ZIP路径: " + path);
        }
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        for (String component : normalized.split("/", -1)) {
            if (component.isEmpty() || ".".equals(component) || "..".equals(component)) { throw new IllegalArgumentException("危险ZIP路径: " + path); }
        }
        for (int i = 0; i < path.length(); i++) {
            if (Character.isISOControl(path.charAt(i))) { throw new IllegalArgumentException("危险ZIP路径"); }
        }
    }

    static ZipEntry requireEntry(Map<String, ZipEntry> entries, String path) {
        ZipEntry entry = entries.get(path);
        if (entry == null || entry.isDirectory()) { throw new IllegalArgumentException("缺少声明文件: " + path); }
        return entry;
    }

    /** ZipFile does not check CRC itself. Always bound actual inflated bytes and explicitly verify at EOF. */
    static InputStream checkedEntry(ZipFile zip, ZipEntry entry, long limit, long[] expanded) throws java.io.IOException {
        if (entry.getSize() < 0 || entry.getSize() > limit) { throw new IllegalArgumentException("ZIP条目超过允许大小: " + entry.getName()); }
        return new java.io.FilterInputStream(zip.getInputStream(entry)) {
            private final java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            private long count;
            @Override public int read() throws java.io.IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
            }
            @Override public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
                if (length == 0) { return 0; }
                int bounded = (int) Math.min(length, Math.min(limit - count, MAX_EXPANDED_BYTES - expanded[0]) + 1);
                int n = in.read(bytes, offset, bounded);
                if (n == -1) {
                    if (count != entry.getSize() || crc.getValue() != entry.getCrc()) { throw new IllegalArgumentException("ZIP条目长度或CRC损坏: " + entry.getName()); }
                } else {
                    count += n; expanded[0] += n;
                    if (count > limit || expanded[0] > MAX_EXPANDED_BYTES) { throw new IllegalArgumentException("ZIP解压内容超过允许大小"); }
                    crc.update(bytes, offset, n);
                }
                return n;
            }
        };
    }

    static byte[] readLimited(InputStream in, long max) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > max) { throw new IllegalArgumentException("文件超过允许大小"); }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    static void safePhotoName(String name) {
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains(":")
                || name.endsWith(".") || name.endsWith(" ") || name.contains("..")
                || name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            throw new IllegalArgumentException("非法照片文件名: " + name);
        }
    }
}
