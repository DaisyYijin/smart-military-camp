package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.auth.DataScope;
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
 * 单位数据访问
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
            long lid = rs.getLong("leader_id");
            d.setLeaderId(lid);
            return d;
        }
    };

    public List<Department> findAll() {
        return jdbc.query("SELECT * FROM department WHERE " + DataScope.departmentSql("id") + " ORDER BY sort, id", MAPPER);
    }

    /** 各单位直属人员数 */
    /** 同级排序:按传入顺序批量写 sort(1..n),id 不变;父级一致性由 Service 校验 */
    public void reorder(java.util.List<Long> ids) {
        int sort = 1;
        for (Long id : ids) {
            jdbc.update("UPDATE department SET sort=? WHERE id=?", Integer.valueOf(sort++), id);
        }
    }

    public Map<Long, Long> personnelCounts() {
        Map<Long, Long> m = new HashMap<Long, Long>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT department_id, COUNT(*) AS c FROM personnel WHERE department_id > 0 AND " + DataScope.departmentSql("department_id") + " GROUP BY department_id")) {
            m.put(((Number) r.get("department_id")).longValue(), ((Number) r.get("c")).longValue());
        }
        return m;
    }

    /** 各单位负责人姓名 */
    public Map<Long, String> leaderNames() {
        Map<Long, String> m = new HashMap<Long, String>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT d.id, p.name FROM department d JOIN personnel p ON d.leader_id = p.id WHERE d.leader_id > 0 AND " + DataScope.departmentSql("d.id") + " AND " + DataScope.departmentSql("p.department_id"))) {
            m.put(((Number) r.get("id")).longValue(), String.valueOf(r.get("name")));
        }
        return m;
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
        // 附带人员统计与负责人
        Map<Long, Long> cnt = personnelCounts();
        Map<Long, String> leaders = leaderNames();
        for (Department d : all) {
            Long c = cnt.get(d.getId());
            d.setCount(c == null ? 0L : c);
            d.setLeaderName(leaders.get(d.getId()));
            if (DataScope.restricted() && d.getLeaderName() == null) d.setLeaderId(0L);
            d.setTotal(0L);
        }
        for (Department r : roots) {
            sumTotal(r);
        }
        return roots;
    }

    /** 自底向上汇总含下级的人员数 */
    private long sumTotal(Department d) {
        long t = d.getCount() == null ? 0 : d.getCount();
        if (d.getChildren() != null) {
            for (Department c : d.getChildren()) {
                t += sumTotal(c);
            }
        }
        d.setTotal(t);
        return t;
    }

    public Department findById(Long id) {
        List<Department> list = jdbc.query("SELECT * FROM department WHERE id=? AND " + DataScope.departmentSql("id"), MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(Department d) {
        return jdbc.queryForObject(
                "INSERT INTO department(name, parent_id, sort, remark, leader_id) VALUES(?,?,?,?,?) RETURNING id",
                Long.class,
                d.getName(),
                d.getParentId() == null ? 0 : d.getParentId(),
                d.getSort() == null ? 0 : d.getSort(),
                d.getRemark() == null ? "" : d.getRemark(),
                d.getLeaderId() == null ? 0 : d.getLeaderId());
    }

    public void update(Department d) {
        jdbc.update("UPDATE department SET name=?, parent_id=?, sort=?, remark=?, leader_id=? WHERE id=?",
                d.getName(),
                d.getParentId() == null ? 0 : d.getParentId(),
                d.getSort() == null ? 0 : d.getSort(),
                d.getRemark() == null ? "" : d.getRemark(),
                d.getLeaderId() == null ? 0 : d.getLeaderId(),
                d.getId());
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM department WHERE id=?", id);
    }

    public boolean assignedToAccount(Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM account_department WHERE department_id=?", Long.class, id) > 0;
    }

    /** 子单位数量 */
    public long countChildren(Long id) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM department WHERE parent_id=?", Long.class, id);
        return n == null ? 0 : n;
    }

    /** 单位下人员数量 */
    public long countPersonnel(Long id) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE department_id=?", Long.class, id);
        return n == null ? 0 : n;
    }
}
