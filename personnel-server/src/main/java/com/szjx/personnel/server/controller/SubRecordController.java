package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.dao.SubRecordDao;
import com.szjx.personnel.server.model.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 人员子记录接口(任职经历 / 奖惩情况),路径段决定表:
 *   /api/sub/{kind}   kind = position-history | reward-punish
 */
@RestController
@RequestMapping("/api/sub")
public class SubRecordController {

    private static final Map<String, String> TABLES = new HashMap<String, String>();
    private static final Map<String, String> ORDER = new HashMap<String, String>();
    private static final Map<String, String[]> COLS = new HashMap<String, String[]>();

    static {
        TABLES.put("position-history", "position_history");
        ORDER.put("position-history", "start_date");
        COLS.put("position-history", new String[]{"start_date", "end_date", "place", "post", "remark"});

        TABLES.put("reward-punish", "reward_punish");
        ORDER.put("reward-punish", "occur_date");

        TABLES.put("children", "child_info");
                COLS.put("reward-punish", new String[]{"kind", "occur_date", "reason", "content", "remark"});

        ORDER.put("children", "birth_date");
        COLS.put("children", new String[]{"name", "gender", "birth_date", "remark"});
    }

    private final SubRecordDao dao;

    public SubRecordController(SubRecordDao dao) {
        this.dao = dao;
    }

    @GetMapping("/{kind}")
    public ApiResponse list(@PathVariable String kind, @RequestParam Long personnelId) {
        String table = table(kind);
        return ApiResponse.ok(dao.listByPerson(table, personnelId, ORDER.get(kind)));
    }

    @PostMapping("/{kind}")
    public ApiResponse create(@PathVariable String kind, @RequestBody Map<String, Object> body) {
        String table = table(kind);
        Long personnelId = asLong(body.get("personnelId"));
        if (personnelId == null || !dao.personExists(personnelId)) {
            return ApiResponse.error("所属人员不存在,请先保存人员");
        }
        Map<String, String> fields = fields(kind, body);
        String err = validate(kind, fields, true);
        if (err != null) {
            return ApiResponse.error(err);
        }
        long id = dao.insert(table, personnelId, fields, Arrays.asList(COLS.get(kind)));
        return ApiResponse.ok(dao.findById(table, id));
    }

    @PutMapping("/{kind}/{id}")
    public ApiResponse update(@PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String table = table(kind);
        if (dao.findById(table, id) == null) {
            return ApiResponse.error("记录不存在");
        }
        Map<String, String> fields = fields(kind, body);
        String err = validate(kind, fields, false);
        if (err != null) {
            return ApiResponse.error(err);
        }
        dao.update(table, id, fields, Arrays.asList(COLS.get(kind)));
        return ApiResponse.ok(dao.findById(table, id));
    }

    @DeleteMapping("/{kind}/{id}")
    public ApiResponse delete(@PathVariable String kind, @PathVariable Long id) {
        dao.delete(table(kind), id);
        return ApiResponse.ok();
    }

    /* ================= 内部 ================= */

    private String table(String kind) {
        String t = TABLES.get(kind);
        if (t == null) {
            throw new IllegalArgumentException("未知的子记录类型: " + kind);
        }
        return t;
    }

    private Map<String, String> fields(String kind, Map<String, Object> body) {
        Map<String, String> out = new HashMap<String, String>();
        for (String c : COLS.get(kind)) {
            Object v = body.get(c);
            out.put(c, v == null ? "" : String.valueOf(v).trim());
        }
        return out;
    }

    private String validate(String kind, Map<String, String> f, boolean creating) {
        if ("position-history".equals(kind)) {
            if (f.get("start_date").isEmpty()) {
                return "请填写开始日期";
            }
            if (f.get("place").isEmpty()) {
                return "请填写任职地点";
            }
            if (f.get("post").isEmpty()) {
                return "请填写担任职务";
            }
        } else if ("children".equals(kind)) {
            if (f.get("name").isEmpty()) {
                return "请填写子女姓名";
            }
            if (!f.get("gender").isEmpty() && !"男".equals(f.get("gender")) && !"女".equals(f.get("gender"))) {
                return "性别只能是 男 或 女";
            }
            return null;
        } else {
            if (f.get("kind").isEmpty()) {
                return "请选择类型(奖励/处分)";
            }
            if (!"奖励".equals(f.get("kind")) && !"处分".equals(f.get("kind"))) {
                return "类型只能是 奖励 或 处分";
            }
            if (f.get("occur_date").isEmpty()) {
                return "请填写日期";
            }
            if (f.get("reason").isEmpty()) {
                return "请填写事由";
            }
            if (f.get("content").isEmpty()) {
                return "请填写表彰/处分内容";
            }
        }
        return null;
    }

    private Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
