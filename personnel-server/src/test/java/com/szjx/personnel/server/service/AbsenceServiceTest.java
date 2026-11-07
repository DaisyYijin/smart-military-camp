package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.dao.AbsenceDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.AbsenceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 在外登记:事由→状态映射、销假恢复、删除未归恢复、到期自动恢复、手工改状态放弃自动管理、
 * 同人限一条未归、数据权限。
 */
class AbsenceServiceTest {

    @TempDir Path temp;
    JdbcTemplate jdbc;
    AbsenceService service;
    AbsenceStatusSyncService sync;

    static JdbcTemplate database(Path path) {
        JdbcTemplate db = PersonnelIntegrityTest.temporaryDatabase(path);
        PersonnelIntegrityTest.personnelSchema(db);
        // auto_absence 为 SchemaInitializer 补充的历史列,隔离 schema 按模型生成,此处补齐
        db.execute("ALTER TABLE personnel ADD COLUMN auto_absence INTEGER DEFAULT 0");
        db.execute("CREATE TABLE absence_record(id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "personnel_id INTEGER NOT NULL,reason TEXT NOT NULL,leave_date TEXT NOT NULL," +
                "expected_return_date TEXT DEFAULT '',actual_return_date TEXT DEFAULT ''," +
                "remark TEXT DEFAULT '',created_at TEXT DEFAULT '',updated_at TEXT DEFAULT '')");
        db.update("INSERT INTO personnel(id,name,id_card,department_id,gender,status) VALUES(1,'周明','CARD1',1,'男','在位')");
        db.update("INSERT INTO personnel(id,name,id_card,department_id,gender,status) VALUES(2,'李宁','CARD2',2,'男','在位')");
        db.update("INSERT INTO personnel(id,name,id_card,department_id,gender,status) VALUES(3,'陈晓','CARD3',1,'男','在位')");
        return db;
    }

    @BeforeEach void setup() {
        jdbc = database(temp);
        service = new AbsenceService(new AbsenceDao(jdbc), new PersonnelDao(jdbc), jdbc);
        sync = new AbsenceStatusSyncService(jdbc);
        DataScope.set(new DataScope.Context(9L, "admin", true, Collections.<Long>emptySet()));
    }

    @AfterEach void clear() { DataScope.clear(); }

    private AbsenceRecord record(long personId, String reason, String leave, String expected, String actual) {
        AbsenceRecord r = new AbsenceRecord();
        r.setPersonnelId(personId);
        r.setReason(reason);
        r.setLeaveDate(leave);
        r.setExpectedReturnDate(expected);
        r.setActualReturnDate(actual);
        return r;
    }

