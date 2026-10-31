package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.util.ItemLoanSnapshots;
import com.szjx.personnel.server.util.PersonnelExtraFilters;

import com.szjx.personnel.server.dao.DepartmentDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.model.Department;
import com.szjx.personnel.server.model.Personnel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 人员业务
 */
@Service
public class PersonnelService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PersonnelService.class);

    private final PersonnelDao personnelDao;
    private final PhotoService photoService;
    private final DepartmentDao departmentDao;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public PersonnelService(PersonnelDao personnelDao, PhotoService photoService, DepartmentDao departmentDao,
                             org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.personnelDao = personnelDao;
        this.photoService = photoService;
        this.departmentDao = departmentDao;
        this.jdbc = jdbc;
    }

    public List<Personnel> query(String keyword, String name, String idCard, Long departmentId, String status, String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education, String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint, String entryDateStart, String entryDateEnd, int page, int size) {
        return personnelDao.query(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, locationPoint, entryDateStart, entryDateEnd, page, size);
    }

    public List<Personnel> query(String keyword, String name, String idCard, Long departmentId, String status, String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education, String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint, String entryDateStart, String entryDateEnd, int page, int size, boolean includeDescendants) {
        return personnelDao.query(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, locationPoint, entryDateStart, entryDateEnd, page, size, includeDescendants);
    }

    public long count(String keyword, String name, String idCard, Long departmentId, String status, String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education, String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint, String entryDateStart, String entryDateEnd) {
        return personnelDao.count(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, locationPoint, entryDateStart, entryDateEnd);
    }

    public long count(String keyword, String name, String idCard, Long departmentId, String status, String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education, String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint, String entryDateStart, String entryDateEnd, boolean includeDescendants) {
        return personnelDao.count(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, locationPoint, entryDateStart, entryDateEnd, includeDescendants);
    }

    public List<Personnel> query(String keyword, String name, String idCard, Long departmentId, String status, String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education, String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint, String entryDateStart, String entryDateEnd, int page, int size, boolean includeDescendants, PersonnelExtraFilters extraFilters) {
        return personnelDao.query(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, locationPoint, entryDateStart, entryDateEnd, page, size, includeDescendants, extraFilters);
    }

    public long count(String keyword, String name, String idCard, Long departmentId, String status, String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education, String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint, String entryDateStart, String entryDateEnd, boolean includeDescendants, PersonnelExtraFilters extraFilters) {
        return personnelDao.count(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, locationPoint, entryDateStart, entryDateEnd, includeDescendants, extraFilters);
    }

    public Personnel findById(Long id) {
        return personnelDao.findById(id);
    }

    @Transactional
    public Personnel create(Personnel p) {
        p.setId(null);
        DataScope.requireDepartment(p.getDepartmentId());
        validate(p, null);
        p.setPhone("");
        String photoFile = savePhotoIfAny(p.getPhotoBase64());
        p.setPhoto(photoFile);
        if (photoFile != null) {
            deleteOnRollback(photoFile); // 必须在入库前注册，否则入库异常无法清理照片
        }
        long id = personnelDao.insert(p);
        return personnelDao.findById(id);
    }

    @Transactional
    public Personnel update(Long id, Personnel p) {
        Personnel old = personnelDao.findById(id);
        if (old == null) {
            throw new IllegalArgumentException("人员不存在");
        }
        // null 字段保留旧值(避免部分更新丢数据;清空字段请传空串)
        mergeMissing(old, p);
        p.setId(id);
        DataScope.requireDepartment(old.getDepartmentId());
        DataScope.requireDepartment(p.getDepartmentId());
        validate(p, old);
        // 人员电话仅为设备模块的镜像，不接受人员表单直接覆盖。
        p.setPhone(personnelDao.devicePhone(id));
        if (!java.util.Objects.equals(old.getStatus(), p.getStatus())) {
            jdbc.update("UPDATE personnel SET auto_leave=0, auto_leave_previous_status='' WHERE id=?", id);
        }
        String photoFile = old.getPhoto();
        if (StringUtils.hasText(p.getPhotoBase64())) {
            // 提交了新照片
            String newFile = savePhotoIfAny(p.getPhotoBase64());
            // 替换旧照片(事务提交后删除旧文件)
            if (StringUtils.hasText(old.getPhoto()) && !old.getPhoto().equals(newFile)) {
                deletePhotoAfterCommit(old.getPhoto());
            }
            deleteOnRollback(newFile);
            photoFile = newFile;
        } else if (p.getPhoto() != null && p.getPhoto().isEmpty() && StringUtils.hasText(old.getPhoto())) {
            // 明确要求删除照片(事务提交后再删文件,避免回滚后文件丢失)
            final String oldPhoto = old.getPhoto();
            deletePhotoAfterCommit(oldPhoto);
            photoFile = "";
        }
        p.setId(id);
        p.setPhoto(photoFile);
        personnelDao.update(p);
        return personnelDao.findById(id);
    }

    /**
     * 事务回滚时删除已写入的照片文件(与写入侧对称)
     */
    private void deleteOnRollback(final String photo) {
        if (photo == null || photo.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    photoService.delete(photo);
                }
            }
        });
    }

    /** 覆盖模型全部可读写属性；null 保留，空串明确清空。提交用照片数据不继承。 */
    private void mergeMissing(Personnel old, Personnel p) {
        org.springframework.beans.BeanWrapper source = new org.springframework.beans.BeanWrapperImpl(old);
        org.springframework.beans.BeanWrapper target = new org.springframework.beans.BeanWrapperImpl(p);
        for (java.beans.PropertyDescriptor property : target.getPropertyDescriptors()) {
            String name = property.getName();
            if (property.getReadMethod() != null && property.getWriteMethod() != null
                    && !"photoBase64".equals(name) && target.getPropertyValue(name) == null) {
                target.setPropertyValue(name, source.getPropertyValue(name));
            }
        }
    }

    /** 级联删除的记录表(撤回时整表恢复) */
    private static final String[] CASCADE_TABLES = {
            "position_history", "reward_punish", "child_info", "family_member",
            "driver_license", "personal_card", "private_vehicle", "housing_info",
            "phone_device", "leave_record", "leave_plan", "fitness_record", "military_assessment",
            "double_excellence_record", "annual_appraisal_record"};
    /** 被解除的外键引用:表 → 引用列 */
    private static final String[][] DETACH_REFS = {
            {"department", "leader_id"}, {"equipment", "keeper_id"},
            {"material", "keeper_id"}, {"vehicle", "driver_id"}};
    /** 撤回快照保留策略:30 天内且总量不超过 1000 条 */
    private static final int UNDO_KEEP_DAYS = 30;
    private static final int UNDO_KEEP_MAX = 1000;
    private static final com.fasterxml.jackson.databind.ObjectMapper UNDO_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @Transactional
    public void delete(Long id) {
        Personnel old = personnelDao.findById(id);
        if (old == null) {
            return;
        }
        // 历史负责人引用可能跨单位，子账号不得借删除人员修改外域单位。
        if (DataScope.restricted()) {
            Long externalLeaders = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM department WHERE leader_id=? AND NOT (" + DataScope.departmentSql("id") + ")",
                    Long.class, id);
            if (externalLeaders != null && externalLeaders > 0) {
                throw new com.szjx.personnel.server.auth.ForbiddenException("该人员仍被管理范围外的单位引用，请联系上级先调整负责人");
            }
        }
        // 打包快照:人员行 + 级联记录 + 被解除的引用 + 照片移入回收站
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("person", jdbc.queryForMap("SELECT * FROM personnel WHERE id=?", id));
        Map<String, List<Map<String, Object>>> cascades = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : CASCADE_TABLES) {
            // 仅兼容新增表缺失的旧隔离fixture；已有表损坏或其它SQL错误仍须中止删除。
            if (("double_excellence_record".equals(table) || "annual_appraisal_record".equals(table)) && jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", Integer.class, table) == 0) continue;
            cascades.put(table, jdbc.queryForList("SELECT * FROM " + table + " WHERE personnel_id=?", id));
        }
        payload.put("cascades", cascades);
        List<Map<String, Object>> detached = new ArrayList<Map<String, Object>>();
        for (String[] ref : DETACH_REFS) {
            for (Map<String, Object> row : jdbc.queryForList(
                    "SELECT id AS row_id, " + ref[1] + " AS ref_value FROM " + ref[0] + " WHERE " + ref[1] + "=?", id)) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("table", ref[0]);
                item.put("column", ref[1]);
                item.put("rowId", row.get("row_id"));
                item.put("value", row.get("ref_value"));
                detached.add(item);
            }
        }
        payload.put("detached", detached);
        String trash = photoService.moveToTrash(old.getPhoto());
        payload.put("photo", old.getPhoto() == null ? "" : old.getPhoto());
        try {
            jdbc.update("INSERT INTO delete_undo(personnel_id, name, summary, payload, photo_trash) VALUES(?,?,?,?,?)",
                    id, old.getName(), buildUndoSummary(cascades), UNDO_MAPPER.writeValueAsString(payload), trash);
        } catch (Exception e) {
            if (!trash.isEmpty()) {
                photoService.restoreFromTrash(trash, old.getPhoto());
            }
            throw new IllegalStateException("生成删除撤回快照失败,已中止删除", e);
        }
        // 同一事务内执行级联删除
        for (String[] ref : DETACH_REFS) {
            jdbc.update("UPDATE " + ref[0] + " SET " + ref[1] + "=0 WHERE " + ref[1] + "=?", id);
        }
        for (String table : cascades.keySet()) {
            jdbc.update("DELETE FROM " + table + " WHERE personnel_id=?", id);
        }
        personnelDao.delete(id);
        pruneUndo();
    }

    private String buildUndoSummary(Map<String, List<Map<String, Object>>> cascades) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<Map<String, Object>>> entry : cascades.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                if (sb.length() > 0) { sb.append(','); }
                sb.append(tableZh(entry.getKey())).append(entry.getValue().size()).append('条');
            }
        }
        return sb.length() > 0 ? sb.toString() : "无关联记录";
    }

    static String tableZh(String table) {
        if ("phone_device".equals(table)) { return "手机设备"; }
        if ("item_loan".equals(table)) { return "物品借还"; }
        if ("leave_plan".equals(table)) { return "休假计划"; }
        if ("leave_record".equals(table)) { return "休假"; }
        if ("fitness_record".equals(table)) { return "体能"; }
        if ("military_assessment".equals(table)) { return "军事考核"; }
        if ("double_excellence_record".equals(table)) { return "双争评比"; }
        if ("annual_appraisal_record".equals(table)) { return "年度考评"; }
        if ("position_history".equals(table)) { return "任职经历"; }
        if ("reward_punish".equals(table)) { return "奖惩"; }
        if ("child_info".equals(table)) { return "子女"; }
        if ("family_member".equals(table)) { return "家庭"; }
        if ("driver_license".equals(table)) { return "驾驶证"; }
        if ("personal_card".equals(table)) { return "卡号"; }
        if ("private_vehicle".equals(table)) { return "私家车"; }
        if ("housing_info".equals(table)) { return "住房"; }
        return table;
    }

    /** 清理过期撤回快照(超 30 天或超 1000 条,含回收站照片);删除后与启动时各执行一次 */
    private void pruneUndo() {
        List<Map<String, Object>> stale = jdbc.queryForList(
                "SELECT id, photo_trash FROM delete_undo WHERE created_at < datetime('now','localtime','-" + UNDO_KEEP_DAYS + " days')" +
                " OR id <= (SELECT id FROM delete_undo ORDER BY id DESC LIMIT 1 OFFSET " + (UNDO_KEEP_MAX - 1) + ")");
        for (Map<String, Object> row : stale) {
            photoService.deleteFromTrash((String) row.get("photo_trash"));
            jdbc.update("DELETE FROM delete_undo WHERE id=?", row.get("id"));
        }
    }

    /** 启动时清理上次运行遗留的过期快照 */
    @javax.annotation.PostConstruct
    public void pruneUndoOnStartup() {
        try {
            pruneUndo();
        } catch (Exception e) {
            log.warn("启动清理撤回快照失败: {}", e.getMessage());
        }
    }

    /** 可撤回的删除列表(新→旧) */
    public List<Map<String, Object>> undoList() {
        DataScope.requireAdmin();
        return jdbc.queryForList(
                "SELECT id, personnel_id, name, summary, created_at FROM delete_undo ORDER BY id DESC");
    }

    /** 撤回最近一次/指定记录的删除,恢复人员及全部级联数据 */
    @Transactional
    public Map<String, Object> undoDelete(Long undoId) {
        DataScope.requireAdmin();
        List<Map<String, Object>> hits = jdbc.queryForList("SELECT * FROM delete_undo WHERE id=?", undoId);
        if (hits.isEmpty()) {
            throw new IllegalArgumentException("该撤回记录不存在或已被使用");
        }
        Map<String, Object> record = hits.get(0);
        Map<String, Object> payload;
        try {
            payload = UNDO_MAPPER.readValue((String) record.get("payload"), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("撤回快照已损坏", e);
        }
        long personId = ((Number) record.get("personnel_id")).longValue();
        if (jdbc.queryForList("SELECT id FROM personnel WHERE id=?", personId).size() > 0) {
            throw new IllegalArgumentException("该人员已存在,不能重复撤回");
        }
        List<String> skipped = new ArrayList<String>();
        // 人员行按原ID恢复
        restoreRow("personnel", (Map<String, Object>) payload.get("person"));
        // 级联记录按原ID恢复
        Map<String, List<Map<String, Object>>> cascades = (Map<String, List<Map<String, Object>>>) payload.get("cascades");
        for (Map.Entry<String, List<Map<String, Object>>> entry : cascades.entrySet()) {
            if ("item_loan".equals(entry.getKey())) { continue; }
            for (Map<String, Object> row : entry.getValue()) {
                restoreRow(entry.getKey(), row);
            }
        }
        // 旧删除快照仍可能包含借还。人员已插入后再读取真实集合，不能使用删除前/覆盖前的本地人员。
        if (cascades.containsKey("item_loan") && !cascades.get("item_loan").isEmpty()) {
            List<Map<String, Object>> personnel = jdbc.queryForList("SELECT * FROM personnel");
            List<Map<String, Object>> departments = jdbc.queryForList("SELECT * FROM department");
            for (Map<String, Object> row : cascades.get("item_loan")) {
                Map<String, Object> converted = ItemLoanSnapshots.convertLegacy(row, personnel, departments);
                // Department snapshots are descriptive only; historical loans do not depend on a live department.
                restoreRow("item_loan", converted);
            }
        }
        // 恢复被解除的引用;期间已被重新指派的跳过
        for (Map<String, Object> ref : (List<Map<String, Object>>) payload.get("detached")) {
            String table = (String) ref.get("table");
            String column = (String) ref.get("column");
            List<Long> currentRefs = jdbc.queryForList(
                    "SELECT " + column + " FROM " + table + " WHERE id=?", Long.class, ref.get("rowId"));
            if (currentRefs.isEmpty()) {
                skipped.add(tableZh(table) + "(ID " + ref.get("rowId") + ")已删除,未恢复关联");
                continue;
            }
            Long current = currentRefs.get(0);
            if (current == null) { continue; }
            if (current != 0) {
                skipped.add(tableZh(table) + "(ID " + ref.get("rowId") + ")已重新指派他人,未覆盖");
                continue;
            }
            jdbc.update("UPDATE " + table + " SET " + column + "=? WHERE id=?", ref.get("value"), ref.get("rowId"));
        }
        // 恢复照片文件
        String trash = String.valueOf(record.get("photo_trash") == null ? "" : record.get("photo_trash"));
        if (!trash.isEmpty() && !photoService.restoreFromTrash(trash, (String) payload.get("photo"))) {
            skipped.add("照片文件恢复失败,请重新上传");
        }
        // 手机号镜像按恢复的设备重算
        List<String> numbers = jdbc.queryForList(
                "SELECT phone_number FROM phone_device WHERE personnel_id=? AND TRIM(COALESCE(phone_number,''))<>'' ORDER BY id DESC LIMIT 1",
                String.class, personId);
        jdbc.update("UPDATE personnel SET phone=? WHERE id=?", numbers.isEmpty() ? "" : numbers.get(0), personId);
        jdbc.update("DELETE FROM delete_undo WHERE id=?", undoId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("person", personnelDao.findById(personId));
        result.put("summary", record.get("summary"));
        result.put("skipped", skipped);
        return result;
    }

    /** 放弃撤回:彻底删除快照与回收站照片 */
    @Transactional
    public void discardUndo(Long undoId) {
        DataScope.requireAdmin();
        List<Map<String, Object>> hits = jdbc.queryForList("SELECT id, photo_trash FROM delete_undo WHERE id=?", undoId);
        if (hits.isEmpty()) {
            throw new IllegalArgumentException("该撤回记录不存在或已被使用");
        }
        Map<String, Object> record = hits.get(0);
        photoService.deleteFromTrash((String) record.get("photo_trash"));
        jdbc.update("DELETE FROM delete_undo WHERE id=?", undoId);
    }

    /** 按快照列名整行恢复(含原ID) */
    private void restoreRow(String table, Map<String, Object> row) {
        List<String> cols = new ArrayList<String>(row.keySet());
        StringBuilder names = new StringBuilder(), marks = new StringBuilder();
        for (String col : cols) {
            if (names.length() > 0) { names.append(','); marks.append(','); }
            names.append(col); marks.append('?');
        }
        jdbc.update("INSERT INTO " + table + "(" + names + ") VALUES(" + marks + ")",
                row.values().toArray());
    }

    /**
     * 事务提交后再删除照片文件;无事务时直接删除
     */
    private void deletePhotoAfterCommit(final String photo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            photoService.delete(photo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                photoService.delete(photo);
            }
        });
    }

    /** 获取照片文件(供下载接口) */
    public java.io.File photoFile(String fileName) {
        return photoService.getFile(fileName);
    }

    public Map<String, Object> findByIdCard(String idCard) {
        Personnel p = personnelDao.findByIdCard(idCard);
        if (p == null) return null;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", p.getId());
        result.put("name", p.getName());
        result.put("idCard", p.getIdCard());
        result.put("militaryIdNo", p.getMilitaryIdNo());
        result.put("departmentId", p.getDepartmentId());
        result.put("departmentName", p.getDepartmentName());
        result.put("departmentPath", p.getDepartmentPath());
        return result;
    }

    /** 全部人员精简选项(下拉框用)。
     *  字段最小化:仅保留下拉显示与借还回填所需;身份证号供物品借还选人后回填,
     *  军人证件号/军衔/职务无页面消费,不再随选项接口下发。 */
    public List<Map<String, Object>> findAllOptions() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : personnelDao.findAllSimple()) {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("id", row.get("id"));
            m.put("name", row.get("name"));
            m.put("idCard", row.get("id_card"));
            m.put("departmentId", row.get("department_id"));
            m.put("departmentPath", row.get("department_path"));
            m.put("departmentName", row.get("department_name"));
            result.add(m);
        }
        return result;
    }

    /**
     * 批量导入:按身份证号匹配,存在则更新,不存在则新增
     * @return [新增数, 更新数, 失败数]
     */
    @Transactional
    public int[] importRows(List<Personnel> rows) {
        int added = 0;
        int updated = 0;
        int failed = 0;
        for (Personnel row : rows) {
            try {
                if (!StringUtils.hasText(row.getName())) {
                    failed++;
                    continue;
                }
                requireText(row.getMilitaryIdNo(), "军人证件号");
                row.setIdCard(normalizeIdCard(row.getIdCard()));
                Personnel existing = personnelDao.findByIdCard(row.getIdCard());
                // 解析单位名称 → 单位ID(找不到则自动创建顶级单位)
                if (StringUtils.hasText(row.getDepartmentName())) {
                    row.setDepartmentId(resolveDepartmentId(row.getDepartmentName()));
                } else {
                    row.setDepartmentId(0L);
                }
                if (existing != null) {
                    row.setId(existing.getId());
                    row.setPhoto(existing.getPhoto());
                    mergeMissing(existing, row);
                    validate(row, existing);
                    row.setPhone(personnelDao.devicePhone(existing.getId()));
                    personnelDao.update(row);
                    updated++;
                } else {
                    row.setId(null);
                    validate(row, null);
                    row.setPhone("");
                    personnelDao.insert(row);
                    added++;
                }
            } catch (Exception e) {
                failed++;
            }
        }
        return new int[]{added, updated, failed};
    }

    private void validate(Personnel p, Personnel old) {
        DataScope.requireDepartment(p.getDepartmentId());
        if (old != null) { DataScope.requireDepartment(old.getDepartmentId()); }
        requireText(p.getName(), "姓名");
        requireText(p.getIdCard(), "身份证号");
        p.setMilitaryIdNo(p.getMilitaryIdNo() == null ? "" : p.getMilitaryIdNo().trim());
        requireText(p.getMilitaryIdNo(), "军人证件号"); // 恒必填:历史空的旧数据编辑时也必须补全(关键标识,见 PersonnelIntegrityTest);
        requireFilled(p.getGender(), old == null ? null : old.getGender(), "性别");
        requireFilled(p.getBirthday(), old == null ? null : old.getBirthday(), "出生日期");
        requireFilled(p.getLocationPoint(), old == null ? null : old.getLocationPoint(), "所在点位");
        requireFilled(p.getCategory(), old == null ? null : old.getCategory(), "人员类别");
        requireFilled(p.getRank(), old == null ? null : old.getRank(), "军衔/职级");
        requireFilled(p.getPost(), old == null ? null : old.getPost(), "职务/岗位");
        requireFilled(p.getEntryDate(), old == null ? null : old.getEntryDate(), "入伍/入职日期");
        requireFilled(p.getEquivEntryDate(), old == null ? null : old.getEquivEntryDate(), "相当入伍时间");
        requireFilled(p.getNation(), old == null ? null : old.getNation(), "民族");
        requireFilled(p.getNativePlace(), old == null ? null : old.getNativePlace(), "籍贯所在地");
        requireFilled(p.getEnlistPlace(), old == null ? null : old.getEnlistPlace(), "入伍所在地");
        requireFilled(p.getBloodType(), old == null ? null : old.getBloodType(), "血型");
        requireFilled(p.getOnlyChild(), old == null ? null : old.getOnlyChild(), "是否独生子女");
        requireFilled(p.getAddress(), old == null ? null : old.getAddress(), "详细地址");
        if (!StringUtils.hasText(p.getStatus())) {
            throw new IllegalArgumentException("状态不能为空");
        }
        if (p.getStatus().length() > 20) {
            throw new IllegalArgumentException("状态内容过长");
        }
        String idCard = normalizeIdCard(p.getIdCard());
        p.setIdCard(idCard);
        // 未修改的旧身份证允许照常编辑，新增/变更必须通过完整校验。
        if (!idCard.isEmpty() && (old == null || !idCard.equals(normalizeIdCard(old.getIdCard())))) {
            validateIdCard(idCard);
        }
        if (!idCard.isEmpty() && personnelDao.existsOtherIdCard(idCard, p.getId())) {
            throw new IllegalArgumentException("身份证号已存在");
        }
        Long departmentId = p.getDepartmentId();
        if (departmentId == null || departmentId <= 0) {
            throw new IllegalArgumentException("单位不能为空");
        }
        if (departmentId < 0 || (departmentId > 0 && departmentDao.findById(departmentId) == null
                && (old == null || !departmentId.equals(old.getDepartmentId())))) {
            throw new IllegalArgumentException("所选单位不存在");
        }
        validateActualDuty(p);
    }

    /**
     * 实际在岗字段独立校验:实际单位只要求真实存在(不要求在调用者数据范围内,
     * 借岗单位本就可能属于上级或兄弟单位)。
     */
    private void validateActualDuty(Personnel p) {
        Long actualDepartmentId = p.getActualDepartmentId();
        if (actualDepartmentId == null || actualDepartmentId <= 0) {
            p.setActualDepartmentId(0L); // null/<=0 归一存 0,统一表示未分离
        } else {
            Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM department WHERE id=?", Long.class, actualDepartmentId);
            if (exists == null || exists == 0) {
                throw new IllegalArgumentException("实际单位不存在");
            }
        }
    }

    /** 编辑模式必填:历史值非空的字段仍必填;历史本就为空的字段允许继续为空(旧数据渐进完善),新增(old==null)保持强制 */
    private static void requireFilled(String value, String oldValue, String label) {
        if (!StringUtils.hasText(value) && (oldValue == null || StringUtils.hasText(oldValue))) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }

    private static void requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }

    private static String normalizeIdCard(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static void validateIdCard(String idCard) {
        if (!idCard.matches("[0-9]{17}[0-9X]|[0-9]{15}")) {
            throw new IllegalArgumentException("身份证号格式不正确");
        }
        String birthday = idCard.length() == 18 ? idCard.substring(6, 14) : "19" + idCard.substring(6, 12);
        try {
            java.time.LocalDate birth = java.time.LocalDate.parse(birthday, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            if (birth.isAfter(java.time.LocalDate.now())) {
                throw new IllegalArgumentException("身份证出生日期不能晚于今天");
            }
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("身份证出生日期不正确");
        }
        if (idCard.length() == 18) {
            int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
            int sum = 0;
            for (int i = 0; i < weights.length; i++) {
                sum += (idCard.charAt(i) - '0') * weights[i];
            }
            if ("10X98765432".charAt(sum % 11) != idCard.charAt(17)) {
                throw new IllegalArgumentException("身份证号校验码不正确");
            }
        }
    }

    /** 按单位名称查找或自动创建(仅用于导入) */
    private Long resolveDepartmentId(String departmentName) {
        String name = departmentName.trim();
        for (Department d : departmentDao.findAll()) {
            if (name.equals(d.getName())) {
                return d.getId();
            }
        }
        DataScope.requireAdmin();
        Department d = new Department();
        d.setName(name);
        d.setParentId(0L);
        d.setSort(0);
        return departmentDao.insert(d);
    }

    /** 保存 base64 照片,返回文件名;无照片返回 null */    private String savePhotoIfAny(String photoBase64) {
        if (!StringUtils.hasText(photoBase64)) {
            return null;
        }
        String base64 = photoBase64;
        if (base64.startsWith("data:")) {
            base64 = base64.substring(base64.indexOf(',') + 1);
        }
        // 100MB 为运行时防护上限,不是照片业务尺寸限制;避免超大 Base64 请求耗尽内存
        if (base64.length() > 140 * 1024 * 1024) {
            throw new IllegalArgumentException("照片请求过大,单张图片最多 100MB");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (Exception e) {
            throw new IllegalArgumentException("照片数据格式错误");
        }
        if (bytes.length > 100 * 1024 * 1024) {
            throw new IllegalArgumentException("照片请求过大,单张图片最多 100MB");
        }
        String ext = guessExt(bytes);
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        photoService.save(fileName, bytes);
        return fileName;
    }

    private String guessExt(byte[] head) {
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8) {
            return ".jpg";
        }
        if (head.length >= 8 && head[0] == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) {
            return ".png";
        }
        return ".jpg";
    }
}
