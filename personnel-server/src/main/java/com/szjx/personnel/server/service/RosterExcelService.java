package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.util.PersonnelExtraFilters;
import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人员花名册 Excel 导出:列由用户勾选(基本档案字段,不含任职经历/奖惩等子表)。
 * 按单位(含上级全名)分组、组行标注人数;横版 A4、一页宽缩放、表头每页重复;
 * 公文字体:标题=方正小标宋简体,表头=黑体,内容=仿宋_GB2312。
 */
@Service
public class RosterExcelService {

    /** 可选字段:key → {表头, 列宽(字符)};顺序即默认顺序 */
    private static final LinkedHashMap<String, String[]> FIELDS = new LinkedHashMap<String, String[]>();

    static {
        FIELDS.put("name", new String[]{"姓名", "10"});
        FIELDS.put("gender", new String[]{"性别", "6"});
        FIELDS.put("idCard", new String[]{"身份证号", "22"});
        FIELDS.put("militaryIdNo", new String[]{"军人证件号", "22"});
        FIELDS.put("departmentPath", new String[]{"单位完整路径", "36"});
        FIELDS.put("department", new String[]{"编制单位", "16"});
        FIELDS.put("locationPoint", new String[]{"所在点位", "10"});
        FIELDS.put("category", new String[]{"人员类别", "10"});
        FIELDS.put("rank", new String[]{"军衔/职级", "10"});
        FIELDS.put("post", new String[]{"职务", "12"});
        FIELDS.put("status", new String[]{"状态", "8"});
        FIELDS.put("phone", new String[]{"手机号", "14"});
        FIELDS.put("birthday", new String[]{"出生日期", "16"});
        FIELDS.put("age", new String[]{"年龄", "6"});
        FIELDS.put("nation", new String[]{"民族", "8"});
        FIELDS.put("nativePlace", new String[]{"籍贯所在地", "18"});
        FIELDS.put("enlistPlace", new String[]{"入伍所在地", "18"});
        FIELDS.put("politicalStatus", new String[]{"政治面貌", "10"});
        FIELDS.put("partyJoinDate", new String[]{"入党/入团时间", "14"});
        FIELDS.put("partyFullDate", new String[]{"转正时间", "14"});
        FIELDS.put("partyApplyDate", new String[]{"提交申请书时间", "15"});
        FIELDS.put("partyActivistDate", new String[]{"积极分子时间", "15"});
        FIELDS.put("partyCandidateDate", new String[]{"预备党员时间", "15"});
        FIELDS.put("partyOrgLocation", new String[]{"组织关系所在地", "18"});
        FIELDS.put("partyPost", new String[]{"党内职务", "12"});
        FIELDS.put("education", new String[]{"学历", "9"});
        FIELDS.put("eduSchool", new String[]{"毕业院校", "20"});
        FIELDS.put("eduMajor", new String[]{"所学专业", "16"});
        FIELDS.put("eduDegree", new String[]{"学位", "10"});
        FIELDS.put("eduForm", new String[]{"学历形式", "11"});
        FIELDS.put("eduCertNo", new String[]{"毕业证号", "20"});
        FIELDS.put("eduGraduate", new String[]{"毕业时间", "14"});
        FIELDS.put("maritalStatus", new String[]{"婚姻状况", "9"});
        FIELDS.put("marriageDate", new String[]{"结婚日期", "14"});
        FIELDS.put("spouseName", new String[]{"配偶姓名", "10"});
        FIELDS.put("spouseWork", new String[]{"配偶工作单位", "18"});
        FIELDS.put("spouseJob", new String[]{"配偶职业", "12"});
        FIELDS.put("spousePhone", new String[]{"配偶电话", "14"});
        FIELDS.put("followArmyDate", new String[]{"随军时间", "14"});
        FIELDS.put("entryDate", new String[]{"入伍日期", "16"});
        FIELDS.put("serviceYears", new String[]{"军龄", "7"});
        FIELDS.put("bloodType", new String[]{"血型", "6"});
        FIELDS.put("height", new String[]{"身高(cm)", "8"});
        FIELDS.put("weight", new String[]{"体重(kg)", "8"});
        FIELDS.put("onlyChild", new String[]{"独生子女", "8"});
        FIELDS.put("address", new String[]{"详细地址", "28"});
        FIELDS.put("driverLicense", new String[]{"驾驶证", "60"});
        FIELDS.put("hasDriverLicense", new String[]{"有无驾驶证", "14"});
        FIELDS.put("driverLicenseType", new String[]{"驾驶证类型", "18"});
        FIELDS.put("driverLicenseNo", new String[]{"驾驶证号", "26"});
        FIELDS.put("driverVehicleClass", new String[]{"准驾车型", "18"});
        FIELDS.put("driverIssueDate", new String[]{"驾驶证初领日期", "24"});
        FIELDS.put("driverExpiryDate", new String[]{"驾驶证有效期至", "24"});
        FIELDS.put("driverLicenseStatus", new String[]{"驾驶证状态", "18"});
        FIELDS.put("driverReviewDate", new String[]{"驾驶证年审日期", "24"});
        FIELDS.put("driverIssuingAuth", new String[]{"驾驶证发证机关", "32"});
        FIELDS.put("actualUnit", new String[]{"实际单位", "24"});
    }

