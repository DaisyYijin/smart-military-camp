package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.config.AppConfig;
import com.szjx.personnel.client.net.ApiClient;
import com.szjx.personnel.client.net.ApiException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 登录窗口(现代扁平化样式)
 */
public class LoginFrame extends JFrame {

    private static final Color BRAND = new Color(0x1D63ED);
    private static final Color PAGE_BG = new Color(0xF0F4FA);

    private final AppConfig config;
    private final JTextField serverField;
    private final JTextField usernameField;
    private final JPasswordField passwordField;

    public LoginFrame(final AppConfig config) {
        this.config = config;
        setTitle("数智军营人员管理系统 - 登录");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(540, 480);
        setMinimumSize(new Dimension(500, 440));
        setLocationRelativeTo(null);

        // 页面背景
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(PAGE_BG);

        // 居中登录卡片
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);
        GridBagConstraints wc = new GridBagConstraints();
        wc.insets = new Insets(20, 20, 20, 20);

        RoundedPanel card = new RoundedPanel(18, Color.WHITE);
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(28, 38, 24, 38));
        card.setPreferredSize(new Dimension(410, 370));
        wrap.add(card, wc);
        page.add(wrap, BorderLayout.CENTER);
        add(page);

        // ---- 卡片内容 ----
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 0, 5, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        // 品牌图标 + 标题
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.CENTER;
        RoundedPanel badge = new RoundedPanel(14, BRAND);
        badge.setPreferredSize(new Dimension(52, 52));
        JLabel star = new JLabel("★", JLabel.CENTER);
        star.setFont(new Font("Dialog", Font.BOLD, 26));
        star.setForeground(Color.WHITE);
        badge.setLayout(new BorderLayout());
        badge.add(star, BorderLayout.CENTER);
        content.add(badge, c);

        c.gridy = 1;
        c.insets = new Insets(12, 0, 2, 0);
        JLabel title = new JLabel("数智军营人员管理系统", JLabel.CENTER);
        title.setFont(new Font("Dialog", Font.BOLD, 23));
        title.setForeground(new Color(0x1F2937));
        content.add(title, c);

        c.gridy = 2;
        c.insets = new Insets(0, 0, 16, 0);
        JLabel sub = new JLabel("Personnel Management System", JLabel.CENTER);
        sub.setFont(new Font("Dialog", Font.PLAIN, 12));
        sub.setForeground(new Color(0x9CA3AF));
        content.add(sub, c);

        // 服务器地址
        c.gridy = 3;
        c.insets = new Insets(3, 0, 3, 0);
        serverField = new JTextField(config.getServerUrl());
        styleInput(serverField, "服务器地址(服务端所在电脑的 IP:端口)");
        content.add(serverField, c);

        // 用户名
        c.gridy = 4;
        usernameField = new JTextField(config.getUsername());
        styleInput(usernameField, "用户名");
        content.add(usernameField, c);

        // 密码
        c.gridy = 5;
        passwordField = new JPasswordField();
        styleInput(passwordField, "密码");
        content.add(passwordField, c);

        // 登录按钮
        c.gridy = 6;
        c.insets = new Insets(14, 0, 2, 0);
        JButton loginBtn = new JButton("登  录");
        loginBtn.setFont(new Font("Dialog", Font.BOLD, 15));
        loginBtn.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; background: " + toCss(BRAND) + "; foreground: #FFFFFF;"
                        + " focusWidth: 0; innerFocusWidth: 0;");
        loginBtn.setPreferredSize(new Dimension(0, 42));
        loginBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doLogin();
            }
        });
        content.add(loginBtn, c);

        // 底部提示
        c.gridy = 7;
        c.insets = new Insets(8, 0, 0, 0);
        JLabel tip = new JLabel(
                "<html><div style='text-align:center;color:#9CA3AF;font-size:12px'>"
                        + "本机测试填 http://127.0.0.1:8080<br>"
                        + "服务端在另一台电脑时,填它的局域网地址,如 http://192.168.1.100:8080</div></html>",
                JLabel.CENTER);
        content.add(tip, c);

        card.add(content, BorderLayout.CENTER);

        passwordField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doLogin();
            }
        });
        getRootPane().setDefaultButton(loginBtn);
    }

    private void styleInput(JComponent field, String placeholder) {
        field.putClientProperty(FlatClientProperties.STYLE, "arc: 8;");
        field.setPreferredSize(new Dimension(0, 38));
        if (field instanceof JTextField) {
            ((JTextField) field).setFont(new Font("Dialog", Font.PLAIN, 15));
        }
        // 占位符提示
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
    }

    private static String toCss(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private void doLogin() {
        final String serverUrl = serverField.getText().trim();
        final String username = usernameField.getText().trim();
        final String password = new String(passwordField.getPassword());
        if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写服务器地址、用户名和密码", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final JButton source = (JButton) getRootPane().getDefaultButton();
        source.setEnabled(false);
        source.setText("登录中...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ApiClient api = new ApiClient(serverUrl);
                    JsonObject body = new JsonObject();
                    body.addProperty("username", username);
                    body.addProperty("password", password);
                    JsonObject resp = api.post("/api/auth/login", body);
                    final String token = resp.getAsJsonObject("data").get("token").getAsString();
                    config.setServerUrl(serverUrl);
                    config.setUsername(username);
                    config.save();
                    java.awt.EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            api.setToken(token);
                            MainFrame frame = new MainFrame(api, username);
                            frame.setVisible(true);
                            dispose();
                        }
                    });
                } catch (final Exception ex) {
                    final String msg = ex instanceof ApiException
                            ? ex.getMessage() : "登录失败:" + ex.getMessage();
                    java.awt.EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            source.setEnabled(true);
                            source.setText("登  录");
                            JOptionPane.showMessageDialog(LoginFrame.this, msg,
                                    "登录失败", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * 圆角背景面板
     */
    static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color fill;

        RoundedPanel(int radius, Color fill) {
            this.radius = radius;
            this.fill = fill;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
