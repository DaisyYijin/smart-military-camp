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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public FitnessService(FitnessDao fitnessDao, PersonnelDao personnelDao,
                          org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.fitnessDao = fitnessDao;
        this.personnelDao = personnelDao;
        this.jdbc = jdbc;
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

    public List<FitnessRecord> queryFiltered2(String personnelName, String idCard,
                                               String item, String grade, String startDate, String endDate,
                                               Long departmentId, String minScore, String maxScore,
                                               int page, int size) {
        StringBuilder sql = new StringBuilder(
            "SELECT f.*, p.name AS personnel_name, p.birthday, p.id_card FROM fitness_record f " +
            "LEFT JOIN personnel p ON f.personnel_id = p.id WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<Object>();
        appendFilter2(sql, args, personnelName, idCard, item, grade, startDate, endDate, departmentId, minScore, maxScore);
        sql.append(" ORDER BY f.test_date DESC, f.id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((page - 1) * size);
        return jdbc.query(sql.toString(), new FitnessRowMapper(), args.toArray());
    }

    public long countFiltered2(String personnelName, String idCard,
                                String item, String grade, String startDate, String endDate,
                                Long departmentId, String minScore, String maxScore) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM fitness_record f " +
            "LEFT JOIN personnel p ON f.personnel_id = p.id WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<Object>();
        appendFilter2(sql, args, personnelName, idCard, item, grade, startDate, endDate, departmentId, minScore, maxScore);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private void appendFilter2(StringBuilder sql, java.util.List<Object> args,
                                String personnelName, String idCard,
                                String item, String grade, String startDate, String endDate,
                                Long departmentId, String minScore, String maxScore) {
        // 支持只填到月(2026-09):起始补-01,终止补-31(字符串比较安全)
        if (startDate != null && startDate.trim().length() == 7) { startDate = startDate.trim() + "-01"; }
        if (endDate != null && endDate.trim().length() == 7) { endDate = endDate.trim() + "-31"; }
        if (personnelName != null && !personnelName.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ?"); args.add("%" + personnelName.trim() + "%");
        }
        if (idCard != null && !idCard.trim().isEmpty()) {
            sql.append(" AND p.id_card LIKE ?"); args.add("%" + idCard.trim() + "%");
        }
        if (item != null && !item.trim().isEmpty()) {
            sql.append(" AND f.item = ?"); args.add(item.trim());
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
                sql.append(" AND f.grade = ?"); args.add(g);
            }
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND f.test_date >= ?"); args.add(startDate.trim());
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND f.test_date <= ?"); args.add(endDate.trim());
        }
        if (departmentId != null && departmentId > 0) {
            sql.append(" AND p.department_id = ?"); args.add(departmentId);
        }
        if (minScore != null && !minScore.trim().isEmpty()) {
            try { sql.append(" AND CAST(f.grade AS REAL) >= ?"); args.add(Double.parseDouble(minScore.trim())); } catch (NumberFormatException ignore) {}
        }
        if (maxScore != null && !maxScore.trim().isEmpty()) {
            try { sql.append(" AND CAST(f.grade AS REAL) <= ?"); args.add(Double.parseDouble(maxScore.trim())); } catch (NumberFormatException ignore) {}
        }
    }

    public List<FitnessRecord> queryFiltered(String keyword, String item, String grade,
                                              String startDate, String endDate,
                                              Long departmentId, String minScore, String maxScore,
                                              int page, int size) {
        StringBuilder sql = new StringBuilder(
            "SELECT f.*, p.name AS personnel_name, p.birthday, p.id_card FROM fitness_record f " +
            "LEFT JOIN personnel p ON f.personnel_id=p.id WHERE 1=1");
        List<Object> args = new java.util.ArrayList<Object>();
        appendFilter(sql, args, keyword, item, grade, startDate, endDate, departmentId, minScore, maxScore);
        sql.append(" ORDER BY f.test_date DESC, f.id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((page - 1) * size);
        return jdbc.query(sql.toString(), new FitnessRowMapper(), args.toArray());
    }

    public long countFiltered(String keyword, String item, String grade,
                               String startDate, String endDate,
                               Long departmentId, String minScore, String maxScore) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM fitness_record f " +
            "LEFT JOIN personnel p ON f.personnel_id=p.id WHERE 1=1");
        List<Object> args = new java.util.ArrayList<Object>();
        appendFilter(sql, args, keyword, item, grade, startDate, endDate, departmentId, minScore, maxScore);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public java.util.Map<String, Object> statsFiltered(String keyword, String item, String grade,
                                              String startDate, String endDate,
                                              Long departmentId, String minScore, String maxScore) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) as total, " +
            "SUM(CASE WHEN CAST(f.grade AS REAL) >= 60 THEN 1 ELSE 0 END) as passCount, " +
            "SUM(CASE WHEN CAST(f.grade AS REAL) >= 90 THEN 1 ELSE 0 END) as excellentCount, " +
            "AVG(CAST(f.grade AS REAL)) as avgScore " +
            "FROM fitness_record f LEFT JOIN personnel p ON f.personnel_id=p.id WHERE 1=1");
        List<Object> args = new java.util.ArrayList<Object>();
        appendFilter(sql, args, keyword, item, grade, startDate, endDate, departmentId, minScore, maxScore);
        return jdbc.queryForMap(sql.toString(), args.toArray());
    }

    private void appendFilter(StringBuilder sql, List<Object> args, String keyword,
                               String item, String grade, String startDate, String endDate,
                               Long departmentId, String minScore, String maxScore) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (p.name LIKE ? OR f.item LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like);
        }
        if (item != null && !item.trim().isEmpty()) {
            sql.append(" AND f.item = ?");
            args.add(item.trim());
        }
        if (grade != null && !grade.trim().isEmpty()) {
            String g = grade.trim();
            if (g.contains("\u5206\u4ee5\u4e0a")) {
                sql.append(" AND CAST(f.grade AS REAL) >= ").append(g.replace("\u5206\u4ee5\u4e0a", ""));
            } else if (g.contains("\u5206\u4ee5\u4e0b")) {
                sql.append(" AND CAST(f.grade AS REAL) < ").append(g.replace("\u5206\u4ee5\u4e0b", ""));
            } else if (g.contains("-")) {
                String[] parts = g.replace("\u5206", "").split("-");
                sql.append(" AND CAST(f.grade AS REAL) >= ? AND CAST(f.grade AS REAL) <= ?");
                args.add(Double.parseDouble(parts[0]));
                args.add(Double.parseDouble(parts[1]));
            } else {
                sql.append(" AND f.grade = ?");
                args.add(g);
            }
        }
        String sd = startDate;
        String ed = endDate;
        if (sd != null && sd.trim().length() == 7) { sd = sd.trim() + "-01"; }
        if (ed != null && ed.trim().length() == 7) { ed = ed.trim() + "-31"; }
        if (sd != null && !sd.trim().isEmpty()) {
            sql.append(" AND f.test_date >= ?");
            args.add(sd.trim());
        }
        if (ed != null && !ed.trim().isEmpty()) {
            sql.append(" AND f.test_date <= ?");
            args.add(ed.trim());
        }
        if (departmentId != null && departmentId > 0) {
            sql.append(" AND p.department_id = ?");
            args.add(departmentId);
        }
        if (minScore != null && !minScore.trim().isEmpty()) {
            try {
                sql.append(" AND CAST(f.grade AS REAL) >= ?");
                args.add(Double.parseDouble(minScore.trim()));
            } catch (NumberFormatException ignore) { }
        }
        if (maxScore != null && !maxScore.trim().isEmpty()) {
            try {
                sql.append(" AND CAST(f.grade AS REAL) <= ?");
                args.add(Double.parseDouble(maxScore.trim()));
            } catch (NumberFormatException ignore) { }
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
            r.setBirthday(rs.getString("birthday"));
            r.setIdCard(rs.getString("id_card"));
            r.setPoints(rs.getObject("points") == null ? null : rs.getDouble("points"));
            return r;
        }
    }
}
