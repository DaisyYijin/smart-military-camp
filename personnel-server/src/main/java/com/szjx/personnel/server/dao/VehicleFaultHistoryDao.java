package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.util.Db;
import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.VehicleFaultHistory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class VehicleFaultHistoryDao {
    private final JdbcTemplate jdbc;

    public VehicleFaultHistoryDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<VehicleFaultHistory> MAPPER = new RowMapper<VehicleFaultHistory>() {
        @Override
        public VehicleFaultHistory mapRow(ResultSet rs, int rowNum) throws SQLException {
            VehicleFaultHistory record = new VehicleFaultHistory();
            record.setId(rs.getLong("id"));
            record.setVehicleId(rs.getLong("vehicle_id"));
            record.setFaultDate(rs.getString("fault_date"));
            record.setDescription(rs.getString("description"));
            record.setCreatedAt(rs.getString("created_at"));
            record.setUpdatedAt(rs.getString("updated_at"));
            return record;
        }
    };

    public List<VehicleFaultHistory> findByVehicleId(Long vehicleId) {
        requireVehicle(vehicleId);
        return jdbc.query("SELECT * FROM vehicle_fault_history WHERE vehicle_id=? ORDER BY fault_date DESC, id DESC", MAPPER, vehicleId);
    }

    public Long insert(VehicleFaultHistory record) {
        requireVehicle(record.getVehicleId());
        return Db.id(jdbc, "INSERT INTO vehicle_fault_history(vehicle_id, fault_date, description) VALUES(?,?,?) RETURNING id", record.getVehicleId(), record.getFaultDate(), record.getDescription());
    }

    public void update(Long vehicleId, Long id, VehicleFaultHistory record) {
        requireVehicle(vehicleId);
        int changed = jdbc.update("UPDATE vehicle_fault_history SET fault_date=?, description=?, updated_at=datetime('now','localtime') WHERE id=? AND vehicle_id=?",
                record.getFaultDate(), record.getDescription(), id, vehicleId);
        if (changed == 0) throw new IllegalArgumentException("故障记录不存在");
    }

    public void delete(Long vehicleId, Long id) {
        requireVehicle(vehicleId);
        int changed = jdbc.update("DELETE FROM vehicle_fault_history WHERE id=? AND vehicle_id=?", id, vehicleId);
        if (changed == 0) throw new IllegalArgumentException("故障记录不存在");
    }

    public void deleteByVehicleId(Long vehicleId) {
        jdbc.update("DELETE FROM vehicle_fault_history WHERE vehicle_id=?", vehicleId);
    }

    private void requireVehicle(Long vehicleId) {
        DataScope.requireRecord(jdbc, "vehicle", "driver_id", vehicleId);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM vehicle WHERE id=?", Integer.class, vehicleId);
        if (count == null || count == 0) throw new IllegalArgumentException("车辆不存在");
    }
}
