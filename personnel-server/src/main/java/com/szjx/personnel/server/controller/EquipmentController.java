package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.Equipment;
import com.szjx.personnel.server.service.EquipmentService;
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
 * 装备接口
 */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String name,
                             @RequestParam(required = false) String code,
                             @RequestParam(required = false) String identCode,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String keeperName,
                             @RequestParam(required = false) String location,
                             @RequestParam(required = false) String buyDateStart,
                             @RequestParam(required = false) String buyDateEnd,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 20;
        return ApiResponse.ok(equipmentService.query(name, code, identCode, category, status,
                keeperName, location, buyDateStart, buyDateEnd, page, size));
    }

    @GetMapping("/{id:\\d+}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        Equipment equipment = equipmentService.get(id);
        if (equipment == null || equipment.getPhoto() == null || equipment.getPhoto().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        java.io.File file = equipmentService.photoFile(equipment.getPhoto());
        if (file == null || !file.exists()) return ResponseEntity.notFound().build();
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            MediaType type = equipment.getPhoto().toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok().contentType(type).body(bytes);
        } catch (java.io.IOException e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping
    public ApiResponse create(@RequestBody Equipment e) {
        return ApiResponse.ok(equipmentService.create(e));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Equipment e) {
        return ApiResponse.ok(equipmentService.update(id, e));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        equipmentService.delete(id);
        return ApiResponse.ok();
    }
}
