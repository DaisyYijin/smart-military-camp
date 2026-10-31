package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;

import com.szjx.personnel.server.dao.FitnessDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.FitnessRecord;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 体能业务
 */
@Service
public class FitnessService {

    private final FitnessDao fitnessDao;
    private final PersonnelDao personnelDao;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final FitnessScoringService scoringService;

    public FitnessService(FitnessDao fitnessDao, PersonnelDao personnelDao,
                          org.springframework.jdbc.core.JdbcTemplate jdbc,
                          FitnessScoringService scoringService) {
        this.fitnessDao = fitnessDao;
        this.personnelDao = personnelDao;
        this.jdbc = jdbc;
        this.scoringService = scoringService;
    }

    public List<FitnessRecord> query(String keyword, String item, int page, int size) {
        return fitnessDao.query(keyword, item, page, size);
    }

    public long count(String keyword, String item) {
        return fitnessDao.count(keyword, item);
    }

    /** 按记录 ID 批量取记录(宽行导出用);逐条做数据域校验,不静默忽略越权 ID。 */
    public List<FitnessRecord> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        for (Long id : ids) {
            DataScope.requireRecord(jdbc, "fitness_record", "personnel_id", id);
        }
        StringBuilder sql = new StringBuilder(
            "SELECT f.*, p.name AS personnel_name, p.birthday, p.id_card FROM fitness_record f " +
            "LEFT JOIN personnel p ON f.personnel_id = p.id WHERE f.id IN (");
        java.util.List<Object> args = new java.util.ArrayList<Object>();
        for (int i = 0; i < ids.size(); i++) {
            sql.append(i > 0 ? ",?" : "?");
            args.add(ids.get(i));
        }
        sql.append(") ORDER BY f.test_date DESC, f.id DESC");
        return jdbc.query(sql.toString(), new FitnessRowMapper(), args.toArray());
    }

    public FitnessRecord create(FitnessRecord r) {
        validate(r);
        // 服务端权威评分:按项目计分方式解析成绩并计算分数/评定(含标准与年龄校验)
        scoringService.score(r);
        long id = fitnessDao.insert(r);
        return fitnessDao.findById(id);
    }

    public FitnessRecord update(Long id, FitnessRecord r) {
        if (fitnessDao.findById(id) == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        validate(r);
        scoringService.score(r);
        r.setId(id);
        fitnessDao.update(r);
        return fitnessDao.findById(id);
    }

    public void delete(Long id) {
        fitnessDao.delete(id);
    }

    private void validate(FitnessRecord r) {
        if (r.getPersonnelId() == null || r.getPersonnelId() <= 0) {
            throw new IllegalArgumentException("请选择考核人员");
        }
        if (personnelDao.findById(r.getPersonnelId()) == null) {
            throw new IllegalArgumentException("所选人员不存在");
        }
        if (!StringUtils.hasText(r.getItem())) {
            throw new IllegalArgumentException("请填写考核项目");
        }
        if (!StringUtils.hasText(r.getTestDate())) {
            throw new IllegalArgumentException("请填写考核日期");
        }
    }

    public List<FitnessRecord> queryFiltered2(String personnelName, String idCard,
                                               String item, String grade, String startDate, String endDate,
                                               Long departmentId, String minScore, String maxScore,
                                               int page, int size, Long personnelId) {
        StringBuilder sql = new StringBuilder(
            "SELECT f.*, p.name AS personnel_name, p.birthday, p.id_card FROM fitness_record f " +
            "LEFT JOIN personnel p ON f.personnel_id = p.id WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<Object>();
        appendFilter2(sql, args, personnelName, idCard, item, grade, startDate, endDate, departmentId, minScore, maxScore, personnelId);
        sql.append(" ORDER BY f.test_date DESC, f.id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((long) (page - 1) * size);
        return jdbc.query(sql.toString(), new FitnessRowMapper(), args.toArray());
    }

    public long countFiltered2(String personnelName, String idCard,
                                String item, String grade, String startDate, String endDate,
                                Long departmentId, String minScore, String maxScore, Long personnelId) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM fitness_record f " +
            "LEFT JOIN personnel p ON f.personnel_id = p.id WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<Object>();
        appendFilter2(sql, args, personnelName, idCard, item, grade, startDate, endDate, departmentId, minScore, maxScore, personnelId);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private void appendFilter2(StringBuilder sql, java.util.List<Object> args,
                                String personnelName, String idCard,
                                String item, String grade, String startDate, String endDate,
                                Long departmentId, String minScore, String maxScore,
                                Long personnelId) {
        sql.append(" AND ").append(DataScope.personSql("f.personnel_id"));
        // 支持只填到月(2026-09):起始补-01,终止补-31(字符串比较安全)
        if (startDate != null && startDate.trim().length() == 7) { startDate = startDate.trim() + "-01"; }
        if (endDate != null && endDate.trim().length() == 7) { endDate = endDate.trim() + "-31"; }
        if (personnelId != null && personnelId > 0) {
            sql.append(" AND f.personnel_id = ?"); args.add(personnelId);
        }
        if (personnelName != null && !personnelName.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ?"); args.add("%" + personnelName.trim() + "%");
        }
        if (idCard != null && !idCard.trim().isEmpty()) {
            sql.append(" AND p.id_card LIKE ?"); args.add("%" + idCard.trim() + "%");
        }
        if (item != null && !item.trim().isEmpty()) {
            sql.append(" AND f.item = ?"); args.add(item.trim());
        }
        if (grade != null && !grade.trim().isEmpty()) {
            String g = grade.trim();
            // 分数条件按数字分列过滤;文字评定只做精确匹配,避免把"优秀/良好"当数字比较
            if (g.contains("分以上")) {
                sql.append(" AND f.points >= ?"); args.add(Double.parseDouble(g.replace("分以上", "")));
            } else if (g.contains("分以下")) {
                sql.append(" AND f.points < ?"); args.add(Double.parseDouble(g.replace("分以下", "")));
            } else if (g.contains("-")) {
                String[] parts = g.replace("分", "").split("-");
                sql.append(" AND f.points >= ? AND f.points <= ?");
                args.add(Double.parseDouble(parts[0]));
                args.add(Double.parseDouble(parts[1]));
            } else if ("不及格".equals(g)) {
                sql.append(" AND (f.points IS NULL OR f.points < 60)");
            } else {
                sql.append(" AND f.grade = ?"); args.add(g);
            }
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND f.test_date >= ?"); args.add(startDate.trim());
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND f.test_date <= ?"); args.add(endDate.trim());
        }
        if (departmentId != null && departmentId > 0) {
            sql.append(" AND p.department_id = ?"); args.add(departmentId);
        }
        if (minScore != null && !minScore.trim().isEmpty()) {
            try { sql.append(" AND f.points >= ?"); args.add(Double.parseDouble(minScore.trim())); } catch (NumberFormatException ignore) {}
        }
        if (maxScore != null && !maxScore.trim().isEmpty()) {
            try { sql.append(" AND f.points <= ?"); args.add(Double.parseDouble(maxScore.trim())); } catch (NumberFormatException ignore) {}
        }
    }

    public List<FitnessRecord> queryFiltered(String keyword, String item, String grade,
                                              String startDate, String endDate,
                                              Long departmentId, String minScore, String maxScore,
                                              int page, int size) {
        StringBuilder sql = new StringBuilder(
            "SELECT f.*, p.name AS personnel_name, p.birthday, p.id_card FROM fitness_record f " +
            "LEFT JOIN personnel p ON f.personnel_id=p.id WHERE 1=1");
        List<Object> args = new java.util.ArrayList<Object>();
        appendFilter(sql, args, keyword, item, grade, startDate, endDate, departmentId, minScore, maxScore);
        sql.append(" ORDER BY f.test_date DESC, f.id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((page - 1) * size);
        return jdbc.query(sql.toString(), new FitnessRowMapper(), args.toArray());
    }

    public long countFiltered(String keyword, String item, String grade,
                               String startDate, String endDate,
                               Long departmentId, String minScore, String maxScore) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM fitness_record f " +
            "LEFT JOIN personnel p ON f.personnel_id=p.id WHERE 1=1");
        List<Object> args = new java.util.ArrayList<Object>();
        appendFilter(sql, args, keyword, item, grade, startDate, endDate, departmentId, minScore, maxScore);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public java.util.Map<String, Object> statsFiltered(String keyword, String item, String grade,
                                              String startDate, String endDate,
                                              Long departmentId, String minScore, String maxScore) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) as total, " +
            "SUM(CASE WHEN CAST(f.grade AS REAL) >= 60 THEN 1 ELSE 0 END) as passCount, " +
            "SUM(CASE WHEN CAST(f.grade AS REAL) >= 90 THEN 1 ELSE 0 END) as excellentCount, " +
            "AVG(CAST(f.grade AS REAL)) as avgScore " +
            "FROM fitness_record f LEFT JOIN personnel p ON f.personnel_id=p.id WHERE 1=1");
        List<Object> args = new java.util.ArrayList<Object>();
        appendFilter(sql, args, keyword, item, grade, startDate, endDate, departmentId, minScore, maxScore);
        return jdbc.queryForMap(sql.toString(), args.toArray());
    }

    /**
     * 宽行汇总:按 人员+考核日期 分组返回各项目成绩,分页按组进行。
     * total = 匹配筛选条件的 distinct(人员,考核日期) 组数;未填项目不出现键;
     * items = 启用体能项目名(按字典 sort,id 排序)。权限 DataScope 与列表查询同口径。
     */
    public java.util.Map<String, Object> summary(String personnelName, String idCard, String item, String grade,
                                                 String startDate, String endDate, Long departmentId,
                                                 String minScore, String maxScore, Long personnelId,
                                                 int page, int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 20;
        String base = " FROM fitness_record f LEFT JOIN personnel p ON f.personnel_id=p.id WHERE 1=1";
        StringBuilder whereSql = new StringBuilder();
        java.util.List<Object> whereArgs = new java.util.ArrayList<Object>();
        appendFilter2(whereSql, whereArgs, personnelName, idCard, item, grade,
                startDate, endDate, departmentId, minScore, maxScore, personnelId);
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM (SELECT DISTINCT f.personnel_id, f.test_date" + base + whereSql + ")",
            Long.class, whereArgs.toArray());
        java.util.List<java.util.Map<String, Object>> groups = jdbc.queryForList(
            "SELECT f.personnel_id, MAX(p.name) AS personnel_name, MAX(p.id_card) AS id_card, f.test_date" + base + whereSql +
            " GROUP BY f.personnel_id, f.test_date ORDER BY f.test_date DESC, f.personnel_id DESC LIMIT ? OFFSET ?",
            concat(whereArgs, java.util.Arrays.<Object>asList(size, (long) (page - 1) * size)).toArray());
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<java.util.Map<String, Object>>();
        if (!groups.isEmpty()) {
            // 组内记录沿用同一筛选条件(如按项目/分数过滤时,items 只包含匹配记录)。
            StringBuilder recordSql = new StringBuilder(
                "SELECT f.*, p.name AS personnel_name, p.birthday, p.id_card FROM fitness_record f " +
                "LEFT JOIN personnel p ON f.personnel_id=p.id WHERE 1=1");
            java.util.List<Object> recordArgs = new java.util.ArrayList<Object>(whereArgs);
            recordSql.append(" AND (");
            for (int i = 0; i < groups.size(); i++) {
                if (i > 0) recordSql.append(" OR ");
                recordSql.append("(f.personnel_id=? AND f.test_date=?)");
                recordArgs.add(((Number) groups.get(i).get("personnel_id")).longValue());
                recordArgs.add(String.valueOf(groups.get(i).get("test_date")));
            }
            recordSql.append(") ORDER BY f.id");
            List<FitnessRecord> records = jdbc.query(recordSql.toString(), new FitnessRowMapper(), recordArgs.toArray());
            // 组键 = 人员+日期;同一人员在页内可出现多个日期组,不得跨组合并。
            java.util.Map<String, java.util.Map<String, FitnessRecord>> byGroup =
                    new java.util.LinkedHashMap<String, java.util.Map<String, FitnessRecord>>();
            for (FitnessRecord r : records) {
                String key = r.getPersonnelId() + "|" + r.getTestDate();
                java.util.Map<String, FitnessRecord> items = byGroup.get(key);
                if (items == null) {
                    items = new java.util.LinkedHashMap<String, FitnessRecord>();
                    byGroup.put(key, items);
                }
                // 同组同项目如存在历史重复记录,保留 id 最大(最新)的一条。
                FitnessRecord existing = items.get(r.getItem());
                if (existing == null || existing.getId() < r.getId()) items.put(r.getItem(), r);
            }
            for (java.util.Map<String, Object> group : groups) {
                long pid = ((Number) group.get("personnel_id")).longValue();
                String testDate = String.valueOf(group.get("test_date"));
                java.util.Map<String, Object> entry = new java.util.LinkedHashMap<String, Object>();
                entry.put("personnelId", pid);
                entry.put("personnelName", group.get("personnel_name") == null ? "" : String.valueOf(group.get("personnel_name")));
                entry.put("idCard", group.get("id_card") == null ? "" : String.valueOf(group.get("id_card")));
                entry.put("testDate", testDate);
                java.util.Map<String, Object> itemMap = new java.util.LinkedHashMap<String, Object>();
                java.util.Map<String, FitnessRecord> items = byGroup.get(pid + "|" + testDate);
                if (items != null) {
                    for (java.util.Map.Entry<String, FitnessRecord> e : items.entrySet()) {
                        java.util.Map<String, Object> cell = new java.util.LinkedHashMap<String, Object>();
                        cell.put("id", e.getValue().getId());
                        cell.put("score", e.getValue().getScore());
                        cell.put("points", e.getValue().getPoints());
                        cell.put("grade", e.getValue().getGrade());
                        itemMap.put(e.getKey(), cell);
                    }
                }
                entry.put("items", itemMap);
                list.add(entry);
            }
        }
        java.util.List<String> enabledItems = new java.util.ArrayList<String>();
        for (String label : jdbc.queryForList(
                "SELECT dict_label FROM sys_dict WHERE dict_type='fitness_item' AND enabled=1 ORDER BY sort, id",
                String.class)) {
            String value = label == null ? "" : label.trim();
            if (!value.isEmpty()) enabledItems.add(value);
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();
        result.put("total", total == null ? 0 : total);
        result.put("list", list);
        result.put("items", enabledItems);
        return result;
    }

    private static java.util.List<Object> concat(java.util.List<Object> first, java.util.List<Object> second) {
        java.util.List<Object> result = new java.util.ArrayList<Object>(first);
        result.addAll(second);
        return result;
    }

    private void appendFilter(StringBuilder sql, List<Object> args, String keyword,
                               String item, String grade, String startDate, String endDate,
                               Long departmentId, String minScore, String maxScore) {
        sql.append(" AND ").append(DataScope.personSql("f.personnel_id"));
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (p.name LIKE ? OR f.item LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like);
        }
        if (item != null && !item.trim().isEmpty()) {
            sql.append(" AND f.item = ?");
            args.add(item.trim());
        }
        if (grade != null && !grade.trim().isEmpty()) {
            String g = grade.trim();
            if (g.contains("\u5206\u4ee5\u4e0a")) {
                sql.append(" AND CAST(f.grade AS REAL) >= ?");
                args.add(Double.parseDouble(g.replace("\u5206\u4ee5\u4e0a", "")));
            } else if (g.contains("\u5206\u4ee5\u4e0b")) {
                sql.append(" AND CAST(f.grade AS REAL) < ?");
                args.add(Double.parseDouble(g.replace("\u5206\u4ee5\u4e0b", "")));
            } else if (g.contains("-")) {
                String[] parts = g.replace("\u5206", "").split("-");
                sql.append(" AND CAST(f.grade AS REAL) >= ? AND CAST(f.grade AS REAL) <= ?");
                args.add(Double.parseDouble(parts[0]));
                args.add(Double.parseDouble(parts[1]));
            } else {
                sql.append(" AND f.grade = ?");
                args.add(g);
            }
        }
        String sd = startDate;
        String ed = endDate;
        if (sd != null && sd.trim().length() == 7) { sd = sd.trim() + "-01"; }
        if (ed != null && ed.trim().length() == 7) { ed = ed.trim() + "-31"; }
        if (sd != null && !sd.trim().isEmpty()) {
            sql.append(" AND f.test_date >= ?");
            args.add(sd.trim());
        }
        if (ed != null && !ed.trim().isEmpty()) {
            sql.append(" AND f.test_date <= ?");
            args.add(ed.trim());
        }
        if (departmentId != null && departmentId > 0) {
            sql.append(" AND p.department_id = ?");
            args.add(departmentId);
        }
        if (minScore != null && !minScore.trim().isEmpty()) {
            try {
                sql.append(" AND CAST(f.grade AS REAL) >= ?");
                args.add(Double.parseDouble(minScore.trim()));
            } catch (NumberFormatException ignore) { }
        }
        if (maxScore != null && !maxScore.trim().isEmpty()) {
            try {
                sql.append(" AND CAST(f.grade AS REAL) <= ?");
                args.add(Double.parseDouble(maxScore.trim()));
            } catch (NumberFormatException ignore) { }
        }
    }

    private static class FitnessRowMapper implements org.springframework.jdbc.core.RowMapper<FitnessRecord> {
        @Override
        public FitnessRecord mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
            FitnessRecord r = new FitnessRecord();
            r.setId(rs.getLong("id"));
            r.setPersonnelId(rs.getLong("personnel_id"));
            r.setPersonnelName(rs.getString("personnel_name"));
            r.setItem(rs.getString("item"));
            r.setScore(rs.getString("score"));
            r.setGrade(rs.getString("grade"));
            r.setTestDate(rs.getString("test_date"));
            r.setTester(rs.getString("tester"));
            r.setRemark(rs.getString("remark"));
            r.setBirthday(rs.getString("birthday"));
            r.setIdCard(rs.getString("id_card"));
            r.setPoints(rs.getObject("points") == null ? null : rs.getDouble("points"));
            return r;
        }
    }
}
