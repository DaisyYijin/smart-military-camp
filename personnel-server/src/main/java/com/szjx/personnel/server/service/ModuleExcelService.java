package com.szjx.personnel.server.service;

import com.szjx.personnel.server.util.ExcelUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用模块 Excel 导入导出。
 *
 * 导出:仅导出用户勾选的记录(ids);人员模块沿用 ExcelUtils 的完整字段模板。
 * 导入:第一行为中文列头,按各模块的业务键存在则更新、不存在则新增;
 *       含"人员姓名"的列自动解析为对应人员 ID,解析失败计为该行失败。
 */
@Service
public class ModuleExcelService {

    /** 列定义:header=Excel列头 key=返回字段/入库字段 type=text|number|int|ref-personnel;computed=服务端自动计算(不进模板) */
    private static class Col {
        final String header;
        final String key;
        final String type;
        final boolean computed;

        Col(String header, String key, String type) {
            this(header, key, type, false);
        }

        Col(String header, String key, String type, boolean computed) {
            this.header = header;
            this.key = key;
            this.type = type;
            this.computed = computed;
        }
    }

    private static class ModuleDef {
        final String key;
        final String name;
        final String table;
        final String sheet;
        final List<Col> cols;
        /** 导入时的业务键列(存在则更新);空 = 永远新增 */
        final String importKey;

        ModuleDef(String key, String name, String table, String sheet, List<Col> cols, String importKey) {
            this.key = key;
            this.name = name;
            this.table = table;
            this.sheet = sheet;
            this.cols = cols;
            this.importKey = importKey;
        }
    }

    private static final Map<String, ModuleDef> MODULES = new LinkedHashMap<String, ModuleDef>();

    /** 字典列映射:模块 → (列key → 字典类型)。导入时严格校验:值必须已在字典中,否则该行拒入 */
    private static final Map<String, Map<String, String>> DICT_COLS = new java.util.HashMap<String, Map<String, String>>();
    static {
        Map<String, String> m = new java.util.HashMap<String, String>();
        m.put("brand", "vehicle_brand"); m.put("type", "vehicle_type");
        m.put("status", "vehicle_status"); m.put("fuelType", "vehicle_fuel");
        DICT_COLS.put("vehicle", m);
        m = new java.util.HashMap<String, String>();
        m.put("category", "material_category"); m.put("location", "material_location");
        DICT_COLS.put("material", m);
        m = new java.util.HashMap<String, String>();
        m.put("category", "equipment_category"); m.put("status", "equipment_status");
        DICT_COLS.put("equipment", m);
        m = new java.util.HashMap<String, String>();
        m.put("leaveType", "leave_type"); m.put("holidayName", "holiday_type");
        DICT_COLS.put("leave", m);
        m = new java.util.HashMap<String, String>();
        m.put("deviceType", "device_type"); m.put("brand", "phone_brand"); m.put("status", "device_status");
        DICT_COLS.put("phone", m);
    }

    /** 预载各字典类型的启用项(导入严格校验用) */
    private Map<String, java.util.Set<String>> loadDictSets(java.util.Collection<String> types) {
        Map<String, java.util.Set<String>> sets = new java.util.HashMap<String, java.util.Set<String>>();
        for (String t : types) {
            java.util.Set<String> s = new java.util.HashSet<String>();
            for (Map<String, Object> row : jdbc.queryForList(
                    "SELECT dict_label FROM sys_dict WHERE dict_type=? AND enabled=1", t)) {
                String lb = String.valueOf(row.get("dict_label")).trim();
                if (!lb.isEmpty()) { s.add(lb); }
            }
            sets.put(t, s);
        }
        return sets;
    }

