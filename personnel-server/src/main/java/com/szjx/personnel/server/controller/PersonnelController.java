package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.AuthInterceptor;
import com.szjx.personnel.server.dao.DepartmentDao;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.PageResult;
import com.szjx.personnel.server.model.Personnel;
import com.szjx.personnel.server.service.PersonnelService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public PersonnelController(PersonnelService personnelService, DepartmentDao departmentDao,
                               org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.personnelService = personnelService;
        this.departmentDao = departmentDao;
        this.jdbc = jdbc;
    }

    /** 按身份证号精确查找人员(各模块表单自动带出姓名用),找不到返回 null */
    @GetMapping("/by-idcard")
    public ApiResponse findByIdCard(@RequestParam String idCard) {
        java.util.List<java.util.Map<String, Object>> list = jdbc.queryForList(
            "SELECT p.id, p.name, p.id_card AS idCard, d.name AS departmentName FROM personnel p " +
            "LEFT JOIN department d ON p.department_id = d.id WHERE p.id_card = ? COLLATE NOCASE LIMIT 1",
            idCard == null ? "" : idCard.trim());
        return ApiResponse.ok(list.isEmpty() ? null : list.get(0));
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String name,
                             @RequestParam(required = false) String idCard,
                             @RequestParam(required = false) Long departmentId,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String gender,
                             @RequestParam(required = false) String politicalStatus,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String rank,
                             @RequestParam(required = false) String post,
                             @RequestParam(required = false) String nativePlace,
                             @RequestParam(required = false) String education,
                             @RequestParam(required = false) String maritalStatus,
                             @RequestParam(required = false) String nation,
                             @RequestParam(required = false) String bloodType,
                             @RequestParam(required = false) String onlyChild,
                             @RequestParam(required = false) String entryDateStart,
                             @RequestParam(required = false) String entryDateEnd,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 20;
        }
        List<Personnel> list = personnelService.query(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, education, maritalStatus, nation, bloodType, onlyChild, entryDateStart, entryDateEnd, page, size);
        long total = personnelService.count(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, education, maritalStatus, nation, bloodType, onlyChild, entryDateStart, entryDateEnd);
        return ApiResponse.ok(new PageResult<Personnel>(total, list));
    }

    @GetMapping("/options")
    public ApiResponse options() {
        return ApiResponse.ok(personnelService.findAllOptions());
    }

    @GetMapping("/{id:\\d+}")
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
    @GetMapping("/{id:\\d+}/photo")
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

    @PutMapping("/{id:\\d+}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Personnel p) {
        return ApiResponse.ok(personnelService.update(id, p));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse delete(@PathVariable Long id) {
        personnelService.delete(id);
        return ApiResponse.ok();
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
