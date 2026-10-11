package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.net.ApiClient;
import com.szjx.personnel.client.net.ApiException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 修改密码对话框
 */
public class ChangePasswordDialog extends JDialog {

    private final ApiClient api;
    private final JPasswordField oldField = new JPasswordField(16);
    private final JPasswordField newField = new JPasswordField(16);
    private final JPasswordField confirmField = new JPasswordField(16);

    public ChangePasswordDialog(java.awt.Window parent, ApiClient api) {
        super(parent, "修改密码", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        this.api = api;
        setSize(400, 280);
        setLocationRelativeTo(parent);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("旧密码:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(oldField, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        form.add(new JLabel("新密码:"), c);
        c.gridx = 1;
        form.add(newField, c);

        c.gridx = 0;
        c.gridy = 2;
        form.add(new JLabel("确认新密码:"), c);
        c.gridx = 1;
        form.add(confirmField, c);

        JLabel tip = new JLabel("密码长度不少于 6 位");
        tip.setForeground(java.awt.Color.GRAY);
        c.gridx = 1;
        c.gridy = 3;
        form.add(tip, c);

        add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton okBtn = new JButton("确 定");
        JButton cancelBtn = new JButton("取 消");
        okBtn.setPreferredSize(new Dimension(100, 30));
        okBtn.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; background: #1D63ED; foreground: #FFFFFF; focusWidth: 0; innerFocusWidth: 0;");
        okBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doChange();
            }
        });
        cancelBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        bottom.add(okBtn);
        bottom.add(cancelBtn);
        add(bottom, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okBtn);
    }

    private void doChange() {
        final String oldPwd = new String(oldField.getPassword());
        final String newPwd = new String(newField.getPassword());
        final String confirm = new String(confirmField.getPassword());
        if (oldPwd.isEmpty() || newPwd.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写完整", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!newPwd.equals(confirm)) {
            JOptionPane.showMessageDialog(this, "两次输入的新密码不一致", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (newPwd.length() < 6) {
            JOptionPane.showMessageDialog(this, "新密码长度不能少于 6 位", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                JsonObject body = new JsonObject();
                body.addProperty("oldPassword", oldPwd);
                body.addProperty("newPassword", newPwd);
                api.post("/api/auth/change-password", body);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(ChangePasswordDialog.this,
                            "密码修改成功", "提示", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception e) {
                    setEnabled(true);
                    String msg = e instanceof ApiException ? e.getMessage() : "修改失败:" + e.getMessage();
                    JOptionPane.showMessageDialog(ChangePasswordDialog.this, msg, "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