    private static final List<String> DRIVER_FIELDS = Arrays.asList(
            "driverLicense", "hasDriverLicense", "driverLicenseType", "driverLicenseNo", "driverVehicleClass",
            "driverIssueDate", "driverExpiryDate", "driverLicenseStatus", "driverReviewDate", "driverIssuingAuth");
    private static final int DRIVER_BATCH_SIZE = 500;

    /** 未指定/全部非法时的默认列 */
    private static final List<String> DEFAULT_FIELDS = Arrays.asList(
            "name", "gender", "idCard", "department", "locationPoint", "rank", "post", "status", "entryDate", "phone");

    private final PersonnelService personnelService;
    private final DepartmentService departmentService;
    private final JdbcTemplate jdbc;

    public RosterExcelService(PersonnelService personnelService, DepartmentService departmentService) {
        this(personnelService, departmentService, null);
    }

    @Autowired
    public RosterExcelService(PersonnelService personnelService, DepartmentService departmentService, JdbcTemplate jdbc) {
        this.personnelService = personnelService;
        this.departmentService = departmentService;
        this.jdbc = jdbc;
    }

    private void requireDriverJdbc() {
        if (jdbc == null) throw new IllegalStateException("驾驶证字段或筛选(驾驶证/实际在岗)需要注入 JdbcTemplate，无法导出数据");
    }

    public List<Map<String, String>> fieldOptions() {
        List<Map<String, String>> out = new ArrayList<Map<String, String>>();
        for (Map.Entry<String, String[]> e : FIELDS.entrySet()) {
            Map<String, String> item = new HashMap<String, String>();
            item.put("key", e.getKey());
            item.put("label", e.getValue()[0]);
            out.add(item);
        }
        return out;
    }

