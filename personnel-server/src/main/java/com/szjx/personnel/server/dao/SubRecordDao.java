package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.auth.DataScope;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人员子记录通用数据访问(任职经历 / 奖惩情况),
 * 两表结构一致(id, personnel_id, 业务字段若干, 备注, 时间戳),按表名参数化。
 */
@Repository
public class SubRecordDao {

    private final JdbcTemplate jdbc;

    public SubRecordDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listByPerson(String table, long personnelId, String orderBy) {
        checkTable(table);
        DataScope.requirePerson(jdbc, personnelId);
        return jdbc.queryForList("SELECT * FROM " + table + " WHERE personnel_id=? ORDER BY " + orderBy + " DESC, id DESC",
                personnelId);
    }

    public Map<String, Object> findById(String table, long id) {
        checkTable(table);
        DataScope.requireRecord(jdbc, table, "personnel_id", id);
        List<Map<String, Object>> list = jdbc.queryForList("SELECT * FROM " + table + " WHERE id=?", id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(String table, long personnelId, Map<String, String> fields, List<String> cols) {
        checkTable(table);
        DataScope.requirePerson(jdbc, personnelId);
        StringBuilder colsSql = new StringBuilder("personnel_id");
        StringBuilder marks = new StringBuilder("?");
        List<Object> args = new ArrayList<Object>();
        args.add(personnelId);
        for (String c : cols) {
            colsSql.append(",").append(c);
            marks.append(",?");
            args.add(fields.get(c) == null ? "" : fields.get(c));
        }
        Long id = jdbc.queryForObject("INSERT INTO " + table + "(" + colsSql + ") VALUES(" + marks + ") RETURNING id",
                Long.class, args.toArray());
        return id == null ? 0 : id;
    }

    public void update(String table, long id, Map<String, String> fields, List<String> cols) {
        checkTable(table);
        DataScope.requireRecord(jdbc, table, "personnel_id", id);
        StringBuilder set = new StringBuilder();
        List<Object> args = new ArrayList<Object>();
        for (String c : cols) {
            if (set.length() > 0) {
                set.append(",");
            }
            set.append(c).append("=?");
            args.add(fields.get(c) == null ? "" : fields.get(c));
        }
        set.append(", updated_at=datetime('now','localtime')");
        args.add(id);
        jdbc.update("UPDATE " + table + " SET " + set + " WHERE id=?", args.toArray());
    }

    public void delete(String table, long id) {
        checkTable(table);
        DataScope.requireRecord(jdbc, table, "personnel_id", id);
        jdbc.update("DELETE FROM " + table + " WHERE id=?", id);
    }

    public boolean personExists(long personnelId) {
        DataScope.requirePerson(jdbc, personnelId);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE id=?", Long.class, personnelId);
        return n != null && n > 0;
    }

    public Map<String, Object> row(String table, long id) {
        return findById(table, id);
    }

    private void checkTable(String table) {
        if (!"position_history".equals(table) && !"reward_punish".equals(table)
                && !"child_info".equals(table) && !"family_member".equals(table)
                && !"driver_license".equals(table) && !"personal_card".equals(table)
                && !"private_vehicle".equals(table) && !"housing_info".equals(table)
                && !"fitness_record".equals(table)) {
            throw new IllegalArgumentException("非法的子记录表: " + table);
        }
    }
}
