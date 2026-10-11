package com.szjx.personnel.server.util;

import com.szjx.personnel.server.model.Personnel;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Excel(xlsx)导入导出工具,基于 Apache POI(纯 Java,跨平台)
 */
public final class ExcelUtils {

    /** 列顺序(与导入模板一致) */
    private static final String[] HEADERS = {
            "姓名", "性别", "身份证号", "出生日期", "手机号", "部门", "军衔",
            "职务", "状态", "入伍日期", "家庭住址", "备注",
            "血型", "民族", "政治面貌", "婚姻状况", "学历", "是否独生子女", "籍贯", "身高(cm)", "体重(kg)", "党团时间",
            "配偶姓名", "配偶工作单位", "配偶电话", "配偶身份证号", "配偶职业", "结婚日期"
    };

    private ExcelUtils() {
    }

    /**
     * 导出人员为 xlsx 字节
     */
    public static byte[] exportXlsx(List<Personnel> list,
                                    Function<Long, String> deptNameResolver) throws IOException {
        Workbook wb = new XSSFWorkbook();
        try {
            Sheet sheet = wb.createSheet("人员信息");

            // 表头样式:加粗 + 蓝底白字 + 边框
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);

            // 数据样式:边框
            CellStyle dataStyle = wb.createCellStyle();
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            Row header = sheet.createRow(0);
            header.setHeightInPoints(24);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Personnel p : list) {
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(20);
                String[] cells = {
                        p.getName(), p.getGender(), p.getIdCard(), p.getBirthday(), p.getPhone(),
                        deptNameResolver.apply(p.getDepartmentId()),
                        p.getRank(), p.getPost(), p.getStatus(), p.getEntryDate(),
                        p.getAddress(), p.getRemark(),
                        p.getBloodType(), p.getNation(), p.getPoliticalStatus(),
                        p.getMaritalStatus(), p.getEducation(), p.getOnlyChild(),
                        p.getNativePlace(),
                        p.getHeight() == null ? "" : String.valueOf(p.getHeight()),
                        p.getWeight() == null ? "" : String.valueOf(p.getWeight()),
                        p.getPartyJoinDate(),
                        p.getSpouseName(), p.getSpouseWork(), p.getSpousePhone(),
                        p.getSpouseIdCard(), p.getSpouseJob(), p.getMarriageDate()
                };
                for (int i = 0; i < cells.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(cells[i] == null ? "" : cells[i]);
                    cell.setCellStyle(dataStyle);
                }
            }

            // 列宽自适应(中文按 2 个字符宽估算)
            for (int i = 0; i < HEADERS.length; i++) {
                int maxLen = HEADERS[i].length();
                for (int r = 1; r < rowIdx; r++) {
                    String v = sheet.getRow(r).getCell(i).getStringCellValue();
                    int len = 0;
                    for (char ch : v.toCharArray()) {
                        len += ch > 0xFF ? 2 : 1;
                    }
                    maxLen = Math.max(maxLen, len);
                }
                sheet.setColumnWidth(i, Math.min(255 * 256, Math.max(10 * 256, (maxLen + 2) * 256)));
            }
            sheet.createFreezePane(0, 1); // 冻结表头

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } finally {
            wb.close();
        }
    }

    /**
     * 解析 xlsx 为人员列表(第一行为列头,列名与导出模板一致)
     */
    public static List<Personnel> importXlsx(InputStream in) throws IOException {
        Workbook wb = new XSSFWorkbook(in);
        try {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                return new ArrayList<Personnel>();
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            int[] idx = new int[HEADERS.length];
            for (int i = 0; i < HEADERS.length; i++) {
                idx[i] = -1;
            }
            DataFormatter formatter = new DataFormatter();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String h = formatter.formatCellValue(headerRow.getCell(c)).trim();
                for (int i = 0; i < HEADERS.length; i++) {
                    if (HEADERS[i].equals(h)) {
                        idx[i] = c;
                    }
                }
            }
            if (idx[0] < 0) {
                throw new IOException("未找到\"姓名\"列,请使用系统导出的模板");
            }

            List<Personnel> result = new ArrayList<Personnel>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                if (isEmptyRow(row, formatter)) {
                    continue;
                }
                Personnel p = new Personnel();
                p.setName(cellText(row, idx[0], formatter));
                p.setGender(cellText(row, idx[1], formatter));
                p.setIdCard(cellText(row, idx[2], formatter));
                p.setBirthday(cellText(row, idx[3], formatter));
                p.setPhone(cellText(row, idx[4], formatter));
                p.setDepartmentName(cellText(row, idx[5], formatter));
                p.setRank(cellText(row, idx[6], formatter));
                p.setPost(cellText(row, idx[7], formatter));
                p.setStatus(cellText(row, idx[8], formatter));
                p.setEntryDate(cellText(row, idx[9], formatter));
                p.setAddress(cellText(row, idx[10], formatter));
                p.setRemark(cellText(row, idx[11], formatter));
                p.setBloodType(cellText(row, idx[12], formatter));
                p.setNation(cellText(row, idx[13], formatter));
                p.setPoliticalStatus(cellText(row, idx[14], formatter));
                p.setMaritalStatus(cellText(row, idx[15], formatter));
                p.setEducation(cellText(row, idx[16], formatter));
                p.setOnlyChild(cellText(row, idx[17], formatter));
                p.setNativePlace(cellText(row, idx[18], formatter));
                p.setHeight(parseNumber(cellText(row, idx[19], formatter)));
                p.setWeight(parseNumber(cellText(row, idx[20], formatter)));
                p.setPartyJoinDate(cellText(row, idx[21], formatter));
                p.setSpouseName(cellText(row, idx[22], formatter));
                p.setSpouseWork(cellText(row, idx[23], formatter));
                p.setSpousePhone(cellText(row, idx[24], formatter));
                p.setSpouseIdCard(cellText(row, idx[25], formatter));
                p.setSpouseJob(cellText(row, idx[26], formatter));
                p.setMarriageDate(cellText(row, idx[27], formatter));
                result.add(p);
            }
            return result;
        } finally {
            wb.close();
        }
    }

    private static boolean isEmptyRow(Row row, DataFormatter formatter) {
        for (int c = 0; c < row.getLastCellNum(); c++) {
            String v = cellText(row, c, formatter);
            if (v != null && !v.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static Double parseNumber(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cellText(Row row, int col, DataFormatter formatter) {
        if (col < 0 || row == null) {
            return "";
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                Date d = cell.getDateCellValue();
                return new SimpleDateFormat("yyyy-MM-dd").format(d);
            }
            // 数字(如身份证号存为数字时):避免科学计数法,转完整数字串
            return new BigDecimal(cell.getNumericCellValue()).toPlainString();
        }
        if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        String v = cell.getStringCellValue();
        return v == null ? "" : v.trim();
    }

    /**
     * 便捷:从字节数组解析
     */
    public static List<Personnel> importXlsx(byte[] bytes) throws IOException {
        return importXlsx(new ByteArrayInputStream(bytes));
    }
}
