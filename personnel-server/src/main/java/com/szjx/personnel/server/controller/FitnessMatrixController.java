package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 体能评分矩阵管理:项目 x 年龄段 x 分数 → 达标值
 */
@RestController
@RequestMapping("/api/fitness-matrix")
public class FitnessMatrixController {

    private final JdbcTemplate jdbc;

    public FitnessMatrixController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 全部项目名列表 */
    @GetMapping("/items")
    public ApiResponse items() {
        List<String> items = jdbc.queryForList(
                "SELECT DISTINCT item FROM fitness_matrix ORDER BY item", String.class);
        return ApiResponse.ok(items);
    }

    /** 某项目的全部矩阵行 */
    @GetMapping
    public ApiResponse list(@RequestParam String item) {
        return ApiResponse.ok(jdbc.queryForList(
                "SELECT * FROM fitness_matrix WHERE item=? ORDER BY score DESC, id", item));
    }

    /** 新增或更新一个单元格(item+age_group+score 相同则更新 value) */
    @PostMapping
    public ApiResponse upsert(@RequestBody Map<String, Object> body) {
        String item = str(body.get("item"));
        String ageGroup = str(body.get("age_group"));
        Integer score = asInt(body.get("score"));
        Double value = asDouble(body.get("value"));
        if (item.isEmpty() || ageGroup.isEmpty() || score == null) {
            return ApiResponse.error("项目/年龄段/分数不能为空");
        }
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id FROM fitness_matrix WHERE item=? AND age_group=? AND score=?",
                item, ageGroup, score);
        if (existing.isEmpty()) {
            jdbc.update("INSERT INTO fitness_matrix(item, age_group, score, value) VALUES(?,?,?,?)",
                    item, ageGroup, score, value == null ? 0 : value);
        } else {
            jdbc.update("UPDATE fitness_matrix SET value=?, updated_at=datetime('now','localtime') WHERE id=?",
                    value == null ? 0 : value, existing.get(0).get("id"));
        }
        return ApiResponse.ok(jdbc.queryForList(
                "SELECT * FROM fitness_matrix WHERE item=? AND age_group=? AND score=?",
                item, ageGroup, score));
    }

    /** 删除某项目某分数的整行 */
    @DeleteMapping
    public ApiResponse deleteRow(@RequestParam String item, @RequestParam Integer score) {
        jdbc.update("DELETE FROM fitness_matrix WHERE item=? AND score=?", item, score);
        return ApiResponse.ok();
    }

    /** 删除某项目某年龄段的整列 */
    @DeleteMapping("/column")
    public ApiResponse deleteColumn(@RequestParam String item, @RequestParam String age_group) {
        jdbc.update("DELETE FROM fitness_matrix WHERE item=? AND age_group=?", item, age_group);
        return ApiResponse.ok();
    }

    /** 初始化新项目(创建矩阵结构并入库,关闭不丢失) */
    @SuppressWarnings("unchecked")
    @PostMapping("/init")
    public ApiResponse init(@RequestBody Map<String, Object> body) {
        String item = str(body.get("item"));
        String unit = str(body.get("unit"));
        if (item.isEmpty()) { return ApiResponse.error("项目名称不能为空"); }
        if (!"count".equals(unit) && !"time".equals(unit)) { unit = "count"; }
        List<String> ageGroups = (List<String>) body.get("ageGroups");
        List<Number> scores = (List<Number>) body.get("scores");
        if (ageGroups == null || ageGroups.isEmpty() || scores == null || scores.isEmpty()) {
            return ApiResponse.error("年龄段和分数不能为空");
        }
        jdbc.update("DELETE FROM fitness_matrix WHERE item=?", item);
        for (String g : ageGroups) {
            for (Number sc : scores) {
                jdbc.update("INSERT INTO fitness_matrix(item, age_group, score, value, unit) VALUES(?,?,?,?,?)",
                        item, g, sc.intValue(), 0.0, unit);
            }
        }
        return ApiResponse.ok(jdbc.queryForList("SELECT * FROM fitness_matrix WHERE item=? ORDER BY score DESC", item));
    }

    /** 复制项目(含全部矩阵数据) */
    @PostMapping("/copy")
    public ApiResponse copy(@RequestBody Map<String, Object> body) {
        String source = str(body.get("source"));
        String target = str(body.get("target"));
        if (source.isEmpty() || target.isEmpty()) { return ApiResponse.error("源项目和目标项目不能为空"); }
        List<Map<String, Object>> src = jdbc.queryForList("SELECT * FROM fitness_matrix WHERE item=?", source);
        if (src.isEmpty()) { return ApiResponse.error("源项目不存在"); }
        jdbc.update("DELETE FROM fitness_matrix WHERE item=?", target);
        for (Map<String, Object> row : src) {
            jdbc.update("INSERT INTO fitness_matrix(item, age_group, score, value, unit) VALUES(?,?,?,?,?)",
                    target, row.get("age_group"), row.get("score"), row.get("value"),
                    row.get("unit") == null ? "" : row.get("unit"));
        }
        return ApiResponse.ok();
    }

    /** 重命名项目 */
    @PostMapping("/rename")
    public ApiResponse rename(@RequestBody Map<String, Object> body) {
        String oldItem = str(body.get("old_item"));
        String newItem = str(body.get("new_item"));
        if (oldItem.isEmpty() || newItem.isEmpty()) {
            return ApiResponse.error("原名称和新名称不能为空");
        }
        if (!oldItem.equals(newItem)) {
            Long exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM fitness_matrix WHERE item=?", Long.class, newItem);
            if (exists != null && exists > 0) {
                return ApiResponse.error("新名称已存在");
            }
            jdbc.update("UPDATE fitness_matrix SET item=? WHERE item=?", newItem, oldItem);
        }
        return ApiResponse.ok();
    }

    /** 删除整个项目 */
    @DeleteMapping("/item")
    public ApiResponse deleteItem(@RequestParam String item) {
        jdbc.update("DELETE FROM fitness_matrix WHERE item=?", item);
        return ApiResponse.ok();
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private Integer asInt(Object o) {
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double asDouble(Object o) {
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