    public byte[] build(Map<String, String> q) throws Exception {
        List<String> keys = resolveFields(q.get("fields"));
        boolean driverFieldsSelected = keys.stream().anyMatch(DRIVER_FIELDS::contains);
        PersonnelExtraFilters extras = PersonnelExtraFilters.fromQuery(q);
        if (driverFieldsSelected || !extras.isEmpty()) requireDriverJdbc();
        List<String[]> cols = new ArrayList<String[]>();
        for (String k : keys) {
            String[] meta = FIELDS.get(k);
            cols.add(new String[]{k, meta[0], meta[1]});
        }

        // 人员筛选(与列表接口同参数;departmentId 含下级展开)
        Long departmentId = parseLong(q.get("departmentId"));
        List<Personnel> rows = personnelService.query(
                trim(q.get("keyword")), trim(q.get("name")), trim(q.get("idCard")), null,
                trim(q.get("status")), trim(q.get("gender")), trim(q.get("politicalStatus")),
                trim(q.get("category")), trim(q.get("rank")), trim(q.get("post")),
                trim(q.get("nativePlace")), trim(q.get("enlistPlace")), trim(q.get("education")),
                trim(q.get("maritalStatus")), trim(q.get("nation")), trim(q.get("bloodType")),
                trim(q.get("onlyChild")), trim(q.get("locationPoint")),
                trim(q.get("entryDateStart")), trim(q.get("entryDateEnd")), 1, 1000000);

        // 查询层也有范围限制，此处再收窄以保护导出及其统计，兼容独立调用/测试替身。
        if (DataScope.restricted()) rows = rows.stream().filter(p -> DataScope.allowsDepartment(p.getDepartmentId()))
                .collect(java.util.stream.Collectors.toList());
        List<Department> tree = departmentService.tree();
        // 展平单位树(与点名册一致的 parent/seq 语义)
        final Map<String, String[]> flat = new LinkedHashMap<String, String[]>();
        final int[] seqHolder = {0};
        flatten(tree, "", flat, seqHolder);
        if (departmentId != null && departmentId > 0) {
            java.util.Set<Long> allowed = new java.util.HashSet<Long>();
            collectSubtree(tree, departmentId, false, allowed);
            List<Personnel> kept = new ArrayList<Personnel>();
            for (Personnel p : rows) {
                if (p.getDepartmentId() != null && allowed.contains(p.getDepartmentId())) {
                    kept.add(p);
                }
            }
            rows = kept;
        }

        // 保留旧查询签名;共享筛选(驾驶证+实际在岗)一次收窄最终权限/单位候选,不改变原序;空候选零查询。
        if (!extras.isEmpty() && !rows.isEmpty()) rows = extras.retainMatching(jdbc, rows);
        Map<Long, List<Map<String, Object>>> licenses = driverFieldsSelected
                ? loadDriverLicenses(rows) : java.util.Collections.emptyMap();

        // 按单位全名分组,组序按单位树顺序
        LinkedHashMap<String, List<Personnel>> groups = new LinkedHashMap<String, List<Personnel>>();
        final Map<String, Integer> groupSeq = new HashMap<String, Integer>();
        for (Personnel p : rows) {
            String[] d = p.getDepartmentId() == null ? null : flat.get(String.valueOf(p.getDepartmentId()));
            String group;
            int seq = Integer.MAX_VALUE;
            if (d == null) {
                group = emptyAs(p.getDepartmentName(), "未分配单位");
            } else {
                seq = Integer.parseInt(d[2]);
                group = d[1].isEmpty() ? d[0] : d[1] + d[0];
            }
            List<Personnel> list = groups.get(group);
            if (list == null) {
                list = new ArrayList<Personnel>();
                groups.put(group, list);
                groupSeq.put(group, seq);
            } else if (seq < groupSeq.get(group)) {
                groupSeq.put(group, seq);
            }
            list.add(p);
        }
        List<Map.Entry<String, List<Personnel>>> ordered = new ArrayList<Map.Entry<String, List<Personnel>>>(groups.entrySet());
        ordered.sort((a, b) -> Integer.compare(groupSeq.get(a.getKey()), groupSeq.get(b.getKey())));

        // ================= 工作簿 =================
        Workbook wb = new XSSFWorkbook();
        try {
            Sheet sh = wb.createSheet("花名册");
            int totalCols = 1 + cols.size(); // 序号 + 勾选字段
            sh.setColumnWidth(0, 6 * 256);
            for (int i = 0; i < cols.size(); i++) {
                sh.setColumnWidth(1 + i, Integer.parseInt(cols.get(i)[2]) * 256);
            }
            sh.getPrintSetup().setLandscape(true);
            sh.getPrintSetup().setPaperSize((short) 9); // A4
            sh.setFitToPage(true);
            sh.getPrintSetup().setFitWidth((short) 1);
            sh.getPrintSetup().setFitHeight((short) 0); // 一页宽,自然分页高
            sh.setMargin(Sheet.LeftMargin, 0.25);
            sh.setMargin(Sheet.RightMargin, 0.25);
            sh.setMargin(Sheet.TopMargin, 0.35);
            sh.setMargin(Sheet.BottomMargin, 0.35);

            CellStyle titleStyle = style(wb, "方正小标宋简体", 18, false, false, false);
            CellStyle subStyle = style(wb, "仿宋_GB2312", 12, false, false, false);
            CellStyle headStyle = style(wb, "黑体", 11, false, false, true);
            CellStyle nameStyle = style(wb, "仿宋_GB2312", 12, false, false, true);
            CellStyle driverStyle = style(wb, "仿宋_GB2312", 12, false, true, true);
            CellStyle footStyle = style(wb, "仿宋_GB2312", 12, false, false, false);

            int male = 0;
            for (Personnel p : rows) {
                if ("男".equals(p.getGender())) {
                    male++;
                }
            }

            int r = 0;
            merge(sh, r, 0, r, totalCols - 1, titleStyle, "人员花名册");
            sh.getRow(r).setHeightInPoints(30);
            r++;
            LocalDate today = LocalDate.now();
            merge(sh, r, 0, r, totalCols - 1, subStyle,
                    "日期:" + today.getYear() + "年" + today.getMonthValue() + "月" + today.getDayOfMonth() + "日"
                            + "    合计:" + rows.size() + "人(男" + male + "/女" + (rows.size() - male) + ")");
            r++;
            r++;

            // 表头(每页重复)
            int headRow = r;
            Row hr = ensureRow(sh, r);
            hr.setHeightInPoints(22);
            setCell(sh, r, 0, headStyle, "序号");
            for (int i = 0; i < cols.size(); i++) {
                setCell(sh, r, 1 + i, headStyle, cols.get(i)[1]);
            }
            sh.setRepeatingRows(CellRangeAddress.valueOf(headRow + ":" + headRow));
            r++;

            int no = 0;
            // 平铺列表:不再插入分组行(单位信息由勾选的单位列体现),人员顺序仍按单位树排列
            for (Map.Entry<String, List<Personnel>> g : ordered) {
                for (Personnel p : g.getValue()) {
                    no++;
                    ensureRow(sh, r).setHeightInPoints(22);
                    setCell(sh, r, 0, nameStyle, String.valueOf(no));
                    String[] d = p.getDepartmentId() == null ? null : flat.get(String.valueOf(p.getDepartmentId()));
                    List<Map<String, Object>> personLicenses = licenses.getOrDefault(p.getId(), java.util.Collections.emptyList());
                    for (int i = 0; i < cols.size(); i++) {
                        String key = cols.get(i)[0];
                        if (DRIVER_FIELDS.contains(key)) {
                            String text = driverValue(personLicenses, key);
                            if (text.length() > 32767) {
                                throw new IllegalArgumentException("人员ID " + p.getId() + " 的" + cols.get(i)[1]
                                        + "超过 Excel 单元格32767字符上限，请减少导出内容");
                            }
                            setCell(sh, r, 1 + i, driverStyle, text);
                            fitDriverText(sh, r, 1 + i, text);
                        } else {
                            setCell(sh, r, 1 + i, nameStyle, value(p, d, key));
                        }
                    }
                    r++;
                }
            }
            r++;
            merge(sh, r, 0, r, totalCols - 1, footStyle,
                    "制表人:                审核人:                日期:" + today.getYear() + "年" + today.getMonthValue() + "月" + today.getDayOfMonth() + "日");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.setPrintArea(0, 0, totalCols - 1, 0, r);
            wb.write(out);
            return out.toByteArray();
        } finally {
            wb.close();
        }
    }

