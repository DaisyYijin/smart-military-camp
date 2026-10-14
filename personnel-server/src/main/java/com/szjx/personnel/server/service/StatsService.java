package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.StatsSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 统计业务
 */
@Service
public class StatsService {

    private final JdbcTemplate jdbc;
    private final PersonnelDao personnelDao;

    public StatsService(JdbcTemplate jdbc, PersonnelDao personnelDao) {
        this.jdbc = jdbc;
        this.personnelDao = personnelDao;
    }

    public StatsSummary summary() {
        StatsSummary s = new StatsSummary();
        s.setTotal(personnelDao.countAll());

        List<Map<String, Object>> dept = jdbc.queryForList(
                "SELECT IFNULL(d.name, '未分配') AS name, COUNT(*) AS count FROM personnel p " +
                "LEFT JOIN department d ON p.department_id = d.id GROUP BY p.department_id ORDER BY count DESC");
        for (Map<String, Object> row : dept) {
            s.getDepartment().add(new StatsSummary.NameCount(
                    String.valueOf(row.get("name")),
                    ((Number) row.get("count")).longValue()));
        }

        List<Map<String, Object>> gender = jdbc.queryForList(
                "SELECT IFNULL(NULLIF(p.gender, ''), '未填写') AS name, COUNT(*) AS count FROM personnel p " +
                "GROUP BY p.gender ORDER BY count DESC");
        for (Map<String, Object> row : gender) {
            s.getGender().add(new StatsSummary.NameCount(
                    String.valueOf(row.get("name")),
                    ((Number) row.get("count")).longValue()));
        }

        List<Map<String, Object>> status = jdbc.queryForList(
                "SELECT IFNULL(NULLIF(p.status, ''), '未填写') AS name, COUNT(*) AS count FROM personnel p " +
                "GROUP BY p.status ORDER BY count DESC");
        for (Map<String, Object> row : status) {
            s.getStatus().add(new StatsSummary.NameCount(
                    String.valueOf(row.get("name")),
                    ((Number) row.get("count")).longValue()));
        }
        return s;
    }

    /* ================= 仪表盘 ================= */

    /** 分组统计辅助 */
    private List<StatsSummary.NameCount> group(String sql) {
        List<StatsSummary.NameCount> out = new java.util.ArrayList<StatsSummary.NameCount>();
        for (Map<String, Object> row : jdbc.queryForList(sql)) {
            out.add(new StatsSummary.NameCount(String.valueOf(row.get("name")),
                    ((Number) row.get("count")).longValue()));
        }
        return out;
    }

    private static final String PERSON_GROUP_TMPL =
            "SELECT IFNULL(NULLIF(p.%s, ''), '未填写') AS name, COUNT(*) AS count FROM personnel p GROUP BY p.%s ORDER BY count DESC, name";

