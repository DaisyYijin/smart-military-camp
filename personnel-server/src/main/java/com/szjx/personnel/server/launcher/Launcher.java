package com.szjx.personnel.server.launcher;

import com.szjx.personnel.server.PersonnelServerApplication;
import com.szjx.personnel.server.service.LocalAdminService;
import com.szjx.personnel.server.config.SchemaInitializer;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 桌面启动入口(jar 主类):双击运行时的完整流程。
 *
 * 启动流程:
 *  1. 读取 data/config.json 里用户选定的端口(首次默认 8080)
 *  2. 端口被本程序已运行实例占用 → 提示后直接打开浏览器,本次退出
 *  3. 有界面模式:每次启动都弹出设置页(可改端口/账号;密码留空不改,
 *     端口被其他程序占用时显示占用进程并给出可选空闲端口)
 *  4. 启动 Spring Boot → 就绪后自动打开浏览器 → 挂载系统托盘(打开系统/改密码/退出)
 *
 * 命令行参数(其余参数原样传给 Spring Boot):
 *  --no-ui        无界面模式(无显示器的 Linux 服务器),全部提示走控制台
 *  --port=8080    本次启动端口,优先于配置文件
 *  --setup        强制重新弹出设置向导
 *  --dev          开发模式:网页文件直接读源码目录(改 HTML/CSS/JS 后浏览器刷新即生效,
 *                 无需重启;Java 后端改动仍需重新构建运行)
 */
public class Launcher {

    static javax.swing.JFrame OWNER_FRAME;

    public static void main(String[] args) throws Exception {
        boolean forceNoUi = hasFlag(args, "--no-ui");
        boolean ui = !forceNoUi && !GraphicsEnvironment.isHeadless();
        Integer portOverride = parsePortArg(args);
        List<String> springArgs = passthroughArgs(args);

        if (ui) {
            System.setProperty("java.awt.headless", "false");
            // 隐藏的 owner frame:给 JOptionPane 等弹窗提供任务栏图标
            javax.swing.JFrame ownerFrame = new javax.swing.JFrame();
            ownerFrame.setUndecorated(true);
            ownerFrame.setSize(1, 1);
            ownerFrame.setLocation(-100, -100); // 移到屏幕外,不可见但保持可见态(任务栏需要)
            ownerFrame.setIconImage(AppIcons.appIcon());
            ownerFrame.setVisible(true);
            OWNER_FRAME = ownerFrame;
            try {
                // 现代扁平外观(FlatLaf),失败时回退系统外观
                com.formdev.flatlaf.FlatIntelliJLaf.setup();
            } catch (Throwable ignore) {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignore2) {
                    // 保持默认外观即可
                }
            }
            SetupWizard.applyGlobalFontSize();
        }

        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        AppConfig config = AppConfig.load();
        int port = portOverride != null ? portOverride : config.getPort();
        final String oldAdminUser = config.getAdminUser(); // 向导改账号时的改名源
        String pendingUsername = oldAdminUser;
        char[] pendingPassword = null;
        boolean firstRun = !config.isSetupDone() || !new File(dataDir, "personnel.db").exists() || hasFlag(args, "--setup");

        // ---- 端口决策与设置 ----
        if (!PortProbe.isFree(port) && PortProbe.isOurApp(port)) {
            // 已有实例在跑:不重复启动,直接带用户进系统
            String url = "http://127.0.0.1:" + port;
            if (ui) {
                JOptionPane.showMessageDialog(OWNER_FRAME,
                        "程序已经在运行(端口 " + port + "),即将在浏览器中打开系统。",
                        "数智军营人员管理系统", JOptionPane.INFORMATION_MESSAGE);
                TrayManager.openBrowser(url);
            } else {
                System.out.println("[启动] 程序已在运行: " + url + " (端口被本程序占用,本次启动直接退出)");
            }
            System.exit(0);
            return;
        }

        if (ui) {
            // 每次启动都显示设置页:端口冲突随时可改,忘记密码每次启动都能重置
            String occupier = PortProbe.isFree(port) ? null : PortProbe.describeOccupier(port);
            SetupWizard.Result r = SetupWizard.show(firstRun, port, occupier, config.getAdminUser());
            if (!r.confirmed) {
                System.exit(0);
                return;
            }
            port = r.port;
            pendingUsername = r.username;
            pendingPassword = r.newPassword;
            config.setPort(port);
            config.save();
        } else {
            if (!PortProbe.isFree(port)) {
                String occupier = PortProbe.describeOccupier(port);
                System.err.println("[启动失败] 端口 " + port + " 被其他程序占用"
                        + (occupier == null ? "" : ": " + occupier));
                System.err.println("[建议] 可用端口: " + PortProbe.findFreePorts(port, 5));
                System.err.println("[建议] 修改 data/config.json 中的 port,或用 --port=xxxx 指定端口后重试。");
                System.exit(1);
                return;
            }
            if (firstRun) {
                System.out.println("[提示] 新数据库须设置 APP_BOOTSTRAP_PASSWORD（可选 APP_BOOTSTRAP_USERNAME）；未设置时会拒绝启动，不再创建固定口令账号。");
            }
        }