    /** 仅取最终候选ID,500一批且再次叠加数据库权限;不JOIN人员主查询、不逐人访问。 */
    private Map<Long, List<Map<String, Object>>> loadDriverLicenses(List<Personnel> people) {
        Map<Long, List<Map<String, Object>>> result = new HashMap<Long, List<Map<String, Object>>>();
        java.util.Set<Long> ids = new java.util.LinkedHashSet<Long>();
        for (Personnel p : people) {
            if (DataScope.allowsDepartment(p.getDepartmentId()) && p.getId() != null) ids.add(p.getId());
        }
        List<Long> orderedIds = new ArrayList<Long>(ids);
        for (int start = 0; start < orderedIds.size(); start += DRIVER_BATCH_SIZE) {
            List<Long> batch = orderedIds.subList(start, Math.min(orderedIds.size(), start + DRIVER_BATCH_SIZE));
            java.util.Set<Long> batchIds = new java.util.HashSet<Long>(batch);
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            String sql = "SELECT dl.id, dl.personnel_id, dl.license_type, dl.license_no, dl.vehicle_class, "
                    + "dl.issue_date, dl.expiry_date, dl.license_status, dl.review_date, dl.issuing_auth "
                    + "FROM driver_license dl WHERE dl.personnel_id IN (" + placeholders + ") AND "
                    + DataScope.personSql("dl.personnel_id") + " ORDER BY dl.personnel_id, dl.id ASC";
            for (Map<String, Object> record : jdbc.queryForList(sql, batch.toArray())) {
                Object owner = record.get("personnel_id");
                // 即使替身/调用链返回了范围外记录,也不能进入导出或影响has。
                if (!(owner instanceof Number)) continue;
                Long id = ((Number) owner).longValue();
                if (!batchIds.contains(id)) continue;
                result.computeIfAbsent(id, ignored -> new ArrayList<Map<String, Object>>()).add(record);
            }
        }
        return result;
    }

