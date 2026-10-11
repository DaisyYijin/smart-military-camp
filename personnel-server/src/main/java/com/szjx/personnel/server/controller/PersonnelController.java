package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.AuthInterceptor;
import com.szjx.personnel.server.dao.DepartmentDao;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.PageResult;
import com.szjx.personnel.server.model.Personnel;
import com.szjx.personnel.server.service.PersonnelService;
import com.szjx.personnel.server.util.CsvUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人员接口
 */
@RestController
@RequestMapping("/api/personnel")
public class PersonnelController {

    private final PersonnelService personnelService;
    private final DepartmentDao departmentDao;

    public PersonnelController(PersonnelService personnelService, DepartmentDao departmentDao) {
        this.personnelService = personnelService;
        this.departmentDao = departmentDao;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) Long departmentId,
                             @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 20;
        }
        List<Personnel> list = personnelService.query(keyword, departmentId, status, page, size);
        long total = personnelService.count(keyword, departmentId, status);
        return ApiResponse.ok(new PageResult<Personnel>(total, list));
    }

    @GetMapping("/options")
    public ApiResponse options() {
        return ApiResponse.ok(personnelService.findAllOptions());
    }

    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable Long id) {
        Personnel p = personnelService.findById(id);
        if (p == null) {
            return ApiResponse.error("人员不存在");
        }
        return ApiResponse.ok(p);
    }

    /**
     * 人员照片下载
     */
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        Personnel p = personnelService.findById(id);
        if (p == null || p.getPhoto() == null || p.getPhoto().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        java.io.File f = personnelService.photoFile(p.getPhoto());
        if (f == null || !f.exists()) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            String name = p.getPhoto().toLowerCase();
            MediaType type = name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok().contentType(type).body(bytes);
        } catch (java.io.IOException e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping
    public ApiResponse create(@RequestBody Personnel p) {
        return ApiResponse.ok(personnelService.create(p));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Personnel p) {
        return ApiResponse.ok(personnelService.update(id, p));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        personnelService.delete(id);
        return ApiResponse.ok();
    }

    /**
     * 导出 Excel(xlsx,含样式,Excel 直接打开)
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long departmentId,
                                         @RequestParam(required = false) String status) {
        List<Personnel> list = personnelService.query(keyword, departmentId, status, 1, 100000);
        final Map<Long, String> deptNames = new HashMap<Long, String>();
        departmentDao.findAll().forEach(d -> deptNames.put(d.getId(), d.getName()));
        byte[] bytes;
        try {
            bytes = com.szjx.personnel.server.util.ExcelUtils.exportXlsx(list, id -> {
                String n = deptNames.get(id);
                return n == null ? "" : n;
            });
        } catch (java.io.IOException e) {
            throw new RuntimeException("导出失败", e);
        }
        String fileName = "人员信息_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".xlsx";
        String encoded;
        try {
            encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            encoded = "export.xlsx";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    /**
     * Excel 批量导入(上传 xlsx,按身份证号匹配新增或更新)
     */
    @PostMapping("/import-excel")
    public ApiResponse importExcel(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("请选择要导入的 Excel 文件");
        }
        List<Personnel> rows;
        try {
            rows = com.szjx.personnel.server.util.ExcelUtils.importXlsx(file.getInputStream());
        } catch (Exception e) {
            return ApiResponse.error("文件解析失败,请确认是 .xlsx 格式且第一行为列头(建议使用系统导出的模板)");
        }
        if (rows.isEmpty()) {
            return ApiResponse.error("文件中没有可导入的数据(第一行为列头,请使用导出的模板)");
        }
        int[] result = personnelService.importRows(rows);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("added", result[0]);
        data.put("updated", result[1]);
        data.put("failed", result[2]);
        return ApiResponse.ok(data);
    }

    /**
     * 批量导入(JSON 数组,按身份证号匹配新增或更新)
     */
    @PostMapping("/import")
    public ApiResponse importRows(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
        if (rows == null || rows.isEmpty()) {
            return ApiResponse.error("没有可导入的数据");
        }
        List<Personnel> personnel = new java.util.ArrayList<Personnel>();
        for (Map<String, Object> row : rows) {
            Personnel p = new Personnel();
            p.setName(str(row.get("name")));
            p.setGender(str(row.get("gender")));
            p.setIdCard(str(row.get("idCard")));
            p.setBirthday(str(row.get("birthday")));
            p.setPhone(str(row.get("phone")));
            String dept = str(row.get("departmentName"));
            if (dept.isEmpty()) {
                dept = str(row.get("department")); // 兼容旧字段名
            }
            p.setDepartmentName(dept);
            p.setRank(str(row.get("rank")));
            p.setPost(str(row.get("post")));
            p.setStatus(str(row.get("status")));
            p.setEntryDate(str(row.get("entryDate")));
            p.setAddress(str(row.get("address")));
            p.setRemark(str(row.get("remark")));
            p.setBloodType(str(row.get("bloodType")));
            p.setNation(str(row.get("nation")));
            p.setPoliticalStatus(str(row.get("politicalStatus")));
            p.setMaritalStatus(str(row.get("maritalStatus")));
            p.setEducation(str(row.get("education")));
            p.setOnlyChild(str(row.get("onlyChild")));
            p.setNativePlace(str(row.get("nativePlace")));
            p.setPartyJoinDate(str(row.get("partyJoinDate")));
            p.setSpouseName(str(row.get("spouseName")));
            p.setSpouseWork(str(row.get("spouseWork")));
            p.setSpousePhone(str(row.get("spousePhone")));
            p.setSpouseIdCard(str(row.get("spouseIdCard")));
            p.setSpouseJob(str(row.get("spouseJob")));
            p.setMarriageDate(str(row.get("marriageDate")));
            Object h = row.get("height");
            p.setHeight(h == null ? null : Double.valueOf(String.valueOf(h)));
            Object w = row.get("weight");
            p.setWeight(w == null ? null : Double.valueOf(String.valueOf(w)));
            personnel.add(p);
        }
        int[] result = personnelService.importRows(personnel);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("added", result[0]);
        data.put("updated", result[1]);
        data.put("failed", result[2]);
        return ApiResponse.ok(data);
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
