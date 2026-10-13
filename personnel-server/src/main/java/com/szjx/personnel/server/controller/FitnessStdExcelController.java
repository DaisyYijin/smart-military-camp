package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.util.*;

/**
 * 体能标准(评分矩阵)与体重标准(BMI/身高×年龄表)的 Excel 模板下载与导入。
 *
 * 评分矩阵模板:每个项目一个 Sheet,Sheet 名=项目名;
 *   首列"分数",其后各列为年龄段;行=分数,单元格=达标值;unit 在 Sheet 名后括号或默认 count。
 * 体重标准模板:一个 Sheet"体重标准";
 *   首列"身高(米)",其后各列为年龄段;行=身高,单元格=下限~上限;男女各一个 Sheet(男标准/女标准)。
 */
@RestController
@RequestMapping("/api/fitness-std")
public class FitnessStdExcelController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public FitnessStdExcelController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ================= 评分矩阵 模板/导入 ================= */

    /** 评分矩阵模板:按现有项目导出可编辑矩阵(空库给示例) */
    @GetMapping("/matrix/template")
    public ResponseEntity<byte[]> matrixTemplate() throws Exception {
        List<String> items = jdbc.queryForList("SELECT DISTINCT item FROM fitness_matrix ORDER BY item", String.class);
        Workbook wb = new XSSFWorkbook();
        try {
            CellStyle head = headStyle(wb);
            if (items.isEmpty()) {
                items = Collections.singletonList("示例项目(引体向上)");
            }
            for (String item : items) {
                Sheet sh = wb.createSheet(sheetName(item));
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT age_group, score, value FROM fitness_matrix WHERE item=? ORDER BY score DESC, id", item);
                String unit = rows.isEmpty() ? "count"
                        : String.valueOf(rows.get(0).get("unit") == null ? "count" : rows.get(0).get("unit"));
                // 表头:分数 + 年龄段列(按首行出现顺序)
                LinkedHashSet<String> ageSet = new LinkedHashSet<String>();
                for (Map<String, Object> r : rows) {
                    ageSet.add(String.valueOf(r.get("age_group")));
                }
                List<String> ages = new ArrayList<String>(ageSet);
                Row hr = sh.createRow(0);
                Cell c0 = hr.createCell(0);
                c0.setCellValue("分数");
                c0.setCellStyle(head);
                for (int i = 0; i < ages.size(); i++) {
                    Cell c = hr.createCell(i + 1);
                    c.setCellValue(ages.get(i));
                    c.setCellStyle(head);
                }
                Cell uc = hr.createCell(ages.size() + 1);
                uc.setCellValue("单位(" + unit + ")");
                uc.setCellStyle(head);
                // 数据行
                int rIdx = 1;
                for (Map<String, Object> r : rows) {
                    Row xr = sh.createRow(rIdx++);
                    xr.createCell(0).setCellValue(((Number) r.get("score")).intValue());
                    int col = ageSet.size() > 0 ? ages.indexOf(String.valueOf(r.get("age_group"))) : -1;
                    if (col >= 0) {
                        xr.createCell(col + 1).setCellValue(((Number) r.get("value")).doubleValue());
                    }
                    xr.createCell(ages.size() + 1).setCellValue(unit);
                }
                sh.setColumnWidth(0, 10 * 256);
                for (int i = 1; i <= ages.size() + 1; i++) {
                    sh.setColumnWidth(i, 13 * 256);
                }
            }
            byte[] body = bytes(wb);
            return download(body, "体能评分标准模板.xlsx");
        } finally {
            wb.close();
        }
    }

    /** 评分矩阵导入:每个 Sheet=一个项目;首列分数,其余列按表头年龄段,末列单位可省略 */
    @PostMapping("/matrix/import")
    public ApiResponse matrixImport(@RequestParam("file") MultipartFile file) throws Exception {
        Workbook wb = WorkbookFactory.create(file.getInputStream());
        try {
            DataFormatter fmt = new DataFormatter();
            int added = 0, updated = 0, failed = 0;
            List<String> errors = new ArrayList<String>();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sh = wb.getSheetAt(s);
                String raw = sh.getSheetName();
                // sheet 名支持 "项目名" 或 "项目名(count)"
                String item = raw, unit = "count";
                int p = raw.indexOf('(');
                if (p > 0 && raw.endsWith(")")) {
                    item = raw.substring(0, p).trim();
                    unit = raw.substring(p + 1, raw.length() - 1).trim();
                }
                if (!"count".equals(unit) && !"time".equals(unit)) { unit = "count"; }
                Row hr = sh.getRow(0);
                if (hr == null) { continue; }
                // 表头:0=分数,1..n-1=年龄段,末尾可能是单位列(跳过)
                List<String> ages = new ArrayList<String>();
                for (int c = 1; c < hr.getLastCellNum(); c++) {
                    String h = fmt.formatCellValue(hr.getCell(c)).trim();
                    if (h.isEmpty()) { continue; }
                    if (h.startsWith("单位")) { break; }
                    ages.add(h);
                }
                if (ages.isEmpty() || item.isEmpty()) {
                    continue;
                }
                for (int r = 1; r <= sh.getLastRowNum(); r++) {
                    Row row = sh.getRow(r);
                    if (row == null) { continue; }
                    String scoreStr = fmt.formatCellValue(row.getCell(0)).trim();
                    if (scoreStr.isEmpty()) { continue; }
                    int score;
                    try {
                        score = (int) Double.parseDouble(scoreStr);
                    } catch (Exception e) {
                        failed++;
                        if (errors.size() < 8) { errors.add(item + " 第" + (r + 1) + "行分数「" + scoreStr + "」不是数字"); }
                        continue;
                    }
                    for (int ai = 0; ai < ages.size(); ai++) {
                        String v = fmt.formatCellValue(row.getCell(ai + 1)).trim();
                        if (v.isEmpty()) { continue; }
                        double value;
                        try {
                            value = Double.parseDouble(v);
                        } catch (Exception e) {
                            failed++;
                            if (errors.size() < 8) { errors.add(item + " " + ages.get(ai) + " " + score + "分 达标值「" + v + "」不是数字"); }
                            continue;
                        }
                        int n = jdbc.update(
                                "UPDATE fitness_matrix SET value=?, unit=?, updated_at=datetime('now','localtime') WHERE item=? AND age_group=? AND score=?",
                                value, unit, item, ages.get(ai), score);
                        if (n == 0) {
                            jdbc.update("INSERT INTO fitness_matrix(item, age_group, score, value, unit) VALUES(?,?,?,?,?)",
                                    item, ages.get(ai), score, value, unit);
                            added++;
                        } else {
                            updated++;
                        }
                    }
                }
            }
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("added", added);
            result.put("updated", updated);
            result.put("failed", failed);
            if (!errors.isEmpty()) { result.put("errors", errors); }
            return ApiResponse.ok(result);
        } finally {
            wb.close();
        }
    }

    /* ================= 体重标准 模板/导入 ================= */

    @GetMapping("/weight/template")
    public ResponseEntity<byte[]> weightTemplate() throws Exception {
        String v;
        try {
            v = jdbc.queryForObject("SELECT svalue FROM sys_setting WHERE skey='bmi_standard'", String.class);
        } catch (Exception e) {
            v = null;
        }
        Map<String, Object> std = v == null ? null : mapper.readValue(v, Map.class);
        List<String> ages = new ArrayList<String>();
        Map<String, List<Map<String, Object>>> sides = new LinkedHashMap<String, List<Map<String, Object>>>();
        sides.put("男标准", null);
        sides.put("女标准", null);
        if (std != null && std.get("ages") instanceof List) {
            ages = (List<String>) std.get("ages");
        }
        if (ages.isEmpty()) {
            ages = Arrays.asList("24岁以下", "25-29岁", "30-39岁", "40-49岁", "50-59岁", "60岁以上");
        }
        if (std != null) {
            sides.put("男标准", (List<Map<String, Object>>) ((Map<String, Object>) std.getOrDefault("male", new HashMap<>())).getOrDefault("rows", new ArrayList<>()));
            sides.put("女标准", (List<Map<String, Object>>) ((Map<String, Object>) std.getOrDefault("female", new HashMap<>())).getOrDefault("rows", new ArrayList<>()));
        }

        Workbook wb = new XSSFWorkbook();
        try {
            CellStyle head = headStyle(wb);
            for (Map.Entry<String, List<Map<String, Object>>> e : sides.entrySet()) {
                Sheet sh = wb.createSheet(e.getKey());
                Row hr = sh.createRow(0);
                Cell c0 = hr.createCell(0);
                c0.setCellValue("身高(米)");
                c0.setCellStyle(head);
                for (int i = 0; i < ages.size(); i++) {
                    Cell c = hr.createCell(i + 1);
                    c.setCellValue(ages.get(i));
                    c.setCellStyle(head);
                }
                // 数据:身高行,单元格=下限~上限
                List<Map<String, Object>> rows = e.getValue();
                // 身高排序
                rows.sort((a, b) -> Double.compare(((Number) a.get("h")).doubleValue(), ((Number) b.get("h")).doubleValue()));
                int rIdx = 1;
                for (Map<String, Object> r : rows) {
                    Row xr = sh.createRow(rIdx++);
                    xr.createCell(0).setCellValue(((Number) r.get("h")).doubleValue());
                    List<?> cells = (List<?>) r.get("w");
                    for (int i = 0; i < ages.size(); i++) {
                        String cellText = "";
                        if (cells != null && i < cells.size() && cells.get(i) instanceof List) {
                            List<?> pair = (List<?>) cells.get(i);
                            if (pair.size() == 2) {
                                cellText = pair.get(0) + "~" + pair.get(1);
                            }
                        }
                        xr.createCell(i + 1).setCellValue(cellText);
                    }
                }
                sh.setColumnWidth(0, 10 * 256);
                for (int i = 1; i <= ages.size(); i++) {
                    sh.setColumnWidth(i, 14 * 256);
                }
            }
            byte[] body = bytes(wb);
            return download(body, "体重合格标准模板.xlsx");
        } finally {
            wb.close();
        }
    }

    /** 体重标准导入:男标准/女标准两个 Sheet;首列身高,其后=年龄段列,单元格=下限~上限 */
    @PostMapping("/weight/import")
    public ApiResponse weightImport(@RequestParam("file") MultipartFile file) throws Exception {
        Workbook wb = WorkbookFactory.create(file.getInputStream());
        try {
            DataFormatter fmt = new DataFormatter();
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            int totalRows = 0;
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sh = wb.getSheetAt(s);
                String name = sh.getSheetName();
                boolean female;
                if (name.contains("女")) { female = true; }
                else if (name.contains("男")) { female = false; }
                else { continue; }
                Row hr = sh.getRow(0);
                if (hr == null) { continue; }
                List<String> ages = new ArrayList<String>();
                for (int c = 1; c < hr.getLastCellNum(); c++) {
                    String h = fmt.formatCellValue(hr.getCell(c)).trim();
                    if (!h.isEmpty()) { ages.add(h); }
                }
                if (ages.isEmpty()) { continue; }
                List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
                for (int r = 1; r <= sh.getLastRowNum(); r++) {
                    Row row = sh.getRow(r);
                    if (row == null) { continue; }
                    String hStr = fmt.formatCellValue(row.getCell(0)).trim();
                    if (hStr.isEmpty()) { continue; }
                    double h;
                    try {
                        h = Double.parseDouble(hStr);
                    } catch (Exception e) {
                        continue;
                    }
                    if (h < 1.0 || h > 2.5) { continue; }
                    List<Object> w = new ArrayList<Object>();
                    for (int c = 1; c <= ages.size(); c++) {
                        String cell = fmt.formatCellValue(row.getCell(c)).trim();
                        java.util.regex.Matcher m = java.util.regex.Pattern
                                .compile("([0-9]+(?:[.][0-9]+)?)\\s*[~～-]\\s*([0-9]+(?:[.][0-9]+)?)").matcher(cell);
                        if (m.find()) {
                            w.add(Arrays.asList(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))));
                        } else {
                            w.add(null);
                        }
                    }
                    Map<String, Object> rr = new LinkedHashMap<String, Object>();
                    rr.put("h", h);
                    rr.put("w", w);
                    rows.add(rr);
                }
                if (rows.isEmpty()) { continue; }
                Map<String, Object> side = new LinkedHashMap<String, Object>();
                side.put("rows", rows);
                out.put(female ? "female" : "male", side);
                totalRows += rows.size();
            }
            if (!out.containsKey("male") && !out.containsKey("female")) {
                return ApiResponse.error("未识别到「男标准」或「女标准」工作表");
            }
            // 读出现有 ages/另一侧并合并
            Map<String, Object> cur;
            try {
                String v = jdbc.queryForObject("SELECT svalue FROM sys_setting WHERE skey='bmi_standard'", String.class);
                cur = mapper.readValue(v == null ? "{}" : v, Map.class);
            } catch (Exception e) {
                cur = new HashMap<String, Object>();
            }
            List<String> ages = (List<String>) out.get("__skip__");
            out.remove("__skip__");
            Map<String, Object> maleSide = (Map<String, Object>) out.get("male");
            List<String> agesOut = agesFromSheet(wb, "男标准", fmt, cur);
            Map<String, Object> merged = new LinkedHashMap<String, Object>();
            merged.put("ages", agesOut);
            merged.put("male", out.containsKey("male") ? out.get("male") : cur.get("male"));
            merged.put("female", out.containsKey("female") ? out.get("female") : cur.get("female"));
            String json = mapper.writeValueAsString(merged);
            jdbc.update("INSERT INTO sys_setting(skey, svalue) VALUES('bmi_standard',?) " +
                    "ON CONFLICT(skey) DO UPDATE SET svalue=excluded.svalue", json);
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("rows", totalRows);
            result.put("ageGroups", agesOut);
            return ApiResponse.ok(result);
        } finally {
            wb.close();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> agesFromSheet(Workbook wb, String sheetName, DataFormatter fmt, Map<String, Object> cur) {
        Sheet sh = wb.getSheet(sheetName);
        if (sh != null && sh.getRow(0) != null) {
            List<String> ages = new ArrayList<String>();
            for (int c = 1; c < sh.getRow(0).getLastCellNum(); c++) {
                String h = fmt.formatCellValue(sh.getRow(0).getCell(c)).trim();
                if (!h.isEmpty()) { ages.add(h); }
            }
            if (!ages.isEmpty()) { return ages; }
        }
        List<String> old = (List<String>) cur.get("ages");
        return old != null ? old : Arrays.asList("24岁以下", "25-29岁", "30-39岁", "40-49岁", "50-59岁", "60岁以上");
    }

    /* ================= 公共 ================= */

    private static String sheetName(String item) {
        // Excel sheet 名最长 31 字符,且不能含 : \ / ? * [ ]
        String s = item.replaceAll("[:\\\\/?*\\[\\]]", "_");
        return s.length() > 28 ? s.substring(0, 28) : s;
    }

    private static CellStyle headStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        st.setFont(f);
        st.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        st.setAlignment(HorizontalAlignment.CENTER);
        st.setBorderTop(BorderStyle.THIN);
        st.setBorderBottom(BorderStyle.THIN);
        return st;
    }

    private static byte[] bytes(Workbook wb) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    private static ResponseEntity<byte[]> download(byte[] body, String fileName) {
        String encoded;
        try {
            encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            encoded = "template.xlsx";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
