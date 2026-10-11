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

    public LeaveService(LeaveDao leaveDao, PersonnelDao personnelDao) {
        this.leaveDao = leaveDao;
        this.personnelDao = personnelDao;
    }

    public List<LeaveRecord> query(String keyword, int page, int size) {
        return leaveDao.query(keyword, page, size);
    }

    public long count(String keyword) {
        return leaveDao.count(keyword);
    }

    public LeaveRecord create(LeaveRecord r) {
        validate(r);
        long id = leaveDao.insert(r);
        return leaveDao.findById(id);
    }

    public LeaveRecord update(Long id, LeaveRecord r) {
        if (leaveDao.findById(id) == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        validate(r);
        r.setId(id);
        leaveDao.update(r);
        return leaveDao.findById(id);
    }

    public void delete(Long id) {
        leaveDao.delete(id);
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
        if (r.getDays() == null || r.getDays() <= 0) {
            throw new IllegalArgumentException("请填写休假天数");
        }
    }
}
