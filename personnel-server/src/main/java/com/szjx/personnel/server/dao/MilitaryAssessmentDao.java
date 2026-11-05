package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.auth.DataScope;

import com.szjx.personnel.server.model.MilitaryAssessment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 军事考核数据访问
 */
@Repository
public class MilitaryAssessmentDao {

    private final JdbcTemplate jdbc;

    public MilitaryAssessmentDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<MilitaryAssessment> MAPPER = (rs, i) -> {
        MilitaryAssessment m = new MilitaryAssessment();
        m.setId(rs.getLong("id"));
        m.setPersonnelId(rs.getLong("personnel_id"));
        m.setPersonnelName(rs.getString("personnel_name"));
        m.setIdCard(rs.getString("id_card"));
        m.setDepartmentId(rs.getObject("p_department_id") == null ? null : rs.getLong("p_department_id"));
        m.setCategory(rs.getString("category"));
        m.setItem(rs.getString("item"));
        m.setScore(rs.getString("score"));
        m.setPoints(rs.getObject("points") == null ? null : rs.getDouble("points"));
        m.setGrade(rs.getString("grade"));
        m.setTestDate(rs.getString("test_date"));
        Object sid = rs.getObject("scheme_id");
        m.setSchemeId(sid == null ? Long.valueOf(0L) : Long.valueOf(rs.getLong("scheme_id")));
        m.setDetail(rs.getString("detail"));
        m.setRemark(rs.getString("remark"));
        m.setCreatedAt(rs.getString("created_at"));
        m.setUpdatedAt(rs.getString("updated_at"));
        return m;
    };

    private static final String BASE =
            "SELECT m.*, p.name AS personnel_name, p.id_card, p.department_id AS p_department_id FROM military_assessment m " +
            "LEFT JOIN personnel p ON m.personnel_id = p.id ";

    private void appendFilter(StringBuilder sql, List<Object> args, String personnelName,
                              String category, String item, String grade,
                              String startDate, String endDate) {
        if (personnelName != null && !personnelName.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ? ");
            args.add("%" + personnelName.trim() + "%");
        }
        if (category != null && !category.trim().isEmpty()) {
            sql.append(" AND m.category = ? ");
            args.add(category.trim());
        }
        if (item != null && !item.trim().isEmpty()) {
            sql.append(" AND m.item LIKE ? ");
            args.add("%" + item.trim() + "%");
        }
        if (grade != null && !grade.trim().isEmpty()) {
            sql.append(" AND m.grade = ? ");
            args.add(grade.trim());
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND m.test_date >= ? ");
            args.add(startDate.trim());
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND m.test_date <= ? ");
            args.add(endDate.trim());
        }
    }

    public List<MilitaryAssessment> query(String personnelName, String category, String item,
                                          String grade, String startDate, String endDate,
                                          int page, int size) {
        StringBuilder sql = new StringBuilder(BASE).append(" WHERE 1=1 ");
        sql.append(" AND ").append(DataScope.personSql("m.personnel_id")).append(" ");
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, personnelName, category, item, grade, startDate, endDate);
        sql.append(" ORDER BY m.test_date DESC, m.id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add((long) (page - 1) * size);
        java.util.List<MilitaryAssessment> list = jdbc.query(sql.toString(), MAPPER, args.toArray());
        // 列表附带人员编制单位完整路径(无斜杠层级)
        if (!list.isEmpty()) {
            com.szjx.personnel.server.util.DepartmentHierarchy hierarchy = com.szjx.personnel.server.util.DepartmentHierarchy.load(jdbc);
            for (MilitaryAssessment m : list) {
                m.setDepartmentPath(hierarchy.path(m.getDepartmentId()));
            }
        }
        return list;
    }

