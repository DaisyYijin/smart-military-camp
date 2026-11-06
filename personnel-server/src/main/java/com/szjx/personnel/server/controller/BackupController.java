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
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 备份恢复接口(由全局AuthInterceptor限制为管理员)
 */
@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final BackupService backupService;
    private com.szjx.personnel.server.service.TokenManager tokenManager;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTokenManager(com.szjx.personnel.server.service.TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    /** 模块目录(网页端勾选用) */
    @GetMapping("/modules")
    public ApiResponse modules() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Map<String, String> labels = new HashMap<String, String>();
        labels.put("personnel", "人员管理");
        labels.put("department", "单位管理");
        labels.put("dict", "数据字典/系统数据");
        labels.put("dashboard", "仪表盘");
        labels.put("equipment", "装备管理");
        labels.put("material", "物资管理");
        labels.put("vehicle", "车辆管理");
        labels.put("leave", "休假管理");
        labels.put("fitness", "体能考核");
        labels.put("phone", "手机设备");
        labels.put("military", "军事考核");
        labels.put("item-loans", "物品借还");
        labels.put("double-excellence", "双争评比");
        labels.put("annual-appraisal", "年度考评");
        labels.put("absence", "在外登记");
        labels.put("organization", "组织建设");
        for (String key : BackupService.allModuleKeys()) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("key", key);
            m.put("label", labels.containsKey(key) ? labels.get(key) : key);
            boolean photos = java.util.Arrays.asList("personnel", "equipment", "material", "vehicle").contains(key);
            m.put("includesPhotos", photos);
            if (photos) { m.put("photoScope", "personnel".equals(key) ? "人员直属照片及删除撤回记录引用的回收站照片,不含资产目录和未引用回收站照片" : "本模块每资产一张照片,单张最多100MB"); }
            if ("dict".equals(key)) { m.put("accountScope", "账号及单位授权成对恢复;缺授权表的旧包保留本地账号及授权并返回警告"); }
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

    /** 单个本系统ZIP最多100MB;仅校验保存,恢复须另行选择模块确认。全局multipart请求上限仍为120MB。 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse upload(@RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.ok(backupService.upload(file));
    }

    /** 恢复,body.modules 为要恢复的模块 key 数组;缺失模块拒绝,兼容性告警见 data.warnings。 */
    @PostMapping("/restore")
    public ApiResponse restore(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.get("name"));
        @SuppressWarnings("unchecked")
        List<String> modules = (List<String>) body.get("modules");
        if (modules == null) {
            modules = new ArrayList<String>();
        }
        Map<String, Object> result = backupService.restore(name, modules);
        boolean securityChanged = Boolean.TRUE.equals(result.get("securityStateRestored"));
        if (securityChanged && tokenManager != null) { tokenManager.revokeAll(); }
        result.put("requiresRelogin", securityChanged);
        return ApiResponse.ok(result);
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
