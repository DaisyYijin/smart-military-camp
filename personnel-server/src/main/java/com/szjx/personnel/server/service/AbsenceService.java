package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.AbsenceDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.AbsenceRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 人员在外登记业务:登记后按事由自动变更人员状态,返回(销假)后恢复"在位"。
 * 销假统一走编辑表单:实际返回日期留空=未归,填写即视为销假返回。
 */
@Service
public class AbsenceService {

    /** 事由 → 在外期间的人员状态;未匹配的事由归入"其他"。字典 absence_reason 可扩展,新事由默认置"其他"。 */
    private static final Map<String, String> REASON_STATUS = new HashMap<String, String>();

    static {
        REASON_STATUS.put("出差", "出差");
        REASON_STATUS.put("学习", "培训");
    }

    /** 在外期间可能被本服务设置的状态集合(手工改成集合外的状态视为放弃自动管理) */
    public static final String[] MANAGED_STATUSES = {"出差", "培训", "其他"};

    private final AbsenceDao absenceDao;
    private final PersonnelDao personnelDao;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public AbsenceService(AbsenceDao absenceDao, PersonnelDao personnelDao,
                          org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.absenceDao = absenceDao;
        this.personnelDao = personnelDao;
        this.jdbc = jdbc;
    }

    public static String statusFor(String reason) {
        String s = REASON_STATUS.get(reason == null ? "" : reason.trim());
        return s == null ? "其他" : s;
    }

    @Transactional
    public AbsenceRecord create(AbsenceRecord r) {
        r.setId(null);
        validate(r);
        long id = absenceDao.insert(r);
        // 补录历史记录(创建即带实际返回日期)不改变当前状态;在外中的登记才按事由置状态
        applyStatus(r.getPersonnelId(), r.getReason(), r.getLeaveDate(), StringUtils.hasText(r.getActualReturnDate()));
        return absenceDao.findById(id);
    }

    @Transactional
    public AbsenceRecord update(Long id, AbsenceRecord r) {
        AbsenceRecord old = absenceDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        r.setId(id);
        // 表单不再编辑返回日期:未传(null)时保留库中旧值,避免编辑已归记录时清空归队日期
        if (r.getActualReturnDate() == null) {
            r.setActualReturnDate(old.getActualReturnDate());
        }
        if (r.getExpectedReturnDate() == null) {
            r.setExpectedReturnDate(old.getExpectedReturnDate());
        }
        validate(r);
        absenceDao.update(r);
        // 状态由"是否已填实际返回日期"决定:未归→按事由映射;已归→恢复在位
        applyStatus(r.getPersonnelId(), r.getReason(), r.getLeaveDate(), StringUtils.hasText(r.getActualReturnDate()));
        return absenceDao.findById(id);
    }