    static {
        MODULES.put("equipment", new ModuleDef("equipment", "装备", "equipment", "装备",
                Arrays.asList(
                        new Col("装备名称", "name", "text"),
                        new Col("装备编号", "code", "text"),
                        new Col("类别", "category", "text"),
                        new Col("型号", "model", "text"),
                        new Col("状态", "status", "text"),
                        new Col("保管人", "keeperId", "ref-personnel"),
                        new Col("存放位置", "location", "text"),
                        new Col("购置日期", "buyDate", "text"),
                        new Col("装备时间", "equipDate", "text"),
                        new Col("备注", "remark", "text")),
                "code"));
        MODULES.put("material", new ModuleDef("material", "物资", "material", "物资",
                Arrays.asList(
                        new Col("物资名称", "name", "text"),
                        new Col("分类", "category", "text"),
                        new Col("规格", "spec", "text"),
                        new Col("单位", "unit", "text"),
                        new Col("数量", "quantity", "number"),
                        new Col("库存预警下限", "minQuantity", "number"),
                        new Col("责任人", "keeperId", "ref-personnel"),
                        new Col("存放位置", "location", "text"),
                        new Col("详细存放点", "detailLocation", "text"),
                        new Col("备注", "remark", "text")),
                "name"));
        MODULES.put("vehicle", new ModuleDef("vehicle", "车辆", "vehicle", "车辆",
                Arrays.asList(
                        new Col("车牌号", "plateNo", "text"),
                        new Col("车架号", "vin", "text"),
                        new Col("发动机号", "engineNo", "text"),
                        new Col("品牌", "brand", "text"),
                        new Col("型号", "model", "text"),
                        new Col("车型", "type", "text"),
                        new Col("车身颜色", "color", "text"),
                        new Col("燃料类型", "fuelType", "text"),
                        new Col("状态", "status", "text"),
                        new Col("驾驶员", "driverId", "ref-personnel"),
                        new Col("核定人数", "seats", "int"),
                        new Col("里程(公里)", "mileage", "number"),
                        new Col("购置日期", "purchaseDate", "text"),
                        new Col("装备时间", "equipDate", "text"),
                        new Col("保险到期日", "insuranceExpiry", "text"),
                        new Col("年检到期日", "inspectionExpiry", "text"),
                        new Col("备注", "remark", "text")),
                "plateNo"));
        MODULES.put("leave", new ModuleDef("leave", "休假", "leave_record", "休假",
                Arrays.asList(
                        new Col("人员姓名", "personnelId", "ref-personnel"),
                        new Col("年度", "year", "text"),
                        new Col("休假类型", "leaveType", "text"),
                        new Col("开始日期", "startDate", "text"),
                        new Col("结束日期", "endDate", "text"),
                        new Col("天数", "days", "number"),
                        new Col("路途天数", "travelDays", "number"),
                        new Col("节假日加假", "holidayDays", "number"),
                        new Col("节假日名称", "holidayName", "text"),
                        new Col("事由", "reason", "text"),
                        new Col("备注", "remark", "text")),
                ""));
        MODULES.put("phone", new ModuleDef("phone", "手机设备", "phone_device", "手机设备",
                Arrays.asList(
                        new Col("姓名", "personnelId", "ref-personnel"),
                        new Col("设备类型", "deviceType", "text"),
                        new Col("品牌", "brand", "text"),
                        new Col("型号", "model", "text"),
                        new Col("序列号", "serialNo", "text"),
                        new Col("IMEI1", "imei1", "text"),
                        new Col("IMEI2", "imei2", "text"),
                        new Col("手机号", "phoneNumber", "text"),
                        new Col("购置日期", "purchaseDate", "text"),
                        new Col("状态", "status", "text"),
                        new Col("备注", "remark", "text")),
                ""));
        MODULES.put("fitness", new ModuleDef("fitness", "体能考核", "fitness_record", "体能考核",
                Arrays.asList(
                        new Col("姓名", "personName", "text"),
                        new Col("身份证号码", "idCard", "text"),
                        new Col("考核日期", "testDate", "text"),
                        new Col("考核项目", "item", "text"),
                        new Col("成绩", "score", "text"),
                        new Col("分数", "points", "number", true),
                        new Col("评定等级", "grade", "text", true)),
                ""));
    }

    private final JdbcTemplate jdbc;
    private final PersonnelService personnelService;
    private final DepartmentDaoBridge departmentDaoBridge;

    /** 部门名解析桥:避免直接依赖 DepartmentDao 造成循环,取人员导出所需 */
    @Service
    public static class DepartmentDaoBridge {
        public final com.szjx.personnel.server.dao.DepartmentDao departmentDao;

        public DepartmentDaoBridge(com.szjx.personnel.server.dao.DepartmentDao departmentDao) {
            this.departmentDao = departmentDao;
        }
    }

    public ModuleExcelService(JdbcTemplate jdbc, PersonnelService personnelService,
                              DepartmentDaoBridge departmentDaoBridge) {
        this.jdbc = jdbc;
        this.personnelService = personnelService;
        this.departmentDaoBridge = departmentDaoBridge;
    }

    public boolean supports(String moduleKey) {
        return "personnel".equals(moduleKey) || "dict".equals(moduleKey) || MODULES.containsKey(moduleKey);
    }

    public String moduleName(String moduleKey) {
        if ("personnel".equals(moduleKey)) {
            return "人员";
        }
        if ("dict".equals(moduleKey)) {
            return "数据字典";
        }
        ModuleDef def = MODULES.get(moduleKey);
        return def == null ? moduleKey : def.name;
    }

    /* ================= 导出 ================= */

