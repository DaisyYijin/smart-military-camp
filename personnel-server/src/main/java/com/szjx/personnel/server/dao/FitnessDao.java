package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.util.Db;
import com.szjx.personnel.server.auth.DataScope;

import com.szjx.personnel.server.model.FitnessRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 体能数据访问
 */
@Repository
public class FitnessDao {

    private final JdbcTemplate jdbc;

    public FitnessDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<FitnessRecord> MAPPER = new RowMapper<FitnessRecord>() {
        @Override
        public FitnessRecord mapRow(ResultSet rs, int i) throws SQLException {
            FitnessRecord r = new FitnessRecord();
            r.setId(rs.getLong("id"));
            r.setPersonnelId(rs.getLong("personnel_id"));
            r.setPersonnelName(rs.getString("personnel_name"));
            r.setItem(rs.getString("item"));
            r.setScore(rs.getString("score"));
            r.setGrade(rs.getString("grade"));
            r.setPoints(rs.getObject("points") == null ? null : rs.getDouble("points"));
            r.setTestDate(rs.getString("test_date"));
            r.setTester(rs.getString("tester"));
            r.setIdCard(rs.getString("id_card"));
            r.setRemark(rs.getString("remark"));
            r.setCreatedAt(rs.getString("created_at"));
            r.setUpdatedAt(rs.getString("updated_at"));
            return r;
        }
    };

    private static final String BASE =
            "SELECT f.*, COALESCE(p.name,'') AS personnel_name, p.id_card FROM fitness_record f " +
            "LEFT JOIN personnel p ON f.personnel_id = p.id ";

    public List<FitnessRecord> query(String keyword, String item, int page, int size) {
        StringBuilder sql = new StringBuilder(BASE).append(" WHERE 1=1 ");
        sql.append(" AND ").append(DataScope.personSql("f.personnel_id")).append(" ");
        List<Object> args = new ArrayList<Object>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ? ");
            args.add("%" + keyword.trim() + "%");
        }
        if (item != null && !item.trim().isEmpty()) {
            sql.append(" AND f.item = ? ");
            args.add(item.trim());
        }
        sql.append(" ORDER BY f.updated_at DESC, f.id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public long count(String keyword, String item) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM fitness_record f LEFT JOIN personnel p ON f.personnel_id=p.id WHERE 1=1 ");
        sql.append(" AND ").append(DataScope.personSql("f.personnel_id")).append(" ");
        List<Object> args = new ArrayList<Object>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ? ");
            args.add("%" + keyword.trim() + "%");
        }
        if (item != null && !item.trim().isEmpty()) {
            sql.append(" AND f.item = ? ");
            args.add(item.trim());
        }
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public FitnessRecord findById(Long id) {
        DataScope.requireRecord(jdbc, "fitness_record", "personnel_id", id);
        List<FitnessRecord> list = jdbc.query(BASE + " WHERE f.id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(FitnessRecord r) {
        DataScope.requirePerson(jdbc, r.getPersonnelId());
        return Db.id(jdbc, "INSERT INTO fitness_record(personnel_id, item, score, points, grade, test_date, tester, remark) " +
                "VALUES(?,?,?,?,?,?,?,?) RETURNING id",
                r.getPersonnelId(),
                nvl(r.getItem()), nvl(r.getScore()),
                r.getPoints() == null ? 0 : r.getPoints(), nvl(r.getGrade()),
                nvl(r.getTestDate()), nvl(r.getTester()), nvl(r.getRemark()));
    }

    public void update(FitnessRecord r) {
        DataScope.requireRecord(jdbc, "fitness_record", "personnel_id", r.getId());
        DataScope.requirePerson(jdbc, r.getPersonnelId());
        jdbc.update(
                "UPDATE fitness_record SET personnel_id=?, item=?, score=?, points=?, grade=?, test_date=?, tester=?, " +
                "remark=?, updated_at=datetime('now','localtime') WHERE id=?",
                r.getPersonnelId(),
                nvl(r.getItem()), nvl(r.getScore()),
                r.getPoints() == null ? 0 : r.getPoints(), nvl(r.getGrade()),
                nvl(r.getTestDate()), nvl(r.getTester()), nvl(r.getRemark()),
                r.getId());
    }

    public void delete(Long id) {
        DataScope.requireRecord(jdbc, "fitness_record", "personnel_id", id);
        jdbc.update("DELETE FROM fitness_record WHERE id=?", id);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
