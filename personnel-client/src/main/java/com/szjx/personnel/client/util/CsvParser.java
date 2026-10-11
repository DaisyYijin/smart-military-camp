package com.szjx.personnel.client.util;

import com.szjx.personnel.client.model.PersonnelItem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 导入解析(自动识别 UTF-8 / GBK 编码,兼容 Win7 Excel 导出的 CSV)
 */
public final class CsvParser {

    private static final String[] HEADERS = {
            "姓名", "性别", "身份证号", "出生日期", "手机号", "部门", "军衔",
            "职务", "状态", "入伍日期", "家庭住址", "备注"
    };

    private CsvParser() {
    }

    public static List<PersonnelItem> parse(File file) throws IOException {
        byte[] bytes = readBytes(file);
        String content = decode(bytes);
        List<List<String>> rows = parseRows(content);
        if (rows.isEmpty()) {
            return new ArrayList<PersonnelItem>();
        }
        List<String> header = rows.get(0);
        int[] idx = new int[HEADERS.length];
        for (int i = 0; i < HEADERS.length; i++) {
            idx[i] = header.indexOf(HEADERS[i]);
        }
        List<PersonnelItem> result = new ArrayList<PersonnelItem>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.size() == 1 && row.get(0).trim().isEmpty()) {
                continue;
            }
            PersonnelItem p = new PersonnelItem();
            p.setName(get(row, idx[0]));
            p.setGender(get(row, idx[1]));
            p.setIdCard(get(row, idx[2]));
            p.setBirthday(get(row, idx[3]));
            p.setPhone(get(row, idx[4]));
            p.setDepartmentName(get(row, idx[5]));
            p.setRank(get(row, idx[6]));
            p.setPost(get(row, idx[7]));
            p.setStatus(get(row, idx[8]));
            p.setEntryDate(get(row, idx[9]));
            p.setAddress(get(row, idx[10]));
            p.setRemark(get(row, idx[11]));
            result.add(p);
        }
        return result;
    }

    private static String get(List<String> row, int index) {
        if (index < 0 || index >= row.size()) {
            return "";
        }
        String v = row.get(index);
        return v == null ? "" : v.trim();
    }

    private static String decode(byte[] bytes) {
        // 有 UTF-8 BOM 则按 UTF-8
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        // 尝试严格 UTF-8 解码,失败则按 GBK
        try {
            Charset utf8 = StandardCharsets.UTF_8;
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes);
            java.nio.charset.CodingErrorAction report = java.nio.charset.CodingErrorAction.REPORT;
            java.nio.charset.CharsetDecoder decoder = utf8.newDecoder()
                    .onMalformedInput(report)
                    .onUnmappableCharacter(report);
            return decoder.decode(buf).toString();
        } catch (Exception e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }

    private static byte[] readBytes(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static List<List<String>> parseRows(String content) {
        if (content.startsWith("\ufeff")) {
            content = content.substring(1);
        }
        List<List<String>> rows = new ArrayList<List<String>>();
        List<String> row = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        while (i < content.length()) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\r' || ch == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<String>();
                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                field.append(ch);
            }
            i++;
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
