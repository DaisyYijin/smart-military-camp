package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 手机设备管理:手机/手表/手环等智能终端
 */
@RestController
@RequestMapping("/api/phone")
public class PhoneDeviceController {

    private final JdbcTemplate jdbc;

    public PhoneDeviceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String personnelName,
                             @RequestParam(required = false) String idCard,
                             @RequestParam(required = false) String deviceType,
                             @RequestParam(required = false) String brand,
                             @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "50") int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 50;
        StringBuilder sql = new StringBuilder(
                "SELECT l.id, l.personnel_id AS personnelId, p.name AS personnelName, p.id_card AS idCard, p.phone AS personnelPhone, " +
                "l.device_type AS deviceType, l.brand, l.model, l.serial_no AS serialNo, " +
                "l.imei1, l.imei2, l.phone_number AS phoneNumber, l.purchase_date AS purchaseDate, " +
                "l.status, l.remark, l.created_at AS createdAt " +
                "FROM phone_device l LEFT JOIN personnel p ON l.personnel_id = p.id WHERE 1=1");
        StringBuilder csql = new StringBuilder(
                "SELECT COUNT(*) FROM phone_device l LEFT JOIN personnel p ON l.personnel_id = p.id WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        List<Object> cargs = new ArrayList<Object>();
        if (personnelName != null && !personnelName.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ?"); args.add("%" + personnelName.trim() + "%");
            csql.append(" AND p.name LIKE ?"); cargs.add("%" + personnelName.trim() + "%");
        }
        if (idCard != null && !idCard.trim().isEmpty()) {
            sql.append(" AND p.id_card LIKE ?"); args.add("%" + idCard.trim() + "%");
            csql.append(" AND p.id_card LIKE ?"); cargs.add("%" + idCard.trim() + "%");
        }
        if (deviceType != null && !deviceType.trim().isEmpty()) {
            sql.append(" AND l.device_type = ?"); args.add(deviceType.trim());
            csql.append(" AND l.device_type = ?"); cargs.add(deviceType.trim());
        }
        if (brand != null && !brand.trim().isEmpty()) {
            sql.append(" AND l.brand LIKE ?"); args.add("%" + brand.trim() + "%");
            csql.append(" AND l.brand LIKE ?"); cargs.add("%" + brand.trim() + "%");
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND l.status = ?"); args.add(status.trim());
            csql.append(" AND l.status = ?"); cargs.add(status.trim());
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());
        sql.append(" ORDER BY l.id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((page - 1) * size);
        List<Map<String, Object>> list = jdbc.queryForList(sql.toString(), args.toArray());
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("total", total == null ? 0 : total);
        result.put("list", list);
        return ApiResponse.ok(result);
    }

    @PostMapping
    public ApiResponse create(@RequestBody Map<String, Object> r) {
        Long pid = lng(r.get("personnelId"));
        if (pid == 0) { return ApiResponse.error("请先填写身份证号带出人员"); }
        if (jdbc.queryForList("SELECT id FROM personnel WHERE id=?", pid).isEmpty()) {
            return ApiResponse.error("所选人员不存在");
        }
        String deviceType = str(r.get("deviceType"));
        if (deviceType.isEmpty()) { return ApiResponse.error("请选择设备类型"); }
        String brand = str(r.get("brand"));
        if (brand.isEmpty()) { return ApiResponse.error("请填写品牌"); }
        jdbc.update("INSERT INTO phone_device(personnel_id, device_type, brand, model, serial_no, imei1, imei2, phone_number, purchase_date, status, remark) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                pid, deviceType, brand, str(r.get("model")), str(r.get("serialNo")),
                str(r.get("imei1")), str(r.get("imei2")), str(r.get("phoneNumber")),
                str(r.get("purchaseDate")), str(r.getOrDefault("status", "在用")), str(r.get("remark")));
        // 联动:如果填了手机号且人员联系电话为空,自动更新
        String pn = str(r.get("phoneNumber"));
        if (!pn.isEmpty()) {
            jdbc.update("UPDATE personnel SET phone=? WHERE id=? AND (phone IS NULL OR phone='')", pn, pid);
        }
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Map<String, Object> r) {
        Long pid = lng(r.get("personnelId"));
        if (pid == 0) { return ApiResponse.error("请先填写身份证号带出人员"); }
        int n = jdbc.update("UPDATE phone_device SET personnel_id=?, device_type=?, brand=?, model=?, serial_no=?, imei1=?, imei2=?, phone_number=?, purchase_date=?, status=?, remark=?, " +
                        "updated_at=datetime('now','localtime') WHERE id=?",
                pid, str(r.get("deviceType")), str(r.get("brand")), str(r.get("model")),
                str(r.get("serialNo")), str(r.get("imei1")), str(r.get("imei2")),
                str(r.get("phoneNumber")), str(r.get("purchaseDate")),
                str(r.getOrDefault("status", "在用")), str(r.get("remark")), id);
        if (n == 0) { return ApiResponse.error("记录不存在或已被删除"); }
        String pn = str(r.get("phoneNumber"));
        if (!pn.isEmpty()) {
            jdbc.update("UPDATE personnel SET phone=? WHERE id=? AND (phone IS NULL OR phone='')", pn, pid);
        }
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM phone_device WHERE id=?", id);
        return ApiResponse.ok();
    }

    /** 某人的所有手机设备(人员管理联动查看) */
    @GetMapping("/by-person")
    public ApiResponse byPerson(@RequestParam Long personnelId) {
        return ApiResponse.ok(jdbc.queryForList(
                "SELECT l.*, p.name AS personnelName FROM phone_device l " +
                "LEFT JOIN personnel p ON l.personnel_id = p.id WHERE l.personnel_id=? ORDER BY l.id DESC", personnelId));
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Long lng(Object v) {
        if (v == null || String.valueOf(v).trim().isEmpty()) { return 0L; }
        try { return Long.parseLong(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return 0L; }
    }
}
