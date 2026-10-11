package com.szjx.personnel.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 无需登录的探测端点:
 * 1. 启动器用它判断服务是否就绪、端口上是否为本程序的另一实例
 * 2. 部署排查时可快速验证服务存活
 */
@RestController
public class PingController {

    public static final String APP_NAME = "personnel-server";

    @GetMapping("/api/ping")
    public Map<String, String> ping() {
        Map<String, String> m = new HashMap<String, String>();
        m.put("app", APP_NAME);
        m.put("status", "ok");
        return m;
    }
}
