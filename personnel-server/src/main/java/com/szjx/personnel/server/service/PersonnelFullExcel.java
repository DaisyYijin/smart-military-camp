package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.util.ExcelDates;
import com.szjx.personnel.server.util.DepartmentHierarchy;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

/**
 * 人员多 Sheet Excel:导出(含全部子记录)、导入、模板下载。
 * Sheet 1 人员主表,Sheet 2-8 子表,用身份证号关联。
 */
@Service
public class PersonnelFullExcel {

    private final JdbcTemplate jdbc;

    /** 每个 Sheet 的定义:名称、表名、列头(中文)、列 key(snake) */
    private static final String[][] SHEETS = {
        {"任职经历", "position_history",
            "身份证号,姓名,开始日期,结束日期,任职地点,担任职务,备注",
            "idcard,__name,start_date,end_date,place,post,remark"},
        {"奖惩情况", "reward_punish",
            "身份证号,姓名,类型,日期,事由,内容,备注",
            "idcard,__name,kind,occur_date,reason,content,remark"},
        {"家庭成员", "family_member",
            "身份证号,姓名,关系,成员姓名,成员身份证号,性别,出生日期,政治面貌,工作单位及职务,联系电话,备注",
            "idcard,__name,relationship,name,id_card,gender,birth_date,political_status,work_info,phone,remark"},
        {"驾驶证", "driver_license",
            "身份证号,姓名,类型,证号,准驾车型,初领日期,有效期至,发证机关,状态,年审日期,备注",
            "idcard,__name,license_type,license_no,vehicle_class,issue_date,expiry_date,issuing_auth,license_status,review_date,remark"},
        {"个人卡号", "personal_card",
            "身份证号,姓名,卡类型,卡号,开户行,开户支行,开户日期,卡状态,备注",
            "idcard,__name,card_type,card_no,bank_name,branch_name,open_date,card_status,remark"},
        {"私家车", "private_vehicle",
            "身份证号,姓名,车牌号,品牌型号,颜色,购置日期,价格(元),状态,备注",
            "idcard,__name,plate_no,brand,color,purchase_date,price,vehicle_status,remark"},
        {"住房情况", "housing_info",
            "身份证号,姓名,类型,地址,面积,性质,房产证,贷款,购置时间,产权人,备注",
            "idcard,__name,housing_type,address,area,house_nature,has_cert,loan_status,purchase_date,owner_name,remark"},
    };

    /** 人员主表列(与 ExcelUtils.exportXlsx 对应但简化,此处自建) */
    private static final String[] MAIN_HEADERS = {
        "姓名", "性别", "身份证号", "出生日期", "手机号", "编制单位", "所在点位", "人员类别", "军衔/职级", "职务/岗位", "任岗位时间",
        "状态", "入伍/入职日期", "相当入伍时间", "民族", "籍贯所在地", "入伍所在地", "血型", "政治面貌",
        "入党/入团日期", "组织关系所在地", "党内职务",
        "提交申请书时间", "积极分子时间", "预备党员时间", "转正时间",
        "婚姻状况", "结婚日期", "离婚日期", "随军时间", "随军条件",
        "学历", "是否独生子女", "身高(cm)", "体重(kg)", "详细地址",
        "入学时间", "毕业时间", "毕业院校", "专业", "学位", "学历形式", "学历证书编号",
        "配偶姓名", "配偶工作单位", "配偶电话", "配偶身份证号", "配偶职业", "党团备注", "婚姻备注", "备注", "照片文件名", "导出人员编号", "军人证件号", "单位完整路径（只读）",
        "实际单位"
    };
    private static final String[] MAIN_KEYS = {
        "name", "gender", "id_card", "birthday", "phone", "department_name", "location_point", "category", "rank", "post", "post_date",
        "status", "entry_date", "equiv_entry_date", "nation", "native_place", "enlist_place", "blood_type", "political_status",
        "party_join_date", "party_org_location", "party_post",
        "party_apply_date", "party_activist_date", "party_candidate_date", "party_full_date",
        "marital_status", "marriage_date", "divorce_date", "follow_army_date", "follow_army_status",
        "education", "only_child", "height", "weight", "address",
        "edu_enroll_date", "edu_graduate_date", "edu_school", "edu_major", "edu_degree", "edu_form", "edu_cert_no",
        "spouse_name", "spouse_work", "spouse_phone", "spouse_id_card", "spouse_job", "party_remark", "marriage_remark", "remark", "photo", "__source_id", "military_id_no", "department_path",
        "actual_department_name"
    };

