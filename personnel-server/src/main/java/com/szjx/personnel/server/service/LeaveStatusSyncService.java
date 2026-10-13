package com.szjx.personnel.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 休假 ⇆ 人员状态 联动:
 *   休假区间覆盖当天 → 状态自动置为"休假"(标记 auto_leave=1);
 *   区间结束(不再覆盖当天) → 自动恢复"在职"并清标记。
 * 只处理系统标记过的人员(auto_leave=1),手工设置的其他状态不受影响。
 * 启动时执行一次,之后每小时核对一次 —— 即使休假那天没人打开系统,状态也会被补正。
 */
@Service
public class LeaveStatusSyncService {

    private static final Logger log = LoggerFactory.getLogger(LeaveStatusSyncService.class);

    private final JdbcTemplate jdbc;
    private ScheduledExecutorService timer;

    public LeaveStatusSyncService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void start() {
        try {
            sync();
        } catch (Exception e) {
            log.warn("休假状态初始同步失败: {}", e.getMessage());
        }
        timer = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "leave-status-sync");
                t.setDaemon(true);
                return t;
            }
        });
        timer.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    sync();
                } catch (Exception e) {
                    log.warn("休假状态同步失败: {}", e.getMessage());
                }
            }
        }, 1, 1, TimeUnit.HOURS);
    }

    @PreDestroy
    public void stop() {
        if (timer != null) {
            timer.shutdownNow();
        }
    }

    /** 执行一次同步;返回 [本次进入休假人数, 本次恢复在职人数] */
    public synchronized int[] sync() {
        String today = java.time.LocalDate.now().toString();
        int goOn = jdbc.update(
                "UPDATE personnel SET status='休假', auto_leave=1, updated_at=datetime('now','localtime') " +
                "WHERE auto_leave=0 AND id IN (" +
                "SELECT personnel_id FROM leave_record WHERE start_date <= ? AND end_date >= ?)",
                today, today);
        int back = jdbc.update(
                "UPDATE personnel SET status='在职', auto_leave=0, updated_at=datetime('now','localtime') " +
                "WHERE auto_leave=1 AND id NOT IN (" +
                "SELECT personnel_id FROM leave_record WHERE start_date <= ? AND end_date >= ?)",
                today, today);
        if (goOn > 0 || back > 0) {
            log.info("休假状态联动:{} 人进入休假,{} 人恢复在职", goOn, back);
        }
        return new int[]{goOn, back};
    }
}
