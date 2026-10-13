package com.szjx.personnel.server.service;

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
            "身份证号,开始日期,结束日期,任职地点,担任职务,备注",
            "idcard,start_date,end_date,place,post,remark"},
        {"奖惩情况", "reward_punish",
            "身份证号,类型,日期,事由,内容,备注",
            "idcard,kind,occur_date,reason,content,remark"},
        {"家庭成员", "family_member",
            "身份证号,关系,姓名,成员身份证号,性别,出生日期,政治面貌,工作单位及职务,联系电话,备注",
            "idcard,relationship,name,id_card,gender,birth_date,political_status,work_info,phone,remark"},
        {"驾驶证", "driver_license",
            "身份证号,类型,证号,准驾车型,初领日期,有效期至,发证机关,状态,年审日期,备注",
            "idcard,license_type,license_no,vehicle_class,issue_date,expiry_date,issuing_auth,license_status,review_date,remark"},
        {"个人卡号", "personal_card",
            "身份证号,卡类型,卡号,开户行,开户支行,开户日期,卡状态,备注",
            "idcard,card_type,card_no,bank_name,branch_name,open_date,card_status,remark"},
        {"私家车", "private_vehicle",
            "身份证号,车牌号,品牌型号,颜色,购置日期,价格(万),保险到期,状态,登记在谁名下,备注",
            "idcard,plate_no,brand,color,purchase_date,price,insurance_date,vehicle_status,owner_name,remark"},
        {"住房情况", "housing_info",
            "身份证号,类型,地址,面积,性质,房产证,贷款,购置时间,产权人,备注",
            "idcard,housing_type,address,area,house_nature,has_cert,loan_status,purchase_date,owner_name,remark"},
    };

    /** 人员主表列(与 ExcelUtils.exportXlsx 对应但简化,此处自建) */
    private static final String[] MAIN_HEADERS = {
        "姓名", "性别", "身份证号", "出生日期", "手机号", "部门", "人员类别", "军衔/职级", "职务/岗位",
        "状态", "入伍/入职日期", "民族", "籍贯", "血型", "政治面貌",
        "入党/入团日期", "组织关系所在地", "党内职务",
        "提交申请书时间", "积极分子时间", "预备党员时间", "转正时间",
        "婚姻状况", "结婚日期", "离婚日期", "随军时间", "随军条件",
        "学历", "是否独生子女", "身高(cm)", "体重(kg)", "家庭住址"
    };
    private static final String[] MAIN_KEYS = {
        "name", "gender", "id_card", "birthday", "phone", "department_name", "category", "rank", "post",
        "status", "entry_date", "nation", "native_place", "blood_type", "political_status",
        "party_join_date", "party_org_location", "party_post",
        "party_apply_date", "party_activist_date", "party_candidate_date", "party_full_date",
        "marital_status", "marriage_date", "divorce_date", "follow_army_date", "follow_army_status",
        "education", "only_child", "height", "weight", "address"
    };

    private static final String[] MAIN_COLS = {
        "name", "gender", "id_card", "birthday", "phone", "category", "rank", "post",
        "status", "entry_date", "nation", "native_place", "blood_type", "political_status",
        "party_join_date", "party_org_location", "party_post",
        "party_apply_date", "party_activist_date", "party_candidate_date", "party_full_date",
        "marital_status", "marriage_date", "divorce_date", "follow_army_date", "follow_army_status",
        "education", "only_child", "height", "weight", "address"
    };

    public PersonnelFullExcel(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ================= 导出 ================= */

    public byte[] export(List<Long> ids) throws Exception {
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
                "SELECT p.*, d.name AS department_name FROM personnel p " +
                "LEFT JOIN department d ON p.department_id=d.id WHERE p.id IN (" + ph + ") ORDER BY p.id",
                ids.toArray());
        Map<Long, String> idCardMap = new HashMap<Long, String>();
        int r = 1;
        for (Map<String, Object> p : people) {
            Row row = main.createRow(r++);
            for (int c = 0; c < MAIN_KEYS.length; c++) {
                Object v = p.get(MAIN_KEYS[c]);
                row.createCell(c).setCellValue(v == null ? "" : String.valueOf(v));
            }
            idCardMap.put(((Number) p.get("id")).longValue(),
                    String.valueOf(p.get("id_card") == null ? "" : p.get("id_card")));
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
                    for (int c = 1; c < keys.length; c++) {
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
        // 示例行
        Row example = main.createRow(1);
        String[] ex = {"张三", "男", "330102199001011234", "1990-01-01", "13800000001", "XX部队XX营",
                "上尉", "参谋", "在职", "2010-06-01", "汉族", "浙江杭州", "A", "党员",
                "2012-06-01", "XX部队党委", "支部委员",
                "2011-03-01", "2011-12-01", "2012-06-01", "2013-06-01",
                "已婚", "2015-01-01", "", "", "",
                "本科", "否", "175", "70", "杭州市XX路XX号"};
        for (int i = 0; i < ex.length; i++) {
            example.createCell(i).setCellValue(ex[i]);
        }
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
        try {
            Workbook wb = WorkbookFactory.create(in);
            DataFormatter fmt = new DataFormatter();
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
                    if (h.equals(MAIN_HEADERS[k])) { colMap.put(i, camelToSnake(MAIN_KEYS[k])); }
                }
            }
            Map<String, Long> cardMap = new HashMap<String, Long>();
            for (Map<String, Object> row : jdbc.queryForList(
                    "SELECT id, id_card FROM personnel WHERE id_card IS NOT NULL AND id_card != ''")) {
                String card = String.valueOf(row.get("id_card")).trim().toUpperCase();
                if (!card.isEmpty()) { cardMap.put(card, ((Number) row.get("id")).longValue()); }
            }
            Map<String, Long> deptMap = new HashMap<String, Long>();
            for (Map<String, Object> d : jdbc.queryForList("SELECT id, name FROM department")) {
                deptMap.put(String.valueOf(d.get("name")).trim(), ((Number) d.get("id")).longValue());
            }
            int pAdded = 0, pUpdated = 0, pFailed = 0, cardWarn = 0;
            for (int r = 1; r <= mainSheet.getLastRowNum(); r++) {
                Row row = mainSheet.getRow(r);
                if (row == null) { continue; }
                try {
                    Map<String, String> vals = new HashMap<String, String>();
                    for (Map.Entry<Integer, String> e : colMap.entrySet()) {
                        vals.put(e.getValue(), getCell(row, e.getKey(), fmt));
                    }
                    // 字典字段自动登记新值(民族/军衔/类别等不在字典中时收编)
                    for (String dk : DICT_FIELDS.keySet()) {
                        ensureDictValue(dk, vals.get(dk));
                    }
                    String name = vals.get("name");
                    if (name == null || name.trim().isEmpty()) { continue; }
                    String card = vals.get("id_card") == null ? "" : vals.get("id_card").trim().toUpperCase();
                    if (!card.isEmpty() && card.length() != 18) {
                        cardWarn++;   // 证件位数异常不拦截,导入完成后提示
                    }
                    Long deptId = null;
                    String deptName = vals.get("department_name");
                    if (deptName != null && !deptName.trim().isEmpty()) {
                        deptId = deptMap.get(deptName.trim());
                        if (deptId == null) {
                            jdbc.update("INSERT INTO department(name) VALUES(?)", deptName.trim());
                            Long newId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
                            deptMap.put(deptName.trim(), newId);
                            deptId = newId;
                        }
                    }
                    Long existingId = card.isEmpty() ? null : cardMap.get(card);
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
                        if (set.length() > 0) {
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
                        jdbc.update("INSERT INTO personnel(" + cols + ") VALUES(" + marks + ")", args.toArray());
                        Long newId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
                        if (!card.isEmpty()) { cardMap.put(card, newId); }
                        pAdded++;
                    }
                } catch (Exception e) {
                    pFailed++;
                    if (errors.size() < 10) { errors.add("\u4eba\u5458Sheet \u7b2c" + (r + 1) + "\u884c: " + simplifyError(e.getMessage())); }
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
                String[] keys = def[3].split(",");
                int count = 0;
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) { continue; }
                    try {
                        String card = getCell(row, 0, fmt);
                        if (card.isEmpty()) { continue; }
                        Long pid = cardMap.get(card.toUpperCase());
                        if (pid == null) {
                            subFailed++;
                            if (errors.size() < 10) {
                                errors.add(sheetName + " \u7b2c" + (r + 1) + "\u884c: \u8eab\u4efd\u8bc1\u53f7\u300c" + card + "\u300d\u672a\u627e\u5230\u5bf9\u5e94\u4eba\u5458");
                            }
                            continue;
                        }
                        StringBuilder cols = new StringBuilder("personnel_id");
                        StringBuilder marks = new StringBuilder("?");
                        List<Object> args = new ArrayList<Object>();
                        args.add(pid);
                        boolean hasData = false;
                        for (int c = 1; c < keys.length; c++) {
                            String v = getCell(row, c, fmt);
                            if (!v.isEmpty()) { hasData = true; }
                            cols.append(",").append(keys[c]);
                            marks.append(",?");
                            args.add(v);
                        }
                        if (!hasData) { continue; }
                        jdbc.update("INSERT INTO " + table + "(" + cols + ") VALUES(" + marks + ")", args.toArray());
                        count++;
                    } catch (Exception e) {
                        subFailed++;
                        if (errors.size() < 10) { errors.add(sheetName + " \u7b2c" + (r + 1) + "\u884c: " + simplifyError(e.getMessage())); }
                    }
                }
                result.put(sheetName, count);
                subTotal += count;
            }
            result.put("subTotal", subTotal);
            result.put("subFailed", subFailed);
            if (!errors.isEmpty()) { result.put("errors", errors); }
            wb.close();
        } catch (Exception e) {
            result.put("error", "\u6587\u4ef6\u89e3\u6790\u5931\u8d25:" + simplifyError(e.getMessage())
                    + "(\u8bf7\u786e\u8ba4\u662f .xlsx \u683c\u5f0f\u4e14\u4f7f\u7528\u7cfb\u7edf\u5bfc\u51fa\u7684\u6a21\u677f)");
        }
        return result;
    }

    /* ================= 内部 ================= */

    private List<com.szjx.personnel.server.model.Personnel> readMainSheet(Sheet sheet, DataFormatter fmt) {
        List<com.szjx.personnel.server.model.Personnel> list = new ArrayList<com.szjx.personnel.server.model.Personnel>();
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
        }
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            com.szjx.personnel.server.model.Personnel p = new com.szjx.personnel.server.model.Personnel();
            for (Map.Entry<Integer, String> e : colMap.entrySet()) {
                String v = getCell(row, e.getKey(), fmt);
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

    private void ensureDictValue(String key, String val) {
        String t = DICT_FIELDS.get(key);
        if (t == null || val == null || val.trim().isEmpty()) {
            return;
        }
        jdbc.update("INSERT OR IGNORE INTO sys_dict(dict_type, dict_label, sort, enabled, remark, parent_id) " +
                "VALUES(?,?,90,1,'',0)", t, val.trim());
    }

    private void setField(com.szjx.personnel.server.model.Personnel p, String key, String val) {
        ensureDictValue(key, val);
        if (val == null || val.isEmpty()) {
            return;
        }
        if ("name".equals(key)) { p.setName(val); }
        else if ("gender".equals(key)) { p.setGender(val); }
        else if ("id_card".equals(key)) { p.setIdCard(val); }
        else if ("birthday".equals(key)) { p.setBirthday(val); }
        else if ("phone".equals(key)) { p.setPhone(val); }
        else if ("category".equals(key)) { p.setCategory(val); }
        else if ("rank".equals(key)) { p.setRank(val); }
        else if ("post".equals(key)) { p.setPost(val); }
        else if ("status".equals(key)) { p.setStatus(val); }
        else if ("entry_date".equals(key)) { p.setEntryDate(val); }
        else if ("nation".equals(key)) { p.setNation(val); }
        else if ("native_place".equals(key)) { p.setNativePlace(val); }
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
        return c == null ? "" : fmt.formatCellValue(c).trim();
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
