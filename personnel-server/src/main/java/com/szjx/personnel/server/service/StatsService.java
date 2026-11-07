package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.StatsSummary;
import com.szjx.personnel.server.util.DepartmentHierarchy;
import com.szjx.personnel.server.util.DriverLicenseFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 统计业务 */
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
        s.getDepartment().addAll(departmentGroup(""));
        s.getGender().addAll(personGroup("gender", ""));
        s.getStatus().addAll(personGroup("status", ""));
        return s;
    }

    private List<StatsSummary.NameCount> group(String sql) {
        List<StatsSummary.NameCount> out = new ArrayList<StatsSummary.NameCount>();
        for (Map<String, Object> row : jdbc.queryForList(sql)) {
            out.add(new StatsSummary.NameCount(String.valueOf(row.get("name")),
                    ((Number) row.get("count")).longValue()));
        }
        return out;
    }

    private List<StatsSummary.NameCount> departmentGroup(String limit) {
        return group("SELECT COALESCE(d.name, '未分配') AS name, COUNT(*) AS count FROM personnel p "
                + "LEFT JOIN department d ON p.department_id=d.id WHERE " + DataScope.departmentSql("p.department_id")
                + " GROUP BY p.department_id ORDER BY count DESC" + limit);
    }

    public static final class DepartmentCount extends StatsSummary.NameCount {
        private final Long id;
        private final Long parentId;
        private final String path;
        private final int depth;
        private final long directCount;

        DepartmentCount(Long id, Long parentId, String name, String path, int depth, long directCount, long total) {
            super(name, total);
            this.id = id;
            this.parentId = parentId;
            this.path = path;
            this.depth = depth;
            this.directCount = directCount;
        }
        public Long getId() { return id; }
        public Long getParentId() { return parentId; }
        public String getPath() { return path; }
        public int getDepth() { return depth; }
        public long getDirectCount() { return directCount; }
    }

    private List<DepartmentCount> departmentHierarchyGroup() {
        DepartmentHierarchy hierarchy = DepartmentHierarchy.load(jdbc);
        Map<Long, Long> direct = new LinkedHashMap<Long, Long>();
        long unassigned = 0;
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT p.department_id AS department_id,COUNT(*) AS count FROM personnel p WHERE "
                        + DataScope.departmentSql("p.department_id") + " GROUP BY p.department_id")) {
            Object rawId = row.get("department_id");
            Long id = rawId instanceof Number ? ((Number) rawId).longValue() : null;
            long count = ((Number) row.get("count")).longValue();
            if (hierarchy.get(id) == null) unassigned += count;
            else direct.put(id, count);
        }
        Map<Long, Long> totals = new LinkedHashMap<Long, Long>(direct);
        List<DepartmentHierarchy.Node> nodes = hierarchy.ordered();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            DepartmentHierarchy.Node node = nodes.get(i);
            long count = totals.getOrDefault(node.getId(), 0L);
            if (node.getParentId() > 0) {
                totals.put(node.getParentId(), totals.getOrDefault(node.getParentId(), 0L) + count);
            }
        }
        List<DepartmentCount> out = new ArrayList<DepartmentCount>();
        for (DepartmentHierarchy.Node node : nodes) {
            if (!DataScope.allowsDepartment(node.getId())) continue;
            out.add(new DepartmentCount(node.getId(), node.getParentId(), node.getName(), node.getPath(),
                    node.getDepth(), direct.getOrDefault(node.getId(), 0L), totals.getOrDefault(node.getId(), 0L)));
        }
        if (unassigned > 0) out.add(new DepartmentCount(0L, 0L, "未分配", "未分配", 0, unassigned, unassigned));
        return out;
    }

    private List<StatsSummary.NameCount> personGroup(String column, String limit) {
        return group("SELECT COALESCE(NULLIF(p." + column + ", ''), '未填写') AS name, COUNT(*) AS count FROM personnel p WHERE "
                + DataScope.departmentSql("p.department_id") + " GROUP BY p." + column + " ORDER BY count DESC, name" + limit);
    }

    private List<StatsSummary.NameCount> provinceGroup(String column) {
        return group("SELECT COALESCE(NULLIF(prov, ''), '未填写') AS name, COUNT(*) AS count FROM ("
                + "SELECT CASE WHEN INSTR(p." + column + ", '/')>0 THEN SUBSTR(p." + column
                + ",1,INSTR(p." + column + ",'/')-1) ELSE p." + column + " END AS prov FROM personnel p WHERE "
                + DataScope.departmentSql("p.department_id") + ") GROUP BY name ORDER BY count DESC, name");
    }

    private List<StatsSummary.NameCount> relatedGroup(String table, String alias, String owner, String column, String extra) {
        return group("SELECT COALESCE(NULLIF(" + alias + "." + column + ", ''), '未填写') AS name, COUNT(*) AS count FROM "
                + table + " " + alias + " WHERE " + DataScope.personSql(alias + "." + owner) + extra
                + " GROUP BY " + alias + "." + column + " ORDER BY count DESC");
    }

    /** Count personnel, not license records: all statuses are registration and orphans do not count. */
    private List<StatsSummary.NameCount> driverLicenseGroup() {
        Map<String, Object> counts = jdbc.queryForMap(
                "SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN " + DriverLicenseFilter.HAS_RECORD_SQL
                        + " THEN 1 ELSE 0 END),0) AS registered FROM personnel p WHERE "
                        + DataScope.departmentSql("p.department_id"));
        long total = ((Number) counts.get("total")).longValue();
        long registered = ((Number) counts.get("registered")).longValue();
        List<StatsSummary.NameCount> result = new ArrayList<StatsSummary.NameCount>();
        result.add(new StatsSummary.NameCount("有驾驶证", registered));
        result.add(new StatsSummary.NameCount("无驾驶证", total - registered));
        return result;
    }

    /**
     * 实际在岗分布:scope 内 actual_department_id>0 的人员按实际单位分组。
     * name=实际单位完整路径,单位按树序输出;人员表一人一行,count 天然按人员不重复。
     * 无实际单位(0/NULL)者不出现,故各组之和与总人数不可比(同 department 组,不可加总)。
     * 实际单位已被删除的历史引用在名称树中无节点可取名,同样不出现。
     */
    private List<StatsSummary.NameCount> actualDutyGroup() {
        DepartmentHierarchy hierarchy = DepartmentHierarchy.load(jdbc);
        Map<Long, Long> counts = new LinkedHashMap<Long, Long>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT p.actual_department_id AS actual_department_id, COUNT(*) AS count FROM personnel p WHERE "
                        + DataScope.departmentSql("p.department_id")
                        + " AND p.actual_department_id>0 GROUP BY p.actual_department_id")) {
            Object rawId = row.get("actual_department_id");
            if (rawId instanceof Number) {
                counts.put(((Number) rawId).longValue(), ((Number) row.get("count")).longValue());
            }
        }
        List<StatsSummary.NameCount> out = new ArrayList<StatsSummary.NameCount>();
        for (DepartmentHierarchy.Node node : hierarchy.ordered()) {
            Long count = counts.remove(node.getId());
            if (count != null) out.add(new StatsSummary.NameCount(node.getPath(), count));
        }
        return out;
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        long total = personnelDao.countAll();
        m.put("total", total);
        // 在位口径:含"在职"与"在位"两种状态(与状态联动兼容的状态值一致)
        m.put("onDuty", count("personnel", DataScope.departmentSql("department_id"), "status IN ('在职','在位')"));
        m.put("onLeave", count("personnel", DataScope.departmentSql("department_id"), "status='休假'"));
        m.put("deptCount", count("department", DataScope.departmentSql("id"), "1=1"));
        m.put("vehicleCount", count("vehicle", DataScope.personSql("driver_id"), "1=1"));
        m.put("equipmentTotal", count("equipment", DataScope.personSql("keeper_id"), "1=1"));
        m.put("equipmentRepair", count("equipment", DataScope.personSql("keeper_id"), "status='维修'"));
        m.put("materialTotal", count("material", DataScope.personSql("keeper_id"), "1=1"));
        String lowStock = "CAST(min_quantity AS REAL)>0 AND CAST(quantity AS REAL)<CAST(min_quantity AS REAL)";
        // materialLowStock 与 materialLow 同口径:一次查询,两处复用,避免重复全表扫描
        long lowStockCount = count("material", DataScope.personSql("keeper_id"), lowStock);
        m.put("materialLowStock", lowStockCount);
        m.put("leaveThisYear", count("leave_record", DataScope.personSql("personnel_id"), "start_date LIKE strftime('%Y','now','localtime') || '%'"));
        m.put("fitnessThisYear", count("fitness_record", DataScope.personSql("personnel_id"), "test_date LIKE strftime('%Y','now','localtime') || '%'"));
        m.put("vehicleRepair", count("vehicle", DataScope.personSql("driver_id"), "status='维修'"));
        m.put("materialLow", lowStockCount);

        Map<String, Object> groups = new LinkedHashMap<String, Object>();
        groups.put("department", departmentHierarchyGroup());
        groups.put("locationPoint", personGroup("location_point", ""));
        groups.put("driverLicense", driverLicenseGroup());
        groups.put("actualDuty", actualDutyGroup());
        for (String field : new String[]{"status", "gender", "category", "rank", "education", "political_status"}) {
            groups.put("political_status".equals(field) ? "politicalStatus" : field,
                    personGroup(field, "rank".equals(field) ? " LIMIT 12" : ""));
        }
        groups.put("nativePlace", provinceGroup("native_place"));
        groups.put("enlistPlace", provinceGroup("enlist_place"));
        groups.put("leaveType", relatedGroup("leave_record", "l", "personnel_id", "leave_type",
                " AND l.start_date LIKE strftime('%Y','now','localtime') || '%'"));
        groups.put("fitnessGrade", relatedGroup("fitness_record", "f", "personnel_id", "grade",
                " AND f.test_date LIKE strftime('%Y','now','localtime') || '%'"));
        groups.put("vehicleStatus", relatedGroup("vehicle", "v", "driver_id", "status", ""));
        groups.put("vehicleType", relatedGroup("vehicle", "v", "driver_id", "type", ""));
        groups.put("vehicleBrand", relatedGroup("vehicle", "v", "driver_id", "brand", ""));
        groups.put("materialCategory", relatedGroup("material", "mt", "keeper_id", "category", ""));
        groups.put("equipmentStatus", relatedGroup("equipment", "e", "keeper_id", "status", ""));
        groups.put("equipmentCategory", relatedGroup("equipment", "e", "keeper_id", "category", ""));
        m.put("groups", groups);
        // 双争与年度考评完成率(本年度,数据范围内):双争按 人×4 季度人次计,年度考评按去重人数计
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        long deRated = count("double_excellence_record r", DataScope.personSql("r.personnel_id"), "r.year=" + currentYear);
        long deDue = total * 4;
        m.put("deRated", deRated);
        m.put("deDue", deDue);
        m.put("dePercent", deDue > 0 ? (int) Math.min(100, deRated * 100 / deDue) : 0);
        long aaRated = count("annual_appraisal_record a", DataScope.personSql("a.personnel_id"), "a.year=" + currentYear);
        m.put("aaRated", aaRated);
        m.put("aaPercent", total > 0 ? (int) Math.min(100, aaRated * 100 / total) : 0);
        m.put("entryTrend", group("SELECT strftime('%Y',p.entry_date) AS name, COUNT(*) AS count FROM personnel p WHERE "
                + DataScope.departmentSql("p.department_id") + " AND p.entry_date LIKE '____-__-__' "
                + "AND CAST(strftime('%Y',p.entry_date) AS INTEGER)>=CAST(strftime('%Y','now','localtime') AS INTEGER)-14 "
                + "GROUP BY strftime('%Y',p.entry_date) ORDER BY name"));
        return m;
    }

    private long count(String table, String scope, String condition) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE (" + scope + ") AND (" + condition + ")", Long.class);
        return n == null ? 0 : n;
    }
}
