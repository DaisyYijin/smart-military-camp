package com.szjx.personnel.server.service;

import com.szjx.personnel.server.auth.ForbiddenException;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class TokenRevocationTest {
    @Test void globalRevocationInvalidatesSessionsAndInFlightLoginsForUnseenUsers() {
        TokenManager tokens = new TokenManager();
        String existing = tokens.create("owner");
        long unseenVersion = tokens.version("not-yet-logged-in");
        tokens.revokeAll();
        assertNull(tokens.validate(existing));
        assertThrows(ForbiddenException.class, () -> tokens.create("not-yet-logged-in", unseenVersion));
        String fresh = tokens.create("owner");
        assertEquals("owner", tokens.validate(fresh));
    }

    @Test void individualAndGlobalRevocationRemainMonotonicAndIsolated() {
        TokenManager tokens = new TokenManager();
        tokens.revokeAll();
        long prior = tokens.version("a");
        String a = tokens.create("a"), b = tokens.create("b");
        tokens.revokeUsers(Collections.singleton("a"));
        assertEquals(prior + 1, tokens.version("a"));
        assertNull(tokens.validate(a));
        assertEquals("b", tokens.validate(b));
        long beforeRestore = tokens.version("a");
        tokens.revokeAll();
        assertEquals(beforeRestore + 1, tokens.version("a"));
        assertThrows(ForbiddenException.class, () -> tokens.create("a", beforeRestore));
        assertNull(tokens.validate(b));
    }
}
