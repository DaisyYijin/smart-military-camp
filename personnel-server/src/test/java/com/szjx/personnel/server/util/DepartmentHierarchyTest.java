package com.szjx.personnel.server.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DepartmentHierarchyTest {
    @TempDir Path temp;
    private SingleConnectionDataSource source;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        source = new SingleConnectionDataSource("jdbc:sqlite:" + temp.resolve("hierarchy.db"), true);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE department(id INTEGER PRIMARY KEY,name TEXT,parent_id INTEGER DEFAULT 0,sort INTEGER DEFAULT 0)");
    }

    @AfterEach void close() { source.destroy(); }

    @Test void preorderUsesSiblingSortThenIdAndFullAncestorPaths() {
        jdbc.update("INSERT INTO department VALUES(1,'营',0,9),(2,'连',1,0),(3,'排',2,0),(4,'班',3,2),(5,'班',3,1),(6,'另营',0,1)");
        DepartmentHierarchy tree = DepartmentHierarchy.load(jdbc);
        assertEquals(Arrays.asList(6L,1L,2L,3L,5L,4L), tree.ordered().stream().map(DepartmentHierarchy.Node::getId).collect(Collectors.toList()));
        assertEquals("营连排班", tree.path(4L));
        assertEquals(3, tree.get(4L).getDepth());
        assertEquals(Arrays.asList(4L,3L,2L,1L), tree.ancestorIds(4L));
        assertEquals(Arrays.asList(3L,5L,4L), tree.descendantIds(3L).stream().collect(Collectors.toList()));
        assertEquals(2, tree.ordered().stream().filter(node -> "班".equals(node.getName())).count());
    }

    @Test void cyclesSelfParentsAndOrphansAreSafeDeterministicAndNeverWrittenBack() {
        jdbc.update("INSERT INTO department VALUES(1,'一',2,0),(2,'二',3,0),(3,'三',1,0),(4,'自身',4,0),(5,'孤儿',999,0)");
        DepartmentHierarchy tree = DepartmentHierarchy.load(jdbc);
        assertEquals(Arrays.asList(1L,3L,2L,4L,5L), tree.ordered().stream().map(DepartmentHierarchy.Node::getId).collect(Collectors.toList()));
        assertEquals(0L, tree.get(1L).getParentId());
        assertEquals("一三二", tree.path(2L));
        assertEquals(Arrays.asList(2L,3L,1L), tree.ancestorIds(2L));
        assertEquals(3, tree.descendantIds(1L).size());
        assertEquals(0L, tree.get(4L).getParentId());
        assertEquals(0L, tree.get(5L).getParentId());
        assertEquals(2L, jdbc.queryForObject("SELECT parent_id FROM department WHERE id=1", Long.class));
        assertEquals(999L, jdbc.queryForObject("SELECT parent_id FROM department WHERE id=5", Long.class));
    }

    @Test void deepHierarchyDoesNotUseTheJavaCallStack() {
        jdbc.execute("BEGIN");
        for (int i = 1; i <= 4000; i++) jdbc.update("INSERT INTO department VALUES(?,?,?,0)", i, "D" + i, i - 1);
        jdbc.execute("COMMIT");
        DepartmentHierarchy tree = DepartmentHierarchy.load(jdbc);
        assertEquals(4000, tree.ordered().size());
        assertEquals(3999, tree.get(4000L).getDepth());
        assertEquals(4000, tree.ancestorIds(4000L).size());
        assertEquals(4000, tree.descendantIds(1L).size());
        assertTrue(tree.path(4000L).startsWith("D1D2D3"));
        assertTrue(tree.path(4000L).endsWith("D3999D4000"));
    }

    @Test void missingNodesAndEmptyTreeHaveNoInventedPath() {
        DepartmentHierarchy tree = DepartmentHierarchy.load(jdbc);
        assertTrue(tree.ordered().isEmpty());
        assertNull(tree.get(null));
        assertEquals("", tree.path(null));
        assertEquals("", tree.path(99L));
        assertEquals(Collections.emptyList(), tree.ancestorIds(99L));
        assertEquals(Collections.emptySet(), tree.descendantIds(null));
    }
}
