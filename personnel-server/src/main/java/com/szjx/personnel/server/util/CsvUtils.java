package com.szjx.personnel.server.util;

import com.szjx.personnel.server.model.Personnel;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 工具(UTF-8,含 BOM,Excel 可直接打开;导入按列头识别)
 */
public final class CsvUtils {

    private CsvUtils() {
    }

    /** 导出列顺序(与导入模板一致) */
    private static final String[] HEADERS = {
            "姓名", "性别", "身份证号", "出生日期", "手机号", "单位", "军衔",
            "职务", "状态", "入伍日期", "家庭住址", "备注"
    };

    public static String toCsv(List<Personnel> list, java.util.function.Function<Long, String> deptNameResolver) {
        StringWriter sw = new StringWriter();
        try {
            sw.write('\ufeff'); // UTF-8 BOM,保证 Excel 中文不乱码
            writeRow(sw, HEADERS);
            for (Personnel p : list) {
                writeRow(sw,
                        p.getName(),
                        p.getGender(),
                        p.getIdCard(),
                        p.getBirthday(),
                        p.getPhone(),
                        deptNameResolver.apply(p.getDepartmentId()),
                        p.getRank(),
                        p.getPost(),
                        p.getStatus(),
                        p.getEntryDate(),
                        p.getAddress(),
                        p.getRemark());
            }
        } catch (IOException e) {
            throw new RuntimeException("导出失败", e);
        }
        return sw.toString();
    }

    /**
     * 解析 CSV(支持 UTF-8 BOM / GBK 需调用方先解码),按列头映射
     */
    public static List<Personnel> parseCsv(String content) throws IOException {
        if (content.startsWith("\ufeff")) {
            content = content.substring(1);
        }
        List<List<String>> rows = new ArrayList<List<String>>();
        Reader reader = new StringReader(content);
        try {
            List<String> row = new ArrayList<String>();
            StringBuilder field = new StringBuilder();
            boolean inQuotes = false;
            int c;
            while ((c = reader.read()) != -1) {
                char ch = (char) c;
                if (inQuotes) {
                    if (ch == '"') {
                        int next = reader.read();
                        if (next == '"') {
                            field.append('"');
                        } else {
                            inQuotes = false;
                            if (next == -1) {
                                row.add(field.toString());
                                field.setLength(0);
                            } else if (next == ',') {
                                row.add(field.toString());
                                field.setLength(0);
                            } else if (next == '\r' || next == '\n') {
                                row.add(field.toString());
                                field.setLength(0);
                                if (next == '\r') {
                                    int lf = reader.read();
                                    if (lf != '\n' && lf != -1) {
                                        // 忽略:不应出现
                                    }
                                }
                                rows.add(row);
                                row = new ArrayList<String>();
                            }
                        }
                    } else {
                        field.append(ch);
                    }
                } else {
                    if (ch == '"') {
                        inQuotes = true;
                    } else if (ch == ',') {
                        row.add(field.toString());
                        field.setLength(0);
                    } else if (ch == '\r' || ch == '\n') {
                        row.add(field.toString());
                        field.setLength(0);
                        rows.add(row);
                        row = new ArrayList<String>();
                        if (ch == '\r') {
                            reader.read(); // 吞掉 \n
                        }
                    } else {
                        field.append(ch);
                    }
                }
            }
            if (field.length() > 0 || !row.isEmpty()) {
                row.add(field.toString());
                rows.add(row);
            }
        } finally {
            reader.close();
        }

        if (rows.isEmpty()) {
            return new ArrayList<Personnel>();
        }
        // 列头定位
        List<String> header = rows.get(0);
        int idxName = header.indexOf("姓名");
        int idxGender = header.indexOf("性别");
        int idxIdCard = header.indexOf("身份证号");
        int idxBirthday = header.indexOf("出生日期");
        int idxPhone = header.indexOf("手机号");
        int idxDept = header.indexOf("单位");
        int idxRank = header.indexOf("军衔");
        int idxPost = header.indexOf("职务");
        int idxStatus = header.indexOf("状态");
        int idxEntry = header.indexOf("入伍日期");
        int idxAddress = header.indexOf("家庭住址");
        int idxRemark = header.indexOf("备注");

        List<Personnel> result = new ArrayList<Personnel>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (r.size() == 1 && r.get(0).trim().isEmpty()) {
                continue; // 空行
            }
            Personnel p = new Personnel();
            p.setName(get(r, idxName));
            p.setGender(get(r, idxGender));
            p.setIdCard(get(r, idxIdCard));
            p.setBirthday(get(r, idxBirthday));
            p.setPhone(get(r, idxPhone));
            p.setDepartmentId(null);
            p.setDepartmentName(get(r, idxDept));
            p.setRank(get(r, idxRank));
            p.setPost(get(r, idxPost));
            p.setStatus(get(r, idxStatus));
            p.setEntryDate(get(r, idxEntry));
            p.setAddress(get(r, idxAddress));
            p.setRemark(get(r, idxRemark));
            result.add(p);
        }
        return result;
    }

    private static String get(List<String> row, int idx) {
        if (idx < 0 || idx >= row.size()) {
            return "";
        }
        String v = row.get(idx);
        return v == null ? "" : v.trim();
    }

    private static void writeRow(Writer w, String... cells) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String v = cells[i] == null ? "" : cells[i];
            v = escapeFormula(v);
            if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        sb.append("\r\n");
        w.write(sb.toString());
    }

    /**
     * 防 CSV 公式注入:以 = + - @ 开头的单元格加单引号前缀,
     * 避免 Excel 打开时执行公式
     */
    private static String escapeFormula(String v) {
        if (v.length() > 0 && (v.charAt(0) == '=' || v.charAt(0) == '+' ||
                v.charAt(0) == '-' || v.charAt(0) == '@')) {
            return "'" + v;
        }
        return v;
    }
}
