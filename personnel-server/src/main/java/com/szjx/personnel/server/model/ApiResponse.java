package com.szjx.personnel.server.model;

/**
 * 统一 API 响应格式
 */
public class ApiResponse {

    private int code;
    private String message;
    private Object data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static ApiResponse ok(Object data) {
        return new ApiResponse(0, "ok", data);
    }

    public static ApiResponse ok() {
        return new ApiResponse(0, "ok", null);
    }

    public static ApiResponse error(String message) {
        return new ApiResponse(1, message, null);
    }

    public static ApiResponse error(int code, String message) {
        return new ApiResponse(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
