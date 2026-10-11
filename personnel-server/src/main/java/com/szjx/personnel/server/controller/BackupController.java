package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.service.BackupService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 备份恢复接口(均需登录)
 */
@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    /** 模块目录(网页端勾选用) */
    @GetMapping("/modules")
    public ApiResponse modules() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Map<String, String> labels = new HashMap<String, String>();
        labels.put("personnel", "人员管理");
        labels.put("department", "部门管理");
        labels.put("dict", "数据字典");
        labels.put("equipment", "装备管理");
        labels.put("material", "物资管理");
        labels.put("vehicle", "车辆管理");
        labels.put("leave", "休假管理");
        labels.put("fitness", "体能考核");
        for (String key : BackupService.allModuleKeys()) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("key", key);
            m.put("label", labels.containsKey(key) ? labels.get(key) : key);
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    @GetMapping("/settings")
    public ApiResponse settings() {
        return ApiResponse.ok(backupService.loadSettings());
    }

    @PostMapping("/settings")
    public ApiResponse saveSettings(@RequestBody BackupService.Settings body) {
        backupService.saveSettings(body);
        return ApiResponse.ok(backupService.loadSettings());
    }

    /** 立即备份,body.modules 为模块 key 数组 */
    @PostMapping("/run")
    public ApiResponse run(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> modules = (List<String>) body.get("modules");
        return ApiResponse.ok(backupService.createBackup(modules, false));
    }

    @GetMapping("/list")
    public ApiResponse list() {
        return ApiResponse.ok(backupService.listBackups());
    }

    /** 恢复,body.modules 为要恢复的模块 key 数组(取与该备份的交集) */
    @PostMapping("/restore")
    public ApiResponse restore(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.get("name"));
        @SuppressWarnings("unchecked")
        List<String> modules = (List<String>) body.get("modules");
        if (modules == null) {
            modules = new ArrayList<String>();
        }
        return ApiResponse.ok(backupService.restore(name, modules));
    }

    @PostMapping("/delete")
    public ApiResponse delete(@RequestBody Map<String, Object> body) {
        backupService.delete(String.valueOf(body.get("name")));
        return ApiResponse.ok();
    }

    /** 下载备份文件 */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam String name) {
        File f = backupService.getBackupFile(name);
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            String encoded = URLEncoder.encode(f.getName(), "UTF-8").replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}
