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
        System.out.println("  本机访问:   http://127.0.0.1:8080");
        System.out.println("  局域网访问: http://<本机IP>:8080  (客户端填写此地址)");
        System.out.println("  默认管理员: admin / admin123  (登录后请及时修改密码)");
        System.out.println("======================================================");
    }

    private static void ensureDataDir() {
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }
}
