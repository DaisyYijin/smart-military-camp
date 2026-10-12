package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.model.Personnel;
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
            p.setBirthday(rs.getString("birthday"));
            p.setPhone(rs.getString("phone"));
            p.setDepartmentId(rs.getLong("department_id"));
            p.setDepartmentName(rs.getString("department_name"));
            p.setRank(rs.getString("rank"));
            p.setPost(rs.getString("post"));
            p.setStatus(rs.getString("status"));
            p.setEntryDate(rs.getString("entry_date"));
            p.setAddress(rs.getString("address"));
            p.setPhoto(rs.getString("photo"));
            p.setRemark(rs.getString("remark"));
            p.setBloodType(rs.getString("blood_type"));
            p.setNation(rs.getString("nation"));
            p.setPoliticalStatus(rs.getString("political_status"));
            p.setPartyJoinDate(rs.getString("party_join_date"));
            p.setMaritalStatus(rs.getString("marital_status"));
            p.setEducation(rs.getString("education"));
            p.setOnlyChild(rs.getString("only_child"));
            p.setNativePlace(rs.getString("native_place"));
            p.setSpouseName(rs.getString("spouse_name"));
            p.setSpouseWork(rs.getString("spouse_work"));
            p.setSpousePhone(rs.getString("spouse_phone"));
            p.setSpouseIdCard(rs.getString("spouse_id_card"));
            p.setSpouseJob(rs.getString("spouse_job"));
            p.setMarriageDate(rs.getString("marriage_date"));
            p.setHeight(rs.getObject("height") == null ? null : rs.getDouble("height"));
            p.setWeight(rs.getObject("weight") == null ? null : rs.getDouble("weight"));
            p.setCreatedAt(rs.getString("created_at"));
            p.setUpdatedAt(rs.getString("updated_at"));
            return p;
        }
    };

    private static final String BASE_SELECT =
            "SELECT p.*, IFNULL(d.name, '') AS department_name FROM personnel p " +
            "LEFT JOIN department d ON p.department_id = d.id ";

    /**
     * 分页查询
     */
    public List<Personnel> query(String keyword, Long departmentId, String status,
                                 String gender, String politicalStatus, String rank, String education,
                                 int page, int size) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, keyword, departmentId, status, gender, politicalStatus, rank, education);
        sql.append(" ORDER BY p.updated_at DESC, p.id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add((page - 1) * size);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public long count(String keyword, Long departmentId, String status, String gender, String politicalStatus, String rank, String education) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM personnel p WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, keyword, departmentId, status, gender, politicalStatus, rank, education);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private void appendFilter(StringBuilder sql, List<Object> args,
                              String keyword, Long departmentId, String status,
                              String gender, String politicalStatus, String rank, String education) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = "%" + keyword.trim() + "%";
            sql.append(" AND (p.name LIKE ? OR p.id_card LIKE ? OR p.phone LIKE ? OR p.post LIKE ?) ");
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (departmentId != null && departmentId > 0) {
            sql.append(" AND p.department_id = ? ");
            args.add(departmentId);
        }
        if (gender != null && !gender.trim().isEmpty()) {
            sql.append(" AND p.gender = ? ");
            args.add(gender.trim());
        }
        if (politicalStatus != null && !politicalStatus.trim().isEmpty()) {
            sql.append(" AND p.political_status = ? ");
            args.add(politicalStatus.trim());
        }
        if (rank != null && !rank.trim().isEmpty()) {
            sql.append(" AND p.rank = ? ");
            args.add(rank.trim());
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
        List<Personnel> list = jdbc.query(BASE_SELECT + " WHERE p.id = ?", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 按身份证号查(用于导入时判断新增或更新) */
    public Personnel findByIdCard(String idCard) {
        if (idCard == null || idCard.trim().isEmpty()) {
            return null;
        }
        List<Personnel> list = jdbc.query(BASE_SELECT + " WHERE p.id_card = ?", MAPPER, idCard.trim());
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(Personnel p) {
        return jdbc.queryForObject(
                "INSERT INTO personnel(name, gender, id_card, birthday, phone, department_id, rank, post, status, entry_date, address, photo, remark, " +
                "blood_type, nation, political_status, party_join_date, marital_status, education, only_child, native_place, height, weight, spouse_name, spouse_work, spouse_phone, spouse_id_card, spouse_job, marriage_date, party_org_location, party_post, party_remark, divorce_date, marriage_remark) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id",
                Long.class,
                nvl(p.getName()), nvl(p.getGender()), nvl(p.getIdCard()), nvl(p.getBirthday()),
                nvl(p.getPhone()), p.getDepartmentId(), nvl(p.getRank()), nvl(p.getPost()),
                nvl(p.getStatus()), nvl(p.getEntryDate()), nvl(p.getAddress()), nvl(p.getPhoto()),
                nvl(p.getRemark()),
                nvl(p.getBloodType()), nvl(p.getNation()), nvl(p.getPoliticalStatus()),
                nvl(p.getPartyJoinDate()),
                nvl(p.getMaritalStatus()), nvl(p.getEducation()), nvl(p.getOnlyChild()),
                nvl(p.getNativePlace()), p.getHeight(), p.getWeight(),
                nvl(p.getSpouseName()), nvl(p.getSpouseWork()), nvl(p.getSpousePhone()),
                nvl(p.getSpouseIdCard()), nvl(p.getSpouseJob()), nvl(p.getMarriageDate()));
    }

    public void update(Personnel p) {
        jdbc.update(
                "UPDATE personnel SET name=?, gender=?, id_card=?, birthday=?, phone=?, department_id=?, " +
                "rank=?, post=?, status=?, entry_date=?, address=?, photo=?, remark=?, " +
                "blood_type=?, nation=?, political_status=?, party_join_date=?, marital_status=?, education=?, only_child=?, " +
                "native_place=?, height=?, weight=?, spouse_name=?, spouse_work=?, spouse_phone=?, spouse_id_card=?, spouse_job=?, marriage_date=?, party_org_location=?, party_post=?, party_remark=?, divorce_date=?, marriage_remark=?, party_apply_date=?, party_activist_date=?, party_candidate_date=?, party_full_date=?, follow_army_date=?, follow_army_status=?, updated_at=datetime('now','localtime') " +
                "WHERE id=?",
                nvl(p.getName()), nvl(p.getGender()), nvl(p.getIdCard()), nvl(p.getBirthday()),
                nvl(p.getPhone()), p.getDepartmentId(), nvl(p.getRank()), nvl(p.getPost()),
                nvl(p.getStatus()), nvl(p.getEntryDate()), nvl(p.getAddress()), nvl(p.getPhoto()),
                nvl(p.getRemark()),
                nvl(p.getBloodType()), nvl(p.getNation()), nvl(p.getPoliticalStatus()),
                nvl(p.getPartyJoinDate()),
                nvl(p.getMaritalStatus()), nvl(p.getEducation()), nvl(p.getOnlyChild()),
                nvl(p.getNativePlace()), p.getHeight(), p.getWeight(),
                nvl(p.getSpouseName()), nvl(p.getSpouseWork()), nvl(p.getSpousePhone()),
                nvl(p.getSpouseIdCard()), nvl(p.getSpouseJob()), nvl(p.getMarriageDate()),
                p.getId());
    }

    public void updatePhoto(Long id, String photo) {
        jdbc.update("UPDATE personnel SET photo=?, updated_at=datetime('now','localtime') WHERE id=?",
                photo, id);
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM personnel WHERE id=?", id);
    }

    public long countByDepartment(Long departmentId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM personnel WHERE department_id=?",
                Long.class, departmentId);
        return n == null ? 0 : n;
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM personnel", Long.class);
        return n == null ? 0 : n;
    }

    /** 全部人员精简列表(供下拉选择,不分页) */
    public List<Map<String, Object>> findAllSimple() {
        return jdbc.queryForList(
                "SELECT p.id, p.name, p.rank, p.post, IFNULL(d.name,'') AS department_name " +
                "FROM personnel p LEFT JOIN department d ON p.department_id=d.id ORDER BY p.id");
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