    /** 通用导入模板:列头 + 一行示例 */
    public byte[] template(String moduleKey) throws Exception {
        ModuleDef def = MODULES.get(moduleKey);
        if (def == null) {
            throw new IllegalArgumentException("不支持的模块: " + moduleKey);
        }
        org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        try {
            org.apache.poi.ss.usermodel.Sheet sh = wb.createSheet(def.sheet);
            org.apache.poi.ss.usermodel.CellStyle headStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font font = wb.createFont();
            font.setBold(true);
            headStyle.setFont(font);
            java.util.List<Col> tplCols = new java.util.ArrayList<Col>();
            for (Col c : def.cols) { if (!c.computed) { tplCols.add(c); } }
            org.apache.poi.ss.usermodel.Row hr = sh.createRow(0);
            for (int i = 0; i < tplCols.size(); i++) {
                org.apache.poi.ss.usermodel.Cell c = hr.createCell(i);
                c.setCellValue(tplCols.get(i).header);
                c.setCellStyle(headStyle);
                sh.setColumnWidth(i, 16 * 256);
            }
            org.apache.poi.ss.usermodel.Row er = sh.createRow(1);
            for (int i = 0; i < tplCols.size(); i++) {
                Col col = tplCols.get(i);
                String v;
                if ("ref-personnel".equals(col.type)) {
                    v = "张三(填人员姓名)";
                } else if ("number".equals(col.type)) {
                    v = "0";
                } else if ("int".equals(col.type)) {
                    v = "0";
                } else if (col.key != null && (col.key.toLowerCase().contains("date") || col.key.toLowerCase().contains("expiry"))) {
                    v = "2026-01-31";
                } else {
                    v = "示例" + col.header;
                }
                er.createCell(i).setCellValue(v);
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } finally {
            wb.close();
        }
    }

    public byte[] export(String moduleKey, List<Long> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请先勾选要导出的数据");
        }
        if ("personnel".equals(moduleKey)) {
            List<com.szjx.personnel.server.model.Personnel> all =
                    personnelService.query(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 1, 100000);
            List<com.szjx.personnel.server.model.Personnel> selected =
                    new ArrayList<com.szjx.personnel.server.model.Personnel>();
            for (com.szjx.personnel.server.model.Personnel p : all) {
                if (ids.contains(p.getId())) {
                    selected.add(p);
                }
            }
            final Map<Long, String> deptNames = new HashMap<Long, String>();
            departmentDaoBridge.departmentDao.findAll()
                    .forEach(d -> deptNames.put(d.getId(), d.getName()));
            return ExcelUtils.exportXlsx(selected, id -> {
                String n = deptNames.get(id);
                return n == null ? "" : n;
            });
        }
        if ("dict".equals(moduleKey)) {
            return exportDict();
        }
        ModuleDef def = MODULES.get(moduleKey);
        if (def == null) {
            throw new IllegalArgumentException("不支持的模块: " + moduleKey);
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            placeholders.append(i > 0 ? ",?" : "?");
        }
        String inSql = "SELECT * FROM " + def.table + " WHERE id IN ("
                + placeholders + ") ORDER BY id";
        List<Map<String, Object>> rows = jdbc.queryForList(inSql, ids.toArray());

        Map<Long, String> personNames = personNameMap();
        Map<Long, java.util.Map<String, String>> personInfo = new java.util.HashMap<Long, java.util.Map<String, String>>();
        boolean needPersonInfo = "fitness".equals(moduleKey);
        if (needPersonInfo) {
            for (Map<String, Object> pr : jdbc.queryForList("SELECT id, name, id_card FROM personnel")) {
                java.util.Map<String, String> info = new java.util.HashMap<String, String>();
                info.put("name", String.valueOf(pr.get("name") == null ? "" : pr.get("name")));
                info.put("idCard", String.valueOf(pr.get("id_card") == null ? "" : pr.get("id_card")));
                personInfo.put(((Number) pr.get("id")).longValue(), info);
            }
        }
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet(def.sheet);
        CellStyle headStyle = headerStyle(wb);
        Row head = sheet.createRow(0);
        for (int i = 0; i < def.cols.size(); i++) {
            Cell c = head.createCell(i);
            c.setCellValue(def.cols.get(i).header);
            c.setCellStyle(headStyle);
            sheet.setColumnWidth(i, 16 * 256);
        }
        DataFormatter fmt = new DataFormatter();
        int r = 1;
        for (Map<String, Object> row : rows) {
            Row xr = sheet.createRow(r++);
            for (int i = 0; i < def.cols.size(); i++) {
                Col col = def.cols.get(i);
                Object v = row.get(snake(col.key));
                if (v == null && needPersonInfo
                        && ("personName".equals(col.key) || "idCard".equals(col.key))) {
                    Object pidv = row.get("personnel_id");
                    if (pidv != null) {
                        java.util.Map<String, String> info = personInfo.get(((Number) pidv).longValue());
                        v = info == null ? "" : ("personName".equals(col.key) ? info.get("name") : info.get("idCard"));
                    }
                }
                if ("ref-personnel".equals(col.type)) {
                    Long pid = v == null ? null : ((Number) v).longValue();
                    xr.createCell(i).setCellValue(pid == null || pid == 0 ? ""
                            : personNames.containsKey(pid) ? personNames.get(pid) : "");
                } else if (v == null) {
                    xr.createCell(i).setCellValue("");
                } else if (v instanceof Number) {
                    xr.createCell(i).setCellValue(((Number) v).doubleValue());
                } else {
                    xr.createCell(i).setCellValue(String.valueOf(v));
                }
            }
        }
        return workbookBytes(wb);
    }

