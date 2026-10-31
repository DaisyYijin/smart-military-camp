package com.szjx.personnel.server.service;

import com.szjx.personnel.server.model.FitnessRecord;
import com.szjx.personnel.server.util.ExcelDates;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 体能考核「宽行(一人一行)」Excel 边界。
 *
 * 模板/导出的项目列来自 sys_dict(fitness_item) 启用项(按 sort,id 排序);
 * 导入按表头自动识别宽行与旧「一项一行」格式(识别入口在 ModuleExcelService 的 fitness 分支)。
 * 记录级语义与旧格式完全一致:同人同日期同项目覆盖更新(upsert),
 * 分数/评定由 FitnessScoringService 按年龄与体能标准权威计算,人员解析与权限沿用共享 Integrity。
 */
@Service
public class FitnessWideExcelService {

    static final int MAX_EXPORT_RECORDS = 50000;
    private static final int PAGE_SIZE = 1000;
    static final String[] FIXED_HEADERS = {"姓名", "身份证号码", "考核日期"};

    private final JdbcTemplate jdbc;
    private final FitnessService fitnessService;

    public FitnessWideExcelService(JdbcTemplate jdbc, FitnessService fitnessService) {
        this.jdbc = jdbc;
        this.fitnessService = fitnessService;
    }

    /** 导出/汇总共用的筛选条件(与体能列表查询同口径)。 */
    public static final class Filters {
        public String personnelName;
        public String idCard;
        public String item;
        public String grade;
        public String startDate;
        public String endDate;
        public String minScore;
        public String maxScore;
        public Long departmentId;
        public Long personnelId;
    }

    /** 启用的体能项目(按字典 sort,id 排序),宽行模板/导出/汇总的项目列来源。 */
    public List<String> enabledItems() {
        List<String> items = new ArrayList<String>();
        for (String label : jdbc.queryForList(
                "SELECT dict_label FROM sys_dict WHERE dict_type='fitness_item' AND enabled=1 ORDER BY sort, id",
                String.class)) {
            String value = label == null ? "" : label.trim();
            if (!value.isEmpty()) items.add(value);
        }
        return items;
    }

    /** 宽行识别:无「考核项目」+「成绩」列(否则优先按旧长格式),且含姓名/身份证号码/考核日期与至少一个启用项目列。 */
    public boolean detectWide(Map<String, Integer> headers) {
        if (headers == null) return false;
        if (headers.containsKey("考核项目") && headers.containsKey("成绩")) return false;
        if (!headers.containsKey("姓名") || !headers.containsKey("身份证号码") || !headers.containsKey("考核日期")) return false;
        Set<String> items = new HashSet<String>(enabledItems());
        for (String header : headers.keySet()) {
            if (items.contains(header)) return true;
        }
        return false;
    }

    /* ================= 模板 ================= */