        // ---- 启动 Spring Boot ----
        System.out.println("[启动] 正在启动服务,端口 " + port + " ...(首次启动可能需要十几秒)");
        List<String> runArgs = new ArrayList<String>();
        runArgs.add("--server.port=" + port);
        if (hasFlag(args, "--dev")) {
            // 开发模式:静态资源优先读源码目录且不缓存,前端改动刷新即生效
            runArgs.add("--spring.web.resources.static-locations="
                    + "file:personnel-server/src/main/resources/static/,classpath:/static/");
            runArgs.add("--spring.web.resources.cache.cachecontrol.no-store=true");
            System.out.println("[开发模式] 网页文件直接读取源码目录,修改后浏览器刷新即可生效");
        }
        runArgs.addAll(springArgs);
        String bootstrapPasswordFromWizard = pendingPassword == null || pendingPassword.length == 0
                ? null : new String(pendingPassword);
        final Map<String, Object> bootstrap = new HashMap<String, Object>();
        if (bootstrapPasswordFromWizard != null) {
            bootstrap.put("app.bootstrap.username", pendingUsername);
            bootstrap.put("app.bootstrap.password", bootstrapPasswordFromWizard);
        }
        ConfigurableApplicationContext ctx;
        try {
            ctx = new SpringApplicationBuilder(PersonnelServerApplication.class)
                    .headless(!ui)
                    .initializers(context -> context.getEnvironment().getPropertySources()
                            .addFirst(new MapPropertySource("local-bootstrap", bootstrap)))
                    .run(runArgs.toArray(new String[0]));
        } catch (RuntimeException e) {
            bootstrap.clear();
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            System.err.println("[启动失败] " + reason);
            if (ui) JOptionPane.showMessageDialog(OWNER_FRAME, reason, "启动失败", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        } finally {
            bootstrap.clear();
            if (pendingPassword != null) Arrays.fill(pendingPassword, '\0');
        }
        port = ((WebServerApplicationContext) ctx).getWebServer().getPort();
        String appliedAdminUser = oldAdminUser;
        try {
            if (ctx.getBean(SchemaInitializer.class).isAdministratorCreated()) {
                appliedAdminUser = pendingUsername;
                if (!ui) appliedAdminUser = ctx.getEnvironment().getProperty("app.bootstrap.username", "admin").trim();
            } else if (ui) {
                // Existing installations may use the local recovery wizard, never bootstrap environment values.
                String resetPassword = bootstrapPasswordFromWizard;
                ctx.getBean(LocalAdminService.class).configureRoot(oldAdminUser, pendingUsername, resetPassword);
                appliedAdminUser = pendingUsername;
            }
            config.setAdminUser(appliedAdminUser);
            config.setSetupDone(true);
            config.save();
        } catch (Exception e) {
            ctx.close();
            System.err.println("[启动失败] 管理员账号设置未完成，服务已关闭；请检查本机配置和日志。");
            if (ui) JOptionPane.showMessageDialog(OWNER_FRAME, "管理员账号设置未完成，服务已关闭。请检查本机配置。", "启动失败", JOptionPane.ERROR_MESSAGE);
            throw new IllegalStateException("管理员账号设置未完成", e);
        }

        final String adminUser = appliedAdminUser;
        org.springframework.boot.autoconfigure.web.ServerProperties serverProperties =
                ctx.getBean(org.springframework.boot.autoconfigure.web.ServerProperties.class);
        boolean tls = serverProperties.getSsl() != null && serverProperties.getSsl().isEnabled();
        String scheme = tls ? "https" : "http";
        String localUrl = scheme + "://127.0.0.1:" + port;
        String lanUrl = scheme + "://" + firstLanIp() + ":" + port;
        printBanner(localUrl, lanUrl, adminUser, tls);

        if (ui) {
            if (firstRun) {
                JOptionPane.showMessageDialog(OWNER_FRAME,
                        "初始设置完成,系统已启动!\n\n本机浏览器即将自动打开,请使用账号 " + adminUser
                                + " 和你设置的密码登录。\n局域网其他电脑访问地址:" + lanUrl,
                        "数智军营人员管理系统", JOptionPane.INFORMATION_MESSAGE);
            }
            TrayManager.openBrowser(localUrl);
            TrayManager.install(port, localUrl, lanUrl, adminUser, new TrayManager.PasswordChanger() {
                public String change(String newPassword) {
                    try {
                        ctx.getBean(LocalAdminService.class).configureRoot(adminUser, adminUser, newPassword);
                        return null;
                    } catch (Exception e) {
                        return e.getMessage();
                    }
                }
            });
        }
        // main 返回后由 Tomcat 工作线程保持进程存活
    }

    private static void printBanner(String localUrl, String lanUrl, String adminUser, boolean tls) {
        System.out.println("======================================================");
        System.out.println("  数智军营人员管理系统 已启动");
        System.out.println("  本机访问:   " + localUrl);
        if (!tls) System.out.println("  [安全提示] 当前为明文 HTTP；局域网正式使用请配置可信 HTTPS，勿直接暴露到互联网。");
        System.out.println("  局域网访问: " + lanUrl);
        System.out.println("  管理账号:   " + adminUser + "(密码为初始设置/托盘修改后的密码)");
        System.out.println("  退出方式:   托盘图标右键 → 退出(无界面模式按 Ctrl+C)");
        System.out.println("======================================================");
    }

    /** 取第一个局域网 IPv4 地址(192.168/10./172.16-31),取不到时回退本机名 */
    private static String firstLanIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()
                            && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "本机IP";
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) {
            if (flag.equals(a)) {
                return true;
            }
        }
        return false;
    }

    private static Integer parsePortArg(String[] args) {
        for (String a : args) {
            if (a != null && a.startsWith("--port=")) {
                try {
                    return Integer.parseInt(a.substring(7));
                } catch (NumberFormatException ignore) {
                    // 格式不对就按配置文件处理
                }
            }
        }
        return null;
    }

    /** 过滤掉本程序自己的参数,其余原样传给 Spring Boot */
    private static List<String> passthroughArgs(String[] args) {
        List<String> rest = new ArrayList<String>();
        for (String a : args) {
            if (a == null || "--no-ui".equals(a) || "--setup".equals(a) || "--dev".equals(a)
                    || a.startsWith("--port=")) {
                continue;
            }
            rest.add(a);
        }
        return rest;
    }
}
