package com.szjx.personnel.server.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 旧调用兼容入口。唯一周期任务是 BackupScheduler,所有设置及轮转由 BackupService 管理。
 * 不再创建第二个启动/每日定时线程,也不再绕过 enabled/modules。
 */
@Service
public class AutoBackupService {
    private final BackupService backupService;

    public AutoBackupService(BackupService backupService, LogService logService, JdbcTemplate jdbc) {
        this.backupService = backupService;
    }

    public void start() { /* 由 Spring BackupScheduler 统一调度。 */ }
    public void stop() { /* 无独立线程。 */ }
    void runBackup(String trigger) { backupService.runAutoIfDue(); }
}