    private static String driverValue(List<Map<String, Object>> records, String key) {
        if ("hasDriverLicense".equals(key)) return records.isEmpty() ? "无" : "有";
        if (records.isEmpty()) return "driverLicense".equals(key) ? "无" : "";
        List<String> lines = new ArrayList<String>();
        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> record = records.get(i);
            String text;
            switch (key) {
                case "driverLicense":
                    text = "类型:" + driverPart(record, "license_type", false)
                            + "；证号:" + driverPart(record, "license_no", false)
                            + "；车型:" + driverPart(record, "vehicle_class", false)
                            + "；状态:" + driverPart(record, "license_status", false);
                    break;
                case "driverLicenseType": text = driverPart(record, "license_type", false); break;
                case "driverLicenseNo": text = driverPart(record, "license_no", false); break;
                case "driverVehicleClass": text = driverPart(record, "vehicle_class", false); break;
                case "driverIssueDate": text = driverPart(record, "issue_date", true); break;
                case "driverExpiryDate": text = driverPart(record, "expiry_date", true); break;
                case "driverLicenseStatus": text = driverPart(record, "license_status", false); break;
                case "driverReviewDate": text = driverPart(record, "review_date", true); break;
                case "driverIssuingAuth": text = driverPart(record, "issuing_auth", false); break;
                default: throw new IllegalArgumentException("未知驾驶证字段:" + key);
            }
            lines.add((records.size() > 1 ? "[" + (i + 1) + "] " : "") + text);
        }
        return String.join("\n", lines);
    }

    private static String driverPart(Map<String, Object> record, String column, boolean date) {
        Object raw = record.get(column);
        String text = raw == null ? "" : String.valueOf(raw);
        if (text.trim().isEmpty()) return "—";
        return date ? cnDate(text) : text;
    }

    /** 估算中西文折行;Excel单行最多409磅,达到边界仍保留完整文本(详见说明)。 */
    private static void fitDriverText(Sheet sh, int rowIndex, int colIndex, String text) {
        double width = Math.max(1, sh.getColumnWidth(colIndex) / 256.0 - 2);
        int lines = 0;
        for (String line : text.split("\n", -1)) {
            int units = 0;
            for (int i = 0; i < line.length(); i++) units += line.charAt(i) > 255 ? 2 : 1;
            lines += Math.max(1, (int) Math.ceil(units / width));
        }
        Row row = ensureRow(sh, rowIndex);
        row.setHeightInPoints(Math.max(row.getHeightInPoints(), Math.min(409f, Math.max(22f, lines * 16f + 4f))));
    }

    private String value(Personnel p, String[] dept, String key) {
        LocalDate today = LocalDate.now();
        switch (key) {
            case "name": return nz(p.getName());
            case "gender": return nz(p.getGender());
            case "idCard": return nz(p.getIdCard());
            case "militaryIdNo": return nz(p.getMilitaryIdNo());
            case "departmentPath": return nz(p.getDepartmentPath());
            case "department":
                if (dept == null) {
                    return nz(p.getDepartmentName());
                }
                return dept[1].isEmpty() ? dept[0] : dept[1] + dept[0];
            case "locationPoint": return nz(p.getLocationPoint());
            case "category": return nz(p.getCategory());
            case "rank": return nz(p.getRank());
            case "post": return nz(p.getPost());
            case "status": return nz(p.getStatus());
            case "phone": return nz(p.getPhone());
            case "birthday": return cnDate(p.getBirthday());
            case "age": {
                LocalDate b = parseDate(p.getBirthday());
                if (b == null) {
                    return "";
                }
                int age = today.getYear() - b.getYear();
                if (b.getDayOfYear() > today.getDayOfYear()) {
                    age--;
                }
                return age >= 0 && age < 130 ? String.valueOf(age) : "";
            }
            case "nation": return nz(p.getNation());
            case "nativePlace": return nz(p.getNativePlace());
            case "enlistPlace": return nz(p.getEnlistPlace());
            case "politicalStatus": return nz(p.getPoliticalStatus());
            case "partyJoinDate": return cnDate(p.getPartyJoinDate());
            case "partyFullDate": return cnDate(p.getPartyFullDate());
            case "partyApplyDate": return cnDate(p.getPartyApplyDate());
            case "partyActivistDate": return cnDate(p.getPartyActivistDate());
            case "partyCandidateDate": return cnDate(p.getPartyCandidateDate());
            case "partyOrgLocation": return nz(p.getPartyOrgLocation());
            case "partyPost": return nz(p.getPartyPost());
            case "education": return nz(p.getEducation());
            case "eduSchool": return nz(p.getEduSchool());
            case "eduMajor": return nz(p.getEduMajor());
            case "eduDegree": return nz(p.getEduDegree());
            case "eduForm": return nz(p.getEduForm());
            case "eduCertNo": return nz(p.getEduCertNo());
            case "eduGraduate": return cnDate(p.getEduGraduateDate());
            case "maritalStatus": return nz(p.getMaritalStatus());
            case "marriageDate": return cnDate(p.getMarriageDate());
            case "spouseName": return nz(p.getSpouseName());
            case "spouseWork": return nz(p.getSpouseWork());
            case "spouseJob": return nz(p.getSpouseJob());
            case "spousePhone": return nz(p.getSpousePhone());
            case "followArmyDate": return cnDate(p.getFollowArmyDate());
            case "entryDate": return cnDate(p.getEntryDate());
            case "serviceYears": {
                LocalDate e0 = parseDate(p.getEntryDate());
                if (e0 == null) {
                    return "";
                }
                int years = today.getYear() - e0.getYear();
                if (e0.getDayOfYear() > today.getDayOfYear()) {
                    years--;
                }
                return years >= 0 && years < 80 ? years + "年" : "";
            }
            case "bloodType": return nz(p.getBloodType());
            case "height": return p.getHeight() == null ? "" : trimNum(p.getHeight());
            case "weight": return p.getWeight() == null ? "" : trimNum(p.getWeight());
            case "onlyChild": return nz(p.getOnlyChild());
            case "address": return nz(p.getAddress());
            case "actualUnit": return nz(p.getActualDepartmentPath());
            default: return "";
        }
    }

    private List<String> resolveFields(String param) {
        List<String> keys = new ArrayList<String>();
        if (param != null && !param.trim().isEmpty()) {
            for (String raw : param.split(",")) {
                String k = raw.trim();
                if (FIELDS.containsKey(k) && !keys.contains(k)) {
                    keys.add(k);
                }
            }
        }
        if (keys.isEmpty()) {
            keys.addAll(DEFAULT_FIELDS);
        }
        return keys;
    }

    private static String trimNum(Double v) {
        if (v == Math.rint(v)) {
            return String.valueOf(v.longValue());
        }
        return String.valueOf(v);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        String v = s.trim();
        try {
            if (v.length() >= 10) {
                return LocalDate.parse(v.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            }
            if (v.length() == 8) {
                return LocalDate.parse(v.substring(0, 4) + "-" + v.substring(4, 6) + "-" + v.substring(6, 8), DateTimeFormatter.ISO_LOCAL_DATE);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /** 日期显示为 年月日;解析失败原样返回 */
    private static String cnDate(String s) {
        LocalDate d = parseDate(s);
        if (d == null) {
            return nz(s);
        }
        return d.getYear() + "年" + d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String emptyAs(String s, String def) {
        return s == null || s.isEmpty() ? def : s;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static Long parseLong(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 展平:id → [单位名, 上级单位名, 序号字符串] */
    private static void flatten(List<Department> nodes, String parentName, Map<String, String[]> out, int[] seq) {
        if (nodes == null) {
            return;
        }
        for (Department d : nodes) {
            out.put(String.valueOf(d.getId()), new String[]{
                    d.getName() == null ? "" : d.getName(), parentName == null ? "" : parentName, String.valueOf(seq[0]++)});
            flatten(d.getChildren(), d.getName(), out, seq);
        }
    }

    private static void collectSubtree(List<Department> nodes, long rootId, boolean inside, java.util.Set<Long> out) {
        if (nodes == null) {
            return;
        }
        for (Department d : nodes) {
            boolean hit = inside || (d.getId() != null && d.getId() == rootId);
            if (hit && d.getId() != null) {
                out.add(d.getId());
            }
            collectSubtree(d.getChildren(), rootId, hit, out);
        }
    }

    private static CellStyle style(Workbook wb, String fontName, int fontPt, boolean bold, boolean wrap, boolean bordered) {
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) fontPt);
        font.setBold(bold);
        font.setFontName(fontName);
        CellStyle st = wb.createCellStyle();
        st.setFont(font);
        st.setAlignment(HorizontalAlignment.CENTER);
        st.setVerticalAlignment(VerticalAlignment.CENTER);
        st.setWrapText(wrap);
        if (bordered) {
            st.setBorderTop(BorderStyle.THIN);
            st.setBorderBottom(BorderStyle.THIN);
            st.setBorderLeft(BorderStyle.THIN);
            st.setBorderRight(BorderStyle.THIN);
        }
        return st;
    }

    private static Row ensureRow(Sheet sh, int rowIdx) {
        Row row = sh.getRow(rowIdx);
        if (row == null) {
            row = sh.createRow(rowIdx);
        }
        return row;
    }

    private static void merge(Sheet sh, int r1, int c1, int r2, int c2, CellStyle st, String text) {
        for (int r = r1; r <= r2; r++) {
            Row row = ensureRow(sh, r);
            for (int c = c1; c <= c2; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    cell = row.createCell(c);
                }
                cell.setCellStyle(st);
            }
        }
        if (text != null && !text.isEmpty()) {
            ensureRow(sh, r1).getCell(c1).setCellValue(text);
        }
        if (r1 != r2 || c1 != c2) {
            sh.addMergedRegion(new CellRangeAddress(r1, r2, c1, c2));
        }
    }

    private static void setCell(Sheet sh, int rowIdx, int colIdx, CellStyle st, String text) {
        Row row = ensureRow(sh, rowIdx);
        Cell cell = row.getCell(colIdx);
        if (cell == null) {
            cell = row.createCell(colIdx);
        }
        cell.setCellStyle(st);
        if (text != null && !text.isEmpty()) {
            cell.setCellValue(text);
        }
    }
}
