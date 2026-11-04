package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.dao.DepartmentDao;
import com.szjx.personnel.server.dao.PersonnelDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 单位同级拖动排序:sort 重写、tree 顺序跟随、跨级/越权拒绝 */
class DepartmentReorderTest {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private DepartmentService service;

    @BeforeEach void setup() {
        jdbc = AccountServiceTest.database(temp.resolve("dept-reorder.sqlite"));
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "parent_id INTEGER DEFAULT 0, sort INTEGER DEFAULT 0, remark TEXT DEFAULT '', leader_id INTEGER DEFAULT 0)");
        jdbc.execute("CREATE TABLE personnel(id INTEGER PRIMARY KEY, name TEXT, department_id INTEGER DEFAULT 0)");
        service = new DepartmentService(new DepartmentDao(jdbc), new PersonnelDao(jdbc));
        DataScope.set(new DataScope.Context(9L, "admin", true, Collections.<Long>emptySet()));
    }

    @AfterEach void clear() { DataScope.clear(); }

    private long addDept(String name, long parentId) {
        jdbc.update("INSERT INTO department(name, parent_id) VALUES(?,?)", name, parentId);
        return jdbc.queryForObject("SELECT id FROM department WHERE name=?", Long.class, name);
    }

    @Test void reorderRewritesSiblingSortAndTreeFollows() {
        long a = addDept("一中队", 0), b = addDept("二中队", 0), c = addDept("三中队", 0);
        service.reorder(Arrays.asList(c, a, b));
        assertEquals(Integer.valueOf(2), jdbc.queryForObject("SELECT sort FROM department WHERE id=?", Integer.class, a));
        assertEquals(Integer.valueOf(3), jdbc.queryForObject("SELECT sort FROM department WHERE id=?", Integer.class, b));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT sort FROM department WHERE id=?", Integer.class, c));
        assertEquals("三中队", service.tree().get(0).getName(), "tree() 根层顺序应跟随新排序");
    }

    @Test void crossParentOrUnknownReorderRejected() {
        long root = addDept("大队", 0);
        long x = addDept("一中队", root), y = addDept("二中队", root), z = addDept("机关", 0);
        assertThrows(IllegalArgumentException.class, () -> service.reorder(Arrays.asList(x, y, z)));
        assertThrows(IllegalArgumentException.class, () -> service.reorder(Arrays.asList(x, 999L)));
    }
}
