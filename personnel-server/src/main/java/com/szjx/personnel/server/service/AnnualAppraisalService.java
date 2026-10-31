package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.AnnualAppraisalRecord;
import com.szjx.personnel.server.util.DepartmentHierarchy;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/** 年度考评：先限定当前人员权限，再进行筛选、分页及年度汇总；一人一年一条。 */
@Service
public class AnnualAppraisalService {
    private final JdbcTemplate jdbc;
    private static final String FROM = " FROM annual_appraisal_record r JOIN personnel p ON p.id=r.personnel_id";
    private static final String SELECT = "SELECT r.*,p.name AS personnel_name,p.id_card AS current_id_card";
    private static final String DUPLICATE = "该人员本年度已存在考评记录，请编辑原记录";
    private static final RowMapper<AnnualAppraisalRecord> MAPPER = (rs, index) -> {
        AnnualAppraisalRecord r = new AnnualAppraisalRecord();
        r.setId(rs.getLong("id")); r.setPersonnelId(rs.getLong("personnel_id"));
        r.setYear(rs.getInt("year"));
        r.setPersonnelName(rs.getString("personnel_name")); r.setIdCard(rs.getString("current_id_card"));
        r.setRating(rs.getString("rating")); r.setRatingDate(rs.getString("rating_date"));
        r.setCreatedAt(rs.getString("created_at")); r.setUpdatedAt(rs.getString("updated_at"));
        return r;
    };

