package com.szjx.personnel.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 照片存储服务(存于 data/photos/)
 */
@Service
public class PhotoService {

    private final File photosDir;

    public PhotoService(@Value("${app.data-dir:data}") String dataDir) {
        this.photosDir = new File(dataDir, "photos");
        if (!photosDir.exists()) {
            photosDir.mkdirs();
        }
    }

    public void saveVehiclePhoto(String fileName, byte[] bytes) {
        File dir = new File(photosDir, "vehicle");
        if (!dir.exists() && !dir.mkdirs()) throw new RuntimeException("创建车辆照片目录失败");
        writeFile(dir, fileName, bytes);
    }

    public File getVehiclePhotoFile(String fileName) {
        return new File(new File(photosDir, "vehicle"), new File(fileName == null ? "" : fileName).getName());
    }

    public void deleteVehiclePhoto(String fileName) {
        if (fileName == null || fileName.isEmpty()) return;
        File file = getVehiclePhotoFile(fileName);
        if (file.exists() && !file.delete()) throw new RuntimeException("删除旧车辆照片失败");
    }

    public void saveEquipmentPhoto(String fileName, byte[] bytes) {
        File dir = new File(photosDir, "equipment");
        if (!dir.exists() && !dir.mkdirs()) throw new RuntimeException("创建装备照片目录失败");
        writeFile(dir, fileName, bytes);
    }

    public File getEquipmentPhotoFile(String fileName) {
        return new File(new File(photosDir, "equipment"), new File(fileName == null ? "" : fileName).getName());
    }

    public void deleteEquipmentPhoto(String fileName) {
        if (fileName == null || fileName.isEmpty()) return;
        File file = getEquipmentPhotoFile(fileName);
        if (file.exists() && !file.delete()) throw new RuntimeException("删除旧装备照片失败");
    }

    public void saveMaterialPhoto(String fileName, byte[] bytes) {
        File dir = new File(photosDir, "material");
        if (!dir.exists() && !dir.mkdirs()) throw new RuntimeException("创建物资照片目录失败");
        writeFile(dir, fileName, bytes);
    }

    public File getMaterialPhotoFile(String fileName) {
        return new File(new File(photosDir, "material"), new File(fileName == null ? "" : fileName).getName());
    }

    public void deleteMaterialPhoto(String fileName) {
        if (fileName == null || fileName.isEmpty()) return;
        File file = getMaterialPhotoFile(fileName);
        if (file.exists() && !file.delete()) throw new RuntimeException("删除旧物资照片失败");
    }

    private void writeFile(File dir, String fileName, byte[] bytes) {
        File target = new File(dir, new File(fileName).getName());
        try {
            FileOutputStream out = new FileOutputStream(target);
            try { out.write(bytes); } finally { out.close(); }
        } catch (IOException e) {
            throw new RuntimeException("保存照片失败", e);
        }
    }

    public void save(String fileName, byte[] bytes) {
        writeFile(photosDir, fileName, bytes);
    }

    public void delete(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        File target = getFile(fileName);
        if (target.exists()) {
            target.delete();
        }
    }

    public File getFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("照片文件名不能为空");
        }
        // 同时识别两种路径分隔符,不能依赖当前操作系统才识别 Windows 路径。
        String relative = fileName.replace('\\', '/');
        if (relative.startsWith("/") || relative.matches("(?i)^[a-z]:.*") || relative.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("非法照片路径");
        }
        try {
            java.nio.file.Path root = photosDir.getCanonicalFile().toPath();
            java.nio.file.Path target = root.resolve(relative).normalize();
            if (target.equals(root) || !target.startsWith(root)) {
                throw new IllegalArgumentException("照片路径超出存储目录");
            }
            // canonical 路径再检查一次,阻止目录内符号链接指向目录外。
            java.nio.file.Path canonical = target.toFile().getCanonicalFile().toPath();
            if (canonical.equals(root) || !canonical.startsWith(root)) {
                throw new IllegalArgumentException("照片路径超出存储目录");
            }
            return canonical.toFile();
        } catch (IOException e) {
            throw new IllegalArgumentException("非法照片路径", e);
        }
    }

    /** 移入回收站(人员删除撤回用),返回回收站内文件名;失败返回原样 */
    public String moveToTrash(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        File trashDir = new File(photosDir, "trash");
        if (!trashDir.exists()) {
            trashDir.mkdirs();
        }
        String safe = new File(fileName).getName();
        String trashName = System.currentTimeMillis() + "_" + safe;
        File source = new File(photosDir, safe);
        if (!source.exists()) {
            return "";
        }
        return source.renameTo(new File(trashDir, trashName)) ? trashName : "";
    }

    /** 从回收站按原文件名恢复,成功返回 true */
    public boolean restoreFromTrash(String trashName, String originalName) {
        if (trashName == null || trashName.isEmpty() || originalName == null || originalName.isEmpty()) {
            return true;
        }
        File source = new File(new File(photosDir, "trash"), new File(trashName).getName());
        if (!source.exists()) {
            return false;
        }
        return source.renameTo(new File(photosDir, new File(originalName).getName()));
    }

    /** 彻底删除回收站照片 */
    public void deleteFromTrash(String trashName) {
        if (trashName == null || trashName.isEmpty()) {
            return;
        }
        File target = new File(new File(photosDir, "trash"), new File(trashName).getName());
        if (target.exists()) {
            target.delete();
        }
    }
}
