package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.util.Db;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.auth.ForbiddenException;
import com.szjx.personnel.server.model.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {
    private static final Set<String> KEYS = new HashSet<String>(Arrays.asList("dashWidgets", "dashStatCards"));
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public PreferenceController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private String storageKey(String key) {
        DataScope.Context user = DataScope.get();
        if (user == null || user.userId == null || user.userId <= 0) throw new ForbiddenException("请先登录");
        if (!KEYS.contains(key)) throw new IllegalArgumentException("不支持的个人设置项");
        return "preference:" + user.userId + ":" + key;
    }

    @GetMapping("/{key}")
    public ApiResponse get(@PathVariable String key) {
        List<String> values = jdbc.queryForList("SELECT svalue FROM sys_setting WHERE skey=?", String.class, storageKey(key));
        return ApiResponse.ok(values.isEmpty() ? "" : values.get(0));
    }

    @PutMapping("/{key}")
    public ApiResponse put(@PathVariable String key, @RequestBody JsonNode body) {
        String target = storageKey(key);
        if (body == null || !body.isArray() || body.size() > 100) throw new IllegalArgumentException("个人布局必须是最多100项的数组");
        try {
            String value = mapper.writeValueAsString(body);
            if (value.getBytes(StandardCharsets.UTF_8).length > 65536) throw new IllegalArgumentException("个人布局不能超过64KB");
            Db.upsertSetting(jdbc, "sys_setting", target, value);
            return ApiResponse.ok();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("个人布局格式不正确");
        }
    }
}
