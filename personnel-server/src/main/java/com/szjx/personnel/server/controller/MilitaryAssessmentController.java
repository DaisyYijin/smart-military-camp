package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.dao.MilitaryAssessmentDao;
import com.szjx.personnel.server.model.ApiResponse;
import com.szjx.personnel.server.model.MilitaryAssessment;
import com.szjx.personnel.server.model.PageResult;
import com.szjx.personnel.server.util.Dates;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 军事考核:考核记录 + 考核方案(两级课目占比,百分制加权)
 */
@RestController
@RequestMapping("/api/military")
public class MilitaryAssessmentController {

    private final MilitaryAssessmentDao dao;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();

    public MilitaryAssessmentController(MilitaryAssessmentDao dao, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.dao = dao;
        this.jdbc = jdbc;
    }

    /* ================= 考核方案 ================= */

    /** 方案树:[{id,name,remark,items:[{id,name,weight,children:[{id,name,weight}]}]}] */
    @GetMapping("/schemes")
    public ApiResponse schemes() {
        List<Map<String, Object>> out = new java.util.ArrayList<Map<String, Object>>();
        for (Map<String, Object> s : jdbc.queryForList("SELECT * FROM mil_scheme ORDER BY id")) {
            Map<String, Object> node = new java.util.LinkedHashMap<String, Object>();
            long sid = ((Number) s.get("id")).longValue();
            node.put("id", sid);
            node.put("name", s.get("name"));
            node.put("remark", s.get("remark"));
            node.put("items", schemeItems(sid));
            out.add(node);
        }
        return ApiResponse.ok(out);
    }

    private List<Map<String, Object>> schemeItems(long sid) {
        List<Map<String, Object>> tops = jdbc.queryForList(
                "SELECT id, name, weight FROM mil_scheme_item WHERE scheme_id=? AND parent_id=0 ORDER BY sort, id", sid);
        List<Map<String, Object>> out = new java.util.ArrayList<Map<String, Object>>();
        for (Map<String, Object> t : tops) {
            Map<String, Object> node = new java.util.LinkedHashMap<String, Object>(t);
            long tid = ((Number) t.get("id")).longValue();
            node.put("children", jdbc.queryForList(
                    "SELECT id, name, weight FROM mil_scheme_item WHERE scheme_id=? AND parent_id=? ORDER BY sort, id", sid, tid));
            out.add(node);
        }
        return out;
    }

    /** 新建/更新方案:body={id?, name, remark, items:[{name, weight, children:[{name,weight}]}]};顶级占比合计须为100,子项占其课目合计须为100 */
    @PostMapping("/schemes")
    public ApiResponse saveScheme(@RequestBody Map<String, Object> body) {
        String name = str(body.get("name"));
        if (name.isEmpty()) {
            return ApiResponse.error("请填写方案名称");
        }
        List<Map<String, Object>> items = castList(body.get("items"));
        if (items.isEmpty()) {
            return ApiResponse.error("请至少添加一个课目");
        }
        double topSum = 0;
        for (Map<String, Object> it : items) {
            double w = num(it.get("weight"), -1);
            if (w <= 0 || w > 100) {
                return ApiResponse.error("课目「" + str(it.get("name")) + "」占比须在(0,100]之间");
            }
            List<Map<String, Object>> children = castList(it.get("children"));
            double childSum = 0;
            for (Map<String, Object> c : children) {
                double cw = num(c.get("weight"), -1);
                if (cw <= 0 || cw > 100) {
                    return ApiResponse.error("子项「" + str(c.get("name")) + "」占比须在(0,100]之间");
                }
                childSum += cw;
            }
            if (!children.isEmpty() && Math.abs(childSum - 100) > 0.01) {
                return ApiResponse.error("课目「" + str(it.get("name")) + "」的子项占比合计须为100%(当前" + round1(childSum) + "%)");
            }
            topSum += w;
        }
        if (Math.abs(topSum - 100) > 0.01) {
            return ApiResponse.error("顶级课目占比合计须为100%(当前" + round1(topSum) + "%)");
        }
        long id = num(body.get("id"), 0) > 0 ? (long) num(body.get("id"), 0) : 0;
        if (id > 0) {
            if (jdbc.queryForList("SELECT id FROM mil_scheme WHERE id=?", id).isEmpty()) {
                return ApiResponse.error("方案不存在");
            }
            jdbc.update("UPDATE mil_scheme SET name=?, remark=?, updated_at=datetime('now','localtime') WHERE id=?",
                    name, str(body.get("remark")), id);
            jdbc.update("DELETE FROM mil_scheme_item WHERE scheme_id=?", id);
        } else {
            jdbc.update("INSERT INTO mil_scheme(name, remark) VALUES(?,?)", name, str(body.get("remark")));
            id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        }
        int sort = 0;
        for (Map<String, Object> it : items) {
            sort++;
            jdbc.update("INSERT INTO mil_scheme_item(scheme_id, parent_id, name, weight, sort) VALUES(?,0,?,?,?)",
                    id, str(it.get("name")), num(it.get("weight"), 0), sort);
            Long parentId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
            int csort = 0;
            for (Map<String, Object> c : castList(it.get("children"))) {
                csort++;
                jdbc.update("INSERT INTO mil_scheme_item(scheme_id, parent_id, name, weight, sort) VALUES(?,?,?,?,?)",
                        id, parentId, str(c.get("name")), num(c.get("weight"), 0), csort);
            }
        }
        return ApiResponse.ok(schemeOne(id));
    }

