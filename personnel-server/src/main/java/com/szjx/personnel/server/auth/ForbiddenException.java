package com.szjx.personnel.server.auth;

/** Authenticated identity is disabled or is outside the permitted data scope. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
