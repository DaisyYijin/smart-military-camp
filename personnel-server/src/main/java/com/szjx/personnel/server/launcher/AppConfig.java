package com.szjx.personnel.server.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

/**
 * 启动配置(data/config.json):持久化用户选定的端口,
 * 保证端口"用户知情、主动选择、持久生效",避免自动漂移导致书签失效
 */
public class AppConfig {

    private static final File FILE = new File("data", "config.json");

    private int port = 8080;
    private boolean setupDone = false;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSetupDone() {
        return setupDone;
    }

    public void setSetupDone(boolean setupDone) {
        this.setupDone = setupDone;
    }

    public static AppConfig load() {
        if (FILE.exists()) {
            try {
                AppConfig c = new ObjectMapper().readValue(FILE, AppConfig.class);
                if (c != null && c.port >= 1 && c.port <= 65535) {
                    return c;
                }
            } catch (Exception e) {
                System.err.println("[启动] 配置文件读取失败,将按默认端口 8080 处理: " + e.getMessage());
            }
        }
        return new AppConfig();
    }

    public void save() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(FILE, this);
        } catch (Exception e) {
            System.err.println("[启动] 保存配置失败: " + e.getMessage());
        }
    }
}
