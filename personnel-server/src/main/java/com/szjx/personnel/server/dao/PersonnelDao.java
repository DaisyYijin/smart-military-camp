package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.util.Db;
import com.szjx.personnel.server.auth.DataScope;

import com.szjx.personnel.server.model.Personnel;
import com.szjx.personnel.server.util.DepartmentHierarchy;
import com.szjx.personnel.server.util.PersonnelExtraFilters;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 人员数据访问
 */
@Repository
public class PersonnelDao {

    private final JdbcTemplate jdbc;

    public PersonnelDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Personnel> MAPPER = new RowMapper<Personnel>() {
        @Override
        public Personnel mapRow(ResultSet rs, int i) throws SQLException {
            Personnel p = new Personnel();
            p.setId(rs.getLong("id"));
            p.setName(rs.getString("name"));
            p.setGender(rs.getString("gender"));
            p.setIdCard(rs.getString("id_card"));
            p.setMilitaryIdNo(rs.getString("military_id_no"));
            p.setBirthday(rs.getString("birthday"));
            p.setPhone(rs.getString("phone"));
            p.setDepartmentId(rs.getLong("department_id"));
            p.setDepartmentName(rs.getString("department_name"));
            p.setActualDepartmentName(rs.getString("actual_department_name"));
            p.setRank(rs.getString("rank"));
            p.setCategory(rs.getString("category"));
            p.setPost(rs.getString("post"));
            p.setPostDate(rs.getString("post_date"));
            p.setActualDepartmentId(rs.getObject("actual_department_id") == null ? null : rs.getLong("actual_department_id"));
            p.setStatus(rs.getString("status"));
            p.setEntryDate(rs.getString("entry_date"));
            p.setEquivEntryDate(rs.getString("equiv_entry_date"));
            p.setAddress(rs.getString("address"));
            p.setPhoto(rs.getString("photo"));
            p.setRemark(rs.getString("remark"));
            p.setBloodType(rs.getString("blood_type"));
            p.setNation(rs.getString("nation"));
            p.setPoliticalStatus(rs.getString("political_status"));
            p.setPartyJoinDate(rs.getString("party_join_date"));
            p.setMaritalStatus(rs.getString("marital_status"));
            p.setEducation(rs.getString("education"));
            p.setEduEnrollDate(rs.getString("edu_enroll_date"));
            p.setEduGraduateDate(rs.getString("edu_graduate_date"));
            p.setEduSchool(rs.getString("edu_school"));
            p.setEduMajor(rs.getString("edu_major"));
            p.setEduDegree(rs.getString("edu_degree"));
            p.setEduForm(rs.getString("edu_form"));
            p.setEduLength(rs.getString("edu_length"));
            p.setEduCertNo(rs.getString("edu_cert_no"));
            p.setOnlyChild(rs.getString("only_child"));
            p.setNativePlace(rs.getString("native_place"));
            p.setEnlistPlace(rs.getString("enlist_place"));
            p.setLocationPoint(rs.getString("location_point"));
            p.setSpouseName(rs.getString("spouse_name"));
            p.setSpouseWork(rs.getString("spouse_work"));
            p.setSpousePhone(rs.getString("spouse_phone"));
            p.setSpouseIdCard(rs.getString("spouse_id_card"));
            p.setSpouseJob(rs.getString("spouse_job"));
            p.setMarriageDate(rs.getString("marriage_date"));
            p.setPartyOrgLocation(rs.getString("party_org_location"));
            p.setPartyPost(rs.getString("party_post"));
            p.setPartyRemark(rs.getString("party_remark"));
            p.setDivorceDate(rs.getString("divorce_date"));
            p.setMarriageRemark(rs.getString("marriage_remark"));
            p.setPartyApplyDate(rs.getString("party_apply_date"));
            p.setPartyActivistDate(rs.getString("party_activist_date"));
            p.setPartyCandidateDate(rs.getString("party_candidate_date"));
            p.setPartyFullDate(rs.getString("party_full_date"));
            p.setFollowArmyDate(rs.getString("follow_army_date"));
            p.setFollowArmyStatus(rs.getString("follow_army_status"));
            p.setFollowArmy(rs.getString("follow_army"));
            p.setHeight(rs.getObject("height") == null ? null : rs.getDouble("height"));
            p.setWeight(rs.getObject("weight") == null ? null : rs.getDouble("weight"));
            p.setCreatedAt(rs.getString("created_at"));
            p.setUpdatedAt(rs.getString("updated_at"));
            return p;
        }
    };