    public byte[] template() throws IOException {
        List<String> items = enabledItems();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, items);
            CellStyle text = textStyle(wb);
            // 第二行预置文本样式,粘贴的身份证号不会被 Excel 转成数值/科学计数。
            Row blank = sheet.createRow(1);
            for (int c = 0; c < FIXED_HEADERS.length + items.size(); c++) {
                blank.createCell(c, CellType.STRING).setCellStyle(text);
            }
            Sheet notes = wb.createSheet("填写说明");
            String[] lines = {
                    "一人一行(宽行):同一人同一考核日期的各项成绩填在同一行,每人每次考核一行。",
                    "第一行表头:姓名、身份证号码、考核日期,其后为数据字典「体能项目」的启用项(按排序);项目列随字典动态变化。",
                    "项目单元格留空 = 该项目未参加,不生成记录也不报错;非空单元格才导入。",
                    "同人同日期同项目重复导入(含同一文件内重复行)按覆盖更新:更新成绩并按体能标准重新计算分数与评定等级。",
                    "成绩格式与旧版一致:时间类项目可填 12'30\"、12:30、12分30秒;次数类填数字(如 60);厘米类填数字(如 230);BMI 项目填体重千克数。",
                    "旧版「一项一行」格式(含「考核项目」「成绩」列)仍可直接导入,系统按表头自动识别,同样按覆盖更新,无需单独模板。",
                    "身份证号码列已设为文本格式;填写时保持文本以免精度丢失。导入优先按身份证号匹配人员,身份证号为空时也可仅填唯一姓名。",
                    "考核日期支持 2026-01-31、2026/1/31、20260131、2026年1月31日 及 Excel 日期单元格。",
                    "每行独立导入:某行任一项目成绩非法则整行失败并在结果中标注行号与项目名;其他行不受影响。"
            };
            for (int i = 0; i < lines.length; i++) {
                notes.createRow(i).createCell(0, CellType.STRING).setCellValue(lines[i]);
            }
            notes.setColumnWidth(0, 120 * 256);
            return bytes(wb);
        }
    }

    /* ================= 导入 ================= */

    /**
     * 宽行导入:每个非空项目单元格生成/覆盖一条记录,键为 (personnel_id,item,test_date)。
     * 行级事务:某行任一项目失败则整行回滚,错误标注 Excel 行号与项目名。
     */
    public Map<String, Object> importWide(Sheet sheet, Map<String, Integer> headers,
                                          DataFormatter fmt, ExcelDates dates) {
        List<String> items = enabledItems();
        // 列头白名单:固定列 + 启用项目列;其它列头直接拒绝整个文件,避免成绩被静默丢弃。
        Set<String> allowed = new LinkedHashSet<String>(Arrays.asList(FIXED_HEADERS));
        allowed.addAll(items);
        for (String header : headers.keySet()) {
            if (!allowed.contains(header)) {
                throw new IllegalArgumentException("不支持的项目列:" + header + ",列头必须是姓名/身份证号码/考核日期或启用的体能项目");
            }
        }
        Map<String, Integer> itemColumns = new LinkedHashMap<String, Integer>();
        for (String item : items) {
            Integer column = headers.get(item);
            if (column != null) itemColumns.put(item, column);
        }
        if (itemColumns.isEmpty()) {
            throw new IllegalArgumentException("缺少体能项目列:至少需要一列表头为启用的体能项目");
        }
        int added = 0, updated = 0, failed = 0;
        List<String> errors = new ArrayList<String>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (ModuleExcelService.Integrity.blank(row, fmt)) continue;
            final String name = text(row.getCell(headers.get("姓名")), fmt);
            final String card = text(row.getCell(headers.get("身份证号码")), fmt);
            final String testDate = dates.read(row.getCell(headers.get("考核日期")), "考核日期");
            final List<String[]> filled = new ArrayList<String[]>();
            for (Map.Entry<String, Integer> entry : itemColumns.entrySet()) {
                String score = text(row.getCell(entry.getValue()), fmt);
                if (!score.isEmpty()) filled.add(new String[]{entry.getKey(), score});
            }
            if (filled.isEmpty()) continue; // 空=未参加,不生成记录不报错
            try {
                int[] counts = ModuleExcelService.Integrity.transaction(jdbc, () -> {
                    long personnelId = ModuleExcelService.Integrity.person(jdbc, card, name);
                    int add = 0, upd = 0;
                    for (String[] cell : filled) {
                        if (upsertRecord(personnelId, cell[0], cell[1], testDate)) upd++;
                        else add++;
                    }
                    return new int[]{add, upd};
                });
                added += counts[0];
                updated += counts[1];
            } catch (Exception e) {
                failed++;
                errors.add("第" + (r + 1) + "行:" + ModuleExcelService.Integrity.message(e));
            }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("added", added);
        result.put("updated", updated);
        result.put("failed", failed);
        result.put("errors", errors);
        return result;
    }

    /** 单条 upsert:已存在则更新成绩并重算分数/评定,否则插入;返回是否为更新。 */
    private boolean upsertRecord(long personnelId, String item, String rawScore, String testDate) {
        if (testDate == null || testDate.trim().isEmpty()) {
            throw new IllegalArgumentException("考核日期不能为空");
        }
        FitnessRecord record = new FitnessRecord();
        record.setPersonnelId(personnelId);
        record.setItem(item);
        record.setScore(rawScore);
        record.setTestDate(testDate.trim());
        try {
            new FitnessScoringService(jdbc).score(record);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("项目「" + item + "」" + ModuleExcelService.Integrity.message(e));
        }
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM fitness_record WHERE personnel_id=? AND item=? AND test_date=?",
                Long.class, personnelId, item, record.getTestDate());
        if (ids.size() > 1) {
            throw new IllegalArgumentException("项目「" + item + "」同日已有多条记录，拒绝任选，请先清理重复数据");
        }
        if (ids.isEmpty()) {
            jdbc.update("INSERT INTO fitness_record(personnel_id,item,score,points,grade,test_date,tester,remark) "
                            + "VALUES(?,?,?,?,?,?,?,?)",
                    personnelId, record.getItem(), record.getScore(), record.getPoints(),
                    record.getGrade(), record.getTestDate(), "", "");
            return false;
        }
        jdbc.update("UPDATE fitness_record SET personnel_id=?, item=?, score=?, points=?, grade=?, test_date=?, "
                        + "updated_at=datetime('now','localtime') WHERE id=?",
                personnelId, record.getItem(), record.getScore(), record.getPoints(),
                record.getGrade(), record.getTestDate(), ids.get(0));
        return true;
    }

    /* ================= 导出 ================= */

    /**
     * 宽行导出:ids 非空仅导所选记录;否则按筛选条件全量分页聚合(上限 50000 条记录)。
     * 单元格 = 成绩（分数分/评定）,无该项目留空;列与模板一致(启用项目按字典排序)。
     */
    public byte[] exportWide(List<Long> ids, Filters filters) throws IOException {
        List<FitnessRecord> records;
        if (ids != null && !ids.isEmpty()) {
            records = fitnessService.listByIds(ids);
        } else {
            Filters f = filters == null ? new Filters() : filters;
            long total = fitnessService.countFiltered2(f.personnelName, f.idCard, f.item, f.grade,
                    f.startDate, f.endDate, f.departmentId, f.minScore, f.maxScore, f.personnelId);
            if (total > MAX_EXPORT_RECORDS) {
                throw new IllegalArgumentException("导出最多" + MAX_EXPORT_RECORDS + "条体能记录，请缩小筛选范围");
            }
            records = new ArrayList<FitnessRecord>();
            for (int page = 1; ; page++) {
                List<FitnessRecord> rows = fitnessService.queryFiltered2(f.personnelName, f.idCard, f.item, f.grade,
                        f.startDate, f.endDate, f.departmentId, f.minScore, f.maxScore, page, PAGE_SIZE, f.personnelId);
                if (rows.isEmpty()) {
                    if (records.size() < total) throw new IllegalArgumentException("导出期间记录发生变化，请刷新后重试");
                    break;
                }
                records.addAll(rows);
                if (records.size() > MAX_EXPORT_RECORDS) {
                    throw new IllegalArgumentException("导出最多" + MAX_EXPORT_RECORDS + "条体能记录，请缩小筛选范围");
                }
                if (records.size() >= total) break;
            }
        }
        List<String> items = enabledItems();
        Set<String> columns = new LinkedHashSet<String>(items);
        // 按人员+日期分组;记录按 test_date DESC,id DESC 到达,同项目先到者(最新一条)填入单元格。
        Map<String, List<FitnessRecord>> groups = new LinkedHashMap<String, List<FitnessRecord>>();
        for (FitnessRecord record : records) {
            String key = record.getPersonnelId() + "|" + record.getTestDate();
            List<FitnessRecord> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<FitnessRecord>();
                groups.put(key, group);
            }
            group.add(record);
        }
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, items);
            int rowIndex = 1;
            for (List<FitnessRecord> group : groups.values()) {
                Row row = sheet.createRow(rowIndex++);
                FitnessRecord first = group.get(0);
                row.createCell(0, CellType.STRING).setCellValue(first.getPersonnelName() == null ? "" : first.getPersonnelName());
                row.createCell(1, CellType.STRING).setCellValue(first.getIdCard() == null ? "" : first.getIdCard());
                row.createCell(2, CellType.STRING).setCellValue(first.getTestDate() == null ? "" : first.getTestDate());
                Map<String, FitnessRecord> cells = new LinkedHashMap<String, FitnessRecord>();
                for (FitnessRecord record : group) {
                    if (columns.contains(record.getItem()) && !cells.containsKey(record.getItem())) {
                        cells.put(record.getItem(), record);
                    }
                }
                int column = FIXED_HEADERS.length;
                for (String item : items) {
                    FitnessRecord record = cells.get(item);
                    row.createCell(column++, CellType.STRING)
                            .setCellValue(record == null ? "" : cellText(record));
                }
            }
            return bytes(wb);
        }
    }

    /** 单元格文本:成绩（分数分/评定）,缺失部分省略。 */
    static String cellText(FitnessRecord record) {
        String score = record.getScore() == null ? "" : record.getScore().trim();
        StringBuilder parts = new StringBuilder();
        if (record.getPoints() != null) parts.append(FitnessScoringService.trimNumber(record.getPoints())).append("分");
        if (record.getGrade() != null && !record.getGrade().trim().isEmpty()) {
            if (parts.length() > 0) parts.append("/");
            parts.append(record.getGrade().trim());
        }
        return parts.length() == 0 ? score : score + "（" + parts + "）";
    }

    /* ================= 私有工具 ================= */

    private static Sheet createSheet(Workbook wb, List<String> items) {
        Sheet sheet = wb.createSheet("体能考核");
        CellStyle head = headerStyle(wb);
        CellStyle text = textStyle(wb);
        Row header = sheet.createRow(0);
        int column = 0;
        for (String title : FIXED_HEADERS) {
            Cell cell = header.createCell(column, CellType.STRING);
            cell.setCellValue(title);
            cell.setCellStyle(head);
            sheet.setColumnWidth(column, "身份证号码".equals(title) ? 24 * 256 : 14 * 256);
            column++;
        }
        // 身份证列文本格式,保持整列字符串显示。
        sheet.setDefaultColumnStyle(1, text);
        for (String item : items) {
            Cell cell = header.createCell(column, CellType.STRING);
            cell.setCellValue(item);
            cell.setCellStyle(head);
            sheet.setColumnWidth(column, 18 * 256);
            column++;
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle textStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat("@"));
        return style;
    }

    /** 单元格取值:整数数值直接按整数文本读取,其余交给 DataFormatter,日期列另行经 ExcelDates。 */
    private static String text(Cell cell, DataFormatter fmt) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (Double.isFinite(value) && value == Math.rint(value) && Math.abs(value) < 1e15) {
                return String.valueOf((long) value);
            }
        }
        if (cell.getCellType() == CellType.BLANK) return "";
        return fmt.formatCellValue(cell).trim();
    }

    private static byte[] bytes(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }
}
