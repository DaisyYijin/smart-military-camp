package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.auth.DataScope;

import com.szjx.personnel.server.model.Vehicle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 车辆数据访问
 */
@Repository
public class VehicleDao {

    private final JdbcTemplate jdbc;

    public VehicleDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Vehicle> MAPPER = new RowMapper<Vehicle>() {
        @Override
        public Vehicle mapRow(ResultSet rs, int i) throws SQLException {
            Vehicle v = new Vehicle();
            v.setId(rs.getLong("id"));
            v.setPlateNo(rs.getString("plate_no"));
            v.setVin(rs.getString("vin"));
            v.setEngineNo(rs.getString("engine_no"));
            v.setBrand(rs.getString("brand"));
            v.setModel(rs.getString("model"));
            v.setType(rs.getString("type"));
            v.setColor(rs.getString("color"));
            v.setFuelType(rs.getString("fuel_type"));
            v.setStatus(rs.getString("status"));
            v.setDriverId(rs.getLong("driver_id"));
            v.setDriverName(rs.getString("driver_name"));
            v.setSeats(rs.getObject("seats") == null ? null : rs.getInt("seats"));
            v.setMileage(rs.getObject("mileage") == null ? null : rs.getDouble("mileage"));
            v.setPurchaseDate(rs.getString("purchase_date"));
            v.setEquipDate(rs.getString("equip_date"));
            v.setInsuranceExpiry(rs.getString("insurance_expiry"));
            v.setInspectionExpiry(rs.getString("inspection_expiry"));
            v.setRemark(rs.getString("remark"));
            v.setPhoto(rs.getString("photo"));
            v.setCreatedAt(rs.getString("created_at"));
            v.setUpdatedAt(rs.getString("updated_at"));
            return v;
        }
    };

    private static final String BASE =
            "SELECT v.*, IFNULL(p.name,'') AS driver_name FROM vehicle v " +
            "LEFT JOIN personnel p ON v.driver_id = p.id ";

    /** 列表查询(驼峰别名列,与前端字段一一对应);SQL 统一住址在 dao,controller 只做参数绑定 */
    public List<Map<String, Object>> query(String plateNo, String vin, String type, String status,
            String brand, String model, String driverName, int page, int size) {
        StringBuilder sql = new StringBuilder(
            "SELECT v.id, v.plate_no AS plateNo, v.vin, v.engine_no AS engineNo, v.brand, v.model, v.type, v.color, " +
            "v.fuel_type AS fuelType, v.status, v.driver_id AS driverId, p.name AS driverName, " +
            "p.id_card AS driverIdCard, v.seats, v.mileage, v.purchase_date AS purchaseDate, " +
            "v.insurance_expiry AS insuranceExpiry, v.inspection_expiry AS inspectionExpiry, v.equip_date AS equipDate, " +
            "v.remark, v.photo, v.created_at AS createdAt, v.updated_at AS updatedAt " +
            "FROM vehicle v LEFT JOIN personnel p ON v.driver_id = p.id WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        appendFilters(sql, args, plateNo, vin, type, status, brand, model, driverName);
        sql.append(" ORDER BY v.updated_at DESC, v.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((long) (page - 1) * size);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public long count(String plateNo, String vin, String type, String status, String brand,
            String model, String driverName) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM vehicle v LEFT JOIN personnel p ON v.driver_id = p.id WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        appendFilters(sql, args, plateNo, vin, type, status, brand, model, driverName);
        Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private void appendFilters(StringBuilder sql, List<Object> args, String plateNo, String vin,
            String type, String status, String brand, String model, String driverName) {
        sql.append(" AND ").append(DataScope.personSql("v.driver_id"));
        if (plateNo != null && !plateNo.trim().isEmpty()) {
            sql.append(" AND v.plate_no LIKE ?"); args.add("%" + plateNo.trim() + "%");
        }
        if (vin != null && !vin.trim().isEmpty()) {
            sql.append(" AND v.vin LIKE ?"); args.add("%" + vin.trim() + "%");
        }
        if (type != null && !type.trim().isEmpty()) {
            sql.append(" AND v.type = ?"); args.add(type.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND v.status = ?"); args.add(status.trim());
        }
        if (brand != null && !brand.trim().isEmpty()) {
            sql.append(" AND v.brand = ?"); args.add(brand.trim());
        }
        if (model != null && !model.trim().isEmpty()) {
            sql.append(" AND v.model LIKE ?"); args.add("%" + model.trim() + "%");
        }
        if (driverName != null && !driverName.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ?"); args.add("%" + driverName.trim() + "%");
        }
    }

    public Vehicle findById(Long id) {
        DataScope.requireRecord(jdbc, "vehicle", "driver_id", id);
        List<Vehicle> list = jdbc.query(BASE + " WHERE v.id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(Vehicle v) {
        requireOwner(v.getDriverId());
        return jdbc.queryForObject(
                "INSERT INTO vehicle(plate_no, vin, engine_no, brand, model, type, color, fuel_type, status, driver_id, " +
                "seats, mileage, purchase_date, equip_date, insurance_expiry, inspection_expiry, remark, photo) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id",
                Long.class,
                nvl(v.getPlateNo()), nvl(v.getVin()), nvl(v.getEngineNo()),
                nvl(v.getBrand()), nvl(v.getModel()), nvl(v.getType()), nvl(v.getColor()), nvl(v.getFuelType()), nvl(v.getStatus()),
                v.getDriverId(), v.getSeats(), v.getMileage(),
                nvl(v.getPurchaseDate()), nvl(v.getEquipDate()), nvl(v.getInsuranceExpiry()), nvl(v.getInspectionExpiry()),
                nvl(v.getRemark()), nvl(v.getPhoto()));
    }

    public void update(Vehicle v) {
        DataScope.requireRecord(jdbc, "vehicle", "driver_id", v.getId());
        requireOwner(v.getDriverId());
        jdbc.update(
                "UPDATE vehicle SET plate_no=?, vin=?, engine_no=?, brand=?, model=?, type=?, color=?, fuel_type=?, " +
                "status=?, driver_id=?, seats=?, mileage=?, purchase_date=?, equip_date=?, insurance_expiry=?, " +
                "inspection_expiry=?, remark=?, photo=?, updated_at=datetime('now','localtime') WHERE id=?",
                nvl(v.getPlateNo()), nvl(v.getVin()), nvl(v.getEngineNo()),
                nvl(v.getBrand()), nvl(v.getModel()), nvl(v.getType()), nvl(v.getColor()), nvl(v.getFuelType()), nvl(v.getStatus()),
                v.getDriverId(), v.getSeats(), v.getMileage(),
                nvl(v.getPurchaseDate()), nvl(v.getEquipDate()), nvl(v.getInsuranceExpiry()), nvl(v.getInspectionExpiry()),
                nvl(v.getRemark()), nvl(v.getPhoto()), v.getId());
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(Long id) {
        DataScope.requireRecord(jdbc, "vehicle", "driver_id", id);
        Integer faultTable = jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='vehicle_fault_history'", Integer.class);
        if (faultTable != null && faultTable > 0) {
            jdbc.update("DELETE FROM vehicle_fault_history WHERE vehicle_id=?", id);
        }
        jdbc.update("DELETE FROM vehicle WHERE id=?", id);
    }

    public void requireOwner(Long personnelId) {
        DataScope.requirePerson(jdbc, personnelId);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
