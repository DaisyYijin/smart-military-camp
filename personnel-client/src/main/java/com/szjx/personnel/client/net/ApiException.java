package com.szjx.personnel.client.net;

/**
 * 业务异常(携带服务端返回的错误信息)
 */
public class ApiException extends Exception {

    public ApiException(String message) {
        super(message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
