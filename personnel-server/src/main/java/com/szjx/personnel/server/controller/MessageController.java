package com.szjx.personnel.server.controller;

import com.szjx.personnel.server.auth.DataScope;
import com.szjx.personnel.server.model.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 站内信/消息通知(局域网多用户):用户间互发+系统通知,顶栏铃铛未读数轮询。
 * 系统通知由其他模块(如审批流)通过 sendSystem 写入,发送者显示为"系统"。
 */
@RestController
@RequestMapping("/api/message")
public class MessageController {

    private final JdbcTemplate jdbc;

    public MessageController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 供其他模块(审批流等)写系统通知 */
    public static void sendSystem(JdbcTemplate jdbc, long receiverId, String title, String content) {
        jdbc.update("INSERT INTO message(sender_id, receiver_id, title, content, type, read_flag) VALUES(0,?,?,?,'system',0)",
                receiverId, title == null ? "" : title, content == null ? "" : content);
    }

    /** 未读数(顶栏铃铛轮询) */
    @GetMapping("/unread-count")
    public ApiResponse unreadCount() {
        Long uid = DataScope.get().userId;
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM message WHERE receiver_id=? AND read_flag=0", Long.class, uid);
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("count", n == null ? 0 : n);
        return ApiResponse.ok(m);
    }

    /** 我的消息(分页,倒序,含发送人名) */
    @GetMapping
    public ApiResponse list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 1) { page = 1; }
        if (size < 1 || size > 100) { size = 20; }
        Long uid = DataScope.get().userId;
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM message WHERE receiver_id=?", Long.class, uid);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT m.id, m.title, m.content, m.type, m.read_flag AS readFlag, m.created_at AS createdAt, " +
                "CASE WHEN m.sender_id=0 THEN '系统' ELSE COALESCE(u.username,'') END AS senderName " +
                "FROM message m LEFT JOIN admin_user u ON m.sender_id=u.id " +
                "WHERE m.receiver_id=? ORDER BY m.id DESC LIMIT ? OFFSET ?", uid, size, (page - 1) * size);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("total", total == null ? 0 : total);
        result.put("list", rows);
        return ApiResponse.ok(result);
    }

    /** 发送站内信 */
    @PostMapping
    public ApiResponse send(@RequestBody Map<String, Object> body) {
        DataScope.Context me = DataScope.get();
        long receiverId = longVal(body.get("receiverId"));
        String title = str(body.get("title"));
        String content = str(body.get("content"));
        if (receiverId <= 0) { return ApiResponse.error("请选择收件人"); }
        if (content.isEmpty()) { return ApiResponse.error("请填写内容"); }
        List<Map<String, Object>> target = jdbc.queryForList("SELECT id FROM admin_user WHERE id=?", receiverId);
        if (target.isEmpty()) { return ApiResponse.error("收件人不存在"); }
        jdbc.update("INSERT INTO message(sender_id, receiver_id, title, content, type, read_flag) VALUES(?,?,?,?,'user',0)",
                me.userId, receiverId, title, content);
        return ApiResponse.ok();
    }

    /** 标记已读 */
    @PutMapping("/{id:\\d+}/read")
    public ApiResponse markRead(@PathVariable Long id) {
        jdbc.update("UPDATE message SET read_flag=1 WHERE id=? AND receiver_id=?", id, DataScope.get().userId);
        return ApiResponse.ok();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public ApiResponse readAll() {
        jdbc.update("UPDATE message SET read_flag=1 WHERE receiver_id=? AND read_flag=0", DataScope.get().userId);
        return ApiResponse.ok();
    }

    /** 删除(仅收件人可删收到的消息) */
    @DeleteMapping("/{id:\\d+}")
    public ApiResponse delete(@PathVariable Long id) {
        jdbc.update("DELETE FROM message WHERE id=? AND receiver_id=?", id, DataScope.get().userId);
        return ApiResponse.ok();
    }

    /** 收件人候选:账号列表(不含自己) */
    @GetMapping("/users")
    public ApiResponse users() {
        return ApiResponse.ok(jdbc.queryForList(
                "SELECT id, username FROM admin_user WHERE id<>? ORDER BY username", DataScope.get().userId));
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static long longVal(Object v) {
        if (v == null || String.valueOf(v).trim().isEmpty()) { return 0; }
        try { return Long.parseLong(String.valueOf(v).trim()); } catch (NumberFormatException e) { return 0; }
    }
}
