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
}
