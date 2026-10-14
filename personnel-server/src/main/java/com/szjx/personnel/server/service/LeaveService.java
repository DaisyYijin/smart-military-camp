package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.LeaveDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.LeaveRecord;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 休假业务
 */
@Service
public class LeaveService {

    private final LeaveDao leaveDao;
    private final PersonnelDao personnelDao;
    private final LeaveStatusSyncService statusSync;

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public LeaveService(LeaveDao leaveDao, PersonnelDao personnelDao, LeaveStatusSyncService statusSync,
                        org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.leaveDao = leaveDao;
        this.personnelDao = personnelDao;
        this.statusSync = statusSync;
        this.jdbc = jdbc;
    }

    public List<LeaveRecord> query(String keyword, int page, int size) {
        return leaveDao.query(keyword, page, size);
    }

    public long count(String keyword) {
        return leaveDao.count(keyword);
    }

    @org.springframework.transaction.annotation.Transactional
    public LeaveRecord create(LeaveRecord r) {
        r.setId(null);
        validate(r);
        long id = leaveDao.insert(r);
        statusSync.sync();   // 休假覆盖当天时,人员状态立即联动
        return leaveDao.findById(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public LeaveRecord update(Long id, LeaveRecord r) {
        if (leaveDao.findById(id) == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        r.setId(id);
        validate(r);
        leaveDao.update(r);
        statusSync.sync();
        return leaveDao.findById(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(Long id) {
        leaveDao.delete(id);
        statusSync.sync();
    }

    private void validate(LeaveRecord r) {
        if (r.getPersonnelId() == null || r.getPersonnelId() <= 0) {
            throw new IllegalArgumentException("请选择休假人员");
        }
        if (personnelDao.findById(r.getPersonnelId()) == null) {
            throw new IllegalArgumentException("所选人员不存在");
        }
        if (!StringUtils.hasText(r.getStartDate()) || !StringUtils.hasText(r.getEndDate())) {
            throw new IllegalArgumentException("请填写开始和结束日期");
        }
        java.time.LocalDate start = parseDate(r.getStartDate());
        java.time.LocalDate end = parseDate(r.getEndDate());
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        r.setStartDate(start.toString());
        r.setEndDate(end.toString());
        if (r.getDays() == null) { throw new IllegalArgumentException("请填写休假天数"); }
        validateDays(r.getDays(), "休假天数");
        validateDays(r.getTravelDays(), "路途天数");
        validateDays(r.getHolidayDays(), "节假日天数");
        Long overlaps = jdbc.queryForObject("SELECT COUNT(*) FROM leave_record WHERE personnel_id=? " +
                        "AND start_date<=? AND end_date>=? AND (? IS NULL OR id<>?)", Long.class,
                r.getPersonnelId(), r.getEndDate(), r.getStartDate(), r.getId(), r.getId());
        if (overlaps != null && overlaps > 0) {
            throw new IllegalArgumentException("该人员已有重叠的休假区间");
        }
    }

    private java.time.LocalDate parseDate(String date) {
        String value = date.trim();
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException("日期格式应为yyyy-MM-dd");
        }
        try {
            return java.time.LocalDate.parse(value);
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("休假日期不存在");
        }
    }

    private void validateDays(Double days, String label) {
        if (days != null && (!Double.isFinite(days) || days < 0)) {
            throw new IllegalArgumentException(label + "必须为有限非负数");
        }
    }
}
