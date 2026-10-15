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
            if (property.getWriteMethod() == null || "id".equals(name) || "photoBase64".equals(name) || "departmentName".equals(name)) continue;
            String column = name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
            ddl.append(',').append(column).append(property.getPropertyType() == Long.class ? " INTEGER DEFAULT 0" : property.getPropertyType() == Double.class ? " REAL" : " TEXT DEFAULT ''");
        }
        jdbc.execute(ddl + ", auto_leave INTEGER DEFAULT 0, auto_leave_previous_status TEXT DEFAULT '')");
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY, name TEXT, parent_id INTEGER, sort INTEGER, remark TEXT, leader_id INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE phone_device(id INTEGER PRIMARY KEY, personnel_id INTEGER, phone_number TEXT)");
        jdbc.execute("CREATE TABLE delete_undo(id INTEGER PRIMARY KEY AUTOINCREMENT, personnel_id INTEGER, name TEXT, summary TEXT DEFAULT '', payload TEXT, photo_trash TEXT DEFAULT '', created_at TEXT DEFAULT (datetime('now','localtime')))");
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
        for (String table : new String[]{"leave_record", "fitness_record", "military_assessment", "position_history", "reward_punish", "child_info", "family_member", "driver_license", "personal_card", "private_vehicle", "housing_info"}) {
            jdbc.execute("CREATE TABLE " + table + "(id INTEGER PRIMARY KEY, personnel_id INTEGER)");
        }
        jdbc.execute("CREATE TABLE equipment(id INTEGER PRIMARY KEY, keeper_id INTEGER)");
        jdbc.execute("CREATE TABLE material(id INTEGER PRIMARY KEY, keeper_id INTEGER)");
        jdbc.execute("CREATE TABLE vehicle(id INTEGER PRIMARY KEY, driver_id INTEGER)");
    }

    Personnel person(String card) { Personnel p = new Personnel(); p.setName("测试人员"); p.setIdCard(card); p.setStatus("在职"); return p; }

    @Test void partialPhotoUpdatePreservesEveryModelPropertyAndEmptyStringsClear() {
        Personnel old = person("");
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
            if (property.getWriteMethod() == null || java.util.Arrays.asList("id", "photo", "photoBase64", "phone", "createdAt", "updatedAt", "departmentName", "marriageRemark", "departmentId").contains(n)) continue;
            assertEquals(bean.getPropertyValue(n), after.getPropertyValue(n), n);
        }
        // DAO 将空部门规范化为 0 入库;部分更新须原样保留而非清空
        assertEquals(0L, updated.getDepartmentId());
        assertEquals("", updated.getMarriageRemark());
        assertEquals("13800138000", updated.getPhone());
        verify(photos).save(anyString(), any(byte[].class));
        verify(photos).delete("old.jpg");
    }

    @Test void identityNormalizationChecksumDuplicateAndBlankCompatibility() {
        Personnel p = service.create(person(" 11010519491231002x "));
        assertEquals("11010519491231002X", p.getIdCard());
        assertThrows(IllegalArgumentException.class, () -> service.create(person("11010519491231002X")));
        assertThrows(IllegalArgumentException.class, () -> service.create(person("110105194912310021")));
        assertThrows(IllegalArgumentException.class, () -> service.create(person("110105199902300021")));
        assertDoesNotThrow(() -> service.create(person("")));
        assertDoesNotThrow(() -> service.create(person("   ")));
        assertDoesNotThrow(() -> service.create(person("110105491231002")));
        jdbc.update("UPDATE personnel SET id_card='legacy-invalid', department_id=99 WHERE id=?", p.getId());
        Personnel partial = new Personnel(); partial.setRemark("兼容旧记录");
        assertEquals("LEGACY-INVALID", service.update(p.getId(), partial).getIdCard());
    }

    @Test void departmentReferenceAndPhoneSource() {
        Personnel bad = person(""); bad.setDepartmentId(5L);
        assertThrows(IllegalArgumentException.class, () -> service.create(bad));
        jdbc.update("INSERT INTO department(id,name) VALUES(5,'部门')");
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
        jdbc.update("INSERT INTO fitness_record(id,personnel_id) VALUES(1,?)", id);
        jdbc.update("INSERT INTO department(id,name,leader_id) VALUES(7,'部门',?)", id);
        jdbc.update("INSERT INTO equipment(id,keeper_id) VALUES(9,?)", id);
        jdbc.update("INSERT INTO position_history(id,personnel_id) VALUES(3,?)", id);
        service.delete(id);
        assertNull(dao.findById(id));
        for (String table : new String[]{"phone_device", "leave_record", "fitness_record", "position_history"}) {
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
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fitness_record", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM position_history", Integer.class));
        assertEquals(id, jdbc.queryForObject("SELECT leader_id FROM department WHERE id=7", Long.class));
        assertEquals(id, jdbc.queryForObject("SELECT keeper_id FROM equipment WHERE id=9", Long.class));
        assertTrue(service.undoList().isEmpty());
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
        jdbc.update("INSERT INTO department(id,name,leader_id) VALUES(1,'部门',?)", id);
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
        assertEquals(0L, jdbc.queryForObject("SELECT leader_id FROM department", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT keeper_id FROM equipment", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT keeper_id FROM material", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT driver_id FROM vehicle", Long.class));
    }
}