    private static final String BASE_SELECT =
            "SELECT p.*, COALESCE(d.name, '') AS department_name, COALESCE(ad.name, '') AS actual_department_name FROM personnel p " +
            "LEFT JOIN department d ON p.department_id = d.id " +
            "LEFT JOIN department ad ON p.actual_department_id = ad.id ";

    /**
     * 分页查询
     */
    public List<Personnel> query(String keyword, String name, String idCard, Long departmentId, String status,
                                 String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education,
                                 String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint,
                                 String entryDateStart, String entryDateEnd,
                                 int page, int size) {
        return query(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, locationPoint, entryDateStart, entryDateEnd, page, size, false);
    }

    public List<Personnel> query(String keyword, String name, String idCard, Long departmentId, String status,
                                 String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education,
                                 String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint,
                                 String entryDateStart, String entryDateEnd,
                                 int page, int size, boolean includeDescendants) {
        return query(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, locationPoint, entryDateStart, entryDateEnd, page, size, includeDescendants, PersonnelExtraFilters.empty());
    }

    public List<Personnel> query(String keyword, String name, String idCard, Long departmentId, String status,
                                 String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education,
                                 String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint,
                                 String entryDateStart, String entryDateEnd,
                                 int page, int size, boolean includeDescendants, PersonnelExtraFilters extraFilters) {
        DepartmentHierarchy hierarchy = DepartmentHierarchy.load(jdbc);
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, locationPoint, entryDateStart, entryDateEnd, includeDescendants, hierarchy);
        extraFilters.appendTo(sql, args);
        sql.append(" ORDER BY p.updated_at DESC, p.id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add((long) (page - 1) * size);
        return withPaths(jdbc.query(sql.toString(), MAPPER, args.toArray()), hierarchy);
    }

    public long count(String keyword, String name, String idCard, Long departmentId, String status, String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education, String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint, String entryDateStart, String entryDateEnd) {
        return count(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, locationPoint, entryDateStart, entryDateEnd, false);
    }

    public long count(String keyword, String name, String idCard, Long departmentId, String status, String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education, String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint, String entryDateStart, String entryDateEnd, boolean includeDescendants) {
        return count(keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, locationPoint, entryDateStart, entryDateEnd, includeDescendants, PersonnelExtraFilters.empty());
    }

