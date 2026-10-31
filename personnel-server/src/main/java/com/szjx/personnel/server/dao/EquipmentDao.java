package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.auth.DataScope;

import com.szjx.personnel.server.model.Equipment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 装备数据访问
 */
@Repository
public class EquipmentDao {

    private final JdbcTemplate jdbc;

    public EquipmentDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Equipment> MAPPER = new RowMapper<Equipment>() {
        @Override
        public Equipment mapRow(ResultSet rs, int i) throws SQLException {
            Equipment e = new Equipment();
            e.setId(rs.getLong("id"));
            e.setName(rs.getString("name"));
            e.setCode(rs.getString("code"));
            e.setIdentCode(rs.getString("ident_code"));
            e.setCategory(rs.getString("category"));
            e.setBrand(rs.getString("brand"));
            e.setModel(rs.getString("model"));
            e.setStatus(rs.getString("status"));
            e.setKeeperId(rs.getLong("keeper_id"));
            e.setKeeperName(rs.getString("keeper_name"));
            e.setLocation(rs.getString("location"));
            e.setBuyDate(rs.getString("buy_date"));
            e.setEquipDate(rs.getString("equip_date"));
    e.setRemark(rs.getString("remark"));
    e.setPhoto(rs.getString("photo"));
    e.setCreatedAt(rs.getString("created_at"));
            e.setUpdatedAt(rs.getString("updated_at"));
            return e;
        }
    };

    private static final String BASE =
            "SELECT e.*, IFNULL(p.name,'') AS keeper_name FROM equipment e " +
            "LEFT JOIN personnel p ON e.keeper_id = p.id ";

    private void buildFilter(StringBuilder sql, List<Object> args, String keyword, String status) {
        sql.append(" WHERE ").append(DataScope.personSql("e.keeper_id")).append(" ");
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (e.name LIKE ? OR e.code LIKE ? OR e.ident_code LIKE ?) ");
            String kw = "%" + keyword.trim() + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND e.status = ? ");
            args.add(status.trim());
        }
    }

    public List<Equipment> query(String keyword, String status, int page, int size) {
        StringBuilder sql = new StringBuilder(BASE);
        List<Object> args = new ArrayList<Object>();
        buildFilter(sql, args, keyword, status);
        sql.append(" ORDER BY e.updated_at DESC, e.id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public long count(String keyword, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM equipment e LEFT JOIN personnel p ON e.keeper_id=p.id ");
        List<Object> args = new ArrayList<Object>();
        buildFilter(sql, args, keyword, status);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public Equipment findById(Long id) {
        DataScope.requireRecord(jdbc, "equipment", "keeper_id", id);
        List<Equipment> list = jdbc.query(BASE + " WHERE e.id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean existsGunCode(String code, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM equipment WHERE category='枪支' AND LOWER(TRIM(code))=LOWER(TRIM(?))";
        Integer count;
        if (excludeId == null) {
            count = jdbc.queryForObject(sql, Integer.class, code);
        } else {
            count = jdbc.queryForObject(sql + " AND id<>?", Integer.class, code, excludeId);
        }
        return count != null && count > 0;
    }

    public long insert(Equipment e) {
        requireOwner(e.getKeeperId());
        return jdbc.queryForObject(
                "INSERT INTO equipment(name, code, ident_code, category, brand, model, status, keeper_id, location, buy_date, equip_date, remark, photo) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id",
                Long.class,
                nvl(e.getName()), nvl(e.getCode()), nvl(e.getIdentCode()), nvl(e.getCategory()), nvl(e.getBrand()), nvl(e.getModel()),
                nvl(e.getStatus()), e.getKeeperId(), nvl(e.getLocation()), nvl(e.getBuyDate()), nvl(e.getEquipDate()), nvl(e.getRemark()), nvl(e.getPhoto()));
    }

    public void update(Equipment e) {
        DataScope.requireRecord(jdbc, "equipment", "keeper_id", e.getId());
        requireOwner(e.getKeeperId());
        jdbc.update(
                "UPDATE equipment SET name=?, code=?, ident_code=?, category=?, brand=?, model=?, status=?, keeper_id=?, location=?, " +
                "buy_date=?, equip_date=?, remark=?, photo=?, updated_at=datetime('now','localtime') WHERE id=?",
                nvl(e.getName()), nvl(e.getCode()), nvl(e.getIdentCode()), nvl(e.getCategory()), nvl(e.getBrand()), nvl(e.getModel()),
                nvl(e.getStatus()), e.getKeeperId(), nvl(e.getLocation()), nvl(e.getBuyDate()), nvl(e.getEquipDate()),
                nvl(e.getRemark()), nvl(e.getPhoto()), e.getId());
    }

    public void delete(Long id) {
        DataScope.requireRecord(jdbc, "equipment", "keeper_id", id);
        jdbc.update("DELETE FROM equipment WHERE id=?", id);
    }

    public void requireOwner(Long personnelId) {
        DataScope.requirePerson(jdbc, personnelId);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
