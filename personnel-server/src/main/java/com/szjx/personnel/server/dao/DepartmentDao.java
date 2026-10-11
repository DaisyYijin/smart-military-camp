package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.model.Department;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门数据访问
 */
@Repository
public class DepartmentDao {

    private final JdbcTemplate jdbc;

    public DepartmentDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Department> MAPPER = new RowMapper<Department>() {
        @Override
        public Department mapRow(ResultSet rs, int i) throws SQLException {
            Department d = new Department();
            d.setId(rs.getLong("id"));
            d.setName(rs.getString("name"));
            d.setParentId(rs.getLong("parent_id"));
            d.setSort(rs.getInt("sort"));
            d.setRemark(rs.getString("remark"));
            return d;
        }
    };

    public List<Department> findAll() {
        return jdbc.query("SELECT * FROM department ORDER BY sort, id", MAPPER);
    }

    /**
     * 组装为树
     */
    public List<Department> tree() {
        List<Department> all = findAll();
        Map<Long, Department> byId = new HashMap<Long, Department>();
        for (Department d : all) {
            byId.put(d.getId(), d);
        }
        List<Department> roots = new ArrayList<Department>();
        for (Department d : all) {
            long pid = d.getParentId() == null ? 0 : d.getParentId();
            Department parent = byId.get(pid);
            if (parent != null) {
                parent.getChildren().add(d);
            } else {
                roots.add(d);
            }
        }
        return roots;
    }

    public Department findById(Long id) {
        List<Department> list = jdbc.query("SELECT * FROM department WHERE id=?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(Department d) {
        return jdbc.queryForObject(
                "INSERT INTO department(name, parent_id, sort, remark) VALUES(?,?,?,?) RETURNING id",
                Long.class,
                d.getName(),
                d.getParentId() == null ? 0 : d.getParentId(),
                d.getSort() == null ? 0 : d.getSort(),
                d.getRemark() == null ? "" : d.getRemark());
    }

    public void update(Department d) {
        jdbc.update("UPDATE department SET name=?, parent_id=?, sort=?, remark=? WHERE id=?",
                d.getName(),
                d.getParentId() == null ? 0 : d.getParentId(),
                d.getSort() == null ? 0 : d.getSort(),
                d.getRemark() == null ? "" : d.getRemark(),
                d.getId());
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM department WHERE id=?", id);
    }

    /** 子部门数量 */
    public long countChildren(Long id) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM department WHERE parent_id=?", Long.class, id);
        return n == null ? 0 : n;
    }

    /** 部门下人员数量 */
    public long countPersonnel(Long id) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE department_id=?", Long.class, id);
        return n == null ? 0 : n;
    }
}
