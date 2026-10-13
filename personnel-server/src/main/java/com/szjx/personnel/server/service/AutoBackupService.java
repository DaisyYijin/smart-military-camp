package com.szjx.personnel.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 自动定时备份:每天 03:00 全量备份到 data/backup(auto_ 前缀),保留最近 14 份;
 * 启动 30 秒后先补做一次,避免长期不开机错过当天备份。
 */
@Service
public class AutoBackupService {

    private static final Logger log = LoggerFactory.getLogger(AutoBackupService.class);
    private static final int KEEP = 14;

    private final BackupService backupService;
    private final com.szjx.personnel.server.service.LogService logService;
    private ScheduledExecutorService timer;

    public AutoBackupService(BackupService backupService, LogService logService) {
        this.backupService = backupService;
        this.logService = logService;
    }

    @PostConstruct
    public void start() {
        timer = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "auto-backup");
                t.setDaemon(true);
                return t;
            }
        });
        // 启动 30 秒后补做一次
        timer.schedule(new Runnable() {
            @Override
            public void run() {
                runBackup("启动补做");
            }
        }, 30, TimeUnit.SECONDS);
        // 此后每天 03:00
        long delayMs = Duration.between(LocalDateTime.now(),
                LocalDateTime.now().toLocalDate().atTime(LocalTime.of(3, 0)).plusDays(
                        LocalDateTime.now().toLocalTime().isAfter(LocalTime.of(3, 0)) ? 1 : 0)).toMillis();
        timer.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                runBackup("每日定时");
            }
        }, delayMs, TimeUnit.HOURS.toMillis(24), TimeUnit.MILLISECONDS);
        log.info("自动备份已启用:每天 03:00,保留最近 {} 份", KEEP);
    }

    @PreDestroy
    public void stop() {
        if (timer != null) {
            timer.shutdownNow();
        }
    }

    void runBackup(String trigger) {
        try {
            Map<String, Object> r = backupService.createBackup(BackupService.allModuleKeys(), true);
            log.info("自动备份完成({}): {} ", trigger, r.get("name"));
            try {
                logService.record("system", "自动备份", trigger + ":" + r.get("name"), "127.0.0.1");
            } catch (Exception ignore) {
                // 日志失败不影响备份
            }
            cleanOld();
        } catch (Exception e) {
            log.warn("自动备份失败: {}", e.getMessage());
        }
    }

    /** 只清理 auto_ 前缀的自动备份,保留最近 KEEP 份 */
    private void cleanOld() {
        File dir = new File("data", "backup");
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        File[] autos = {};
        int n = 0;
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith("auto_") && f.getName().endsWith(".zip")) {
                if (n == autos.length) {
                    autos = Arrays.copyOf(autos, n + 4);
                }
                autos[n++] = f;
            }
        }
        if (n <= KEEP) {
            return;
        }
        File[] all = Arrays.copyOf(autos, n);
        Arrays.sort(all, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return b.getName().compareTo(a.getName());   // 新的在前
            }
        });
        for (int i = KEEP; i < all.length; i++) {
            if (all[i].delete()) {
                log.info("自动备份轮转:删除 {}", all[i].getName());
            }
        }
    }
}
