package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.FitnessDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.FitnessRecord;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 体能业务
 */
@Service
public class FitnessService {

    private final FitnessDao fitnessDao;
    private final PersonnelDao personnelDao;

    public FitnessService(FitnessDao fitnessDao, PersonnelDao personnelDao) {
        this.fitnessDao = fitnessDao;
        this.personnelDao = personnelDao;
    }

    public List<FitnessRecord> query(String keyword, String item, int page, int size) {
        return fitnessDao.query(keyword, item, page, size);
    }

    public long count(String keyword, String item) {
        return fitnessDao.count(keyword, item);
    }

    public FitnessRecord create(FitnessRecord r) {
        validate(r);
        long id = fitnessDao.insert(r);
        return fitnessDao.findById(id);
    }

    public FitnessRecord update(Long id, FitnessRecord r) {
        if (fitnessDao.findById(id) == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        validate(r);
        r.setId(id);
        fitnessDao.update(r);
        return fitnessDao.findById(id);
    }

    public void delete(Long id) {
        fitnessDao.delete(id);
    }

    private void validate(FitnessRecord r) {
        if (r.getPersonnelId() == null || r.getPersonnelId() <= 0) {
            throw new IllegalArgumentException("请选择考核人员");
        }
        if (personnelDao.findById(r.getPersonnelId()) == null) {
            throw new IllegalArgumentException("所选人员不存在");
        }
        if (!StringUtils.hasText(r.getItem())) {
            throw new IllegalArgumentException("请填写考核项目");
        }
        if (!StringUtils.hasText(r.getTestDate())) {
            throw new IllegalArgumentException("请填写考核日期");
        }
    }
}
