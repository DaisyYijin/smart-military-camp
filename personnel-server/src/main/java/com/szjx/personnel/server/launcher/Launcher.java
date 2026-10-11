package com.szjx.personnel.server.launcher;

import com.szjx.personnel.server.PersonnelServerApplication;
import com.szjx.personnel.server.dao.AdminUserDao;
import com.szjx.personnel.server.service.PasswordUtil;
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
 *  3. 端口被其他程序占用 → 弹出向导:显示占用进程,用户从空闲端口列表挑选或手动输入
 *  4. 首次运行(--setup 可强制) → 弹出向导:设置端口 + 管理员初始密码
 *  5. 启动 Spring Boot → 就绪后自动打开浏览器 → 挂载系统托盘(打开系统/改密码/退出)
 *
 * 命令行参数(其余参数原样传给 Spring Boot):
 *  --no-ui        无界面模式(无显示器的 Linux 服务器),全部提示走控制台
 *  --port=8080    本次启动端口,优先于配置文件
 *  --setup        强制重新弹出设置向导
 */
public class Launcher {

    public static void main(String[] args) throws Exception {
        boolean forceNoUi = hasFlag(args, "--no-ui");
        boolean ui = !forceNoUi && !GraphicsEnvironment.isHeadless();
        Integer portOverride = parsePortArg(args);
        List<String> springArgs = passthroughArgs(args);

        if (ui) {
            System.setProperty("java.awt.headless", "false");
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignore) {
                // 保持默认外观即可
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
        boolean firstRun = !config.isSetupDone() || hasFlag(args, "--setup");

        // ---- 端口决策 ----
        if (!PortProbe.isFree(port)) {
            if (PortProbe.isOurApp(port)) {
                // 已有实例在跑:不重复启动,直接带用户进系统
                String url = "http://127.0.0.1:" + port;
                if (ui) {
                    JOptionPane.showMessageDialog(null,
                            "程序已经在运行(端口 " + port + "),即将在浏览器中打开系统。",
                            "数智军营人员管理系统", JOptionPane.INFORMATION_MESSAGE);
                    TrayManager.openBrowser(url);
                } else {
                    System.out.println("[启动] 程序已在运行: " + url + " (端口被本程序占用,本次启动直接退出)");
                }
                System.exit(0);
                return;
            }
            // 被其他程序占用
            String occupier = PortProbe.describeOccupier(port);
            if (!ui) {
                System.err.println("[启动失败] 端口 " + port + " 被其他程序占用"
                        + (occupier == null ? "" : ": " + occupier));
                System.err.println("[建议] 可用端口: " + PortProbe.findFreePorts(port, 5));
                System.err.println("[建议] 修改 data/config.json 中的 port,或用 --port=xxxx 指定端口后重试。");
                System.exit(1);
                return;
            }
            SetupWizard.Result r = SetupWizard.show(false, port, occupier, config.getAdminUser());
            if (!r.confirmed) {
                System.exit(0);
                return;
            }
            port = r.port;
            pendingUsername = r.username;
            pendingPassword = r.newPassword;
            config.setPort(port);
            config.setAdminUser(r.username);
            config.setSetupDone(true);
            config.save();
        } else if (firstRun) {
            if (ui) {
                SetupWizard.Result r = SetupWizard.show(true, port, null, config.getAdminUser());
                if (!r.confirmed) {
                    System.exit(0);
                    return;
                }
                port = r.port;
                pendingUsername = r.username;
                pendingPassword = r.newPassword;
                config.setPort(port);
                config.setAdminUser(r.username);
                config.setSetupDone(true);
                config.save();
            } else {
                // 无界面模式无法弹向导:按默认管理员启动并明确提示
                System.out.println("[提示] 无界面模式首次运行,未做初始设置。默认管理员 admin/admin123,请登录后尽快修改!");
                config.setSetupDone(true);
                config.save();
            }
        }

        // ---- 启动 Spring Boot ----
        System.out.println("[启动] 正在启动服务,端口 " + port + " ...(首次启动可能需要十几秒)");
        List<String> runArgs = new ArrayList<String>();
        runArgs.add("--server.port=" + port);
        runArgs.addAll(springArgs);
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(PersonnelServerApplication.class)
                .headless(!ui)
                .run(runArgs.toArray(new String[0]));

        if (!PortProbe.waitReady(port, 90_000)) {
            String msg = "服务启动超时(端口 " + port + "),详情请查看 logs/personnel-server.log";
            System.err.println(msg);
            if (ui) {
                JOptionPane.showMessageDialog(null, msg, "启动失败", JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
            return;
        }

        // ---- 应用向导里设置的账号与密码(首次运行时数据库刚初始化为 admin/admin123,这里覆盖) ----
        final String adminUser = pendingUsername;
        try {
            AdminUserDao adminDao = ctx.getBean(AdminUserDao.class);
            if (!oldAdminUser.equals(adminUser)) {
                adminDao.rename(oldAdminUser, adminUser); // 旧名不存在时为 0 行,无副作用
            }
            if (pendingPassword != null && pendingPassword.length > 0) {
                adminDao.updatePassword(adminUser, PasswordUtil.hash(new String(pendingPassword)));
            }
            System.out.println("[启动] 管理员账号已按设置更新: " + adminUser);
        } catch (Exception e) {
            System.err.println("[启动] 管理员账号/密码设置失败: " + e.getMessage());
            if (ui) {
                JOptionPane.showMessageDialog(null,
                        "管理员账号设置失败:" + e.getMessage() + "\n可登录后在托盘菜单修改。",
                        "账号设置失败", JOptionPane.WARNING_MESSAGE);
            }
        }

        String lanUrl = "http://" + firstLanIp() + ":" + port;
        printBanner(port, lanUrl, adminUser);

        if (ui) {
            if (firstRun) {
                JOptionPane.showMessageDialog(null,
                        "初始设置完成,系统已启动!\n\n本机浏览器即将自动打开,请使用账号 " + adminUser
                                + " 和你设置的密码登录。\n局域网其他电脑访问地址:" + lanUrl,
                        "数智军营人员管理系统", JOptionPane.INFORMATION_MESSAGE);
            }
            TrayManager.openBrowser("http://127.0.0.1:" + port);
            TrayManager.install(port, lanUrl, adminUser, new TrayManager.PasswordChanger() {
                public String change(String newPassword) {
                    try {
                        ctx.getBean(AdminUserDao.class)
                                .updatePassword(adminUser, PasswordUtil.hash(newPassword));
                        return null;
                    } catch (Exception e) {
                        return e.getMessage();
                    }
                }
            });
        }
        // main 返回后由 Tomcat 工作线程保持进程存活
    }

    private static void printBanner(int port, String lanUrl, String adminUser) {
        System.out.println("======================================================");
        System.out.println("  数智军营人员管理系统 已启动");
        System.out.println("  本机访问:   http://127.0.0.1:" + port);
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
            if (a == null || "--no-ui".equals(a) || "--setup".equals(a) || a.startsWith("--port=")) {
                continue;
            }
            rest.add(a);
        }
        return rest;
    }
}
