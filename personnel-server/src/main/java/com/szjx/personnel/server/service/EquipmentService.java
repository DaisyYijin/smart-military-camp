package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.EquipmentDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.Equipment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 装备业务
 */
@Service
public class EquipmentService {

    private final EquipmentDao equipmentDao;
    private final PersonnelDao personnelDao;

    public EquipmentService(EquipmentDao equipmentDao, PersonnelDao personnelDao) {
        this.equipmentDao = equipmentDao;
        this.personnelDao = personnelDao;
    }

    public List<Equipment> query(String keyword, String status, int page, int size) {
        return equipmentDao.query(keyword, status, page, size);
    }

    public long count(String keyword, String status) {
        return equipmentDao.count(keyword, status);
    }

    public Equipment create(Equipment e) {
        validate(e);
        long id = equipmentDao.insert(e);
        return equipmentDao.findById(id);
    }

    public Equipment update(Long id, Equipment e) {
        if (equipmentDao.findById(id) == null) {
            throw new IllegalArgumentException("装备不存在");
        }
        validate(e);
        e.setId(id);
        equipmentDao.update(e);
        return equipmentDao.findById(id);
    }

    public void delete(Long id) {
        equipmentDao.delete(id);
    }

    private void validate(Equipment e) {
        if (!StringUtils.hasText(e.getName())) {
            throw new IllegalArgumentException("装备名称不能为空");
        }
        if (e.getKeeperId() != null && e.getKeeperId() > 0
                && personnelDao.findById(e.getKeeperId()) == null) {
            throw new IllegalArgumentException("所选保管人不存在");
        }
    }
}
