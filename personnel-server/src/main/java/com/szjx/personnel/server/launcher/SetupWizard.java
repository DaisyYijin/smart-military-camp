package com.szjx.personnel.server.launcher;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 首次启动/端口冲突时的设置向导:
 * - 选择端口(可检测占用、可从空闲端口列表挑选,选定后持久化)
 * - 设置管理员密码(首次必填,之后留空表示不修改)
 */
public class SetupWizard extends JDialog {

    public static class Result {
        public int port;
        public char[] newPassword; // null/空 = 不修改
        public boolean confirmed = false;
    }

    private final boolean firstRun;
    private final Result result = new Result();

    private JTextField portField;
    private JLabel statusLabel;
    private JPanel suggestionPanel;
    private JPasswordField pwField;
    private JPasswordField pw2Field;
    private JLabel banner;

    private SetupWizard(boolean firstRun, int initialPort, String occupier) {
        super((JFrame) null, firstRun ? "数智军营人员管理系统 - 首次运行设置"
                : "数智军营人员管理系统 - 端口设置", true);
        this.firstRun = firstRun;
        buildUi(initialPort, occupier);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cancel();
            }
        });
        pack();
        setMinimumSize(new Dimension(460, getHeight()));
        setLocationRelativeTo(null);
    }

    private void buildUi(int initialPort, String occupier) {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 6, 5, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        banner = new JLabel(occupier == null
                ? "欢迎使用!首次运行需要进行简单设置。"
                : "端口 " + initialPort + " 已被 " + occupier + " 占用,请换一个端口。");
        banner.setForeground(occupier == null ? new Color(0, 102, 153) : new Color(200, 40, 40));
        banner.setFont(banner.getFont().deriveFont(Font.BOLD));
        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 3;
        root.add(banner, gc);
        gc.gridwidth = 1;

        gc.gridy = row++;
        gc.gridx = 0;
        root.add(new JLabel("服务端口:"), gc);
        portField = new JTextField(String.valueOf(initialPort), 8);
        gc.gridx = 1;
        gc.weightx = 1;
        root.add(portField, gc);
        JButton checkBtn = new JButton("检测占用");
        checkBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                checkPort();
            }
        });
        gc.gridx = 2;
        gc.weightx = 0;
        root.add(checkBtn, gc);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        gc.gridx = 1;
        gc.gridy = row++;
        root.add(statusLabel, gc);

        suggestionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        suggestionPanel.setVisible(false);
        gc.gridx = 1;
        gc.gridy = row++;
        root.add(suggestionPanel, gc);

        JSeparatorLike sep1 = new JSeparatorLike();
        gc.gridx = 0;
        gc.gridwidth = 3;
        gc.gridy = row++;
        root.add(sep1, gc);
        gc.gridwidth = 1;

        JLabel pwTitle = new JLabel(firstRun
                ? "设置管理员密码(登录账号 admin,至少 6 位)"
                : "修改管理员密码(账号 admin,留空表示不修改)");
        pwTitle.setFont(pwTitle.getFont().deriveFont(Font.BOLD));
        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 3;
        root.add(pwTitle, gc);
        gc.gridwidth = 1;

        gc.gridy = row++;
        gc.gridx = 0;
        root.add(new JLabel(firstRun ? "密码:" : "新密码:"), gc);
        pwField = new JPasswordField(14);
        gc.gridx = 1;
        root.add(pwField, gc);

        gc.gridy = row++;
        gc.gridx = 0;
        root.add(new JLabel("确认密码:"), gc);
        pw2Field = new JPasswordField(14);
        gc.gridx = 1;
        root.add(pw2Field, gc);

        JLabel tip = new JLabel("<html><body style='width:380px'>提示:局域网其他电脑可通过 "
                + "http://本机IP:端口 访问本系统;首次启动 Windows 防火墙弹窗时请点“允许访问”。"
                + "</body></html>");
        tip.setFont(tip.getFont().deriveFont(Font.PLAIN, 12f));
        tip.setForeground(Color.GRAY);
        gc.gridx = 0;
        gc.gridwidth = 3;
        gc.gridy = row++;
        root.add(tip, gc);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton ok = new JButton(firstRun ? "保存并启动" : "保存设置");
        ok.setPreferredSize(new Dimension(110, 28));
        ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                trySave();
            }
        });
        JButton quit = new JButton("退出");
        quit.setPreferredSize(new Dimension(80, 28));
        quit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                cancel();
            }
        });
        btns.add(quit);
        btns.add(ok);
        gc.gridy = row++;
        gc.gridwidth = 3;
        root.add(btns, gc);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(root, BorderLayout.CENTER);
        getRootPane().setDefaultButton(ok);
    }

    private void checkPort() {
        Integer port = parsePort();
        if (port == null) {
            return;
        }
        if (PortProbe.isFree(port)) {
            statusLabel.setText("端口 " + port + " 可用 ✓");
            statusLabel.setForeground(new Color(0, 138, 0));
            suggestionPanel.setVisible(false);
        } else {
            String occupier = PortProbe.describeOccupier(port);
            statusLabel.setText("端口 " + port + " 被占用"
                    + (occupier == null ? "" : ":" + occupier));
            statusLabel.setForeground(new Color(200, 40, 40));
            fillSuggestions(port);
        }
        suggestionPanel.revalidate();
        pack();
    }

    /** 端口被占用时,扫描一组空闲端口生成"改用 xxxx"按钮,用户挑哪个就填哪个 */
    private void fillSuggestions(int occupiedPort) {
        suggestionPanel.removeAll();
        java.util.List<Integer> free = PortProbe.findFreePorts(occupiedPort, 5);
        if (free.isEmpty()) {
            suggestionPanel.setVisible(false);
            return;
        }
        JLabel label = new JLabel("可改用:");
        suggestionPanel.add(label);
        for (final Integer p : free) {
            JButton b = new JButton(String.valueOf(p));
            b.setMargin(new Insets(1, 6, 1, 6));
            b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    portField.setText(String.valueOf(p));
                    checkPort();
                }
            });
            suggestionPanel.add(b);
        }
        suggestionPanel.setVisible(true);
    }

    private Integer parsePort() {
        String text = portField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            port = -1;
        }
        if (port < 1 || port > 65535) {
            JOptionPane.showMessageDialog(this, "端口必须是 1-65535 之间的数字:" + text, "端口无效",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return port;
    }

    private void trySave() {
        Integer port = parsePort();
        if (port == null) {
            return;
        }
        if (!PortProbe.isFree(port)) {
            String occupier = PortProbe.describeOccupier(port);
            banner.setText("端口 " + port + " 已被 " + (occupier == null ? "其他程序" : occupier)
                    + " 占用,请换一个端口。");
            banner.setForeground(new Color(200, 40, 40));
            fillSuggestions(port);
            pack();
            return;
        }

        String pw = new String(pwField.getPassword());
        String pw2 = new String(pw2Field.getPassword());
        if (firstRun && pw.length() < 6) {
            JOptionPane.showMessageDialog(this, "首次运行必须设置至少 6 位的管理员密码。",
                    "请设置密码", JOptionPane.WARNING_MESSAGE);
            pwField.requestFocus();
            return;
        }
        if (pw.length() > 0) {
            if (pw.length() < 6) {
                JOptionPane.showMessageDialog(this, "密码至少需要 6 位。", "密码太短",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!pw.equals(pw2)) {
                JOptionPane.showMessageDialog(this, "两次输入的密码不一致。", "密码不一致",
                        JOptionPane.WARNING_MESSAGE);
                pw2Field.requestFocus();
                return;
            }
        }

        result.port = port;
        result.newPassword = pw.length() == 0 ? null : pw.toCharArray();
        result.confirmed = true;
        dispose();
    }

    private void cancel() {
        result.confirmed = false;
        dispose();
    }

    private Result showDialog() {
        setVisible(true);
        return result;
    }

    /** 在 EDT 上弹出向导并阻塞调用线程,直到用户保存或退出 */
    public static Result show(final boolean firstRun, final int initialPort, final String occupier)
            throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            // 无显示环境兜底:直接取消,由调用方按无界面模式处理
            Result r = new Result();
            r.confirmed = false;
            return r;
        }
        final Result[] holder = new Result[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                holder[0] = new SetupWizard(firstRun, initialPort, occupier).showDialog();
            }
        });
        return holder[0];
    }

    /** 简单分隔线(避免引入更多组件) */
    private static class JSeparatorLike extends javax.swing.JSeparator {
        JSeparatorLike() {
            setPreferredSize(new Dimension(1, 1));
        }
    }
}
