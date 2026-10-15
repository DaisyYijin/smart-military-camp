package com.szjx.personnel.server.service;

import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人员点名册 Excel 导出:与网页版版式一致。
 * 表头=标题+日期;在位人员=单位(连)→班 两级表头、班列并排、姓名纵向填充;
 * 不在位人员=原因在左侧整块合并、姓名横向排列,休假人员姓名下方(单元格内换行)显示归队时间;
 * 底部=应到/在位/不在位统计。横版 A4、按宽度缩放打印。
 */
@Service
public class RollCallExcelService {

    private final PersonnelService personnelService;
    private final DepartmentService departmentService;
    private final JdbcTemplate jdbc;

    public RollCallExcelService(PersonnelService personnelService, DepartmentService departmentService, JdbcTemplate jdbc) {
        this.personnelService = personnelService;
        this.departmentService = departmentService;
        this.jdbc = jdbc;
    }

    /** 部门树展平条目:name=部门名,parent=上级部门名(顶级为空),seq=树内顺序 */
    private static class DeptInfo {
        String name;
        String parent;
        int seq;
    }

    private static class SquadBlock {
        String label;
        int seq;
        int colStart;
        int cols;
        List<String> names = new ArrayList<String>();
    }

    private static class UnitBlock {
        String label;
        int seq = Integer.MAX_VALUE;
        boolean split;
        LinkedHashMap<String, SquadBlock> squads = new LinkedHashMap<String, SquadBlock>();
        int total;
        int colStart;
        int cols;
    }

