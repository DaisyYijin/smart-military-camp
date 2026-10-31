package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;

import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.dao.VehicleDao;
import com.szjx.personnel.server.model.Vehicle;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 车辆业务
 */
@Service
public class VehicleService {

    private final VehicleDao vehicleDao;
    private final PersonnelDao personnelDao;
    private final PhotoService photoService;

    public VehicleService(VehicleDao vehicleDao, PersonnelDao personnelDao, PhotoService photoService) {
        this.vehicleDao = vehicleDao;
        this.personnelDao = personnelDao;
        this.photoService = photoService;
    }

    public List<Vehicle> query(String keyword, String status, int page, int size) {
        return vehicleDao.query(keyword, status, page, size);
    }

    public long count(String keyword, String status) {
        return vehicleDao.count(keyword, status);
    }

    public Vehicle create(Vehicle v) {
        validate(v);
        String photo = savePhotoIfAny(v.getPhotoBase64());
        v.setPhoto(photo);
        try {
            long id = vehicleDao.insert(v);
            return vehicleDao.findById(id);
        } catch (RuntimeException ex) {
            photoService.deleteVehiclePhoto(photo);
            throw ex;
        }
    }

    public Vehicle update(Long id, Vehicle v) {
        Vehicle old = vehicleDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("车辆不存在");
        }
        validate(v);
        // 表单已不再维护的字段:未随请求提交时沿用旧值,避免编辑其他项时把库里数据清空
        if (v.getFuelType() == null) v.setFuelType(old.getFuelType());
        if (v.getMileage() == null) v.setMileage(old.getMileage());
        if (v.getPurchaseDate() == null) v.setPurchaseDate(old.getPurchaseDate());
        if (v.getInsuranceExpiry() == null) v.setInsuranceExpiry(old.getInsuranceExpiry());
        boolean replacePhoto = v.getPhotoBase64() != null;
        boolean removePhoto = v.getPhoto() != null && v.getPhoto().isEmpty();
        String oldPhoto = old.getPhoto();
        String newPhoto = replacePhoto ? savePhotoIfAny(v.getPhotoBase64()) : null;
        if (replacePhoto) v.setPhoto(newPhoto);
        else if (removePhoto) v.setPhoto("");
        else v.setPhoto(oldPhoto);
        v.setId(id);
        try {
            vehicleDao.update(v);
        } catch (RuntimeException ex) {
            photoService.deleteVehiclePhoto(newPhoto);
            throw ex;
        }
        if ((replacePhoto || removePhoto) && oldPhoto != null && !oldPhoto.isEmpty()) {
            photoService.deleteVehiclePhoto(oldPhoto);
        }
        return vehicleDao.findById(id);
    }

    public void delete(Long id) {
        Vehicle existing = vehicleDao.findById(id);
        vehicleDao.delete(id);
        if (existing != null && existing.getPhoto() != null && !existing.getPhoto().isEmpty()) {
            photoService.deleteVehiclePhoto(existing.getPhoto());
        }
    }

    public Vehicle get(Long id) {
        return vehicleDao.findById(id);
    }

    public java.io.File photoFile(String fileName) {
        return photoService.getVehiclePhotoFile(fileName);
    }

    private String savePhotoIfAny(String photoBase64) {
        if (!StringUtils.hasText(photoBase64)) return null;
        String base64 = photoBase64;
        if (base64.startsWith("data:")) base64 = base64.substring(base64.indexOf(',') + 1);
        if (base64.length() > 140 * 1024 * 1024) throw new IllegalArgumentException("照片请求过大,单张图片最多 100MB");
        byte[] bytes;
        try { bytes = java.util.Base64.getDecoder().decode(base64); }
        catch (Exception e) { throw new IllegalArgumentException("照片数据格式错误"); }
        if (bytes.length > 100 * 1024 * 1024) throw new IllegalArgumentException("照片请求过大,单张图片最多 100MB");
        String ext = guessExt(bytes);
        String fileName = java.util.UUID.randomUUID().toString().replace("-", "") + ext;
        photoService.saveVehiclePhoto(fileName, bytes);
        return fileName;
    }

    private String guessExt(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) return ".jpg";
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) return ".png";
        throw new IllegalArgumentException("仅支持 JPG 或 PNG 图片");
    }

    private void validate(Vehicle v) {
        vehicleDao.requireOwner(v.getDriverId());
        if (!StringUtils.hasText(v.getPlateNo())) {
            throw new IllegalArgumentException("车牌号不能为空");
        }
        if (v.getDriverId() != null && v.getDriverId() > 0
                && personnelDao.findById(v.getDriverId()) == null) {
            throw new IllegalArgumentException("所选驾驶员不存在");
        }
        if (!com.szjx.personnel.server.util.Dates.validOrEmpty(v.getPurchaseDate())) {
            throw new IllegalArgumentException("购置日期格式应为 yyyy-MM-dd");
        }
        if (!com.szjx.personnel.server.util.Dates.validOrEmpty(v.getEquipDate())) {
            throw new IllegalArgumentException("装备时间格式应为 yyyy-MM-dd");
        }
        if (!com.szjx.personnel.server.util.Dates.validOrEmpty(v.getInsuranceExpiry())) {
            throw new IllegalArgumentException("保险到期日格式应为 yyyy-MM-dd");
        }
        if (!com.szjx.personnel.server.util.Dates.validOrEmpty(v.getInspectionExpiry())) {
            throw new IllegalArgumentException("年检到期日格式应为 yyyy-MM-dd");
        }
    }
}
