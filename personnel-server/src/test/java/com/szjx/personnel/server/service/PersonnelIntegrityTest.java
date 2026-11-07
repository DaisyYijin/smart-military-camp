package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.DepartmentDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.Personnel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** No Spring application startup, SchemaInitializer, timers or real data paths. */
public class PersonnelIntegrityTest {
    @TempDir Path temp;
    JdbcTemplate jdbc;
    PersonnelDao dao;
    PersonnelService service;
    PhotoService photos;

    public static JdbcTemplate temporaryDatabase(Path directory) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + directory.resolve("isolated.sqlite").toAbsolutePath());
        return new JdbcTemplate(ds);
    }

    public static void personnelSchema(JdbcTemplate jdbc) {
        BeanWrapper bean = new BeanWrapperImpl(new Personnel());
        StringBuilder ddl = new StringBuilder("CREATE TABLE personnel(id INTEGER PRIMARY KEY AUTOINCREMENT");
        for (java.beans.PropertyDescriptor property : bean.getPropertyDescriptors()) {
            String name = property.getName();
            if (property.getWriteMethod() == null || "id".equals(name) || "photoBase64".equals(name) || "departmentName".equals(name) || "departmentPath".equals(name) || "actualDepartmentName".equals(name) || "actualDepartmentPath".equals(name)) continue;
            String column = name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
            ddl.append(',').append(column).append(property.getPropertyType() == Long.class ? " INTEGER DEFAULT 0" : property.getPropertyType() == Double.class ? " REAL" : " TEXT DEFAULT ''");
        }
        jdbc.execute(ddl + ", auto_leave INTEGER DEFAULT 0, auto_leave_previous_status TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY, name TEXT, parent_id INTEGER, sort INTEGER, remark TEXT, leader_id INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE transfer_record(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER NOT NULL, type TEXT NOT NULL, " +
                "from_department_id INTEGER, from_department_name TEXT DEFAULT '', to_department_id INTEGER, to_department_name TEXT DEFAULT '', " +
                "change_date TEXT NOT NULL, remark TEXT DEFAULT '', created_at TEXT DEFAULT '')");
        jdbc.update("INSERT INTO department(id, name) VALUES(1, '机关')");
        jdbc.execute("CREATE TABLE phone_device(id INTEGER PRIMARY KEY, personnel_id INTEGER, phone_number TEXT)");
        jdbc.execute("CREATE TABLE delete_undo(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, name TEXT, summary TEXT DEFAULT '', payload TEXT, photo_trash TEXT DEFAULT '', created_at TEXT DEFAULT (datetime('now','localtime')))");
    }

    /** 借还完整列schema,仅显式需要的新测试使用;旧隔离schema继续不建此表。 */
    public static void itemLoanSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE item_loan(id INTEGER PRIMARY KEY AUTOINCREMENT, item_name TEXT NOT NULL, " +
                "specification TEXT DEFAULT '', quantity REAL NOT NULL DEFAULT 1, unit TEXT DEFAULT '', " +
                "personnel_id INTEGER NOT NULL DEFAULT 0, borrower_name TEXT NOT NULL DEFAULT '', " +
                "borrower_id_card TEXT NOT NULL DEFAULT '', borrower_phone TEXT NOT NULL DEFAULT '', borrower_unit TEXT NOT NULL DEFAULT '', " +
                "department_id INTEGER NOT NULL DEFAULT 0, owner_user_id INTEGER NOT NULL DEFAULT 0, " +
                "handler_id INTEGER NOT NULL DEFAULT 0, handler_name TEXT NOT NULL DEFAULT '', " +
                "handler_id_card TEXT NOT NULL DEFAULT '', " +
                "borrower_migrated INTEGER NOT NULL DEFAULT 0, returned INTEGER NOT NULL DEFAULT 0, registration_department TEXT NOT NULL DEFAULT '', borrow_date TEXT NOT NULL, " +
                "expected_return_date TEXT DEFAULT '', actual_return_date TEXT DEFAULT '', return_condition TEXT DEFAULT '', " +
                "return_remark TEXT DEFAULT '', remark TEXT DEFAULT '', created_at TEXT DEFAULT '', updated_at TEXT DEFAULT '')");
    }

    @SuppressWarnings("unchecked")
    public static <T> T transactional(T target, JdbcTemplate jdbc) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(jdbc.getDataSource()), new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    @BeforeEach void setup() {
        jdbc = temporaryDatabase(temp);
        personnelSchema(jdbc);
        dao = new PersonnelDao(jdbc);
        photos = mock(PhotoService.class);
        service = transactional(new PersonnelService(dao, photos, new DepartmentDao(jdbc), jdbc), jdbc);
        for (String table : new String[]{"leave_record", "leave_plan", "fitness_record", "military_assessment", "position_history", "reward_punish", "child_info", "family_member", "driver_license", "personal_card", "private_vehicle", "housing_info"}) {
            jdbc.execute("CREATE TABLE " + table + "(id INTEGER PRIMARY KEY, personnel_id INTEGER)");
        }
        jdbc.execute("CREATE TABLE equipment(id INTEGER PRIMARY KEY, keeper_id INTEGER)");
        jdbc.execute("CREATE TABLE material(id INTEGER PRIMARY KEY, keeper_id INTEGER)");
        jdbc.execute("CREATE TABLE vehicle(id INTEGER PRIMARY KEY, driver_id INTEGER)");
        DoubleExcellenceServiceTest.createSchema(jdbc);
    }

    /** 人员表单必填项已扩充,统一填齐,各用例只聚焦自己的校验点 */
    Personnel person(String card) {
        Personnel p = new Personnel();
        p.setName("测试人员");
        p.setIdCard(card);
        p.setMilitaryIdNo("军证测试-001");
        p.setGender("男");
        p.setBirthday("1990-01-01");
        p.setDepartmentId(1L);
        p.setLocationPoint("机关");
        p.setCategory("军官");
        p.setRank("上尉");
        p.setPost("参谋");
        p.setEntryDate("2010-06-01");
        p.setEquivEntryDate("2010-06-01");
        p.setNation("汉族");
        p.setNativePlace("浙江省/杭州市/上城区");
        p.setEnlistPlace("浙江省/杭州市/上城区");
        p.setBloodType("A");
        p.setOnlyChild("否");
        p.setAddress("杭州市XX路XX号");
        p.setStatus("在职");
        return p;
    }

    @Test void batchUpdateAppliesWhitelistedFieldsAndRejectsOthers() {
        Personnel a = person("110105199001010011");
        Personnel b = person("110105199001010022");
        long ia = dao.insert(a), ib = dao.insert(b);
        java.util.Map<String, Object> patch = new java.util.LinkedHashMap<String, Object>();
        patch.put("status", "休假");
        patch.put("category", "军士");
        java.util.Map<String, Object> r = service.batchUpdate(java.util.Arrays.asList(ia, ib), patch);
        assertEquals(2, ((Number) r.get("ok")).intValue(), "两条都应成功");
        assertEquals(0, ((Number) r.get("fail")).intValue());
        assertEquals("休假", jdbc.queryForObject("SELECT status FROM personnel WHERE id=?", String.class, ia));
        assertEquals("军士", jdbc.queryForObject("SELECT category FROM personnel WHERE id=?", String.class, ib));
        // 非白名单字段(姓名)整体拒绝,不做任何修改
        java.util.Map<String, Object> bad = new java.util.LinkedHashMap<String, Object>();
        bad.put("name", "新名字");
        try {
            service.batchUpdate(java.util.Arrays.asList(ia), bad);
            org.junit.jupiter.api.Assertions.fail("应拒绝非白名单字段");
        } catch (IllegalArgumentException expected) {
            assertEquals("110105199001010011", jdbc.queryForObject("SELECT id_card FROM personnel WHERE id=?", String.class, ia));
        }
    }

    @Test void partialPhotoUpdatePreservesEveryModelPropertyAndEmptyStringsClear() {
        Personnel old = person("11010519491231002x");
        BeanWrapper bean = new BeanWrapperImpl(old);
        for (java.beans.PropertyDescriptor property : bean.getPropertyDescriptors()) {
            if (property.getWriteMethod() != null && property.getPropertyType() == String.class && !"photoBase64".equals(property.getName()) && !"idCard".equals(property.getName())) bean.setPropertyValue(property.getName(), "保留值");
        }
        old.setPhoto("old.jpg"); old.setHeight(180d); old.setWeight(75d);
        long id = dao.insert(old);
        jdbc.update("INSERT INTO phone_device VALUES(1,?, '13800138000')", id);
        Personnel partial = new Personnel(); partial.setPhotoBase64("/9j/"); partial.setMarriageRemark("");
        Personnel updated = service.update(id, partial);
        BeanWrapper after = new BeanWrapperImpl(updated);
        for (java.beans.PropertyDescriptor property : bean.getPropertyDescriptors()) {
            String n = property.getName();
            if (property.getWriteMethod() == null || java.util.Arrays.asList("id", "photo", "photoBase64", "phone", "idCard", "createdAt", "updatedAt", "departmentName", "departmentPath", "actualDepartmentName", "actualDepartmentPath", "marriageRemark", "departmentId", "actualDepartmentId").contains(n)) continue;
            assertEquals(bean.getPropertyValue(n), after.getPropertyValue(n), n);
        }
        // 单位为必填,保留旧值;部分更新不得清空
        assertEquals(1L, updated.getDepartmentId());
        assertEquals("", updated.getMarriageRemark());
        assertEquals("13800138000", updated.getPhone());
        verify(photos).save(anyString(), any(byte[].class));
        verify(photos).delete("old.jpg");
    }

    @Test void militaryIdentityIsRequiredTrimmedIndependentAndNotUnique() {
        for (String value : new String[]{null, "", " " + (char) 9 + (char) 13 + (char) 10}) {
            Personnel invalid = person("11010519491231002X"); invalid.setMilitaryIdNo(value);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.create(invalid));
            assertTrue(error.getMessage().contains("军人证件号"));
        }
        assertEquals(0, dao.countAll());
        Personnel p = person("11010519491231002X"); p.setMilitaryIdNo("  001-军证/自定义文本  ");
        Personnel saved = service.create(p);
        assertEquals("001-军证/自定义文本", saved.getMilitaryIdNo());
        assertEquals(saved.getMilitaryIdNo(), dao.findById(saved.getId()).getMilitaryIdNo());
        assertEquals("11010519491231002X", saved.getIdCard());
        Personnel duplicateMilitary = person("110105491231002"); duplicateMilitary.setMilitaryIdNo(saved.getMilitaryIdNo());
        assertEquals(saved.getMilitaryIdNo(), service.create(duplicateMilitary).getMilitaryIdNo());
        Personnel partial = new Personnel(); partial.setRemark("仅改备注");
        assertEquals(saved.getMilitaryIdNo(), service.update(saved.getId(), partial).getMilitaryIdNo());
        for (String value : new String[]{"", " 	 "}) {
            Personnel clear = new Personnel(); clear.setMilitaryIdNo(value);
            assertThrows(IllegalArgumentException.class, () -> service.update(saved.getId(), clear));
            assertEquals(saved.getMilitaryIdNo(), dao.findById(saved.getId()).getMilitaryIdNo());
        }
        Personnel change = new Personnel(); change.setMilitaryIdNo("  000007  ");
        assertEquals("000007", service.update(saved.getId(), change).getMilitaryIdNo());
    }

    @Test void legacyEmptyMilitaryIdentityCanBeReadAndUndoneButMustBeCompletedBeforeEdit() throws Exception {
        Personnel original = person("11010519491231002X"); original.setMilitaryIdNo("");
        long id = dao.insert(original); // Legacy/restore SQL deliberately has no nonempty CHECK.
        assertEquals("", service.findById(id).getMilitaryIdNo());
        assertThrows(IllegalArgumentException.class, () -> service.update(id, new Personnel()));
        service.delete(id);
        long undoId = ((Number) service.undoList().get(0).get("id")).longValue();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String,Object> payload = mapper.readValue(jdbc.queryForObject("SELECT payload FROM delete_undo WHERE id=?", String.class, undoId), Map.class);
        Map<?,?> person = (Map<?,?>) payload.get("person");
        assertFalse(person.containsKey("department_path"));
        person.remove("military_id_no"); // Snapshot from before the column existed.
        jdbc.update("UPDATE delete_undo SET payload=? WHERE id=?", mapper.writeValueAsString(payload), undoId);
        service.undoDelete(undoId);
        assertEquals("", service.findById(id).getMilitaryIdNo());
        assertThrows(IllegalArgumentException.class, () -> service.update(id, new Personnel()));
        Personnel complete = new Personnel(); complete.setMilitaryIdNo("军证补录-001");
        assertEquals("军证补录-001", service.update(id, complete).getMilitaryIdNo());
    }

    @Test void departmentPathsUseAllAncestorsAndIdsWithoutPersistingOrChangingLeafNames() throws Exception {
        jdbc.update("UPDATE department SET name='总队',parent_id=0 WHERE id=1");
        jdbc.update("INSERT INTO department(id,name,parent_id) VALUES(2,'一营',1),(3,'一连',2),(4,'一班',3),(5,'二营',1),(6,'一班',5)");
        Personnel one = person("11010519491231002X"); one.setDepartmentId(4L);
        Personnel two = person("110105491231002"); two.setDepartmentId(6L);
        long first = dao.insert(one), second = dao.insert(two);
        assertEquals("一班", dao.findById(first).getDepartmentName());
        assertEquals("总队一营一连一班", dao.findById(first).getDepartmentPath());
        assertEquals("总队二营一班", dao.findById(second).getDepartmentPath());
        assertEquals("总队一营一连一班", dao.findByIdCard(one.getIdCard()).getDepartmentPath());
        assertEquals("总队一营一连一班", dao.findAllSimple().get(0).get("department_path"));
        assertEquals("总队二营一班", service.findAllOptions().get(1).get("departmentPath"));
        assertFalse(jdbc.queryForMap("SELECT * FROM personnel WHERE id=?", first).containsKey("department_path"));
        assertFalse(jdbc.queryForMap("SELECT * FROM personnel WHERE id=?", first).containsKey("actual_department_path"));
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Personnel request = mapper.readValue("{\"militaryIdNo\":\"新军证\",\"departmentPath\":\"伪造路径\",\"actualDepartmentPath\":\"伪造实际路径\",\"actualDepartmentName\":\"伪造实际单位\"}", Personnel.class);
        assertNull(request.getDepartmentPath());
        assertNull(request.getActualDepartmentPath());
        assertNull(request.getActualDepartmentName());
        assertEquals("新军证", request.getMilitaryIdNo());
        assertTrue(mapper.writeValueAsString(dao.findById(first)).contains("总队一营一连一班"));
    }

    @Test void personnelBatchPathsLoadOneSharedHierarchyRatherThanOneQueryPerPerson() {
        for (int i = 0; i < 25; i++) dao.insert(person("legacy-" + i));
        JdbcTemplate tracked = spy(jdbc);
        PersonnelDao trackedDao = new PersonnelDao(tracked);
        List<Personnel> rows = trackedDao.query(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, 1, 100);
        assertEquals(25, rows.size());
        for (Personnel row : rows) assertEquals("机关", row.getDepartmentPath());
        long treeReads = mockingDetails(tracked).getInvocations().stream()
                .filter(call -> "query".equals(call.getMethod().getName()) && call.getArguments().length == 2
                        && call.getArguments()[1] instanceof org.springframework.jdbc.core.RowMapper)
                .map(call -> ((String) call.getArguments()[0]).toLowerCase(java.util.Locale.ROOT))
                .filter(sql -> sql.contains("from department")).count();
        assertEquals(1L, treeReads);
    }

    @Test void identityNormalizationChecksumDuplicateAndBlankCompatibility() {
        Personnel p = service.create(person(" 11010519491231002x "));
        assertEquals("11010519491231002X", p.getIdCard());
        assertThrows(IllegalArgumentException.class, () -> service.create(person("11010519491231002X")));
        assertThrows(IllegalArgumentException.class, () -> service.create(person("110105194912310021")));
        assertThrows(IllegalArgumentException.class, () -> service.create(person("110105199902300021")));
        // 身份证号已改为必填,空白不再放行
        assertThrows(IllegalArgumentException.class, () -> service.create(person("")));
        assertThrows(IllegalArgumentException.class, () -> service.create(person("   ")));
        assertDoesNotThrow(() -> service.create(person("110105491231002")));
        jdbc.update("UPDATE personnel SET id_card='legacy-invalid', department_id=99 WHERE id=?", p.getId());
        Personnel partial = new Personnel(); partial.setRemark("兼容旧记录");
        assertEquals("LEGACY-INVALID", service.update(p.getId(), partial).getIdCard());
    }

    @Test void departmentReferenceAndPhoneSource() {
        Personnel bad = person("11010519491231002X"); bad.setDepartmentId(5L);
        assertThrows(IllegalArgumentException.class, () -> service.create(bad));
        jdbc.update("INSERT INTO department(id,name) VALUES(5,'单位')");
        bad.setPhone("不可直接写入");
        Personnel saved = service.create(bad);
        assertEquals(5L, saved.getDepartmentId()); assertEquals("", saved.getPhone());
        Personnel change = new Personnel(); change.setPhone("伪造号码");
        assertEquals("", service.update(saved.getId(), change).getPhone());
    }

    @Test void normalizedDuplicateCheckExcludesSelfAndFindsLegacyCase() {
        long id = dao.insert(person(" 11010519491231002x "));
        assertTrue(dao.existsOtherIdCard("11010519491231002X", null));
        assertFalse(dao.existsOtherIdCard("11010519491231002X", id));
        assertEquals(id, dao.findByIdCard("11010519491231002X").getId());
        assertFalse(dao.existsOtherIdCard("", null));
    }

    @Test void deleteCascadesPhoneLeaveFitnessAndUndoRestoresEverything() {
        long id = dao.insert(person(""));
        Personnel saved = dao.findById(id);
        saved.setStatus("休假"); saved.setPhone("13900000000");
        jdbc.update("UPDATE personnel SET status=?, phone=? WHERE id=?", "休假", "13900000000", id);
        jdbc.update("INSERT INTO phone_device(id,personnel_id,phone_number) VALUES(1,?,'13911112222')", id);
        jdbc.update("INSERT INTO phone_device(id,personnel_id,phone_number) VALUES(2,?,'13900000000')", id);
        jdbc.update("INSERT INTO leave_record(id,personnel_id) VALUES(1,?)", id);
        jdbc.update("INSERT INTO leave_plan(id,personnel_id) VALUES(1,?)", id);
        jdbc.update("INSERT INTO fitness_record(id,personnel_id) VALUES(1,?)", id);
        // 双争与年度考评同为人员级联记录:删除进快照,撤回整表恢复(双争表由 setup 的 createSchema 创建,年度表在此补建)
        jdbc.execute("CREATE TABLE annual_appraisal_record(id INTEGER PRIMARY KEY AUTOINCREMENT,personnel_id INTEGER NOT NULL,year INTEGER NOT NULL,rating TEXT NOT NULL,rating_date TEXT NOT NULL)");
        jdbc.update("INSERT INTO double_excellence_record(id,personnel_id,year,quarter,rating,rating_date) VALUES(1,?,2026,1,'好','2026-10-22')", id);
        jdbc.update("INSERT INTO annual_appraisal_record(id,personnel_id,year,rating,rating_date) VALUES(1,?,2026,'优秀','2026-10-22')", id);
        jdbc.update("INSERT INTO department(id,name,leader_id) VALUES(7,'单位',?)", id);
        jdbc.update("INSERT INTO equipment(id,keeper_id) VALUES(9,?)", id);
        jdbc.update("INSERT INTO position_history(id,personnel_id) VALUES(3,?)", id);
        service.delete(id);
        assertNull(dao.findById(id));
        for (String table : new String[]{"phone_device", "leave_record", "leave_plan", "fitness_record", "position_history", "double_excellence_record", "annual_appraisal_record"}) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class), table);
        }
        assertEquals(0L, jdbc.queryForObject("SELECT leader_id FROM department WHERE id=7", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT keeper_id FROM equipment WHERE id=9", Long.class));
        List<Map<String, Object>> undoList = service.undoList();
        assertEquals(1, undoList.size());
        Map<String, Object> restored = service.undoDelete(((Number) undoList.get(0).get("id")).longValue());
        Personnel back = (Personnel) restored.get("person");
        assertEquals(id, back.getId());
        assertEquals("休假", back.getStatus());
        assertEquals("13900000000", back.getPhone()); // 镜像按恢复设备的最新号码重算
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM phone_device", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM leave_record", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM leave_plan", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fitness_record", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM position_history", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM double_excellence_record", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM annual_appraisal_record", Integer.class));
        assertEquals(id, jdbc.queryForObject("SELECT leader_id FROM department WHERE id=7", Long.class));
        assertEquals(id, jdbc.queryForObject("SELECT keeper_id FROM equipment WHERE id=9", Long.class));
        assertTrue(service.undoList().isEmpty());
    }

    @Test void personnelTransferDeleteAndUndoNeverModifyIndependentLoans() throws Exception {
        itemLoanSchema(jdbc);
        long id = dao.insert(person("11010519491231002X"));
        jdbc.update("INSERT INTO item_loan(id,item_name,borrow_date,borrower_name,borrower_id_card,borrower_phone," +
                "borrower_unit,department_id,borrower_migrated,created_at,updated_at) " +
                "VALUES(10,'对讲机','2026-10-01','手填姓名','手填证件','手填电话','外部单位',1,1,'原创建','原更新')");
        jdbc.update("INSERT INTO item_loan(id,item_name,borrow_date,actual_return_date,department_id,borrower_migrated) " +
                "VALUES(11,'已归还工具','2026-09-01','2026-09-09',1,1)");
        jdbc.update("UPDATE item_loan SET handler_id=?,handler_name='原经手快照',handler_id_card='原经手证件' WHERE id=10", id);
        jdbc.update("UPDATE item_loan SET handler_id=999,handler_name='已不存在人员',handler_id_card='已不存在证件' WHERE id=11");
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM item_loan ORDER BY id");
        jdbc.update("INSERT INTO department(id,name) VALUES(2,'新单位')");
        Personnel transfer = new Personnel(); transfer.setDepartmentId(2L); transfer.setName("经手人员已改名");
        service.update(id, transfer);
        assertEquals(before, jdbc.queryForList("SELECT * FROM item_loan ORDER BY id"));
        service.delete(id);
        assertNull(dao.findById(id));
        assertEquals(before, jdbc.queryForList("SELECT * FROM item_loan ORDER BY id"));
        Map<String, Object> undo = service.undoList().get(0);
        assertEquals("无关联记录", undo.get("summary"));
        String payload = jdbc.queryForObject("SELECT payload FROM delete_undo WHERE id=?", String.class, undo.get("id"));
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload).get("cascades").has("item_loan"));
        service.undoDelete(((Number) undo.get("id")).longValue());
        assertEquals(before, jdbc.queryForList("SELECT * FROM item_loan ORDER BY id"));
    }

    @Test void legacyUndoConvertsLoansAfterRestoringPersonnelAndPreservesAllBusinessFields() throws Exception {
        itemLoanSchema(jdbc);
        Personnel original = person("旧证件"); original.setName("原借用人"); original.setPhone("原电话");
        long id = dao.insert(original);
        jdbc.update("INSERT INTO item_loan(id,item_name,specification,quantity,unit,personnel_id,borrow_date," +
                "expected_return_date,actual_return_date,return_condition,return_remark,remark,created_at,updated_at) " +
                "VALUES(10,'工具箱','大号',2,'套',?,'2026-09-01','2026-09-10','2026-09-09','完好','验收','备注','创建','更新')", id);
        Map<String, Object> legacy = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=10");
        for (String column : new String[]{"borrower_name","borrower_id_card","borrower_phone","borrower_unit","department_id","owner_user_id","handler_id","handler_name","handler_id_card","borrower_migrated","registration_department","returned"}) legacy.remove(column);
        jdbc.update("DELETE FROM item_loan");
        long undoId = legacyUndo(id, java.util.Collections.singletonList(legacy));
        service.undoDelete(undoId);
        Map<String, Object> restored = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=10");
        for (Map.Entry<String,Object> entry : legacy.entrySet()) {
            if (!"personnel_id".equals(entry.getKey())) assertEquals(entry.getValue(), restored.get(entry.getKey()), entry.getKey());
        }
        assertEquals("原借用人", restored.get("borrower_name"));
        assertEquals("旧证件", restored.get("borrower_id_card"));
        assertEquals("原电话", restored.get("borrower_phone"));
        assertEquals("机关", restored.get("borrower_unit"));
        assertEquals("机关", restored.get("registration_department"));
        assertEquals(1L, ((Number) restored.get("department_id")).longValue());
        assertEquals(0L, ((Number) restored.get("personnel_id")).longValue());
        assertEquals(1, ((Number) restored.get("borrower_migrated")).intValue());
        assertEquals(1, ((Number) restored.get("returned")).intValue());
        assertEquals(0L, ((Number) restored.get("owner_user_id")).longValue());
        assertEquals(0L, ((Number) restored.get("handler_id")).longValue());
        assertEquals("", restored.get("handler_name"));
        assertEquals("", restored.get("handler_id_card"));
    }

    @Test void legacyUndoReturnStatesRespectExplicitFlagsAndRetainDateAndBusinessValues() throws Exception {
        itemLoanSchema(jdbc);
        long id = dao.insert(person("旧证件"));
        List<Map<String,Object>> loans = new java.util.ArrayList<>();
        List<Integer> expectedStates = new java.util.ArrayList<>();
        for (int marker : new int[]{0, 1}) {
            for (Object[] values : new Object[][]{
                    {"", null, 0}, {"2024-03-01", null, 1}, {"2024-03-01", 0, 0}, {"", 1, 1}, {null, null, 0}}) {
                Map<String,Object> loan = new java.util.LinkedHashMap<>();
                loan.put("id", loans.size()+10); loan.put("item_name", "旧撤回记录");
                loan.put("personnel_id", marker == 0 ? id : 0L); loan.put("borrower_migrated", marker);
                loan.put("borrower_name", "手填姓名"); loan.put("borrower_unit", "手填单位");
                loan.put("borrow_date", "2024-02-29"); loan.put("expected_return_date", "2024-03-02");
                loan.put("actual_return_date", values[0]);
                if (values[1] != null) loan.put("returned", values[1]);
                loan.put("return_condition", "完好"); loan.put("return_remark", "验收");
                loan.put("remark", "原备注"); loan.put("created_at", "创建"); loan.put("updated_at", "更新");
                loans.add(loan); expectedStates.add((Integer)values[2]);
            }
        }
        long undoId = legacyUndo(id, loans);
        service.undoDelete(undoId);
        assertEquals(expectedStates,jdbc.queryForList("SELECT returned FROM item_loan ORDER BY id",Integer.class));
        for (Map<String,Object> loan : loans) {
            Map<String,Object> restored = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=?",loan.get("id"));
            for (String field : new String[]{"borrow_date","expected_return_date","actual_return_date","return_condition",
                    "return_remark","remark","created_at","updated_at"}) {
                assertEquals(loan.get(field),restored.get(field),field + " for id=" + loan.get("id"));
            }
            assertEquals(1,((Number)restored.get("borrower_migrated")).intValue());
            assertEquals(0L,((Number)restored.get("personnel_id")).longValue());
            if (Integer.valueOf(1).equals(loan.get("borrower_migrated"))) {
                assertEquals("手填姓名",restored.get("borrower_name"));
                assertEquals("手填单位",restored.get("borrower_unit"));
            }
        }
        assertNotNull(dao.findById(id));
        assertTrue(service.undoList().isEmpty());
        List<Map<String,Object>> restored = jdbc.queryForList("SELECT * FROM item_loan ORDER BY id");
        assertThrows(IllegalArgumentException.class, () -> service.undoDelete(undoId));
        assertEquals(restored,jdbc.queryForList("SELECT * FROM item_loan ORDER BY id"));
    }

    @Test void legacyUndoPreservesExplicitHandlerSnapshotsWithoutResolvingPersonnel() throws Exception {
        itemLoanSchema(jdbc);
        long id = dao.insert(person("旧证件"));
        List<Map<String,Object>> loans = new java.util.ArrayList<>();
        for (int marker : new int[]{0, 1}) {
            for (Object[] handler : new Object[][]{{0L, "", ""}, {999L, "人员已不存在的原经手人", "原经手证件"}, {id, "不同于当前人员的快照名", "不同于当前人员的证件"}}) {
                Map<String,Object> loan = new java.util.LinkedHashMap<>();
                loan.put("id", loans.size()+10); loan.put("item_name", "经手人旧撤回快照");
                loan.put("personnel_id", marker == 0 ? id : 0L); loan.put("borrower_migrated", marker);
                loan.put("handler_id", handler[0]); loan.put("handler_name", handler[1]); loan.put("handler_id_card", handler[2]);
                loan.put("borrow_date", "2024-02-29"); loan.put("actual_return_date", "2024-03-01");
                loan.put("returned", 0); loan.put("created_at", "创建"); loan.put("updated_at", "更新");
                loans.add(loan);
            }
        }
        service.undoDelete(legacyUndo(id, loans));
        for (Map<String,Object> loan : loans) {
            Map<String,Object> restored = jdbc.queryForMap("SELECT * FROM item_loan WHERE id=?",loan.get("id"));
            for (String field : new String[]{"handler_name","handler_id_card","borrow_date","actual_return_date","returned","created_at","updated_at"}) {
                assertEquals(loan.get(field), restored.get(field), field);
            }
            assertEquals(((Number)loan.get("handler_id")).longValue(), ((Number)restored.get("handler_id")).longValue());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE id=999", Integer.class));
    }

    @Test void legacyUndoWithoutLiveDepartmentRestoresLoanAdminOnly() throws Exception {
        itemLoanSchema(jdbc);
        long id = dao.insert(person(""));
        Map<String,Object> legacy = new java.util.LinkedHashMap<>();
        legacy.put("id", 10); legacy.put("item_name", "旧借还"); legacy.put("personnel_id", id); legacy.put("borrow_date", "2026-10-01");
        long undoId = legacyUndo(id, java.util.Collections.singletonList(legacy));
        jdbc.update("DELETE FROM department WHERE id=1");
        service.undoDelete(undoId);
        assertEquals(0L, jdbc.queryForObject("SELECT department_id FROM item_loan WHERE id=10", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT owner_user_id FROM item_loan WHERE id=10", Long.class));
        assertEquals(1, jdbc.queryForObject("SELECT borrower_migrated FROM item_loan WHERE id=10", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT returned FROM item_loan WHERE id=10", Integer.class));
        assertEquals("", jdbc.queryForObject("SELECT expected_return_date FROM item_loan WHERE id=10", String.class));
        assertEquals("", jdbc.queryForObject("SELECT actual_return_date FROM item_loan WHERE id=10", String.class));
    }

    @Test void legacyUndoLoanConflictRollsBackRestoredPersonnelAndKeepsUndo() throws Exception {
        itemLoanSchema(jdbc);
        long id = dao.insert(person(""));
        Map<String,Object> legacy = new java.util.LinkedHashMap<>();
        legacy.put("id", 10); legacy.put("item_name", "旧借还"); legacy.put("personnel_id", id); legacy.put("borrow_date", "2026-10-01");
        long undoId = legacyUndo(id, java.util.Collections.singletonList(legacy));
        jdbc.update("INSERT INTO item_loan(id,item_name,borrow_date,department_id,borrower_migrated) VALUES(10,'新借还','2026-10-02',1,1)");
        assertThrows(RuntimeException.class, () -> service.undoDelete(undoId));
        assertNull(dao.findById(id));
        assertEquals("新借还", jdbc.queryForObject("SELECT item_name FROM item_loan WHERE id=10", String.class));
        assertEquals(undoId, ((Number) service.undoList().get(0).get("id")).longValue());
    }

    @SuppressWarnings("unchecked")
    private long legacyUndo(long personId, List<Map<String,Object>> loans) throws Exception {
        service.delete(personId);
        long undoId = ((Number) service.undoList().get(0).get("id")).longValue();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String,Object> payload = mapper.readValue(jdbc.queryForObject("SELECT payload FROM delete_undo WHERE id=?", String.class, undoId), Map.class);
        ((Map<String,Object>) payload.get("cascades")).put("item_loan", loans);
        jdbc.update("UPDATE delete_undo SET payload=? WHERE id=?", mapper.writeValueAsString(payload), undoId);
        return undoId;
    }

    @Test void departmentDeletionIgnoresLoanRowsButKeepsOtherDepartmentRules() {
        itemLoanSchema(jdbc);
        jdbc.execute("CREATE TABLE account_department(user_id INTEGER,department_id INTEGER)");
        DepartmentService departments = transactional(new DepartmentService(new DepartmentDao(jdbc), dao), jdbc);
        jdbc.update("INSERT INTO department(id,name,parent_id) VALUES(2,'子单位',1)");
        jdbc.update("INSERT INTO item_loan(id,item_name,borrow_date,department_id,borrower_migrated) VALUES(10,'借出','2026-10-01',2,1)");
        assertThrows(IllegalArgumentException.class, () -> departments.delete(1L)); // child still blocks
        assertDoesNotThrow(() -> departments.delete(2L)); // loans are independent historical records
        assertEquals(2L, jdbc.queryForObject("SELECT department_id FROM item_loan WHERE id=10", Long.class));
    }

    @Test void departmentDeletionDoesNotInspectLoanSchema() {
        jdbc.execute("CREATE TABLE account_department(user_id INTEGER,department_id INTEGER)");
        DepartmentService departments = transactional(new DepartmentService(new DepartmentDao(jdbc), dao), jdbc);
        assertDoesNotThrow(() -> departments.delete(1L));
        jdbc.update("INSERT INTO department(id,name) VALUES(2,'旧隔离单位')");
        jdbc.execute("CREATE TABLE item_loan(id INTEGER PRIMARY KEY)");
        assertDoesNotThrow(() -> departments.delete(2L));
        assertNull(new DepartmentDao(jdbc).findById(2L));
    }

    @Test void legacyIsolatedSchemaWithoutItemLoanStillSupportsDeleteAndUndo() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='item_loan'", Integer.class));
        long id = dao.insert(person(""));
        service.delete(id);
        long undoId = ((Number) service.undoList().get(0).get("id")).longValue();
        assertDoesNotThrow(() -> service.undoDelete(undoId));
        assertNotNull(dao.findById(id));
        assertTrue(service.undoList().isEmpty());
    }

    @Test void personnelDeleteAndUndoRetainEveryDoubleExcellenceFieldAndOriginalIds() {
        long personId = dao.insert(person("旧证件"));
        jdbc.update("INSERT INTO double_excellence_record(id,personnel_id,year,quarter,rating,rating_date,created_at,updated_at) VALUES(88,?,2026,1,'好','2026-10-18','创建时间','修改时间')",personId);
        List<Map<String,Object>> original = jdbc.queryForList("SELECT * FROM double_excellence_record");
        service.delete(personId);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM double_excellence_record",Integer.class));
        Map<String,Object> undo = service.undoList().get(0);
        assertTrue(String.valueOf(undo.get("summary")).contains("双争评比1条"));
        service.undoDelete(((Number)undo.get("id")).longValue());
        assertEquals(original,jdbc.queryForList("SELECT * FROM double_excellence_record"));
        assertEquals("旧证件",new DoubleExcellenceService(jdbc).get(88L).getIdCard());
    }

    @Test void oldUndoSnapshotsWithoutDoubleExcellenceRemainCompatible() throws Exception {
        long id = dao.insert(person("旧证件"));
        service.delete(id);
        long undoId = ((Number)service.undoList().get(0).get("id")).longValue();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String,Object> payload = mapper.readValue(jdbc.queryForObject("SELECT payload FROM delete_undo WHERE id=?",String.class,undoId),Map.class);
        ((Map<?,?>)payload.get("cascades")).remove("double_excellence_record");
        jdbc.update("UPDATE delete_undo SET payload=? WHERE id=?",mapper.writeValueAsString(payload),undoId);
        assertDoesNotThrow(() -> service.undoDelete(undoId));
        assertNotNull(dao.findById(id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM double_excellence_record",Integer.class));
    }

    @Test void missingNewTableIsCompatibleButMalformedExistingTableMustAbortDeletion() {
        jdbc.execute("DROP TABLE double_excellence_record");
        long id = dao.insert(person("旧证件"));
        service.delete(id);
        service.undoDelete(((Number)service.undoList().get(0).get("id")).longValue());
        assertNotNull(dao.findById(id));
        jdbc.execute("CREATE TABLE double_excellence_record(id INTEGER PRIMARY KEY)");
        assertThrows(RuntimeException.class, () -> service.delete(id));
        assertNotNull(dao.findById(id));
        assertTrue(service.undoList().isEmpty());
    }

    @Test void doubleExcellenceRestoreConflictRollsBackPersonnelAndKeepsUndo() {
        long id = dao.insert(person("旧证件"));
        jdbc.update("INSERT INTO double_excellence_record(id,personnel_id,year,quarter,rating,rating_date) VALUES(88,?,2026,1,'好','2026-10-18')",id);
        service.delete(id);
        long undoId = ((Number)service.undoList().get(0).get("id")).longValue();
        jdbc.update("INSERT INTO double_excellence_record(id,personnel_id,year,quarter,rating,rating_date) VALUES(88,999,2026,2,'中','2026-10-19')");
        assertThrows(RuntimeException.class, () -> service.undoDelete(undoId));
        assertNull(dao.findById(id));
        assertEquals(undoId,((Number)service.undoList().get(0).get("id")).longValue());
        assertEquals(999L,jdbc.queryForObject("SELECT personnel_id FROM double_excellence_record WHERE id=88",Long.class));
    }

    @Test void undoSkipsReassignedAssetsAndDoubleUndoIsRejected() {
        long id = dao.insert(person(""));
        jdbc.update("INSERT INTO equipment(id,keeper_id) VALUES(9,?)", id);
        service.delete(id);
        jdbc.update("UPDATE equipment SET keeper_id=55 WHERE id=9"); // 删除后重新指派
        Map<String, Object> restored = service.undoDelete(((Number) service.undoList().get(0).get("id")).longValue());
        assertEquals(55L, jdbc.queryForObject("SELECT keeper_id FROM equipment WHERE id=9", Long.class));
        assertFalse(((List<?>) restored.get("skipped")).isEmpty());
    }

    @Test void undoSkipsDeletedDetachedRowsButRestoresSurvivingReferencesAndCascades() {
        long id = dao.insert(person(""));
        jdbc.update("INSERT INTO department(id,name,leader_id) VALUES(7,'已删除单位',?)", id);
        jdbc.update("INSERT INTO equipment(id,keeper_id) VALUES(9,?)", id);
        jdbc.update("INSERT INTO material(id,keeper_id) VALUES(10,?)", id);
        jdbc.update("INSERT INTO vehicle(id,driver_id) VALUES(11,?)", id);
        jdbc.update("INSERT INTO position_history(id,personnel_id) VALUES(3,?)", id);
        service.delete(id);
        long undoId = ((Number) service.undoList().get(0).get("id")).longValue();
        jdbc.update("DELETE FROM department WHERE id=7");
        jdbc.update("DELETE FROM equipment WHERE id=9");
        jdbc.update("UPDATE material SET keeper_id=55 WHERE id=10");
        Map<String, Object> restored = service.undoDelete(undoId);
        assertNotNull(restored.get("person"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM position_history WHERE personnel_id=?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM equipment WHERE id=9", Integer.class));
        assertEquals(55L, jdbc.queryForObject("SELECT keeper_id FROM material WHERE id=10", Long.class));
        assertEquals(id, jdbc.queryForObject("SELECT driver_id FROM vehicle WHERE id=11", Long.class));
        assertEquals(3, ((List<?>) restored.get("skipped")).size());
        assertTrue(restored.get("skipped").toString().contains("已删除"));
        assertTrue(service.undoList().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.undoDelete(undoId));
    }

    @Test void expiredUndoSnapshotsArePrunedWhileFreshOnesKept() {
        Personnel stalePerson = person(""); stalePerson.setName("旧快照人员");
        long stale = dao.insert(stalePerson);
        service.delete(stale);
        jdbc.update("UPDATE delete_undo SET created_at=datetime('now','localtime','-40 days') WHERE personnel_id=?", stale);
        Personnel freshPerson = person(""); freshPerson.setName("新快照人员");
        long fresh = dao.insert(freshPerson);
        service.delete(fresh); // 触发 prune
        List<Map<String, Object>> left = service.undoList();
        assertEquals(1, left.size());
        assertEquals("新快照人员", left.get(0).get("name"));
    }

    @Test void deletionDetachesAssetsAndRollsBackAllChangesOnFailure() {
        long id = dao.insert(person(""));
        jdbc.update("INSERT INTO department(id,name,leader_id) VALUES(7,'单位',?)", id);
        jdbc.update("INSERT INTO equipment VALUES(1,?)", id);
        jdbc.update("INSERT INTO material VALUES(1,?)", id);
        jdbc.update("INSERT INTO vehicle VALUES(1,?)", id);
        jdbc.update("INSERT INTO position_history VALUES(1,?)", id);
        jdbc.execute("CREATE TRIGGER deny_personnel_delete BEFORE DELETE ON personnel BEGIN SELECT RAISE(ABORT,'fixture failure'); END");
        assertThrows(RuntimeException.class, () -> service.delete(id));
        assertEquals(id, jdbc.queryForObject("SELECT keeper_id FROM equipment", Long.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM position_history", Integer.class));
        jdbc.execute("DROP TRIGGER deny_personnel_delete");
        service.delete(id);
        assertNull(dao.findById(id));
        assertEquals(0L, jdbc.queryForObject("SELECT leader_id FROM department WHERE id=7", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT keeper_id FROM equipment", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT keeper_id FROM material", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT driver_id FROM vehicle", Long.class));
    }
}
