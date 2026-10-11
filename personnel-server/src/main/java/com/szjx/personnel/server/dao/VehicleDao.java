package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.model.Vehicle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
            v.setBrand(rs.getString("brand"));
            v.setType(rs.getString("type"));
            v.setStatus(rs.getString("status"));
            v.setDriverId(rs.getLong("driver_id"));
            v.setDriverName(rs.getString("driver_name"));
            v.setSeats(rs.getObject("seats") == null ? null : rs.getInt("seats"));
            v.setMileage(rs.getObject("mileage") == null ? null : rs.getDouble("mileage"));
            v.setRemark(rs.getString("remark"));
            v.setCreatedAt(rs.getString("created_at"));
            v.setUpdatedAt(rs.getString("updated_at"));
            return v;
        }
    };

    private static final String BASE =
            "SELECT v.*, IFNULL(p.name,'') AS driver_name FROM vehicle v " +
            "LEFT JOIN personnel p ON v.driver_id = p.id ";

    private void buildFilter(StringBuilder sql, List<Object> args, String keyword, String status) {
        sql.append(" WHERE 1=1 ");
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (v.plate_no LIKE ? OR v.brand LIKE ?) ");
            String kw = "%" + keyword.trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND v.status = ? ");
            args.add(status.trim());
        }
    }

    public List<Vehicle> query(String keyword, String status, int page, int size) {
        StringBuilder sql = new StringBuilder(BASE);
        List<Object> args = new ArrayList<Object>();
        buildFilter(sql, args, keyword, status);
        sql.append(" ORDER BY v.updated_at DESC, v.id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public long count(String keyword, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM vehicle v LEFT JOIN personnel p ON v.driver_id=p.id ");
        List<Object> args = new ArrayList<Object>();
        buildFilter(sql, args, keyword, status);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public Vehicle findById(Long id) {
        List<Vehicle> list = jdbc.query(BASE + " WHERE v.id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(Vehicle v) {
        return jdbc.queryForObject(
                "INSERT INTO vehicle(plate_no, brand, type, status, driver_id, seats, mileage, remark) " +
                "VALUES(?,?,?,?,?,?,?,?) RETURNING id",
                Long.class,
                nvl(v.getPlateNo()), nvl(v.getBrand()), nvl(v.getType()), nvl(v.getStatus()),
                v.getDriverId(), v.getSeats(), v.getMileage(), nvl(v.getRemark()));
    }

    public void update(Vehicle v) {
        jdbc.update(
                "UPDATE vehicle SET plate_no=?, brand=?, type=?, status=?, driver_id=?, seats=?, mileage=?, " +
                "remark=?, updated_at=datetime('now','localtime') WHERE id=?",
                nvl(v.getPlateNo()), nvl(v.getBrand()), nvl(v.getType()), nvl(v.getStatus()),
                v.getDriverId(), v.getSeats(), v.getMileage(), nvl(v.getRemark()), v.getId());
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM vehicle WHERE id=?", id);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
