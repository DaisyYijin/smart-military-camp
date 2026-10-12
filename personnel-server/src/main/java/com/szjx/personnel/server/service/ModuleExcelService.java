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

    /** 列定义:header=Excel列头 key=返回字段/入库字段 type=text|number|int|ref-personnel */
    private static class Col {
        final String header;
        final String key;
        final String type;

        Col(String header, String key, String type) {
            this.header = header;
            this.key = key;
            this.type = type;
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
                        new Col("备注", "remark", "text")),
                "name"));
        MODULES.put("vehicle", new ModuleDef("vehicle", "车辆", "vehicle", "车辆",
                Arrays.asList(
                        new Col("车牌号", "plateNo", "text"),
                        new Col("品牌型号", "brand", "text"),
                        new Col("车型", "type", "text"),
                        new Col("状态", "status", "text"),
                        new Col("驾驶员", "driverId", "ref-personnel"),
                        new Col("核定人数", "seats", "int"),
                        new Col("里程(公里)", "mileage", "number"),
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
                        new Col("事由", "reason", "text"),
                        new Col("备注", "remark", "text")),
                ""));
        MODULES.put("fitness", new ModuleDef("fitness", "体能考核", "fitness_record", "体能考核",
                Arrays.asList(
                        new Col("人员姓名", "personnelId", "ref-personnel"),
                        new Col("考核项目", "item", "text"),
                        new Col("成绩", "score", "text"),
                        new Col("评定等级", "grade", "text"),
                        new Col("考核日期", "testDate", "text"),
                        new Col("考核人", "tester", "text"),
                        new Col("备注", "remark", "text")),
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

    public byte[] export(String moduleKey, List<Long> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请先勾选要导出的数据");
        }
        if ("personnel".equals(moduleKey)) {
            List<com.szjx.personnel.server.model.Personnel> all =
                    personnelService.query(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 1, 100000);
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
                        } else {
                            vals.put(col.key, raw);
                        }
                    }
                    if (!rowOk) {
                        continue;
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

    private void upsert(ModuleDef def, Map<String, Object> vals) {
        rowUpdated = false;
        StringBuilder cols = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        List<Object> args = new ArrayList<Object>();
        for (Col col : def.cols) {
            if (cols.length() > 0) {
                cols.append(",");
                marks.append(",");
            }
            cols.append(snake(col.key));
            marks.append("?");
            args.add(vals.get(col.key));
        }
        if (def.importKey != null && !def.importKey.isEmpty()) {
            Object keyVal = vals.get(def.importKey);
            List<Long> existing = jdbc.queryForList(
                    "SELECT id FROM " + def.table + " WHERE " + snake(def.importKey) + "=?",
                    Long.class, keyVal);
            if (!existing.isEmpty()) {
                StringBuilder set = new StringBuilder();
                List<Object> setArgs = new ArrayList<Object>();
                for (Col col : def.cols) {
                    if (set.length() > 0) {
                        set.append(",");
                    }
                    set.append(snake(col.key)).append("=?");
                    setArgs.add(vals.get(col.key));
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
        if (errors.size() < 5) {
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
