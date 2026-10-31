package com.szjx.personnel.server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 受限目录(拒绝 rename 但允许读写,如麒麟安全策略或 U 盘/网络盘挂载)下
 * 备份恢复照片替换的降级逻辑:恢复时"照片替换失败: 不允许的操作"即由此类环境触发。
 */
class BackupPhotoMoveCompatTest {

    @Test
    void fallbackOnlyForPermissionStyleRejections() {
        assertTrue(BackupService.moveFallbackAllowed(new AccessDeniedException("/x/a.jpg")));
        assertTrue(BackupService.moveFallbackAllowed(new FileSystemException("/x/a.jpg", null, "Operation not permitted")));
        assertTrue(BackupService.moveFallbackAllowed(new FileSystemException("/x/a.jpg", null, "不允许的操作")));
        assertTrue(BackupService.moveFallbackAllowed(new FileSystemException("/x/a.jpg", null, "权限不足")));
        // 目标冲突/跨设备等其他错误不降级:copy 会误覆盖或掩盖真实原因
        assertFalse(BackupService.moveFallbackAllowed(new FileAlreadyExistsException("/x/a.jpg")));
        assertFalse(BackupService.moveFallbackAllowed(new FileSystemException("/x/a.jpg", null, "Invalid cross-device link")));
        assertFalse(BackupService.moveFallbackAllowed(new FileSystemException("/x/a.jpg", null, null)));
    }

    @Test
    void copyAndDeleteReplacesTargetAndRemovesSource(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("a.jpg");
        Path dst = dir.resolve("b.jpg");
        Files.write(src, new byte[]{1, 2, 3});
        Files.write(dst, new byte[]{9});
        BackupService.copyAndDelete(src, dst);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(dst));
        assertFalse(Files.exists(src), "降级移动后源文件应被删除");
    }

    @Test
    void moveCompatKeepsFastRenamePathWhenAllowed(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("a.jpg");
        Path dst = dir.resolve("b.jpg");
        Files.write(src, new byte[]{7, 7});
        BackupService.moveFileCompat(src, dst);
        assertArrayEquals(new byte[]{7, 7}, Files.readAllBytes(dst));
        assertFalse(Files.exists(src), "正常环境仍走 rename 快路径");
    }
}
