package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.Vehicle;
import com.szjx.personnel.server.service.VehicleService;
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
 * 车辆接口
 */
@RestController
@RequestMapping("/api/vehicle")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String plateNo,
                             @RequestParam(required = false) String vin,
                             @RequestParam(required = false) String type,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String brand,
                             @RequestParam(required = false) String model,
                             @RequestParam(required = false) String driverName,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 20;
        return ApiResponse.ok(vehicleService.query(plateNo, vin, type, status, brand, model,
                driverName, page, size));
    }


    @GetMapping("/{id:\\d+}/photo")
    public org.springframework.http.ResponseEntity<byte[]> photo(@org.springframework.web.bind.annotation.PathVariable Long id) {
        Vehicle vehicle = vehicleService.get(id);
        if (vehicle == null || vehicle.getPhoto() == null || vehicle.getPhoto().isEmpty()) return org.springframework.http.ResponseEntity.notFound().build();
        java.io.File file = vehicleService.photoFile(vehicle.getPhoto());
        if (!file.isFile()) return org.springframework.http.ResponseEntity.notFound().build();
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            org.springframework.http.MediaType type = file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png")
                    ? org.springframework.http.MediaType.IMAGE_PNG : org.springframework.http.MediaType.IMAGE_JPEG;
            return org.springframework.http.ResponseEntity.ok().contentType(type).body(bytes);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("读取车辆照片失败", e);
        }
    }

    @org.springframework.web.bind.annotation.PostMapping
    public ApiResponse create(@RequestBody Vehicle v) {
        return ApiResponse.ok(vehicleService.create(v));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Vehicle v) {
        return ApiResponse.ok(vehicleService.update(id, v));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        vehicleService.delete(id);
        return ApiResponse.ok();
    }
}
