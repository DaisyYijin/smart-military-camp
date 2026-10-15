package com.szjx.personnel.server.service;

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
 * 按部门(含上级全名)分组、组行标注人数;横版 A4、一页宽缩放、表头每页重复;
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
        FIELDS.put("department", new String[]{"部门", "16"});
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
        FIELDS.put("politicalStatus", new String[]{"政治面貌", "10"});
        FIELDS.put("education", new String[]{"学历", "9"});
        FIELDS.put("maritalStatus", new String[]{"婚姻状况", "9"});
        FIELDS.put("entryDate", new String[]{"入伍日期", "16"});
        FIELDS.put("serviceYears", new String[]{"军龄", "7"});
        FIELDS.put("bloodType", new String[]{"血型", "6"});
        FIELDS.put("height", new String[]{"身高(cm)", "8"});
        FIELDS.put("weight", new String[]{"体重(kg)", "8"});
        FIELDS.put("onlyChild", new String[]{"独生子女", "8"});
        FIELDS.put("address", new String[]{"详细地址", "28"});
    }

    /** 未指定/全部非法时的默认列 */
    private static final List<String> DEFAULT_FIELDS = Arrays.asList(
            "name", "gender", "idCard", "department", "locationPoint", "rank", "post", "status", "entryDate", "phone");

    private final PersonnelService personnelService;
    private final DepartmentService departmentService;

    public RosterExcelService(PersonnelService personnelService, DepartmentService departmentService) {
        this.personnelService = personnelService;
        this.departmentService = departmentService;
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

        List<Department> tree = departmentService.tree();
        // 展平部门树(与点名册一致的 parent/seq 语义)
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

        // 按部门全名分组,组序按部门树顺序
        LinkedHashMap<String, List<Personnel>> groups = new LinkedHashMap<String, List<Personnel>>();
        final Map<String, Integer> groupSeq = new HashMap<String, Integer>();
        for (Personnel p : rows) {
            String[] d = p.getDepartmentId() == null ? null : flat.get(String.valueOf(p.getDepartmentId()));
            String group;
            int seq = Integer.MAX_VALUE;
            if (d == null) {
                group = emptyAs(p.getDepartmentName(), "未分配部门");
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
            // 平铺列表:不再插入分组行(部门信息由勾选的部门列体现),人员顺序仍按部门树排列
            for (Map.Entry<String, List<Personnel>> g : ordered) {
                for (Personnel p : g.getValue()) {
                    no++;
                    ensureRow(sh, r).setHeightInPoints(22);
                    setCell(sh, r, 0, nameStyle, String.valueOf(no));
                    String[] d = p.getDepartmentId() == null ? null : flat.get(String.valueOf(p.getDepartmentId()));
                    for (int i = 0; i < cols.size(); i++) {
                        setCell(sh, r, 1 + i, nameStyle, value(p, d, cols.get(i)[0]));
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

    private String value(Personnel p, String[] dept, String key) {
        LocalDate today = LocalDate.now();
        switch (key) {
            case "name": return nz(p.getName());
            case "gender": return nz(p.getGender());
            case "idCard": return nz(p.getIdCard());
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
            case "politicalStatus": return nz(p.getPoliticalStatus());
            case "education": return nz(p.getEducation());
            case "maritalStatus": return nz(p.getMaritalStatus());
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

    /** 展平:id → [部门名, 上级部门名, 序号字符串] */
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
