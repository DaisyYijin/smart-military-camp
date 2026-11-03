package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.Material;
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

/**
 * 物资接口
 */
@RestController
@RequestMapping("/api/material")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
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
        return ApiResponse.ok(materialService.query(name, category, spec, keeperName, location, page, size));
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
