package com.szjx.personnel.server.util;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.Date1904Support;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Workbook;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict date conversion at Excel import boundaries only; never apply to ordinary columns or API values. */
public final class ExcelDates {
    private static final Pattern COMPACT = Pattern.compile("^([0-9]{4})([0-9]{2})([0-9]{2})$");
    private static final Pattern SEPARATED = Pattern.compile("^([0-9]{4})([-/.])([0-9]{1,2})\\2([0-9]{1,2})$");
    private static final Pattern CHINESE = Pattern.compile("^([0-9]{4})年([0-9]{1,2})月([0-9]{1,2})日$");
    private static final LocalDate BASE_1900 = LocalDate.of(1899, 12, 31);
    private static final LocalDate BASE_1904 = LocalDate.of(1904, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    private final Workbook workbook;
    private final FormulaEvaluator evaluator;
    private final boolean date1904;

    /** One reader/evaluator per imported workbook. External workbooks are not registered or opened. */
    public ExcelDates(Workbook workbook) {
        if (workbook == null) throw new IllegalArgumentException("工作簿不能为空");
        this.workbook = workbook;
        this.date1904 = workbook instanceof Date1904Support
                ? ((Date1904Support) workbook).isDate1904()
                : workbook instanceof HSSFWorkbook
                && ((HSSFWorkbook) workbook).getInternalWorkbook().isUsing1904DateWindowing();
        this.evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        this.evaluator.setIgnoreMissingWorkbooks(false);
    }

    /** Missing, blank and empty-string date cells remain empty; requiredness belongs to the caller. */
    public String read(Cell cell, String label) {
        if (cell == null) return "";
        if (cell.getSheet().getWorkbook() != workbook) throw invalid(label, "单元格不属于当前工作簿");
        switch (cell.getCellType()) {
            case BLANK: return "";
            case STRING: return normalize(cell.getStringCellValue(), label);
            case NUMERIC: return numeric(cell.getNumericCellValue(), cell, label);
            case FORMULA:
                CellValue value;
                try {
                    // evaluate (not evaluateInCell) preserves the imported formula and its cached result.
                    value = evaluator.evaluate(cell);
                } catch (RuntimeException ex) {
                    throw invalid(label, "日期公式无法在本工作簿内计算");
                }
                if (value == null) throw invalid(label, "日期公式没有结果");
                switch (value.getCellType()) {
                    case BLANK: return "";
                    case STRING: return normalize(value.getStringValue(), label);
                    case NUMERIC:
                        // POI 4.1.2 DATE and several date functions hard-code the 1900 system.
                        // Reject rather than silently shifting a 1904 formula date by 1462 days.
                        if (date1904) throw invalid(label, "1904日期系统不支持数值日期公式，请转为日期值或日期文本");
                        return numeric(value.getNumberValue(), cell, label);
                    default: throw invalid(label, "日期公式结果不是有效日期");
                }
            default: throw invalid(label, "单元格不是有效日期");
        }
    }

    /** Accept full year-first dates only. All successful non-empty values are yyyy-MM-dd, years 0001..9999. */
    public static String normalize(String value, String label) {
        String text = value == null ? "" : trim(value);
        if (text.isEmpty()) return "";
        Matcher match = COMPACT.matcher(text);
        if (match.matches()) return date(match.group(1), match.group(2), match.group(3), label);
        match = SEPARATED.matcher(text);
        if (match.matches()) return date(match.group(1), match.group(3), match.group(4), label);
        match = CHINESE.matcher(text);
        if (match.matches()) return date(match.group(1), match.group(2), match.group(3), label);
        throw invalid(label, "请输入完整年月日，如20160901、2016-09-01、2016/9/1、2016.9.1或2016年9月1日");
    }

    private String numeric(double value, Cell cell, String label) {
        if (!Double.isFinite(value)) throw invalid(label, "日期数值无效");
        // A user may type yyyyMMdd into a preformatted date column. Real Excel serials
        // through 9999-12-31 have at most seven digits, so these ranges never overlap.
        if (value == Math.rint(value) && value >= 10000000 && value <= 99999999) {
            return normalize(Long.toString((long) value), label);
        }
        CellStyle style = cell.getCellStyle();
        // Inspect the style rather than isCellDateFormatted, which also reads a formula's stale cache.
        if (style != null && DateUtil.isADateFormat(style.getDataFormat(), style.getDataFormatString())) {
            double day = Math.floor(value);
            long max = ChronoUnit.DAYS.between(date1904 ? BASE_1904 : BASE_1900, MAX_DATE) + (date1904 ? 0 : 1);
            if (day < (date1904 ? 0 : 1) || day > max) throw invalid(label, "Excel日期序列超出有效范围");
            if (!date1904 && day == 60) throw invalid(label, "Excel日期1900-02-29不存在");
            long offset = (long) day;
            if (!date1904 && offset > 60) offset--;
            return (date1904 ? BASE_1904 : BASE_1900).plusDays(offset).toString();
        }
        // Plain numbers are never inferred Excel serials/timestamps.
        throw invalid(label, "普通数值日期必须为八位年月日");
    }

    private static String date(String year, String month, String day, String label) {
        try {
            int y = Integer.parseInt(year);
            if (y < 1 || y > 9999) throw invalid(label, "年份必须为0001至9999");
            return LocalDate.of(y, Integer.parseInt(month), Integer.parseInt(day)).toString();
        } catch (DateTimeException ex) {
            throw invalid(label, "日期不存在或超出有效范围");
        }
    }

    private static String trim(String value) {
        int start = 0, end = value.length();
        while (start < end && whitespace(value.charAt(start))) start++;
        while (end > start && whitespace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private static boolean whitespace(char ch) {
        return Character.isWhitespace(ch) || Character.isSpaceChar(ch);
    }

    private static IllegalArgumentException invalid(String label, String detail) {
        return new IllegalArgumentException((label == null ? "日期" : label) + "格式不正确：" + detail);
    }
}