    private String personStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM personnel WHERE id=?", String.class, id);
    }

    private int autoFlag(long id) {
        return jdbc.queryForObject("SELECT auto_absence FROM personnel WHERE id=?", Integer.class, id);
    }

    @Test void createMapsStatusByReason() {
        service.create(record(1, "出差", "2026-11-01", "2026-11-05", null));
        service.create(record(2, "学习", "2026-11-01", "", null));
        service.create(record(3, "其他", "2026-11-01", "", null));
        assertEquals("出差", personStatus(1));
        assertEquals("培训", personStatus(2));
        assertEquals("其他", personStatus(3));
        assertEquals(1, autoFlag(1));
        assertEquals(1, autoFlag(2));
        assertEquals(1, autoFlag(3));
    }

    @Test void updateWithActualReturnRestoresStatus() {
        AbsenceRecord created = service.create(record(1, "出差", "2026-11-01", "2026-11-05", null));
        assertEquals("出差", personStatus(1));
        service.update(created.getId(), record(1, "出差", "2026-11-01", "2026-11-05", "2026-11-04"));
        assertEquals("在位", personStatus(1));
        assertEquals(0, autoFlag(1));
        assertEquals("2026-11-04", jdbc.queryForObject(
                "SELECT actual_return_date FROM absence_record WHERE id=?", String.class, created.getId()));
    }

    @Test void deleteOpenRecordRestoresStatusButClosedDoesNot() {
        AbsenceRecord open = service.create(record(1, "出差", "2026-11-01", "", null));
        assertEquals("出差", personStatus(1));
        service.delete(open.getId());
        assertEquals("在位", personStatus(1));
        assertEquals(0, autoFlag(1));

        AbsenceRecord closed = service.create(record(1, "学习", "2026-10-01", "", "2026-10-03"));
        assertEquals("在位", personStatus(1));
        // 手工改一个在外状态后删除已归记录,不应把手工状态改回在位之外的内容
        jdbc.update("UPDATE personnel SET status='休假' WHERE id=1");
        service.delete(closed.getId());
        assertEquals("休假", personStatus(1));
    }

    @Test void syncKeepsOverdueRecordOpenUntilManualAction() {
        String yesterday = LocalDate.now().minusDays(1).toString();
        AbsenceRecord created = service.create(record(1, "出差", yesterday, yesterday, null));
        assertEquals("出差", personStatus(1));
        sync.sync();
        // 到期不再自动销假:保持在外状态与未归记录,由「延期」顺延或「归队」销假人工处理
        assertEquals("出差", personStatus(1));
        assertEquals(1, autoFlag(1));
        assertEquals("", jdbc.queryForObject(
                "SELECT actual_return_date FROM absence_record WHERE id=?", String.class, created.getId()));
    }

    @Test void postponeExtendsExpectedReturnForOpenRecord() {
        AbsenceRecord created = service.create(record(1, "出差", "2026-11-01", "2026-11-05", null));
        service.postpone(created.getId(), "2026-11-09");
        assertEquals("2026-11-09", jdbc.queryForObject(
                "SELECT expected_return_date FROM absence_record WHERE id=?", String.class, created.getId()));
        assertEquals("出差", personStatus(1), "延期后应保持在外状态");
        service.markReturned(created.getId(), null);
        assertThrows(IllegalArgumentException.class, () -> service.postpone(created.getId(), "2026-11-20"));
        AbsenceRecord other = service.create(record(2, "学习", "2026-11-01", "2026-11-02", null));
        assertThrows(IllegalArgumentException.class, () -> service.postpone(other.getId(), "2026-10-30"));
        assertThrows(IllegalArgumentException.class, () -> service.postpone(other.getId(), ""));
    }

    @Test void syncAbandonsManualStatusChange() {
        String today = LocalDate.now().toString();
        service.create(record(1, "出差", today, "", null));
        jdbc.update("UPDATE personnel SET status='离职' WHERE id=1");   // 模拟管理员手工改状态
        sync.sync();
        assertEquals("离职", personStatus(1), "手工状态应保留");
        assertEquals(0, autoFlag(1), "应放弃自动管理");
    }

    @Test void secondOpenRecordForSamePersonRejected() {
        service.create(record(1, "出差", "2026-11-01", "", null));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(record(1, "学习", "2026-11-10", "", null)));
        assertTrue(String.valueOf(ex.getMessage()).contains("未返回的在外记录"));
        // 销假后可再次登记
        AbsenceRecord closed = service.create(record(2, "出差", "2026-11-01", "", "2026-11-02"));
        assertEquals("在位", personStatus(2));
    }

    @Test void invalidDatesRejected() {
        assertThrows(RuntimeException.class, () -> service.create(record(1, "出差", "2026/11/01", "", null)));
        assertThrows(RuntimeException.class, () -> service.create(record(1, "出差", "2026-11-05", "2026-11-01", null)));
        assertThrows(RuntimeException.class, () -> service.create(record(1, "出差", "2026-11-01", "", "2026-10-01")));
    }

    @Test void markReturnedDefaultsToTodayAndRestoresStatus() {
        AbsenceRecord created = service.create(record(1, "出差", "2026-11-01", "", null));
        assertEquals("出差", personStatus(1));
        service.markReturned(created.getId(), null);
        assertEquals(LocalDate.now().toString(), jdbc.queryForObject(
                "SELECT actual_return_date FROM absence_record WHERE id=?", String.class, created.getId()));
        assertEquals("在位", personStatus(1));
        assertEquals(0, autoFlag(1));
    }

    @Test void markReturnedRejectsClosedRecordAndFutureDate() {
        AbsenceRecord open = service.create(record(1, "出差", "2026-11-01", "", null));
        service.markReturned(open.getId(), null);
        assertThrows(IllegalArgumentException.class, () -> service.markReturned(open.getId(), null));
        AbsenceRecord another = service.create(record(2, "学习", "2026-11-01", "", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.markReturned(another.getId(), LocalDate.now().plusDays(1).toString()));
    }

    @Test void updateKeepsReturnDateWhenOmitted() {
        AbsenceRecord created = service.create(record(1, "出差", "2026-11-01", "", null));
        service.markReturned(created.getId(), null);
        // 编辑表单不再提交返回日期(null):归队日期与状态必须保持
        AbsenceRecord edit = record(1, "出差", "2026-11-01", "2026-11-05", null);
        service.update(created.getId(), edit);
        assertEquals(LocalDate.now().toString(), jdbc.queryForObject(
                "SELECT actual_return_date FROM absence_record WHERE id=?", String.class, created.getId()));
        assertEquals("在位", personStatus(1));
    }

    @Test void dataScopeBlocksForeignPersonnel() {
        DataScope.set(new DataScope.Context(9L, "child", false, new HashSet<Long>(java.util.Arrays.asList(1L))));
        assertThrows(RuntimeException.class, () -> service.create(record(2, "出差", "2026-11-01", "", null)));
        assertEquals("在位", personStatus(2), "范围外人员不应被改动");
        AbsenceRecord created = service.create(record(1, "出差", "2026-11-01", "", null));
        DataScope.set(new DataScope.Context(9L, "other", false, new HashSet<Long>(java.util.Arrays.asList(2L))));
        assertThrows(RuntimeException.class, () -> service.update(created.getId(), record(1, "学习", "2026-11-01", "", null)));
        assertThrows(RuntimeException.class, () -> service.delete(created.getId()));
    }

    @Test void futureLeaveDatePreRegistrationKeepsStatusUntilDue() {
        // 11月预登记12月的在外 → 状态不应立即变更
        AbsenceRecord created = service.create(record(1, "学习",
                LocalDate.now().plusDays(30).toString(), LocalDate.now().plusDays(90).toString(), null));
        assertEquals("在位", personStatus(1));
        assertEquals(2, jdbc.queryForObject("SELECT auto_absence FROM personnel WHERE id=1", Integer.class));
        // 离开日期到期(把记录改到昨天) → 每小时同步生效为"培训"
        jdbc.update("UPDATE absence_record SET leave_date=? WHERE id=?",
                LocalDate.now().minusDays(1).toString(), created.getId());
        sync.sync();
        assertEquals("培训", personStatus(1));
        assertEquals(1, jdbc.queryForObject("SELECT auto_absence FROM personnel WHERE id=1", Integer.class));
    }

    @Test void syncDemotesPrematurelyActivatedPreRegistration() {
        // 自愈:历史登记被提前改了状态但离开日期在未来 → 恢复在位并回退待生效
        jdbc.update("INSERT INTO absence_record(personnel_id,reason,leave_date,expected_return_date,actual_return_date,remark,created_at,updated_at) " +
                        "VALUES(1,'学习',?,?,'','','','')",
                LocalDate.now().plusDays(30).toString(), LocalDate.now().plusDays(90).toString());
        jdbc.update("UPDATE personnel SET status='培训', auto_absence=1 WHERE id=1");
        sync.sync();
        assertEquals("在位", personStatus(1));
        assertEquals(2, jdbc.queryForObject("SELECT auto_absence FROM personnel WHERE id=1", Integer.class));
    }
}
