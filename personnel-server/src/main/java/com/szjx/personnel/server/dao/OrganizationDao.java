package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.auth.DataScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 组织建设数据访问:组织定义/成员名单/自定义表头
 */
@Repository
public class OrganizationDao {

    private final JdbcTemplate jdbc;

    public OrganizationDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ===== 组织 ===== */

    public List<Map<String, Object>> listOrganizations() {
        return jdbc.queryForList(
                "SELECT o.id, o.name, o.sort, o.remark, o.created_at AS createdAt, o.updated_at AS updatedAt, " +
                "(SELECT COUNT(*) FROM organization_member m WHERE m.organization_id = o.id) AS memberCount " +
                "FROM organization o ORDER BY o.sort, o.id");
    }

    public Map<String, Object> findOrganization(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM organization WHERE id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long insertOrganization(String name, String remark, int sort) {
        return jdbc.queryForObject(
                "INSERT INTO organization(name, remark, sort) VALUES(?,?,?) RETURNING id",
                Long.class, name, remark, sort);
    }

    public int updateOrganization(Long id, String name, String remark) {
        return jdbc.update("UPDATE organization SET name=?, remark=?, updated_at=datetime('now','localtime') WHERE id=?",
                name, remark, id);
    }

    public void deleteOrganization(Long id) {
        jdbc.update("DELETE FROM organization_member WHERE organization_id=?", id);
        jdbc.update("DELETE FROM organization_column WHERE organization_id=?", id);
        jdbc.update("DELETE FROM organization WHERE id=?", id);
    }

    /* ===== 成员 ===== */

    public List<Map<String, Object>> listMembers(Long organizationId) {
        return jdbc.queryForList(
                "SELECT m.id, m.personnel_id AS personnelId, m.role, m.extra, m.sort, m.remark, " +
                "m.created_at AS createdAt, p.name AS personnelName, p.id_card AS idCard, " +
                "p.department_id AS pDepartmentId " +
                "FROM organization_member m " +
                "LEFT JOIN personnel p ON m.personnel_id = p.id " +
                "WHERE m.organization_id = ? ORDER BY m.sort, m.id", organizationId);
    }

    public Map<String, Object> findMember(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM organization_member WHERE id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long insertMember(Long organizationId, Long personnelId, String role, String extra, String remark, int sort) {
        return jdbc.queryForObject(
                "INSERT INTO organization_member(organization_id, personnel_id, role, extra, remark, sort) " +
                "VALUES(?,?,?,?,?,?) RETURNING id",
                Long.class, organizationId, personnelId, role, extra, remark, sort);
    }

    public int updateMember(Long id, Long personnelId, String role, String extra, String remark) {
        return jdbc.update(
                "UPDATE organization_member SET personnel_id=?, role=?, extra=?, remark=?, " +
                "updated_at=datetime('now','localtime') WHERE id=?",
                personnelId, role, extra, remark, id);
    }

    public void deleteMember(Long id) {
        jdbc.update("DELETE FROM organization_member WHERE id=?", id);
    }

    /* ===== 自定义表头 ===== */

    public List<Map<String, Object>> listColumns(Long organizationId) {
        return jdbc.queryForList(
                "SELECT id, column_key AS columnKey, column_label AS columnLabel, sort " +
                "FROM organization_column WHERE organization_id=? ORDER BY sort, id", organizationId);
    }

    public void replaceColumns(Long organizationId, List<Map<String, Object>> columns) {
        jdbc.update("DELETE FROM organization_column WHERE organization_id=?", organizationId);
        int sort = 1;
        for (Map<String, Object> col : columns) {
            String key = String.valueOf(col.get("key")).trim();
            String label = String.valueOf(col.get("label")).trim();
            if (key.isEmpty() || label.isEmpty()) { continue; }
            jdbc.update("INSERT INTO organization_column(organization_id, column_key, column_label, sort) VALUES(?,?,?,?)",
                    organizationId, key, label, sort++);
        }
    }

    /* ===== 通用 ===== */

    public boolean personExists(Long personnelId) {
        return !jdbc.queryForList("SELECT id FROM personnel WHERE id=?", personnelId).isEmpty();
    }

    public void requirePerson(Long personnelId) {
        DataScope.requirePerson(jdbc, personnelId);
    }
}
