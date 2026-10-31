package com.szjx.personnel.server.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataScopeTest {
    @AfterEach void clear() { DataScope.clear(); }

    @Test void internalAndRootGuardsPreserveLegacyNoQueryBehavior() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        for (DataScope.Context context : Arrays.asList(null,
                new DataScope.Context(1L, "admin", true, Collections.<Long>emptySet()))) {
            DataScope.set(context);
            assertFalse(DataScope.restricted());
            assertTrue(DataScope.isAdmin());
            assertEquals("1=1", DataScope.departmentSql("department_id"));
            assertEquals("1=1", DataScope.personSql("personnel_id"));
            DataScope.requireDepartment(null);
            DataScope.requirePerson(jdbc, null);
            DataScope.requireRecord(jdbc, "does_not_exist", "owner", null);
            DataScope.requireAdmin();
        }
        verifyNoInteractions(jdbc);
    }

    @Test void predicatesOnlyContainSafeIdentifiersAndPositiveIntegralIdsAndContextIsImmutable() {
        Set<Long> source = new LinkedHashSet<Long>(Arrays.asList(2L, 3L, null, 0L, -1L));
        DataScope.set(new DataScope.Context(9L, "child", false, source));
        source.add(99L);
        assertTrue(DataScope.restricted());
        assertFalse(DataScope.isAdmin());
        assertEquals(9L, DataScope.userId());
        assertEquals("p.department_id IN (2,3)", DataScope.departmentSql("p.department_id"));
        assertEquals("personnel_id IN (SELECT id FROM personnel WHERE department_id IN (2,3))", DataScope.personSql("personnel_id"));
        assertThrows(IllegalArgumentException.class, () -> DataScope.departmentSql("id); DROP TABLE personnel;--"));
        assertThrows(IllegalArgumentException.class, () -> DataScope.personSql("id OR 1=1"));
        assertThrows(UnsupportedOperationException.class, () -> DataScope.get().departmentIds.add(7L));
        assertFalse(DataScope.allowsDepartment(null));
        assertFalse(DataScope.allowsDepartment(0L));
        assertFalse(DataScope.allowsDepartment(99L));
        assertThrows(ForbiddenException.class, DataScope::requireAdmin);
        DataScope.set(new DataScope.Context(9L, "child", false, Collections.<Long>emptySet()));
        assertEquals("1=0", DataScope.departmentSql("department_id"));
    }

    @Test void recordAndPersonGuardsRejectMissingNullZeroOrOutsideOwners() {
        DataScope.set(new DataScope.Context(9L, "child", false, Collections.singleton(2L)));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList("SELECT department_id FROM personnel WHERE id=?", Long.class, 10L)).thenReturn(Collections.singletonList(2L));
        when(jdbc.queryForList("SELECT department_id FROM personnel WHERE id=?", Long.class, 11L)).thenReturn(Collections.singletonList(4L));
        when(jdbc.queryForList("SELECT department_id FROM personnel WHERE id=?", Long.class, 12L)).thenReturn(Collections.singletonList(null));
        when(jdbc.queryForList("SELECT department_id FROM personnel WHERE id=?", Long.class, 13L)).thenReturn(Collections.singletonList(0L));
        when(jdbc.queryForList("SELECT keeper_id FROM equipment WHERE id=?", Long.class, 1L)).thenReturn(Collections.singletonList(10L));
        when(jdbc.queryForList("SELECT keeper_id FROM equipment WHERE id=?", Long.class, 2L)).thenReturn(Collections.singletonList(11L));
        when(jdbc.queryForList("SELECT keeper_id FROM equipment WHERE id=?", Long.class, 3L)).thenReturn(Collections.singletonList(0L));
        when(jdbc.queryForList("SELECT keeper_id FROM equipment WHERE id=?", Long.class, 4L)).thenReturn(Collections.singletonList(null));
        DataScope.requirePerson(jdbc, 10L);
        DataScope.requireRecord(jdbc, "equipment", "keeper_id", 1L);
        for (Long id : Arrays.asList(null, 0L, 11L, 12L, 13L, 99L))
            assertThrows(ForbiddenException.class, () -> DataScope.requirePerson(jdbc, id));
        for (Long id : Arrays.asList(null, 0L, 2L, 3L, 4L, 99L))
            assertThrows(ForbiddenException.class, () -> DataScope.requireRecord(jdbc, "equipment", "keeper_id", id));
        assertThrows(IllegalArgumentException.class, () -> DataScope.requireRecord(jdbc, "equipment;DROP", "keeper_id", 1L));
    }

    @Test void threadLocalDoesNotPropagateToAnotherThread() throws Exception {
        DataScope.set(new DataScope.Context(2L, "child", false, Collections.singleton(2L)));
        java.util.concurrent.atomic.AtomicReference<DataScope.Context> observed = new java.util.concurrent.atomic.AtomicReference<DataScope.Context>();
        Thread worker = new Thread(() -> observed.set(DataScope.get()));
        worker.start(); worker.join();
        assertNull(observed.get());
        DataScope.clear(); assertNull(DataScope.get());
    }
}
