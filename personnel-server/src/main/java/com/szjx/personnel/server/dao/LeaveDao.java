package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.model.LeaveRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 休假数据访问
 */
@Repository
public class LeaveDao {

    private final JdbcTemplate jdbc;

    public LeaveDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<LeaveRecord> MAPPER = new RowMapper<LeaveRecord>() {
        @Override
        public LeaveRecord mapRow(ResultSet rs, int i) throws SQLException {
            LeaveRecord r = new LeaveRecord();
            r.setId(rs.getLong("id"));
            r.setPersonnelId(rs.getLong("personnel_id"));
            r.setPersonnelName(rs.getString("personnel_name"));
            r.setYear(rs.getString("year"));
            r.setTravelDays(rs.getObject("travel_days") == null ? null : rs.getDouble("travel_days"));
            r.setHolidayDays(rs.getObject("holiday_days") == null ? null : rs.getDouble("holiday_days"));
            r.setHolidayName(rs.getString("holiday_name"));
            r.setLeaveType(rs.getString("leave_type"));
            r.setStartDate(rs.getString("start_date"));
            r.setEndDate(rs.getString("end_date"));
            r.setDays(rs.getObject("days") == null ? null : rs.getDouble("days"));
            r.setReason(rs.getString("reason"));
            r.setRemark(rs.getString("remark"));
            r.setCreatedAt(rs.getString("created_at"));
            r.setUpdatedAt(rs.getString("updated_at"));
            return r;
        }
    };

    private static final String BASE =
            "SELECT l.*, IFNULL(p.name,'') AS personnel_name FROM leave_record l " +
            "LEFT JOIN personnel p ON l.personnel_id = p.id ";

    public List<LeaveRecord> query(String keyword, int page, int size) {
        StringBuilder sql = new StringBuilder(BASE).append(" WHERE 1=1 ");
        List<Object> args = new ArrayList<Object>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ? ");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY l.updated_at DESC, l.id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public long count(String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM leave_record l LEFT JOIN personnel p ON l.personnel_id=p.id WHERE 1=1 ");
        List<Object> args = new ArrayList<Object>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ? ");
            args.add("%" + keyword.trim() + "%");
        }
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public LeaveRecord findById(Long id) {
        List<LeaveRecord> list = jdbc.query(BASE + " WHERE l.id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(LeaveRecord r) {
        return jdbc.queryForObject(
                "INSERT INTO leave_record(personnel_id, year, leave_type, start_date, end_date, days, " +
                "travel_days, holiday_days, holiday_name, reason, remark) VALUES(?,?,?,?,?,?,?,?,?,?,?) RETURNING id",
                Long.class,
                r.getPersonnelId(), nvl(r.getYear()),
                nvl(r.getLeaveType()), nvl(r.getStartDate()), nvl(r.getEndDate()),
                r.getDays() == null ? 0 : r.getDays(),
                r.getTravelDays() == null ? 0 : r.getTravelDays(),
                r.getHolidayDays() == null ? 0 : r.getHolidayDays(),
                nvl(r.getHolidayName()),
                nvl(r.getReason()), nvl(r.getRemark()));
    }

    public void update(LeaveRecord r) {
        jdbc.update(
                "UPDATE leave_record SET personnel_id=?, year=?, leave_type=?, start_date=?, end_date=?, days=?, " +
                "travel_days=?, holiday_days=?, holiday_name=?, reason=?, remark=?, updated_at=datetime('now','localtime') WHERE id=?",
                r.getPersonnelId(), nvl(r.getYear()),
                nvl(r.getLeaveType()), nvl(r.getStartDate()), nvl(r.getEndDate()),
                r.getDays() == null ? 0 : r.getDays(),
                r.getTravelDays() == null ? 0 : r.getTravelDays(),
                r.getHolidayDays() == null ? 0 : r.getHolidayDays(),
                nvl(r.getHolidayName()),
                nvl(r.getReason()), nvl(r.getRemark()),
                r.getId());
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM leave_record WHERE id=?", id);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
