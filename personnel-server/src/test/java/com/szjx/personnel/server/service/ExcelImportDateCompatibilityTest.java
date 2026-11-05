package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.util.Dates;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Real import entry points and temporary SQLite only; no application, timers or real data. */
class ExcelImportDateCompatibilityTest {
    private static final String ISO = "2016-09-01";
    private static final String CARD = "110105201609010019";
    private static final String NUMBER = "20160901";

    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private ModuleExcelService service;
    private PersonnelFullExcel personnel;

    private enum Encoding {
        COMPACT_TEXT, ISO_TEXT, DOT_TEXT, SLASH_TEXT, CHINESE_TEXT, COMPACT_NUMBER, STYLED_COMPACT_NUMBER,
        NATIVE_DATE, DATE_FORMULA, TEXT_FORMULA, NUMBER_FORMULA;

        void write(Cell cell) {
            switch (this) {
                case COMPACT_TEXT: cell.setCellValue("20160901"); break;
                case ISO_TEXT: cell.setCellValue(ISO); break;
                case DOT_TEXT: cell.setCellValue("2016.09.01"); break;
                case SLASH_TEXT: cell.setCellValue("2016/9/1"); break;
                case CHINESE_TEXT: cell.setCellValue("2016年9月1日"); break;
                case COMPACT_NUMBER: cell.setCellValue(20160901d); break;
                case STYLED_COMPACT_NUMBER:
                    cell.setCellValue(20160901d);
                    dateStyle(cell);
                    break;
                case NATIVE_DATE:
                    cell.setCellValue(LocalDateTime.of(2016, 9, 1, 0, 0));
                    dateStyle(cell);
                    break;
                case DATE_FORMULA:
                    cell.setCellFormula("DATE(2016,9,1)");
                    dateStyle(cell);
                    break;
                case TEXT_FORMULA: cell.setCellFormula("\"2016年9月1日\""); break;
                case NUMBER_FORMULA: cell.setCellFormula("20160000+901"); break;
                default: throw new AssertionError(this);
            }
        }

        private static void dateStyle(Cell cell) {
            Workbook wb = cell.getSheet().getWorkbook();
            CellStyle style = wb.createCellStyle();
            // Deliberately not ISO: the import must read the date, not its displayed string.
            style.setDataFormat(wb.createDataFormat().getFormat("m/d/yy"));
            cell.setCellStyle(style);
        }
    }

    private static final class DateField {
        final String module;
        final String table;
        final String header;
        final String column;
        DateField(String module, String table, String header, String column) {
            this.module = module; this.table = table; this.header = header; this.column = column;
        }
        @Override public String toString() { return module + "." + column; }
    }

    private static final DateField[] MODULE_DATES = {
            new DateField("equipment", "equipment", "装备时间", "equip_date"),
            new DateField("equipment-gun", "equipment", "装备时间", "equip_date"),
            new DateField("vehicle", "vehicle", "装备时间", "equip_date"),
            new DateField("vehicle", "vehicle", "年检到期日", "inspection_expiry"),
            new DateField("leave", "leave_record", "开始日期", "start_date"),
            new DateField("leave", "leave_record", "结束日期", "end_date"),
            new DateField("leave-plan", "leave_plan", "计划开始日期", "start_date"),
            new DateField("leave-plan", "leave_plan", "计划结束日期", "end_date"),
            new DateField("phone", "phone_device", "购置日期", "purchase_date"),
            new DateField("fitness", "fitness_record", "考核日期", "test_date"),
            new DateField("military", "military_assessment", "考核日期", "test_date")
    };

    private static final Map<String, String> MAIN_DATES = values(
            "出生日期", "birthday", "任岗位时间", "post_date", "入伍/入职日期", "entry_date",
            "相当入伍时间", "equiv_entry_date", "入党/入团日期", "party_join_date",
            "提交申请书时间", "party_apply_date", "积极分子时间", "party_activist_date",
            "预备党员时间", "party_candidate_date", "转正时间", "party_full_date",
            "结婚日期", "marriage_date", "离婚日期", "divorce_date", "随军时间", "follow_army_date",
            "入学时间", "edu_enroll_date", "毕业时间", "edu_graduate_date");