    private static final String[] REQUIRED_MAIN_COLS = {
        "name", "id_card", "military_id_no", "gender", "birthday", "department_name", "location_point", "category", "rank", "post",
        "status", "entry_date", "equiv_entry_date", "nation", "native_place", "enlist_place", "blood_type", "political_status",
        "marital_status", "education", "only_child", "address"
    };
    private static final java.util.Map<String, String> REQUIRED_MAIN_LABELS = new java.util.HashMap<String, String>();
    static {
        REQUIRED_MAIN_LABELS.put("name", "姓名");
        REQUIRED_MAIN_LABELS.put("id_card", "身份证号");
        REQUIRED_MAIN_LABELS.put("military_id_no", "军人证件号");
        REQUIRED_MAIN_LABELS.put("gender", "性别");
        REQUIRED_MAIN_LABELS.put("birthday", "出生日期");
        REQUIRED_MAIN_LABELS.put("department_name", "编制单位");
        REQUIRED_MAIN_LABELS.put("location_point", "所在点位");
        REQUIRED_MAIN_LABELS.put("category", "人员类别");
        REQUIRED_MAIN_LABELS.put("rank", "军衔/职级");
        REQUIRED_MAIN_LABELS.put("post", "职务/岗位");
        REQUIRED_MAIN_LABELS.put("entry_date", "入伍/入职日期");
        REQUIRED_MAIN_LABELS.put("equiv_entry_date", "相当入伍时间");
        REQUIRED_MAIN_LABELS.put("nation", "民族");
        REQUIRED_MAIN_LABELS.put("native_place", "籍贯所在地");
        REQUIRED_MAIN_LABELS.put("enlist_place", "入伍所在地");
        REQUIRED_MAIN_LABELS.put("blood_type", "血型");
        REQUIRED_MAIN_LABELS.put("political_status", "政治面貌");
        REQUIRED_MAIN_LABELS.put("marital_status", "婚姻状况");
        REQUIRED_MAIN_LABELS.put("education", "学历");
        REQUIRED_MAIN_LABELS.put("status", "状态");
        REQUIRED_MAIN_LABELS.put("only_child", "是否独生子女");
        REQUIRED_MAIN_LABELS.put("address", "详细地址");
    }

    // phone 是手机设备模块镜像,Excel 主表只展示,不能直接导入覆盖。
    private static final String[] MAIN_COLS = {
        "name", "gender", "id_card", "military_id_no", "birthday", "location_point", "category", "rank", "post", "post_date",
        "status", "entry_date", "equiv_entry_date", "nation", "native_place", "enlist_place", "blood_type", "political_status",
        "party_join_date", "party_org_location", "party_post",
        "party_apply_date", "party_activist_date", "party_candidate_date", "party_full_date",
        "marital_status", "marriage_date", "divorce_date", "follow_army_date", "follow_army_status",
        "education", "only_child", "height", "weight", "address",
        "edu_enroll_date", "edu_graduate_date", "edu_school", "edu_major", "edu_degree", "edu_form", "edu_cert_no",
        "spouse_name", "spouse_work", "spouse_phone", "spouse_id_card", "spouse_job", "party_remark", "marriage_remark", "remark"
    };

    /** 旧版模板表头 → 新键(导入兼容:老文件的"籍贯/家庭住址"仍能对上列) */
    private static final java.util.Map<String, String> HEADER_ALIASES = new java.util.HashMap<String, String>();
    static {
        HEADER_ALIASES.put("籍贯", "native_place");
        HEADER_ALIASES.put("部门", "department_name");
        HEADER_ALIASES.put("家庭住址", "address");
    }

