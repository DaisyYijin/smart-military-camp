package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.DepartmentDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.model.Personnel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 人员业务
 */
@Service
public class PersonnelService {

    private final PersonnelDao personnelDao;
    private final PhotoService photoService;
    private final DepartmentDao departmentDao;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public PersonnelService(PersonnelDao personnelDao, PhotoService photoService, DepartmentDao departmentDao,
                             org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.personnelDao = personnelDao;
        this.photoService = photoService;
        this.departmentDao = departmentDao;
        this.jdbc = jdbc;
    }

    public List<Personnel> query(String keyword, String name, String idCard, Long departmentId, String status, String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education, String maritalStatus, String nation, String bloodType, String onlyChild, String entryDateStart, String entryDateEnd, int page, int size) {
        return personnelDao.query(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, entryDateStart, entryDateEnd, page, size);
    }

    public long count(String keyword, String name, String idCard, Long departmentId, String status, String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education, String maritalStatus, String nation, String bloodType, String onlyChild, String entryDateStart, String entryDateEnd) {
        return personnelDao.count(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, entryDateStart, entryDateEnd);
    }

    public Personnel findById(Long id) {
        return personnelDao.findById(id);
    }

    @Transactional
    public Personnel create(Personnel p) {
        validate(p);
        String photoFile = savePhotoIfAny(p.getPhotoBase64());
        p.setPhoto(photoFile);
        long id = personnelDao.insert(p);
        if (photoFile != null) {
            deleteOnRollback(photoFile); // 入库失败则清理已写入的照片文件
        }
        return personnelDao.findById(id);
    }

