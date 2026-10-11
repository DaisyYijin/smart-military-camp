package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.model.LeaveItem;
import com.szjx.personnel.client.model.PersonnelOption;
import com.szjx.personnel.client.net.ApiClient;
import com.szjx.personnel.client.net.ApiException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * 休假新增/编辑对话框
 */
public class LeaveDialog extends JDialog {

    private final ApiClient api;
    private final boolean editMode;
    private final LeaveItem editing;
    private final Runnable onSaved;

    private final JComboBox<PersonnelOption> personBox = new JComboBox<PersonnelOption>();
    private final JComboBox<String> typeBox = new JComboBox<String>();
    private final JTextField startField = new JTextField(10);
    private final JTextField endField = new JTextField(10);
    private final JTextField daysField = new JTextField(6);
    private final JTextField reasonField = new JTextField(18);
    private final JTextArea remarkArea = new JTextArea(3, 18);

    public static void showAdd(Component parent, ApiClient api, Runnable onSaved) {
        new LeaveDialog(SwingUtilities.getWindowAncestor(parent), api, false, null, onSaved)
                .setVisible(true);
    }

    public static void showEdit(Component parent, ApiClient api, LeaveItem item, Runnable onSaved) {
        new LeaveDialog(SwingUtilities.getWindowAncestor(parent), api, true, item, onSaved)
                .setVisible(true);
    }

    private LeaveDialog(java.awt.Window parent, ApiClient api, boolean editMode,
                        LeaveItem editing, Runnable onSaved) {
        super(parent, editMode ? "编辑休假" : "新增休假", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.editMode = editMode;
        this.editing = editing;
        this.onSaved = onSaved;

        typeBox.setEditable(true);
        initForm();
        loadPersonnel();
        // 休假类型从字典加载
        com.szjx.personnel.client.util.DictLoader.load(api, "leave_type", typeBox, null, new Runnable() {
            @Override
            public void run() {
                if (editMode && editing != null) {
                    typeBox.setSelectedItem(editing.getLeaveType() != null && !editing.getLeaveType().isEmpty()
                            ? editing.getLeaveType() : "事假");
                }
            }
        });
        if (editMode && editing != null) {
            fillForm(editing);
        }
        setSize(460, 420);
        setLocationRelativeTo(parent);
    }

    private void initForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 8, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("休假人员*:"), c);
        c.gridx = 1;
        c.weightx = 1;
        personBox.setPreferredSize(new Dimension(0, 34));
        form.add(personBox, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        form.add(new JLabel("休假类型:"), c);
        c.gridx = 1;
        form.add(typeBox, c);

        c.gridx = 0;
        c.gridy = 2;
        form.add(new JLabel("开始日期*:"), c);
        c.gridx = 1;
        startField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "yyyy-MM-dd");
        form.add(startField, c);

        c.gridx = 0;
        c.gridy = 3;
        form.add(new JLabel("结束日期*:"), c);
        c.gridx = 1;
        endField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "yyyy-MM-dd");
        form.add(endField, c);

        c.gridx = 0;
        c.gridy = 4;
        form.add(new JLabel("天数*:"), c);
        c.gridx = 1;
        daysField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "如 3 或 1.5");
        form.add(daysField, c);

        c.gridx = 0;
        c.gridy = 5;
        form.add(new JLabel("事由:"), c);
        c.gridx = 1;
        form.add(reasonField, c);

        c.gridx = 0;
        c.gridy = 6;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("备注:"), c);
        c.gridx = 1;
        remarkArea.setLineWrap(true);
        JScrollPane remarkScroll = new JScrollPane(remarkArea);
        remarkScroll.setPreferredSize(new Dimension(280, 64));
        form.add(remarkScroll, c);

        add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton saveBtn = new JButton("保 存");
        JButton cancelBtn = new JButton("取 消");
        saveBtn.setPreferredSize(new Dimension(100, 32));
        saveBtn.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; background: #1D63ED; foreground: #FFFFFF; focusWidth: 0; innerFocusWidth: 0;");
        saveBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doSave();
            }
        });
        cancelBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        bottom.add(saveBtn);
        bottom.add(cancelBtn);
        add(bottom, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(saveBtn);
    }

    private void loadPersonnel() {
        new SwingWorker<List<PersonnelOption>, Void>() {
            @Override
            protected List<PersonnelOption> doInBackground() throws Exception {
                JsonObject resp = api.get("/api/personnel/options");
                return new Gson().fromJson(resp.get("data"),
                        new com.google.gson.reflect.TypeToken<List<PersonnelOption>>() {
                        }.getType());
            }

            @Override
            protected void done() {
                try {
                    List<PersonnelOption> list = get();
                    for (PersonnelOption o : list) {
                        personBox.addItem(o);
                    }
                    if (editMode && editing != null) {
                        selectPerson(editing.getPersonnelId());
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(LeaveDialog.this,
                            "人员列表加载失败:" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void selectPerson(Long id) {
        if (id == null) {
            return;
        }
        for (int i = 0; i < personBox.getItemCount(); i++) {
            if (personBox.getItemAt(i).getId() == id) {
                personBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void fillForm(LeaveItem r) {
        selectPerson(r.getPersonnelId());
        startField.setText(nvl(r.getStartDate()));
        endField.setText(nvl(r.getEndDate()));
        daysField.setText(r.getDays() == null ? "" : String.valueOf(r.getDays()));
        reasonField.setText(nvl(r.getReason()));
        remarkArea.setText(nvl(r.getRemark()));
    }

    private void doSave() {
        PersonnelOption selected = (PersonnelOption) personBox.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "请选择休假人员", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final LeaveItem r = new LeaveItem();
        r.setPersonnelId(selected.getId());
        r.setLeaveType(String.valueOf(typeBox.getSelectedItem()));
        r.setStartDate(startField.getText().trim());
        r.setEndDate(endField.getText().trim());
        try {
            r.setDays(Double.parseDouble(daysField.getText().trim()));
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "天数必须是数字", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        r.setReason(reasonField.getText().trim());
        r.setRemark(remarkArea.getText().trim());
        if (r.getStartDate().isEmpty() || r.getEndDate().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写开始和结束日期", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (editMode) {
            r.setId(editing.getId());
        }

        setEnabled(false);
        new SwingWorker<JsonObject, Void>() {
            @Override
            protected JsonObject doInBackground() throws Exception {
                if (editMode) {
                    return api.put("/api/leave/" + editing.getId(), r);
                }
                return api.post("/api/leave", r);
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (onSaved != null) {
                        onSaved.run();
                    }
                    dispose();
                } catch (Exception e) {
                    setEnabled(true);
                    String msg = e instanceof ApiException ? e.getMessage() : "保存失败:" + e.getMessage();
                    JOptionPane.showMessageDialog(LeaveDialog.this, msg, "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