    private static final String[][] CHILDREN = {
            {"任职经历", "position_history", "开始日期", "start_date", "结束日期", "end_date"},
            {"奖惩情况", "reward_punish", "日期", "occur_date"},
            {"家庭成员", "family_member", "出生日期", "birth_date"},
            {"驾驶证", "driver_license", "初领日期", "issue_date", "有效期至", "expiry_date", "年审日期", "review_date"},
            {"个人卡号", "personal_card", "开户日期", "open_date"},
            {"私家车", "private_vehicle", "购置日期", "purchase_date"},
            {"住房情况", "housing_info", "购置时间", "purchase_date"}
    };

    @BeforeEach void setUp() {
        DataScope.clear();
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        PersonnelIntegrityTest.personnelSchema(jdbc);
        jdbc.execute("DROP TABLE phone_device");
        jdbc.execute("CREATE TABLE sys_dict(id INTEGER PRIMARY KEY AUTOINCREMENT, dict_type TEXT, dict_label TEXT, enabled INTEGER DEFAULT 1)");
        for (String[] dict : new String[][]{
                {"personnel_category", "军官"}, {"rank", "上尉"}, {"personnel_status", "在职"},
                {"education", "本科"}, {"marital_status", "未婚"}, {"nation", "汉族"},
                {"blood_type", "A"}, {"political_status", "党员"}, {"equipment_category", "枪支"},
                {"leave_type", "年假"}, {"device_type", "地方手机"}, {"phone_brand", "华为"},
                {"military_type", "训练"}}) {
            jdbc.update("INSERT INTO sys_dict(dict_type,dict_label) VALUES(?,?)", dict[0], dict[1]);
        }
        table("equipment", "keeper_id", "name,code,ident_code,category,brand,model,status,location,buy_date,equip_date,remark,updated_at");
        table("vehicle", "driver_id", "plate_no,vin,engine_no,brand,model,type,color,fuel_type,status,seats,mileage,purchase_date,equip_date,insurance_expiry,inspection_expiry,remark,updated_at");
        table("leave_record", "personnel_id", "year,leave_type,start_date,end_date,days,travel_days,holiday_days,holiday_name,leave_location,reason,remark,updated_at");
        table("leave_plan", "personnel_id", "year,leave_type,start_date,end_date,days,remark,updated_at");
        table("phone_device", "personnel_id", "device_type,brand,model,serial_no,imei1,imei2,phone_number,purchase_date,status,remark,updated_at");
        table("fitness_record", "personnel_id", "test_date,item,score,points,grade,tester,remark,created_at,updated_at");
        table("military_assessment", "personnel_id", "category,item,score,points,grade,test_date,remark,updated_at");
        table("position_history", "personnel_id", "start_date,end_date,place,post,remark");
        table("reward_punish", "personnel_id", "kind,occur_date,reason,content,remark");
        table("family_member", "personnel_id", "relationship,name,id_card,gender,birth_date,political_status,work_info,phone,remark");
        table("driver_license", "personnel_id", "license_type,license_no,vehicle_class,issue_date,expiry_date,issuing_auth,license_status,review_date,remark");
        table("personal_card", "personnel_id", "card_type,card_no,bank_name,branch_name,open_date,card_status,remark");
        table("private_vehicle", "personnel_id", "plate_no,brand,color,purchase_date,price,insurance_date,vehicle_status,owner_name,remark");
        table("housing_info", "personnel_id", "housing_type,address,area,house_nature,has_cert,loan_status,purchase_date,owner_name,remark");
        jdbc.execute("CREATE TABLE fitness_matrix(age_group TEXT,score REAL,value REAL,unit TEXT,item TEXT)");
        jdbc.update("INSERT INTO fitness_matrix VALUES('18-60',100,60,'count','仰卧起坐')");
        jdbc.execute("CREATE TABLE sys_setting(skey TEXT PRIMARY KEY,svalue TEXT)");
        jdbc.update("INSERT INTO sys_setting VALUES('fitness_grade_rule',?)", "[{\"min\":90,\"label\":\"优秀\",\"op\":\">=\"},{\"min\":0,\"label\":\"不及格\",\"op\":\">=\"}]");
        // Adult fitness fixture; personnel imports use their own birth-date-matching identity.
        jdbc.update("INSERT INTO personnel(id,name,id_card,birthday,gender,height,status,department_id) VALUES(1,'张三','11010519491231002X','1990-01-01','男',175,'在职',1)");
        service = new ModuleExcelService(jdbc, null, null);
        service.setLeaveStatusSyncService(new LeaveStatusSyncService(jdbc));
        personnel = new PersonnelFullExcel(jdbc);
    }

