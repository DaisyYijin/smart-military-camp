package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.util.ExcelDates;
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
 * 导入:第一行为中文列头;台账按指定业务键更新,考核/休假/物资逐行追加;
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
        m.put("brand", "vehicle_brand"); m.put("model", "vehicle_model"); m.put("type", "vehicle_type");
        m.put("status", "vehicle_status"); m.put("fuelType", "vehicle_fuel");
        DICT_COLS.put("vehicle", m);
        m = new java.util.HashMap<String, String>();
        m.put("category", "material_category"); m.put("location", "material_location");
        DICT_COLS.put("material", m);
        m = new java.util.HashMap<String, String>();
        m.put("category", "equipment_category"); m.put("status", "equipment_status"); m.put("location", "equipment_location");
        DICT_COLS.put("equipment-gun", new java.util.HashMap<String, String>(m));
        // 枪支仅沿用类别、状态、位置字典；品牌只属于普通装备。
        m.put("brand", "equipment_brand");
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
                        new Col("识别码", "identCode", "text"),
                        new Col("类别", "category", "text"),
                        new Col("品牌", "brand", "text"),
                        new Col("型号", "model", "text"),
                        new Col("状态", "status", "text"),
                        new Col("保管人", "keeperId", "ref-personnel"),
                        new Col("存放位置", "location", "text"),
                        new Col("装备时间", "equipDate", "text"),
                        new Col("备注", "remark", "text")),
                "identCode"));
        MODULES.put("equipment-gun", new ModuleDef("equipment-gun", "枪支", "equipment", "枪支",
                Arrays.asList(
                        new Col("装备名称", "name", "text"),
                        new Col("装备编号", "code", "text"),
                        new Col("类别", "category", "text"),
                        new Col("型号", "model", "text"),
                        new Col("状态", "status", "text"),
                        new Col("保管人", "keeperId", "ref-personnel"),
                        new Col("存放位置", "location", "text"),
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
                ""));
        MODULES.put("vehicle", new ModuleDef("vehicle", "车辆", "vehicle", "车辆",
                Arrays.asList(
                        new Col("车牌号", "plateNo", "text"),
                        new Col("车架号", "vin", "text"),
                        new Col("发动机号", "engineNo", "text"),
                        new Col("品牌", "brand", "text"),
                        new Col("型号", "model", "text"),
                        new Col("车型", "type", "text"),
                        new Col("车身颜色", "color", "text"),
                        new Col("状态", "status", "text"),
                        new Col("驾驶员", "driverId", "ref-personnel"),
                        new Col("核定人数", "seats", "int"),
                        new Col("装备时间", "equipDate", "text"),
                        new Col("年检到期日", "inspectionExpiry", "text"),
                        new Col("备注", "remark", "text")),
                "vin"));
        MODULES.put("leave", new ModuleDef("leave", "休假", "leave_record", "休假",
                Arrays.asList(
                        new Col("人员姓名", "personnelId", "ref-personnel"),
                        new Col("年度", "year", "text"),
                        new Col("休假类型", "leaveType", "text"),
                        new Col("休假地点", "leaveLocation", "text"),
                        new Col("开始日期", "startDate", "text"),
                        new Col("结束日期", "endDate", "text"),
                        new Col("天数", "days", "number"),
                        new Col("路途天数", "travelDays", "number"),
                        new Col("节假日加假", "holidayDays", "number"),
                        new Col("节假日名称", "holidayName", "text"),
                        new Col("事由", "reason", "text"),
                        new Col("备注", "remark", "text")),
                ""));
        MODULES.put("leave-plan", new ModuleDef("leave-plan", "休假计划", "leave_plan", "休假计划",
                Arrays.asList(
                        new Col("人员姓名", "personnelId", "ref-personnel"),
                        new Col("身份证号", "idCard", "text", true),
                        new Col("年度", "year", "text"),
                        new Col("休假类型", "leaveType", "text"),
                        new Col("计划开始日期", "startDate", "text"),
                        new Col("计划结束日期", "endDate", "text"),
                        new Col("天数", "days", "number"),
                        new Col("备注", "remark", "text")),
                ""));
        MODULES.put("phone", new ModuleDef("phone", "手机设备", "phone_device", "手机设备",
                Arrays.asList(
                        new Col("姓名", "personnelId", "ref-personnel"),
                        new Col("身份证号", "idCard", "text", true),
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
        MODULES.put("military", new ModuleDef("military", "军事考核", "military_assessment", "军事考核",
                Arrays.asList(
                        new Col("姓名", "personnelId", "ref-personnel"),
                        new Col("考核类别", "category", "text"),
                        new Col("考核项目", "item", "text"),
                        new Col("成绩", "score", "text"),
                        new Col("分数", "points", "number"),
                        new Col("评定等级", "grade", "text"),
                        new Col("考核日期", "testDate", "text"),
                        new Col("备注", "remark", "text")),
                ""));
        Map<String, String> militaryDict = new java.util.HashMap<String, String>();
        militaryDict.put("category", "military_type");
        DICT_COLS.put("military", militaryDict);
    }

    private final JdbcTemplate jdbc;
    private final PersonnelService personnelService;
    private final DepartmentDaoBridge departmentDaoBridge;
    private LeaveStatusSyncService leaveStatusSyncService;
    private FitnessWideExcelService wideExcel;

    @org.springframework.beans.factory.annotation.Autowired
    public void setLeaveStatusSyncService(LeaveStatusSyncService leaveStatusSyncService) {
        this.leaveStatusSyncService = leaveStatusSyncService;
    }

    /** 体能宽行(一人一行)服务;测试用旧构造时可为 null,此时仅支持旧一项一行格式。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setWideExcel(FitnessWideExcelService wideExcel) {
        this.wideExcel = wideExcel;
    }

    /** 单位名解析桥:避免直接依赖 DepartmentDao 造成循环,取人员导出所需 */
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
            for (Col c : def.cols) {
                if (!c.computed || (("leave-plan".equals(moduleKey) || "phone".equals(moduleKey)) && "idCard".equals(c.key))) {
                    tplCols.add(c);
                }
            }
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
        boolean leavePlanExport = "leave-plan".equals(moduleKey) && (ids == null || ids.isEmpty());
        if (!leavePlanExport && (ids == null || ids.isEmpty())) {
            throw new IllegalArgumentException("请先勾选要导出的数据");
        }
        if ("personnel".equals(moduleKey)) {
            for (Long id : ids) DataScope.requirePerson(jdbc, id);
            List<com.szjx.personnel.server.model.Personnel> all =
                    personnelService.query(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 1, 100000);
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
            DataScope.requireAdmin();
            return exportDict();
        }
        ModuleDef def = MODULES.get(moduleKey);
        if (def == null) {
            throw new IllegalArgumentException("不支持的模块: " + moduleKey);
        }

        // 校验全部指定 ID，不能静默忽略不在当前用户范围内的记录。
        if (ids != null) for (Long id : ids) DataScope.requireRecord(jdbc, def.table, ownerColumn(moduleKey), id);
        StringBuilder placeholders = new StringBuilder();
        List<Map<String, Object>> rows;
        if ("leave-plan".equals(moduleKey) && (ids == null || ids.isEmpty())) {
            rows = jdbc.queryForList("SELECT * FROM leave_plan WHERE " + DataScope.personSql("personnel_id") + " ORDER BY year DESC, start_date, id");
        } else {
            for (int i = 0; i < ids.size(); i++) {
                placeholders.append(i > 0 ? ",?" : "?");
            }
            String inSql = "SELECT * FROM " + def.table + " WHERE id IN ("
                    + placeholders + ")";
            List<Object> args = new ArrayList<Object>(ids);
            if ("equipment-gun".equals(moduleKey)) {
                inSql += " AND category=?";
                args.add("枪支");
            }
            inSql += " AND " + DataScope.personSql(ownerColumn(moduleKey)) + " ORDER BY id";
            rows = jdbc.queryForList(inSql, args.toArray());
        }


        Map<Long, String> personNames = personNameMap();
        Map<Long, java.util.Map<String, String>> personInfo = new java.util.HashMap<Long, java.util.Map<String, String>>();
        boolean needPersonInfo = "fitness".equals(moduleKey) || "leave-plan".equals(moduleKey) || "phone".equals(moduleKey);
        if (needPersonInfo) {
            for (Map<String, Object> pr : jdbc.queryForList("SELECT id, name, id_card FROM personnel WHERE " + DataScope.departmentSql("department_id"))) {
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
            Col col = def.cols.get(i);
            Cell c = head.createCell(i);
            c.setCellValue(col.header);
            c.setCellStyle(headStyle);
            sheet.setColumnWidth(i, "idCard".equals(col.key) ? 20 * 256 : 16 * 256);
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
                } else if ("fitness".equals(moduleKey)
                        && ("personName".equals(col.key) || "idCard".equals(col.key))) {
                    Object pidv = row.get("personnel_id");
                    java.util.Map<String, String> info = pidv == null ? null : personInfo.get(((Number) pidv).longValue());
                    xr.createCell(i).setCellValue(info == null ? ""
                            : ("personName".equals(col.key) ? info.get("name") : info.get("idCard")));
                } else if (("leave-plan".equals(moduleKey) || "phone".equals(moduleKey)) && "idCard".equals(col.key)) {
                    Object pidv = row.get("personnel_id");
                    java.util.Map<String, String> info = pidv == null ? null : personInfo.get(((Number) pidv).longValue());
                    xr.createCell(i).setCellValue(info == null ? "" : info.get("idCard"));
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

    public Map<String, Object> importExcel(String moduleKey, InputStream in) {
        if ("dict".equals(moduleKey)) DataScope.requireAdmin();
        if ("personnel".equals(moduleKey)) return new PersonnelFullExcel(jdbc).importAll(in);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        List<String> errors = new ArrayList<String>();
        int added = 0, updated = 0, failed = 0;
        try (Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(in)) {
            if (wb.getNumberOfSheets() == 0) throw new IllegalArgumentException("文件没有工作表");
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            ExcelDates dates = new ExcelDates(wb);
            Map<String, Integer> headers = Integrity.headers(sheet, fmt);
            ModuleDef def = MODULES.get(moduleKey);
            if (!"dict".equals(moduleKey) && def == null) throw new IllegalArgumentException("不支持的模块:" + moduleKey);
            // 体能宽行(一人一行,项目为列)按表头自动识别;旧一项一行格式走下方通用导入,两者统一覆盖更新。
            if ("fitness".equals(moduleKey) && wideExcel != null && !headers.containsKey("考核项目")) {
                if (wideExcel.detectWide(headers)) {
                    return wideExcel.importWide(sheet, headers, fmt, dates);
                }
                if (headers.containsKey("姓名") && headers.containsKey("身份证号码") && headers.containsKey("考核日期")) {
                    // 形似宽行却没有任何启用项目列,直接说明而不是落入旧格式的业务键报错。
                    throw new IllegalArgumentException("缺少体能项目列:至少需要一列表头为启用的体能项目");
                }
            }
            List<Integer> rows = new ArrayList<Integer>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) if (!Integrity.blank(sheet.getRow(r), fmt)) rows.add(r);
            if ("dict".equals(moduleKey)) rows.sort((a, b) -> Integer.compare(cell(sheet.getRow(a), headers, "父级路径", fmt).split("/", -1).length, cell(sheet.getRow(b), headers, "父级路径", fmt).split("/", -1).length));
            for (int r : rows) {
                try {
                    boolean changed = Integrity.transaction(jdbc, () -> {
                        Row row = sheet.getRow(r);
                        if ("dict".equals(moduleKey)) return importDictRow(row, headers, fmt);
                        Map<String, Object> vals = new LinkedHashMap<String, Object>();
                        for (Col col : def.cols) {
                            if (col.computed) continue;
                            String raw = Integrity.dateKey(snake(col.key)) && headers.containsKey(col.header)
                                    ? dates.read(row.getCell(headers.get(col.header)), col.header)
                                    : cell(row, headers, col.header, fmt);
                            if ("ref-personnel".equals(col.type)) {
                                String card = cell(row, headers, col.header + "身份证号", fmt);
                                if (card.isEmpty()) card = cell(row, headers, "身份证号码", fmt);
                                if (card.isEmpty()) card = cell(row, headers, "身份证号", fmt);
                                if ("phone".equals(moduleKey) && card.isEmpty()) {
                                    throw new IllegalArgumentException("手机设备导入身份证号不能为空");
                                }
                                if (!raw.isEmpty() || !card.isEmpty()) vals.put(snake(col.key), Integrity.person(jdbc, card, raw));
                                else if (headers.containsKey(col.header)) vals.put(snake(col.key), null);
                            } else if (headers.containsKey(col.header)) {
                                if ("number".equals(col.type) || "int".equals(col.type)) {
                                    if (raw.isEmpty()) vals.put(snake(col.key), null);
                                    else {
                                        double n = FitnessScoringService.number(raw, col.header);
                                        if ("int".equals(col.type) && (n != Math.rint(n) || n > Integer.MAX_VALUE)) throw new IllegalArgumentException(col.header + "必须为非负整数");
                                        vals.put(snake(col.key), "int".equals(col.type) ? (Object)(int)n : n);
                                    }
                                } else {
                                    vals.put(snake(col.key), raw);
                                }
                            }
                        }
                        if ("equipment-gun".equals(moduleKey)) {
                            vals.put("category", "枪支");
                            // 历史枪支表单的识别码/购置日期不属于枪支 Excel 接口，忽略不导入。
                        }
                        if ("fitness".equals(moduleKey)) vals.put("personnel_id", Integrity.person(jdbc, str(vals.remove("id_card")), str(vals.remove("person_name"))));
                        if ("leave-plan".equals(moduleKey)) {
                            String card = cell(row, headers, "身份证号", fmt);
                            String name = cell(row, headers, "人员姓名", fmt);
                            if (card.isEmpty() && name.isEmpty()) throw new IllegalArgumentException("人员姓名/身份证号为空");
                            vals.put("personnel_id", Integrity.person(jdbc, card, name));
                        }
                        String[] keys = businessKeys(def, vals);
                        boolean gun = "equipment-gun".equals(moduleKey)
                                || ("equipment".equals(moduleKey) && "枪支".equals(str(vals.get("category"))));
                        Long id = gun ? Integrity.existingGun(jdbc, vals)
                                : (keys.length == 0 ? null : Integrity.existing(jdbc, def.table, vals, keys));
                        // 业务键全局命中后先校验旧归属，再校验写入后的归属，禁止抢占外域记录。
                        if (id != null) DataScope.requireRecord(jdbc, def.table, ownerColumn(moduleKey), id);
                        Map<String, Object> merged = new LinkedHashMap<String, Object>();
                        if (id != null) merged.putAll(jdbc.queryForMap("SELECT * FROM " + def.table + " WHERE id=?", id));
                        merged.putAll(vals);
                        Object owner = merged.get(ownerColumn(moduleKey));
                        DataScope.requirePerson(jdbc, owner == null ? null : ((Number) owner).longValue());
                        requireModule(def.key, merged);
                        Map<String, String> dict = DICT_COLS.get(def.key);
                        if (dict != null) for (Map.Entry<String, String> entry : dict.entrySet()) Integrity.dict(jdbc, entry.getValue(), str(merged.get(snake(entry.getKey()))));
                        if ("vehicle".equals(moduleKey)) Integrity.cascade(jdbc, "vehicle_brand", str(merged.get("brand")), "vehicle_model", str(merged.get("model")));
                        if ("fitness".equals(moduleKey)) {
                            com.szjx.personnel.server.model.FitnessRecord record = new com.szjx.personnel.server.model.FitnessRecord();
                            record.setPersonnelId(((Number)merged.get("personnel_id")).longValue()); record.setItem(str(merged.get("item"))); record.setScore(str(merged.get("score"))); record.setTestDate(str(merged.get("test_date")));
                            new FitnessScoringService(jdbc).score(record); vals.put("points", record.getPoints()); vals.put("grade", record.getGrade());
                        }
                        Integrity.save(jdbc, def.table, vals, id);
                        if ("leave".equals(moduleKey)) {
                            // Excel按行追加，允许重复区间；普通API的重叠校验不变。
                            if (leaveStatusSyncService == null) throw new IllegalStateException("休假状态服务未配置");
                            leaveStatusSyncService.sync();
                        }
                        if ("phone".equals(moduleKey)) Integrity.syncPhone(jdbc, ((Number)merged.get("personnel_id")).longValue());
                        return id != null;
                    });
                    if (changed) updated++; else added++;
                } catch (Exception e) { failed++; errors.add("第" + (r + 1) + "行:" + Integrity.message(e)); }
            }
        } catch (Exception e) { result.put("error", "文件解析失败:" + Integrity.message(e)); }
        result.put("added", added); result.put("updated", updated); result.put("failed", failed); result.put("errors", errors);
        return result;
    }

    private String ownerColumn(String moduleKey) {
        if ("equipment".equals(moduleKey) || "equipment-gun".equals(moduleKey) || "material".equals(moduleKey)) return "keeper_id";
        return "vehicle".equals(moduleKey) ? "driver_id" : "personnel_id";
    }

    private String[] businessKeys(ModuleDef def, Map<String, Object> vals) {
        if ("equipment-gun".equals(def.key)) return new String[]{"code"};
        if ("equipment".equals(def.key)) {
            return "枪支".equals(str(vals.get("category"))) ? new String[]{"code"} : new String[]{"ident_code"};
        }
        if ("phone".equals(def.key)) return new String[]{"personnel_id"};
        // 体能考核:同人+同项目+同考核日期已有成绩时覆盖更新(重算分数/评定)。
        if ("fitness".equals(def.key)) return new String[]{"personnel_id", "item", "test_date"};
        // 考核(军事)、休假登记/计划、物资没有导入匹配键，每个有效行只新增。
        return def.importKey.isEmpty() ? new String[0] : new String[]{snake(def.importKey)};
    }
    private void requireModule(String key, Map<String, Object> vals) {
        String[] required;
        if ("leave-plan".equals(key)) required = new String[]{"personnel_id", "year", "leave_type", "start_date", "end_date", "days"};
        else if ("equipment-gun".equals(key)) required = new String[]{"name", "code"};
        else if ("equipment".equals(key)) required = new String[]{"name", "枪支".equals(str(vals.get("category"))) ? "code" : "ident_code"};
        else if ("material".equals(key)) required = new String[]{"name", "quantity"};
        else if ("vehicle".equals(key)) required = new String[]{"plate_no"};
        else if ("phone".equals(key)) required = new String[]{"personnel_id", "device_type", "brand"};
        else if ("leave".equals(key)) required = new String[]{"personnel_id", "leave_type", "start_date", "end_date", "days"};
        else required = new String[]{"personnel_id", "test_date", "item", "score"};
        for (String field : required) Integrity.required(vals, field);
        if (("leave".equals(key) || "leave-plan".equals(key))
                && str(vals.get("start_date")).compareTo(str(vals.get("end_date"))) > 0) {
            throw new IllegalArgumentException("结束日期早于开始日期");
        }
        if ("leave-plan".equals(key) && FitnessScoringService.number(str(vals.get("days")), "天数") < 0) {
            throw new IllegalArgumentException("天数不能为负");
        }
    }
    private boolean importDictRow(Row row, Map<String, Integer> headers, DataFormatter fmt) {
        String type = cell(row, headers, "字典类型", fmt), label = cell(row, headers, "选项内容", fmt);
        if (type.isEmpty() || label.isEmpty()) throw new IllegalArgumentException("字典类型/选项内容为空");
        String parentType = cell(row, headers, "父级类型", fmt), path = cell(row, headers, "父级路径", fmt);
        Long parent = 0L;
        if (!path.isEmpty()) parent = Integrity.dictPath(jdbc, parentType, path, false);
        else if (!cell(row, headers, "父级内容", fmt).isEmpty()) parent = Integrity.dictId(jdbc, parentType, cell(row, headers, "父级内容", fmt), null, false);
        String expected = Integrity.parentType(type);
        if (!expected.isEmpty() && !"rank".equals(type) && (parent == 0 || !expected.equals(parentType))) throw new IllegalArgumentException("该字典需有效父级类型/路径:" + expected);
        Map<String, Object> vals = new LinkedHashMap<String, Object>(); vals.put("dict_type", type); vals.put("dict_label", label);
        if (headers.containsKey("父级路径") || headers.containsKey("父级内容") || headers.containsKey("父级类型")) vals.put("parent_id", parent);
        Long id = Integrity.existing(jdbc, "sys_dict", vals, vals.containsKey("parent_id") ? new String[]{"dict_type", "dict_label", "parent_id"} : new String[]{"dict_type", "dict_label"});
        if (headers.containsKey("排序")) {
            String sort = cell(row, headers, "排序", fmt); double n = sort.isEmpty() ? 0 : FitnessScoringService.number(sort, "排序");
            if (n != Math.rint(n) || n > Integer.MAX_VALUE) throw new IllegalArgumentException("排序必须为非负整数"); vals.put("sort", (int)n);
        }
        if (headers.containsKey("状态")) {
            String enabled = cell(row, headers, "状态", fmt);
            if (!Arrays.asList("", "启用", "停用", "1", "0").contains(enabled)) throw new IllegalArgumentException("状态应为启用/停用");
            vals.put("enabled", "停用".equals(enabled) || "0".equals(enabled) ? 0 : 1);
        }
        if (headers.containsKey("备注")) vals.put("remark", cell(row, headers, "备注", fmt));
        Integrity.save(jdbc, "sys_dict", vals, id); return id != null;
    }
    private String cell(Row row, Map<String, Integer> headers, String header, DataFormatter fmt) {
        Integer index = headers.get(header);
        if (index == null || row.getCell(index) == null) {
            return "";
        }
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(index);
        // 日期单元格统一 yyyy-MM-dd,避免 DataFormatter 按单元格样式/区域输出 01/03/23
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
        }
        return fmt.formatCellValue(cell).trim();
    }
    private Map<Long, String> personNameMap() {
        Map<Long, String> result = new HashMap<Long, String>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT id, name FROM personnel WHERE " + DataScope.departmentSql("department_id"))) result.put(((Number)row.get("id")).longValue(), str(row.get("name")));
        return result;
    }

    /** Shared import primitives; writes and derived state are committed per row. */
    static final class Integrity {
        static <T> T transaction(JdbcTemplate jdbc, java.util.function.Supplier<T> work) {
            org.springframework.transaction.support.TransactionTemplate tx = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
            tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return tx.execute(status -> work.get());
        }
        static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
        static String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
        static void required(Map<String, ?> values, String key) { if (text(values.get(key)).isEmpty()) throw new IllegalArgumentException("必填字段为空:" + key); }
        static boolean blank(Row row, DataFormatter fmt) {
            if (row == null) return true;
            for (Cell cell : row) if (!fmt.formatCellValue(cell).trim().isEmpty()) return false;
            return true;
        }
        static Map<String, Integer> headers(Sheet sheet, DataFormatter fmt) {
            Row row = sheet.getRow(0); if (row == null) throw new IllegalArgumentException("文件没有列头");
            Map<String, Integer> result = new LinkedHashMap<String, Integer>();
            for (Cell cell : row) {
                String name = fmt.formatCellValue(cell).trim();
                if (!name.isEmpty() && result.put(name, cell.getColumnIndex()) != null) throw new IllegalArgumentException("重复列头:" + name);
            }
            return result;
        }
        static boolean dateKey(String key) { return "birthday".equals(key) || key.endsWith("_date") || key.endsWith("_expiry"); }
        static Long person(JdbcTemplate jdbc, String card, String name) {
            card = text(card).toUpperCase(java.util.Locale.ROOT); name = text(name);
            List<Long> ids;
            if (!card.isEmpty()) ids = jdbc.queryForList("SELECT id FROM personnel WHERE UPPER(TRIM(id_card))=?", Long.class, card);
            else {
                if (name.isEmpty()) throw new IllegalArgumentException("人员姓名/身份证号为空");
                ids = jdbc.queryForList("SELECT id FROM personnel WHERE TRIM(name)=?", Long.class, name);
            }
            if (ids.size() != 1) throw new IllegalArgumentException(ids.isEmpty() ? "人员不存在:" + (card.isEmpty() ? name : card) : "人员不唯一，请使用身份证号:" + name);
            DataScope.requirePerson(jdbc, ids.get(0));
            return ids.get(0);
        }
        static Long existingGun(JdbcTemplate jdbc, Map<String, ?> values) {
            String code = text(values.get("code"));
            if (code.isEmpty()) throw new IllegalArgumentException("缺少业务键列:code");
            List<Long> ids = jdbc.queryForList(
                    "SELECT id FROM equipment WHERE COALESCE(code,'')=COALESCE(?,'') AND category=?",
                    Long.class, code, "枪支");
            if (ids.size() > 1) throw new IllegalArgumentException("业务键匹配多条记录，拒绝任选:equipment-gun");
            return ids.isEmpty() ? null : ids.get(0);
        }
        static Long existing(JdbcTemplate jdbc, String table, Map<String, ?> values, String[] keys) {
            StringBuilder where = new StringBuilder(); List<Object> args = new ArrayList<Object>();
            for (String key : keys) {
                if (!values.containsKey(key)) {
                    if ("material".equals(table)) args.add("");
                    else throw new IllegalArgumentException("缺少业务键列:" + key);
                } else args.add(values.get(key));
                if (where.length() > 0) where.append(" AND ");
                if ("parent_id".equals(key)) where.append("COALESCE(parent_id,0)=?");
                else where.append("COALESCE(").append(key).append(",'')=COALESCE(?,'')");
            }
            if (args.stream().allMatch(v -> text(v).isEmpty())) throw new IllegalArgumentException("业务键为空");
            if (!"material".equals(table)) for (int i = 0; i < keys.length; i++) if (text(args.get(i)).isEmpty() && !"parent_id".equals(keys[i])) throw new IllegalArgumentException("业务键为空:" + keys[i]);
            // 枪支只按编号走existingGun，历史残留识别码不得被普通装备导入命中。
            if ("equipment".equals(table)) where.append(" AND COALESCE(category,'')<>'枪支'");
            List<Long> ids = jdbc.queryForList("SELECT id FROM " + table + " WHERE " + where, Long.class, args.toArray());
            if (ids.size() > 1) throw new IllegalArgumentException("业务键匹配多条记录，拒绝任选:" + table);
            return ids.isEmpty() ? null : ids.get(0);
        }
        static long save(JdbcTemplate jdbc, String table, Map<String, ?> vals, Long id) {
            StringBuilder columns = new StringBuilder(), marks = new StringBuilder(), set = new StringBuilder(); List<Object> args = new ArrayList<Object>();
            for (Map.Entry<String, ?> entry : vals.entrySet()) {
                if (columns.length() > 0) { columns.append(','); marks.append(','); set.append(','); }
                columns.append(entry.getKey()); marks.append('?'); set.append(entry.getKey()).append("=?"); args.add(entry.getValue());
            }
            if (id == null) {
                jdbc.update("INSERT INTO " + table + "(" + columns + ") VALUES(" + marks + ")", args.toArray());
                return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
            }
            args.add(id); jdbc.update("UPDATE " + table + " SET " + set + ",updated_at=datetime('now','localtime') WHERE id=?", args.toArray()); return id;
        }
        static void syncPhone(JdbcTemplate jdbc, Long pid) {
            List<String> phones = jdbc.queryForList("SELECT phone_number FROM phone_device WHERE personnel_id=? AND TRIM(COALESCE(phone_number,''))!='' ORDER BY id DESC LIMIT 1", String.class, pid);
            jdbc.update("UPDATE personnel SET phone=? WHERE id=?", phones.isEmpty() ? "" : text(phones.get(0)), pid);
        }
        static String parentType(String type) {
            if ("region_city".equals(type)) return "region_province";
            if ("region_district".equals(type)) return "region_city";
            if ("vehicle_model".equals(type)) return "vehicle_brand";
            if ("rank".equals(type)) return "personnel_category";
            return "";
        }
        static Long dictId(JdbcTemplate jdbc, String type, String label, Long parent, boolean enabled) {
            if (type.isEmpty() || label.isEmpty()) throw new IllegalArgumentException("字典类型/内容为空");
            String sql = "SELECT id FROM sys_dict WHERE dict_type=? AND dict_label=?" + (enabled ? " AND enabled=1" : "");
            List<Object> args = new ArrayList<Object>(Arrays.asList(type, label));
            if (parent != null) { sql += " AND COALESCE(parent_id,0)=?"; args.add(parent); }
            List<Long> ids = jdbc.queryForList(sql, Long.class, args.toArray());
            if (ids.size() != 1) throw new IllegalArgumentException("字典不存在、停用或父级不明确:" + type + "「" + label + "」");
            return ids.get(0);
        }
        static void dict(JdbcTemplate jdbc, String type, String label) {
            if (!label.isEmpty() && jdbc.queryForList("SELECT id FROM sys_dict WHERE dict_type=? AND dict_label=? AND enabled=1", Long.class, type, label).isEmpty()) throw new IllegalArgumentException("不在启用下拉选项中:" + type + "「" + label + "」");
        }
        static void cascade(JdbcTemplate jdbc, String parentType, String parent, String childType, String child) {
            if (child.isEmpty()) return;
            if (parent.isEmpty()) throw new IllegalArgumentException("下级选项缺少上级:" + childType);
            Long id = dictId(jdbc, parentType, parent, null, true); dictId(jdbc, childType, child, id, true);
        }
        static Long dictPath(JdbcTemplate jdbc, String leafType, String path, boolean enabled) {
            String parent = parentType(leafType); int slash = path.lastIndexOf('/'); Long parentId = 0L; String label = path;
            if (slash >= 0) {
                if (parent.isEmpty()) throw new IllegalArgumentException("父级路径层级错误");
                parentId = dictPath(jdbc, parent, path.substring(0, slash), enabled); label = path.substring(slash + 1);
            } else if (!parent.isEmpty()) throw new IllegalArgumentException("父级路径不完整:" + path);
            return dictId(jdbc, leafType, label, parentId, enabled);
        }
        static void region(JdbcTemplate jdbc, String value) {
            if (value.isEmpty()) return;
            String[] parts = value.split("/", -1); if (parts.length > 3) throw new IllegalArgumentException("行政区划应为省/市/区县");
            String[] types = {"region_province", "region_city", "region_district"}; Long parent = 0L;
            for (int i = 0; i < parts.length; i++) parent = dictId(jdbc, types[i], parts[i].trim(), parent, true);
        }
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
