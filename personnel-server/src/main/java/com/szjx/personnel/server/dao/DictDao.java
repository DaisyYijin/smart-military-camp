package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.model.DictItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 数据字典数据访问
 */
@Repository
public class DictDao {

    private final JdbcTemplate jdbc;

    public DictDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<DictItem> MAPPER = new RowMapper<DictItem>() {
        @Override
        public DictItem mapRow(ResultSet rs, int i) throws SQLException {
            DictItem d = new DictItem();
            d.setId(rs.getLong("id"));
            d.setDictType(rs.getString("dict_type"));
            d.setDictLabel(rs.getString("dict_label"));
            d.setParentId(rs.getLong("parent_id"));
            d.setSort(rs.getInt("sort"));
            d.setEnabled(rs.getInt("enabled") == 1);
            d.setRemark(rs.getString("remark"));
            d.setCreatedAt(rs.getString("created_at"));
            d.setUpdatedAt(rs.getString("updated_at"));
            return d;
        }
    };

    /** 某类型全部项(管理用) */
    public List<DictItem> findByType(String type) {
        return jdbc.query("SELECT * FROM sys_dict WHERE dict_type=? ORDER BY sort, id", MAPPER, type);
    }

    /** 某类型启用项(下拉用) */
    public List<DictItem> findByTypeEnabled(String type) {
        return jdbc.query("SELECT * FROM sys_dict WHERE dict_type=? AND enabled=1 ORDER BY sort, id",
                MAPPER, type);
    }

    /** 全部类型(去重) */
    public List<String> findTypes() {
        return jdbc.queryForList("SELECT DISTINCT dict_type FROM sys_dict ORDER BY dict_type", String.class);
    }

    public DictItem findById(Long id) {
        List<DictItem> list = jdbc.query("SELECT * FROM sys_dict WHERE id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean exists(String type, String label) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict WHERE dict_type=? AND dict_label=?",
                Long.class, type, label);
        return n != null && n > 0;
    }

    /** 新选项默认追加到同类型末尾。 */
    public int nextSort(String type) {
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort),0)+1 FROM sys_dict WHERE dict_type=?", Integer.class, type);
        return next == null ? 1 : next;
    }

    public void updateSort(Long id, int sort) {
        jdbc.update("UPDATE sys_dict SET sort=?, updated_at=datetime('now','localtime') WHERE id=?", sort, id);
    }

    public long insert(DictItem d) {
        return jdbc.queryForObject(
                "INSERT INTO sys_dict(dict_type, dict_label, sort, enabled, remark, parent_id) VALUES(?,?,?,?,?,?) RETURNING id",
                Long.class,
                d.getDictType(),
                d.getDictLabel(),
                d.getSort() == null ? 0 : d.getSort(),
                d.getEnabled() != null && d.getEnabled() ? 1 : 0,
                d.getRemark() == null ? "" : d.getRemark(),
                d.getParentId() == null ? 0 : d.getParentId());
    }

    public void update(DictItem d) {
        jdbc.update("UPDATE sys_dict SET dict_type=?, dict_label=?, sort=?, enabled=?, remark=?, parent_id=?, " +
                        "updated_at=datetime('now','localtime') WHERE id=?",
                d.getDictType(),
                d.getDictLabel(),
                d.getSort() == null ? 0 : d.getSort(),
                d.getEnabled() != null && d.getEnabled() ? 1 : 0,
                d.getRemark() == null ? "" : d.getRemark(),
                d.getParentId() == null ? 0 : d.getParentId(),
                d.getId());
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM sys_dict WHERE id=?", id);
    }

    /** 某字典项的下级数量 */
    public int countChildren(Long parentId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict WHERE parent_id=?", Long.class, parentId);
        return n == null ? 0 : n.intValue();
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict", Long.class);
        return n == null ? 0 : n;
    }
}
