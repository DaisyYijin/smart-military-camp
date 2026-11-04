package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.AbsenceRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 人员外出登记数据访问(查询列表由 Controller 直查,这里承载写入与单条读取)
 */
@Repository
public class AbsenceDao {

    private final JdbcTemplate jdbc;

    public AbsenceDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<AbsenceRecord> MAPPER = new RowMapper<AbsenceRecord>() {
        @Override
        public AbsenceRecord mapRow(ResultSet rs, int i) throws SQLException {
            AbsenceRecord r = new AbsenceRecord();
            r.setId(rs.getLong("id"));
            r.setPersonnelId(rs.getLong("personnel_id"));
            r.setPersonnelName(rs.getString("personnel_name"));
            r.setReason(rs.getString("reason"));
            r.setLeaveDate(rs.getString("leave_date"));
            r.setExpectedReturnDate(rs.getString("expected_return_date"));
            r.setActualReturnDate(rs.getString("actual_return_date"));
            r.setRemark(rs.getString("remark"));
            r.setCreatedAt(rs.getString("created_at"));
            r.setUpdatedAt(rs.getString("updated_at"));
            return r;
        }
    };

    private static final String BASE =
            "SELECT a.*, IFNULL(p.name,'') AS personnel_name FROM absence_record a " +
            "LEFT JOIN personnel p ON a.personnel_id = p.id ";

    public AbsenceRecord findById(Long id) {
        DataScope.requireRecord(jdbc, "absence_record", "personnel_id", id);
        List<AbsenceRecord> list = jdbc.query(BASE + " WHERE a.id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(AbsenceRecord r) {
        DataScope.requirePerson(jdbc, r.getPersonnelId());
        return jdbc.queryForObject(
                "INSERT INTO absence_record(personnel_id, reason, leave_date, expected_return_date, actual_return_date, remark) " +
                "VALUES(?,?,?,?,?,?) RETURNING id",
                Long.class,
                r.getPersonnelId(), nvl(r.getReason()), nvl(r.getLeaveDate()),
                nvl(r.getExpectedReturnDate()), nvl(r.getActualReturnDate()), nvl(r.getRemark()));
    }

    public void update(AbsenceRecord r) {
        DataScope.requireRecord(jdbc, "absence_record", "personnel_id", r.getId());
        DataScope.requirePerson(jdbc, r.getPersonnelId());
        jdbc.update(
                "UPDATE absence_record SET personnel_id=?, reason=?, leave_date=?, expected_return_date=?, " +
                "actual_return_date=?, remark=?, updated_at=datetime('now','localtime') WHERE id=?",
                r.getPersonnelId(), nvl(r.getReason()), nvl(r.getLeaveDate()),
                nvl(r.getExpectedReturnDate()), nvl(r.getActualReturnDate()), nvl(r.getRemark()),
                r.getId());
    }

    public void delete(Long id) {
        DataScope.requireRecord(jdbc, "absence_record", "personnel_id", id);
        jdbc.update("DELETE FROM absence_record WHERE id=?", id);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
