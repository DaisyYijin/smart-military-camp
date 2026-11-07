package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.dao.OrganizationDao;
import com.szjx.personnel.server.model.ApiResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

    /** 名册 Excel 下载:标题方正小标宋/表头黑体/内容仿宋_GB2312(公文格式) */
    @GetMapping("/{id:\\d+}/roster/excel")
    public org.springframework.http.ResponseEntity<byte[]> rosterExcel(@PathVariable Long id) throws java.io.IOException {
        Map<String, Object> orgInfo = dao.findOrganization(id);
        if (orgInfo == null) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body("组织不存在".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        List<Map<String, Object>> members = dao.listMembers(id);
        List<Map<String, Object>> columns = dao.listColumns(id);
        int colCount = 3 + columns.size();

        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("名册");

        // 公文字体:标题方正小标宋简体 22pt,表头黑体 12pt,内容仿宋_GB2312 12pt
        Font titleFont = wb.createFont();
        titleFont.setFontName("方正小标宋简体");
        titleFont.setFontHeightInPoints((short) 22);
        Font headFont = wb.createFont();
        headFont.setFontName("黑体");
        headFont.setFontHeightInPoints((short) 12);
        Font bodyFont = wb.createFont();
        bodyFont.setFontName("仿宋_GB2312");
        bodyFont.setFontHeightInPoints((short) 12);
        Font subFont = wb.createFont();
        subFont.setFontName("仿宋_GB2312");
        subFont.setFontHeightInPoints((short) 11);

        CellStyle titleStyle = wb.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        CellStyle subStyle = wb.createCellStyle();
        subStyle.setFont(subFont);
        subStyle.setAlignment(HorizontalAlignment.RIGHT);
        CellStyle headStyle = wb.createCellStyle();
        headStyle.setFont(headFont);
        headStyle.setAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        thinBorder(headStyle);
        CellStyle bodyStyle = wb.createCellStyle();
        bodyStyle.setFont(bodyFont);
        bodyStyle.setAlignment(HorizontalAlignment.CENTER);
        bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        thinBorder(bodyStyle);

        // 标题行(合并跨全部列)
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(38);
        Cell tc = titleRow.createCell(0);
        tc.setCellValue(String.valueOf(orgInfo.get("name")) + "名册");
        tc.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(0, colCount - 1)));

        // 副标题:共 X 人 · 下载日期
        Row subRow = sheet.createRow(1);
        Cell sc = subRow.createCell(0);
        sc.setCellValue("共 " + members.size() + " 人 · " + java.time.LocalDate.now());
        sc.setCellStyle(subStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, Math.max(0, colCount - 1)));

        // 表头行
        Row head = sheet.createRow(2);
        headRow(head, headStyle, columns);

        // 数据行:人员字段关联列自动取档案值,手动列取 extra JSON
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        int rowIdx = 3;
        for (Map<String, Object> m : members) {
            Row row = sheet.createRow(rowIdx++);
            row.setHeightInPoints(22);
            Cell c0 = row.createCell(0);
            c0.setCellValue(rowIdx - 3);
            c0.setCellStyle(bodyStyle);
            Cell c1 = row.createCell(1);
            c1.setCellValue(String.valueOf(m.get("personnelName") == null ? "" : m.get("personnelName")));
            c1.setCellStyle(bodyStyle);
            Cell c2 = row.createCell(2);
            c2.setCellValue(String.valueOf(m.get("idCard") == null ? "" : m.get("idCard")));
            c2.setCellStyle(bodyStyle);
            Map<String, Object> extra = java.util.Collections.emptyMap();
            try {
                String raw = String.valueOf(m.get("extra"));
                if (raw != null && !"null".equals(raw) && !raw.isEmpty()) {
                    extra = mapper.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                }
            } catch (Exception ignore) { /* 解析失败按空处理 */ }
            for (int ci = 0; ci < columns.size(); ci++) {
                Map<String, Object> col = columns.get(ci);
                Object val;
                String pf = String.valueOf(col.get("personnelField") == null ? "" : col.get("personnelField"));
                if (pf != null && !pf.isEmpty() && !"null".equals(pf)) {
                    val = m.get(pf);
                } else {
                    val = extra.get(String.valueOf(col.get("columnKey")));
                }
                Cell cell = row.createCell(3 + ci);
                cell.setCellValue(val == null ? "" : String.valueOf(val));
                cell.setCellStyle(bodyStyle);
            }
        }

        // 列宽:序号/姓名/身份证固定,自定义列按表头名估算
        sheet.setColumnWidth(0, 6 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 24 * 256);
        for (int ci = 0; ci < columns.size(); ci++) {
            String label = String.valueOf(columns.get(ci).get("columnLabel"));
            int w = Math.max(8, label.length() * 2 + 4);
            sheet.setColumnWidth(3 + ci, w * 256);
        }

        // 打印设置:A4 横向,缩放至一页宽(列多时完整铺开)
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        String name = java.net.URLEncoder.encode(String.valueOf(orgInfo.get("name")) + "名册-"
                + java.time.LocalDate.now() + ".xlsx", "UTF-8").replace("+", "%20");
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + name)
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(out.toByteArray());
    }

    private static void headRow(Row head, CellStyle headStyle, List<Map<String, Object>> columns) {
        String[] fixed = {"序号", "姓名", "身份证号"};
        for (int i = 0; i < fixed.length; i++) {
            Cell c = head.createCell(i);
            c.setCellValue(fixed[i]);
            c.setCellStyle(headStyle);
        }
        for (int i = 0; i < columns.size(); i++) {
            Cell c = head.createCell(3 + i);
            c.setCellValue(String.valueOf(columns.get(i).get("columnLabel")));
            c.setCellStyle(headStyle);
        }
    }

    private static void thinBorder(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
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
