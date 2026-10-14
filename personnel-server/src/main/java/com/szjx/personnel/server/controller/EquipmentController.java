package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.Equipment;
import com.szjx.personnel.server.model.PageResult;
import com.szjx.personnel.server.service.EquipmentService;
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
 * 装备接口
 */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public EquipmentController(EquipmentService equipmentService,
                               org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.equipmentService = equipmentService;
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String name,
                             @RequestParam(required = false) String code,
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
        StringBuilder sql = new StringBuilder(
            "SELECT e.id, e.name, e.code, e.category, e.model, e.status, e.keeper_id AS keeperId, " +
            "p.name AS keeperName, p.id_card AS keeperIdCard, e.location, e.buy_date AS buyDate, " +
            "e.equip_date AS equipDate, e.remark, e.created_at AS createdAt, e.updated_at AS updatedAt " +
            "FROM equipment e LEFT JOIN personnel p ON e.keeper_id = p.id WHERE 1=1");
        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM equipment e LEFT JOIN personnel p ON e.keeper_id = p.id WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<Object>();
        java.util.List<Object> cargs = new java.util.ArrayList<Object>();
        if (keyword != null && !keyword.trim().isEmpty()) { keyword = keyword.trim(); }
        if (name != null && !name.trim().isEmpty()) {
            sql.append(" AND e.name LIKE ?"); args.add("%" + name.trim() + "%");
            csql.append(" AND e.name LIKE ?"); cargs.add("%" + name.trim() + "%");
        }
        if (code != null && !code.trim().isEmpty()) {
            sql.append(" AND e.code LIKE ?"); args.add("%" + code.trim() + "%");
            csql.append(" AND e.code LIKE ?"); cargs.add("%" + code.trim() + "%");
        }
        if (category != null && !category.trim().isEmpty()) {
            sql.append(" AND e.category = ?"); args.add(category.trim());
            csql.append(" AND e.category = ?"); cargs.add(category.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND e.status = ?"); args.add(status.trim());
            csql.append(" AND e.status = ?"); cargs.add(status.trim());
        }
        if (keeperName != null && !keeperName.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ?"); args.add("%" + keeperName.trim() + "%");
            csql.append(" AND p.name LIKE ?"); cargs.add("%" + keeperName.trim() + "%");
        }
        if (location != null && !location.trim().isEmpty()) {
            sql.append(" AND e.location LIKE ?"); args.add("%" + location.trim() + "%");
            csql.append(" AND e.location LIKE ?"); cargs.add("%" + location.trim() + "%");
        }
        if (buyDateStart != null && !buyDateStart.trim().isEmpty()) {
            sql.append(" AND e.buy_date >= ?"); args.add(buyDateStart.trim());
            csql.append(" AND e.buy_date >= ?"); cargs.add(buyDateStart.trim());
        }
        if (buyDateEnd != null && !buyDateEnd.trim().isEmpty()) {
            sql.append(" AND e.buy_date <= ?"); args.add(buyDateEnd.trim());
            csql.append(" AND e.buy_date <= ?"); cargs.add(buyDateEnd.trim());
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());
        sql.append(" ORDER BY e.updated_at DESC, e.id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((long) (page - 1) * size);
        java.util.List<java.util.Map<String, Object>> list = jdbc.queryForList(sql.toString(), args.toArray());
        return ApiResponse.ok(new PageResult<java.util.Map<String, Object>>(total == null ? 0 : total, list));
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