    private byte[] exportDict() throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT dict_type, dict_label, sort, enabled, remark FROM sys_dict ORDER BY dict_type, sort");
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("数据字典");
        Row head = sheet.createRow(0);
        String[] headers = {"字典类型", "选项内容", "排序", "状态", "备注"};
        CellStyle headStyle = headerStyle(wb);
        for (int i = 0; i < headers.length; i++) {
            Cell c = head.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headStyle);
            sheet.setColumnWidth(i, 16 * 256);
        }
        int r = 1;
        for (Map<String, Object> row : rows) {
            Row xr = sheet.createRow(r++);
            xr.createCell(0).setCellValue(str(row.get("dict_type")));
            xr.createCell(1).setCellValue(str(row.get("dict_label")));
            xr.createCell(2).setCellValue(row.get("sort") == null ? 0 : ((Number) row.get("sort")).intValue());
            xr.createCell(3).setCellValue(Number.class.cast(row.get("enabled")) != null
                    && ((Number) row.get("enabled")).intValue() == 1 ? "启用" : "停用");
            xr.createCell(4).setCellValue(str(row.get("remark")));
        }
        return workbookBytes(wb);
    }

    /* ================= 导入 ================= */

    @SuppressWarnings("unchecked")
    public Map<String, Object> importExcel(String moduleKey, InputStream in) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        int added = 0, updated = 0, failed = 0;
        List<String> errors = new ArrayList<String>();
        try {
            if ("personnel".equals(moduleKey)) {
                List<com.szjx.personnel.server.model.Personnel> rows;
                try {
                    rows = ExcelUtils.importXlsx(in);
                } catch (Exception e) {
                    throw new IllegalArgumentException("文件解析失败,请使用系统导出的模板(.xlsx)");
                }
                int[] r = personnelService.importRows(rows);
                result.put("added", r[0]);
                result.put("updated", r[1]);
                result.put("failed", r[2]);
                return result;
            }

            Workbook wb = new XSSFWorkbook(in);
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row headRow = sheet.getRow(0);
            if (headRow == null) {
                throw new IllegalArgumentException("文件没有列头(第一行应为中文列头,建议用系统导出的模板)");
            }
            Map<String, Integer> headerIndex = new HashMap<String, Integer>();
            for (int i = 0; i < headRow.getLastCellNum(); i++) {
                String h = fmt.formatCellValue(headRow.getCell(i)).trim();
                if (!h.isEmpty()) {
                    headerIndex.put(h, i);
                }
            }

            if ("dict".equals(moduleKey)) {
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    String type = cell(row, headerIndex, "字典类型", fmt);
                    String label = cell(row, headerIndex, "选项内容", fmt);
                    if (type.isEmpty() || label.isEmpty()) {
                        failed++;
                        addError(errors, r, "字典类型/选项内容为空");
                        continue;
                    }
                    String sortStr = cell(row, headerIndex, "排序", fmt);
                    String enabledStr = cell(row, headerIndex, "状态", fmt);
                    String remark = cell(row, headerIndex, "备注", fmt);
                    Integer existing = jdbc.queryForObject(
                            "SELECT id FROM sys_dict WHERE dict_type=? AND dict_label=?",
                            Integer.class, type, label);
                    int sort = sortStr.isEmpty() ? 0 : (int) Double.parseDouble(sortStr);
                    int enabled = "停用".equals(enabledStr) ? 0 : 1;
                    if (existing != null && existing > 0) {
                        jdbc.update("UPDATE sys_dict SET sort=?, enabled=?, remark=?, "
                                        + "updated_at=datetime('now','localtime') WHERE id=?",
                                sort, enabled, remark, existing);
                        updated++;
                    } else {
                        jdbc.update("INSERT INTO sys_dict(dict_type, dict_label, sort, enabled, remark) "
                                        + "VALUES(?,?,?,?,?)",
                                type, label, sort, enabled, remark);
                        added++;
                    }
                }
            } else {
                ModuleDef def = MODULES.get(moduleKey);
                if (def == null) {
                    throw new IllegalArgumentException("不支持的模块: " + moduleKey);
                }
                Map<String, Long> personIds = personIdMap();
                Map<String, Long> cardMap = new java.util.HashMap<String, Long>();
                for (Map<String, Object> pr : jdbc.queryForList(
                        "SELECT id, id_card FROM personnel WHERE id_card IS NOT NULL AND id_card != ''")) {
                    cardMap.put(String.valueOf(pr.get("id_card")).trim().toUpperCase(),
                            ((Number) pr.get("id")).longValue());
                }
                // 字典下拉项预载:导入值必须命中,防止手填错别字批量入库
                Map<String, String> dm = DICT_COLS.get(moduleKey);
                Map<String, java.util.Set<String>> dictSets = dm == null
                        ? java.util.Collections.emptyMap()
                        : loadDictSets(dm.values());
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    Map<String, Object> vals = new LinkedHashMap<String, Object>();
                    boolean rowOk = true;
                    for (Col col : def.cols) {
                        String raw = cell(row, headerIndex, col.header, fmt);
                        if ("ref-personnel".equals(col.type)) {
                            if (raw.isEmpty()) {
                                vals.put(col.key, 0L);
                            } else if (personIds.containsKey(raw)) {
                                vals.put(col.key, personIds.get(raw));
                            } else {
                                failed++;
                                addError(errors, r, "人员不存在:" + raw);
                                rowOk = false;
                                break;
                            }
                        } else if ("number".equals(col.type)) {
                            vals.put(col.key, raw.isEmpty() ? null : Double.valueOf(raw));
                        } else if ("int".equals(col.type)) {
                            vals.put(col.key, raw.isEmpty() ? null : (int) Double.parseDouble(raw));
                        } else if (dm != null && dm.containsKey(col.key) && !raw.isEmpty()
                                && !dictSets.get(dm.get(col.key)).contains(raw)) {
                            failed++;
                            addError(errors, r, col.header + "「" + raw + "」不在下拉选项中,请先在数据字典中维护后再导入");
                            rowOk = false;
                            break;
                        } else {
                            vals.put(col.key, raw);
                        }
                    }
                    if (!rowOk) {
                        continue;
                    }
                    if ("fitness".equals(moduleKey)) {
                        String err = prepareFitnessRow(vals, cardMap, personIds);
                        if (err != null) {
                            failed++;
                            addError(errors, r, err);
                            continue;
                        }
                    }
                    // 必填与业务键
                    String nameVal = String.valueOf(vals.get(def.cols.get(0).key));
                    if (nameVal == null || nameVal.isEmpty() || "null".equals(nameVal)) {
                        failed++;
                        addError(errors, r, def.cols.get(0).header + "为空");
                        continue;
                    }
                    upsert(def, vals);
                    if (rowUpdated) {
                        updated++;
                    } else {
                        added++;
                    }
                }
            }
            wb.close();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("文件解析失败:" + e.getMessage()
                    + "(请使用系统导出的 .xlsx 模板)");
        }
        result.put("added", added);
        result.put("updated", updated);
        result.put("failed", failed);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return result;
    }

    private boolean rowUpdated;

    /* ================= 体能导入:按体能标准矩阵与评定标准自动计算 ================= */

    /** 体能行准备:匹配人员 → 校验项目与体能标准一致 → 算分数 → 评定;返回错误信息或 null */
    private String prepareFitnessRow(Map<String, Object> vals, Map<String, Long> cardMap, Map<String, Long> nameMap) {
        String card = str(vals.get("idCard"));
        String name = str(vals.get("personName"));
        Long pid = null;
        if (!card.isEmpty()) {
            pid = cardMap.get(card.trim().toUpperCase());
        }
        if (pid == null && !name.isEmpty()) {
            pid = nameMap.get(name.trim());
        }
        if (pid == null || pid == 0) {
            return "人员不存在(按身份证号/姓名均未匹配)";
        }
        String item = str(vals.get("item"));
        if (item.isEmpty()) {
            return "考核项目为空";
        }
        // BMI:按性别合格区间评分(身高体重指数),不走评分矩阵
        // 体重标准(身高×年龄交叉表):成绩填体重kg,按该人身高行+年龄列查合格区间
        if ("BMI".equals(item) || "体重".equals(item)) {
            Double weight = null;
            java.util.regex.Matcher wm = java.util.regex.Pattern.compile("([0-9]+(?:[.][0-9]+)?)").matcher(str(vals.get("score")));
            if (wm.find()) { weight = Double.parseDouble(wm.group(1)); }
            if (weight == null || weight < 25 || weight > 200) {
                return "体重成绩应为25~200之间的数值(kg)";
            }
            List<Map<String, Object>> g = jdbc.queryForList("SELECT gender, height, birthday FROM personnel WHERE id=?", pid);
            String gender = "";
            Double heightCm = null;
            Integer age = null;
            if (!g.isEmpty()) {
                Map<String, Object> pp = g.get(0);
                if (pp.get("gender") != null) { gender = String.valueOf(pp.get("gender")); }
                if (pp.get("height") != null) { heightCm = ((Number) pp.get("height")).doubleValue(); }
                age = ageAt(String.valueOf(pp.get("birthday") == null ? "" : pp.get("birthday")), null);
            }
            if (heightCm == null || heightCm < 140 || heightCm > 210) {
                return "该人员未维护身高(或超出140~210cm),无法按体重标准判定";
            }
            double heightM = Math.round(heightCm) / 100.0 * 100 / 100.0;
            heightM = Math.round(heightCm) / 100.0;
            heightM = Math.round(heightCm * 1.0) / 100.0;
            heightM = Math.floor(heightCm + 0.5) / 100.0;
            double[] range = weightRange(gender, heightCm, age);
            if (range == null) {
                return "体重标准中没有身高 " + String.format("%.2f", heightM) + "m 的行,请先在[体能标准-BMI标准]中维护";
            }
            boolean pass = weight >= range[0] && weight <= range[1];
            vals.put("personnelId", pid);
            vals.put("points", pass ? 100.0 : 0.0);
            vals.put("grade", pass ? "及格" : "不及格");
            return null;
        }
        java.util.Set<String> items = new java.util.HashSet<String>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT DISTINCT item FROM fitness_matrix")) {
            items.add(String.valueOf(r.get("item")));
        }
        if (!items.contains(item)) {
            return "考核项目「" + item + "」与体能标准不一致,请先在[体能标准]中维护该项目";
        }
        String testDate = str(vals.get("testDate"));
        String birthday = birthdayOf(pid);
        Integer age = ageAt(birthday, testDate);
        Double pts = calcFitnessPoints(item, str(vals.get("score")), age);
        if (pts == null) {
            return "无法按体能标准计算分数(检查成绩格式,及该项目年龄段是否覆盖考核者年龄)";
        }
        vals.put("personnelId", pid);
        vals.put("points", pts);
        vals.put("grade", gradeForPoints(pts));
        // idCard/personName 保留在 vals(必填检查用),入库时由 UPSERT_SKIP 排除
        return null;
    }

    /**
     * 体重合格区间:身高(米,四舍五入到cm)行 × 年龄段列。
     * 格式 {"ages":["24岁以下","25-29岁",...], "male":{"rows":[{"h":1.6,"w":[[47.4,66.3],[48,68],...]},...]}, "female":{...}}
     */
    private double[] weightRange(String gender, Double heightCm, Integer age) {
        boolean female = "女".equals(gender);
        try {
            String v = jdbc.queryForObject("SELECT svalue FROM sys_setting WHERE skey='bmi_standard'", String.class);
            if (v == null) { return null; }
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(v);
            com.fasterxml.jackson.databind.JsonNode side = node.get(female ? "female" : "male");
            com.fasterxml.jackson.databind.JsonNode ages = node.get("ages");
            if (side == null || ages == null || !ages.isArray() || ages.size() == 0) { return null; }
            com.fasterxml.jackson.databind.JsonNode rows = side.get("rows");
            if (rows == null || !rows.isArray()) { return null; }
            double hm = Math.round(heightCm + 0.5) / 100.0;
            com.fasterxml.jackson.databind.JsonNode hit = null;
            double bestDiff = Double.MAX_VALUE;
            for (int i = 0; i < rows.size(); i++) {
                com.fasterxml.jackson.databind.JsonNode r = rows.get(i);
                double h = r.get("h").asDouble();
                double diff = Math.abs(h - hm);
                if (diff < bestDiff - 0.0001) { bestDiff = diff; hit = r; }
            }
            if (hit == null || bestDiff > 0.011) { return null; }   // 没有这一身高行
            com.fasterxml.jackson.databind.JsonNode cells = hit.get("w");
            if (cells == null || !cells.isArray() || cells.size() != ages.size()) { return null; }
            int col = -1;
            for (int i = 0; i < ages.size(); i++) {
                if (ageInGroup(ages.get(i).asText(""), age)) { col = i; break; }
            }
            if (col < 0) { return null; }
            com.fasterxml.jackson.databind.JsonNode pair = cells.get(col);
            if (pair == null || !pair.isArray() || pair.size() != 2) { return null; }
            return new double[]{pair.get(0).asDouble(), pair.get(1).asDouble()};
        } catch (Exception e) {
            return null;
        }
    }

    /** 年龄是否落在年龄段标签内(如 "24岁以下"/"25-29岁"/"40-49岁"/"50岁以上") */
    private static boolean ageInGroup(String label, Integer age) {
        if (age == null) { return false; }
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("[0-9]+").matcher(label);
        List<Integer> nums = new ArrayList<Integer>();
        while (mm.find()) { nums.add(Integer.parseInt(mm.group())); }
        if (label.contains("以下") || label.contains("≤") || label.contains("<=")) {
            return nums.isEmpty() || age <= nums.get(0);
        }
        if (label.contains("以上") || label.contains("≥") || label.contains(">=")) {
            return nums.isEmpty() || age >= nums.get(0);
        }
        if (nums.size() >= 2) {
            return age >= nums.get(0) && age <= nums.get(1);
        }
        return false;
    }

    private String birthdayOf(Long pid) {
        List<Map<String, Object>> r = jdbc.queryForList("SELECT birthday FROM personnel WHERE id=?", pid);
        return r.isEmpty() || r.get(0).get("birthday") == null ? "" : String.valueOf(r.get(0).get("birthday"));
    }

    private static Integer ageAt(String birthday, String date) {
        if (birthday == null || birthday.length() < 10) { return null; }
        try {
            java.time.LocalDate b = java.time.LocalDate.of(Integer.parseInt(birthday.substring(0, 4)),
                    Integer.parseInt(birthday.substring(5, 7)), Integer.parseInt(birthday.substring(8, 10)));
            java.time.LocalDate d = java.time.LocalDate.now();
            if (date != null && date.length() >= 10) {
                d = java.time.LocalDate.of(Integer.parseInt(date.substring(0, 4)),
                        Integer.parseInt(date.substring(5, 7)), Integer.parseInt(date.substring(8, 10)));
            }
            return java.time.Period.between(b, d).getYears();
        } catch (Exception e) {
            return null;
        }
    }

    /** 年龄 → 年龄段标签(与前端 matchAgeGroup 一致) */
    private static String matchAgeGroupJava(java.util.List<String> groups, int age) {
        for (String label : groups) {
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("\\d+").matcher(label);
            if (!mm.find()) { continue; }
            int a = Integer.parseInt(mm.group());
            int b = a;
            if (mm.find()) { b = Integer.parseInt(mm.group()); }
            boolean hit;
            if (label.contains("以下")) { hit = age <= a; }
            else if (label.contains("以上")) { hit = age >= a; }
            else { hit = age >= a && age <= b; }
            if (hit) { return label; }
        }
        return null;
    }

    /** 按矩阵计算分数(与前端 calcFitnessGrade 一致);无法计算返回 null */
    private Double calcFitnessPoints(String item, String scoreText, Integer age) {
        List<Map<String, Object>> matrix = jdbc.queryForList(
                "SELECT age_group, score, value FROM fitness_matrix WHERE item=? ORDER BY CAST(score AS REAL) DESC", item);
        if (matrix.isEmpty() || age == null) { return null; }
        double maxV = 0;
        for (Map<String, Object> r : matrix) { maxV = Math.max(maxV, asDouble(r.get("value"))); }
        boolean isTime = maxV > 300;
        String text = scoreText == null ? "" : scoreText.trim();
        if (text.isEmpty()) { return null; }
        Double perf = null;
        java.util.regex.Matcher m;
        if (isTime) {
            m = java.util.regex.Pattern.compile("(\\d+)['\\u2032:](\\d+)").matcher(text);
            if (m.find()) { perf = Integer.parseInt(m.group(1)) * 60.0 + Integer.parseInt(m.group(2)); }
            if (perf == null) {
                m = java.util.regex.Pattern.compile("(\\d+)\\s*秒").matcher(text);
                if (m.find()) { perf = Double.parseDouble(m.group(1)); }
            }
            if (perf == null) {
                m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(text);
                if (m.find()) { perf = Double.parseDouble(m.group(1)); }
            }
        } else {
            m = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
            if (m.find()) { perf = Double.parseDouble(m.group(1)); }
        }
        if (perf == null) { return null; }
        java.util.List<String> groups = new java.util.ArrayList<String>();
        for (Map<String, Object> r : matrix) {
            if (!groups.contains(String.valueOf(r.get("age_group")))) { groups.add(String.valueOf(r.get("age_group"))); }
        }
        String myGroup = matchAgeGroupJava(groups, age);
        if (myGroup == null) { return null; }
        for (Map<String, Object> r : matrix) {
            if (!myGroup.equals(String.valueOf(r.get("age_group")))) { continue; }
            double need = asDouble(r.get("value"));
            if (isTime ? perf <= need : perf >= need) { return asDouble(r.get("score")); }
        }
        return 55.0;
    }

    private static double asDouble(Object v) {
        if (v == null) { return 0; }
        try { return Double.parseDouble(String.valueOf(v).trim()); } catch (Exception e) { return 0; }
    }

    /** 按评定标准(sys_setting)给分数定级 */
    private String gradeForPoints(double pts) {
        try {
            String json = jdbc.queryForObject("SELECT svalue FROM sys_setting WHERE skey='fitness_grade_rule'", String.class);
            com.fasterxml.jackson.databind.JsonNode arr = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            java.util.List<com.fasterxml.jackson.databind.JsonNode> rule = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            if (arr != null && arr.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : arr) { rule.add(n); }
            }
            rule.sort((x, y) -> Double.compare(y.path("min").asDouble(0), x.path("min").asDouble(0)));
            for (com.fasterxml.jackson.databind.JsonNode n : rule) {
                double min = n.path("min").asDouble(0);
                boolean gt = ">".equals(n.path("op").asText(">="));
                if (gt ? pts > min : pts >= min) { return n.path("label").asText("及格"); }
            }
            return "不及格";
        } catch (Exception e) {
            if (pts >= 90) { return "优秀"; }
            if (pts >= 80) { return "良好"; }
            if (pts >= 60) { return "及格"; }
            return "不及格";
        }
    }

    /** 仅用于人员匹配/展示、不作为入库列的 key */
    private static final java.util.Set<String> UPSERT_SKIP = new java.util.HashSet<String>(
            java.util.Arrays.asList("idCard", "personName"));

    private void upsert(ModuleDef def, Map<String, Object> vals) {
        rowUpdated = false;
        // 入库列 = 定义列(去掉展示列) + vals 中的额外键(如体能导入的 personnelId)
        List<String> colKeys = new ArrayList<String>();
        for (Col col : def.cols) {
            if (!UPSERT_SKIP.contains(col.key)) { colKeys.add(col.key); }
        }
        for (String k : vals.keySet()) {
            if (!colKeys.contains(k) && !UPSERT_SKIP.contains(k)) { colKeys.add(k); }
        }
        StringBuilder cols = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        List<Object> args = new ArrayList<Object>();
        for (String ck : colKeys) {
            if (cols.length() > 0) {
                cols.append(",");
                marks.append(",");
            }
            cols.append(snake(ck));
            marks.append("?");
            args.add(vals.get(ck));
        }
        if (def.importKey != null && !def.importKey.isEmpty()) {
            Object keyVal = vals.get(def.importKey);
            List<Long> existing = jdbc.queryForList(
                    "SELECT id FROM " + def.table + " WHERE " + snake(def.importKey) + "=?",
                    Long.class, keyVal);
            if (!existing.isEmpty()) {
                StringBuilder set = new StringBuilder();
                List<Object> setArgs = new ArrayList<Object>();
                for (String ck : colKeys) {
                    if (set.length() > 0) {
                        set.append(",");
                    }
                    set.append(snake(ck)).append("=?");
                    setArgs.add(vals.get(ck));
                }
                setArgs.add(existing.get(0));
                jdbc.update("UPDATE " + def.table + " SET " + set
                        + ", updated_at=datetime('now','localtime') WHERE id=?", setArgs.toArray());
                rowUpdated = true;
                return;
            }
        }
        jdbc.update("INSERT INTO " + def.table + "(" + cols + ") VALUES(" + marks + ")", args.toArray());
    }

    /* ================= 工具 ================= */

    private String cell(Row row, Map<String, Integer> headerIndex, String header, DataFormatter fmt) {
        Integer idx = headerIndex.get(header);
        if (idx == null) {
            return "";
        }
        Cell c = row.getCell(idx);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    private void addError(List<String> errors, int rowIndex, String msg) {
        if (errors.size() < 50) {
            errors.add("第" + (rowIndex + 1) + "行:" + msg);
        }
    }

    private Map<Long, String> personNameMap() {
        Map<Long, String> map = new HashMap<Long, String>();
        for (Map<String, Object> m : jdbc.queryForList("SELECT id, name FROM personnel")) {
            map.put(((Number) m.get("id")).longValue(), str(m.get("name")));
        }
        return map;
    }

    private Map<String, Long> personIdMap() {
        Map<String, Long> map = new HashMap<String, Long>();
        for (Map<String, Object> m : jdbc.queryForList("SELECT id, name FROM personnel")) {
            map.put(str(m.get("name")), ((Number) m.get("id")).longValue());
        }
        return map;
    }

    /** camelCase → snake_case */
    private String snake(String key) {
        StringBuilder sb = new StringBuilder();
        for (char ch : key.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                sb.append('_').append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private byte[] workbookBytes(Workbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }
}
