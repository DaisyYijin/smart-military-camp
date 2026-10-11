package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.DepartmentDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.Department;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 部门业务
 */
@Service
public class DepartmentService {

    private final DepartmentDao departmentDao;
    private final PersonnelDao personnelDao;

    public DepartmentService(DepartmentDao departmentDao, PersonnelDao personnelDao) {
        this.departmentDao = departmentDao;
        this.personnelDao = personnelDao;
    }

    public List<Department> tree() {
        return departmentDao.tree();
    }

    public Department create(Department d) {
        if (!StringUtils.hasText(d.getName())) {
            throw new IllegalArgumentException("部门名称不能为空");
        }
        if (d.getParentId() != null && d.getParentId() > 0 && departmentDao.findById(d.getParentId()) == null) {
            throw new IllegalArgumentException("上级部门不存在");
        }
        long id = departmentDao.insert(d);
        return departmentDao.findById(id);
    }

    public Department update(Long id, Department d) {
        if (!StringUtils.hasText(d.getName())) {
            throw new IllegalArgumentException("部门名称不能为空");
        }
        Department old = departmentDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("部门不存在");
        }
        if (d.getParentId() != null && d.getParentId() > 0) {
            if (d.getParentId().equals(id)) {
                throw new IllegalArgumentException("上级部门不能是自己");
            }
            if (departmentDao.findById(d.getParentId()) == null) {
                throw new IllegalArgumentException("上级部门不存在");
            }
            // 防止环引用:新上级不能是自己的后代
            Department cursor = departmentDao.findById(d.getParentId());
            while (cursor != null && cursor.getParentId() != null && cursor.getParentId() > 0) {
                if (cursor.getParentId().equals(id)) {
                    throw new IllegalArgumentException("上级部门不能是自己的下级部门");
                }
                cursor = departmentDao.findById(cursor.getParentId());
            }
        }
        d.setId(id);
        departmentDao.update(d);
        return departmentDao.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        if (departmentDao.countChildren(id) > 0) {
            throw new IllegalArgumentException("该部门下还有子部门,请先删除子部门");
        }
        if (departmentDao.countPersonnel(id) > 0) {
            throw new IllegalArgumentException("该部门下还有人员,请先移走人员");
        }
        departmentDao.delete(id);
    }
}
