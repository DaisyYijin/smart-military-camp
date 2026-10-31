package com.szjx.personnel.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 备份超过上传恢复上限(100MB)时的告警文本边界:生成侧提示与上传侧限制保持一致 */
class BackupOversizeWarningTest {

    @Test
    void noWarningAtOrBelowUploadLimit() {
        assertNull(BackupService.oversizedWarning(0));
        assertNull(BackupService.oversizedWarning(1));
        assertNull(BackupService.oversizedWarning(BackupService.MAX_UPLOAD_BYTES),
                "恰好等于上限不应告警:上传入口允许不超过100MB");
    }

    @Test
    void warnsAboveUploadLimitWithMbFigures() {
        String warning = BackupService.oversizedWarning(BackupService.MAX_UPLOAD_BYTES + 1);
        assertNotNull(warning);
        assertTrue(warning.contains("上传恢复上限"), warning);
        assertTrue(warning.contains("100MB"), warning);
        assertTrue(warning.contains("文件拷贝"), "应提示改用文件拷贝迁移: " + warning);

        String justOver = BackupService.oversizedWarning(101L * 1024 * 1024);
        assertTrue(justOver.contains("101MB"), "提示应带实际大小: " + justOver);

        String huge = BackupService.oversizedWarning(250L * 1024 * 1024);
        assertTrue(huge.contains("250MB"));
    }
}