    public long count(String keyword, String name, String idCard, Long departmentId, String status, String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education, String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint, String entryDateStart, String entryDateEnd, boolean includeDescendants, PersonnelExtraFilters extraFilters) {
        DepartmentHierarchy hierarchy = includeDescendants ? DepartmentHierarchy.load(jdbc) : null;
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM personnel p WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, keyword, name, idCard, departmentId, status, gender, politicalStatus, category, rank, post, nativePlace, enlistPlace, education, maritalStatus, nation, bloodType, onlyChild, locationPoint, entryDateStart, entryDateEnd, includeDescendants, hierarchy);
        extraFilters.appendTo(sql, args);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private void appendFilter(StringBuilder sql, List<Object> args,
                              String keyword, String name, String idCard, Long departmentId, String status,
                              String gender, String politicalStatus, String category, String rank, String post, String nativePlace, String enlistPlace, String education,
                              String maritalStatus, String nation, String bloodType, String onlyChild, String locationPoint,
                              String entryDateStart, String entryDateEnd, boolean includeDescendants,
                              DepartmentHierarchy hierarchy) {
        sql.append(" AND ").append(DataScope.departmentSql("p.department_id"));
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = "%" + keyword.trim() + "%";
            sql.append(" AND (p.name LIKE ? OR p.id_card LIKE ? OR p.phone LIKE ? OR p.post LIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (name != null && !name.trim().isEmpty()) {
            sql.append(" AND p.name LIKE ? ");
            args.add("%" + name.trim() + "%");
        }
        if (idCard != null && !idCard.trim().isEmpty()) {
            sql.append(" AND p.id_card LIKE ? ");
            args.add("%" + idCard.trim() + "%");
        }
        if (departmentId != null && departmentId > 0) {
            if (includeDescendants) {
                java.util.Set<Long> ids = hierarchy.descendantIds(departmentId);
                if (ids.isEmpty()) {
                    sql.append(" AND 1=0 ");
                } else {
                    sql.append(" AND p.department_id IN (");
                    int index = 0;
                    for (Long id : ids) {
                        if (index++ > 0) sql.append(',');
                        sql.append('?');
                        args.add(id);
                    }
                    sql.append(") ");
                }
            } else {
                sql.append(" AND p.department_id = ? ");
                args.add(departmentId);
            }
        }
        if (maritalStatus != null && !maritalStatus.trim().isEmpty()) {
            sql.append(" AND p.marital_status = ? ");
            args.add(maritalStatus.trim());
        }
        if (nation != null && !nation.trim().isEmpty()) {
            sql.append(" AND p.nation = ? ");
            args.add(nation.trim());
        }
        if (bloodType != null && !bloodType.trim().isEmpty()) {
            sql.append(" AND p.blood_type = ? ");
            args.add(bloodType.trim());
        }
        if (onlyChild != null && !onlyChild.trim().isEmpty()) {
            sql.append(" AND p.only_child = ? ");
            args.add(onlyChild.trim());
        }
        if (entryDateStart != null && !entryDateStart.trim().isEmpty()) {
            sql.append(" AND p.entry_date >= ? ");
            args.add(entryDateStart.trim());
        }
        if (entryDateEnd != null && !entryDateEnd.trim().isEmpty()) {
            sql.append(" AND p.entry_date <= ? ");
            args.add(entryDateEnd.trim());
        }
        if (gender != null && !gender.trim().isEmpty()) {
            sql.append(" AND p.gender = ? ");
            args.add(gender.trim());
        }
        if (politicalStatus != null && !politicalStatus.trim().isEmpty()) {
            sql.append(" AND p.political_status = ? ");
            args.add(politicalStatus.trim());
        }
        if (category != null && !category.trim().isEmpty()) {
            sql.append(" AND p.category = ? ");
            args.add(category.trim());
        }
        if (rank != null && !rank.trim().isEmpty()) {
            sql.append(" AND p.rank = ? ");
            args.add(rank.trim());
        }
        if (post != null && !post.trim().isEmpty()) {
            sql.append(" AND p.post LIKE ? ");
            args.add("%" + post.trim() + "%");
        }
        if (nativePlace != null && !nativePlace.trim().isEmpty()) {
            sql.append(" AND p.native_place LIKE ? ");
            args.add("%" + nativePlace.trim() + "%");
        }
        if (enlistPlace != null && !enlistPlace.trim().isEmpty()) {
            sql.append(" AND p.enlist_place LIKE ? ");
            args.add("%" + enlistPlace.trim() + "%");
        }
        if (locationPoint != null && !locationPoint.trim().isEmpty()) {
            sql.append(" AND p.location_point = ? ");
            args.add(locationPoint.trim());
        }
        if (education != null && !education.trim().isEmpty()) {
            sql.append(" AND p.education = ? ");
            args.add(education.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND p.status = ? ");
            args.add(status.trim());
        }
    }

    public Personnel findById(Long id) {
        DataScope.requirePerson(jdbc, id);
        List<Personnel> list = jdbc.query(BASE_SELECT + " WHERE p.id = ?", MAPPER, id);
        return list.isEmpty() ? null : withPaths(list, DepartmentHierarchy.load(jdbc)).get(0);
    }

    /** 按身份证号查(用于导入时判断新增或更新) */
    public Personnel findByIdCard(String idCard) {
        if (idCard == null || idCard.trim().isEmpty()) {
            return null;
        }
        List<Personnel> list = jdbc.query(BASE_SELECT + " WHERE UPPER(TRIM(p.id_card)) = ? AND " + DataScope.departmentSql("p.department_id"), MAPPER,
                idCard.trim().toUpperCase(java.util.Locale.ROOT));
        return list.isEmpty() ? null : withPaths(list, DepartmentHierarchy.load(jdbc)).get(0);
    }

    /** 空身份证不参与唯一性检查，兼容旧值大小写和两端空格。 */
    public boolean existsOtherIdCard(String idCard, Long excludeId) {
        if (idCard == null || idCard.trim().isEmpty()) {
            return false;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE UPPER(TRIM(id_card))=? " +
                        "AND (? IS NULL OR id<>?)", Long.class,
                idCard.trim().toUpperCase(java.util.Locale.ROOT), excludeId, excludeId);
        return count != null && count > 0;
    }

    /** 手机设备为人员电话的唯一来源。 */
    public String devicePhone(Long id) {
        DataScope.requirePerson(jdbc, id);
        List<String> phones = jdbc.queryForList("SELECT TRIM(phone_number) FROM phone_device WHERE personnel_id=? " +
                "AND TRIM(COALESCE(phone_number,''))<>'' ORDER BY id DESC LIMIT 1", String.class, id);
        return phones.isEmpty() ? "" : phones.get(0);
    }

    public long insert(Personnel p) {
        DataScope.requireDepartment(p.getDepartmentId());
        return Db.id(jdbc, "INSERT INTO personnel(name, gender, id_card, military_id_no, birthday, phone, department_id, category, rank, post, post_date, actual_department_id, status, entry_date, equiv_entry_date, address, photo, remark, " +
                "blood_type, nation, political_status, party_join_date, marital_status, education, edu_enroll_date, edu_graduate_date, edu_school, edu_major, edu_degree, edu_form, edu_length, edu_cert_no, only_child, native_place, enlist_place, location_point, height, weight, spouse_name, spouse_work, spouse_phone, spouse_id_card, spouse_job, marriage_date, party_org_location, party_post, party_remark, divorce_date, marriage_remark, party_apply_date, party_activist_date, party_candidate_date, party_full_date, follow_army_date, follow_army_status, follow_army) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id",
                nvl(p.getName()), nvl(p.getGender()), nvl(p.getIdCard()), nvl(p.getMilitaryIdNo()), nvl(p.getBirthday()),
                nvl(p.getPhone()), p.getDepartmentId(), nvl(p.getCategory()), nvl(p.getRank()), nvl(p.getPost()), nvl(p.getPostDate()),
                p.getActualDepartmentId(),
                nvl(p.getStatus()), nvl(p.getEntryDate()), nvl(p.getEquivEntryDate()), nvl(p.getAddress()), nvl(p.getPhoto()),
                nvl(p.getRemark()),
                nvl(p.getBloodType()), nvl(p.getNation()), nvl(p.getPoliticalStatus()),
                nvl(p.getPartyJoinDate()),
                nvl(p.getMaritalStatus()), nvl(p.getEducation()), nvl(p.getEduEnrollDate()), nvl(p.getEduGraduateDate()),
                nvl(p.getEduSchool()), nvl(p.getEduMajor()), nvl(p.getEduDegree()), nvl(p.getEduForm()), nvl(p.getEduLength()), nvl(p.getEduCertNo()), nvl(p.getOnlyChild()),
                nvl(p.getNativePlace()), nvl(p.getEnlistPlace()), nvl(p.getLocationPoint()), p.getHeight(), p.getWeight(),
                nvl(p.getSpouseName()), nvl(p.getSpouseWork()), nvl(p.getSpousePhone()),
                nvl(p.getSpouseIdCard()), nvl(p.getSpouseJob()), nvl(p.getMarriageDate()),
                nvl(p.getPartyOrgLocation()), nvl(p.getPartyPost()), nvl(p.getPartyRemark()),
                nvl(p.getDivorceDate()), nvl(p.getMarriageRemark()),
                nvl(p.getPartyApplyDate()), nvl(p.getPartyActivistDate()), nvl(p.getPartyCandidateDate()), nvl(p.getPartyFullDate()),
                nvl(p.getFollowArmyDate()), nvl(p.getFollowArmyStatus()), nvl(p.getFollowArmy()));
    }

    public void update(Personnel p) {
        DataScope.requirePerson(jdbc, p.getId());
        DataScope.requireDepartment(p.getDepartmentId());
        jdbc.update(
                "UPDATE personnel SET name=?, gender=?, id_card=?, military_id_no=?, birthday=?, phone=?, department_id=?, " +
                "category=?, rank=?, post=?, post_date=?, actual_department_id=?, status=?, entry_date=?, equiv_entry_date=?, address=?, photo=?, remark=?, " +
                "blood_type=?, nation=?, political_status=?, party_join_date=?, marital_status=?, education=?, edu_enroll_date=?, edu_graduate_date=?, edu_school=?, edu_major=?, edu_degree=?, edu_form=?, edu_length=?, edu_cert_no=?, only_child=?, " +
                "native_place=?, enlist_place=?, location_point=?, height=?, weight=?, spouse_name=?, spouse_work=?, spouse_phone=?, spouse_id_card=?, spouse_job=?, marriage_date=?, party_org_location=?, party_post=?, party_remark=?, divorce_date=?, marriage_remark=?, party_apply_date=?, party_activist_date=?, party_candidate_date=?, party_full_date=?, follow_army_date=?, follow_army_status=?, follow_army=?, updated_at=datetime('now','localtime') " +
                "WHERE id=?",
                nvl(p.getName()), nvl(p.getGender()), nvl(p.getIdCard()), nvl(p.getMilitaryIdNo()), nvl(p.getBirthday()),
                nvl(p.getPhone()), p.getDepartmentId(), nvl(p.getCategory()), nvl(p.getRank()), nvl(p.getPost()), nvl(p.getPostDate()),
                p.getActualDepartmentId(),
                nvl(p.getStatus()), nvl(p.getEntryDate()), nvl(p.getEquivEntryDate()), nvl(p.getAddress()), nvl(p.getPhoto()),
                nvl(p.getRemark()),
                nvl(p.getBloodType()), nvl(p.getNation()), nvl(p.getPoliticalStatus()),
                nvl(p.getPartyJoinDate()),
                nvl(p.getMaritalStatus()), nvl(p.getEducation()), nvl(p.getEduEnrollDate()), nvl(p.getEduGraduateDate()),
                nvl(p.getEduSchool()), nvl(p.getEduMajor()), nvl(p.getEduDegree()), nvl(p.getEduForm()), nvl(p.getEduLength()), nvl(p.getEduCertNo()), nvl(p.getOnlyChild()),
                nvl(p.getNativePlace()), nvl(p.getEnlistPlace()), nvl(p.getLocationPoint()), p.getHeight(), p.getWeight(),
                nvl(p.getSpouseName()), nvl(p.getSpouseWork()), nvl(p.getSpousePhone()),
                nvl(p.getSpouseIdCard()), nvl(p.getSpouseJob()), nvl(p.getMarriageDate()),
                nvl(p.getPartyOrgLocation()), nvl(p.getPartyPost()), nvl(p.getPartyRemark()),
                nvl(p.getDivorceDate()), nvl(p.getMarriageRemark()),
                nvl(p.getPartyApplyDate()), nvl(p.getPartyActivistDate()), nvl(p.getPartyCandidateDate()), nvl(p.getPartyFullDate()),
                nvl(p.getFollowArmyDate()), nvl(p.getFollowArmyStatus()), nvl(p.getFollowArmy()),
                p.getId());
    }

    public void updatePhoto(Long id, String photo) {
        DataScope.requirePerson(jdbc, id);
        jdbc.update("UPDATE personnel SET photo=?, updated_at=datetime('now','localtime') WHERE id=?",
                photo, id);
    }

    public void delete(Long id) {
        DataScope.requirePerson(jdbc, id);
        jdbc.update("DELETE FROM personnel WHERE id=?", id);
    }

    public long countByDepartment(Long departmentId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE department_id=? AND " + DataScope.departmentSql("department_id"),
                Long.class, departmentId);
        return n == null ? 0 : n;
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE " + DataScope.departmentSql("department_id"), Long.class);
        return n == null ? 0 : n;
    }

