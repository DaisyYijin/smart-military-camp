package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.model.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通用设置(如体能评定标准)
 */
@RestController
@RequestMapping("/api/setting")
public class SettingController {

    private static final java.util.Set<String> ALLOWED = new java.util.HashSet<String>(
            java.util.Arrays.asList("fitness_grade_rule"));

    private final JdbcTemplate jdbc;

    public SettingController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{key}")
    public ApiResponse get(@PathVariable String key) {
        if (!ALLOWED.contains(key)) {
            throw new IllegalArgumentException("不支持的设置项");
        }
        String v = jdbc.queryForObject("SELECT svalue FROM sys_setting WHERE skey=?", String.class, key);
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(v == null ? "[]" : v);
            return ApiResponse.ok(node);
        } catch (Exception e) {
            return ApiResponse.ok(new com.fasterxml.jackson.databind.node.ArrayNode(
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance));
        }
    }

    @PutMapping("/{key}")
    public ApiResponse put(@PathVariable String key, @RequestBody Object body) {
        if (!ALLOWED.contains(key)) {
            throw new IllegalArgumentException("不支持的设置项");
        }
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
            jdbc.update("INSERT INTO sys_setting(skey, svalue) VALUES(?,?) " +
                    "ON CONFLICT(skey) DO UPDATE SET svalue=excluded.svalue", key, json);
            return ApiResponse.ok();
        } catch (Exception e) {
            throw new IllegalArgumentException("保存失败:" + e.getMessage());
        }
    }
}
