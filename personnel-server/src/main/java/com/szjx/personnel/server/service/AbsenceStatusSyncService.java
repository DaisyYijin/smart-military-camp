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
 * 在外登记 ⇆ 人员状态 联动(与休假联动互补,互不干涉):
 *   预计返回日期已过且未销假 → 记录按期返回,人员恢复"在位";
 *   人员无任何未归记录 → 恢复"在位";
 *   手工把状态改成非在外类状态 → 视为放弃自动管理,清除标记。
 * 启动时执行一次,之后每小时核对一次。
 */
@Service
public class AbsenceStatusSyncService {

    private static final Logger log = LoggerFactory.getLogger(AbsenceStatusSyncService.class);

    private final JdbcTemplate jdbc;
    private ScheduledExecutorService timer;

    public AbsenceStatusSyncService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void start() {
        try {
            sync();
        } catch (Exception e) {
            log.warn("在外状态初始同步失败: {}", e.getMessage());
        }
        timer = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "absence-status-sync");
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
                    log.warn("在外状态同步失败: {}", e.getMessage());
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

    /** 执行一次同步;启动/定时自调用由 TransactionTemplate 保证原子性。 */
    public synchronized int[] sync() {
        org.springframework.transaction.support.TransactionTemplate tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        return tx.execute(ignored -> syncWithinTransaction());
    }

    private int[] syncWithinTransaction() {
        // 1) 手工改状态 → 放弃自动管理(与休假联动的 auto_leave 机制一致)
        int abandoned = jdbc.update("UPDATE personnel SET auto_absence=0 WHERE auto_absence=1 AND COALESCE(status,'') NOT IN ('出差','培训','其他')");
        // 2) 预登记到期生效:离开日期已到且未归队 → 按事由变更状态(创建时未动状态,等真实离开日)
        int activated = jdbc.update(
                "UPDATE personnel SET status=(SELECT CASE a.reason WHEN '出差' THEN '出差' WHEN '学习' THEN '培训' ELSE '其他' END " +
                "FROM absence_record a WHERE a.personnel_id=personnel.id AND a.actual_return_date='' AND a.leave_date<=date('now','localtime') " +
                "ORDER BY a.id DESC LIMIT 1), auto_absence=1, updated_at=datetime('now','localtime') " +
                "WHERE auto_absence=2 AND EXISTS (SELECT 1 FROM absence_record a WHERE a.personnel_id=personnel.id " +
                "AND a.actual_return_date='' AND a.leave_date<=date('now','localtime'))");
        // 3) 自愈:管理中(auto=1)但仅有未来日期的未归记录 → 回退待生效并恢复在位(纠正被提前改状态的历史登记)
        int demoted = jdbc.update(
                "UPDATE personnel SET status='在位', auto_absence=2, updated_at=datetime('now','localtime') " +
                "WHERE auto_absence=1 AND NOT EXISTS (SELECT 1 FROM absence_record a WHERE a.personnel_id=personnel.id " +
                "AND a.actual_return_date='' AND a.leave_date<=date('now','localtime')) " +
                "AND EXISTS (SELECT 1 FROM absence_record a WHERE a.personnel_id=personnel.id " +
                "AND a.actual_return_date='' AND a.leave_date>date('now','localtime'))");
        // 4) 兜底:已无任何未归记录的在外人员(含预登记) → 恢复在位(正常由归队/删除端点处理,此处防直接改库等旁路)
        int restored = jdbc.update(
                "UPDATE personnel SET status='在位', auto_absence=0, updated_at=datetime('now','localtime') " +
                "WHERE auto_absence IN (1,2) AND NOT EXISTS (" +
                "SELECT 1 FROM absence_record a WHERE a.personnel_id=personnel.id AND a.actual_return_date='')");
        // 注:预计返回到期不再自动销假——人是否真回来系统无法得知,到期由列表显示"已超期"提醒,
        // 由「延期」按钮顺延预计日期或「归队」按钮销假(2026-11-03 需求调整)
        if (abandoned + activated + demoted + restored > 0) {
            log.info("在外状态联动:{} 人放弃管理,{} 人到期生效,{} 人回退待生效,{} 人恢复在位",
                    abandoned, activated, demoted, restored);
        }
        return new int[]{abandoned, activated, demoted, restored};
    }
}
