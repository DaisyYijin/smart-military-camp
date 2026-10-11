package com.szjx.personnel.server.launcher;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.SystemTray;
import java.awt.RenderingHints;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;

/**
 * 系统托盘:服务启动后常驻右下角。
 * 菜单:打开系统 / 复制局域网地址 / 修改管理员密码 / 退出。
 * "修改管理员密码"让用户随时能重置密码,避免忘记密码被锁在门外。
 */
public final class TrayManager {

    /** 由 Launcher 注入的密码修改回调,返回 null 表示成功,否则返回错误信息 */
    public interface PasswordChanger {
        String change(String newPassword);
    }

    private TrayManager() {
    }

    public static void install(final int port, final String lanUrl, final String adminUser,
                               final PasswordChanger changer) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    if (!SystemTray.isSupported()) {
                        System.out.println("[托盘] 当前系统不支持托盘图标(不影响使用,浏览器访问 http://127.0.0.1:" + port + ")");
                        return;
                    }
                    SystemTray tray = SystemTray.getSystemTray();
                    TrayIcon icon = new TrayIcon(createIcon(),
                            "数智军营人员管理系统 - 端口 " + port);
                    icon.setImageAutoSize(true);

                    java.awt.PopupMenu menu = new java.awt.PopupMenu();
                    menu.add(item("打开系统", new ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent e) {
                            openBrowser("http://127.0.0.1:" + port);
                        }
                    }));
                    menu.add(item("复制局域网访问地址", new ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent e) {
                            copyToClipboard(lanUrl);
                        }
                    }));
                    menu.addSeparator();
                    menu.add(item("修改管理员密码", new ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent e) {
                            showPasswordDialog(adminUser, changer);
                        }
                    }));
                    menu.addSeparator();
                    menu.add(item("退出", new ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent e) {
                            if (JOptionPane.showConfirmDialog(null,
                                    "确定退出数智军营人员管理系统吗?\n退出后所有电脑将无法访问本系统。",
                                    "退出确认", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                System.exit(0);
                            }
                        }
                    }));
                    icon.setPopupMenu(menu);
                    // 双击托盘 = 打开系统
                    icon.addActionListener(new ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent e) {
                            openBrowser("http://127.0.0.1:" + port);
                        }
                    });
                    tray.add(icon);
                    icon.displayMessage("数智军营人员管理系统",
                            "系统已启动,端口 " + port + "。双击图标可打开系统。",
                            TrayIcon.MessageType.INFO);
                } catch (AWTException e) {
                    System.out.println("[托盘] 托盘图标创建失败(不影响使用): " + e.getMessage());
                }
            }
        });
    }

    /** 打开系统默认浏览器,失败时退回系统命令 */
    public static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop d = Desktop.getDesktop();
                if (d.isSupported(Desktop.Action.BROWSE)) {
                    d.browse(new URI(url));
                    return;
                }
            }
        } catch (Exception ignore) {
            // 走下面的兜底
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "无法自动打开浏览器,请手动访问:" + url, "打开浏览器失败",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void showPasswordDialog(final String adminUser, final PasswordChanger changer) {
        JPanel panel = new JPanel();
        JPasswordField pw1 = new JPasswordField(16);
        JPasswordField pw2 = new JPasswordField(16);
        pw1.setFont(pw1.getFont().deriveFont(18f));
        pw2.setFont(pw2.getFont().deriveFont(18f));
        panel.add(new JLabel("新密码(至少6位):"));
        panel.add(pw1);
        panel.add(new JLabel("确认密码:"));
        panel.add(pw2);
        while (true) {
            int ok = JOptionPane.showConfirmDialog(null, panel,
                    "修改管理员(" + adminUser + ")密码",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) {
                return;
            }
            String pw = new String(pw1.getPassword());
            String confirm = new String(pw2.getPassword());
            if (pw.length() < 6) {
                JOptionPane.showMessageDialog(null, "密码至少需要 6 位。", "密码太短",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (!pw.equals(confirm)) {
                JOptionPane.showMessageDialog(null, "两次输入的密码不一致。", "密码不一致",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }
            String error = changer.change(pw);
            if (error == null) {
                JOptionPane.showMessageDialog(null, "管理员密码修改成功,下次登录请使用新密码。",
                        "修改成功", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(null, "密码修改失败:" + error, "修改失败",
                        JOptionPane.ERROR_MESSAGE);
            }
            return;
        }
    }

    private static void copyToClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            JOptionPane.showMessageDialog(null, "已复制:" + text, "复制成功",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "复制失败,请手动记录:" + text, "复制失败",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private static java.awt.MenuItem item(String label, ActionListener listener) {
        java.awt.MenuItem mi = new java.awt.MenuItem(label);
        mi.addActionListener(listener);
        return mi;
    }

    /** 程序化生成托盘图标,避免依赖图片资源文件 */
    private static Image createIcon() {
        int size = 24;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(21, 74, 148));
        g.fillOval(0, 0, size - 1, size - 1);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        String s = "军";
        int x = (size - fm.stringWidth(s)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(s, x, y);
        g.dispose();
        return img;
    }
}
