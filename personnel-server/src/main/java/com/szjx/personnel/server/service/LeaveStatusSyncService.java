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

    /** 执行一次同步；启动/定时自调用也由 TransactionTemplate 保证原子性。 */
    public synchronized int[] sync() {
        org.springframework.transaction.support.TransactionTemplate tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        return tx.execute(ignored -> syncWithinTransaction());
    }

    private int[] syncWithinTransaction() {
        String today = java.time.LocalDate.now().toString();
        // 人工切换培训/借调/离职等状态，取消自动标记，不得自动覆盖。
        jdbc.update("UPDATE personnel SET auto_leave=0, auto_leave_previous_status='' " +
                "WHERE auto_leave=1 AND IFNULL(status,'')<>'休假'");
        int goOn = jdbc.update(
                "UPDATE personnel SET auto_leave_previous_status=status, status='休假', auto_leave=1, updated_at=datetime('now','localtime') " +
                "WHERE IFNULL(auto_leave,0)=0 AND status IN ('在职','在位') AND EXISTS (" +
                "SELECT 1 FROM leave_record l WHERE l.personnel_id=personnel.id AND l.start_date <= ? AND l.end_date >= ?)",
                today, today);
        int back = jdbc.update(
                "UPDATE personnel SET status='在位', auto_leave=0, auto_leave_previous_status='', updated_at=datetime('now','localtime') " +
                "WHERE auto_leave=1 AND status='休假' AND NOT EXISTS (" +
                "SELECT 1 FROM leave_record l WHERE l.personnel_id=personnel.id AND l.start_date <= ? AND l.end_date >= ?)",
                today, today);
        if (goOn > 0 || back > 0) {
            log.info("休假状态联动:{} 人进入休假,{} 人恢复在职/在位", goOn, back);
        }
        return new int[]{goOn, back};
    }
}
