package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.auth.DataScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 手机设备数据访问:SQL 自 PhoneDeviceController 下沉(查询筛选/增删改/人员手机号镜像)
 */
@Repository
public class PhoneDeviceDao {

    private final JdbcTemplate jdbc;

    public PhoneDeviceDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count(Long personnelId, String personnelName, String idCard, String deviceType, String brand, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM phone_device l LEFT JOIN personnel p ON l.personnel_id = p.id WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        appendFilters(sql, args, personnelId, personnelName, idCard, deviceType, brand, status);
        Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public List<Map<String, Object>> query(Long personnelId, String personnelName, String idCard, String deviceType,
                                           String brand, String status, int page, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT l.id, l.personnel_id AS personnelId, p.name AS personnelName, p.id_card AS idCard, p.phone AS personnelPhone, " +
                "l.device_type AS deviceType, l.brand, l.model, l.serial_no AS serialNo, " +
                "l.imei1, l.imei2, l.phone_number AS phoneNumber, l.purchase_date AS purchaseDate, " +
                "l.status, l.remark, l.created_at AS createdAt, p.department_id AS pDepartmentId " +
                "FROM phone_device l LEFT JOIN personnel p ON l.personnel_id = p.id WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        appendFilters(sql, args, personnelId, personnelName, idCard, deviceType, brand, status);
        sql.append(" ORDER BY l.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((long) (page - 1) * size);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    private void appendFilters(StringBuilder sql, List<Object> args, Long personnelId, String personnelName,
                               String idCard, String deviceType, String brand, String status) {
        sql.append(" AND ").append(DataScope.personSql("l.personnel_id"));
        if (personnelId != null) { sql.append(" AND l.personnel_id = ?"); args.add(personnelId); }
        if (notBlank(personnelName)) { sql.append(" AND p.name LIKE ?"); args.add("%" + personnelName.trim() + "%"); }
        if (notBlank(idCard)) { sql.append(" AND p.id_card LIKE ?"); args.add("%" + idCard.trim() + "%"); }
        if (notBlank(deviceType)) { sql.append(" AND l.device_type = ?"); args.add(deviceType.trim()); }
        if (notBlank(brand)) { sql.append(" AND l.brand LIKE ?"); args.add("%" + brand.trim() + "%"); }
        if (notBlank(status)) { sql.append(" AND l.status = ?"); args.add(status.trim()); }
    }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    public void insert(Map<String, Object> r) {
        jdbc.update("INSERT INTO phone_device(personnel_id, device_type, brand, model, serial_no, imei1, imei2, phone_number, purchase_date, status, remark) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                lng(r.get("personnelId")), str(r.get("deviceType")), str(r.get("brand")), str(r.get("model")), str(r.get("serialNo")),
                str(r.get("imei1")), str(r.get("imei2")), str(r.get("phoneNumber")),
                str(r.get("purchaseDate")), str(r.getOrDefault("status", "在用")), str(r.get("remark")));
    }

    /** 记录所属人员 id;记录不存在返回 null */
    public Long personnelIdOf(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT personnel_id FROM phone_device WHERE id=?", id);
        return rows.isEmpty() ? null : lng(rows.get(0).get("personnel_id"));
    }

    public int update(long id, Map<String, Object> r) {
        return jdbc.update("UPDATE phone_device SET personnel_id=?, device_type=?, brand=?, model=?, serial_no=?, imei1=?, imei2=?, phone_number=?, purchase_date=?, status=?, remark=?, " +
                        "updated_at=datetime('now','localtime') WHERE id=?",
                lng(r.get("personnelId")), str(r.get("deviceType")), str(r.get("brand")), str(r.get("model")),
                str(r.get("serialNo")), str(r.get("imei1")), str(r.get("imei2")),
                str(r.get("phoneNumber")), str(r.get("purchaseDate")),
                str(r.getOrDefault("status", "在用")), str(r.get("remark")), id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM phone_device WHERE id=?", id);
    }

    /** 某人的所有手机设备(人员管理联动查看) */
    public List<Map<String, Object>> byPerson(long personnelId) {
        return jdbc.queryForList(
                "SELECT l.*, p.name AS personnelName FROM phone_device l " +
                "LEFT JOIN personnel p ON l.personnel_id = p.id WHERE l.personnel_id=? ORDER BY l.id DESC", personnelId);
    }

    /** 人员手机号镜像:取最新一个有号码的设备,无则清空 */
    public void syncPersonnelPhone(long pid) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT phone_number FROM phone_device WHERE personnel_id=? AND TRIM(COALESCE(phone_number,''))<>'' ORDER BY id DESC LIMIT 1", pid);
        String pn = rows.isEmpty() ? "" : String.valueOf(rows.get(0).get("phone_number")).trim();
        jdbc.update("UPDATE personnel SET phone=? WHERE id=?", pn, pid);
    }

    public boolean personExists(long pid) {
        return !jdbc.queryForList("SELECT id FROM personnel WHERE id=?", pid).isEmpty();
    }

    /** 列表附带人员编制单位完整路径(无斜杠层级) */
    public void fillDepartmentPath(List<Map<String, Object>> list) {
        com.szjx.personnel.server.util.DepartmentHierarchy.fillPath(jdbc, list, "pDepartmentId", "departmentPath");
    }

    public void requireRecord(long id) { DataScope.requireRecord(jdbc, "phone_device", "personnel_id", id); }

    public void requirePerson(long pid) { DataScope.requirePerson(jdbc, pid); }

    public static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    public static Long lng(Object v) {
        if (v == null || String.valueOf(v).trim().isEmpty()) { return 0L; }
        try { return Long.parseLong(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return 0L; }
    }
}