    @Transactional
    public Personnel update(Long id, Personnel p) {
        Personnel old = personnelDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("人员不存在");
        }
        // null 字段保留旧值(避免部分更新丢数据;清空字段请传空串)
        mergeMissing(old, p);
        validate(p);
        String photoFile = old.getPhoto();
        if (StringUtils.hasText(p.getPhotoBase64())) {
            // 提交了新照片
            String newFile = savePhotoIfAny(p.getPhotoBase64());
            // 替换旧照片(事务提交后删除旧文件)
            if (StringUtils.hasText(old.getPhoto()) && !old.getPhoto().equals(newFile)) {
                deletePhotoAfterCommit(old.getPhoto());
            }
            deleteOnRollback(newFile);
            photoFile = newFile;
        } else if (p.getPhoto() != null && p.getPhoto().isEmpty() && StringUtils.hasText(old.getPhoto())) {
            // 明确要求删除照片(事务提交后再删文件,避免回滚后文件丢失)
            final String oldPhoto = old.getPhoto();
            deletePhotoAfterCommit(oldPhoto);
            photoFile = "";
        }
        p.setId(id);
        p.setPhoto(photoFile);
        personnelDao.update(p);
        return personnelDao.findById(id);
    }

    /**
     * 事务回滚时删除已写入的照片文件(与写入侧对称)
     */
    private void deleteOnRollback(final String photo) {
        if (photo == null || photo.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    photoService.delete(photo);
                }
            }
        });
    }

    /** 把传入对象中为 null 的字段用旧值补齐 */
    private void mergeMissing(Personnel old, Personnel p) {
        p.setName(p.getName() == null ? old.getName() : p.getName());
        p.setGender(p.getGender() == null ? old.getGender() : p.getGender());
        p.setIdCard(p.getIdCard() == null ? old.getIdCard() : p.getIdCard());
        p.setBirthday(p.getBirthday() == null ? old.getBirthday() : p.getBirthday());
        p.setPhone(p.getPhone() == null ? old.getPhone() : p.getPhone());
        p.setDepartmentId(p.getDepartmentId() == null ? old.getDepartmentId() : p.getDepartmentId());
        p.setRank(p.getRank() == null ? old.getRank() : p.getRank());
        p.setPost(p.getPost() == null ? old.getPost() : p.getPost());
        p.setStatus(p.getStatus() == null ? old.getStatus() : p.getStatus());
        p.setEntryDate(p.getEntryDate() == null ? old.getEntryDate() : p.getEntryDate());
        p.setAddress(p.getAddress() == null ? old.getAddress() : p.getAddress());
        p.setRemark(p.getRemark() == null ? old.getRemark() : p.getRemark());
        p.setBloodType(p.getBloodType() == null ? old.getBloodType() : p.getBloodType());
        p.setNation(p.getNation() == null ? old.getNation() : p.getNation());
        p.setPoliticalStatus(p.getPoliticalStatus() == null ? old.getPoliticalStatus() : p.getPoliticalStatus());
        p.setPartyJoinDate(p.getPartyJoinDate() == null ? old.getPartyJoinDate() : p.getPartyJoinDate());
        p.setMaritalStatus(p.getMaritalStatus() == null ? old.getMaritalStatus() : p.getMaritalStatus());
        p.setEducation(p.getEducation() == null ? old.getEducation() : p.getEducation());
        p.setOnlyChild(p.getOnlyChild() == null ? old.getOnlyChild() : p.getOnlyChild());
        p.setNativePlace(p.getNativePlace() == null ? old.getNativePlace() : p.getNativePlace());
        p.setSpouseName(p.getSpouseName() == null ? old.getSpouseName() : p.getSpouseName());
        p.setSpouseWork(p.getSpouseWork() == null ? old.getSpouseWork() : p.getSpouseWork());
        p.setSpousePhone(p.getSpousePhone() == null ? old.getSpousePhone() : p.getSpousePhone());
        p.setSpouseIdCard(p.getSpouseIdCard() == null ? old.getSpouseIdCard() : p.getSpouseIdCard());
        p.setSpouseJob(p.getSpouseJob() == null ? old.getSpouseJob() : p.getSpouseJob());
        p.setMarriageDate(p.getMarriageDate() == null ? old.getMarriageDate() : p.getMarriageDate());
        p.setHeight(p.getHeight() == null ? old.getHeight() : p.getHeight());
        p.setWeight(p.getWeight() == null ? old.getWeight() : p.getWeight());
    }

    @Transactional
    public void delete(Long id) {
        Personnel old = personnelDao.findById(id);
        if (old != null) {
            final String photo = old.getPhoto();
            personnelDao.delete(id);
            jdbc.update("DELETE FROM position_history WHERE personnel_id=?", id);
            jdbc.update("DELETE FROM reward_punish WHERE personnel_id=?", id);
            jdbc.update("DELETE FROM child_info WHERE personnel_id=?", id);
            jdbc.update("DELETE FROM family_member WHERE personnel_id=?", id);
            jdbc.update("DELETE FROM driver_license WHERE personnel_id=?", id);
            jdbc.update("DELETE FROM personal_card WHERE personnel_id=?", id);
            jdbc.update("DELETE FROM private_vehicle WHERE personnel_id=?", id);
            jdbc.update("DELETE FROM housing_info WHERE personnel_id=?", id);
            if (photo != null && !photo.isEmpty()) {
                deletePhotoAfterCommit(photo);
            }
        }
    }

    /**
     * 事务提交后再删除照片文件;无事务时直接删除
     */
    private void deletePhotoAfterCommit(final String photo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            photoService.delete(photo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                photoService.delete(photo);
            }
        });
    }

    /** 获取照片文件(供下载接口) */
    public java.io.File photoFile(String fileName) {
        return photoService.getFile(fileName);
    }

    /** 全部人员精简选项(下拉框用) */
    public List<Map<String, Object>> findAllOptions() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : personnelDao.findAllSimple()) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("id", row.get("id"));
            m.put("name", row.get("name"));
            m.put("departmentName", row.get("department_name"));
            m.put("rank", row.get("rank"));
            m.put("post", row.get("post"));
            result.add(m);
        }
        return result;
    }

    /**
     * 批量导入:按身份证号匹配,存在则更新,不存在则新增
     * @return [新增数, 更新数, 失败数]
     */
    @Transactional
    public int[] importRows(List<Personnel> rows) {
        int added = 0;
        int updated = 0;
        int failed = 0;
        for (Personnel row : rows) {
            try {
                if (!StringUtils.hasText(row.getName())) {
                    failed++;
                    continue;
                }
                Personnel existing = personnelDao.findByIdCard(row.getIdCard());
                // 解析部门名称 → 部门ID(找不到则自动创建顶级部门)
                if (StringUtils.hasText(row.getDepartmentName())) {
                    row.setDepartmentId(resolveDepartmentId(row.getDepartmentName()));
                } else {
                    row.setDepartmentId(0L);
                }
                if (existing != null) {
                    row.setId(existing.getId());
                    row.setPhoto(existing.getPhoto());
                    personnelDao.update(row);
                    updated++;
                } else {
                    personnelDao.insert(row);
                    added++;
                }
            } catch (Exception e) {
                failed++;
            }
        }
        return new int[]{added, updated, failed};
    }

    private void validate(Personnel p) {
        if (!StringUtils.hasText(p.getName())) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        if (StringUtils.hasText(p.getStatus()) && p.getStatus().length() > 20) {
            throw new IllegalArgumentException("状态内容过长");
        }
        String idCard = p.getIdCard();
        if (StringUtils.hasText(idCard) && !idCard.matches("\\d{17}[\\dXx]|\\d{15}")) {
            throw new IllegalArgumentException("身份证号格式不正确: " + idCard);
        }
    }

    /** 按部门名称查找或自动创建(仅用于导入) */
    private Long resolveDepartmentId(String departmentName) {
        String name = departmentName.trim();
        for (Department d : departmentDao.findAll()) {
            if (name.equals(d.getName())) {
                return d.getId();
            }
        }
        Department d = new Department();
        d.setName(name);
        d.setParentId(0L);
        d.setSort(0);
        return departmentDao.insert(d);
    }

    /** 保存 base64 照片,返回文件名;无照片返回 null */    private String savePhotoIfAny(String photoBase64) {
        if (!StringUtils.hasText(photoBase64)) {
            return null;
        }
        String base64 = photoBase64;
        if (base64.startsWith("data:")) {
            base64 = base64.substring(base64.indexOf(',') + 1);
        }
        // 解码前先按 base64 字符串长度预检,防止超大请求耗尽内存
        if (base64.length() > 7 * 1024 * 1024) {
            throw new IllegalArgumentException("照片不能超过 5MB");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (Exception e) {
            throw new IllegalArgumentException("照片数据格式错误");
        }
        if (bytes.length > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("照片不能超过 5MB");
        }
        String ext = guessExt(bytes);
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        photoService.save(fileName, bytes);
        return fileName;
    }

    private String guessExt(byte[] head) {
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8) {
            return ".jpg";
        }
        if (head.length >= 8 && head[0] == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) {
            return ".png";
        }
        return ".jpg";
    }
}
