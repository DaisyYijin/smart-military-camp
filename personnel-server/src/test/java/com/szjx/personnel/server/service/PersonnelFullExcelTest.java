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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PersonnelFullExcelTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private PersonnelFullExcel excel;

    @BeforeEach
    void setUp() {
        jdbc = PersonnelIntegrityTest.temporaryDatabase(temp);
        PersonnelIntegrityTest.personnelSchema(jdbc);
        jdbc.execute("CREATE TABLE sys_dict(id INTEGER PRIMARY KEY AUTOINCREMENT, dict_type TEXT, dict_label TEXT, enabled INTEGER DEFAULT 1)");
        jdbc.execute("CREATE TABLE position_history(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER NOT NULL, start_date TEXT DEFAULT '', end_date TEXT DEFAULT '', place TEXT DEFAULT '', post TEXT DEFAULT '', remark TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE reward_punish(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER NOT NULL, kind TEXT DEFAULT '', occur_date TEXT DEFAULT '', reason TEXT DEFAULT '', content TEXT DEFAULT '', remark TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE family_member(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER NOT NULL, relationship TEXT DEFAULT '', name TEXT DEFAULT '', id_card TEXT DEFAULT '', gender TEXT DEFAULT '', birth_date TEXT DEFAULT '', political_status TEXT DEFAULT '', work_info TEXT DEFAULT '', phone TEXT DEFAULT '', remark TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE driver_license(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER NOT NULL, license_type TEXT DEFAULT '', license_no TEXT DEFAULT '', vehicle_class TEXT DEFAULT '', issue_date TEXT DEFAULT '', expiry_date TEXT DEFAULT '', issuing_auth TEXT DEFAULT '', license_status TEXT DEFAULT '', review_date TEXT DEFAULT '', remark TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE personal_card(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER NOT NULL, card_type TEXT DEFAULT '', card_no TEXT DEFAULT '', bank_name TEXT DEFAULT '', branch_name TEXT DEFAULT '', open_date TEXT DEFAULT '', card_status TEXT DEFAULT '', remark TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE private_vehicle(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER NOT NULL, plate_no TEXT DEFAULT '', brand TEXT DEFAULT '', color TEXT DEFAULT '', purchase_date TEXT DEFAULT '', price TEXT DEFAULT '', insurance_date TEXT DEFAULT '', vehicle_status TEXT DEFAULT '', owner_name TEXT DEFAULT '', remark TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE housing_info(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER NOT NULL, housing_type TEXT DEFAULT '', address TEXT DEFAULT '', area TEXT DEFAULT '', house_nature TEXT DEFAULT '', has_cert TEXT DEFAULT '', loan_status TEXT DEFAULT '', purchase_date TEXT DEFAULT '', owner_name TEXT DEFAULT '', remark TEXT DEFAULT '')");
        addDict("personnel_category", "军官"); addDict("rank", "上尉"); addDict("personnel_status", "在职");
        addDict("education", "本科"); addDict("marital_status", "未婚"); addDict("nation", "汉族");
        addDict("blood_type", "A"); addDict("political_status", "党员");
        excel = new PersonnelFullExcel(jdbc);
    }

    @Test
    void templateHasExpandedColumnsButNoExampleData() throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel.template()))) {
            Sheet main = wb.getSheet("人员信息");
            assertEquals("入学时间", main.getRow(0).getCell(36).getStringCellValue());
            assertEquals("学制", main.getRow(0).getCell(42).getStringCellValue());
            assertEquals("配偶姓名", main.getRow(0).getCell(43).getStringCellValue());
            assertEquals("照片文件名", main.getRow(0).getCell(51).getStringCellValue());
            assertEquals("导出人员编号", main.getRow(0).getCell(52).getStringCellValue());
            assertEquals("军人证件号", main.getRow(0).getCell(53).getStringCellValue());
            assertEquals("单位完整路径（只读）", main.getRow(0).getCell(54).getStringCellValue());
            assertEquals("实际单位", main.getRow(0).getCell(55).getStringCellValue());
            assertNull(main.getRow(0).getCell(56), "实际岗位/实际在岗开始时间列已删除");
            assertNull(main.getRow(0).getCell(57), "实际岗位/实际在岗开始时间列已删除");
            assertEquals(1, main.getPhysicalNumberOfRows());
        }
    }

    @Test void militaryIdentityColumnIsRequiredBeforeAnyImportSideEffects() throws Exception {
        assertEquals(1, excel.importAll(new ByteArrayInputStream(changedWorkbook("机关", "11010519491231002X"))).get("added"));
        for (String value : new String[]{"", " 	 "}) {
            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(changedWorkbook("不应创建的单位", "11010519491231002X")))) {
                Sheet main = wb.getSheetAt(0);
                set(main.getRow(1), main.getRow(0), "军人证件号", value);
                ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out);
                Map<String,Object> result = excel.importAll(new ByteArrayInputStream(out.toByteArray()));
                assertEquals(1, result.get("failed"), result.toString());
                assertTrue(result.get("errors").toString().contains("军人证件号不能为空"));
            }
        }
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(changedWorkbook("不应创建的单位", "11010519491231002X")))) {
            Row header = wb.getSheetAt(0).getRow(0);
            for (org.apache.poi.ss.usermodel.Cell cell : header) {
                if ("军人证件号".equals(cell.getStringCellValue())) { header.removeCell(cell); break; }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out);
            Map<String,Object> result = excel.importAll(new ByteArrayInputStream(out.toByteArray()));
            assertTrue(result.get("error").toString().contains("军人证件号"));
            assertTrue(result.get("error").toString().contains("新模板"));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM department", Integer.class));
        assertEquals("军证测试-001", jdbc.queryForObject("SELECT military_id_no FROM personnel", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM position_history", Integer.class));
    }

    @Test void militaryIdentityAndLeafDepartmentRoundTripWhileFullPathStaysReadOnly() throws Exception {
        jdbc.update("INSERT INTO department(id,name,parent_id) VALUES(10,'营部',20),(20,'总队',0)");
        jdbc.update("UPDATE department SET parent_id=10 WHERE id=1");
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(changedWorkbook("机关", "11010519491231002X")))) {
            Sheet main = wb.getSheetAt(0);
            set(main.getRow(1), main.getRow(0), "军人证件号", "  0000123-军证文本  ");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out);
            assertEquals(1, excel.importAll(new ByteArrayInputStream(out.toByteArray())).get("added"));
        }
        Long id = jdbc.queryForObject("SELECT id FROM personnel", Long.class);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel.export(java.util.Collections.singletonList(id))))) {
            Sheet main = wb.getSheetAt(0);
            assertEquals("机关", cell(main.getRow(1), "编制单位").getStringCellValue());
            assertEquals("总队营部机关", cell(main.getRow(1), "单位完整路径（只读）").getStringCellValue());
            assertEquals("0000123-军证文本", cell(main.getRow(1), "军人证件号").getStringCellValue());
            assertEquals(org.apache.poi.ss.usermodel.CellType.STRING, cell(main.getRow(1), "军人证件号").getCellType());
            set(main.getRow(1), main.getRow(0), "单位完整路径（只读）", "伪造 / 不应匹配为单位");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out);
            Map<String,Object> result = excel.importAll(new ByteArrayInputStream(out.toByteArray()));
            assertEquals(1, result.get("updated"), result.toString());
            assertEquals(0, result.get("failed"));
        }
        assertEquals(1L, jdbc.queryForObject("SELECT department_id FROM personnel", Long.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM department", Integer.class));
        assertEquals("0000123-军证文本", jdbc.queryForObject("SELECT military_id_no FROM personnel", String.class));
        assertFalse(jdbc.queryForMap("SELECT * FROM personnel").containsKey("department_path"));
        jdbc.update("UPDATE personnel SET military_id_no=''");
        Map<String,Object> legacy = excel.importAll(new ByteArrayInputStream(excel.export(java.util.Collections.singletonList(id))));
        assertEquals(1, legacy.get("failed"));
        assertTrue(legacy.get("errors").toString().contains("军人证件号不能为空"));
    }

    @Test void fallbackExcelPreservesMilitaryTextAndDatesButRejectsMissingOrBlankIdentity() throws Exception {
        com.szjx.personnel.server.model.Personnel p = new com.szjx.personnel.server.model.Personnel();
        p.setName("备用导出人员"); p.setIdCard("11010519491231002X"); p.setDepartmentId(1L);
        p.setMilitaryIdNo("00001234-军证"); p.setDepartmentPath("总队 / 机关");
        byte[] bytes = com.szjx.personnel.server.util.ExcelUtils.exportXlsx(java.util.Collections.singletonList(p), id -> "机关");
        for (String date : new String[]{"20160901", "2016-09-01", "2016/9/1", "2016.9.1", "2016年9月1日"}) {
            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                Sheet sheet = wb.getSheetAt(0); set(sheet.getRow(1), sheet.getRow(0), "出生日期", date);
                ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out);
                com.szjx.personnel.server.model.Personnel back = com.szjx.personnel.server.util.ExcelUtils.importXlsx(out.toByteArray()).get(0);
                assertEquals("00001234-军证", back.getMilitaryIdNo());
                assertEquals("机关", back.getDepartmentName()); assertNull(back.getDepartmentPath());
                assertEquals("2016-09-01", back.getBirthday());
            }
        }
        for (boolean missing : new boolean[]{false, true}) {
            try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                Sheet sheet = wb.getSheetAt(0);
                if (missing) sheet.getRow(0).removeCell(cell(sheet.getRow(0), "军人证件号"));
                else set(sheet.getRow(1), sheet.getRow(0), "军人证件号", "   ");
                ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out);
                java.io.IOException error = assertThrows(java.io.IOException.class,
                        () -> com.szjx.personnel.server.util.ExcelUtils.importXlsx(out.toByteArray()));
                assertTrue(error.getMessage().contains("军人证件号"));
                if (missing) assertTrue(error.getMessage().contains("新模板"));
            }
        }
    }

    private static org.apache.poi.ss.usermodel.Cell cell(Row row, String label) {
        for (org.apache.poi.ss.usermodel.Cell header : row.getSheet().getRow(0)) {
            if (label.equals(header.getStringCellValue())) return row.getCell(header.getColumnIndex());
        }
        throw new AssertionError("Missing header: " + label);
    }

    @Test
    void importsAliasesDatesExtrasAndChildrenOnlyForValidPeopleWithoutDuplicates() throws Exception {
        byte[] bytes = workbookWithData();
        Map<String, Object> result = excel.importAll(new ByteArrayInputStream(bytes));
        assertEquals(1, result.get("added"));
        assertEquals(1, result.get("failed"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
        assertEquals("2018-07-01", jdbc.queryForObject("SELECT post_date FROM personnel", String.class));
        assertEquals("2020-09-01", jdbc.queryForObject("SELECT edu_enroll_date FROM personnel", String.class));
        assertEquals("配偶甲", jdbc.queryForObject("SELECT spouse_name FROM personnel", String.class));
        assertEquals("党团备注内容", jdbc.queryForObject("SELECT party_remark FROM personnel", String.class));
        assertEquals("婚姻备注内容", jdbc.queryForObject("SELECT marriage_remark FROM personnel", String.class));
        assertEquals("人员备注内容", jdbc.queryForObject("SELECT remark FROM personnel", String.class));
        assertEquals("", jdbc.queryForObject("SELECT photo FROM personnel", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM position_history", Integer.class));
        assertTrue(String.valueOf(result.get("errors")).contains("政治面貌不能为空"));
        assertTrue(String.valueOf(result.get("errors")).contains("日期格式不正确"));

        Map<String, Object> again = excel.importAll(new ByteArrayInputStream(bytes));
        assertEquals(1, again.get("updated"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM position_history", Integer.class));
    }

    @Test void mainSheetPhoneIsReadOnlyAndAlwaysMirrorsNewestNonblankDeviceNumber() throws Exception {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(changedWorkbook("机关", "11010519491231002X")))) {
            Sheet main = wb.getSheet("人员信息");
            set(main.getRow(1), main.getRow(0), "手机号", "伪造的Excel号码");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out); bytes = out.toByteArray();
        }
        Map<String, Object> added = excel.importAll(new ByteArrayInputStream(bytes));
        assertEquals(1, added.get("added"), added.toString());
        assertEquals("", jdbc.queryForObject("SELECT phone FROM personnel", String.class));
        Long id = jdbc.queryForObject("SELECT id FROM personnel", Long.class);
        jdbc.update("INSERT INTO phone_device(id,personnel_id,phone_number) VALUES(1,?,'13800000001')", id);
        jdbc.update("INSERT INTO phone_device(id,personnel_id,phone_number) VALUES(2,?,'13800000002')", id);
        jdbc.update("INSERT INTO phone_device(id,personnel_id,phone_number) VALUES(3,?,'   ')", id);
        jdbc.update("UPDATE personnel SET phone='旧的过期镜像'");
        Map<String, Object> updated = excel.importAll(new ByteArrayInputStream(bytes));
        assertEquals(1, updated.get("updated"), updated.toString());
        assertEquals("13800000002", jdbc.queryForObject("SELECT phone FROM personnel", String.class));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel.export(java.util.Collections.singletonList(id))))) {
            assertEquals("13800000002", wb.getSheet("人员信息").getRow(1).getCell(4).getStringCellValue());
        }
        jdbc.update("DELETE FROM phone_device");
        excel.importAll(new ByteArrayInputStream(bytes));
        assertEquals("", jdbc.queryForObject("SELECT phone FROM personnel", String.class));
    }

    @AfterEach void clearScope() { DataScope.clear(); }

    /** 实际在岗三列入库列(与 SchemaInitializer 口径一致);schema 帮手未覆盖时补列,已存在则跳过。 */
    private void ensureActualColumns() {
        ensureColumn("actual_department_id", "actual_department_id INTEGER DEFAULT 0");
        ensureColumn("actual_post", "actual_post TEXT DEFAULT ''");
        ensureColumn("actual_post_date", "actual_post_date TEXT DEFAULT ''");
    }

    private void ensureColumn(String column, String ddl) {
        for (Map<String, Object> row : jdbc.queryForList("PRAGMA table_info(personnel)")) {
            if (column.equalsIgnoreCase(String.valueOf(row.get("name")))) return;
        }
        jdbc.execute("ALTER TABLE personnel ADD COLUMN " + ddl);
    }

    private void assertActualState(long departmentId, String post, String postDate) {
        assertEquals(departmentId, jdbc.queryForObject("SELECT actual_department_id FROM personnel", Long.class));
        assertEquals(post, jdbc.queryForObject("SELECT actual_post FROM personnel", String.class));
        assertEquals(postDate, jdbc.queryForObject("SELECT actual_post_date FROM personnel", String.class));
    }

    @Test void actualUnitRoundTripsWhileLegacyPostColumnsAreIgnoredAndMissingHeaderPreservesValue() throws Exception {
        ensureActualColumns();
        jdbc.update("INSERT INTO department(id,name,parent_id) VALUES(2,'一营',0),(3,'一连',2)");
        // 新模板只有"实际单位"一列:名称 trim 后全局唯一解析
        Map<String, Object> result = excel.importAll(new ByteArrayInputStream(actualWorkbook(" 一连 ")));
        assertEquals(0, result.get("failed"), result.toString());
        assertEquals(1, ((Number) result.get("added")).intValue() + ((Number) result.get("updated")).intValue());
        assertActualState(3L, "", ""); // 历史列仍在库,但新导入不再读写
        // 导出:实际单位写叶子名,不再有实际岗位/实际在岗开始时间列
        Long id = jdbc.queryForObject("SELECT id FROM personnel", Long.class);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel.export(java.util.Collections.singletonList(id))))) {
            Sheet main = wb.getSheet("人员信息");
            assertEquals("一连", cell(main.getRow(1), "实际单位").getStringCellValue());
            boolean legacyHeaderGone = true;
            for (org.apache.poi.ss.usermodel.Cell header : main.getRow(0)) {
                if ("实际岗位".equals(header.getStringCellValue()) || "实际在岗开始时间".equals(header.getStringCellValue())) legacyHeaderGone = false;
            }
            assertTrue(legacyHeaderGone, "导出不再包含实际岗位/实际在岗开始时间列");
        }
        // 旧导出文件仍带"实际岗位/实际在岗开始时间"列:未知列被忽略,一个字节都不写
        byte[] legacy;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(actualWorkbook("一连")))) {
            Sheet main = wb.getSheetAt(0);
            int extra = main.getRow(0).getLastCellNum();
            main.getRow(0).createCell(extra).setCellValue("实际岗位");
            main.getRow(0).createCell(extra + 1).setCellValue("实际在岗开始时间");
            main.getRow(1).createCell(extra).setCellValue("帮助工作");
            main.getRow(1).createCell(extra + 1).setCellValue("2024-03-05");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out); legacy = out.toByteArray();
        }
        Map<String, Object> ignored = excel.importAll(new ByteArrayInputStream(legacy));
        assertEquals(1, ignored.get("updated"), ignored.toString());
        assertEquals(0, ignored.get("failed"));
        assertActualState(3L, "", ""); // 未知列不写入:actual_post/actual_post_date 保持空串
        // 旧模板("实际单位"表头缺失):更新时保留旧值,行内残留值不生效
        byte[] headerMissing;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(actualWorkbook("幽灵单位")))) {
            Sheet main = wb.getSheetAt(0);
            main.getRow(0).removeCell(cell(main.getRow(0), "实际单位"));
            ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out); headerMissing = out.toByteArray();
        }
        Map<String, Object> kept = excel.importAll(new ByteArrayInputStream(headerMissing));
        assertEquals(1, kept.get("updated"), kept.toString());
        assertEquals(0, kept.get("failed"));
        assertActualState(3L, "", "");
        // 列存在但空 → 按空写入(清空)
        Map<String, Object> cleared = excel.importAll(new ByteArrayInputStream(changedWorkbook("机关", "11010519491231002X")));
        assertEquals(1, cleared.get("updated"), cleared.toString());
        assertEquals(0, cleared.get("failed"));
        assertActualState(0L, "", "");
    }

    @Test void unknownOrAmbiguousActualUnitFailsTheRowWithoutWritingAnything() throws Exception {
        ensureActualColumns();
        assertEquals(1, excel.importAll(new ByteArrayInputStream(changedWorkbook("机关", "11010519491231002X"))).get("added"));
        // 历史列仍在库:导入流程不得触碰(保留旧行为验证收缩为 actual_department_id 不变 + 历史列原样)
        jdbc.update("UPDATE personnel SET actual_department_id=1, actual_post='旧岗位', actual_post_date='2020-01-01'");
        Map<String, Object> unknown = excel.importAll(new ByteArrayInputStream(actualWorkbook("幽灵单位")));
        assertEquals(1, unknown.get("failed"), unknown.toString());
        assertEquals(0, unknown.get("updated"));
        assertTrue(String.valueOf(unknown.get("errors")).contains("实际单位不存在或不唯一"));
        assertActualState(1L, "旧岗位", "2020-01-01");
        // 同名多条 → 同样拒绝,且绝不自动创建单位
        jdbc.update("INSERT INTO department(id,name) VALUES(2,'重复连'),(3,'重复连')");
        Map<String, Object> ambiguous = excel.importAll(new ByteArrayInputStream(actualWorkbook("重复连")));
        assertEquals(1, ambiguous.get("failed"), ambiguous.toString());
        assertTrue(String.valueOf(ambiguous.get("errors")).contains("实际单位不存在或不唯一"));
        assertActualState(1L, "旧岗位", "2020-01-01");
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM department", Integer.class));
    }

    private byte[] actualWorkbook(String actualName) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(changedWorkbook("机关", "11010519491231002X")))) {
            Sheet main = wb.getSheetAt(0);
            set(main.getRow(1), main.getRow(0), "实际单位", actualName);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out); return out.toByteArray();
        }
    }

    private void restrict(java.util.Set<Long> departments) {
        DataScope.set(new DataScope.Context(10L, "sub", false, departments));
    }

    private byte[] changedWorkbook(String department, String card) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(workbookWithData()))) {
            Sheet main = wb.getSheetAt(0);
            set(main.getRow(1), main.getRow(0), "编制单位", department);
            set(main.getRow(1), main.getRow(0), "身份证号", card);
            main.removeRow(main.getRow(2));
            ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out); return out.toByteArray();
        }
    }

    @Test void scopedPersonnelUpdateChecksGlobalIdCardAndBothDepartments() throws Exception {
        excel.importAll(new ByteArrayInputStream(workbookWithData()));
        jdbc.update("INSERT INTO department(id,name) VALUES(2,'外域单位')");
        jdbc.update("UPDATE personnel SET department_id=2, name='外域旧人员'");
        restrict(java.util.Collections.singleton(1L));
        Map<String,Object> stolen = excel.importAll(new ByteArrayInputStream(changedWorkbook("机关", "11010519491231002X")));
        assertEquals(1, stolen.get("failed"), stolen.toString());
        assertEquals(0, stolen.get("added"));
        assertEquals(0, stolen.get("updated"));
        assertEquals("外域旧人员", jdbc.queryForObject("SELECT name FROM personnel", String.class));
        assertEquals(2L, jdbc.queryForObject("SELECT department_id FROM personnel", Long.class));
        jdbc.update("UPDATE personnel SET department_id=1");
        Map<String,Object> move = excel.importAll(new ByteArrayInputStream(changedWorkbook("外域单位", "11010519491231002X")));
        assertEquals(1, move.get("failed"), move.toString());
        assertEquals(1L, jdbc.queryForObject("SELECT department_id FROM personnel", Long.class));
        Map<String,Object> allowed = excel.importAll(new ByteArrayInputStream(changedWorkbook("机关", "11010519491231002X")));
        assertEquals(1, allowed.get("updated"), allowed.toString());
    }

    @Test void scopedNewPersonnelRejectsForeignUnknownAndAmbiguousDepartments() throws Exception {
        jdbc.update("INSERT INTO department(id,name) VALUES(2,'外域单位')");
        restrict(java.util.Collections.singleton(1L));
        for (String department : new String[]{"外域单位", "不存在的新顶级单位"}) {
            Map<String,Object> result = excel.importAll(new ByteArrayInputStream(changedWorkbook(department, "11010519491231002X")));
            assertEquals(1, result.get("failed"), result.toString());
            assertEquals(0, result.get("added"));
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM department", Integer.class));
        jdbc.update("INSERT INTO department(id,name) VALUES(3,'机关')");
        Map<String,Object> ambiguous = excel.importAll(new ByteArrayInputStream(changedWorkbook("机关", "11010519491231002X")));
        assertEquals(1, ambiguous.get("failed"), ambiguous.toString());
        assertTrue(ambiguous.get("errors").toString().contains("单位名称匹配多条"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Integer.class));
    }

    @Test void childSheetsOnlyAcceptSuccessfulVisiblePeopleAndRejectForgedIds() throws Exception {
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(20,'外域','110105199001011235',2)");
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id) VALUES(21,'未在主表成功','110105199001011236',1)");
        restrict(java.util.Collections.singleton(1L));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(changedWorkbook("机关", "11010519491231002X")))) {
            Sheet history = wb.getSheet("任职经历");
            history.getRow(0).createCell(7).setCellValue("人员ID");
            String[] cards = {"110105199001011235", "110105199001011236", "11010519491231002X"};
            for (int i = 0; i < cards.length; i++) {
                Row row = history.createRow(4 + i);
                row.createCell(0).setCellValue(cards[i]); row.createCell(2).setCellValue("2021-01-01");
                row.createCell(4).setCellValue("地点"); row.createCell(5).setCellValue("职务");
                if (i == 2) row.createCell(7).setCellValue("20");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out);
            Map<String,Object> result = excel.importAll(new ByteArrayInputStream(out.toByteArray()));
            assertEquals(1, result.get("added"), result.toString());
            assertEquals(1, result.get("subTotal"), result.toString());
            assertEquals(4, result.get("subFailed"), result.toString()); // 无效日期 + 外域身份证 + 未成功主表 + 伪造ID
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM position_history WHERE personnel_id IN (20,21)", Integer.class));
        }
    }

    @Test void rootCanStillCreateNamedDepartmentsAndImportTheirPersonnel() throws Exception {
        DataScope.set(new DataScope.Context(1L, "root", true, java.util.Collections.emptySet()));
        Map<String,Object> result = excel.importAll(new ByteArrayInputStream(changedWorkbook("根用户新建单位", "11010519491231002X")));
        assertEquals(1, result.get("added"), result.toString());
        assertEquals(0, result.get("failed"), result.toString());
        Long deptId = jdbc.queryForObject("SELECT id FROM department WHERE name='根用户新建单位'", Long.class);
        assertTrue(deptId > 0);
        assertEquals(deptId, jdbc.queryForObject("SELECT department_id FROM personnel", Long.class));
    }

    @Test void fullExportValidatesAllPeopleBeforeReadingChildrenAndPhotoNames() throws Exception {
        excel.importAll(new ByteArrayInputStream(workbookWithData()));
        jdbc.update("INSERT INTO personnel(id,name,id_card,department_id,photo) VALUES(20,'秘密姓名','110105199001011235',2,'secret.jpg')");
        restrict(java.util.Collections.singleton(1L));
        assertThrows(ForbiddenException.class, () -> excel.export(java.util.Arrays.asList(1L, 20L)));
        assertThrows(ForbiddenException.class, () -> excel.export(java.util.Arrays.asList(999L)));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel.export(java.util.Arrays.asList(1L))))) {
            assertEquals(2, wb.getSheet("人员信息").getPhysicalNumberOfRows());
            assertEquals(2, wb.getSheet("任职经历").getPhysicalNumberOfRows());
        }
        restrict(java.util.Collections.emptySet());
        assertThrows(ForbiddenException.class, () -> excel.export(java.util.Arrays.asList(1L)));
        Map<String,Object> empty = excel.importAll(new ByteArrayInputStream(changedWorkbook("机关", "11010519491231002X")));
        assertEquals(1, empty.get("failed"), empty.toString());
        assertEquals(0, empty.get("subTotal"));
        DataScope.set(new DataScope.Context(1L, "root", true, java.util.Collections.emptySet()));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel.export(java.util.Arrays.asList(1L,20L))))) {
            assertEquals(3, wb.getSheet("人员信息").getPhysicalNumberOfRows());
            assertEquals("secret.jpg", wb.getSheetAt(0).getRow(2).getCell(51).getStringCellValue());
        }
    }

    private byte[] workbookWithData() throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel.template()))) {
            Sheet main = wb.getSheet("人员信息");
            Row valid = main.createRow(1);
            set(valid, main.getRow(0), "姓名", "测试人员");
            set(valid, main.getRow(0), "性别", "男");
            set(valid, main.getRow(0), "身份证号", "11010519491231002X");
            set(valid, main.getRow(0), "军人证件号", "军证测试-001");
            set(valid, main.getRow(0), "出生日期", "1949-12-31");
            set(valid, main.getRow(0), "编制单位", "机关");
            set(valid, main.getRow(0), "所在点位", "机关");
            set(valid, main.getRow(0), "人员类别", "军官"); set(valid, main.getRow(0), "军衔/职级", "上尉");
            set(valid, main.getRow(0), "职务/岗位", "参谋"); set(valid, main.getRow(0), "任岗位时间", "2018-07-01");
            set(valid, main.getRow(0), "状态", "在职"); set(valid, main.getRow(0), "入伍/入职日期", "2010-06-01");
            set(valid, main.getRow(0), "相当入伍时间", "2010-06-01"); set(valid, main.getRow(0), "民族", "汉族");
            set(valid, main.getRow(0), "籍贯所在地", "浙江省/杭州市"); set(valid, main.getRow(0), "入伍所在地", "浙江省/杭州市");
            set(valid, main.getRow(0), "血型", "A"); set(valid, main.getRow(0), "政治面貌", "党员");
            set(valid, main.getRow(0), "婚姻状况", "未婚"); set(valid, main.getRow(0), "学历", "本科");
            set(valid, main.getRow(0), "是否独生子女", "否"); set(valid, main.getRow(0), "详细地址", "杭州");
            set(valid, main.getRow(0), "入学时间", "2020-09-01"); set(valid, main.getRow(0), "配偶姓名", "配偶甲");
            set(valid, main.getRow(0), "党团备注", "党团备注内容"); set(valid, main.getRow(0), "婚姻备注", "婚姻备注内容");
            set(valid, main.getRow(0), "备注", "人员备注内容"); set(valid, main.getRow(0), "照片文件名", "do-not-import.jpg");
            set(valid, main.getRow(0), "导出人员编号", "do-not-import-id");

            Row invalid = main.createRow(2);
            for (int c = 0; c < valid.getLastCellNum(); c++) {
                if (valid.getCell(c) != null) invalid.createCell(c).setCellValue(valid.getCell(c).getStringCellValue());
            }
            set(invalid, main.getRow(0), "姓名", "缺政治面貌");
            set(invalid, main.getRow(0), "身份证号", "11010519491231003X");
            set(invalid, main.getRow(0), "政治面貌", "");

            Sheet history = wb.getSheet("任职经历");
            Row good = history.createRow(1);
            good.createCell(0).setCellValue("11010519491231002X"); good.createCell(2).setCellValue("2021-01-01");
            good.createCell(4).setCellValue("机关"); good.createCell(5).setCellValue("排长");
            Row duplicate = history.createRow(2);
            duplicate.createCell(0).setCellValue("11010519491231002X"); duplicate.createCell(2).setCellValue("2021-01-01");
            duplicate.createCell(4).setCellValue("机关"); duplicate.createCell(5).setCellValue("排长");
            Row badDate = history.createRow(3);
            badDate.createCell(0).setCellValue("11010519491231002X"); badDate.createCell(2).setCellValue("2021-02-30");
            badDate.createCell(5).setCellValue("连长");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); wb.write(out); return out.toByteArray();
        }
    }

    private void addDict(String type, String label) {
        jdbc.update("INSERT INTO sys_dict(dict_type,dict_label,enabled) VALUES(?,?,1)", type, label);
    }

    private static void set(Row row, Row header, String label, String value) {
        for (int c = 0; c < header.getLastCellNum(); c++) {
            if (label.equals(header.getCell(c).getStringCellValue())) { row.createCell(c).setCellValue(value); return; }
        }
        fail("Missing header: " + label);
    }
}
