package com.szjx.personnel.client.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * 客户端配置(存于用户目录 ~/.personnel-client/config.properties)
 */
public class AppConfig {

    private static final File CONFIG_FILE = new File(
            System.getProperty("user.home"), ".personnel-client" + File.separator + "config.properties");

    private String serverUrl = "http://127.0.0.1:8080";
    private String username = "";

    public static AppConfig load() {
        AppConfig cfg = new AppConfig();
        Properties props = new Properties();
        if (CONFIG_FILE.exists()) {
            try {
                InputStream in = new FileInputStream(CONFIG_FILE);
                try {
                    props.load(in);
                } finally {
                    in.close();
                }
                if (props.getProperty("serverUrl") != null) {
                    cfg.serverUrl = props.getProperty("serverUrl").trim();
                }
                if (props.getProperty("username") != null) {
                    cfg.username = props.getProperty("username").trim();
                }
            } catch (IOException e) {
                // 忽略读取失败
            }
        }
        return cfg;
    }

    public void save() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            Properties props = new Properties();
            props.setProperty("serverUrl", serverUrl);
            props.setProperty("username", username);
            OutputStream out = new FileOutputStream(CONFIG_FILE);
            try {
                props.store(out, "personnel-client config");
            } finally {
                out.close();
            }
        } catch (IOException e) {
            // 保存失败不影响主流程
        }
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
