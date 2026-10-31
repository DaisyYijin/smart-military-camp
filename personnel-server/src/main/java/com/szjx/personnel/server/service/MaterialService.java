package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;

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
    private final PhotoService photoService;

    public MaterialService(MaterialDao materialDao, PersonnelDao personnelDao, PhotoService photoService) {
        this.materialDao = materialDao;
        this.personnelDao = personnelDao;
        this.photoService = photoService;
    }

    public List<Material> query(String keyword, String category, int page, int size) {
        return materialDao.query(keyword, category, page, size);
    }

    public long count(String keyword, String category) {
        return materialDao.count(keyword, category);
    }

    public Material create(Material m) {
        validate(m);
        String photo = savePhotoIfAny(m.getPhotoBase64());
        m.setPhoto(photo);
        try {
            long id = materialDao.insert(m);
            return materialDao.findById(id);
        } catch (RuntimeException ex) {
            photoService.deleteMaterialPhoto(photo);
            throw ex;
        }
    }

    public Material update(Long id, Material m) {
        Material old = materialDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("物资不存在");
        }
        validate(m);
        boolean replacePhoto = m.getPhotoBase64() != null;
        boolean removePhoto = m.getPhoto() != null && m.getPhoto().isEmpty();
        String oldPhoto = old.getPhoto();
        String newPhoto = replacePhoto ? savePhotoIfAny(m.getPhotoBase64()) : null;
        if (replacePhoto) {
            m.setPhoto(newPhoto);
        } else if (removePhoto) {
            m.setPhoto("");
        } else {
            m.setPhoto(oldPhoto);
        }
        m.setId(id);
        try {
            materialDao.update(m);
        } catch (RuntimeException ex) {
            photoService.deleteMaterialPhoto(newPhoto);
            throw ex;
        }
        if ((replacePhoto || removePhoto) && oldPhoto != null && !oldPhoto.isEmpty()) {
            photoService.deleteMaterialPhoto(oldPhoto);
        }
        return materialDao.findById(id);
    }

    public void delete(Long id) {
        Material existing = materialDao.findById(id);
        materialDao.delete(id);
        if (existing != null && existing.getPhoto() != null && !existing.getPhoto().isEmpty()) {
            photoService.deleteMaterialPhoto(existing.getPhoto());
        }
    }

    public Material get(Long id) {
        return materialDao.findById(id);
    }

    public java.io.File photoFile(String fileName) {
        return photoService.getMaterialPhotoFile(fileName);
    }

    /** 保存 base64 照片,返回文件名;无照片返回 null(100MB 为运行时防护上限,与人员/装备一致) */
    private String savePhotoIfAny(String photoBase64) {
        if (!StringUtils.hasText(photoBase64)) return null;
        String base64 = photoBase64;
        if (base64.startsWith("data:")) base64 = base64.substring(base64.indexOf(',') + 1);
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
        photoService.saveMaterialPhoto(fileName, bytes);
        return fileName;
    }

    private String guessExt(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) return ".jpg";
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) return ".png";
        throw new IllegalArgumentException("仅支持 JPG 或 PNG 图片");
    }

    private void validate(Material m) {
        materialDao.requireOwner(m.getKeeperId());
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
