package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.VehicleFaultHistory;
import com.szjx.personnel.server.service.VehicleFaultHistoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicle/{vehicleId}/fault-history")
public class VehicleFaultHistoryController {
    private final VehicleFaultHistoryService service;

    public VehicleFaultHistoryController(VehicleFaultHistoryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse list(@PathVariable Long vehicleId) {
        return ApiResponse.ok(service.list(vehicleId));
    }

    @PostMapping
    public ApiResponse create(@PathVariable Long vehicleId, @RequestBody VehicleFaultHistory record) {
        return ApiResponse.ok(service.create(vehicleId, record));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long vehicleId, @PathVariable Long id, @RequestBody VehicleFaultHistory record) {
        return ApiResponse.ok(service.update(vehicleId, id, record));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long vehicleId, @PathVariable Long id) {
        service.delete(vehicleId, id);
        return ApiResponse.ok();
    }
}
