package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModuleExcelServiceRegressionTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private ModuleExcelService service;

    @BeforeEach void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + temp.resolve("module-excel.sqlite").toAbsolutePath());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, id_card TEXT, birthday TEXT DEFAULT '1990-01-01', gender TEXT DEFAULT '男', height REAL DEFAULT 175, phone TEXT DEFAULT '', status TEXT DEFAULT '在职', auto_leave INTEGER DEFAULT 0, auto_leave_previous_status TEXT DEFAULT '', updated_at TEXT DEFAULT '')");
        jdbc.update("INSERT INTO personnel(id,name,id_card) VALUES(1,'张三','110105199001011234')");
        jdbc.execute("CREATE TABLE sys_dict(id INTEGER PRIMARY KEY,dict_type TEXT,dict_label TEXT,enabled INTEGER DEFAULT 1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('equipment_category','枪支',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('equipment_category','大型装备',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('equipment_category','小型装备',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('leave_type','年假',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('material_category','办公用品',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('material_location','库房',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('device_type','地方手机',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('phone_brand','华为',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('device_status','在用',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('equipment_brand','品牌甲',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('equipment_brand','品牌乙',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('equipment_brand','停用品牌',0)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('equipment_status','正常',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('equipment_location','库房',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('military_type','训练',1)");
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES('vehicle_fuel','汽油',1)");
        jdbc.execute("CREATE TABLE equipment(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT DEFAULT '',code TEXT DEFAULT '',ident_code TEXT DEFAULT '',category TEXT DEFAULT '',brand TEXT DEFAULT '',model TEXT DEFAULT '',status TEXT DEFAULT '',keeper_id INTEGER DEFAULT 0,location TEXT DEFAULT '',buy_date TEXT DEFAULT '',equip_date TEXT DEFAULT '',remark TEXT DEFAULT '',updated_at TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE vehicle(id INTEGER PRIMARY KEY AUTOINCREMENT,plate_no TEXT DEFAULT '',vin TEXT DEFAULT '',engine_no TEXT DEFAULT '',brand TEXT DEFAULT '',model TEXT DEFAULT '',type TEXT DEFAULT '',color TEXT DEFAULT '',fuel_type TEXT DEFAULT '',status TEXT DEFAULT '',driver_id INTEGER DEFAULT 0,seats INTEGER,mileage REAL,purchase_date TEXT DEFAULT '',equip_date TEXT DEFAULT '',insurance_expiry TEXT DEFAULT '',inspection_expiry TEXT DEFAULT '',remark TEXT DEFAULT '',updated_at TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE fitness_record(id INTEGER PRIMARY KEY AUTOINCREMENT,personnel_id INTEGER,test_date TEXT,item TEXT,score TEXT,points REAL DEFAULT 0,grade TEXT DEFAULT '',tester TEXT DEFAULT '',remark TEXT DEFAULT '',created_at TEXT DEFAULT '',updated_at TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE military_assessment(id INTEGER PRIMARY KEY AUTOINCREMENT,personnel_id INTEGER,category TEXT DEFAULT '',item TEXT DEFAULT '',score TEXT DEFAULT '',points REAL DEFAULT 0,grade TEXT DEFAULT '',test_date TEXT DEFAULT '',remark TEXT DEFAULT '',updated_at TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE material(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT DEFAULT '',category TEXT DEFAULT '',spec TEXT DEFAULT '',unit TEXT DEFAULT '',quantity REAL DEFAULT 0,min_quantity REAL DEFAULT 0,keeper_id INTEGER DEFAULT 0,location TEXT DEFAULT '',detail_location TEXT DEFAULT '',remark TEXT DEFAULT '',updated_at TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE leave_record(id INTEGER PRIMARY KEY AUTOINCREMENT,personnel_id INTEGER,year TEXT DEFAULT '',leave_type TEXT DEFAULT '',start_date TEXT DEFAULT '',end_date TEXT DEFAULT '',days REAL DEFAULT 0,travel_days REAL DEFAULT 0,holiday_days REAL DEFAULT 0,holiday_name TEXT DEFAULT '',leave_location TEXT DEFAULT '',reason TEXT DEFAULT '',remark TEXT DEFAULT '',updated_at TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE leave_plan(id INTEGER PRIMARY KEY AUTOINCREMENT,personnel_id INTEGER,year TEXT DEFAULT '',leave_type TEXT DEFAULT '',start_date TEXT DEFAULT '',end_date TEXT DEFAULT '',days REAL DEFAULT 0,remark TEXT DEFAULT '',updated_at TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE phone_device(id INTEGER PRIMARY KEY AUTOINCREMENT,personnel_id INTEGER,device_type TEXT DEFAULT '',brand TEXT DEFAULT '',model TEXT DEFAULT '',serial_no TEXT DEFAULT '',imei1 TEXT DEFAULT '',imei2 TEXT DEFAULT '',phone_number TEXT DEFAULT '',purchase_date TEXT DEFAULT '',status TEXT DEFAULT '',remark TEXT DEFAULT '',updated_at TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE fitness_matrix(age_group TEXT,score REAL,value REAL,unit TEXT,item TEXT)");
        jdbc.update("INSERT INTO fitness_matrix VALUES('18-60',100,60,'count','仰卧起坐')");
        jdbc.execute("CREATE TABLE sys_setting(skey TEXT PRIMARY KEY,svalue TEXT)");
        jdbc.update("INSERT INTO sys_setting VALUES('fitness_grade_rule',?)", "[{\"min\":90,\"label\":\"优秀\",\"op\":\">=\"},{\"min\":0,\"label\":\"不及格\",\"op\":\">=\"}]");
        service = new ModuleExcelService(jdbc, null, null);
        service.setLeaveStatusSyncService(new LeaveStatusSyncService(jdbc));
    }

    @Test void leaveLocationRoundTripsThroughTemplateImportAndExport() throws Exception {
        try (Workbook template = read(service.template("leave"))) {
            assertTrue(headers(template.getSheetAt(0).getRow(0)).contains("休假地点"));
        }
        Map<String,Object> result = service.importExcel("leave", input(new String[][]{
                {"人员姓名","休假类型","休假地点","开始日期","结束日期","天数"},
                {"张三","年假","北京市","2026-06-10","2026-06-15","5"}
        }));
        assertCounts(result, 1, 0, 0);
        assertEquals("北京市", jdbc.queryForObject("SELECT leave_location FROM leave_record WHERE id=1", String.class));
        try (Workbook exported = read(service.export("leave", Arrays.asList(1L)))) {
            Row header = exported.getSheetAt(0).getRow(0);
            int locationColumn = headers(header).indexOf("休假地点");
            assertTrue(locationColumn >= 0);
            assertEquals("北京市", exported.getSheetAt(0).getRow(1).getCell(locationColumn).getStringCellValue());
        }
    }

    @Test void gunUsesDedicatedColumnsAndOnlyExportsSelectedGunRows() throws Exception {
        assertTrue(service.supports("equipment-gun"));
        try (Workbook template = read(service.template("equipment-gun"))) {
            Row head = template.getSheetAt(0).getRow(0);
            assertFalse(headers(head).contains("识别码"));
            assertFalse(headers(head).contains("购置日期"));
            assertFalse(headers(head).contains("品牌"));
        }
        jdbc.update("INSERT INTO equipment(name,code,ident_code,category,brand,buy_date) VALUES('枪','G-1','KEEP-ID','枪支','历史枪支品牌','2020-01-01')");
        jdbc.update("INSERT INTO equipment(name,code,category) VALUES('非枪','G-2','大型装备')");
        Map<String,Object> result = service.importExcel("equipment-gun", input(new String[][]{
                {"装备名称","装备编号","类别","识别码","购置日期","品牌","状态","存放位置"},
                {"更新枪","G-1","大型装备","LEGACY","2024-01-01","不存在的品牌","正常","库房"},
                {"新枪","G-3","枪支","","","停用品牌","正常","库房"}
        }));
        assertEquals(1, result.get("added"), result.toString());
        assertEquals("", jdbc.queryForObject("SELECT brand FROM equipment WHERE code='G-3'", String.class));
        assertEquals(1, result.get("updated"), result.toString());
        assertEquals(0, result.get("failed"), result.toString());
        assertEquals("历史枪支品牌", jdbc.queryForObject("SELECT brand FROM equipment WHERE code='G-1'", String.class));
        assertEquals("枪支", jdbc.queryForObject("SELECT category FROM equipment WHERE code='G-1'", String.class));
        assertEquals("KEEP-ID", jdbc.queryForObject("SELECT ident_code FROM equipment WHERE code='G-1'", String.class));
        assertEquals("2020-01-01", jdbc.queryForObject("SELECT buy_date FROM equipment WHERE code='G-1'", String.class));
        try (Workbook exported = read(service.export("equipment-gun", Arrays.asList(1L, 2L)))) {
            assertFalse(headers(exported.getSheetAt(0).getRow(0)).contains("品牌"));
            assertEquals(2, exported.getSheetAt(0).getPhysicalNumberOfRows());
            assertEquals("更新枪", exported.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test void gunImportValidatesEquipmentLocationDictionary() throws Exception {
        Map<String,Object> result = service.importExcel("equipment-gun", input(new String[][]{
                {"装备名称","装备编号","存放位置"}, {"测试枪","G-LOC","不存在的位置"}
        }));
        assertEquals(1, result.get("failed"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
    }

    @Test void gunImportValidatesStatusDictionary() throws Exception {
        Map<String,Object> result = service.importExcel("equipment-gun", input(new String[][]{
                {"装备名称","装备编号","状态"}, {"测试枪","G-STATUS","不存在的状态"}
        }));
        assertEquals(1, result.get("failed"), result.toString());
        assertTrue(result.get("errors").toString().contains("equipment_status"), result.toString());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
    }

    @Test void gunImportRejectsDisabledCategoryStatusAndLocation() throws Exception {
        for (String type : Arrays.asList("equipment_category", "equipment_status", "equipment_location")) {
            jdbc.update("UPDATE sys_dict SET enabled=0 WHERE dict_type=?", type);
            Map<String,Object> result = service.importExcel("equipment-gun", input(new String[][]{
                    {"装备名称","装备编号","类别","状态","存放位置"},
                    {"测试枪","G-DISABLED","枪支","正常","库房"}
            }));
            assertEquals(1, result.get("failed"), result.toString());
            assertTrue(result.get("errors").toString().contains(type), result.toString());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
            jdbc.update("UPDATE sys_dict SET enabled=1 WHERE dict_type=?", type);
        }
    }

    @Test void equipmentTemplatePlacesBrandBeforeModel() throws Exception {
        try (Workbook template = read(service.template("equipment"))) {
            assertEquals(Arrays.asList("装备名称", "装备编号", "识别码", "类别", "品牌", "型号", "状态",
                    "保管人", "存放位置", "装备时间", "备注"), headers(template.getSheetAt(0).getRow(0)));
        }
    }

    @Test void equipmentBrandImportsUpdatesAndRoundTripsThroughExport() throws Exception {
        Map<String,Object> added = service.importExcel("equipment", input(new String[][]{
                {"装备名称","识别码","类别","品牌","型号","状态","存放位置"},
                {"测试装备","E-BRAND","大型装备","品牌甲","型号一","正常","库房"}
        }));
        assertEquals(1, added.get("added"), added.toString());
        assertEquals(0, added.get("failed"), added.toString());
        assertEquals("品牌甲", jdbc.queryForObject("SELECT brand FROM equipment WHERE ident_code='E-BRAND'", String.class));
        Long id = jdbc.queryForObject("SELECT id FROM equipment WHERE ident_code='E-BRAND'", Long.class);
        byte[] bytes = service.export("equipment", Arrays.asList(id));
        try (Workbook exported = read(bytes); Workbook template = read(service.template("equipment"))) {
            Sheet sheet = exported.getSheetAt(0);
            assertEquals(headers(template.getSheetAt(0).getRow(0)), headers(sheet.getRow(0)));
            assertEquals("品牌甲", sheet.getRow(1).getCell(4).getStringCellValue());
            assertEquals("型号一", sheet.getRow(1).getCell(5).getStringCellValue());
        }
        Map<String,Object> updated = service.importExcel("equipment", input(new String[][]{
                {"装备名称","识别码","品牌"}, {"更新装备","E-BRAND","品牌乙"}
        }));
        assertEquals(1, updated.get("updated"), updated.toString());
        assertEquals("品牌乙", jdbc.queryForObject("SELECT brand FROM equipment WHERE id=?", String.class, id));
        Map<String,Object> roundTrip = service.importExcel("equipment", new ByteArrayInputStream(bytes));
        assertEquals(1, roundTrip.get("updated"), roundTrip.toString());
        assertEquals(0, roundTrip.get("failed"), roundTrip.toString());
        assertEquals("品牌甲", jdbc.queryForObject("SELECT brand FROM equipment WHERE id=?", String.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
    }

    @Test void equipmentImportRejectsUnknownAndDisabledBrandsWithoutChangingRows() throws Exception {
        jdbc.update("INSERT INTO equipment(name,ident_code,brand) VALUES('原装备','E-EXISTING','品牌甲')");
        Map<String,Object> result = service.importExcel("equipment", input(new String[][]{
                {"装备名称","识别码","品牌"},
                {"未知品牌新增","E-UNKNOWN","未知品牌"},
                {"停用品牌新增","E-DISABLED","停用品牌"},
                {"未知品牌更新","E-EXISTING","未知品牌"},
                {"停用品牌更新","E-EXISTING","停用品牌"}
        }));
        assertEquals(0, result.get("added"), result.toString());
        assertEquals(0, result.get("updated"), result.toString());
        assertEquals(4, result.get("failed"), result.toString());
        java.util.List<?> errors = (java.util.List<?>) result.get("errors");
        assertEquals(4, errors.size());
        for (Object error : errors) assertTrue(error.toString().contains("equipment_brand"), error.toString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
        assertEquals("原装备", jdbc.queryForObject("SELECT name FROM equipment", String.class));
        assertEquals("品牌甲", jdbc.queryForObject("SELECT brand FROM equipment", String.class));
    }

    @Test void equipmentImportWithoutBrandColumnPreservesStoredBrand() throws Exception {
        jdbc.update("INSERT INTO equipment(name,ident_code,brand,model) VALUES('原装备','E-OLD','品牌甲','旧型号')");
        Map<String,Object> result = service.importExcel("equipment", input(new String[][]{
                {"装备名称","识别码","型号"},
                {"更新装备","E-OLD","新型号"},
                {"无品牌装备","E-NO-BRAND","型号一"}
        }));
        assertEquals(1, result.get("updated"), result.toString());
        assertEquals(1, result.get("added"), result.toString());
        assertEquals(0, result.get("failed"), result.toString());
        assertEquals("品牌甲", jdbc.queryForObject("SELECT brand FROM equipment WHERE ident_code='E-OLD'", String.class));
        assertEquals("新型号", jdbc.queryForObject("SELECT model FROM equipment WHERE ident_code='E-OLD'", String.class));
        assertEquals("", jdbc.queryForObject("SELECT brand FROM equipment WHERE ident_code='E-NO-BRAND'", String.class));
    }

    @Test void equipmentBrandMayBeBlank() throws Exception {
        jdbc.update("INSERT INTO equipment(name,ident_code,brand) VALUES('原装备','E-CLEAR','品牌甲')");
        Map<String,Object> result = service.importExcel("equipment", input(new String[][]{
                {"装备名称","识别码","品牌"},
                {"清空品牌装备","E-CLEAR",""},
                {"空品牌装备","E-BLANK",""}
        }));
        assertEquals(1, result.get("updated"), result.toString());
        assertEquals(1, result.get("added"), result.toString());
        assertEquals(0, result.get("failed"), result.toString());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM equipment WHERE brand=''", Integer.class));
    }

    @Test void normalEquipmentUsesOnlyIdentCodeAndAllowsRepeatedEquipmentCodes() throws Exception {
        assertCounts(service.importExcel("equipment", input(new String[][]{
                {"装备名称","装备编号","识别码","类别"},
                {"大型一","E-1","IDENT-1","大型装备"},
                {"小型二","E-1","IDENT-2","小型装备"},
                {"无编号装备","","IDENT-3","大型装备"}
        })), 3, 0, 0);
        assertCounts(service.importExcel("equipment", input(new String[][]{
                {"装备名称","装备编号","识别码","类别"},
                {"识别码一更新","E-NEW","IDENT-1","小型装备"}
        })), 0, 1, 0);
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
        assertEquals("小型二", jdbc.queryForObject("SELECT name FROM equipment WHERE ident_code='IDENT-2'", String.class));
        assertEquals("小型装备", jdbc.queryForObject("SELECT category FROM equipment WHERE ident_code='IDENT-1'", String.class));
        assertEquals("E-NEW", jdbc.queryForObject("SELECT code FROM equipment WHERE ident_code='IDENT-1'", String.class));
    }

    @Test void normalEquipmentRejectsMissingOrBlankIdentCodeInsteadOfFallingBackToCode() throws Exception {
        jdbc.update("INSERT INTO equipment(name,code,ident_code) VALUES('原装备','E-1','IDENT-1')");
        Map<String,Object> missing = service.importExcel("equipment", input(new String[][]{
                {"装备名称","装备编号"}, {"缺识别码列","E-1"}
        }));
        assertCounts(missing, 0, 0, 1);
        assertTrue(missing.get("errors").toString().contains("ident_code"));
        assertCounts(service.importExcel("equipment", input(new String[][]{
                {"装备名称","装备编号","识别码"}, {"空识别码","E-1",""}, {"空名称","","IDENT-2"}, {"","","IDENT-3"}
        })), 1, 0, 2);
        assertEquals("原装备", jdbc.queryForObject("SELECT name FROM equipment WHERE ident_code='IDENT-1'", String.class));
    }

    @Test void equipmentIdentCodeMatchingExcludesGunsAndRejectsAmbiguousNonGunMatches() throws Exception {
        jdbc.update("INSERT INTO equipment(name,code,ident_code,category) VALUES('历史枪','G-1','SHARED','枪支')");
        assertCounts(service.importExcel("equipment", input(new String[][]{
                {"装备名称","识别码","类别"}, {"普通装备","SHARED","大型装备"}
        })), 1, 0, 0);
        assertCounts(service.importExcel("equipment", input(new String[][]{
                {"装备名称","识别码"}, {"普通装备更新","SHARED"}
        })), 0, 1, 0);
        assertEquals("历史枪", jdbc.queryForObject("SELECT name FROM equipment WHERE category='枪支'", String.class));
        jdbc.update("INSERT INTO equipment(name,ident_code,category) VALUES('重复识别码','SHARED','小型装备')");
        Map<String,Object> ambiguous = service.importExcel("equipment", input(new String[][]{
                {"装备名称","识别码"}, {"不能任选","SHARED"}
        }));
        assertCounts(ambiguous, 0, 0, 1);
        assertTrue(ambiguous.get("errors").toString().contains("匹配多条"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
    }

    @Test void genericEquipmentGunRowsMatchOnlyGunCodeAndDoNotRequireIdentCode() throws Exception {
        jdbc.update("INSERT INTO equipment(name,code,ident_code,category) VALUES('普通装备','G-1','ORDINARY','大型装备')");
        jdbc.update("INSERT INTO equipment(name,code,ident_code,category,buy_date) VALUES('历史枪','G-1','LEGACY','枪支','2020-01-01')");
        assertCounts(service.importExcel("equipment", input(new String[][]{
                {"装备名称","装备编号","类别"}, {"更新枪","G-1","枪支"}, {"新枪","G-2","枪支"}, {"无编号枪","","枪支"}
        })), 1, 1, 1);
        assertEquals("普通装备", jdbc.queryForObject("SELECT name FROM equipment WHERE ident_code='ORDINARY'", String.class));
        assertEquals("更新枪", jdbc.queryForObject("SELECT name FROM equipment WHERE code='G-1' AND category='枪支'", String.class));
        assertEquals("LEGACY", jdbc.queryForObject("SELECT ident_code FROM equipment WHERE code='G-1' AND category='枪支'", String.class));
        assertEquals("2020-01-01", jdbc.queryForObject("SELECT buy_date FROM equipment WHERE code='G-1' AND category='枪支'", String.class));
    }

    @Test void equipmentOmitsPurchaseDateAndIgnoresLegacyImportColumn() throws Exception {
        jdbc.update("INSERT INTO equipment(name,ident_code,buy_date) VALUES('装备','EQ-OLD','2020-03-04')");
        try (Workbook template = read(service.template("equipment"))) {
            assertFalse(headers(template.getSheetAt(0).getRow(0)).contains("购置日期"));
            assertTrue(headers(template.getSheetAt(0).getRow(0)).contains("装备时间"));
        }
        Map<String,Object> result = service.importExcel("equipment", input(new String[][]{
                {"装备名称","识别码","购置日期"}, {"更新装备","EQ-OLD","2026-01-01"}
        }));
        assertEquals(1, result.get("updated"), result.toString());
        assertEquals("2020-03-04", jdbc.queryForObject("SELECT buy_date FROM equipment", String.class));
        try (Workbook exported = read(service.export("equipment", Arrays.asList(1L)))) {
            assertFalse(headers(exported.getSheetAt(0).getRow(0)).contains("购置日期"));
        }
    }

    @Test void vehicleTemplateOmitsLegacyFieldsAndImportPreservesTheirStoredValues() throws Exception {
        jdbc.update("INSERT INTO vehicle(plate_no,vin,fuel_type,mileage,purchase_date,insurance_expiry) VALUES('浙A12345','VIN-1','汽油',12345,'2020-02-03','2026-02-03')");
        try (Workbook template = read(service.template("vehicle"))) {
            String headers = headers(template.getSheetAt(0).getRow(0)).toString();
            assertFalse(headers.contains("燃料类型"));
            assertFalse(headers.contains("里程(公里)"));
            assertFalse(headers.contains("购置日期"));
            assertFalse(headers.contains("保险到期日"));
        }
        Map<String,Object> result = service.importExcel("vehicle", input(new String[][]{
                {"车牌号","车架号"}, {"浙A12345","VIN-1"}
        }));
        assertEquals(1, result.get("updated"), result.toString());
        assertEquals("汽油", jdbc.queryForObject("SELECT fuel_type FROM vehicle", String.class));
        assertEquals(12345.0, jdbc.queryForObject("SELECT mileage FROM vehicle", Double.class));
        assertEquals("2020-02-03", jdbc.queryForObject("SELECT purchase_date FROM vehicle", String.class));
        assertEquals("2026-02-03", jdbc.queryForObject("SELECT insurance_expiry FROM vehicle", String.class));
    }

    @Test void fitnessExportResolvesPersonnelNameAndIdCardAndMilitaryImportAppends() throws Exception {
        jdbc.update("INSERT INTO fitness_record(personnel_id,test_date,item,score) VALUES(1,'2026-05-01','跑步','12')");
        try (Workbook exported = read(service.export("fitness", Arrays.asList(1L)))) {
            Row row = exported.getSheetAt(0).getRow(1);
            assertEquals("张三", row.getCell(0).getStringCellValue());
            assertEquals("110105199001011234", row.getCell(1).getStringCellValue());
        }
        String[][] data = {{"姓名","考核类别","考核项目","成绩","分数","评定等级","考核日期"},
                {"张三","训练","射击","合格","80","良好","2026-05-02"}};
        Map<String,Object> imported = service.importExcel("military", input(data));
        assertEquals(1, imported.get("added"), imported.toString());
        data[1][3] = "优秀";
        assertCounts(service.importExcel("military", input(data)), 1, 0, 0);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM military_assessment", Integer.class));
        assertEquals("合格", jdbc.queryForObject("SELECT score FROM military_assessment WHERE id=1", String.class));
        assertEquals("优秀", jdbc.queryForObject("SELECT score FROM military_assessment WHERE id=2", String.class));
    }

    @Test void incrementalModulesAppendRepeatedRowsWhileFitnessUpsertsSamePersonItemDate() throws Exception {
        String[] modules = {"fitness", "military", "leave-plan", "leave", "material"};
        String[] tables = {"fitness_record", "military_assessment", "leave_plan", "leave_record", "material"};
        String[][][] data = {
                {{"姓名","考核日期","考核项目","成绩"}, {"张三","2026-05-01","仰卧起坐","60"}},
                {{"姓名","考核类别","考核项目","成绩","考核日期"}, {"张三","训练","射击","合格","2026-05-01"}},
                {{"人员姓名","年度","休假类型","计划开始日期","计划结束日期","天数","备注"}, {"张三","2026","年假","2026-05-01","2026-05-02","2","原计划"}},
                {{"人员姓名","休假类型","开始日期","结束日期","天数","备注"}, {"张三","年假","2026-05-01","2026-05-02","2","原登记"}},
                {{"物资名称","分类","规格","单位","存放位置","详细存放点","数量"}, {"纸张","办公用品","A4","包","库房","一号柜","10"}}
        };
        for (int i = 0; i < modules.length; i++) {
            assertCounts(service.importExcel(modules[i], input(data[i])), 1, 0, 0);
            Map<String,Object> old = jdbc.queryForMap("SELECT * FROM " + tables[i] + " WHERE id=1");
            String[][] duplicateRows = {data[i][0], data[i][1], data[i][1]};
            if ("fitness".equals(modules[i])) {
                // 体能同人+同项目+同考核日期按覆盖更新:两行重复→首行新增、次行更新原记录。
                assertCounts(service.importExcel(modules[i], input(duplicateRows)), 0, 2, 0);
                assertCounts(service.importExcel(modules[i], input(data[i])), 0, 1, 0);
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + tables[i], Integer.class), modules[i]);
            } else {
                assertCounts(service.importExcel(modules[i], input(duplicateRows)), 2, 0, 0);
                // 已经存在多条相同记录也不查重、不拒绝下一次导入。
                assertCounts(service.importExcel(modules[i], input(data[i])), 1, 0, 0);
                assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM " + tables[i], Integer.class), modules[i]);
            }
            if (!"fitness".equals(modules[i])) {
                assertEquals(old, jdbc.queryForMap("SELECT * FROM " + tables[i] + " WHERE id=1"), modules[i]);
            }
        }
    }

    @Test void repeatedLeaveImportsStillSynchronizePersonnelStatus() throws Exception {
        String today = java.time.LocalDate.now().toString();
        String[][] data = {{"人员姓名","休假类型","开始日期","结束日期","天数"}, {"张三","年假",today,today,"1"}};
        assertCounts(service.importExcel("leave", input(data)), 1, 0, 0);
        assertEquals("休假", jdbc.queryForObject("SELECT status FROM personnel WHERE id=1", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT auto_leave FROM personnel WHERE id=1", Integer.class));
        assertEquals("在职", jdbc.queryForObject("SELECT auto_leave_previous_status FROM personnel WHERE id=1", String.class));
        assertCounts(service.importExcel("leave", input(data)), 1, 0, 0);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM leave_record", Integer.class));
        assertEquals("休假", jdbc.queryForObject("SELECT status FROM personnel WHERE id=1", String.class));
        assertEquals("在职", jdbc.queryForObject("SELECT auto_leave_previous_status FROM personnel WHERE id=1", String.class));
    }

    @Test void incrementalImportsKeepRequiredDatePersonnelDictionaryAndScoreValidation() throws Exception {
        assertCounts(service.importExcel("leave", input(new String[][]{
                {"人员姓名","休假类型","开始日期","结束日期","天数","路途天数"},
                {"张三","年假","2026-05-02","2026-05-01","1","0"},
                {"张三","年假","2026-02-30","2026-03-01","1","0"},
                {"不存在","年假","2026-05-01","2026-05-02","1","0"},
                {"张三","未知类型","2026-05-01","2026-05-02","1","0"},
                {"张三","年假","2026-05-01","2026-05-02","","0"},
                {"张三","年假","2026-05-01","2026-05-02","1","-1"}
        })), 0, 0, 6);
        assertCounts(service.importExcel("leave-plan", input(new String[][]{
                {"人员姓名","年度","休假类型","计划开始日期","计划结束日期","天数"},
                {"张三","","年假","2026-05-01","2026-05-02","2"},
                {"张三","2026","年假","2026-05-02","2026-05-01","2"}
        })), 0, 0, 2);
        assertCounts(service.importExcel("material", input(new String[][]{
                {"物资名称","分类","数量"}, {"","办公用品","1"}, {"纸张","办公用品",""}, {"纸张","未知分类","1"}
        })), 0, 0, 3);
        assertCounts(service.importExcel("fitness", input(new String[][]{
                {"姓名","考核日期","考核项目","成绩"}, {"张三","","仰卧起坐","60"}, {"张三","2026-05-01","仰卧起坐","错误成绩"}
        })), 0, 0, 2);
        assertCounts(service.importExcel("military", input(new String[][]{
                {"姓名","考核类别","考核项目","成绩","考核日期"},
                {"张三","训练","射击","合格",""}, {"张三","未知类别","射击","合格","2026-05-01"}
        })), 0, 0, 2);
        for (String table : Arrays.asList("leave_record", "leave_plan", "material", "fitness_record", "military_assessment")) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class), table);
        }
    }

    @Test void phoneUsesRequiredIdCardForMatchingAndRoundTripsIdentity() throws Exception {
        // 同名人员必须通过身份证区分；SN/IMEI相同不改变导入匹配规则。
        jdbc.update("INSERT INTO personnel(id,name,id_card) VALUES(2,'张三','110105199001011235')");
        assertCounts(service.importExcel("phone", input(new String[][]{
                {"姓名","身份证号","设备类型","品牌","序列号","IMEI1","手机号"},
                {"张三","110105199001011234","地方手机","华为","SAME-SN","SAME-IMEI","13800000001"},
                {"张三","110105199001011235","地方手机","华为","SAME-SN","SAME-IMEI","13800000002"}
        })), 2, 0, 0);
        assertCounts(service.importExcel("phone", input(new String[][]{
                {"身份证号码","设备类型","品牌","序列号","手机号"},
                {"110105199001011234","地方手机","华为","UPDATED-SN","13900000001"}
        })), 0, 1, 0);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM phone_device", Integer.class));
        assertEquals("UPDATED-SN", jdbc.queryForObject("SELECT serial_no FROM phone_device WHERE personnel_id=1", String.class));
        assertEquals("SAME-SN", jdbc.queryForObject("SELECT serial_no FROM phone_device WHERE personnel_id=2", String.class));
        assertEquals("13900000001", jdbc.queryForObject("SELECT phone FROM personnel WHERE id=1", String.class));
        assertEquals("13800000002", jdbc.queryForObject("SELECT phone FROM personnel WHERE id=2", String.class));
        try (Workbook template = read(service.template("phone")); Workbook exported = read(service.export("phone", Arrays.asList(1L, 2L)))) {
            assertTrue(headers(template.getSheetAt(0).getRow(0)).contains("身份证号"));
            assertEquals("110105199001011234", exported.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
            assertEquals("110105199001011235", exported.getSheetAt(0).getRow(2).getCell(1).getStringCellValue());
        }
        assertCounts(service.importExcel("phone", new ByteArrayInputStream(service.export("phone", Arrays.asList(1L, 2L)))), 0, 2, 0);
    }

    @Test void phoneRejectsMissingOrBlankIdCardEvenWhenNameIsUnique() throws Exception {
        Map<String,Object> missing = service.importExcel("phone", input(new String[][]{
                {"姓名","设备类型","品牌"}, {"张三","地方手机","华为"}
        }));
        assertCounts(missing, 0, 0, 1);
        assertTrue(missing.get("errors").toString().contains("身份证号不能为空"));
        assertCounts(service.importExcel("phone", input(new String[][]{
                {"姓名","身份证号","设备类型","品牌"}, {"张三","","地方手机","华为"}, {"张三","不存在的身份证","地方手机","华为"}
        })), 0, 0, 2);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM phone_device", Integer.class));
    }

    @Test void vehicleMatchesVinNotPlateAndRejectsMissingVin() throws Exception {
        assertCounts(service.importExcel("vehicle", input(new String[][]{
                {"车牌号","车架号"}, {"同一车牌","VIN-1"}, {"同一车牌","VIN-2"}
        })), 2, 0, 0);
        assertCounts(service.importExcel("vehicle", input(new String[][]{
                {"车牌号","车架号"}, {"更新车牌","VIN-1"}, {"缺车架号",""}
        })), 0, 1, 1);
        assertCounts(service.importExcel("vehicle", input(new String[][]{
                {"车牌号"}, {"缺车架号列"}
        })), 0, 0, 1);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM vehicle", Integer.class));
        assertEquals("更新车牌", jdbc.queryForObject("SELECT plate_no FROM vehicle WHERE vin='VIN-1'", String.class));
        assertEquals("同一车牌", jdbc.queryForObject("SELECT plate_no FROM vehicle WHERE vin='VIN-2'", String.class));
    }

    @AfterEach void clearScope() { DataScope.clear(); }

    private void scopedFixture() {
        jdbc.execute("ALTER TABLE personnel ADD COLUMN department_id INTEGER DEFAULT 1");
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(2,'外域人员','110105199001011235',2)");
        DataScope.set(new DataScope.Context(10L, "sub", false, java.util.Collections.singleton(1L)));
    }

    @Test void scopedBusinessKeyUpdatesCheckOldAndNewOwnersGlobally() throws Exception {
        scopedFixture();
        jdbc.update("INSERT INTO equipment(id,name,code,ident_code,category,keeper_id) VALUES(1,'外域枪','G-OUT','','枪支',2)");
        jdbc.update("INSERT INTO equipment(id,name,ident_code,keeper_id) VALUES(2,'外域装备','E-OUT',2)");
        jdbc.update("INSERT INTO equipment(id,name,ident_code,keeper_id) VALUES(3,'本域装备','E-IN',1)");
        jdbc.update("INSERT INTO vehicle(id,plate_no,vin,driver_id) VALUES(1,'外域车','VIN-OUT',2)");
        jdbc.update("INSERT INTO phone_device(id,personnel_id,device_type,brand) VALUES(1,2,'地方手机','华为')");
        assertCounts(service.importExcel("equipment-gun", input(new String[][]{
                {"装备名称","装备编号","保管人"}, {"抢占枪","G-OUT","张三"}})), 0, 0, 1);
        assertCounts(service.importExcel("equipment", input(new String[][]{
                {"装备名称","识别码","保管人"}, {"抢占装备","E-OUT","张三"}, {"移出范围","E-IN","外域人员"}})), 0, 0, 2);
        assertCounts(service.importExcel("vehicle", input(new String[][]{
                {"车牌号","车架号","驾驶员"}, {"抢占车","VIN-OUT","张三"}})), 0, 0, 1);
        assertCounts(service.importExcel("phone", input(new String[][]{
                {"姓名","身份证号","设备类型","品牌"}, {"外域人员","110105199001011235","地方手机","华为"}})), 0, 0, 1);
        assertEquals("外域枪", jdbc.queryForObject("SELECT name FROM equipment WHERE id=1", String.class));
        assertEquals(1L, jdbc.queryForObject("SELECT keeper_id FROM equipment WHERE id=3", Long.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vehicle", Integer.class));
        // 省略保管人列仍可更新本域旧记录，不能绕过外域旧记录归属。
        assertCounts(service.importExcel("equipment", input(new String[][]{
                {"装备名称","识别码"}, {"本域更新","E-IN"}, {"越域更新","E-OUT"}})), 0, 1, 1);
    }

    @Test void scopedExportsValidateEveryIdAndPlanWithoutIdsOnlyExportsScope() throws Exception {
        scopedFixture();
        String[] modules = {"equipment", "equipment-gun", "vehicle", "material", "fitness", "military", "leave", "leave-plan", "phone"};
        String[] tables = {"equipment", "equipment", "vehicle", "material", "fitness_record", "military_assessment", "leave_record", "leave_plan", "phone_device"};
        String[] owners = {"keeper_id", "keeper_id", "driver_id", "keeper_id", "personnel_id", "personnel_id", "personnel_id", "personnel_id", "personnel_id"};
        for (int i = 0; i < modules.length; i++) {
            jdbc.update("DELETE FROM " + tables[i]);
            jdbc.update("INSERT INTO " + tables[i] + "(id," + owners[i] + ") VALUES(1,1),(2,2),(3,0),(4,NULL)");
            if (modules[i].equals("equipment-gun")) jdbc.update("UPDATE equipment SET category='枪支'");
            final String module = modules[i];
            assertThrows(ForbiddenException.class, () -> service.export(module, Arrays.asList(1L, 2L)), module);
            assertThrows(ForbiddenException.class, () -> service.export(module, Arrays.asList(3L)), module);
            assertThrows(ForbiddenException.class, () -> service.export(module, Arrays.asList(4L)), module);
            assertThrows(ForbiddenException.class, () -> service.export(module, Arrays.asList(999L)), module);
            try (Workbook wb = read(service.export(module, Arrays.asList(1L)))) {
                assertEquals(2, wb.getSheetAt(0).getPhysicalNumberOfRows(), module);
            }
        }
        try (Workbook wb = read(service.export("leave-plan", java.util.Collections.emptyList()))) {
            assertEquals(2, wb.getSheetAt(0).getPhysicalNumberOfRows());
        }
        DataScope.set(new DataScope.Context(10L, "empty", false, java.util.Collections.emptySet()));
        try (Workbook wb = read(service.export("leave-plan", null))) {
            assertEquals(1, wb.getSheetAt(0).getPhysicalNumberOfRows());
        }
    }

    @Test void scopedAppendImportsRejectForeignPersonnelAndKeepAppendSemantics() throws Exception {
        scopedFixture();
        String[][] military = {{"姓名","考核类别","考核项目","成绩","考核日期"},
                {"张三","训练","射击","合格","2026-05-01"}, {"张三","训练","射击","合格","2026-05-01"},
                {"外域人员","训练","射击","合格","2026-05-01"}};
        assertCounts(service.importExcel("military", input(military)), 2, 0, 1);
        assertCounts(service.importExcel("fitness", input(new String[][]{
                {"姓名","考核日期","考核项目","成绩"}, {"外域人员","2026-05-01","仰卧起坐","60"}})), 0, 0, 1);
        assertCounts(service.importExcel("leave-plan", input(new String[][]{
                {"身份证号","年度","休假类型","计划开始日期","计划结束日期","天数"},
                {"110105199001011235","2026","年假","2026-05-01","2026-05-02","2"}})), 0, 0, 1);
        assertCounts(service.importExcel("leave", input(new String[][]{
                {"人员姓名","休假类型","开始日期","结束日期","天数"},
                {"外域人员","年假","2026-05-01","2026-05-02","2"}})), 0, 0, 1);
        String[][] materials = {{"物资名称","数量","责任人"}, {"纸张","1","张三"}, {"纸张","1","张三"}, {"纸张","1","外域人员"}, {"无责任人","1",""}};
        assertCounts(service.importExcel("material", input(materials)), 2, 0, 2);
        DataScope.set(new DataScope.Context(10L, "empty", false, java.util.Collections.emptySet()));
        assertCounts(service.importExcel("military", input(military)), 0, 0, 3);
    }

    @Test void scopedDictionaryImportIsForbiddenButBusinessTemplatesAreAvailable() throws Exception {
        scopedFixture();
        assertThrows(ForbiddenException.class, () -> service.importExcel("dict", input(new String[][]{{"字典类型","选项内容"},{"test","bad"}})));
        for (String module : Arrays.asList("equipment", "equipment-gun", "vehicle", "material", "fitness", "military", "leave", "leave-plan", "phone")) {
            try (Workbook wb = read(service.template(module))) { assertEquals(2, wb.getSheetAt(0).getPhysicalNumberOfRows()); }
        }
    }

    @Test void rootScopeKeepsUnownedLegacyExportAndImportBehavior() throws Exception {
        DataScope.set(new DataScope.Context(1L, "root", true, java.util.Collections.emptySet()));
        // 旧测试数据库故意无 department_id，管理员 guard 不得查询归属。
        assertCounts(service.importExcel("equipment", input(new String[][]{{"装备名称","识别码"},{"无保管人装备","ROOT-ID"}})), 1, 0, 0);
        try (Workbook wb = read(service.export("equipment", Arrays.asList(1L)))) { assertEquals(2, wb.getSheetAt(0).getPhysicalNumberOfRows()); }
    }

    private static void assertCounts(Map<String,Object> result, int added, int updated, int failed) {
        assertEquals(added, result.get("added"), result.toString());
        assertEquals(updated, result.get("updated"), result.toString());
        assertEquals(failed, result.get("failed"), result.toString());
        assertFalse(result.containsKey("error"), result.toString());
    }

    private static java.util.List<String> headers(Row row) {
        java.util.List<String> result = new java.util.ArrayList<String>();
        for (org.apache.poi.ss.usermodel.Cell cell : row) result.add(cell.getStringCellValue());
        return result;
    }
    private static Workbook read(byte[] bytes) throws Exception { return new XSSFWorkbook(new ByteArrayInputStream(bytes)); }
    private static ByteArrayInputStream input(String[][] values) throws Exception { return new ByteArrayInputStream(workbook(values)); }
    private static byte[] workbook(String[][] values) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("data");
            for (int r = 0; r < values.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < values[r].length; c++) row.createCell(c).setCellValue(values[r][c]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
