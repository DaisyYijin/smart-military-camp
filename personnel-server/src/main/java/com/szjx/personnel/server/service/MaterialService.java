package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.MaterialDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.Material;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 物资业务
 */
@Service
public class MaterialService {

    private final MaterialDao materialDao;
    private final PersonnelDao personnelDao;

    public MaterialService(MaterialDao materialDao, PersonnelDao personnelDao) {
        this.materialDao = materialDao;
        this.personnelDao = personnelDao;
    }

    public List<Material> query(String keyword, String category, int page, int size) {
        return materialDao.query(keyword, category, page, size);
    }

    public long count(String keyword, String category) {
        return materialDao.count(keyword, category);
    }

    public Material create(Material m) {
        validate(m);
        long id = materialDao.insert(m);
        return materialDao.findById(id);
    }

    public Material update(Long id, Material m) {
        if (materialDao.findById(id) == null) {
            throw new IllegalArgumentException("物资不存在");
        }
        validate(m);
        m.setId(id);
        materialDao.update(m);
        return materialDao.findById(id);
    }

    public void delete(Long id) {
        materialDao.delete(id);
    }

    private void validate(Material m) {
        if (!StringUtils.hasText(m.getName())) {
            throw new IllegalArgumentException("物资名称不能为空");
        }
        if (m.getQuantity() != null && m.getQuantity() < 0) {
            throw new IllegalArgumentException("数量不能为负数");
        }
        if (m.getKeeperId() != null && m.getKeeperId() > 0
                && personnelDao.findById(m.getKeeperId()) == null) {
            throw new IllegalArgumentException("所选责任人不存在");
        }
    }
}
