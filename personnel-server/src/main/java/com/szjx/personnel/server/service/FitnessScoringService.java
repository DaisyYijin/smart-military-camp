package com.szjx.personnel.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szjx.personnel.server.model.FitnessRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Authoritative scoring shared by ordinary CRUD and Excel imports. */
@Service
public class FitnessScoringService {
    private final JdbcTemplate jdbc;
    public FitnessScoringService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void score(FitnessRecord record) {
        if (record == null || record.getPersonnelId() == null || record.getPersonnelId() <= 0)
            throw new IllegalArgumentException("请选择考核人员");
        List<Map<String, Object>> people = jdbc.queryForList("SELECT birthday, gender, height FROM personnel WHERE id=?", record.getPersonnelId());
        if (people.size() != 1) throw new IllegalArgumentException("所选人员不存在或不唯一");
        LocalDate date = date(record.getTestDate(), "考核日期");
        Map<String, Object> person = people.get(0);
        LocalDate birth = date(text(person.get("birthday")), "人员出生日期");
        if (birth.isAfter(date)) throw new IllegalArgumentException("考核日期早于出生日期");
        int age = Period.between(birth, date).getYears();
        String item = text(record.getItem());
        if (item.isEmpty()) throw new IllegalArgumentException("请填写考核项目");
        double points;
        String normalized;
        if ("BMI".equals(item) || "体重".equals(item)) {
            double weight = performance(record.getScore(), "count");
            if (weight < 25 || weight > 200) throw new IllegalArgumentException("体重成绩应为25~200kg");
            String gender = text(person.get("gender"));
            if (!"男".equals(gender) && !"女".equals(gender)) throw new IllegalArgumentException("请维护人员性别");
            double height = number(text(person.get("height")), "身高");
            if (height < 140 || height > 210) throw new IllegalArgumentException("请维护140~210cm的身高");
            JsonNode standard = setting("bmi_standard");
            JsonNode ages = standard.path("ages");
            int column = -1;
            for (int i = 0; i < ages.size(); i++) if (inAgeGroup(ages.get(i).asText(), age)) {
                if (column >= 0) throw new IllegalArgumentException("体重标准年龄段重叠");
                column = i;
            }
            if (column < 0) throw new IllegalArgumentException("体重标准年龄段未覆盖考核年龄");
            JsonNode hit = null;
            double hm = Math.round(height) / 100.0;
            for (JsonNode row : standard.path("女".equals(gender) ? "female" : "male").path("rows")) {
                if (Math.abs(row.path("h").asDouble() - hm) < 0.00001) {
                    if (hit != null) throw new IllegalArgumentException("体重标准身高行重复");
                    hit = row;
                }
            }
            if (hit == null) throw new IllegalArgumentException("体重标准缺少对应身高行");
            JsonNode range = hit.path("w").path(column);
            if (!range.isArray() || range.size() != 2) throw new IllegalArgumentException("体重标准区间未维护");
            double low = number(range.get(0).asText(), "体重下限");
            double high = number(range.get(1).asText(), "体重上限");
            if (low > high) throw new IllegalArgumentException("体重标准区间无效");
            points = weight >= low && weight <= high ? 100 : 0;
            normalized = trimNumber(weight);
        } else {
            List<Map<String, Object>> matrix = jdbc.queryForList("SELECT age_group, score, value, unit FROM fitness_matrix WHERE item=? ORDER BY CAST(score AS REAL) DESC", item);
            if (matrix.isEmpty()) throw new IllegalArgumentException("考核项目未维护体能标准");
            Set<String> units = new HashSet<String>();
            Set<String> groups = new LinkedHashSet<String>();
            for (Map<String, Object> row : matrix) { units.add(text(row.get("unit"))); groups.add(text(row.get("age_group"))); }
            if (units.size() != 1 || (!units.contains("time") && !units.contains("count")))
                throw new IllegalArgumentException("体能标准计分方式缺失或不一致，请维护unit=time/count");
            String unit = units.iterator().next();
            double value = performance(record.getScore(), unit);
            String group = null;
            for (String candidate : groups) if (inAgeGroup(candidate, age)) {
                if (group != null) throw new IllegalArgumentException("体能标准年龄段重叠");
                group = candidate;
            }
            if (group == null) throw new IllegalArgumentException("体能标准年龄段未覆盖考核年龄");
            // Existing product rule: performances below the lowest threshold receive 55.
            points = 55;
            Set<Double> scores = new HashSet<Double>();
            Double previous = null;
            for (Map<String, Object> row : matrix) {
                if (!group.equals(text(row.get("age_group")))) continue;
                double point = number(text(row.get("score")), "矩阵分数");
                double need = number(text(row.get("value")), "矩阵成绩");
                if (!scores.add(point)) throw new IllegalArgumentException("矩阵分数重复");
                if (previous != null && ("time".equals(unit) ? need < previous : need > previous))
                    throw new IllegalArgumentException("矩阵成绩与计分方向不一致");
                previous = need;
            }
            for (Map<String, Object> row : matrix) {
                if (!group.equals(text(row.get("age_group")))) continue;
                double need = number(text(row.get("value")), "矩阵成绩");
                if ("time".equals(unit) ? value <= need : value >= need) {
                    points = number(text(row.get("score")), "矩阵分数"); break;
                }
            }
            normalized = formatPerformance(value, unit);
        }
        record.setItem(item);
        record.setTestDate(date.toString());
        // 成绩统一格式入库:时间型 → X分Y秒 / Y秒;次数型/体重 → 纯数字
        record.setScore(normalized);
        record.setPoints(points);
        record.setGrade(grade(points));
    }

