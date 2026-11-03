package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.model.DoubleExcellenceRecord;
import com.szjx.personnel.server.util.ExcelDates;
import com.szjx.personnel.server.util.ExcelIo;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dedicated six-column Excel boundary. All identity/scope/upsert decisions stay in the core service. */
@Service
public class DoubleExcellenceExcelService {
    static final int MAX_ROWS = ExcelIo.MAX_ROWS;
    static final int MAX_COLUMNS = ExcelIo.MAX_COLUMNS;
    static final int MAX_BYTES = ExcelIo.MAX_BYTES;
    private static final int PAGE_SIZE = 1000;
    private static final String[] HEADERS = {"年份", "季度", "姓名", "身份证号码", "评定", "评比日期"};
    private static final String[] FILTERS = {"year", "quarter", "personnelName", "idCard", "departmentId", "rating", "ratingDateStart", "ratingDateEnd", "personnelIds"};
    private static final String IMPORT_RULE = "同一人员、年份、季度仅一条记录；导入组合键已存在时更新，不存在时新增。身份证号码须为完整文本，姓名须与现有人员一致。";
    private static final String SHEET_NAME = "双争评比";

    private final DoubleExcellenceService records;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate rowTransaction;

    public DoubleExcellenceExcelService(DoubleExcellenceService records, JdbcTemplate jdbc) {
        if (jdbc.getDataSource() == null) throw new IllegalArgumentException("Excel导入需要数据源");
        this.records = records;
        this.jdbc = jdbc;
        this.rowTransaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        this.rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 评定合法值与页面/核心服务一致：数据字典 de_rating 启用项，字典缺失时回退默认三档。 */
    private List<String> ratingOptions() {
        List<String> labels = jdbc.queryForList(
                "SELECT dict_label FROM sys_dict WHERE dict_type='de_rating' AND enabled=1 ORDER BY sort,id", String.class);
        return labels.isEmpty() ? Arrays.asList("好", "中", "一般") : labels;
    }

    public byte[] template() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = ExcelIo.createSheet(workbook, SHEET_NAME, HEADERS, 3, 5);
            CellStyle text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            // Text preserves long identities and allows the documented date spellings without conversion by Excel.
            sheet.setDefaultColumnStyle(3, text);
            sheet.setDefaultColumnStyle(5, text);
            Row blank = sheet.createRow(1);
            for (int c = 0; c < HEADERS.length; c++) blank.createCell(c).setCellStyle(text);
            DataValidationHelper helper = sheet.getDataValidationHelper();
            ExcelIo.addValidation(sheet, helper, helper.createIntegerConstraint(
                    DataValidationConstraint.OperatorType.BETWEEN, "1900", "9999"), 0, "年份范围", "请填写1900至9999的整数年份");
            ExcelIo.addValidation(sheet, helper, helper.createExplicitListConstraint(
                    new String[]{"第1季度", "第2季度", "第3季度", "第4季度"}), 1, "季度范围", "请填写第1季度至第4季度");
            List<String> ratingOptions = ratingOptions();
            ExcelIo.addValidation(sheet, helper, helper.createExplicitListConstraint(
                    ratingOptions.toArray(new String[0])), 4, "评定范围", "评定仅允许" + String.join("、", ratingOptions));
            Sheet notes = workbook.createSheet("填写说明");
            String[] explanations = {IMPORT_RULE,
                    "第一张工作表第一行必须包含六项表头：年份、季度、姓名、身份证号码、评定、评比日期；请保留表头。",
                    "年份1900至9999；季度可填1至4、Q1至Q4、一季度、第一季度等。评定以数据字典(de_rating)启用项为准，当前允许：" + String.join("、", ratingOptions) + "。",
                    "评比日期可填完整年月日，如20260901、2026-09-01、2026/9/1、2026.9.1、2026年9月1日，亦支持有效Excel日期。",
                    "评比日期允许跨季度补评，不按日期自动改变所填年份或季度。",
                    "身份证号码列必须设为文本后填写；数值或科学计数格式可能丢失精度，请从原始人员资料重新填写。",
                    "每行独立校验并提交，失败行不写入；其它合法行继续。导入结果列明新增、更新、失败数量及Excel行号。",
                    "文件不超过10MiB，数据不超过50000行、工作表不超过16张、每张表不超过64列。模板无真实人员示例。"};
            for (int i = 0; i < explanations.length; i++) notes.createRow(i).createCell(0).setCellValue(explanations[i]);
            notes.setColumnWidth(0, 120 * 256);
            return ExcelIo.bytes(workbook);
        }
    }

    public Map<String, Object> importExcel(InputStream input) throws IOException {
        byte[] uploaded = ExcelIo.readLimited(input);
        try (Workbook workbook = ExcelIo.openWorkbook(uploaded)) {
            // Complete structural validation before the first row transaction or any core service call.
            ExcelIo.validateDimensions(workbook);
            Sheet sheet = workbook.getSheetAt(0);
            int[] columns = ExcelIo.requiredColumns(sheet.getRow(0), HEADERS);
            ExcelDates dates = new ExcelDates(workbook);
            List<String> ratingOptions = ratingOptions();
            String ratingRule = "评定仅允许" + String.join("、", ratingOptions);
            int added = 0, updated = 0, failed = 0;
            List<String> errors = new ArrayList<String>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (ExcelIo.empty(row)) continue;
                Map<String, String> value = new LinkedHashMap<String, String>();
                try {
                    value.put("year", parseYear(ExcelIo.scalar(row.getCell(columns[0]), "年份", "必须为整数或季度文本，不支持日期或公式推测")));
                    value.put("quarter", parseQuarter(ExcelIo.scalar(row.getCell(columns[1]), "季度", "必须为整数或季度文本，不支持日期或公式推测")));
                    value.put("personnelName", ExcelIo.text(row.getCell(columns[2]), "姓名"));
                    value.put("idCard", ExcelIo.identity(row.getCell(columns[3])));
                    String rating = ExcelIo.text(row.getCell(columns[4]), "评定");
                    if (!ratingOptions.contains(rating)) {
                        throw new IllegalArgumentException(ratingRule);
                    }
                    value.put("rating", rating);
                    String date = dates.read(row.getCell(columns[5]), "评比日期");
                    if (date.isEmpty()) throw new IllegalArgumentException("评比日期不能为空");
                    value.put("ratingDate", date);
                    Map<String, Object> saved = rowTransaction.execute(status -> {
                        Map<String, Object> result = records.importRecord(value);
                        if (result == null || !(result.get("updated") instanceof Boolean)) {
                            throw new IllegalStateException("导入服务未返回更新标识");
                        }
                        return result;
                    });
                    if (Boolean.TRUE.equals(saved.get("updated"))) updated++; else added++;
                } catch (ForbiddenException ex) {
                    failed++;
                    errors.add("第" + (r + 1) + "行：无权导入该人员的评比记录");
                } catch (IllegalArgumentException ex) {
                    failed++;
                    errors.add("第" + (r + 1) + "行：" + ExcelIo.safeError(ex, value));
                } catch (RuntimeException ex) {
                    // Never return JDBC exception text: it can contain complete identity parameters.
                    failed++;
                    errors.add("第" + (r + 1) + "行：保存失败，该行已回滚，请检查数据后重试");
                }
            }
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("added", added);
            result.put("updated", updated);
            result.put("failed", failed);
            result.put("errors", errors);
            result.put("rule", IMPORT_RULE);
            return result;
        }
    }

    /** ids refer to assessment-record IDs, never personnel IDs. Authorization completes before workbook creation. */
    public byte[] export(String ids, Map<String, String> filters) throws IOException {
        List<DoubleExcellenceRecord> selected = new ArrayList<DoubleExcellenceRecord>();
        if (ids != null) {
            Set<Long> recordIds = ExcelIo.parseIds(ids, "评比记录"); // Reject malformed record IDs before any personnel/record reads.
            Set<Long> personnel = records.selectedPersonnel(filters);
            for (Long id : recordIds) {
                DoubleExcellenceRecord record = records.get(id);
                if (record == null) throw new IllegalArgumentException("所选评比记录不存在，请刷新后重试");
                if (personnel != null && !personnel.contains(record.getPersonnelId())) {
                    throw new IllegalArgumentException("所选记录不属于当前人员范围");
                }
                selected.add(record);
            }
        } else {
            Map<String, String> query = new LinkedHashMap<String, String>();
            if (filters != null) {
                for (String filter : FILTERS) {
                    String value = filters.get(filter);
                    // Preserve presence, including blank/null, so a broken selection cannot become a full export.
                    if ("personnelIds".equals(filter)) {
                        if (filters.containsKey(filter)) query.put(filter, value);
                    } else if (value != null && !value.trim().isEmpty()) query.put(filter, value.trim());
                }
            }
            if (query.containsKey("year")) query.put("year", parseYear(query.get("year")));
            if (query.containsKey("quarter")) query.put("quarter", parseQuarter(query.get("quarter")));
            query.put("size", String.valueOf(PAGE_SIZE));
            for (int page = 1; ; page++) {
                query.put("page", String.valueOf(page));
                Map<String, Object> result = records.query(query);
                long total = ((Number) result.get("total")).longValue();
                if (total > MAX_ROWS) throw new IllegalArgumentException("导出最多50000条评比记录，请缩小筛选范围");
                List<?> rows = (List<?>) result.get("list");
                if (selected.size() + rows.size() > MAX_ROWS) {
                    throw new IllegalArgumentException("导出最多50000条评比记录，请缩小筛选范围");
                }
                for (Object row : rows) selected.add((DoubleExcellenceRecord) row);
                if (selected.size() >= total) break;
                if (rows.isEmpty()) throw new IllegalArgumentException("导出期间记录发生变化，请刷新后重试");
            }
        }
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = ExcelIo.createSheet(workbook, SHEET_NAME, HEADERS, 3, 5);
            int index = 1;
            for (DoubleExcellenceRecord record : selected) {
                Row row = sheet.createRow(index++);
                String[] values = {ExcelIo.string(record.getYear()), "第" + parseQuarter(ExcelIo.string(record.getQuarter())) + "季度",
                        ExcelIo.string(record.getPersonnelName()), ExcelIo.string(record.getIdCard()), ExcelIo.string(record.getRating()),
                        ExcelDates.normalize(ExcelIo.string(record.getRatingDate()), "评比日期")};
                // Always write literal STRING cells, including names beginning with =,+,-,@.
                for (int c = 0; c < values.length; c++) row.createCell(c, CellType.STRING).setCellValue(values[c]);
            }
            return ExcelIo.bytes(workbook);
        }
    }

    static String parseYear(String value) {
        String year = value == null ? "" : value.trim();
        if (!year.matches("[0-9]{4}")) throw new IllegalArgumentException("年份必须为1900至9999的整数");
        int number = Integer.parseInt(year);
        if (number < 1900 || number > 9999) throw new IllegalArgumentException("年份必须为1900至9999的整数");
        return String.valueOf(number);
    }

    static String parseQuarter(String value) {
        String quarter = value == null ? "" : value.trim();
        if (quarter.matches("[1-4]")) return quarter;
        if (quarter.matches("[Qq][1-4]")) return quarter.substring(1);
        if (quarter.matches("第?[一二三四1-4]季度")) {
            String number = quarter.replace("第", "").replace("季度", "");
            int chinese = "一二三四".indexOf(number);
            return chinese >= 0 ? String.valueOf(chinese + 1) : number;
        }
        throw new IllegalArgumentException("季度必须为1至4、Q1至Q4或第一季度至第四季度");
    }
}
