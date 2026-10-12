package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.service.ModuleExcelService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 通用模块 Excel 导入导出:
 *   GET  /api/{module}/export?ids=1,2,3   导出勾选的记录
 *   POST /api/{module}/import-excel      上传 xlsx 导入(存在则更新,不存在则新增)
 */
@RestController
public class ModuleExcelController {

    private final ModuleExcelService excelService;
    private final com.szjx.personnel.server.service.PersonnelFullExcel fullExcel;

    public ModuleExcelController(ModuleExcelService excelService,
                                 com.szjx.personnel.server.service.PersonnelFullExcel fullExcel) {
        this.excelService = excelService;
        this.fullExcel = fullExcel;
    }

    @GetMapping("/api/{module}/export")
    public ResponseEntity<byte[]> export(@PathVariable String module,
                                         @RequestParam(required = false) String ids) {
        if (!excelService.supports(module)) {
            return ResponseEntity.notFound().build();
        }
        List<Long> idList = new ArrayList<Long>();
        if (ids != null && !ids.trim().isEmpty()) {
            for (String s : ids.split(",")) {
                try {
                    idList.add(Long.parseLong(s.trim()));
                } catch (NumberFormatException ignore) {
                    // 跳过非法 id
                }
            }
        }
        byte[] bytes;
        try {
            if ("personnel".equals(module)) {
                bytes = fullExcel.export(idList);
            } else {
                bytes = excelService.export(module, idList);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"code\":1,\"message\":\"" + e.getMessage() + "\",\"data\":null}")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
        String fileName = excelService.moduleName(module) + "_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx";
        String encoded;
        try {
            encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            encoded = "export.xlsx";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(new MediaType("application",
                        "vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/api/{module}/template")
    public ResponseEntity<byte[]> template(@PathVariable String module) {
        if (!"personnel".equals(module)) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] bytes = fullExcel.template();
            String fileName = "人员信息导入模板.xlsx";
            String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename*=UTF-8''" + encoded)
                    .contentType(new MediaType("application",
                            "vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/api/{module}/import-excel")
    public ApiResponse importExcel(@PathVariable String module,
                                   @RequestParam("file") MultipartFile file) {
        if (!excelService.supports(module)) {
            return ApiResponse.error("不支持的模块: " + module);
        }
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("请选择要导入的 Excel 文件");
        }
        try {
            Map<String, Object> result;
            if ("personnel".equals(module)) {
                result = fullExcel.importAll(file.getInputStream());
            } else {
                result = excelService.importExcel(module, file.getInputStream());
            }
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("导入失败:" + e.getMessage());
        }
    }
}