    /**
     * 仪表盘数据:概况数字 + 各维度分组(图表用) + 入伍趋势
     */
    public Map<String, Object> dashboard() {
        Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        m.put("total", personnelDao.countAll());
        m.put("onDuty", countOrZero("SELECT COUNT(*) FROM personnel WHERE status='在职'"));
        m.put("onLeave", countOrZero("SELECT COUNT(*) FROM personnel WHERE status='休假'"));
        m.put("deptCount", countOrZero("SELECT COUNT(*) FROM department"));
        m.put("vehicleCount", countOrZero("SELECT COUNT(*) FROM vehicle"));
        m.put("equipmentTotal", countOrZero("SELECT COUNT(*) FROM equipment"));
        m.put("equipmentRepair", countOrZero("SELECT COUNT(*) FROM equipment WHERE status='维修'"));
        m.put("materialTotal", countOrZero("SELECT COUNT(*) FROM material"));
        m.put("materialLowStock", countOrZero("SELECT COUNT(*) FROM material WHERE CAST(min_quantity AS REAL)>0 AND CAST(quantity AS REAL)<CAST(min_quantity AS REAL)"));
        m.put("leaveThisYear", countOrZero("SELECT COUNT(*) FROM leave_record WHERE start_date LIKE strftime('%Y','now','localtime') || '%'"));
        m.put("fitnessThisYear", countOrZero("SELECT COUNT(*) FROM fitness_record WHERE test_date LIKE strftime('%Y','now','localtime') || '%'"));
        m.put("vehicleRepair", countOrZero("SELECT COUNT(*) FROM vehicle WHERE status='维修'"));
        m.put("materialLow", countOrZero(
                "SELECT COUNT(*) FROM material WHERE CAST(min_quantity AS REAL) > 0 AND CAST(quantity AS REAL) < CAST(min_quantity AS REAL)"));

        Map<String, Object> groups = new java.util.LinkedHashMap<String, Object>();
        groups.put("department", group(
                "SELECT IFNULL(d.name, '未分配') AS name, COUNT(*) AS count FROM personnel p " +
                "LEFT JOIN department d ON p.department_id = d.id GROUP BY p.department_id ORDER BY count DESC LIMIT 15"));
        groups.put("status", group(String.format(PERSON_GROUP_TMPL, "status", "status")));
        groups.put("gender", group(String.format(PERSON_GROUP_TMPL, "gender", "gender")));
        groups.put("category", group(String.format(PERSON_GROUP_TMPL, "category", "category")));
        groups.put("rank", group(String.format(PERSON_GROUP_TMPL, "rank", "rank") + " LIMIT 12"));
        groups.put("education", group(String.format(PERSON_GROUP_TMPL, "education", "education")));
        groups.put("politicalStatus", group(String.format(PERSON_GROUP_TMPL, "political_status", "political_status")));
        // 籍贯/入伍地按省份分布(取"省/市/区县"组合串中的省)
        groups.put("nativePlace", group(
                "SELECT IFNULL(NULLIF(prov, ''), '未填写') AS name, COUNT(*) AS count FROM (" +
                "SELECT CASE WHEN INSTR(p.native_place, '/') > 0 " +
                "THEN SUBSTR(p.native_place, 1, INSTR(p.native_place, '/') - 1) ELSE p.native_place END AS prov " +
                "FROM personnel p) GROUP BY name ORDER BY count DESC, name"));
        groups.put("enlistPlace", group(
                "SELECT IFNULL(NULLIF(prov, ''), '未填写') AS name, COUNT(*) AS count FROM (" +
                "SELECT CASE WHEN INSTR(p.enlist_place, '/') > 0 " +
                "THEN SUBSTR(p.enlist_place, 1, INSTR(p.enlist_place, '/') - 1) ELSE p.enlist_place END AS prov " +
                "FROM personnel p) GROUP BY name ORDER BY count DESC, name"));
        groups.put("leaveType", group(
                "SELECT IFNULL(NULLIF(l.leave_type, ''), '未填写') AS name, COUNT(*) AS count FROM leave_record l " +
                "WHERE l.start_date LIKE strftime('%Y', 'now', 'localtime') || '%' GROUP BY l.leave_type ORDER BY count DESC"));
        groups.put("fitnessGrade", group(
                "SELECT IFNULL(NULLIF(f.grade, ''), '未填写') AS name, COUNT(*) AS count FROM fitness_record f " +
                "WHERE f.test_date LIKE strftime('%Y', 'now', 'localtime') || '%' GROUP BY f.grade ORDER BY count DESC"));
        groups.put("vehicleStatus", group(
                "SELECT IFNULL(NULLIF(v.status, ''), '未填写') AS name, COUNT(*) AS count FROM vehicle v GROUP BY v.status ORDER BY count DESC"));
        groups.put("vehicleBrand", group(
                "SELECT IFNULL(NULLIF(v.brand, ''), '未填写') AS name, COUNT(*) AS count FROM vehicle v GROUP BY v.brand ORDER BY count DESC"));
        groups.put("materialCategory", group(
                "SELECT IFNULL(NULLIF(mt.category, ''), '未填写') AS name, COUNT(*) AS count FROM material mt GROUP BY mt.category ORDER BY count DESC"));
        groups.put("equipmentStatus", group(
                "SELECT IFNULL(NULLIF(e.status, ''), '未填写') AS name, COUNT(*) AS count FROM equipment e GROUP BY e.status ORDER BY count DESC"));
        groups.put("equipmentCategory", group(
                "SELECT IFNULL(NULLIF(e.category, ''), '未填写') AS name, COUNT(*) AS count FROM equipment e GROUP BY e.category ORDER BY count DESC"));
        m.put("groups", groups);

        // 入伍趋势:近15年
        m.put("entryTrend", group(
                "SELECT strftime('%Y', p.entry_date) AS name, COUNT(*) AS count FROM personnel p " +
                "WHERE p.entry_date LIKE '____-__-__' " +
                "AND CAST(strftime('%Y', p.entry_date) AS INTEGER) >= CAST(strftime('%Y', 'now', 'localtime') AS INTEGER) - 14 " +
                "GROUP BY strftime('%Y', p.entry_date) ORDER BY name"));
        return m;
    }

    private long countOrZero(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
