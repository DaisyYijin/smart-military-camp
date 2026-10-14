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

    public void save(String fileName, byte[] bytes) {
        File target = new File(photosDir, fileName);
        try {
            FileOutputStream out = new FileOutputStream(target);
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("保存照片失败", e);
        }
    }

    public void delete(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        File target = new File(photosDir, fileName);
        if (target.exists()) {
            target.delete();
        }
    }

    public File getFile(String fileName) {
        return new File(photosDir, fileName);
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