    /** 成绩统一显示格式:时间型转为「X分Y秒」(不足1分钟为「Y秒」),次数型为纯数字 */
    static String formatPerformance(double value, String unit) {
        if (!"time".equals(unit)) { return trimNumber(value); }
        if (value < 60) { return trimNumber(value) + "秒"; }
        long minutes = (long) (value / 60);
        double seconds = Math.round((value - minutes * 60) * 100) / 100.0;
        return minutes + "分" + trimNumber(seconds) + "秒";
    }

    /** 数字文本:最多两位小数,去掉多余的零(40.0→40,2.35→2.35) */
    static String trimNumber(double value) {
        double rounded = Math.round(value * 100) / 100.0;
        if (rounded == Math.floor(rounded)) { return String.valueOf((long) rounded); }
        return String.valueOf(rounded);
    }

    public String grade(double points) {
        JsonNode rules = setting("fitness_grade_rule");
        if (!rules.isArray() || rules.size() == 0) throw new IllegalArgumentException("请维护体能评定标准");
        List<JsonNode> sorted = new ArrayList<JsonNode>();
        for (JsonNode rule : rules) {
            number(rule.path("min").asText(), "评定阈值");
            if (text(rule.path("label").asText()).isEmpty() || !(">".equals(rule.path("op").asText()) || ">=".equals(rule.path("op").asText())))
                throw new IllegalArgumentException("体能评定标准无效");
            sorted.add(rule);
        }
        sorted.sort((a, b) -> Double.compare(b.path("min").asDouble(), a.path("min").asDouble()));
        for (JsonNode rule : sorted) {
            double min = rule.path("min").asDouble();
            if (">".equals(rule.path("op").asText()) ? points > min : points >= min) return rule.path("label").asText();
        }
        return "不及格";
    }

    private JsonNode setting(String key) {
        List<String> settings = jdbc.queryForList("SELECT svalue FROM sys_setting WHERE skey=?", String.class, key);
        if (settings.size() != 1) throw new IllegalArgumentException("请维护设置：" + key);
        try { return new ObjectMapper().readTree(settings.get(0)); }
        catch (Exception e) { throw new IllegalArgumentException("设置格式错误：" + key); }
    }

    public static LocalDate date(String raw, String label) {
        String value = text(raw);
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException(label + "应为真实日期yyyy-MM-dd");
        try { return LocalDate.parse(value); }
        catch (Exception e) { throw new IllegalArgumentException(label + "不是有效日期"); }
    }

    public static double number(String raw, String label) {
        try {
            double value = Double.parseDouble(text(raw));
            if (!Double.isFinite(value) || value < 0) throw new NumberFormatException();
            return value;
        } catch (Exception e) { throw new IllegalArgumentException(label + "必须为非负有限数值"); }
    }

    public static double performance(String raw, String unit) {
        String value = text(raw);
        if ("time".equals(unit)) {
            Matcher clock = Pattern.compile("^(\\d+)\\s*[:'′分]\\s*(\\d{1,2}(?:\\.\\d+)?)\\s*(?:[\"″秒])?$").matcher(value);
            if (clock.matches()) {
                double seconds = number(clock.group(2), "秒数");
                if (seconds >= 60) throw new IllegalArgumentException("秒数必须小于60");
                return number(clock.group(1), "分钟") * 60 + seconds;
            }
            value = value.replaceFirst("\\s*秒$", "").trim();
        } else value = value.replaceFirst("\\s*(?:次|个|米|厘米|cm|kg|公斤|千克)$", "").trim();
        if (!value.matches("\\d+(?:\\.\\d+)?")) throw new IllegalArgumentException("成绩格式无效");
        return number(value, "成绩");
    }

    static boolean inAgeGroup(String label, int age) {
        Matcher matcher = Pattern.compile("\\d+").matcher(label);
        List<Integer> nums = new ArrayList<Integer>();
        while (matcher.find()) nums.add(Integer.parseInt(matcher.group()));
        if (nums.isEmpty()) return false;
        if (label.contains("以下") || label.contains("≤") || label.contains("<=")) return age <= nums.get(0);
        if (label.contains("以上") || label.contains("≥") || label.contains(">=")) return age >= nums.get(0);
        if (nums.size() == 1) return age == nums.get(0);
        return age >= nums.get(0) && age <= nums.get(1);
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