    @AfterEach void clearScope() { DataScope.clear(); }

    private void table(String name, String owner, String columns) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(name)
                .append("(id INTEGER PRIMARY KEY AUTOINCREMENT,").append(owner).append(" INTEGER DEFAULT 0");
        for (String column : columns.split(",")) {
            boolean numeric = Arrays.asList("seats", "mileage", "days", "travel_days", "holiday_days", "points").contains(column);
            ddl.append(',').append(column).append(numeric ? " REAL DEFAULT 0" : " TEXT DEFAULT ''");
        }
        jdbc.execute(ddl.append(')').toString());
    }

    static Stream<Arguments> moduleFormats() {
        return Arrays.stream(MODULE_DATES).flatMap(field -> Arrays.stream(Encoding.values())
                .filter(encoding -> encoding.ordinal() <= Encoding.STYLED_COMPACT_NUMBER.ordinal())
                .map(encoding -> Arguments.of(field, encoding)));
    }

    static Stream<DateField> moduleDateFields() { return Arrays.stream(MODULE_DATES); }

    @ParameterizedTest(name = "{0} accepts {1}")
    @MethodSource("moduleFormats")
    void moduleDateColumnsPersistIso(DateField field, Encoding encoding) throws Exception {
        Map<String, String> row = moduleRow(field.module, "only");
        Map<String, Object> result = importModule(field.module, Arrays.asList(row), field.header, encoding);
        counts(result, 1, 0, 0);
        assertEquals(ISO, jdbc.queryForObject("SELECT " + field.column + " FROM " + field.table, String.class));
    }

    @ParameterizedTest(name = "{0} rejects impossible dates one row at a time")
    @MethodSource("moduleDateFields")
    void invalidModuleDatesDoNotAbortOtherRowsOrOverwriteGoodRows(DateField field) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(moduleRow(field.module, "before"));
        for (String invalid : Arrays.asList("20160230", "2015年2月29日", "2016/13/1", "2016.09.31")) {
            Map<String, String> row = moduleRow(field.module, "bad" + rows.size());
            row.put(field.header, invalid);
            rows.add(row);
        }
        rows.add(moduleRow(field.module, "after"));
        Map<String, Object> result = importModule(field.module, rows, null, null);
        boolean phone = "phone".equals(field.module); // phone remains one device row per identity.
        boolean fitness = "fitness".equals(field.module); // 体能同人+同项目+同日期重复行按覆盖更新。
        counts(result, fitness ? 1 : phone ? 1 : 2, fitness ? 1 : phone ? 1 : 0, 4);
        List<?> errors = (List<?>) result.get("errors");
        assertEquals(4, errors.size());
        for (int i = 0; i < errors.size(); i++) assertTrue(errors.get(i).toString().contains("第" + (i + 3) + "行"), result.toString());
        assertEquals(fitness ? 1 : phone ? 1 : 2, jdbc.queryForObject("SELECT COUNT(*) FROM " + field.table, Integer.class));
        for (String date : jdbc.queryForList("SELECT " + field.column + " FROM " + field.table, String.class)) assertEquals(ISO, date);
    }

    @ParameterizedTest
    @MethodSource("rangeModules")
    void leaveRangesAreComparedAfterNormalization(String module, String table, String start, String end) throws Exception {
        Map<String, String> accepted = moduleRow(module, "accepted");
        accepted.put(start, "2016/9/2"); accepted.put(end, "2016年9月10日");
        Map<String, String> reversed = moduleRow(module, "reversed");
        reversed.put(start, "2016.09.10"); reversed.put(end, "20160902");
        counts(importModule(module, Arrays.asList(accepted, reversed), null, null), 1, 0, 1);
        assertEquals("2016-09-02", jdbc.queryForObject("SELECT start_date FROM " + table, String.class));
        assertEquals("2016-09-10", jdbc.queryForObject("SELECT end_date FROM " + table, String.class));
    }

    static Stream<Arguments> rangeModules() {
        return Stream.of(Arguments.of("leave", "leave_record", "开始日期", "结束日期"),
                Arguments.of("leave-plan", "leave_plan", "计划开始日期", "计划结束日期"));
    }

    @ParameterizedTest
    @EnumSource(value = Encoding.class, names = {"NATIVE_DATE", "DATE_FORMULA", "TEXT_FORMULA", "NUMBER_FORMULA"})
    void nativeAndUncachedFormulaCellsUseTheRealImportPipeline(Encoding encoding) throws Exception {
        counts(importModule("vehicle", Arrays.asList(moduleRow("vehicle", "native")), "装备时间", encoding), 1, 0, 0);
        assertEquals(ISO, jdbc.queryForObject("SELECT equip_date FROM vehicle", String.class));
        try (Workbook wb = personnelWorkbook(encoding)) {
            // No evaluateAll: the production reader must evaluate formulas rather than trust stale caches.
            Map<String, Object> result = personnel.importAll(input(wb));
            counts(result, 1, 0, 0);
            assertEquals(7, result.get("subTotal"), result.toString());
            assertEquals(0, result.get("subFailed"), result.toString());
            assertPersonnelDates();
        }
    }

    @ParameterizedTest
    @EnumSource(value = Encoding.class, names = {"COMPACT_TEXT", "ISO_TEXT", "DOT_TEXT", "SLASH_TEXT", "CHINESE_TEXT", "COMPACT_NUMBER", "STYLED_COMPACT_NUMBER"})
    void personnelBirthdayAllMainDatesAndSevenChildSheetsNormalize(Encoding encoding) throws Exception {
        try (Workbook wb = personnelWorkbook(encoding)) {
            Map<String, Object> result = personnel.importAll(input(wb));
            counts(result, 1, 0, 0);
            assertEquals(7, result.get("subTotal"), result.toString());
            assertEquals(0, result.get("subFailed"), result.toString());
            assertPersonnelDates();
            assertEquals("军证测试-20160901", jdbc.queryForObject("SELECT military_id_no FROM personnel WHERE id_card=?", String.class, CARD));
            assertEquals(NUMBER, jdbc.queryForObject("SELECT edu_cert_no FROM personnel WHERE id_card=?", String.class, CARD));
            assertEquals(NUMBER, jdbc.queryForObject("SELECT license_no FROM driver_license", String.class));
            assertEquals(NUMBER, jdbc.queryForObject("SELECT card_no FROM personal_card", String.class));
            // Reimport matches personnel and normalized child values instead of duplicating children.
            Map<String, Object> again = personnel.importAll(input(wb));
            counts(again, 0, 1, 0);
            assertEquals(0, again.get("subTotal"), again.toString());
            assertEquals(0, again.get("subFailed"), again.toString());
        }
    }

    static Stream<Arguments> mainDateFields() {
        return MAIN_DATES.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    @ParameterizedTest(name = "personnel rejects invalid {0} without overwriting existing ISO")
    @MethodSource("mainDateFields")
    void invalidPersonnelDatesRejectOnlyTheirRows(String header, String column) throws Exception {
        try (Workbook good = personnelWorkbook(Encoding.COMPACT_TEXT)) {
            counts(personnel.importAll(input(good)), 1, 0, 0);
        }
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(personnel.template()))) {
            Sheet sheet = wb.getSheetAt(0);
            Row invalid = writeRow(sheet, 1, mainRow());
            set(invalid, header, "2015年2月29日");
            Row valid = writeRow(sheet, 2, mainRow());
            set(valid, "身份证号", "11010519491231002X");
            set(valid, "出生日期", "1949.12.31");
            set(valid, "姓名", "张三更新");
            counts(personnel.importAll(input(wb)), 0, 1, 1);
        }
        assertEquals(ISO, jdbc.queryForObject("SELECT " + column + " FROM personnel WHERE id_card=?", String.class, CARD));
        assertEquals("张三更新", jdbc.queryForObject("SELECT name FROM personnel WHERE id=1", String.class));
    }

    static Stream<Arguments> childDateFields() {
        List<Arguments> result = new ArrayList<>();
        for (String[] child : CHILDREN) for (int i = 2; i < child.length; i += 2)
            result.add(Arguments.of(child[0], child[1], child[i], child[i + 1]));
        return result.stream();
    }

    @ParameterizedTest(name = "{0}.{3} rejects invalid dates per row")
    @MethodSource("childDateFields")
    void invalidChildDatesDoNotAbortValidSiblingRows(String sheetName, String table, String header, String column) throws Exception {
        try (Workbook wb = personnelWorkbook(Encoding.COMPACT_TEXT)) {
            Sheet sheet = wb.getSheet(sheetName);
            Row bad = writeRow(sheet, 2, childRow(sheetName));
            set(bad, header, "2016/2/30");
            Map<String, Object> result = personnel.importAll(input(wb));
            counts(result, 1, 0, 0);
            assertEquals(7, result.get("subTotal"), result.toString());
            assertEquals(1, result.get("subFailed"), result.toString());
            assertTrue(result.get("errors").toString().contains(sheetName), result.toString());
            assertTrue(result.get("errors").toString().contains("第3行"), result.toString());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
            assertEquals(ISO, jdbc.queryForObject("SELECT " + column + " FROM " + table, String.class));
        }
    }

    @Test void eightDigitIdentifiersAndIgnoredLegacyColumnsKeepTheirExistingContract() throws Exception {
        jdbc.update("INSERT INTO equipment(name,ident_code,buy_date) VALUES('原装备',?,'2001-02-03')", NUMBER);
        Map<String, String> equipment = moduleRow("equipment", "unused");
        equipment.put("识别码", NUMBER); equipment.put("装备编号", NUMBER);
        equipment.put("购置日期", "2015年2月29日"); // No longer an import field: even invalid legacy input is ignored.
        counts(importModule("equipment", Arrays.asList(equipment), "装备时间", Encoding.COMPACT_NUMBER), 0, 1, 0);
        assertEquals(NUMBER, jdbc.queryForObject("SELECT ident_code FROM equipment", String.class));
        assertEquals(NUMBER, jdbc.queryForObject("SELECT code FROM equipment", String.class));
        assertEquals("2001-02-03", jdbc.queryForObject("SELECT buy_date FROM equipment", String.class));
        jdbc.update("INSERT INTO vehicle(plate_no,vin,purchase_date,insurance_expiry) VALUES('旧车',?,'2002-03-04','2003-04-05')", NUMBER);
        Map<String, String> vehicle = moduleRow("vehicle", "unused");
        vehicle.put("车架号", NUMBER); vehicle.put("发动机号", NUMBER);
        vehicle.put("购置日期", "20160230"); vehicle.put("保险到期日", "2015年2月29日");
        counts(importModule("vehicle", Arrays.asList(vehicle), "装备时间", Encoding.COMPACT_NUMBER), 0, 1, 0);
        Map<String, Object> stored = jdbc.queryForMap("SELECT * FROM vehicle");
        assertEquals(NUMBER, stored.get("vin")); assertEquals(NUMBER, stored.get("engine_no"));
        assertEquals("2002-03-04", stored.get("purchase_date")); assertEquals("2003-04-05", stored.get("insurance_expiry"));
        Map<String, String> phone = moduleRow("phone", "number");
        phone.put("序列号", NUMBER); phone.put("IMEI1", NUMBER);
        counts(importModule("phone", Arrays.asList(phone), "购置日期", Encoding.COMPACT_NUMBER), 1, 0, 0);
        assertEquals(NUMBER, jdbc.queryForObject("SELECT serial_no FROM phone_device", String.class));
        assertEquals(NUMBER, jdbc.queryForObject("SELECT imei1 FROM phone_device", String.class));
    }

    static Stream<DateField> identifierFields() {
        return Stream.of(
                new DateField("equipment", "equipment", "识别码", "ident_code"),
                new DateField("equipment", "equipment", "装备编号", "code"),
                new DateField("equipment-gun", "equipment", "装备编号", "code"),
                new DateField("vehicle", "vehicle", "车架号", "vin"),
                new DateField("vehicle", "vehicle", "发动机号", "engine_no"),
                new DateField("phone", "phone_device", "序列号", "serial_no"),
                new DateField("phone", "phone_device", "IMEI1", "imei1"));
    }

    @ParameterizedTest(name = "numeric {0} is an identifier, not a date")
    @MethodSource("identifierFields")
    void numericEightDigitIdentifiersAreNotInterpretedAsDates(DateField field) throws Exception {
        Map<String, String> row = moduleRow(field.module, "identifier");
        row.put(field.header, NUMBER);
        counts(importModule(field.module, Arrays.asList(row), field.header, Encoding.COMPACT_NUMBER), 1, 0, 0);
        assertEquals(NUMBER, jdbc.queryForObject("SELECT " + field.column + " FROM " + field.table, String.class));
    }

    @Test void importCompatibilityDoesNotRelaxOrdinaryApiDateValidation() {
        for (String value : Arrays.asList("20160901", "2016.09.01", "2016/9/1", "2016年9月1日")) {
            assertFalse(Dates.validOrEmpty(value), value);
            assertThrows(IllegalArgumentException.class, () -> FitnessScoringService.date(value, "普通API日期"), value);
        }
        assertTrue(Dates.validOrEmpty(ISO));
    }

    private Map<String, Object> importModule(String module, List<Map<String, String>> data, String dateHeader, Encoding encoding) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("导入");
            Row header = sheet.createRow(0);
            int c = 0;
            for (String label : data.get(0).keySet()) header.createCell(c++).setCellValue(label);
            for (int i = 0; i < data.size(); i++) {
                Row row = writeRow(sheet, i + 1, data.get(i));
                if (dateHeader != null) encoding.write(cell(row, dateHeader));
            }
            return service.importExcel(module, input(wb));
        }
    }

    private static Map<String, String> moduleRow(String module, String key) {
        switch (module) {
            case "equipment": return values("装备名称", "装备" + key, "识别码", key, "装备时间", ISO);
            case "equipment-gun": return values("装备名称", "枪" + key, "装备编号", key, "装备时间", ISO);
            case "vehicle": return values("车牌号", "浙A" + key, "车架号", key, "装备时间", ISO, "年检到期日", ISO);
            case "leave": return values("人员姓名", "张三", "休假类型", "年假", "开始日期", ISO, "结束日期", ISO, "天数", "1", "备注", key);
            case "leave-plan": return values("人员姓名", "张三", "年度", "2016", "休假类型", "年假", "计划开始日期", ISO, "计划结束日期", ISO, "天数", "1", "备注", key);
            case "phone": return values("姓名", "张三", "身份证号", "11010519491231002X", "设备类型", "地方手机", "品牌", "华为", "购置日期", ISO);
            case "fitness": return values("姓名", "张三", "考核日期", ISO, "考核项目", "仰卧起坐", "成绩", "60");
            case "military": return values("姓名", "张三", "考核类别", "训练", "考核项目", "射击", "成绩", "合格", "考核日期", ISO);
            default: throw new AssertionError(module);
        }
    }

    private Workbook personnelWorkbook(Encoding encoding) throws Exception {
        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(personnel.template()));
        Row main = writeRow(wb.getSheetAt(0), 1, mainRow());
        for (String label : MAIN_DATES.keySet()) encoding.write(cell(main, label));
        for (String[] child : CHILDREN) {
            Row row = writeRow(wb.getSheet(child[0]), 1, childRow(child[0]));
            for (int i = 2; i < child.length; i += 2) encoding.write(cell(row, child[i]));
        }
        return wb;
    }

    private static Map<String, String> mainRow() {
        Map<String, String> row = values("姓名", "日期兼容人员", "性别", "男", "身份证号", CARD, "军人证件号", "军证测试-20160901",
                "编制单位", "机关", "所在点位", "机关", "人员类别", "军官", "军衔/职级", "上尉", "职务/岗位", "参谋",
                "状态", "在职", "民族", "汉族", "籍贯所在地", "浙江省/杭州市", "入伍所在地", "浙江省/杭州市",
                "血型", "A", "政治面貌", "党员", "婚姻状况", "未婚", "学历", "本科", "是否独生子女", "否",
                "详细地址", "杭州", "学历证书编号", NUMBER);
        for (String label : MAIN_DATES.keySet()) row.put(label, ISO);
        return row;
    }

    private static Map<String, String> childRow(String sheet) {
        Map<String, String> row = values("身份证号", CARD);
        switch (sheet) {
            case "任职经历": row.putAll(values("任职地点", "机关", "担任职务", "参谋")); break;
            case "奖惩情况": row.putAll(values("类型", "奖励", "事由", "训练", "内容", "嘉奖")); break;
            case "家庭成员": row.putAll(values("关系", "父亲", "成员姓名", "家属")); break;
            case "驾驶证": row.putAll(values("类型", "地方驾驶证", "证号", NUMBER)); break;
            case "个人卡号": row.putAll(values("卡类型", "银行卡", "卡号", NUMBER)); break;
            case "私家车": row.put("车牌号", "浙A12345"); break;
            case "住房情况": row.put("地址", "杭州测试住房"); break;
            default: throw new AssertionError(sheet);
        }
        for (String[] child : CHILDREN) if (child[0].equals(sheet))
            for (int i = 2; i < child.length; i += 2) row.put(child[i], ISO);
        return row;
    }

    private void assertPersonnelDates() {
        for (String column : MAIN_DATES.values())
            assertEquals(ISO, jdbc.queryForObject("SELECT " + column + " FROM personnel WHERE id_card=?", String.class, CARD), column);
        Long id = jdbc.queryForObject("SELECT id FROM personnel WHERE id_card=?", Long.class, CARD);
        for (String[] child : CHILDREN) {
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + child[1] + " WHERE personnel_id=?", Integer.class, id), child[0]);
            for (int i = 2; i < child.length; i += 2)
                assertEquals(ISO, jdbc.queryForObject("SELECT " + child[i + 1] + " FROM " + child[1] + " WHERE personnel_id=?", String.class, id), child[0] + "." + child[i + 1]);
        }
    }

    private static Map<String, String> values(String... entries) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put(entries[i], entries[i + 1]);
        return result;
    }

    private static Row writeRow(Sheet sheet, int at, Map<String, String> data) {
        Row row = sheet.createRow(at);
        for (Map.Entry<String, String> entry : data.entrySet()) set(row, entry.getKey(), entry.getValue());
        return row;
    }

    private static Cell cell(Row row, String header) {
        for (Cell candidate : row.getSheet().getRow(0)) if (header.equals(candidate.getStringCellValue())) {
            Cell result = row.getCell(candidate.getColumnIndex());
            return result == null ? row.createCell(candidate.getColumnIndex()) : result;
        }
        throw new AssertionError("Missing header: " + header);
    }

    private static void set(Row row, String header, String value) { cell(row, header).setCellValue(value); }

    private static ByteArrayInputStream input(Workbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static void counts(Map<String, Object> result, int added, int updated, int failed) {
        assertFalse(result.containsKey("error"), result.toString());
        assertEquals(added, result.get("added"), result.toString());
        assertEquals(updated, result.get("updated"), result.toString());
        assertEquals(failed, result.get("failed"), result.toString());
    }
}
