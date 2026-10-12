package com.szjx.personnel.server.launcher;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.plaf.FontUIResource;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 首次启动/端口冲突时的设置向导:
 * - 设置管理员账号与密码(首次必填,之后账号可改、密码留空表示不修改)
 * - 选择端口(可检测占用、可从空闲端口列表挑选,选定后持久化)
 */
public class SetupWizard extends javax.swing.JDialog {

    public static class Result {
        public int port;
        public String username;
        public char[] newPassword; // null/空 = 不修改
        public boolean confirmed = false;
    }

    private final boolean firstRun;
    private final Result result = new Result();

    private JTextField portField;
    private JTextField userField;
    private JLabel statusLabel;
    private JPanel suggestionPanel;
    private JPasswordField pwField;
    private JPasswordField pw2Field;
    private JLabel banner;

    private SetupWizard(boolean firstRun, int initialPort, String occupier, String initialUsername) {
        super(createOwner(), firstRun ? "数智军营人员管理系统 - 首次运行设置"
                : "数智军营人员管理系统 - 启动设置", true);
        this.firstRun = firstRun;
        setIconImage(AppIcons.appIcon());
        buildUi(initialPort, occupier, initialUsername);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cancel();
            }
        });
        pack();
        setMinimumSize(new Dimension(720, getHeight()));
        setSize(Math.max(720, getWidth()), getHeight());
        setLocationRelativeTo(null);
    }

    /** 统一的字段标签(显式 20 号,不依赖全局字体机制) */
    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 20f));
        return l;
    }

    private void buildUi(int initialPort, String occupier, String initialUsername) {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(20, 26, 20, 26));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 10, 8, 10);
        gc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        banner = new JLabel(
                occupier != null
                        ? "端口 " + initialPort + " 已被 " + occupier + " 占用,请换一个端口。"
                        : firstRun
                        ? "欢迎使用,请完成初始设置。"
                        : "确认设置后点击启动。");
        banner.setForeground(occupier == null ? new Color(0, 102, 153) : new Color(200, 40, 40));
        banner.setFont(banner.getFont().deriveFont(Font.BOLD, 24f));
        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 3;
        root.add(banner, gc);
        gc.gridwidth = 1;

        gc.gridy = row++;
        gc.gridx = 0;
        root.add(lbl("服务端口:"), gc);
        portField = new JTextField(String.valueOf(initialPort), 10);
        portField.setFont(portField.getFont().deriveFont(Font.PLAIN, 22f));
        portField.setPreferredSize(new Dimension(160, 48));
        gc.gridx = 1;
        gc.weightx = 1;
        root.add(portField, gc);
        JButton checkBtn = new JButton("检测占用");
        checkBtn.setFont(checkBtn.getFont().deriveFont(Font.PLAIN, 18f));
        checkBtn.setPreferredSize(new Dimension(140, 44));
        checkBtn.putClientProperty("JButton.buttonType", "roundRect");
        checkBtn.setFocusPainted(false);
        checkBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                checkPort();
            }
        });
        gc.gridx = 2;
        gc.weightx = 0;
        root.add(checkBtn, gc);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 17f));
        gc.gridx = 1;
        gc.gridy = row++;
        root.add(statusLabel, gc);

        suggestionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        suggestionPanel.setVisible(false);
        gc.gridx = 1;
        gc.gridy = row++;
        root.add(suggestionPanel, gc);

        root.add(new javax.swing.JSeparator(), gbcRow(gc, row++));

        JLabel pwTitle = new JLabel("管理员账号设置");
        pwTitle.setFont(pwTitle.getFont().deriveFont(Font.BOLD, 21f));
        gc.gridx = 0;
        gc.gridwidth = 3;
        gc.gridy = row++;
        root.add(pwTitle, gc);
        gc.gridwidth = 1;

        gc.gridy = row++;
        gc.gridx = 0;
        root.add(lbl("管理员账号:"), gc);
        userField = new JTextField(initialUsername == null ? "admin" : initialUsername, 14);
        userField.setFont(userField.getFont().deriveFont(Font.PLAIN, 22f));
        userField.setPreferredSize(new Dimension(230, 48));
        gc.gridx = 1;
        root.add(userField, gc);

        gc.gridy = row++;
        gc.gridx = 0;
        root.add(lbl(firstRun ? "设置密码(至少6位):" : "新密码(留空不改):"), gc);
        pwField = new JPasswordField(14);
        pwField.setFont(pwField.getFont().deriveFont(Font.PLAIN, 22f));
        pwField.setPreferredSize(new Dimension(230, 48));
        gc.gridx = 1;
        root.add(pwField, gc);

        gc.gridy = row++;
        gc.gridx = 0;
        root.add(lbl("确认密码:"), gc);
        pw2Field = new JPasswordField(14);
        pw2Field.setFont(pw2Field.getFont().deriveFont(Font.PLAIN, 22f));
        pw2Field.setPreferredSize(new Dimension(230, 48));
        gc.gridx = 1;
        root.add(pw2Field, gc);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton ok = new JButton(firstRun ? "保存并启动" : "启动系统");
        ok.setFont(ok.getFont().deriveFont(Font.PLAIN, 19f));
        ok.setPreferredSize(new Dimension(160, 48));
        ok.putClientProperty("JButton.buttonType", "roundRect");
        ok.setBackground(new Color(47, 111, 214));
        ok.setForeground(Color.WHITE);
        ok.setOpaque(true);
        ok.setFocusPainted(false);
        ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                trySave();
            }
        });
        JButton quit = new JButton("退出");
        quit.setFont(quit.getFont().deriveFont(Font.PLAIN, 19f));
        quit.setPreferredSize(new Dimension(110, 48));
        quit.putClientProperty("JButton.buttonType", "roundRect");
        quit.setFocusPainted(false);
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

    private GridBagConstraints gbcRow(GridBagConstraints gc, int row) {
        gc.gridx = 0;
        gc.gridwidth = 3;
        gc.gridy = row;
        gc.weightx = 0;
        return gc;
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
        setSize(Math.max(720, getWidth()), getHeight());
    }

    /** 端口被占用时,扫描一组空闲端口生成"改用 xxxx"按钮,用户挑哪个就填哪个 */
    private void fillSuggestions(int occupiedPort) {
        suggestionPanel.removeAll();
        List<Integer> free = PortProbe.findFreePorts(occupiedPort, 5);
        if (free.isEmpty()) {
            suggestionPanel.setVisible(false);
            return;
        }
        suggestionPanel.add(new JLabel("可改用:"));
        for (final Integer p : free) {
            JButton b = new JButton(String.valueOf(p));
            b.setFont(b.getFont().deriveFont(Font.PLAIN, 17f));
            b.setMargin(new Insets(2, 10, 2, 10));
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

        String username = userField.getText().trim();
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请设置管理员账号(登录时使用)。",
                    "账号不能为空", JOptionPane.WARNING_MESSAGE);
            userField.requestFocus();
            return;
        }
        if (username.length() > 30) {
            JOptionPane.showMessageDialog(this, "账号名过长(最多 30 个字符)。", "账号无效",
                    JOptionPane.WARNING_MESSAGE);
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
        result.username = username;
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
    public static Result show(final boolean firstRun, final int initialPort, final String occupier,
                              final String initialUsername) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            // 无显示环境兜底:直接取消,由调用方按无界面模式处理
            Result r = new Result();
            r.confirmed = false;
            return r;
        }
        final Result[] holder = new Result[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                holder[0] = new SetupWizard(firstRun, initialPort, occupier, initialUsername).showDialog();
            }
        });
        return holder[0];
    }

    /**
     * 放大 Swing 全局字体(所有界面元素按 1.35 倍放大,最小 15 号),
     * 须在创建任何界面组件之前调用。
     */
    /** 创建带图标的隐藏 owner frame,让 JDialog 在任务栏显示正确图标 */
    private static JFrame createOwner() {
        JFrame f = new JFrame();
        f.setUndecorated(true);
        f.setSize(0, 0);
        f.setIconImage(AppIcons.appIcon());
        return f;
    }

    public static void applyGlobalFontSize() {
        float scale = 1.7f;
        int minSize = 19;
        List<Object[]> toSet = new ArrayList<Object[]>();
        for (Map.Entry<Object, Object> e : UIManager.getLookAndFeelDefaults().entrySet()) {
            Object key = e.getKey();
            if (key == null || !(key instanceof String) || !((String) key).endsWith(".font")) {
                continue;
            }
            Object val = e.getValue();
            if (val instanceof Font) {
                Font f = (Font) val;
                int newSize = Math.max(minSize, Math.round(f.getSize() * scale));
                toSet.add(new Object[]{key, new FontUIResource(f.getName(), f.getStyle(), newSize)});
            }
        }
        for (Object[] kv : toSet) {
            UIManager.put(kv[0], kv[1]);
        }
    }
}
