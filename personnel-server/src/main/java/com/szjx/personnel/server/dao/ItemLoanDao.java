package com.szjx.personnel.server.dao;

import com.szjx.personnel.server.util.Db;
import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.model.ItemLoan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ItemLoanDao {
    private final JdbcTemplate jdbc;
    private static final String FROM = " FROM item_loan l ";
    private static final String UNRETURNED = "COALESCE(returned,0)=0";
    private static final RowMapper<ItemLoan> MAPPER = (rs, row) -> {
        ItemLoan loan = new ItemLoan();
        loan.setId(rs.getLong("id"));
        loan.setItemName(rs.getString("item_name"));
        loan.setSpecification(rs.getString("specification"));
        String quantity = rs.getString("quantity");
        loan.setQuantity(quantity == null ? null : new BigDecimal(quantity));
        loan.setUnit(rs.getString("unit"));
        loan.setBorrowerName(rs.getString("borrower_name"));
        loan.setBorrowerIdCard(rs.getString("borrower_id_card"));
        loan.setBorrowerPhone(rs.getString("borrower_phone"));
        loan.setBorrowerUnit(rs.getString("borrower_unit"));
        loan.setRegistrationDepartment(rs.getString("registration_department"));
        loan.setPersonnelId(rs.getLong("personnel_id"));
        loan.setPersonnelName(loan.getBorrowerName());
        loan.setHandlerId(rs.getLong("handler_id"));
        loan.setHandlerName(rs.getString("handler_name"));
        loan.setHandlerIdCard(rs.getString("handler_id_card"));
        loan.setOwnerUserId(rs.getLong("owner_user_id"));
        loan.setBorrowDate(rs.getString("borrow_date"));
        loan.setReturnCondition(rs.getString("return_condition"));
        loan.setReturnRemark(rs.getString("return_remark"));
        loan.setRemark(rs.getString("remark"));
        loan.setStatus(rs.getString("loan_status"));
        loan.setCreatedAt(rs.getString("created_at"));
        loan.setUpdatedAt(rs.getString("updated_at"));
        return loan;
    };

    public ItemLoanDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 仪表盘提醒:未归还且已超期或窗口内到期的借用;可见性与列表一致(owner 账号范围)。 */
    public List<Map<String, Object>> dueReminders(String windowEnd) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT l.item_name, l.borrower_name, l.expected_return_date FROM item_loan l WHERE "
                + visibility("l.owner_user_id");
        addScope(args);
        sql += " AND COALESCE(l.returned,0)=0 AND l.expected_return_date LIKE '____-__-__' AND l.expected_return_date <= ?"
                + " ORDER BY l.expected_return_date LIMIT 30";
        args.add(windowEnd);
        return jdbc.queryForList(sql, args.toArray());
    }

    private boolean restricted() { return DataScope.restricted(); }

    /** UNION (not UNION ALL) makes account traversal terminate even if corrupt parent links contain a cycle. */
    private String visibility(String ownerColumn) {
        if (!restricted()) return "1=1";
        return ownerColumn + " IN (WITH RECURSIVE visible_users(id) AS (" +
                "SELECT ? UNION SELECT child.id FROM admin_user child JOIN visible_users parent ON child.parent_id=parent.id) " +
                "SELECT id FROM visible_users)";
    }

    private void addScope(List<Object> args) {
        if (restricted()) args.add(DataScope.userId());
    }

    private String select() {
        return "SELECT l.*,CASE WHEN COALESCE(l.returned,0)=1 THEN 'returned' ELSE 'borrowing' END AS loan_status" + FROM;
    }

    private String filter(List<Object> args, String itemName, String borrowerName,
                          String registrationDepartment, String status, LocalDate today) {
        StringBuilder sql = new StringBuilder(" WHERE ").append(visibility("l.owner_user_id"));
        addScope(args);
        if (itemName != null && !itemName.isEmpty()) { sql.append(" AND l.item_name LIKE ? ESCAPE '\\'"); args.add(like(itemName)); }
        if (borrowerName != null && !borrowerName.isEmpty()) { sql.append(" AND l.borrower_name LIKE ? ESCAPE '\\'"); args.add(like(borrowerName)); }
        if (registrationDepartment != null && !registrationDepartment.isEmpty()) { sql.append(" AND l.registration_department LIKE ? ESCAPE '\\'"); args.add(like(registrationDepartment)); }
        if ("returned".equals(status)) sql.append(" AND COALESCE(l.returned,0)=1");
        else if ("borrowing".equals(status)) sql.append(" AND ").append(UNRETURNED);
        return sql.toString();
    }

    private String like(String text) { return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; }

    public List<ItemLoan> query(String itemName, String borrowerName, String registrationDepartment,
                                String status, int page, int size, LocalDate today) {
        List<Object> args = new ArrayList<Object>();
        String sql = select() + filter(args, itemName, borrowerName, registrationDepartment, status, today) +
                " ORDER BY l.updated_at DESC,l.id DESC LIMIT ? OFFSET ?";
        args.add(size); args.add(((long) page - 1) * size);
        return jdbc.query(sql, MAPPER, args.toArray());
    }

    public long count(String itemName, String borrowerName, String registrationDepartment, String status, LocalDate today) {
        List<Object> args = new ArrayList<Object>();
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + FROM +
                filter(args, itemName, borrowerName, registrationDepartment, status, today), Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private void requireRecord(Long id) {
        if (!restricted()) return;
        List<Long> owners = jdbc.queryForList("SELECT owner_user_id FROM item_loan WHERE id=? AND " + visibility("owner_user_id"),
                Long.class, id, DataScope.userId());
        if (owners.size() != 1) throw new ForbiddenException("无权访问该记录");
    }

    public ItemLoan findById(Long id, LocalDate today) {
        List<Object> args = new ArrayList<Object>(); args.add(id);
        String scope = visibility("l.owner_user_id");
        if (restricted()) args.add(DataScope.userId());
        List<ItemLoan> loans = jdbc.query(select() + " WHERE l.id=? AND " + scope, MAPPER, args.toArray());
        if (loans.isEmpty() && restricted()) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM item_loan WHERE id=?", Integer.class, id);
            if (exists != null && exists > 0) throw new ForbiddenException("无权访问该记录");
        }
        return loans.isEmpty() ? null : loans.get(0);
    }

    private long ownerForInsert() {
        DataScope.Context context = DataScope.get();
        if (context == null) return 0L;
        Long userId = context.userId;
        if (userId == null || userId <= 0) throw new ForbiddenException("当前账号无法创建记录");
        return userId;
    }

    public Map<String, String> findHandlerSnapshot(Long id) {
        DataScope.requirePerson(jdbc, id);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT name,id_card FROM personnel WHERE id=? AND " +
                DataScope.departmentSql("department_id"), id);
        if (rows.size() != 1) throw new IllegalArgumentException("经手人人员不存在或不可选择");
        Map<String, Object> row = rows.get(0);
        Map<String, String> snapshot = new LinkedHashMap<String, String>();
        snapshot.put("name", row.get("name") == null ? "" : String.valueOf(row.get("name")));
        snapshot.put("idCard", row.get("id_card") == null ? "" : String.valueOf(row.get("id_card")));
        return snapshot;
    }

    public long insert(ItemLoan loan) {
        long owner = ownerForInsert();
        // Legacy personnel_id and department_id remain stored for migration/backup compatibility only.
        return Db.id(jdbc, "INSERT INTO item_loan(item_name,specification,quantity,unit,personnel_id," +
                        "borrower_name,borrower_id_card,borrower_phone,borrower_unit,registration_department,department_id,borrower_migrated," +
                        "borrow_date,remark,owner_user_id,handler_id,handler_name,handler_id_card) VALUES(?,?,?,?,0,?,?,?,?,?,0,1,?,?,?,?,?,?) RETURNING id",
                loan.getItemName(), loan.getSpecification(), loan.getQuantity().toPlainString(), loan.getUnit(),
                loan.getBorrowerName(), loan.getBorrowerIdCard(), loan.getBorrowerPhone(), loan.getBorrowerUnit(),
                loan.getRegistrationDepartment(), loan.getBorrowDate(), loan.getRemark(), owner,
                loan.getHandlerId(), loan.getHandlerName(), loan.getHandlerIdCard());
    }

    public int update(Long id, Long originalOwner, ItemLoan loan) {
        requireRecord(id);
        List<Object> args = new ArrayList<Object>(java.util.Arrays.asList(loan.getItemName(), loan.getSpecification(),
                loan.getQuantity().toPlainString(), loan.getUnit(), loan.getBorrowerName(), loan.getBorrowerIdCard(),
                loan.getBorrowerPhone(), loan.getBorrowerUnit(), loan.getRegistrationDepartment(), loan.getBorrowDate(),
                loan.getRemark(), loan.getHandlerId(), loan.getHandlerName(), loan.getHandlerIdCard(), id, originalOwner));
        String scope = visibility("owner_user_id"); addScope(args);
        return jdbc.update("UPDATE item_loan SET item_name=?,specification=?,quantity=?,unit=?,personnel_id=0," +
                "borrower_name=?,borrower_id_card=?,borrower_phone=?,borrower_unit=?,registration_department=?,borrower_migrated=1," +
                "borrow_date=?,remark=?,handler_id=?,handler_name=?,handler_id_card=?,updated_at=datetime('now','localtime') " +
                "WHERE id=? AND owner_user_id=? AND " + UNRETURNED + " AND " + scope, args.toArray());
    }

    public int returnLoan(ItemLoan original, String condition, String remark) {
        requireRecord(original.getId());
        List<Object> args = new ArrayList<Object>(java.util.Arrays.asList(condition, remark,
                original.getId(), original.getOwnerUserId(), original.getBorrowDate()));
        String scope = visibility("owner_user_id"); addScope(args);
        return jdbc.update("UPDATE item_loan SET returned=1,return_condition=?,return_remark=?," +
                "updated_at=datetime('now','localtime') WHERE id=? AND owner_user_id=? AND borrow_date=? AND " +
                UNRETURNED + " AND " + scope, args.toArray());
    }

    public int delete(Long id, Long originalOwner) {
        requireRecord(id);
        List<Object> args = new ArrayList<Object>(java.util.Arrays.asList(id, originalOwner));
        String scope = visibility("owner_user_id"); addScope(args);
        return jdbc.update("DELETE FROM item_loan WHERE id=? AND owner_user_id=? AND " + scope, args.toArray());
    }
}
