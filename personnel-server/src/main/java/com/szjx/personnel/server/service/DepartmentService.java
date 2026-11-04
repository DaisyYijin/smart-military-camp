package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.dao.DepartmentDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.Department;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 单位业务
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

    /** 同级单位拖动排序:ids 为同一父级下的完整顺序,逐个写 sort(1..n);跨级拖动拒绝(可见范围外单位视为不存在) */
    @Transactional
    public void reorder(List<Long> ids) {
        if (ids == null || ids.size() < 2) {
            throw new IllegalArgumentException("排序信息不能为空");
        }
        java.util.Map<Long, Long> parentOf = new java.util.HashMap<Long, Long>();
        for (Department d : departmentDao.findAll()) {
            parentOf.put(d.getId(), d.getParentId() == null ? Long.valueOf(0L) : d.getParentId());
        }
        Long parent = null;
        for (Long id : ids) {
            Long p = parentOf.get(id);
            if (p == null) {
                throw new IllegalArgumentException("单位不存在");
            }
            if (parent == null) { parent = p; }
            else if (!parent.equals(p)) {
                throw new IllegalArgumentException("只能调整同级单位之间的顺序");
            }
        }
        departmentDao.reorder(ids);
    }

    public Department create(Department d) {
        DataScope.requireDepartment(d.getParentId());
        validateLeader(d);
        if (!StringUtils.hasText(d.getName())) {
            throw new IllegalArgumentException("单位名称不能为空");
        }
        if (d.getParentId() != null && d.getParentId() > 0 && departmentDao.findById(d.getParentId()) == null) {
            throw new IllegalArgumentException("上级单位不存在");
        }
        long id = departmentDao.insert(d);
        d.setId(id);
        return d;
    }

    public Department update(Long id, Department d) {
        DataScope.requireDepartment(id);
        validateLeader(d);
        if (!StringUtils.hasText(d.getName())) {
            throw new IllegalArgumentException("单位名称不能为空");
        }
        Department old = departmentDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("单位不存在");
        }
        if (DataScope.restricted()) {
            long oldParent = old.getParentId() == null ? 0 : old.getParentId();
            long newParent = d.getParentId() == null ? oldParent : d.getParentId();
            if (newParent == 0 && !DataScope.allowsDepartment(old.getParentId())) newParent = oldParent;
            if (newParent != oldParent) throw new ForbiddenException("子账号不能调整单位层级，请联系上级账号");
            d.setParentId(oldParent);
        }
        if (d.getParentId() != null && d.getParentId() > 0) {
            if (d.getParentId().equals(id)) {
                throw new IllegalArgumentException("上级单位不能是自己");
            }
            if ((!DataScope.restricted() || DataScope.allowsDepartment(d.getParentId()))
                    && departmentDao.findById(d.getParentId()) == null) {
                throw new IllegalArgumentException("上级单位不存在");
            }
            // 防止环引用:新上级不能是自己的后代
            Department cursor = departmentDao.findById(d.getParentId());
            while (cursor != null && cursor.getParentId() != null && cursor.getParentId() > 0) {
                if (cursor.getParentId().equals(id)) {
                    throw new IllegalArgumentException("上级单位不能是自己的下级单位");
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
        DataScope.requireDepartment(id);
        if (departmentDao.assignedToAccount(id)) {
            throw new IllegalArgumentException("该单位已分配给账号，请先调整账号的管理单位");
        }
        if (departmentDao.countChildren(id) > 0) {
            throw new IllegalArgumentException("该单位下还有子单位,请先删除子单位");
        }
        if (departmentDao.countPersonnel(id) > 0) {
            throw new IllegalArgumentException("该单位下还有人员,请先移走人员");
        }
        departmentDao.delete(id);
    }

    private void validateLeader(Department d) {
        if (DataScope.restricted() && d.getLeaderId() != null && d.getLeaderId() > 0
                && personnelDao.findById(d.getLeaderId()) == null) {
            throw new ForbiddenException("负责人不在当前账号的管理范围内");
        }
    }
}
