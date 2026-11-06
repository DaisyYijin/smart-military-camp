package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.dao.OrganizationDao;
import com.szjx.personnel.server.model.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 组织建设:党支部/团支部/风气监督员/安全员等组织与成员管理。
 * 组织可由用户自建;成员通过身份证或选人挂接;每个组织支持自定义表头。
 */
@RestController
@RequestMapping("/api/organization")
public class OrganizationController {

    private final OrganizationDao dao;

    public OrganizationController(OrganizationDao dao) {
        this.dao = dao;
    }

    /** 列出全部组织(含成员数) */
    @GetMapping
    public ApiResponse list() {
        return ApiResponse.ok(dao.listOrganizations());
    }

    /** 创建组织 */
    @PostMapping
    public ApiResponse create(@RequestBody Map<String, Object> body) {
        DataScope.requireAdmin();
        String name = str(body.get("name"));
        if (name.isEmpty()) { return ApiResponse.error("请填写组织名称"); }
        int sort = intVal(body.get("sort"), 99);
        long id = dao.insertOrganization(name, str(body.get("remark")), sort);
        return ApiResponse.ok(dao.findOrganization(id));
    }

    /** 更新组织 */
    @PutMapping("/{id:\\d+}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        DataScope.requireAdmin();
        if (dao.findOrganization(id) == null) { return ApiResponse.error("组织不存在"); }
        String name = str(body.get("name"));
        if (name.isEmpty()) { return ApiResponse.error("请填写组织名称"); }
        dao.updateOrganization(id, name, str(body.get("remark")));
        return ApiResponse.ok(dao.findOrganization(id));
    }

    /** 删除组织(级联删除成员与表头定义) */
    @DeleteMapping("/{id:\\d+}")
    public ApiResponse delete(@PathVariable Long id) {
        DataScope.requireAdmin();
        if (dao.findOrganization(id) == null) { return ApiResponse.error("组织不存在"); }
        dao.deleteOrganization(id);
        return ApiResponse.ok();
    }

    /** 组织详情:含成员列表+自定义表头 */
    @GetMapping("/{id:\\d+}")
    public ApiResponse detail(@PathVariable Long id) {
        Map<String, Object> org = dao.findOrganization(id);
        if (org == null) { return ApiResponse.error("组织不存在"); }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", org.get("id"));
        result.put("name", org.get("name"));
        result.put("remark", org.get("remark"));
        result.put("members", dao.listMembers(id));
        result.put("columns", dao.listColumns(id));
        return ApiResponse.ok(result);
    }

    /** 添加成员 */
    @PostMapping("/{orgId:\\d+}/members")
    public ApiResponse addMember(@PathVariable Long orgId, @RequestBody Map<String, Object> body) {
        if (dao.findOrganization(orgId) == null) { return ApiResponse.error("组织不存在"); }
        Long personnelId = longVal(body.get("personnelId"));
        dao.requirePerson(personnelId);
        if (personnelId <= 0) { return ApiResponse.error("请选择人员"); }
        if (!dao.personExists(personnelId)) { return ApiResponse.error("所选人员不存在"); }
        String role = str(body.get("role"));
        String extra = str(body.get("extra"));
        if (extra.isEmpty()) { extra = "{}"; }
        String remark = str(body.get("remark"));
        int sort = intVal(body.get("sort"), 99);
        long id = dao.insertMember(orgId, personnelId, role, extra, remark, sort);
        Map<String, Object> m = dao.findMember(id);
        return ApiResponse.ok(m);
    }

    /** 更新成员 */
    @PutMapping("/{orgId:\\d+}/members/{id:\\d+}")
    public ApiResponse updateMember(@PathVariable Long orgId, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (dao.findOrganization(orgId) == null) { return ApiResponse.error("组织不存在"); }
        Map<String, Object> existing = dao.findMember(id);
        if (existing == null || !Long.valueOf(orgId).equals(((Number) existing.get("organization_id")).longValue())) {
            return ApiResponse.error("成员不存在");
        }
        Long personnelId = longVal(body.get("personnelId"));
        if (personnelId <= 0) { return ApiResponse.error("请选择人员"); }
        if (!dao.personExists(personnelId)) { return ApiResponse.error("所选人员不存在"); }
        String role = str(body.get("role"));
        String extra = str(body.get("extra"));
        if (extra.isEmpty()) { extra = "{}"; }
        String remark = str(body.get("remark"));
        dao.updateMember(id, personnelId, role, extra, remark);
        return ApiResponse.ok(dao.findMember(id));
    }

    /** 删除成员 */
    @DeleteMapping("/{orgId:\\d+}/members/{id:\\d+}")
    public ApiResponse deleteMember(@PathVariable Long orgId, @PathVariable Long id) {
        Map<String, Object> existing = dao.findMember(id);
        if (existing == null || !Long.valueOf(orgId).equals(((Number) existing.get("organization_id")).longValue())) {
            return ApiResponse.error("成员不存在");
        }
        dao.deleteMember(id);
        return ApiResponse.ok();
    }

    /** 更新自定义表头 */
    @PutMapping("/{orgId:\\d+}/columns")
    public ApiResponse updateColumns(@PathVariable Long orgId, @RequestBody Map<String, Object> body) {
        DataScope.requireAdmin();
        if (dao.findOrganization(orgId) == null) { return ApiResponse.error("组织不存在"); }
        Object cols = body.get("columns");
        List<Map<String, Object>> columnList = new ArrayList<Map<String, Object>>();
        if (cols instanceof List) {
            for (Object o : (List<?>) cols) {
                if (o instanceof Map) {
                    columnList.add((Map<String, Object>) o);
                }
            }
        }
        dao.replaceColumns(orgId, columnList);
        return ApiResponse.ok(dao.listColumns(orgId));
    }

    /** 成员拖拽排序:body.ids 为该组织下完整顺序 */
    @PutMapping("/{orgId:\\d+}/members/reorder")
    public ApiResponse reorderMembers(@PathVariable Long orgId, @RequestBody Map<String, Object> body) {
        if (dao.findOrganization(orgId) == null) { return ApiResponse.error("组织不存在"); }
        List<Long> ids = new ArrayList<Long>();
        Object raw = body.get("ids");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                try { ids.add(Long.parseLong(String.valueOf(o).trim())); }
                catch (NumberFormatException ignore) { /* skip */ }
            }
        }
        if (ids.size() < 2) { return ApiResponse.error("排序信息不能为空"); }
        dao.reorderMembers(orgId, ids);
        return ApiResponse.ok();
    }

    /* ===== 工具 ===== */

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Long longVal(Object v) {
        if (v == null || String.valueOf(v).trim().isEmpty()) { return 0L; }
        try { return Long.parseLong(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return 0L; }
    }

    private static int intVal(Object v, int def) {
        if (v == null || String.valueOf(v).trim().isEmpty()) { return def; }
        try { return Integer.parseInt(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return def; }
    }
}
