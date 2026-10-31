package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.DataScope;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.Material;
import com.szjx.personnel.server.model.PageResult;
import com.szjx.personnel.server.service.MaterialService;
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

import java.util.List;

/**
 * 物资接口
 */
@RestController
@RequestMapping("/api/material")
public class MaterialController {

    private final MaterialService materialService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public MaterialController(MaterialService materialService,
                              org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.materialService = materialService;
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String name,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String spec,
                             @RequestParam(required = false) String keeperName,
                             @RequestParam(required = false) String location,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "50") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 50;
        StringBuilder sql = new StringBuilder(
            "SELECT m.id, m.name, m.category, m.spec, m.unit, m.quantity, " +
            "m.min_quantity AS minQuantity, m.keeper_id AS keeperId, p.name AS keeperName, " +
            "p.id_card AS keeperIdCard, m.location, m.detail_location AS detailLocation, m.remark, m.photo, " +
            "m.created_at AS createdAt, m.updated_at AS updatedAt " +
            "FROM material m LEFT JOIN personnel p ON m.keeper_id = p.id WHERE 1=1");
        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM material m LEFT JOIN personnel p ON m.keeper_id = p.id WHERE 1=1");
        sql.append(" AND ").append(DataScope.personSql("m.keeper_id"));
        csql.append(" AND ").append(DataScope.personSql("m.keeper_id"));
        java.util.List<Object> args = new java.util.ArrayList<Object>();
        java.util.List<Object> cargs = new java.util.ArrayList<Object>();
        if (name != null && !name.trim().isEmpty()) {
            sql.append(" AND m.name LIKE ?"); args.add("%" + name.trim() + "%");
            csql.append(" AND m.name LIKE ?"); cargs.add("%" + name.trim() + "%");
        }
        if (category != null && !category.trim().isEmpty()) {
            sql.append(" AND m.category = ?"); args.add(category.trim());
            csql.append(" AND m.category = ?"); cargs.add(category.trim());
        }
        if (spec != null && !spec.trim().isEmpty()) {
            sql.append(" AND m.spec LIKE ?"); args.add("%" + spec.trim() + "%");
            csql.append(" AND m.spec LIKE ?"); cargs.add("%" + spec.trim() + "%");
        }
        if (keeperName != null && !keeperName.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ?"); args.add("%" + keeperName.trim() + "%");
            csql.append(" AND p.name LIKE ?"); cargs.add("%" + keeperName.trim() + "%");
        }
        if (location != null && !location.trim().isEmpty()) {
            sql.append(" AND m.location LIKE ?"); args.add("%" + location.trim() + "%");
            csql.append(" AND m.location LIKE ?"); cargs.add("%" + location.trim() + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());
        sql.append(" ORDER BY m.updated_at DESC, m.id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((page - 1) * size);
        java.util.List<java.util.Map<String, Object>> list = jdbc.queryForList(sql.toString(), args.toArray());
        return ApiResponse.ok(new PageResult<java.util.Map<String, Object>>(total == null ? 0 : total, list));
    }

    @GetMapping("/{id:\\d+}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        Material material = materialService.get(id);
        if (material == null || material.getPhoto() == null || material.getPhoto().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        java.io.File file = materialService.photoFile(material.getPhoto());
        if (file == null || !file.exists()) return ResponseEntity.notFound().build();
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            MediaType type = material.getPhoto().toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok().contentType(type).body(bytes);
        } catch (java.io.IOException e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping
    public ApiResponse create(@RequestBody Material m) {
        return ApiResponse.ok(materialService.create(m));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Material m) {
        return ApiResponse.ok(materialService.update(id, m));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        materialService.delete(id);
        return ApiResponse.ok();
    }
}