    public byte[] build(Map<String, String> q) throws Exception {
        // 1) 人员筛选(参数名与列表接口一致;departmentId 单独做含下级展开)
        Long departmentId = parseLong(q.get("departmentId"));
        List<Personnel> rows = personnelService.query(
                trim(q.get("keyword")), trim(q.get("name")), trim(q.get("idCard")), null,
                trim(q.get("status")), trim(q.get("gender")), trim(q.get("politicalStatus")),
                trim(q.get("category")), trim(q.get("rank")), trim(q.get("post")),
                trim(q.get("nativePlace")), trim(q.get("enlistPlace")), trim(q.get("education")),
                trim(q.get("maritalStatus")), trim(q.get("nation")), trim(q.get("bloodType")),
                trim(q.get("onlyChild")), trim(q.get("locationPoint")),
                trim(q.get("entryDateStart")), trim(q.get("entryDateEnd")), 1, 1000000);

        // 2) 部门树:展平索引 + 子树过滤
        List<Department> tree = departmentService.tree();
        final Map<String, DeptInfo> idx = new HashMap<String, DeptInfo>();
        final int[] seqHolder = {0};
        walkTree(tree, "", idx, seqHolder);
        if (departmentId != null && departmentId > 0) {
            final java.util.Set<Long> allowed = new java.util.HashSet<Long>();
            collectSubtree(tree, departmentId, false, allowed);
            List<Personnel> kept = new ArrayList<Personnel>();
            for (Personnel p : rows) {
                if (p.getDepartmentId() != null && allowed.contains(p.getDepartmentId())) {
                    kept.add(p);
                }
            }
            rows = kept;
        }

        // 3) 在位 / 不在位划分
        List<Personnel> present = new ArrayList<Personnel>();
        List<Personnel> absent = new ArrayList<Personnel>();
        for (Personnel p : rows) {
            String st = p.getStatus() == null ? "" : p.getStatus();
            if ("在位".equals(st) || "在职".equals(st)) {
                present.add(p);
            } else {
                absent.add(p);
            }
        }

        // 4) 归队时间:当前在休(结束日期>=今天)的最晚结束日期
        Map<Long, String> returns = new HashMap<Long, String>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT personnel_id, MAX(end_date) AS ed FROM leave_record " +
                        "WHERE IFNULL(end_date,'')<>'' AND end_date>=date('now','localtime') GROUP BY personnel_id")) {
            Object pid = r.get("personnel_id");
            if (pid != null) {
                returns.put(((Number) pid).longValue(), String.valueOf(r.get("ed")));
            }
        }

        // 5) 组织 单位→班 结构(在位)
        LinkedHashMap<String, UnitBlock> unitMap = new LinkedHashMap<String, UnitBlock>();
        for (Personnel p : present) {
            DeptInfo d = p.getDepartmentId() == null ? null : idx.get(String.valueOf(p.getDepartmentId()));
            String unit;
            String squad;
            int seq = Integer.MAX_VALUE;
            if (d == null) {
                unit = emptyAs(p.getDepartmentName(), "未分配部门");
                squad = unit;
            } else {
                seq = d.seq;
                unit = d.parent.isEmpty() ? d.name : d.parent;
                squad = d.parent.isEmpty() ? d.name : d.name;
            }
            UnitBlock u = unitMap.get(unit);
            if (u == null) {
                u = new UnitBlock();
                u.label = unit;
                unitMap.put(unit, u);
            }
            if (seq < u.seq) {
                u.seq = seq;
            }
            if (d != null && !d.parent.isEmpty()) {
                u.split = true;
            }
            SquadBlock s = u.squads.get(squad);
            if (s == null) {
                s = new SquadBlock();
                s.label = squad;
                s.seq = seq;
                u.squads.put(squad, s);
            }
            s.names.add(p.getName() == null ? "" : p.getName());
        }
        List<UnitBlock> units = new ArrayList<UnitBlock>(unitMap.values());
        units.sort((a, b) -> Integer.compare(a.seq, b.seq));
        int colCursor = 0;
        int presentCols = 0;
        for (UnitBlock u : units) {
            List<SquadBlock> squads = new ArrayList<SquadBlock>(u.squads.values());
            squads.sort((a, b) -> Integer.compare(a.seq, b.seq));
            LinkedHashMap<String, SquadBlock> ordered = new LinkedHashMap<String, SquadBlock>();
            for (SquadBlock s : squads) {
                ordered.put(s.label, s);
            }
            u.squads = ordered;
            u.colStart = colCursor;
            u.cols = Math.max(1, ordered.size());
            u.total = 0;
            for (SquadBlock s : ordered.values()) {
                s.colStart = colCursor; // 每班一列
                s.cols = 1;
                colCursor += 1;
                u.total += s.names.size();
            }
            presentCols += u.cols;
        }

        // 6) 不在位按原因分组
        LinkedHashMap<String, List<Personnel>> reasons = new LinkedHashMap<String, List<Personnel>>();
        for (Personnel p : absent) {
            String key = emptyAs(p.getStatus(), "其他");
            List<Personnel> list = reasons.get(key);
            if (list == null) {
                list = new ArrayList<Personnel>();
                reasons.put(key, list);
            }
            list.add(p);
        }

        // ================= 生成工作簿 =================
        Workbook wb = new XSSFWorkbook();
        try {
            Sheet sh = wb.createSheet("点名册");
            int totalCols = Math.max(presentCols, 6);
            // 列宽自适应:目标总宽≈160字符,让内容宽高比接近横版A4,减少整页缩放后的左右留白;
            // 同时保证不在位列能容纳“归队:yyyy-MM-dd”整行
            int colWidth = Math.max(16, Math.min(24, 160 / Math.max(1, totalCols)));
            for (int c = 0; c < totalCols; c++) {
                sh.setColumnWidth(c, colWidth * 256);
            }
            sh.getPrintSetup().setLandscape(true);
            sh.getPrintSetup().setPaperSize((short) 9); // A4
            sh.setFitToPage(true);
            sh.getPrintSetup().setFitWidth((short) 1);
            sh.getPrintSetup().setFitHeight((short) 1); // 整表缩放到一页
            sh.setHorizontallyCenter(true);
            sh.setMargin(Sheet.LeftMargin, 0.25);
            sh.setMargin(Sheet.RightMargin, 0.25);
            sh.setMargin(Sheet.TopMargin, 0.35);
            sh.setMargin(Sheet.BottomMargin, 0.35);
            sh.setMargin(Sheet.HeaderMargin, 0.15);
            sh.setMargin(Sheet.FooterMargin, 0.15);

            // 公文字体:标题=方正小标宋简体,表头=黑体,内容/日期/统计=仿宋_GB2312;标题与表头字体自带笔重,不再加粗
            CellStyle titleStyle = style(wb, "方正小标宋简体", 18, false, false, null, false);
            CellStyle subStyle = style(wb, "仿宋_GB2312", 12, false, false, null, false);
            CellStyle sectionStyle = style(wb, "黑体", 12, false, false, null, false);
            CellStyle unitStyle = style(wb, "黑体", 12, false, false, null, true);
            CellStyle squadStyle = style(wb, "黑体", 11, false, true, null, true);
            CellStyle nameStyle = style(wb, "仿宋_GB2312", 12, false, true, null, true);
            CellStyle footStyle = style(wb, "仿宋_GB2312", 12, false, false, null, false);

            int r = 0;
            // 标题 + 日期
            merge(sh, r, 0, r, totalCols - 1, titleStyle, "人员点名册");
            sh.getRow(r).setHeightInPoints(30);
            r++;
            LocalDate today = LocalDate.now();
            merge(sh, r, 0, r, totalCols - 1, subStyle,
                    "日期:" + today.getYear() + "年" + today.getMonthValue() + "月" + today.getDayOfMonth() + "日");
            r++;
            r++; // 空行

            // 在位人员
            merge(sh, r, 0, r, totalCols - 1, sectionStyle, "在位人员(共" + present.size() + "人)");
            r++;
            int unitHeadRow = r;
            int squadHeadRow = r + 1;
            int nameStart = r + 2;
            int maxNames = 0;
            for (UnitBlock u : units) {
                for (SquadBlock s : u.squads.values()) {
                    maxNames = Math.max(maxNames, s.names.size());
                }
            }
            // 保证行存在并统一行高(名单行稍高便于阅读/手写)
            ensureRow(sh, squadHeadRow).setHeightInPoints(20);
            // 表头行与名单行先整行铺边框样式(无数据的单元格也显示框线),再写入内容
            for (int c = 0; c < totalCols; c++) {
                setCell(sh, unitHeadRow, c, unitStyle, "");
                setCell(sh, squadHeadRow, c, squadStyle, "");
            }
            for (int i = 0; i < maxNames; i++) {
                ensureRow(sh, nameStart + i).setHeightInPoints(22);
                for (int c = 0; c < totalCols; c++) {
                    setCell(sh, nameStart + i, c, nameStyle, "");
                }
            }
            for (UnitBlock u : units) {
                if (u.split) {
                    merge(sh, unitHeadRow, u.colStart, unitHeadRow, u.colStart + u.cols - 1, unitStyle,
                            u.label + "(在位" + u.total + "人)");
                    for (SquadBlock s : u.squads.values()) {
                        merge(sh, squadHeadRow, s.colStart, squadHeadRow, s.colStart, squadStyle,
                                s.label + "(" + s.names.size() + "人)");
                        for (int i = 0; i < s.names.size(); i++) {
                            setCell(sh, nameStart + i, s.colStart, nameStyle, s.names.get(i));
                        }
                    }
                } else {
                    SquadBlock only = u.squads.values().iterator().next();
                    merge(sh, unitHeadRow, u.colStart, squadHeadRow, u.colStart, unitStyle,
                            u.label + "(在位" + only.names.size() + "人)");
                    for (int i = 0; i < only.names.size(); i++) {
                        setCell(sh, nameStart + i, u.colStart, nameStyle, only.names.get(i));
                    }
                }
            }
            r = nameStart + maxNames;
            r++; // 空行

            // 不在位人员
            StringBuilder reasonText = new StringBuilder();
            for (Map.Entry<String, List<Personnel>> e : reasons.entrySet()) {
                if (reasonText.length() > 0) {
                    reasonText.append("、");
                }
                reasonText.append(e.getKey()).append(e.getValue().size()).append("人");
            }
            String absentTitle = "不在位人员(共" + absent.size() + "人" +
                    (reasonText.length() > 0 ? ":" + reasonText : "") + ")";
            merge(sh, r, 0, r, totalCols - 1, sectionStyle, absentTitle);
            r++;
            int nameCols = Math.max(1, totalCols - 1); // 姓名横向铺满原因列右侧的所有列
            for (Map.Entry<String, List<Personnel>> e : reasons.entrySet()) {
                List<Personnel> people = e.getValue();
                int rowsN = Math.max(1, (people.size() + nameCols - 1) / nameCols);
                for (int i = 0; i < rowsN; i++) {
                    ensureRow(sh, r + i).setHeightInPoints(32); // 预留归队时间第二行
                    for (int c = 1; c < totalCols; c++) {
                        setCell(sh, r + i, c, nameStyle, "");
                    }
                }
                merge(sh, r, 0, r + rowsN - 1, 0, squadStyle, e.getKey() + "(" + people.size() + "人)");
                for (int i = 0; i < people.size(); i++) {
                    Personnel p = people.get(i);
                    int rowAt = r + i / nameCols;
                    int colAt = 1 + i % nameCols;
                    String ret = returns.get(p.getId());
                    String text = p.getName() == null ? "" : p.getName();
                    if (ret != null && !ret.isEmpty()) {
                        text = text + "\n归队:" + ret;
                    }
                    setCell(sh, rowAt, colAt, nameStyle, text);
                }
                r += rowsN;
            }
            if (reasons.isEmpty()) {
                merge(sh, r, 0, r, totalCols - 1, nameStyle, "无");
                r++;
            }
            r++; // 空行

            // 底部统计
            merge(sh, r, 0, r, totalCols - 1, footStyle,
                    "应到:" + rows.size() + "人    在位:" + present.size() + "人    不在位:" + absent.size() +
                            "人    实到:        人    点名人:");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.setPrintArea(0, 0, totalCols - 1, 0, r);
            wb.write(out);
            return out.toByteArray();
        } finally {
            wb.close();
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String emptyAs(String s, String def) {
        return s == null || s.isEmpty() ? def : s;
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

    private static void walkTree(List<Department> nodes, String parentName, Map<String, DeptInfo> idx, int[] seq) {
        if (nodes == null) {
            return;
        }
        for (Department d : nodes) {
            DeptInfo info = new DeptInfo();
            info.name = d.getName() == null ? "" : d.getName();
            info.parent = parentName == null ? "" : parentName;
            info.seq = seq[0]++;
            idx.put(String.valueOf(d.getId()), info);
            walkTree(d.getChildren(), d.getName(), idx, seq);
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

    private static CellStyle style(Workbook wb, String fontName, int fontPt, boolean bold, boolean wrap, IndexedColors fill, boolean bordered) {
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
        if (fill != null) {
            st.setFillForegroundColor(fill.getIndex());
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
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

    /** 先给合并区内所有单元格上样式与文本(左上角),再合并,保证边框完整 */
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
