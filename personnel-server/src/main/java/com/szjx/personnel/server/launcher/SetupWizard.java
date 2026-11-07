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
    private JLabel dbSummaryLabel;
    private JTextField userField;
    private JLabel statusLabel;
    private JPanel suggestionPanel;
    private JPasswordField pwField;
    private JPasswordField pw2Field;
    private JLabel banner;

    private SetupWizard(boolean firstRun, int initialPort, String occupier, String initialUsername) {
        super(Launcher.OWNER_FRAME != null ? Launcher.OWNER_FRAME : (java.awt.Frame) null,
                firstRun ? "数智军营人员管理系统 - 首次运行设置"
                : "数智军营人员管理系统 - 启动设置", true); // 挂全局 owner:向导期间 owner 可见,任务栏有按钮
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

        // ---- 数据库(可选):默认内置 SQLite,点「数据库设置…」弹窗配置 ----
        root.add(new javax.swing.JSeparator(), gbcRow(gc, row++));
        gc.gridy = row++;
        gc.gridx = 0;
        root.add(lbl("数据库:"), gc);
        JPanel dbLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        dbSummaryLabel = new JLabel(" ");
        dbSummaryLabel.setFont(dbSummaryLabel.getFont().deriveFont(Font.PLAIN, 17f));
        dbLine.add(dbSummaryLabel);
        JButton dbBtn = new JButton("数据库设置…");
        dbBtn.setFont(dbBtn.getFont().deriveFont(Font.PLAIN, 16f));
        dbBtn.setPreferredSize(new Dimension(150, 36));
        dbBtn.putClientProperty("JButton.buttonType", "roundRect");
        dbBtn.setFocusPainted(false);
        dbBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { openDbDialog(); }
        });
        dbLine.add(dbBtn);
        gc.gridx = 1;
        root.add(dbLine, gc);
        refreshDbSummary();

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

    /** 向导页摘要:内置或 外部库·地址:端口/库名。 */
    private void refreshDbSummary() {
        com.szjx.personnel.server.util.DbProfile p = com.szjx.personnel.server.util.DbProfile.load();
        if (!p.external()) {
            dbSummaryLabel.setText("内置 SQLite(单机推荐)");
            return;
        }
        String n = "mysql".equals(p.dialect) ? "MySQL" : "postgresql".equals(p.dialect) ? "PostgreSQL"
                : "kingbase".equals(p.dialect) ? "人大金仓" : "达梦";
        dbSummaryLabel.setText(n + " · " + p.host + ":" + p.port + "/" + p.database);
    }

    /** 数据库设置弹窗:标签右对齐+字段等宽两列对齐;「确定」即保存并刷新摘要(随后的 Spring 启动读取生效)。 */
    private void openDbDialog() {
        final com.szjx.personnel.server.util.DbProfile profile = com.szjx.personnel.server.util.DbProfile.load();
        final JDialog dlg = new JDialog(this, "数据库设置", true);
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(18, 24, 14, 24));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(7, 10, 7, 4);
        gc.fill = GridBagConstraints.HORIZONTAL;
        final int[] rowRef = {0};

        final javax.swing.JComboBox<String> typeBox = new javax.swing.JComboBox<String>(new String[]{
                "内置 SQLite(单机推荐)", "MySQL / MariaDB", "PostgreSQL", "人大金仓 KingbaseES", "达梦 DM8"});
        typeBox.setFont(typeBox.getFont().deriveFont(Font.PLAIN, 18f));
        typeBox.setSelectedIndex(
                "mysql".equals(profile.dialect) ? 1 : "postgresql".equals(profile.dialect) ? 2
                : "kingbase".equals(profile.dialect) ? 3 : "dm".equals(profile.dialect) ? 4 : 0);

        final JTextField hostF = new JTextField(profile.host, 18);
        final JTextField portF = new JTextField(profile.port, 8);
        final JTextField nameF = new JTextField(profile.database, 18);
        final JTextField userF = new JTextField(profile.user, 16);
        final JPasswordField pwF = new JPasswordField(profile.password == null ? "" : profile.password, 16);
        final JTextField[] fields = {hostF, portF, nameF, userF, pwF};
        for (JTextField f : fields) {
            f.setFont(f.getFont().deriveFont(Font.PLAIN, 18f));
            f.setPreferredSize(new Dimension(240, 38));
        }
        final String[][] defaults = {{"127.0.0.1", "3306"}, {"127.0.0.1", "5432"}, {"127.0.0.1", "54321"}, {"127.0.0.1", "5236"}};
        final JLabel testLabel = new JLabel("选外部库时,请把驱动 jar 放入程序目录 lib/ 文件夹");
        testLabel.setFont(testLabel.getFont().deriveFont(Font.PLAIN, 14f));
        testLabel.setForeground(new Color(120, 130, 145));

        final Runnable sync = new Runnable() {
            @Override public void run() {
                int i = typeBox.getSelectedIndex() - 1;
                for (JTextField f : fields) { f.setEnabled(i >= 0); }
                if (i >= 0) {
                    if (hostF.getText().trim().isEmpty()) { hostF.setText(defaults[i][0]); }
                    if (portF.getText().trim().isEmpty()) { portF.setText(defaults[i][1]); }
                }
            }
        };
        typeBox.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { sync.run(); }
        });

        // 两列对齐:标签列右对齐等宽,字段列同宽补满
        final class Row {
            void add(String label, java.awt.Component field) {
                JLabel l = new JLabel(label, JLabel.RIGHT);
                l.setFont(l.getFont().deriveFont(Font.PLAIN, 18f));
                l.setPreferredSize(new Dimension(110, 38));
                gc.gridx = 0; gc.gridy = rowRef[0]; gc.weightx = 0;
                root.add(l, gc);
                gc.gridx = 1; gc.weightx = 1;
                root.add(field, gc);
                rowRef[0]++;
            }
        }
        Row r = new Row();
        r.add("数据库类型:", typeBox);
        r.add("服务器地址:", hostF);
        r.add("端口:", portF);
        r.add("数据库名:", nameF);
        r.add("用户名:", userF);
        r.add("密码:", pwF);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton testBtn = new JButton("测试连接");
        testBtn.setFont(testBtn.getFont().deriveFont(Font.PLAIN, 16f));
        testBtn.setPreferredSize(new Dimension(120, 36));
        testBtn.setFocusPainted(false);
        testBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (typeBox.getSelectedIndex() == 0) {
                    testLabel.setText("内置数据库无需测试。");
                    testLabel.setForeground(new Color(120, 130, 145));
                    return;
                }
                testLabel.setText("正在连接…");
                com.szjx.personnel.server.util.DbProfile p = com.szjx.personnel.server.util.DbProfile.load();
                int i = typeBox.getSelectedIndex();
                p.dialect = i == 1 ? "mysql" : i == 2 ? "postgresql" : i == 3 ? "kingbase" : "dm";
                p.host = hostF.getText().trim();
                p.port = portF.getText().trim();
                p.database = nameF.getText().trim();
                p.user = userF.getText().trim();
                p.password = new String(pwF.getPassword());
                String err = p.testConnection();
                testLabel.setForeground(err == null ? new Color(30, 130, 70) : new Color(200, 40, 40));
                testLabel.setText(err == null ? "连接成功" : "失败: " + err);
            }
        });
        actions.add(testBtn);
        actions.add(testLabel);
        gc.gridx = 0; gc.gridwidth = 2; gc.gridy = rowRef[0]++;
        root.add(actions, gc);
        gc.gridwidth = 1;

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(cancelBtn.getFont().deriveFont(Font.PLAIN, 16f));
        cancelBtn.setPreferredSize(new Dimension(100, 38));
        cancelBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { dlg.dispose(); }
        });
        JButton okBtn = new JButton("确定");
        okBtn.setFont(okBtn.getFont().deriveFont(Font.PLAIN, 16f));
        okBtn.setPreferredSize(new Dimension(100, 38));
        okBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                try {
                    com.szjx.personnel.server.util.DbProfile p = com.szjx.personnel.server.util.DbProfile.load();
                    int i = typeBox.getSelectedIndex();
                    p.dialect = i == 1 ? "mysql" : i == 2 ? "postgresql" : i == 3 ? "kingbase" : i == 4 ? "dm" : "sqlite";
                    p.host = hostF.getText().trim();
                    p.port = portF.getText().trim();
                    p.database = nameF.getText().trim();
                    p.user = userF.getText().trim();
                    p.password = new String(pwF.getPassword());
                    if (i > 0 && (p.host.isEmpty() || p.database.isEmpty())) {
                        JOptionPane.showMessageDialog(dlg, "请填写服务器地址与数据库名。", "配置不完整",
                                JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    p.save();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dlg, "保存失败: " + ex.getMessage(), "保存失败",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                refreshDbSummary();
                dlg.dispose();
            }
        });
        btns.add(cancelBtn);
        btns.add(okBtn);
        gc.gridx = 0; gc.gridwidth = 2; gc.gridy = rowRef[0]++;
        root.add(btns, gc);

        sync.run();
        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setResizable(false);
        dlg.setVisible(true);
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
        // 向导显示期间让 owner 可见(任务栏出现应用图标),关闭后隐藏——与 Launcher.showDialog 同机制。
        // 注: JDialog((Frame)null) 传 null 时 Swing 内部仍会挂一个隐藏共享 Frame,任务栏不会有按钮,
        // 因此必须挂"可见的" owner 才能在任务栏出现图标。
        final javax.swing.JFrame owner = Launcher.OWNER_FRAME;
        if (owner != null) {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() { owner.setVisible(true); }
                });
                Thread.sleep(200); // 等任务栏按钮注册完成
            } catch (Exception ignore) { /* 不影响向导 */ }
        }
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                holder[0] = new SetupWizard(firstRun, initialPort, occupier, initialUsername).showDialog();
            }
        });
        if (owner != null) {
            try {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() { owner.setVisible(false); }
                });
            } catch (Exception ignore) { /* 忽略 */ }
        }
        return holder[0];
    }

    /**
     * 放大 Swing 全局字体(所有界面元素按 1.35 倍放大,最小 15 号),
     * 须在创建任何界面组件之前调用。
     */


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
