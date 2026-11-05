package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.auth.DataScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 休假计划数据访问:SQL 自 LeavePlanController 下沉(查询筛选/增删改查/单位路径)
 */
@Repository
public class LeavePlanDao {

    private final JdbcTemplate jdbc;

    public LeavePlanDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String BASE =
            "SELECT lp.*, p.name AS personnel_name, p.id_card, p.department_id AS p_department_id FROM leave_plan lp " +
                    "LEFT JOIN personnel p ON lp.personnel_id = p.id ";

    public long count(String personnelName, String idCard, String year, String leaveType, String month,
                      String startDate, String endDate, Long personnelId) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<Object>();
        appendFilters(where, args, personnelName, idCard, year, leaveType, month, startDate, endDate, personnelId);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM leave_plan lp LEFT JOIN personnel p ON lp.personnel_id = p.id" + where,
                Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public List<Map<String, Object>> query(String personnelName, String idCard, String year, String leaveType, String month,
                                           String startDate, String endDate, Long personnelId, int page, int size) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<Object>();
        appendFilters(where, args, personnelName, idCard, year, leaveType, month, startDate, endDate, personnelId);
        StringBuilder sql = new StringBuilder(BASE);
        sql.append(where).append(" ORDER BY lp.year DESC, lp.start_date, lp.id LIMIT ? OFFSET ?");
        args.add(size);
        args.add((long) (page - 1) * size);
        List<Map<String, Object>> list = jdbc.queryForList(sql.toString(), args.toArray());
        // 列表附带人员编制单位完整路径(无斜杠层级;snake_case 与本接口字段风格一致)
        fillDepartmentPath(list);
        return list;
    }

    private void appendFilters(StringBuilder where, List<Object> args, String personnelName, String idCard, String year,
                               String leaveType, String month, String startDate, String endDate, Long personnelId) {
        where.append(" WHERE ").append(DataScope.personSql("lp.personnel_id"));
        if (notBlank(personnelName)) { where.append(" AND p.name LIKE ?"); args.add("%" + personnelName.trim() + "%"); }
        if (notBlank(idCard)) { where.append(" AND p.id_card LIKE ?"); args.add("%" + idCard.trim() + "%"); }
        if (notBlank(year)) { where.append(" AND lp.year = ?"); args.add(year.trim()); }
        if (notBlank(leaveType)) { where.append(" AND lp.leave_type = ?"); args.add(leaveType.trim()); }
        if (notBlank(month)) {
            where.append(" AND lp.start_date <= ? AND lp.end_date >= ?");
            args.add(month.trim() + "-31");
            args.add(month.trim() + "-01");
        }
        if (notBlank(startDate)) { where.append(" AND lp.start_date >= ?"); args.add(startDate.trim()); }
        if (notBlank(endDate)) { where.append(" AND lp.end_date <= ?"); args.add(endDate.trim()); }
        if (personnelId != null && personnelId > 0) { where.append(" AND lp.personnel_id = ?"); args.add(personnelId); }
    }

    /** 列表附带人员编制单位完整路径 */
    public void fillDepartmentPath(List<Map<String, Object>> list) {
        com.szjx.personnel.server.util.DepartmentHierarchy.fillPath(jdbc, list, "p_department_id", "department_path");
    }

    public long insert(Map<String, Object> m) {
        return jdbc.queryForObject("INSERT INTO leave_plan(personnel_id, year, leave_type, start_date, end_date, days, remark) VALUES(?,?,?,?,?,?,?) RETURNING id",
                Long.class, lng(m.get("personnelId")), str(m.get("year")), str(m.get("leaveType")),
                str(m.get("startDate")), str(m.get("endDate")), num(m.get("days")), str(m.get("remark")));
    }

    public int update(long id, Map<String, Object> m) {
        return jdbc.update("UPDATE leave_plan SET personnel_id=?, year=?, leave_type=?, start_date=?, end_date=?, days=?, remark=?," +
                        " updated_at=datetime('now','localtime') WHERE id=?",
                lng(m.get("personnelId")), str(m.get("year")), str(m.get("leaveType")),
                str(m.get("startDate")), str(m.get("endDate")), num(m.get("days")), str(m.get("remark")), id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM leave_plan WHERE id=?", id);
    }

    /** 按 id 查询(含数据权限复检,与原 controller.findOne 语义一致);不存在返回 null */
    public Map<String, Object> findById(long id) {
        DataScope.requireRecord(jdbc, "leave_plan", "personnel_id", id);
        List<Map<String, Object>> rows = jdbc.queryForList(BASE + " WHERE lp.id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean personExists(long pid) {
        return !jdbc.queryForList("SELECT id FROM personnel WHERE id=?", pid).isEmpty();
    }

    public void requireRecord(long id) { DataScope.requireRecord(jdbc, "leave_plan", "personnel_id", id); }

    public void requirePerson(long pid) { DataScope.requirePerson(jdbc, pid); }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    public static String str(Object o) { return o == null ? "" : String.valueOf(o).trim(); }

    public static double num(Object o) {
        if (o == null || String.valueOf(o).trim().isEmpty()) { return 0; }
        try { return Double.parseDouble(String.valueOf(o).trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    public static Long lng(Object o) {
        if (o == null || String.valueOf(o).trim().isEmpty()) { return 0L; }
        try { return Long.parseLong(String.valueOf(o).trim()); }
        catch (NumberFormatException e) {
            try { return (long) Double.parseDouble(String.valueOf(o).trim()); }
            catch (NumberFormatException e2) { return 0L; }
        }
    }
}
