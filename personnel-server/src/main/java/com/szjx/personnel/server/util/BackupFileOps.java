package com.szjx.personnel.server.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 备份恢复文件工具:跨文件系统移动降级与受限目录清理(自 BackupService 下沉,静态无状态)
 */
public final class BackupFileOps {

    private static final Logger log = LoggerFactory.getLogger(BackupFileOps.class);

    private BackupFileOps() {
    }

    /** 优先原子移动;被系统拒绝时降级为复制+删除(跨盘/受限目录)。 */
    public static void moveFileCompat(java.nio.file.Path src, java.nio.file.Path dst) throws java.io.IOException {
        try {
            java.nio.file.Files.move(src, dst);
            return;
        } catch (java.nio.file.FileSystemException e) {
            if (!moveFallbackAllowed(e)) { throw e; }
        }
        copyAndDelete(src, dst);
    }

    /** 仅对"操作被系统拒绝"类错误降级;目标冲突/源缺失等照常抛出,避免误覆盖。 */
    public static boolean moveFallbackAllowed(java.nio.file.FileSystemException e) {
        if (e instanceof java.nio.file.AccessDeniedException) { return true; }
        String reason = e.getReason() == null ? "" : e.getReason();
        String low = reason.toLowerCase(java.util.Locale.ROOT);
        return low.contains("not permitted") || low.contains("denied") || reason.contains("不允许") || reason.contains("权限");
    }

    /** 复制后删除源;若删除也被拒绝,源文件留存于临时目录(不影响恢复正确性,照片仍可事后手动找回)。 */
    public static void copyAndDelete(java.nio.file.Path src, java.nio.file.Path dst) throws java.io.IOException {
        java.nio.file.Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try {
            java.nio.file.Files.delete(src);
        } catch (java.io.IOException e) {
            log.warn("受限目录下已复制 {} 至 {},但源文件删除失败(留存于原临时目录)", src, dst);
        }
    }

    /** 路径上任一环节为符号链接即拒绝(备份/恢复不接受软链)。 */
    public static void rejectSymlink(java.nio.file.Path path) throws java.io.IOException {
        java.nio.file.Path current = path.toAbsolutePath();
        while (current != null) {
            if (java.nio.file.Files.isSymbolicLink(current)) { throw new IllegalArgumentException("不允许符号链接路径"); }
            current = current.getParent();
        }
    }

    /** 后序删除目录树;失败仅告警(暂存目录清理,不阻断主流程)。 */
    public static void deleteTree(java.nio.file.Path path) {
        if (!java.nio.file.Files.exists(path)) { return; }
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(path)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { java.nio.file.Files.deleteIfExists(p); } catch (Exception e) { log.warn("暂存清理失败: {}", p); }
            });
        } catch (Exception e) { log.warn("暂存目录清理失败: {}", path); }
    }
}
