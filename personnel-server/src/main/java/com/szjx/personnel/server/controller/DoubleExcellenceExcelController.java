package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.service.DoubleExcellenceExcelService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Literal routes take precedence over /api/{module}/* without changing the generic Excel implementation. */
@RestController
@RequestMapping("/api/double-excellence")
public class DoubleExcellenceExcelController {
    private static final MediaType XLSX = new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final DoubleExcellenceExcelService excel;

    public DoubleExcellenceExcelController(DoubleExcellenceExcelService excel) {
        this.excel = excel;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() throws IOException {
        return download(excel.template(), "双争评比导入模板.xlsx", "double-excellence-template.xlsx");
    }

    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择非空Excel文件");
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("Excel文件不得超过10MiB");
        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xls") && !lower.endsWith(".xlsx")) {
            throw new IllegalArgumentException("请上传.xls或.xlsx格式Excel文件");
        }
        // The suffix is only a user-facing guard. WorkbookFactory checks the actual bytes in the service.
        try (InputStream input = file.getInputStream()) {
            return ApiResponse.ok(excel.importExcel(input));
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(value = "ids", required = false) String ids,
                                         @RequestParam Map<String, String> filters) throws IOException {
        byte[] bytes = excel.export(ids, filters);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return download(bytes, "双争评比_" + timestamp + ".xlsx", "double-excellence-" + timestamp + ".xlsx");
    }

    private static ResponseEntity<byte[]> download(byte[] bytes, String filename, String fallback) throws IOException {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded)
                .contentType(XLSX)
                .body(bytes);
    }
}