    /** 一键归队:实际返回日期置为指定日期(默认今天),人员状态恢复"在位"。 */
    @Transactional
    public AbsenceRecord markReturned(Long id, String date) {
        AbsenceRecord old = absenceDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        if (StringUtils.hasText(old.getActualReturnDate())) {
            throw new IllegalArgumentException("该人员已于 " + old.getActualReturnDate() + " 归队");
        }
        String day = StringUtils.hasText(date) ? date : java.time.LocalDate.now().toString();
        java.time.LocalDate actual = parseDate(day, "归队日期");
        if (actual.isAfter(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("归队日期不能晚于今天");
        }
        if (actual.isBefore(parseDate(old.getLeaveDate(), "离开单位日期"))) {
            throw new IllegalArgumentException("归队日期不能早于离开日期");
        }
        jdbc.update("UPDATE absence_record SET actual_return_date=?, updated_at=datetime('now','localtime') WHERE id=?", actual.toString(), id);
        restoreStatus(old.getPersonnelId());
        return absenceDao.findById(id);
    }

    /** 延期:未归记录顺延预计返回日期(到期未归时使用,记录保持未归、状态保持在外)。 */
    @Transactional
    public AbsenceRecord postpone(Long id, String newExpected) {
        AbsenceRecord old = absenceDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        if (StringUtils.hasText(old.getActualReturnDate())) {
            throw new IllegalArgumentException("该人员已归队,无需延期");
        }
        if (!StringUtils.hasText(newExpected)) {
            throw new IllegalArgumentException("请填写新的预计返回日期");
        }
        java.time.LocalDate expected = parseDate(newExpected, "预计返回日期");
        if (expected.isBefore(parseDate(old.getLeaveDate(), "离开单位日期"))) {
            throw new IllegalArgumentException("预计返回日期不能早于离开日期");
        }
        jdbc.update("UPDATE absence_record SET expected_return_date=?, updated_at=datetime('now','localtime') WHERE id=?",
                expected.toString(), id);
        return absenceDao.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        AbsenceRecord old = absenceDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        // 删除未归记录时人员视为已返回,恢复在位
        if (!StringUtils.hasText(old.getActualReturnDate())) {
            restoreStatus(old.getPersonnelId());
        }
        absenceDao.delete(id);
    }

    private void applyStatus(Long personnelId, String reason, String leaveDate, boolean returned) {
        if (returned) {
            restoreStatus(personnelId);
        } else if (leaveDate != null && leaveDate.compareTo(java.time.LocalDate.now().toString()) > 0) {
            // 预登记:离开日期还没到,不改当前状态;标记待生效(auto_absence=2),到期由 AbsenceStatusSyncService 每小时核对自动变更
            jdbc.update("UPDATE personnel SET auto_absence=2, updated_at=datetime('now','localtime') WHERE id=?", personnelId);
        } else {
            jdbc.update("UPDATE personnel SET status=?, auto_absence=1, updated_at=datetime('now','localtime') WHERE id=?",
                    statusFor(reason), personnelId);
        }
    }

    private void restoreStatus(Long personnelId) {
        jdbc.update("UPDATE personnel SET status='在位', auto_absence=0, updated_at=datetime('now','localtime') WHERE id=?",
                personnelId);
    }

    private void validate(AbsenceRecord r) {
        if (r.getPersonnelId() == null || r.getPersonnelId() <= 0) {
            throw new IllegalArgumentException("请选择在外人员");
        }
        if (personnelDao.findById(r.getPersonnelId()) == null) {
            throw new IllegalArgumentException("所选人员不存在");
        }
        if (!StringUtils.hasText(r.getReason())) {
            throw new IllegalArgumentException("请填写在外事由");
        }
        if (!StringUtils.hasText(r.getLeaveDate())) {
            throw new IllegalArgumentException("请填写离开单位日期");
        }
        java.time.LocalDate leave = parseDate(r.getLeaveDate(), "离开单位日期");
        r.setLeaveDate(leave.toString());
        if (StringUtils.hasText(r.getExpectedReturnDate())) {
            java.time.LocalDate expected = parseDate(r.getExpectedReturnDate(), "预计返回日期");
            if (expected.isBefore(leave)) {
                throw new IllegalArgumentException("预计返回日期不能早于离开日期");
            }
            r.setExpectedReturnDate(expected.toString());
        } else {
            r.setExpectedReturnDate("");
        }
        if (StringUtils.hasText(r.getActualReturnDate())) {
            java.time.LocalDate actual = parseDate(r.getActualReturnDate(), "实际返回日期");
            if (actual.isBefore(leave)) {
                throw new IllegalArgumentException("实际返回日期不能早于离开日期");
            }
            r.setActualReturnDate(actual.toString());
        } else {
            r.setActualReturnDate("");
        }
        // 同一人员同时只允许一条未归记录,避免状态歧义;延期场景应编辑原记录的预计返回日期
        Long open = jdbc.queryForObject(
                "SELECT COUNT(*) FROM absence_record WHERE personnel_id=? AND actual_return_date='' AND (? IS NULL OR id<>?)",
                Long.class, r.getPersonnelId(), r.getId(), r.getId());
        if (open != null && open > 0) {
            throw new IllegalArgumentException("该人员已有未返回的在外记录,请编辑原记录或先销假");
        }
    }

    private java.time.LocalDate parseDate(String date, String label) {
        String value = date == null ? "" : date.trim();
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException(label + "格式应为yyyy-MM-dd");
        }
        try {
            return java.time.LocalDate.parse(value);
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException(label + "不存在");
        }
    }
}