    public AnnualAppraisalService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String,Object> query(Map<String,String> filters) {
        Map<String,String> f = safe(filters);
        Sql where = personFilters(f);
        Integer year = optionalInt(f, "year", 1900, 9999);
        if (year != null) where.add("r.year=?", year);
        String rating = text(f.get("rating"));
        if (!rating.isEmpty()) { requireRating(rating); where.add("r.rating=?", rating); }
        String start = text(f.get("ratingDateStart")), end = text(f.get("ratingDateEnd"));
        if (!start.isEmpty()) { requireDate(start); where.add("r.rating_date>=?", start); }
        if (!end.isEmpty()) { requireDate(end); where.add("r.rating_date<=?", end); }
        if (!start.isEmpty() && !end.isEmpty() && start.compareTo(end) > 0) throw new IllegalArgumentException("考评日期开始不能晚于结束");
        int page = page(f), size = size(f);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + FROM + where.sql, Long.class, where.args.toArray());
        List<Object> args = new ArrayList<>(where.args); args.add(size); args.add(offset(page, size));
        List<AnnualAppraisalRecord> list = jdbc.query(SELECT + FROM + where.sql + " ORDER BY r.year DESC,r.id DESC LIMIT ? OFFSET ?", MAPPER, args.toArray());
        return result(total, list);
    }

    public AnnualAppraisalRecord get(Long id) {
        DataScope.requireRecord(jdbc, "annual_appraisal_record", "personnel_id", id);
        List<AnnualAppraisalRecord> rows = jdbc.query(SELECT + FROM + " WHERE r.id=? AND " + DataScope.departmentSql("p.department_id"), MAPPER, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("年度考评记录不存在");
        return rows.get(0);
    }

    @Transactional
    public AnnualAppraisalRecord create(AnnualAppraisalRecord record) {
        validate(record);
        Long personId = resolvePerson(record, false);
        ensureUnique(personId, record.getYear(), null);
        KeyHolder key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("INSERT INTO annual_appraisal_record(personnel_id,year,rating,rating_date) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, personId); ps.setInt(2, record.getYear());
                ps.setString(3, record.getRating()); ps.setString(4, record.getRatingDate());
                return ps;
            }, key);
        } catch (DataAccessException e) {
            ensureUnique(personId, record.getYear(), null);
            throw e;
        }
        if (key.getKey() == null) throw new IllegalStateException("新增考评记录未返回ID");
        return get(key.getKey().longValue());
    }

    @Transactional
    public AnnualAppraisalRecord update(Long id, AnnualAppraisalRecord record) {
        get(id); // 旧关联人员也必须在权限范围内，不可借改绑越权。
        validate(record);
        Long personId = resolvePerson(record, false);
        ensureUnique(personId, record.getYear(), id);
        try {
            jdbc.update("UPDATE annual_appraisal_record SET personnel_id=?,year=?,rating=?,rating_date=?,updated_at=datetime('now','localtime') WHERE id=?",
                    personId, record.getYear(), record.getRating(), record.getRatingDate(), id);
        } catch (DataAccessException e) {
            ensureUnique(personId, record.getYear(), id);
            throw e;
        }
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        jdbc.update("DELETE FROM annual_appraisal_record WHERE id=?", id);
    }

    /** Excel 唯一允许的组合键 upsert；绝不按姓名猜测人员或修改人员档案。 */
    @Transactional
    public Map<String,Object> importRecord(Map<String,String> row) {
        Map<String,String> f = safe(row);
        AnnualAppraisalRecord record = new AnnualAppraisalRecord();
        record.setYear(requiredInt(f.get("year"), "year", 1900, 9999));
        record.setPersonnelName(text(f.get("personnelName"))); record.setIdCard(text(f.get("idCard")));
        record.setRating(f.get("rating")); record.setRatingDate(f.get("ratingDate"));
        validate(record);
        Long personId = resolvePerson(record, true);
        List<Long> existing = jdbc.queryForList("SELECT id FROM annual_appraisal_record WHERE personnel_id=? AND year=?", Long.class, personId, record.getYear());
        AnnualAppraisalRecord saved;
        if (existing.isEmpty()) saved = create(record);
        else { record.setPersonnelId(personId); saved = update(existing.get(0), record); }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("updated", !existing.isEmpty()); result.put("record", saved);
        return result;
    }

    /** 年度全员视图：全部权限内人员一人一行，entry 为当年考评（无则 null）。 */
    public Map<String,Object> stats(Map<String,String> filters) {
        Map<String,String> f = safe(filters);
        Integer selectedYear = optionalInt(f, "year", 1900, 9999);
        int year = selectedYear == null ? LocalDate.now().getYear() : selectedYear;
        Sql where = personFilters(f);
        String rating = text(f.get("rating"));
        if (!rating.isEmpty()) {
            if ("unrated".equals(rating)) {
                where.add("a.id IS NULL"); // 无占位符条件不得追加参数,否则参数与占位符错位
            } else {
                requireRating(rating);
                where.add("a.rating=?", rating);
            }
        }
        String from = " FROM personnel p LEFT JOIN annual_appraisal_record a ON a.personnel_id=p.id AND a.year=?";
        List<Object> args = new ArrayList<>();
        args.add(year);
        args.addAll(where.args);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where.sql, Long.class, args.toArray());
        String select = "SELECT p.id,p.name,p.id_card,p.department_id AS p_department_id,a.id AS aid,a.rating AS arating,a.rating_date AS adate";
        DepartmentHierarchy hierarchy = DepartmentHierarchy.load(jdbc);
        int page = page(f), size = size(f); args.add(size); args.add(offset(page, size));
        List<Map<String,Object>> list = jdbc.query(select + from + where.sql + " ORDER BY p.id LIMIT ? OFFSET ?", (rs, index) -> {
            Map<String,Object> person = new LinkedHashMap<>();
            person.put("personnelId", rs.getLong("id")); person.put("personnelName", rs.getString("name"));
            person.put("idCard", rs.getString("id_card")); person.put("year", year);
            Object department = rs.getObject("p_department_id");
            person.put("departmentPath", department == null ? "" : hierarchy.path(((Number) department).longValue()));
            Object recordId = rs.getObject("aid");
            Map<String,Object> entry = null;
            if (recordId != null) {
                entry = new LinkedHashMap<>(); entry.put("id", ((Number) recordId).longValue());
                entry.put("rating", rs.getString("arating")); entry.put("ratingDate", rs.getString("adate"));
            }
            person.put("entry", entry);
            return person;
        }, args.toArray());
        Map<String,Object> result = result(total, list); result.put("year", year);
        return result;
    }

    private Sql personFilters(Map<String,String> f) {
        Sql where = new Sql();
        where.sql.append(" WHERE ").append(DataScope.departmentSql("p.department_id"));
        Set<Long> selected = selectedPersonnel(f);
        if (selected != null) {
            where.sql.append(" AND p.id IN (").append(placeholders(selected.size())).append(')');
            where.args.addAll(selected);
        }
        for (String[] field : new String[][]{{"personnelName","p.name"},{"idCard","p.id_card"}}) {
            String value = text(f.get(field[0]));
            if (!value.isEmpty()) where.add(field[1] + " LIKE ? ESCAPE '\\'", "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        String department = text(f.get("departmentId"));
        if (!department.isEmpty()) {
            long id;
            try { id = Long.parseLong(department); } catch (NumberFormatException e) { throw new IllegalArgumentException("departmentId必须为有效单位ID"); }
            if (id <= 0) throw new IllegalArgumentException("departmentId必须为有效单位ID");
            where.add("p.department_id IN (WITH RECURSIVE subtree(id) AS (SELECT id FROM department WHERE id=? UNION SELECT d.id FROM department d JOIN subtree s ON d.parent_id=s.id) SELECT id FROM subtree)", id);
        }
        return where;
    }

    /**
     * Explicit personnel whitelist shared by query/stats and selected-record export.
     * Absent means legacy scoped-all behavior; present-but-empty is never an all-data request.
     * Validate every ID in one parameterized lookup before applying other filters or pagination.
     */
    public Set<Long> selectedPersonnel(Map<String,String> filters) {
        Map<String,String> f = safe(filters);
        if (!f.containsKey("personnelIds")) return null;
        String value = f.get("personnelIds");
        if (value == null) throw new IllegalArgumentException("personnelIds不能为空，请先选择人员");
        String[] parts = value.split(",", -1);
        if (parts.length > 200) throw new IllegalArgumentException("personnelIds最多允许200项人员ID");
        Set<Long> selected = new LinkedHashSet<>();
        for (String part : parts) {
            String id = part.trim();
            if (!id.matches("[1-9][0-9]*")) throw new IllegalArgumentException("personnelIds必须为逗号分隔的正整数，且不能包含空项");
            try {
                selected.add(Long.valueOf(id));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("personnelIds中的人员ID超出有效范围");
            }
        }
        List<Map<String,Object>> people = jdbc.queryForList(
                "SELECT id,department_id FROM personnel WHERE id IN (" + placeholders(selected.size()) + ")", selected.toArray());
        // Check scope before disclosing missing IDs; never return a partial selection.
        for (Map<String,Object> person : people) {
            Number department = (Number) person.get("department_id");
            DataScope.requireDepartment(department == null ? null : department.longValue());
        }
        if (people.size() != selected.size()) throw new IllegalArgumentException("personnelIds中包含不存在的人员，请刷新后重新选择");
        return Collections.unmodifiableSet(selected);
    }

    private static String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }

    private Long resolvePerson(AnnualAppraisalRecord record, boolean importing) {
        String card = text(record.getIdCard()).toUpperCase(Locale.ROOT), name = text(record.getPersonnelName());
        List<Map<String,Object>> people;
        if (importing || record.getPersonnelId() == null) {
            if (card.isEmpty()) throw new IllegalArgumentException("身份证号不能为空，请选择关联人员");
            people = jdbc.queryForList("SELECT id,name,id_card FROM personnel WHERE UPPER(TRIM(id_card))=?", card);
        } else {
            DataScope.requirePerson(jdbc, record.getPersonnelId());
            people = jdbc.queryForList("SELECT id,name,id_card FROM personnel WHERE id=?", record.getPersonnelId());
        }
        if (people.isEmpty()) throw new IllegalArgumentException("身份证号对应的人员不存在");
        if (people.size() != 1) throw new IllegalArgumentException("身份证号对应多个档案，请先修正人员档案");
        Map<String,Object> person = people.get(0);
        Long id = ((Number) person.get("id")).longValue();
        DataScope.requirePerson(jdbc, id);
        if (importing && name.isEmpty()) throw new IllegalArgumentException("姓名不能为空");
        if (!name.isEmpty() && !name.equals(text((String) person.get("name")))) throw new IllegalArgumentException("姓名与身份证号对应的人员档案不一致");
        if (!card.isEmpty() && !card.equals(text((String) person.get("id_card")).toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("身份证号与关联人员档案不一致");
        return id;
    }

    private void ensureUnique(Long personId, Integer year, Long excluded) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM annual_appraisal_record WHERE personnel_id=? AND year=?" + (excluded == null ? "" : " AND id<>?"), Long.class,
                excluded == null ? new Object[]{personId,year} : new Object[]{personId,year,excluded});
        if (count != null && count > 0) throw new IllegalArgumentException(DUPLICATE);
    }

    /** 实例方法:评定合法值随数据字典变化,validate/requireRating 不再是 static */
    private void validate(AnnualAppraisalRecord r) {
        if (r == null) throw new IllegalArgumentException("考评记录不能为空");
        if (r.getYear() == null || r.getYear() < 1900 || r.getYear() > 9999) throw new IllegalArgumentException("年度必须在1900至9999之间");
        requireRating(r.getRating()); requireDate(r.getRatingDate());
    }
    /** 评定合法值：以数据字典 annual_rating 启用项为准（管理员可在数据字典维护），字典缺失时回退默认四档 */
    private List<String> ratingOptions() {
        List<String> labels = jdbc.queryForList(
                "SELECT dict_label FROM sys_dict WHERE dict_type='annual_rating' AND enabled=1 ORDER BY sort,id", String.class);
        return labels.isEmpty() ? Arrays.asList("优秀", "称职", "一般称职", "不称职") : labels;
    }
    private void requireRating(String rating) {
        List<String> options = ratingOptions();
        if (!options.contains(rating)) throw new IllegalArgumentException("评定只能为" + String.join("、", options) + "；未评定不可保存");
    }
    private static void requireDate(String date) {
        if (date == null || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("考评日期必须为有效的yyyy-MM-dd日期");
        try {
            if (LocalDate.parse(date).getYear() < 1) throw new IllegalArgumentException("考评日期年份必须大于0");
        } catch (DateTimeParseException e) { throw new IllegalArgumentException("考评日期必须为有效的yyyy-MM-dd日期"); }
    }
    private static String text(String s) { return s == null ? "" : s.trim(); }
    private static Map<String,String> safe(Map<String,String> f) { return f == null ? Collections.emptyMap() : f; }
    private static Integer optionalInt(Map<String,String> f, String key, int min, int max) { return text(f.get(key)).isEmpty() ? null : requiredInt(f.get(key), key, min, max); }
    private static int requiredInt(String value, String key, int min, int max) {
        try { int number = Integer.parseInt(text(value)); if (number >= min && number <= max) return number; } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(key + "必须为" + min + "至" + max + "的整数");
    }
    private static int page(Map<String,String> f) { Integer n = optionalInt(f,"page",1,Integer.MAX_VALUE); return n == null ? 1 : n; }
    private static int size(Map<String,String> f) { Integer n = optionalInt(f,"size",1,50000); return n == null ? 20 : n; }
    private static long offset(int page, int size) { return ((long) page - 1) * size; }
    private static Map<String,Object> result(long total, Object list) { Map<String,Object> r = new LinkedHashMap<>(); r.put("total",total); r.put("list",list); return r; }
    private static class Sql {
        final StringBuilder sql = new StringBuilder();
        final List<Object> args = new ArrayList<>();
        void add(String clause, Object value) { sql.append(" AND ").append(clause); args.add(value); }
        void add(String clause) { sql.append(" AND ").append(clause); }
    }
}