    /** 全部人员精简列表(供下拉选择,不分页) */
    public List<Map<String, Object>> findAllSimple() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.id, p.name, p.id_card, p.military_id_no, p.department_id, p.rank, p.post, COALESCE(d.name,'') AS department_name " +
                "FROM personnel p LEFT JOIN department d ON p.department_id=d.id WHERE " + DataScope.departmentSql("p.department_id") + " ORDER BY p.id");
        if (!rows.isEmpty()) {
            DepartmentHierarchy hierarchy = DepartmentHierarchy.load(jdbc);
            for (Map<String, Object> row : rows) {
                Object id = row.get("department_id");
                row.put("department_path", hierarchy.path(id == null ? null : ((Number) id).longValue()));
            }
        }
        return rows;
    }

    /** 同一次批量读取共用一棵纯名称树，只填充已通过权限查询的人员。 */
    private List<Personnel> withPaths(List<Personnel> rows, DepartmentHierarchy hierarchy) {
        for (Personnel p : rows) {
            p.setDepartmentPath(hierarchy.path(p.getDepartmentId()));
            // 实际在岗路径仅查询上下文:0/NULL=未分离,按编制单位在岗,无实际路径。
            Long actualDepartment = p.getActualDepartmentId();
            p.setActualDepartmentPath(actualDepartment == null || actualDepartment <= 0 ? null : hierarchy.path(actualDepartment));
            // 实际单位末级名:路径已无斜杠分隔,前端不可再按'/'拆分,由后端直接提供末级名
            if (actualDepartment != null && actualDepartment > 0) {
                DepartmentHierarchy.Node actualNode = hierarchy.get(actualDepartment);
                p.setActualDepartmentName(actualNode == null ? null : actualNode.getName());
            }
        }
        return rows;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