    public PersonnelFullExcel(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ================= 导出 ================= */

    public byte[] export(List<Long> ids) throws Exception {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("请先勾选要导出的数据");
        // 主表、全部子表及照片文件名读取前统一校验，混合 ID 不允许部分导出。
        for (Long id : ids) DataScope.requirePerson(jdbc, id);
        Workbook wb = new XSSFWorkbook();
        CellStyle head = headStyle(wb);

        // Sheet 1: 人员主表
        Sheet main = wb.createSheet("人员信息");
        writeHeader(main, MAIN_HEADERS, head);
        StringBuilder ph = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            ph.append(i > 0 ? ",?" : "?");
        }
        List<Map<String, Object>> people = jdbc.queryForList(
                "SELECT p.*, d.name AS department_name, ad.name AS actual_department_name FROM personnel p " +
                "LEFT JOIN department d ON p.department_id=d.id " +
                "LEFT JOIN department ad ON p.actual_department_id=ad.id WHERE p.id IN (" + ph + ") ORDER BY p.id",
                ids.toArray());
        Map<Long, String> idCardMap = new HashMap<Long, String>();
        Map<Long, String> nameMap = new HashMap<Long, String>();
        DepartmentHierarchy hierarchy = DepartmentHierarchy.load(jdbc);
        int r = 1;
        for (Map<String, Object> p : people) {
            Object departmentId = p.get("department_id");
            p.put("department_path", hierarchy.path(departmentId == null ? null : ((Number) departmentId).longValue()));
            Row row = main.createRow(r++);
            for (int c = 0; c < MAIN_KEYS.length; c++) {
                Object v = p.get(MAIN_KEYS[c]);
                if ("__source_id".equals(MAIN_KEYS[c])) {
                    v = "export-" + p.get("id");
                }
                row.createCell(c).setCellValue(v == null ? "" : String.valueOf(v));
            }
            Long pidKey = ((Number) p.get("id")).longValue();
            idCardMap.put(pidKey,
                    String.valueOf(p.get("id_card") == null ? "" : p.get("id_card")));
            nameMap.put(pidKey,
                    String.valueOf(p.get("name") == null ? "" : p.get("name")));
        }
        // 列宽
        for (int i = 0; i < MAIN_HEADERS.length; i++) {
            main.setColumnWidth(i, 14 * 256);
        }

        // Sheet 2-8: 子表
        for (String[] def : SHEETS) {
            String sheetName = def[0];
            String table = def[1];
            String[] headers = def[2].split(",");
            String[] keys = def[3].split(",");

            Sheet sheet = wb.createSheet(sheetName);
            writeHeader(sheet, headers, head);
            int row = 1;
            for (Map.Entry<Long, String> e : idCardMap.entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) {
                    continue;
                }
                List<Map<String, Object>> subs = jdbc.queryForList(
                        "SELECT * FROM " + table + " WHERE personnel_id=? ORDER BY id", e.getKey());
                for (Map<String, Object> sub : subs) {
                    Row sr = sheet.createRow(row++);
                    sr.createCell(0).setCellValue(e.getValue()); // 身份证号
                    sr.createCell(1).setCellValue(nameMap.get(e.getKey()) == null ? "" : nameMap.get(e.getKey())); // 姓名
                    for (int c = 2; c < keys.length; c++) {
                        Object v = sub.get(keys[c]);
                        sr.createCell(c).setCellValue(v == null ? "" : String.valueOf(v));
                    }
                }
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 14 * 256);
            }
        }

        return bytes(wb);
    }

    /* ================= 模板 ================= */

    public byte[] template() throws Exception {
        Workbook wb = new XSSFWorkbook();
        CellStyle head = headStyle(wb);

        Sheet main = wb.createSheet("人员信息");
        writeHeader(main, MAIN_HEADERS, head);
        for (int i = 0; i < MAIN_HEADERS.length; i++) {
            main.setColumnWidth(i, 14 * 256);
        }

        for (String[] def : SHEETS) {
            Sheet sheet = wb.createSheet(def[0]);
            writeHeader(sheet, def[2].split(","), head);
            for (int i = 0; i < def[2].split(",").length; i++) {
                sheet.setColumnWidth(i, 14 * 256);
            }
        }
        return bytes(wb);
    }

    /* ================= 导入 ================= */

    @SuppressWarnings("unchecked")
    public Map<String, Object> importAll(InputStream in) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        List<String> errors = new ArrayList<String>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            DataFormatter fmt = new DataFormatter();
            ExcelDates dates = new ExcelDates(wb);
            Sheet mainSheet = wb.getSheetAt(0);
            if (mainSheet == null || mainSheet.getRow(0) == null) {
                result.put("error", "Excel \u7b2c\u4e00\u4e2aSheet\u4e3a\u7a7a\u6216\u6ca1\u6709\u5217\u5934");
                return result;
            }
            Map<Integer, String> colMap = new HashMap<Integer, String>();
            Row headRow = mainSheet.getRow(0);
            for (int i = 0; i < headRow.getLastCellNum(); i++) {
                String h = fmt.formatCellValue(headRow.getCell(i)).trim();
                for (int k = 0; k < MAIN_HEADERS.length; k++) {
                    if (h.equals(MAIN_HEADERS[k]) && !"photo".equals(MAIN_KEYS[k]) && !"__source_id".equals(MAIN_KEYS[k]) && !"department_path".equals(MAIN_KEYS[k])) {
                        colMap.put(i, camelToSnake(MAIN_KEYS[k]));
                    }
                }
                if (!colMap.containsKey(i) && HEADER_ALIASES.containsKey(h)) {
                    colMap.put(i, HEADER_ALIASES.get(h));
                }
            }
            // 必须在任何人员/子表/单位写入前拒绝旧模板，避免缺列被当成清空或补丁保留。
            if (!colMap.containsValue("military_id_no")) {
                result.put("error", "缺少必填列「军人证件号」，请下载并使用新模板，补齐后重新导入");
                return result;
            }
            Map<String, Long> cardMap = new HashMap<String, Long>();
            Set<String> successfullyImportedCards = new HashSet<String>();
            // cardMap 只保存本次成功写入的人员，匹配旧人员仍须使用全局业务键查询。
            // 单位也按全局名字解析，不能过滤后把越域或重名单位误当作新顶级单位。
            // 字典下拉项预载:导入值必须命中,防止手填错别字批量入库
            Map<String, java.util.Set<String>> dictSets = new HashMap<String, java.util.Set<String>>();
            for (String dt : DICT_FIELDS.values()) {
                java.util.Set<String> s = new java.util.HashSet<String>();
                for (Map<String, Object> dr : jdbc.queryForList(
                        "SELECT dict_label FROM sys_dict WHERE dict_type=? AND enabled=1", dt)) {
                    String lb = String.valueOf(dr.get("dict_label")).trim();
                    if (!lb.isEmpty()) { s.add(lb); }
                }
                dictSets.put(dt, s);
            }
            // 列key → 中文列名(报错指明是哪一列)
            Map<String, String> colNames = new HashMap<String, String>();
            for (int i = 0; i < MAIN_KEYS.length; i++) { colNames.put(camelToSnake(MAIN_KEYS[i]), MAIN_HEADERS[i]); }
            int pAdded = 0, pUpdated = 0, pFailed = 0, cardWarn = 0;
            for (int r = 1; r <= mainSheet.getLastRowNum(); r++) {
                Row row = mainSheet.getRow(r);
                if (row == null) { continue; }
                try {
                    Map<String, String> vals = new HashMap<String, String>();
                    for (Map.Entry<Integer, String> e : colMap.entrySet()) {
                        String v = isDateColumn(e.getValue())
                                ? dates.read(row.getCell(e.getKey()), colNames.get(e.getValue()))
                                : getCell(row, e.getKey(), fmt);
                        vals.put(e.getValue(), v);
                    }
                    // 字典字段严格校验:不在下拉选项中的值该行拒入
                    boolean dictBad = false;
                    for (Map.Entry<String, String> de : DICT_FIELDS.entrySet()) {
                        String dv = vals.get(de.getKey());
                        if (dv == null || dv.trim().isEmpty()) { continue; }
                        if (!dictSets.get(de.getValue()).contains(dv.trim())) {
                            pFailed++;
                            errors.add("人员Sheet 第" + (r + 1) + "行: "
                                    + colNames.get(de.getKey()) + "「" + dv.trim() + "」不在下拉选项中,请先在数据字典中维护后再导入");
                            dictBad = true;
                            break;
                        }
                    }
                    if (dictBad) { continue; }
                    boolean emptyRow = true;
                    for (String value : vals.values()) {
                        if (value != null && !value.trim().isEmpty()) { emptyRow = false; break; }
                    }
                    if (emptyRow) { continue; }
                    validateMainRow(vals);
                    String name = vals.get("name");
                    String card = vals.get("id_card") == null ? "" : vals.get("id_card").trim().toUpperCase(java.util.Locale.ROOT);
                    vals.put("id_card", card);
                    if (!card.isEmpty() && card.length() != 18) {
                        cardWarn++;   // 证件位数异常不拦截,导入完成后提示
                    }
                    List<Long> existing = jdbc.queryForList("SELECT id FROM personnel WHERE UPPER(TRIM(id_card))=?", Long.class, card);
                    if (existing.size() > 1) throw new IllegalArgumentException("身份证号匹配多条人员，拒绝任选");
                    Long existingId = existing.isEmpty() ? null : existing.get(0);
                    if (existingId != null) DataScope.requirePerson(jdbc, existingId);
                    String deptName = vals.get("department_name").trim();
                    List<Long> departments = jdbc.queryForList("SELECT id FROM department WHERE TRIM(name)=?", Long.class, deptName);
                    if (departments.size() > 1) throw new IllegalArgumentException("单位名称匹配多条，请明确单位后重试");
                    Long deptId;
                    if (departments.isEmpty()) {
                        if (DataScope.restricted()) throw new ForbiddenException("单位不存在，子账号不能自动创建顶级单位");
                        jdbc.update("INSERT INTO department(name) VALUES(?)", deptName);
                        deptId = jdbc.queryForObject("SELECT id FROM department WHERE TRIM(name)=?", Long.class, deptName);
                    } else {
                        deptId = departments.get(0);
                    }
                    DataScope.requireDepartment(deptId);
                    // 实际在岗三列:只有列出现在文件(colMap)中才写入,缺列=旧模板,更新时完全保留旧值。
                    // 实际单位按名称全局匹配:非空必须唯一存在,绝不自动创建、不用路径猜;空=清0(写0)。
                    Long actualDeptId = null;
                    if (vals.containsKey("actual_department_name")) {
                        String actualName = vals.get("actual_department_name") == null ? "" : vals.get("actual_department_name").trim();
                        if (actualName.isEmpty()) {
                            actualDeptId = 0L;
                        } else {
                            List<Long> actualMatches = jdbc.queryForList("SELECT id FROM department WHERE TRIM(name)=?", Long.class, actualName);
                            if (actualMatches.size() != 1) {
                                throw new IllegalArgumentException("实际单位不存在或不唯一");
                            }
                            actualDeptId = actualMatches.get(0);
                        }
                    }
                    if (existingId != null) {
                        StringBuilder set = new StringBuilder();
                        List<Object> args = new ArrayList<Object>();
                        for (String col : MAIN_COLS) {
                            String v = vals.get(col);
                            if (v != null && !v.trim().isEmpty()) {
                                if (set.length() > 0) { set.append(","); }
                                set.append(col).append("=?");
                                args.add(v.trim());
                            }
                        }
                        if (deptId != null) {
                            if (set.length() > 0) { set.append(","); }
                            set.append("department_id=?");
                            args.add(deptId);
                        }
                        // 列存在但空 → 按空写入(清空);列缺失 → 不在 SET 中,旧值原样保留
                        if (actualDeptId != null) {
                            if (set.length() > 0) { set.append(","); }
                            set.append("actual_department_id=?");
                            args.add(actualDeptId);
                        }
                        if (set.length() > 0) {
                            set.append(", phone=COALESCE((SELECT phone_number FROM phone_device WHERE personnel_id=personnel.id" +
                                    " AND TRIM(COALESCE(phone_number,''))<>'' ORDER BY id DESC LIMIT 1),'')");
                            set.append(", updated_at=datetime('now','localtime')");
                            args.add(existingId);
                            jdbc.update("UPDATE personnel SET " + set + " WHERE id=?", args.toArray());
                        }
                        pUpdated++;
                    } else {
                        StringBuilder cols = new StringBuilder();
                        StringBuilder marks = new StringBuilder();
                        List<Object> args = new ArrayList<Object>();
                        for (String col : MAIN_COLS) {
                            String v = vals.get(col);
                            if (v != null && !v.trim().isEmpty()) {
                                if (cols.length() > 0) { cols.append(","); marks.append(","); }
                                cols.append(col); marks.append("?");
                                args.add(v.trim());
                            }
                        }
                        if (deptId != null) {
                            if (cols.length() > 0) { cols.append(","); marks.append(","); }
                            cols.append("department_id"); marks.append("?");
                            args.add(deptId);
                        }
                        if (actualDeptId != null) {
                            if (cols.length() > 0) { cols.append(","); marks.append(","); }
                            cols.append("actual_department_id"); marks.append("?");
                            args.add(actualDeptId);
                        }
                        jdbc.update("INSERT INTO personnel(" + cols + ") VALUES(" + marks + ")", args.toArray());
                        Long newId = jdbc.queryForObject("SELECT id FROM personnel WHERE id_card=?", Long.class, card);
                        if (!card.isEmpty()) { cardMap.put(card, newId); }
                        pAdded++;
                    }
                    if (existingId != null) cardMap.put(card, existingId);
                    if (!card.isEmpty()) { successfullyImportedCards.add(card); }
                } catch (Exception e) {
                    pFailed++;
                    errors.add("\u4eba\u5458Sheet \u7b2c" + (r + 1) + "\u884c: " + simplifyError(e.getMessage()));
                }
            }
            if (cardWarn > 0) {
                result.put("cardWarn", cardWarn);
            }
            result.put("added", pAdded);
            result.put("updated", pUpdated);
            result.put("failed", pFailed);
            int subTotal = 0;
            int subFailed = 0;
            for (String[] def : SHEETS) {
                Sheet sheet = wb.getSheet(def[0]);
                if (sheet == null || sheet.getRow(0) == null) { continue; }
                String sheetName = def[0];
                String table = def[1];
                String[] headers = def[2].split(",");
                String[] keys = def[3].split(",");
                // 按列头名定位(兼容历史无姓名列的旧文件)
                java.util.Map<String, Integer> hIdx = new HashMap<String, Integer>();
                Row hr = sheet.getRow(0);
                for (int h = 0; h < hr.getLastCellNum(); h++) {
                    String hv = getCell(hr, h, fmt).trim();
                    if (!hv.isEmpty()) { hIdx.put(hv, h); }
                }
                Integer cardCol = hIdx.get("身份证号");
                if (cardCol == null) { cardCol = 0; }
                int count = 0;
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) { continue; }
                    try {
                        String card = getCell(row, cardCol, fmt);
                        if (card.isEmpty()) { continue; }
                        Long resolved = ModuleExcelService.Integrity.person(jdbc, card, "");
                        // 即使有人伪造子表人员 ID，也必须可见且与身份证解析结果一致。
                        for (String idHeader : Arrays.asList("人员ID", "人员编号", "personnel_id")) {
                            Integer idCol = hIdx.get(idHeader);
                            String rawId = idCol == null ? "" : getCell(row, idCol, fmt);
                            if (!rawId.isEmpty()) {
                                Long supplied = Long.valueOf(rawId);
                                DataScope.requirePerson(jdbc, supplied);
                                if (!supplied.equals(resolved)) throw new IllegalArgumentException("子表人员ID与身份证号不一致");
                            }
                        }
                        Long pid = cardMap.get(card.toUpperCase(java.util.Locale.ROOT));
                        if (pid == null || !pid.equals(resolved) || !successfullyImportedCards.contains(card.toUpperCase(java.util.Locale.ROOT))) {
                            subFailed++;
                            errors.add(sheetName + " \u7b2c" + (r + 1) + "\u884c: \u8eab\u4efd\u8bc1\u53f7\u300c" + card + "\u300d\u672a\u627e\u5230\u5bf9\u5e94\u4eba\u5458");
                            continue;
                        }
                        StringBuilder cols = new StringBuilder("personnel_id");
                        StringBuilder marks = new StringBuilder("?");
                        List<Object> args = new ArrayList<Object>();
                        args.add(pid);
                        boolean hasData = false;
                        Map<String, String> childValues = new LinkedHashMap<String, String>();
                        for (int c = 1; c < keys.length; c++) {
                            if (keys[c].startsWith("__")) { continue; }   // 姓名列仅导出展示,不入库
                            Integer colAt = hIdx.get(headers[c]);
                            String v = colAt == null ? "" : isDateColumn(keys[c])
                                    ? dates.read(row.getCell(colAt), headers[c])
                                    : getCell(row, colAt, fmt);
                            if (!v.isEmpty()) { hasData = true; }
                            childValues.put(keys[c], v);
                            cols.append(",").append(keys[c]);
                            marks.append(",?");
                            args.add(v);
                        }
                        if (!hasData) { continue; }
                        validateChildRow(sheetName, childValues);
                        if (isDuplicateChild(table, pid, childValues)) { continue; }
                        jdbc.update("INSERT INTO " + table + "(" + cols + ") VALUES(" + marks + ")", args.toArray());
                        count++;
                    } catch (Exception e) {
                        subFailed++;
                        errors.add(sheetName + " \u7b2c" + (r + 1) + "\u884c: " + simplifyError(e.getMessage()));
                    }
                }
                result.put(sheetName, count);
                subTotal += count;
            }
            result.put("subTotal", subTotal);
            result.put("subFailed", subFailed);
            if (!errors.isEmpty()) { result.put("errors", errors); }
        } catch (Exception e) {
            result.put("error", "\u6587\u4ef6\u89e3\u6790\u5931\u8d25:" + simplifyError(e.getMessage())
                    + "(\u8bf7\u786e\u8ba4\u662f .xlsx \u683c\u5f0f\u4e14\u4f7f\u7528\u7cfb\u7edf\u5bfc\u51fa\u7684\u6a21\u677f)");
        }
        return result;
    }

    /* ================= 内部 ================= */

    private void validateMainRow(Map<String, String> values) {
        for (String key : REQUIRED_MAIN_COLS) {
            String value = values.get(key);
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(REQUIRED_MAIN_LABELS.get(key) + "不能为空");
            }
        }
        String card = values.get("id_card").trim().toUpperCase(java.util.Locale.ROOT);
        if (!card.matches("[0-9]{17}[0-9X]|[0-9]{15}")) {
            throw new IllegalArgumentException("身份证号格式不正确");
        }
        String birth = card.length() == 18 ? card.substring(6, 14) : "19" + card.substring(6, 12);
        try {
            java.time.LocalDate birthDate = java.time.LocalDate.parse(birth, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            if (birthDate.isAfter(java.time.LocalDate.now())) { throw new IllegalArgumentException("身份证出生日期不能晚于今天"); }
        } catch (java.time.DateTimeException ex) {
            throw new IllegalArgumentException("身份证出生日期不正确");
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (isDateColumn(entry.getKey()) && entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                validateDate(entry.getValue(), REQUIRED_MAIN_LABELS.containsKey(entry.getKey())
                        ? REQUIRED_MAIN_LABELS.get(entry.getKey()) : entry.getKey());
            }
        }
    }

    private boolean isDateColumn(String key) {
        return "birthday".equals(key) || key.endsWith("_date");
    }

    private void validateDate(String value, String label) {
        try {
            java.time.LocalDate.parse(value, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (java.time.DateTimeException ex) {
            throw new IllegalArgumentException(label + "日期格式不正确,请使用 yyyy-MM-dd");
        }
    }

    private void validateChildRow(String sheet, Map<String, String> values) {
        String[] required;
        if ("任职经历".equals(sheet)) required = new String[]{"start_date", "place", "post"};
        else if ("奖惩情况".equals(sheet)) required = new String[]{"kind", "occur_date", "reason", "content"};
        else if ("家庭成员".equals(sheet)) required = new String[]{"relationship", "name"};
        else if ("驾驶证".equals(sheet)) required = new String[]{"license_type", "license_no"};
        else if ("个人卡号".equals(sheet)) required = new String[]{"card_type", "card_no"};
        else if ("私家车".equals(sheet)) required = new String[]{"plate_no"};
        else required = new String[]{"address"};
        for (String key : required) {
            if (values.get(key) == null || values.get(key).trim().isEmpty()) {
                throw new IllegalArgumentException("子表必填字段「" + key + "」不能为空");
            }
        }
        if ("奖惩情况".equals(sheet) && !"奖励".equals(values.get("kind")) && !"处分".equals(values.get("kind"))) {
            throw new IllegalArgumentException("类型只能是 奖励 或 处分");
        }
        if ("家庭成员".equals(sheet)) {
            String idCard = values.get("id_card");
            if (idCard != null && !idCard.isEmpty() && !idCard.matches("[0-9]{17}[0-9Xx]|[0-9]{15}")) {
                throw new IllegalArgumentException("身份证号格式不正确(15 或 18 位)");
            }
        }
    }

    private boolean isDuplicateChild(String table, Long personnelId, Map<String, String> values) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(table).append(" WHERE personnel_id=?");
        List<Object> args = new ArrayList<Object>();
        args.add(personnelId);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() == null || entry.getValue().trim().isEmpty()) { continue; }
            sql.append(" AND ").append(entry.getKey()).append("=?");
            args.add(entry.getValue());
        }
        return jdbc.queryForObject(sql.toString(), Integer.class, args.toArray()) > 0;
    }

    private List<com.szjx.personnel.server.model.Personnel> readMainSheet(Sheet sheet, DataFormatter fmt) {
        List<com.szjx.personnel.server.model.Personnel> list = new ArrayList<com.szjx.personnel.server.model.Personnel>();
        ExcelDates dates = new ExcelDates(sheet.getWorkbook());
        Row head = sheet.getRow(0);
        if (head == null) {
            return list;
        }
        Map<Integer, String> colMap = new HashMap<Integer, String>();
        for (int i = 0; i < head.getLastCellNum(); i++) {
            String h = fmt.formatCellValue(head.getCell(i)).trim();
            for (int k = 0; k < MAIN_HEADERS.length; k++) {
                if (h.equals(MAIN_HEADERS[k])) {
                    colMap.put(i, MAIN_KEYS[k]);
                }
            }
            // 旧版表头(籍贯/家庭住址)兼容:新列名没对上时按别名映射
            if (!colMap.containsKey(i) && HEADER_ALIASES.containsKey(h)) {
                colMap.put(i, HEADER_ALIASES.get(h));
            }
        }
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            com.szjx.personnel.server.model.Personnel p = new com.szjx.personnel.server.model.Personnel();
            for (Map.Entry<Integer, String> e : colMap.entrySet()) {
                String v = isDateColumn(e.getValue())
                        ? dates.read(row.getCell(e.getKey()), e.getValue())
                        : getCell(row, e.getKey(), fmt);
                setField(p, e.getValue(), v);
            }
            if (p.getName() != null && !p.getName().trim().isEmpty()) {
                list.add(p);
            }
        }
        return list;
    }

    /** 人员字典字段:key → 字典类型(导入时自动登记新值) */
    private static final java.util.Map<String, String> DICT_FIELDS = new java.util.HashMap<String, String>();
    static {
        DICT_FIELDS.put("category", "personnel_category");
        DICT_FIELDS.put("rank", "rank");
        DICT_FIELDS.put("status", "personnel_status");
        DICT_FIELDS.put("education", "education");
        DICT_FIELDS.put("marital_status", "marital_status");
        DICT_FIELDS.put("nation", "nation");
        DICT_FIELDS.put("blood_type", "blood_type");
        DICT_FIELDS.put("political_status", "political_status");
        DICT_FIELDS.put("follow_army_status", "follow_army_status");
    }

    private void setField(com.szjx.personnel.server.model.Personnel p, String key, String val) {
        if (val == null || val.isEmpty()) {
            return;
        }
        if ("name".equals(key)) { p.setName(val); }
        else if ("gender".equals(key)) { p.setGender(val); }
        else if ("id_card".equals(key)) { p.setIdCard(val); }
        else if ("military_id_no".equals(key)) { p.setMilitaryIdNo(val); }
        else if ("birthday".equals(key)) { p.setBirthday(val); }
        else if ("phone".equals(key)) { p.setPhone(val); }
        else if ("category".equals(key)) { p.setCategory(val); }
        else if ("rank".equals(key)) { p.setRank(val); }
        else if ("post".equals(key)) { p.setPost(val); }
        else if ("status".equals(key)) { p.setStatus(val); }
        else if ("entry_date".equals(key)) { p.setEntryDate(val); }
        else if ("nation".equals(key)) { p.setNation(val); }
        else if ("native_place".equals(key)) { p.setNativePlace(val); }
        else if ("enlist_place".equals(key)) { p.setEnlistPlace(val); }
        else if ("blood_type".equals(key)) { p.setBloodType(val); }
        else if ("political_status".equals(key)) { p.setPoliticalStatus(val); }
        else if ("party_join_date".equals(key)) { p.setPartyJoinDate(val); }
        else if ("party_org_location".equals(key)) { p.setPartyOrgLocation(val); }
        else if ("party_post".equals(key)) { p.setPartyPost(val); }
        else if ("party_apply_date".equals(key)) { p.setPartyApplyDate(val); }
        else if ("party_activist_date".equals(key)) { p.setPartyActivistDate(val); }
        else if ("party_candidate_date".equals(key)) { p.setPartyCandidateDate(val); }
        else if ("party_full_date".equals(key)) { p.setPartyFullDate(val); }
        else if ("marital_status".equals(key)) { p.setMaritalStatus(val); }
        else if ("marriage_date".equals(key)) { p.setMarriageDate(val); }
        else if ("divorce_date".equals(key)) { p.setDivorceDate(val); }
        else if ("follow_army_date".equals(key)) { p.setFollowArmyDate(val); }
        else if ("follow_army_status".equals(key)) { p.setFollowArmyStatus(val); }
        else if ("education".equals(key)) { p.setEducation(val); }
        else if ("only_child".equals(key)) { p.setOnlyChild(val); }
        else if ("height".equals(key)) { try { p.setHeight(Double.valueOf(val)); } catch (Exception ignore) {} }
        else if ("weight".equals(key)) { try { p.setWeight(Double.valueOf(val)); } catch (Exception ignore) {} }
        else if ("address".equals(key)) { p.setAddress(val); }
        else if ("remark".equals(key)) { p.setRemark(val); }
    }

    private String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char ch : camel.toCharArray()) {
            if (Character.isUpperCase(ch)) { sb.append('_').append(Character.toLowerCase(ch)); }
            else { sb.append(ch); }
        }
        return sb.toString();
    }

    private String simplifyError(String msg) {
        if (msg == null) { return "未知错误"; }
        if (msg.contains("UNIQUE constraint")) { return "数据重复(身份证号已存在)"; }
        if (msg.contains("NOT NULL")) { return "必填字段为空"; }
        if (msg.length() > 120) { return msg.substring(0, 120) + "..."; }
        return msg;
    }

    private String getCell(Row row, int col, DataFormatter fmt) {
        Cell c = row.getCell(col);
        if (c == null) {
            return "";
        }
        // Excel 日期是数值单元格,DataFormatter 会按单元格样式/区域渲染成 01/03/23 之类;
        // 日期单元格统一取日期值格式化为 yyyy-MM-dd,与系统展示和军龄计算一致
        if (c.getCellType() == CellType.NUMERIC && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(c)) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").format(c.getDateCellValue());
        }
        return fmt.formatCellValue(c).trim();
    }

    private void writeHeader(Sheet sheet, String[] headers, CellStyle style) {
        Row head = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = head.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(style);
        }
    }

    private CellStyle headStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private byte[] bytes(Workbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }
}
