package com.szjx.personnel.server.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Excel 导入导出共用管线：读取上限、工作簿打开、结构校验、表头定位、单元格读取、
 * 错误脱敏、导出 ID 解析、表头样式与数据验证。业务差异(表头/文案/字典)由调用方以参数声明。
 */
public final class ExcelIo {

    public static final int MAX_ROWS = 50000;
    public static final int MAX_COLUMNS = 64;
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    private ExcelIo() {
    }

    public static byte[] readLimited(InputStream input) throws IOException {
        if (input == null) throw new IllegalArgumentException("请选择非空Excel文件");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (out.size() + read > MAX_BYTES) throw new IllegalArgumentException("Excel文件不得超过10MiB");
            out.write(buffer, 0, read);
        }
        if (out.size() == 0) throw new IllegalArgumentException("请选择非空Excel文件");
        return out.toByteArray();
    }

    public static Workbook openWorkbook(byte[] bytes) {
        try {
            return WorkbookFactory.create(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("文件不是有效的Excel工作簿，或文件已损坏、加密；请上传未加密的.xls或.xlsx文件");
        }
    }

    /** 完整结构校验先于任何业务读取：工作表数、行数、列数。 */
    public static void validateDimensions(Workbook workbook) {
        if (workbook.getNumberOfSheets() < 1 || workbook.getNumberOfSheets() > 16) {
            throw new IllegalArgumentException("Excel必须包含1至16张工作表");
        }
        for (Sheet sheet : workbook) {
            if (sheet.getLastRowNum() > MAX_ROWS) throw new IllegalArgumentException("Excel每张表最多50000行数据（另加一行表头）");
            for (Row row : sheet) {
                if (row.getLastCellNum() > MAX_COLUMNS) throw new IllegalArgumentException("Excel每张表最多64列，请删除多余列");
            }
        }
    }

    public static int[] requiredColumns(Row header, String[] headers) {
        if (header == null) throw new IllegalArgumentException("Excel缺少第一行表头，必须包含：" + String.join("、", headers));
        int[] columns = new int[headers.length];
        Arrays.fill(columns, -1);
        for (Cell cell : header) {
            if (cell.getCellType() != CellType.STRING) continue;
            String name = cell.getStringCellValue().trim();
            for (int i = 0; i < headers.length; i++) {
                if (!headers[i].equals(name)) continue;
                if (columns[i] != -1) throw new IllegalArgumentException("Excel表头重复：" + headers[i]);
                columns[i] = cell.getColumnIndex();
            }
        }
        for (int i = 0; i < columns.length; i++) {
            if (columns[i] < 0) throw new IllegalArgumentException("Excel缺少必须列：" + headers[i]);
        }
        return columns;
    }

    public static boolean empty(Row row) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.BLANK) continue;
            if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().trim().isEmpty()) continue;
            return false;
        }
        return true;
    }

    /** 数值或文本单元格读取为 0-9999 整数文本；rule 为业务侧错误提示尾缀。 */
    public static String scalar(Cell cell, String label, String rule) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (Double.isFinite(value) && value == Math.rint(value) && value >= 0 && value <= 9999) {
                return String.valueOf((long) value);
            }
        }
        throw new IllegalArgumentException(label + rule);
    }

    public static String text(Cell cell, String label) {
        if (cell == null || cell.getCellType() == CellType.BLANK) throw new IllegalArgumentException(label + "不能为空");
        if (cell.getCellType() != CellType.STRING) throw new IllegalArgumentException(label + "必须使用文本填写");
        String value = cell.getStringCellValue().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(label + "不能为空");
        if (value.length() > 200) throw new IllegalArgumentException(label + "过长，最多200个字符");
        return value;
    }

    public static String identity(Cell cell) {
        String value = text(cell, "身份证号码（请设为文本，勿使用数值或科学计数）");
        if (value.matches("(?i)[+-]?[0-9]+(?:\\.[0-9]+)?e[+-]?[0-9]+")) {
            throw new IllegalArgumentException("身份证号码不能使用科学计数法，请以文本填写完整号码");
        }
        return value;
    }

    /** 导入行级错误脱敏：隐去身份证号与姓名，压平换行并截断。 */
    public static String safeError(IllegalArgumentException ex, Map<String, String> value) {
        String message = ex.getMessage() == null ? "数据无效" : ex.getMessage();
        for (String key : Arrays.asList("idCard", "personnelName")) {
            String sensitive = value.get(key);
            if (sensitive != null && !sensitive.isEmpty()) message = message.replace(sensitive, "[已隐藏]");
        }
        message = message.replace('\r', ' ').replace('\n', ' ');
        return message.length() > 200 ? message.substring(0, 200) : message;
    }

    /** 导出 ID 集解析：entityName 用于超量错误文案（如"考评记录"）。 */
    public static Set<Long> parseIds(String ids, String entityName) {
        String[] parts = ids.split(",", -1);
        if (parts.length > MAX_ROWS) throw new IllegalArgumentException("导出最多50000条" + entityName);
        Set<Long> result = new LinkedHashSet<Long>();
        for (String part : parts) {
            String id = part.trim();
            if (!id.matches("[1-9][0-9]*")) throw new IllegalArgumentException("导出ID必须为逗号分隔的正整数");
            try {
                result.add(Long.valueOf(id));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("导出ID超出有效范围");
            }
        }
        return result;
    }

    /** 统一导出表头：粗体表头、冻结首行；wideColumn 列宽 26（证件号），dateColumn 列宽 18，其余 15。 */
    public static Sheet createSheet(Workbook workbook, String name, String[] headers, int wideColumn, int dateColumn) {
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = header.createCell(c, CellType.STRING);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(style);
            sheet.setColumnWidth(c, (c == wideColumn ? 26 : c == dateColumn ? 18 : 15) * 256);
        }
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    public static void addValidation(Sheet sheet, DataValidationHelper helper,
                                     DataValidationConstraint constraint, int column, String title, String message) {
        DataValidation validation = helper.createValidation(constraint, new CellRangeAddressList(1, MAX_ROWS, column, column));
        validation.setEmptyCellAllowed(true);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox(title, message);
        sheet.addValidationData(validation);
    }

    public static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static byte[] bytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }
}
