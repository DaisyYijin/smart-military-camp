package com.szjx.personnel.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

/**
 * 数智军营人员管理系统 - 服务端入口
 * 运行:java -jar personnel-server.jar
 */
@SpringBootApplication
public class PersonnelServerApplication {

    public static void main(String[] args) {
        ensureDataDir();
        SpringApplication.run(PersonnelServerApplication.class, args);
        System.out.println("======================================================");
        System.out.println("  数智军营人员管理系统 服务端已启动");
        System.out.println("  请使用配置的端口和 HTTP/HTTPS 协议访问；局域网正式部署请启用可信 HTTPS。");
        System.out.println("  管理员使用初始化时设置的账号与密码，不存在固定默认密码。");
        System.out.println("======================================================");
    }

    private static void ensureDataDir() {
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }
}
