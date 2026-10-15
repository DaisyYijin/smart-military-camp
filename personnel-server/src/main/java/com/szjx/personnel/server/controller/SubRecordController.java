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

        TABLES.put("education", "education_record");
        ORDER.put("education", "graduate_date");
        COLS.put("education", new String[]{"enroll_date", "graduate_date", "school", "major", "degree", "remark"});

        TABLES.put("reward-punish", "reward_punish");
        ORDER.put("reward-punish", "occur_date");

        TABLES.put("children", "child_info");
                COLS.put("reward-punish", new String[]{"kind", "occur_date", "reason", "content", "remark"});

        ORDER.put("children", "birth_date");
        COLS.put("children", new String[]{"name", "id_card", "gender", "birth_date", "remark"});

        TABLES.put("family", "family_member");
        TABLES.put("driver", "driver_license");
        TABLES.put("card", "personal_card");
        TABLES.put("pvehicle", "private_vehicle");
        TABLES.put("housing", "housing_info");
        TABLES.put("fitness", "fitness_record");
        ORDER.put("family", "id");
        COLS.put("family", new String[]{"relationship", "name", "id_card", "gender",
                "birth_date", "political_status", "work_info", "phone", "remark"});

        ORDER.put("driver", "id");
        COLS.put("driver", new String[]{"license_type", "license_no", "vehicle_class",
                "issue_date", "expiry_date", "issuing_auth", "license_status", "review_date", "remark"});

        ORDER.put("card", "id");
        COLS.put("card", new String[]{"card_type", "card_no", "bank_name", "branch_name", "open_date", "card_status", "remark"});

        ORDER.put("pvehicle", "id");
        COLS.put("pvehicle", new String[]{"plate_no", "brand", "color", "purchase_date", "price", "insurance_date", "vehicle_status", "owner_name", "remark"});

        ORDER.put("housing", "id");
        COLS.put("housing", new String[]{"housing_type", "address", "area", "house_nature", "has_cert", "loan_status", "purchase_date", "owner_name", "remark"});

        ORDER.put("fitness", "test_date");
        COLS.put("fitness", new String[]{"item", "score", "points", "grade", "test_date", "remark"});
    }

    private final SubRecordDao dao;
    private final com.szjx.personnel.server.service.FitnessService fitnessService;

    public SubRecordController(SubRecordDao dao, com.szjx.personnel.server.service.FitnessService fitnessService) {
        this.dao = dao;
        this.fitnessService = fitnessService;
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
        if ("fitness".equals(kind)) {
            return ApiResponse.ok(fitnessService.create(fitnessRecord(personnelId, body)));
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
        if ("fitness".equals(kind)) {
            Long pid = asLong(body.get("personnelId"));
            if (pid == null) {
                Map<String, Object> old = dao.findById(table, id);
                pid = asLong(old.get("personnel_id"));
            }
            if (pid == null) { return ApiResponse.error("所属人员缺失"); }
            com.szjx.personnel.server.model.FitnessRecord r = fitnessRecord(pid, body);
            return ApiResponse.ok(fitnessService.update(id, r));
        }
        Map<String, String> fields = fields(kind, body);
        String err = validate(kind, fields, false);
        if (err != null) {
            return ApiResponse.error(err);
        }
        dao.update(table, id, fields, Arrays.asList(COLS.get(kind)));
        return ApiResponse.ok(dao.findById(table, id));
    }

    /** 子表体能字段 → 体能记录(走统一评分) */
    private com.szjx.personnel.server.model.FitnessRecord fitnessRecord(Long personnelId, Map<String, Object> body) {
        com.szjx.personnel.server.model.FitnessRecord r = new com.szjx.personnel.server.model.FitnessRecord();
        r.setPersonnelId(personnelId);
        r.setItem(subStr(body.get("item")));
        r.setScore(subStr(body.get("score")));
        r.setTestDate(subStr(body.get("test_date")));
        r.setRemark(subStr(body.get("remark")));
        return r;
    }

    private String subStr(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
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
        } else if ("family".equals(kind)) {
            if (f.get("relationship").isEmpty()) {
                return "请选择与本人的关系";
            }
            if (f.get("name").isEmpty()) {
                return "请填写姓名";
            }
            String idc2 = f.get("id_card");
            if (!idc2.isEmpty() && !idc2.matches("[0-9]{17}[0-9Xx]") && !idc2.matches("[0-9]{15}")) {
                return "身份证号格式不正确(15 或 18 位)";
            }
            return null;
        } else if ("driver".equals(kind)) {
            if (f.get("license_type").isEmpty()) { return "请选择驾照类型(军本/地方本)"; }
            if (f.get("license_no").isEmpty()) { return "请填写证号"; }
            return null;
        } else if ("card".equals(kind)) {
            if (f.get("card_type").isEmpty()) { return "请选择卡类型"; }
            if (f.get("card_no").isEmpty()) { return "请填写卡号"; }
            return null;
        } else if ("pvehicle".equals(kind)) {
            if (f.get("plate_no").isEmpty()) { return "请填写车牌号"; }
            return null;
        } else if ("housing".equals(kind)) {
            if (f.get("address").isEmpty()) { return "请填写住房地址"; }
            return null;
        } else if ("fitness".equals(kind)) {
            if (f.get("item").isEmpty()) { return "请填写考核项目"; }
            if (f.get("test_date").isEmpty()) { return "请填写考核日期"; }
            return null;
        } else if ("children".equals(kind)) {
            if (f.get("name").isEmpty()) {
                return "请填写子女姓名";
            }
            String idc = f.get("id_card");
            if (!idc.isEmpty() && !idc.matches("[0-9]{17}[0-9Xx]") && !idc.matches("[0-9]{15}")) {
                return "身份证号格式不正确(15 或 18 位)";
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
