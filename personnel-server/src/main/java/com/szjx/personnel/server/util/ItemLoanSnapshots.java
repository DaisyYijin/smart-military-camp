package com.szjx.personnel.server.util;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts personnel-linked historical loans into independent borrower snapshots. */
public final class ItemLoanSnapshots {
    private ItemLoanSnapshots() { }

    /** SchemaInitializer must add the snapshot columns before calling this method. */
    public static void migrateExisting(JdbcTemplate jdbc) {
        if (!tableExists(jdbc, "item_loan")) { return; }
        // The schema migration adds this column with DEFAULT 0; repair nullable legacy copies too.
        jdbc.update("UPDATE item_loan SET owner_user_id=0 WHERE owner_user_id IS NULL");
        // Deliberately fail on an existing, unupgraded table rather than hiding SQL errors.
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,personnel_id,borrower_name,borrower_id_card,borrower_phone,borrower_unit,department_id,borrower_migrated,registration_department " +
                        "FROM item_loan WHERE COALESCE(borrower_migrated,0)=0");
        if (rows.isEmpty()) { return; }
        List<Map<String, Object>> personnel = rows(jdbc, "personnel");
        List<Map<String, Object>> departments = rows(jdbc, "department");
        for (Map<String, Object> row : rows) {
            Map<String, Object> converted = convertLegacy(row, personnel, departments);
            jdbc.update("UPDATE item_loan SET borrower_name=?,borrower_id_card=?,borrower_phone=?,borrower_unit=?," +
                            "department_id=?,registration_department=?,personnel_id=0,borrower_migrated=1 WHERE id=? AND COALESCE(borrower_migrated,0)=0",
                    converted.get("borrower_name"), converted.get("borrower_id_card"), converted.get("borrower_phone"),
                    converted.get("borrower_unit"), converted.get("department_id"), converted.get("registration_department"), row.get("id"));
        }
    }

    /** Returns a copy; already converted/manual snapshots and all business timestamps are immutable here. */
    public static Map<String, Object> convertLegacy(Map<String, Object> row,
            List<Map<String, Object>> personnel, List<Map<String, Object>> departments) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>(row);
        // Optional handler snapshots are independent of personnel; never infer historical handlers.
        if (!copy.containsKey("handler_id")) { copy.put("handler_id", 0L); }
        if (!copy.containsKey("handler_name")) { copy.put("handler_name", ""); }
        if (!copy.containsKey("handler_id_card")) { copy.put("handler_id_card", ""); }
        // Legacy rows and archives have no trustworthy account owner; zero is admin-only.
        if (!copy.containsKey("owner_user_id") || copy.get("owner_user_id") == null) {
            copy.put("owner_user_id", 0L);
        }
        // Only legacy archives lacking usable state consult the retained date; explicit 0/1 always wins.
        if (!copy.containsKey("returned") || copy.get("returned") == null) {
            Object actualReturnDate = copy.get("actual_return_date");
            copy.put("returned", actualReturnDate != null && !String.valueOf(actualReturnDate).isEmpty() ? 1 : 0);
        }
        Object marker = row.get("borrower_migrated");
        if ("1".equals(String.valueOf(marker))) {
            supplementRegistration(copy, departments, copy.get("department_id"));
            return copy;
        }
        if (marker != null && !"0".equals(String.valueOf(marker))) {
            throw new IllegalArgumentException("非法借用人转换标记");
        }
        Map<String, Object> person = find(personnel, row.get("personnel_id"));
        Map<String, Object> department = person == null ? null : find(departments, person.get("department_id"));
        copy.put("borrower_name", text(person, "name", row.get("borrower_name")));
        copy.put("borrower_id_card", text(person, "id_card", row.get("borrower_id_card")));
        copy.put("borrower_phone", text(person, "phone", row.get("borrower_phone")));
        copy.put("borrower_unit", text(department, "name", row.get("borrower_unit")));
        if (department == null && (!copy.containsKey("department_id") || copy.get("department_id") == null)) {
            copy.put("department_id", 0L);
        } else if (department != null && isZero(copy.get("department_id"))) {
            copy.put("department_id", department.get("id"));
        }
        copy.put("personnel_id", 0L);
        copy.put("borrower_migrated", 1);
        Object registrationDepartmentId = department == null ? copy.get("department_id") : department.get("id");
        supplementRegistration(copy, departments, registrationDepartmentId);
        return copy;
    }

    private static void supplementRegistration(Map<String, Object> row, List<Map<String, Object>> departments,
                                               Object departmentId) {
        // An explicit empty value is a saved snapshot, not a missing field from an old archive.
        if (!row.containsKey("registration_department")) {
            row.put("registration_department", text(find(departments, departmentId), "name", null));
        }
    }

    private static boolean isZero(Object value) {
        return value == null || "0".equals(String.valueOf(value));
    }

    private static String text(Map<String, Object> source, String key, Object fallback) {
        Object value = source == null ? null : source.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) { value = fallback; }
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, Object> find(List<Map<String, Object>> rows, Object id) {
        if (id == null || !String.valueOf(id).matches("[1-9][0-9]*")) { return null; }
        for (Map<String, Object> row : rows) {
            if (String.valueOf(id).equals(String.valueOf(row.get("id")))) { return row; }
        }
        return null;
    }

    private static List<Map<String, Object>> rows(JdbcTemplate jdbc, String table) {
        return tableExists(jdbc, table) ? jdbc.queryForList("SELECT * FROM " + table)
                : Collections.<Map<String, Object>>emptyList();
    }

    private static boolean tableExists(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", Integer.class, table);
        return count != null && count > 0;
    }
}
