package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.auth.DataScope;

import com.szjx.personnel.server.model.Material;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 物资数据访问
 */
@Repository
public class MaterialDao {

    private final JdbcTemplate jdbc;

    public MaterialDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Material> MAPPER = new RowMapper<Material>() {
        @Override
        public Material mapRow(ResultSet rs, int i) throws SQLException {
            Material m = new Material();
            m.setId(rs.getLong("id"));
            m.setName(rs.getString("name"));
            m.setCategory(rs.getString("category"));
            m.setSpec(rs.getString("spec"));
            m.setUnit(rs.getString("unit"));
            m.setQuantity(rs.getObject("quantity") == null ? null : rs.getDouble("quantity"));
            m.setMinQuantity(rs.getObject("min_quantity") == null ? null : rs.getDouble("min_quantity"));
            m.setKeeperId(rs.getLong("keeper_id"));
            m.setKeeperName(rs.getString("keeper_name"));
            m.setLocation(rs.getString("location"));
            m.setDetailLocation(rs.getString("detail_location"));
            m.setRemark(rs.getString("remark"));
            m.setPhoto(rs.getString("photo"));
            m.setCreatedAt(rs.getString("created_at"));
            m.setUpdatedAt(rs.getString("updated_at"));
            return m;
        }
    };

    private static final String BASE =
            "SELECT m.*, IFNULL(p.name,'') AS keeper_name FROM material m " +
            "LEFT JOIN personnel p ON m.keeper_id = p.id ";

    /** 列表查询(驼峰别名列,与前端字段一一对应);SQL 统一住址在 dao,controller 只做参数绑定 */
    public List<Map<String, Object>> query(String name, String category, String spec,
            String keeperName, String location, int page, int size) {
        StringBuilder sql = new StringBuilder(
            "SELECT m.id, m.name, m.category, m.spec, m.unit, m.quantity, " +
            "m.min_quantity AS minQuantity, m.keeper_id AS keeperId, p.name AS keeperName, " +
            "p.id_card AS keeperIdCard, m.location, m.detail_location AS detailLocation, m.remark, m.photo, " +
            "m.created_at AS createdAt, m.updated_at AS updatedAt " +
            "FROM material m LEFT JOIN personnel p ON m.keeper_id = p.id WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        appendFilters(sql, args, name, category, spec, keeperName, location);
        sql.append(" ORDER BY m.updated_at DESC, m.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((long) (page - 1) * size);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public long count(String name, String category, String spec, String keeperName, String location) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM material m LEFT JOIN personnel p ON m.keeper_id = p.id WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        appendFilters(sql, args, name, category, spec, keeperName, location);
        Long total = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private void appendFilters(StringBuilder sql, List<Object> args, String name, String category,
            String spec, String keeperName, String location) {
        sql.append(" AND ").append(DataScope.personSql("m.keeper_id"));
        if (name != null && !name.trim().isEmpty()) {
            sql.append(" AND m.name LIKE ?"); args.add("%" + name.trim() + "%");
        }
        if (category != null && !category.trim().isEmpty()) {
            sql.append(" AND m.category = ?"); args.add(category.trim());
        }
        if (spec != null && !spec.trim().isEmpty()) {
            sql.append(" AND m.spec LIKE ?"); args.add("%" + spec.trim() + "%");
        }
        if (keeperName != null && !keeperName.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ?"); args.add("%" + keeperName.trim() + "%");
        }
        if (location != null && !location.trim().isEmpty()) {
            sql.append(" AND m.location LIKE ?"); args.add("%" + location.trim() + "%");
        }
    }

    public Material findById(Long id) {
        DataScope.requireRecord(jdbc, "material", "keeper_id", id);
        List<Material> list = jdbc.query(BASE + " WHERE m.id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(Material m) {
        requireOwner(m.getKeeperId());
        return jdbc.queryForObject(
                "INSERT INTO material(name, category, spec, unit, quantity, min_quantity, keeper_id, location, detail_location, remark, photo) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?) RETURNING id",
                Long.class,
                nvl(m.getName()), nvl(m.getCategory()), nvl(m.getSpec()), nvl(m.getUnit()),
                m.getQuantity() == null ? 0 : m.getQuantity(),
                m.getMinQuantity() == null ? 0 : m.getMinQuantity(),
                m.getKeeperId(), nvl(m.getLocation()), nvl(m.getDetailLocation()), nvl(m.getRemark()), nvl(m.getPhoto()));
    }

    public void update(Material m) {
        DataScope.requireRecord(jdbc, "material", "keeper_id", m.getId());
        requireOwner(m.getKeeperId());
        jdbc.update(
                "UPDATE material SET name=?, category=?, spec=?, unit=?, quantity=?, min_quantity=?, keeper_id=?, " +
                "location=?, detail_location=?, remark=?, photo=?, updated_at=datetime('now','localtime') WHERE id=?",
                nvl(m.getName()), nvl(m.getCategory()), nvl(m.getSpec()), nvl(m.getUnit()),
                m.getQuantity() == null ? 0 : m.getQuantity(),
                m.getMinQuantity() == null ? 0 : m.getMinQuantity(),
                m.getKeeperId(), nvl(m.getLocation()), nvl(m.getDetailLocation()), nvl(m.getRemark()), nvl(m.getPhoto()), m.getId());
    }

    public void delete(Long id) {
        DataScope.requireRecord(jdbc, "material", "keeper_id", id);
        jdbc.update("DELETE FROM material WHERE id=?", id);
    }

    public void requireOwner(Long personnelId) {
        DataScope.requirePerson(jdbc, personnelId);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
