package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.PageResult;
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

import java.util.List;

/**
 * 车辆接口
 */
@RestController
@RequestMapping("/api/vehicle")
public class VehicleController {

    private final VehicleService vehicleService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public VehicleController(VehicleService vehicleService,
                             org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.vehicleService = vehicleService;
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String plateNo,
                             @RequestParam(required = false) String vin,
                             @RequestParam(required = false) String type,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String brand,
                             @RequestParam(required = false) String driverName,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "50") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 50;
        StringBuilder sql = new StringBuilder(
            "SELECT v.id, v.plate_no AS plateNo, v.vin, v.engine_no AS engineNo, v.brand, v.type, v.color, " +
            "v.fuel_type AS fuelType, v.status, v.driver_id AS driverId, p.name AS driverName, " +
            "p.id_card AS driverIdCard, v.seats, v.mileage, v.purchase_date AS purchaseDate, " +
            "v.insurance_expiry AS insuranceExpiry, v.inspection_expiry AS inspectionExpiry, " +
            "v.remark, v.created_at AS createdAt, v.updated_at AS updatedAt " +
            "FROM vehicle v LEFT JOIN personnel p ON v.driver_id = p.id WHERE 1=1");
        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM vehicle v LEFT JOIN personnel p ON v.driver_id = p.id WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<Object>();
        java.util.List<Object> cargs = new java.util.ArrayList<Object>();
        if (plateNo != null && !plateNo.trim().isEmpty()) {
            sql.append(" AND v.plate_no LIKE ?"); args.add("%" + plateNo.trim() + "%");
            csql.append(" AND v.plate_no LIKE ?"); cargs.add("%" + plateNo.trim() + "%");
        }
        if (vin != null && !vin.trim().isEmpty()) {
            sql.append(" AND v.vin LIKE ?"); args.add("%" + vin.trim() + "%");
            csql.append(" AND v.vin LIKE ?"); cargs.add("%" + vin.trim() + "%");
        }
        if (type != null && !type.trim().isEmpty()) {
            sql.append(" AND v.type = ?"); args.add(type.trim());
            csql.append(" AND v.type = ?"); cargs.add(type.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND v.status = ?"); args.add(status.trim());
            csql.append(" AND v.status = ?"); cargs.add(status.trim());
        }
        if (brand != null && !brand.trim().isEmpty()) {
            sql.append(" AND v.brand LIKE ?"); args.add("%" + brand.trim() + "%");
            csql.append(" AND v.brand LIKE ?"); cargs.add("%" + brand.trim() + "%");
        }
        if (driverName != null && !driverName.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ?"); args.add("%" + driverName.trim() + "%");
            csql.append(" AND p.name LIKE ?"); cargs.add("%" + driverName.trim() + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());
        sql.append(" ORDER BY v.updated_at DESC, v.id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((page - 1) * size);
        java.util.List<java.util.Map<String, Object>> list = jdbc.queryForList(sql.toString(), args.toArray());
        return ApiResponse.ok(new PageResult<java.util.Map<String, Object>>(total == null ? 0 : total, list));
    }

    @PostMapping
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
