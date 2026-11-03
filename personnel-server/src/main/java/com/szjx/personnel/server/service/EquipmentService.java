package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;

import com.szjx.personnel.server.dao.EquipmentDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.Equipment;
import com.szjx.personnel.server.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 装备业务
 */
@Service
public class EquipmentService {

    private final EquipmentDao equipmentDao;
    private final PersonnelDao personnelDao;
    private final PhotoService photoService;

    public EquipmentService(EquipmentDao equipmentDao, PersonnelDao personnelDao, PhotoService photoService) {
        this.equipmentDao = equipmentDao;
        this.personnelDao = personnelDao;
        this.photoService = photoService;
    }

    public PageResult<java.util.Map<String, Object>> query(String name, String code, String identCode,
            String category, String status, String keeperName, String location,
            String buyDateStart, String buyDateEnd, int page, int size) {
        long total = equipmentDao.count(name, code, identCode, category, status, keeperName,
                location, buyDateStart, buyDateEnd);
        java.util.List<java.util.Map<String, Object>> rows = equipmentDao.query(name, code, identCode,
                category, status, keeperName, location, buyDateStart, buyDateEnd, page, size);
        return new PageResult<java.util.Map<String, Object>>(total, rows);
    }

    public Equipment create(Equipment e) {
        validate(e, null);
            String photo = savePhotoIfAny(e.getPhotoBase64());
            e.setPhoto(photo);
        try {
            long id = equipmentDao.insert(e);
            return equipmentDao.findById(id);
        } catch (RuntimeException ex) {
            photoService.deleteEquipmentPhoto(photo);
            throw ex;
        }
    }

    public Equipment update(Long id, Equipment e) {
        Equipment old = equipmentDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("装备不存在");
        }
        if (e.getCategory() == null) e.setCategory(old.getCategory());
        if (e.getBrand() == null) e.setBrand(old.getBrand());
        if (e.getCode() == null) e.setCode(old.getCode());
        if (e.getIdentCode() == null) e.setIdentCode(old.getIdentCode());
        if (e.getBuyDate() == null) e.setBuyDate(old.getBuyDate());
        validate(e, id);
        boolean replacePhoto = e.getPhotoBase64() != null;
        boolean removePhoto = e.getPhoto() != null && e.getPhoto().isEmpty();
        String oldPhoto = old.getPhoto();
        String newPhoto = replacePhoto ? savePhotoIfAny(e.getPhotoBase64()) : null;
        if (replacePhoto) {
            e.setPhoto(newPhoto);
        } else if (removePhoto) {
            e.setPhoto("");
        } else {
            e.setPhoto(oldPhoto);
        }
        e.setId(id);
        try {
            equipmentDao.update(e);
        } catch (RuntimeException ex) {
            photoService.deleteEquipmentPhoto(newPhoto);
            throw ex;
        }
        if ((replacePhoto || removePhoto) && oldPhoto != null && !oldPhoto.isEmpty()) {
            photoService.deleteEquipmentPhoto(oldPhoto);
        }
        return equipmentDao.findById(id);
    }

    public void delete(Long id) {
        Equipment existing = equipmentDao.findById(id);
        equipmentDao.delete(id);
        if (existing != null && existing.getPhoto() != null && !existing.getPhoto().isEmpty()) {
            photoService.deleteEquipmentPhoto(existing.getPhoto());
        }
    }

    public Equipment get(Long id) {
        return equipmentDao.findById(id);
    }

    public java.io.File photoFile(String fileName) {
        return photoService.getEquipmentPhotoFile(fileName);
    }

    private String savePhotoIfAny(String photoBase64) {
        if (!StringUtils.hasText(photoBase64)) return null;
        String base64 = photoBase64;
        if (base64.startsWith("data:")) base64 = base64.substring(base64.indexOf(',') + 1);
        // 100MB 为运行时防护上限(与人员照片一致),避免超大 Base64 请求耗尽内存
        if (base64.length() > 140 * 1024 * 1024) {
            throw new IllegalArgumentException("照片请求过大,单张图片最多 100MB");
        }
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(base64);
        } catch (Exception e) {
            throw new IllegalArgumentException("照片数据格式错误");
        }
        if (bytes.length > 100 * 1024 * 1024) {
            throw new IllegalArgumentException("照片请求过大,单张图片最多 100MB");
        }
        String ext = guessExt(bytes);
        String fileName = java.util.UUID.randomUUID().toString().replace("-", "") + ext;
        photoService.saveEquipmentPhoto(fileName, bytes);
        return fileName;
    }

    private String guessExt(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) return ".jpg";
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) return ".png";
        throw new IllegalArgumentException("仅支持 JPG 或 PNG 图片");
    }

    private void validate(Equipment e, Long excludeId) {
        equipmentDao.requireOwner(e.getKeeperId());
        if (!StringUtils.hasText(e.getName())) {
            throw new IllegalArgumentException("装备名称不能为空");
        }
        if ("枪支".equals(e.getCategory())) {
            if (!StringUtils.hasText(e.getCode())) {
                throw new IllegalArgumentException("枪支装备编号不能为空");
            }
            e.setCode(e.getCode().trim());
            if (equipmentDao.existsGunCode(e.getCode(), excludeId)) {
                throw new IllegalArgumentException("枪支装备编号已存在");
            }
        }
        if (e.getKeeperId() != null && e.getKeeperId() > 0
                && personnelDao.findById(e.getKeeperId()) == null) {
            throw new IllegalArgumentException("所选保管人不存在");
        }
        if (!com.szjx.personnel.server.util.Dates.validOrEmpty(e.getBuyDate())) {
            throw new IllegalArgumentException("购置日期格式应为 yyyy-MM-dd");
        }
        if (!com.szjx.personnel.server.util.Dates.validOrEmpty(e.getEquipDate())) {
            throw new IllegalArgumentException("装备时间格式应为 yyyy-MM-dd");
        }
    }
}