    public long count(String personnelName, String category, String item,
                      String grade, String startDate, String endDate) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM military_assessment m LEFT JOIN personnel p ON m.personnel_id = p.id WHERE 1=1 ");
        sql.append(" AND ").append(DataScope.personSql("m.personnel_id")).append(" ");
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, personnelName, category, item, grade, startDate, endDate);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public MilitaryAssessment findById(Long id) {
        DataScope.requireRecord(jdbc, "military_assessment", "personnel_id", id);
        List<MilitaryAssessment> list = jdbc.query(BASE + " WHERE m.id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(MilitaryAssessment m) {
        DataScope.requirePerson(jdbc, m.getPersonnelId());
        return jdbc.queryForObject(
                "INSERT INTO military_assessment(personnel_id, scheme_id, detail, category, item, score, points, grade, test_date, remark) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?) RETURNING id",
                Long.class,
                m.getPersonnelId(), m.getSchemeId() == null ? Long.valueOf(0L) : m.getSchemeId(), nvl(m.getDetail()), nvl(m.getCategory()), nvl(m.getItem()), nvl(m.getScore()),
                m.getPoints(), nvl(m.getGrade()), nvl(m.getTestDate()), nvl(m.getRemark()));
    }

    public void update(MilitaryAssessment m) {
        DataScope.requireRecord(jdbc, "military_assessment", "personnel_id", m.getId());
        DataScope.requirePerson(jdbc, m.getPersonnelId());
        jdbc.update(
                "UPDATE military_assessment SET personnel_id=?, scheme_id=?, detail=?, category=?, item=?, score=?, points=?, grade=?, " +
                "test_date=?, remark=?, updated_at=datetime('now','localtime') WHERE id=?",
                m.getPersonnelId(), m.getSchemeId() == null ? Long.valueOf(0L) : m.getSchemeId(), nvl(m.getDetail()), nvl(m.getCategory()), nvl(m.getItem()), nvl(m.getScore()),
                m.getPoints(), nvl(m.getGrade()), nvl(m.getTestDate()), nvl(m.getRemark()), m.getId());
    }

    public void delete(Long id) {
        DataScope.requireRecord(jdbc, "military_assessment", "personnel_id", id);
        jdbc.update("DELETE FROM military_assessment WHERE id=?", id);
    }

    public boolean personExists(Long personnelId) {
        DataScope.requirePerson(jdbc, personnelId);
        if (personnelId == null || personnelId <= 0) {
            return false;
        }
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE id=?", Long.class, personnelId);
        return n != null && n > 0;
    }

    /* ===== 考核方案(军事考核方案化加权评分),SQL 自 controller 下沉 ===== */

    public java.util.List<java.util.Map<String, Object>> allSchemes() {
        return jdbc.queryForList("SELECT * FROM mil_scheme ORDER BY id");
    }

    public java.util.Map<String, Object> schemeById(long id) {
        java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM mil_scheme WHERE id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean schemeExists(long id) {
        return !jdbc.queryForList("SELECT id FROM mil_scheme WHERE id=?", id).isEmpty();
    }

    public String schemeName(long id) {
        java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList("SELECT name FROM mil_scheme WHERE id=?", id);
        return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("name"));
    }

    public java.util.List<java.util.Map<String, Object>> schemeItems(long schemeId, long parentId) {
        return jdbc.queryForList("SELECT id, name, weight FROM mil_scheme_item WHERE scheme_id=? AND parent_id=? ORDER BY sort, id",
                schemeId, parentId);
    }

    public long insertScheme(String name, String remark) {
        return jdbc.queryForObject("INSERT INTO mil_scheme(name, remark) VALUES(?,?) RETURNING id", Long.class, name, remark);
    }

    public void updateScheme(long id, String name, String remark) {
        jdbc.update("UPDATE mil_scheme SET name=?, remark=?, updated_at=datetime('now','localtime') WHERE id=?", name, remark, id);
    }

    public void deleteSchemeItems(long schemeId) {
        jdbc.update("DELETE FROM mil_scheme_item WHERE scheme_id=?", schemeId);
    }

    public void deleteScheme(long id) {
        deleteSchemeItems(id);
        jdbc.update("DELETE FROM mil_scheme WHERE id=?", id);
    }

    public long insertSchemeItem(long schemeId, long parentId, String name, double weight, int sort) {
        return jdbc.queryForObject("INSERT INTO mil_scheme_item(scheme_id, parent_id, name, weight, sort) VALUES(?,?,?,?,?) RETURNING id",
                Long.class, schemeId, parentId, name, weight, sort);
    }

    public void insertChildSchemeItem(long schemeId, long parentId, String name, double weight, int sort) {
        jdbc.update("INSERT INTO mil_scheme_item(scheme_id, parent_id, name, weight, sort) VALUES(?,?,?,?,?)",
                schemeId, parentId, name, weight, sort);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