    @DeleteMapping("/schemes/{id:\\d+}")
    public ApiResponse deleteScheme(@PathVariable Long id) {
        jdbc.update("DELETE FROM mil_scheme_item WHERE scheme_id=?", id);
        jdbc.update("DELETE FROM mil_scheme WHERE id=?", id);
        return ApiResponse.ok();
    }

    private Map<String, Object> schemeOne(long id) {
        Map<String, Object> node = new java.util.LinkedHashMap<String, Object>();
        Map<String, Object> s = jdbc.queryForMap("SELECT * FROM mil_scheme WHERE id=?", id);
        node.put("id", s.get("id"));
        node.put("name", s.get("name"));
        node.put("remark", s.get("remark"));
        node.put("items", schemeItems(id));
        return node;
    }

    @GetMapping
    public ApiResponse query(@RequestParam(required = false) String personnelName,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String item,
                             @RequestParam(required = false) String grade,
                             @RequestParam(required = false) String startDate,
                             @RequestParam(required = false) String endDate,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        if (page < 1) { page = 1; }
        if (size < 1 || size > 200) { size = 20; }
        List<MilitaryAssessment> list = dao.query(personnelName, category, item, grade, startDate, endDate, page, size);
        long total = dao.count(personnelName, category, item, grade, startDate, endDate);
        return ApiResponse.ok(new PageResult<MilitaryAssessment>(total, list));
    }

    @PostMapping
    public ApiResponse create(@RequestBody MilitaryAssessment m) {
        computeByDetail(m);
        validate(m);
        long id = dao.insert(m);
        return ApiResponse.ok(dao.findById(id));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse update(@PathVariable Long id, @RequestBody MilitaryAssessment m) {
        if (dao.findById(id) == null) {
            return ApiResponse.error("记录不存在");
        }
        computeByDetail(m);
        validate(m);
        m.setId(id);
        dao.update(m);
        return ApiResponse.ok(dao.findById(id));
    }

    /** 按课目明细自动计算:百分制总分 = Σ(成绩×占总分百分比/100);分数/评定/方案名/明细摘要自动填充 */
    @SuppressWarnings("unchecked")
    private void computeByDetail(MilitaryAssessment m) {
        String detail = m.getDetail();
        if (detail == null || detail.trim().isEmpty() || m.getSchemeId() == null || m.getSchemeId() <= 0) {
            return; // 旧式手工记录,不做改写
        }
        List<Map<String, Object>> rows;
        try {
            rows = om.readValue(detail, List.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("课目明细格式错误");
        }
        double total = 0;
        StringBuilder scoreText = new StringBuilder();
        for (Map<String, Object> r : rows) {
            double weight = num(r.get("weight"), -1);
            double score = num(r.get("score"), -1);
            if (weight <= 0 || weight > 100) {
                throw new IllegalArgumentException("课目「" + str(r.get("name")) + "」占比须在(0,100]之间");
            }
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("课目「" + str(r.get("name")) + "」成绩须在0-100之间");
            }
            total += score * weight / 100.0;
            if (scoreText.length() > 0) {
                scoreText.append("·");
            }
            scoreText.append(str(r.get("name"))).append(trimNum(score));
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("请填写课目成绩明细");
        }
        m.setPoints(round1(total));
        m.setGrade(total >= 90 ? "优秀" : total >= 80 ? "良好" : total >= 60 ? "及格" : "不及格");
        m.setScore(scoreText.toString());
        m.setCategory("综合考核");
        List<Map<String, Object>> scheme = jdbc.queryForList("SELECT name FROM mil_scheme WHERE id=?", m.getSchemeId());
        if (!scheme.isEmpty()) {
            m.setItem(String.valueOf(scheme.get(0).get("name")));
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String trimNum(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(round1(v));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static double num(Object o, double def) {
        if (o == null || String.valueOf(o).trim().isEmpty()) {
            return def;
        }
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        if (o instanceof List) {
            return (List<Map<String, Object>>) o;
        }
        return new java.util.ArrayList<Map<String, Object>>();
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse delete(@PathVariable Long id) {
        dao.delete(id);
        return ApiResponse.ok();
    }

    private void validate(MilitaryAssessment m) {
        if (!dao.personExists(m.getPersonnelId())) {
            throw new IllegalArgumentException("请选择考核人员");
        }
        if (m.getItem() == null || m.getItem().trim().isEmpty()) {
            throw new IllegalArgumentException("请填写考核项目");
        }
        if (m.getTestDate() == null || m.getTestDate().trim().isEmpty()) {
            throw new IllegalArgumentException("请填写考核日期");
        }
        if (!Dates.validOrEmpty(m.getTestDate())) {
            throw new IllegalArgumentException("考核日期格式应为 yyyy-MM-dd");
        }
        if (m.getPoints() != null && (m.getPoints() < 0 || m.getPoints() > 100)) {
            throw new IllegalArgumentException("分数应在 0~100 之间");
        }
    }
}
