package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.FitnessDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.FitnessRecord;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 体能业务
 */
@Service
public class FitnessService {

    private final FitnessDao fitnessDao;
    private final PersonnelDao personnelDao;

    public FitnessService(FitnessDao fitnessDao, PersonnelDao personnelDao) {
        this.fitnessDao = fitnessDao;
        this.personnelDao = personnelDao;
    }

    public List<FitnessRecord> query(String keyword, String item, int page, int size) {
        return fitnessDao.query(keyword, item, page, size);
    }

    public long count(String keyword, String item) {
        return fitnessDao.count(keyword, item);
    }

    public FitnessRecord create(FitnessRecord r) {
        validate(r);
        long id = fitnessDao.insert(r);
        return fitnessDao.findById(id);
    }

    public FitnessRecord update(Long id, FitnessRecord r) {
        if (fitnessDao.findById(id) == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        validate(r);
        r.setId(id);
        fitnessDao.update(r);
        return fitnessDao.findById(id);
    }

    public void delete(Long id) {
        fitnessDao.delete(id);
    }

    private void validate(FitnessRecord r) {
        if (r.getPersonnelId() == null || r.getPersonnelId() <= 0) {
            throw new IllegalArgumentException("请选择考核人员");
        }
        if (personnelDao.findById(r.getPersonnelId()) == null) {
            throw new IllegalArgumentException("所选人员不存在");
        }
        if (!StringUtils.hasText(r.getItem())) {
            throw new IllegalArgumentException("请填写考核项目");
        }
        if (!StringUtils.hasText(r.getTestDate())) {
            throw new IllegalArgumentException("请填写考核日期");
        }
    }

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public List<FitnessRecord> queryFiltered(String keyword, String item, String grade,
                                              String startDate, String endDate, int page, int size) {
        StringBuilder sql = new StringBuilder(
            "SELECT f.*, p.name AS personnel_name FROM fitness_record f " +
            "LEFT JOIN personnel p ON f.personnel_id=p.id WHERE 1=1");
        List<Object> args = new java.util.ArrayList<Object>();
        appendFilter(sql, args, keyword, item, grade, startDate, endDate);
        sql.append(" ORDER BY f.test_date DESC, f.id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((page - 1) * size);
        return jdbc.query(sql.toString(), new FitnessRowMapper(), args.toArray());
    }

    public long countFiltered(String keyword, String item, String grade,
                               String startDate, String endDate) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM fitness_record f " +
            "LEFT JOIN personnel p ON f.personnel_id=p.id WHERE 1=1");
        List<Object> args = new java.util.ArrayList<Object>();
        appendFilter(sql, args, keyword, item, grade, startDate, endDate);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private void appendFilter(StringBuilder sql, List<Object> args, String keyword,
                               String item, String grade, String startDate, String endDate) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (p.name LIKE ? OR f.item LIKE ? OR f.tester LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (item != null && !item.trim().isEmpty()) {
            sql.append(" AND f.item = ?");
            args.add(item.trim());
        }
        if (grade != null && !grade.trim().isEmpty()) {
            String g = grade.trim();
            if (g.contains("分以上")) {
                sql.append(" AND CAST(f.grade AS REAL) >= ").append(g.replace("分以上", ""));
            } else if (g.contains("分以下")) {
                sql.append(" AND CAST(f.grade AS REAL) < ").append(g.replace("分以下", ""));
            } else if (g.contains("-")) {
                String[] parts = g.replace("分", "").split("-");
                sql.append(" AND CAST(f.grade AS REAL) >= ? AND CAST(f.grade AS REAL) <= ?");
                args.add(Double.parseDouble(parts[0]));
                args.add(Double.parseDouble(parts[1]));
            } else if ("不及格".equals(g)) {
                sql.append(" AND CAST(f.grade AS REAL) < 60");
            } else {
                sql.append(" AND f.grade = ?");
                args.add(g);
            }
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND f.test_date >= ?");
            args.add(startDate.trim());
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND f.test_date <= ?");
            args.add(endDate.trim());
        }
    }

    private static class FitnessRowMapper implements org.springframework.jdbc.core.RowMapper<FitnessRecord> {
        @Override
        public FitnessRecord mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
            FitnessRecord r = new FitnessRecord();
            r.setId(rs.getLong("id"));
            r.setPersonnelId(rs.getLong("personnel_id"));
            r.setPersonnelName(rs.getString("personnel_name"));
            r.setItem(rs.getString("item"));
            r.setScore(rs.getString("score"));
            r.setGrade(rs.getString("grade"));
            r.setTestDate(rs.getString("test_date"));
            r.setTester(rs.getString("tester"));
            r.setRemark(rs.getString("remark"));
            return r;
        }
    }
}
