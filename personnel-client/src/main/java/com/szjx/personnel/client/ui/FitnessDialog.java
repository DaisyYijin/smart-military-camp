package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.model.FitnessItem;
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
 * 体能考核新增/编辑对话框
 */
public class FitnessDialog extends JDialog {

    private final ApiClient api;
    private final boolean editMode;
    private final FitnessItem editing;
    private final Runnable onSaved;

    private final JComboBox<PersonnelOption> personBox = new JComboBox<PersonnelOption>();
    private final JComboBox<String> itemBox = new JComboBox<String>();
    private final JTextField scoreField = new JTextField(10);
    private final JComboBox<String> gradeBox = new JComboBox<String>();
    private final JTextField dateField = new JTextField(10);
    private final JTextField testerField = new JTextField(12);
    private final JTextArea remarkArea = new JTextArea(3, 18);

    public static void showAdd(Component parent, ApiClient api, Runnable onSaved) {
        new FitnessDialog(SwingUtilities.getWindowAncestor(parent), api, false, null, onSaved)
                .setVisible(true);
    }

    public static void showEdit(Component parent, ApiClient api, FitnessItem item, Runnable onSaved) {
        new FitnessDialog(SwingUtilities.getWindowAncestor(parent), api, true, item, onSaved)
                .setVisible(true);
    }

    private FitnessDialog(java.awt.Window parent, ApiClient api, boolean editMode,
                          FitnessItem editing, Runnable onSaved) {
        super(parent, editMode ? "编辑体能考核" : "新增体能考核", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.editMode = editMode;
        this.editing = editing;
        this.onSaved = onSaved;

        itemBox.setEditable(true);
        gradeBox.setEditable(true);

        initForm();
        loadPersonnel();
        // 项目与等级从字典加载
        com.szjx.personnel.client.util.DictLoader.load(api, "fitness_item", itemBox, null, new Runnable() {
            @Override
            public void run() {
                if (editMode && editing != null) {
                    itemBox.setSelectedItem(editing.getItem() != null && !editing.getItem().isEmpty()
                            ? editing.getItem() : "3000米跑");
                }
            }
        });
        com.szjx.personnel.client.util.DictLoader.load(api, "fitness_grade", gradeBox, null, new Runnable() {
            @Override
            public void run() {
                if (editMode && editing != null) {
                    gradeBox.setSelectedItem(editing.getGrade() != null && !editing.getGrade().isEmpty()
                            ? editing.getGrade() : "及格");
                }
            }
        });
        if (editMode && editing != null) {
            fillForm(editing);
        }
        setSize(460, 430);
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
        form.add(new JLabel("考核人员*:"), c);
        c.gridx = 1;
        c.weightx = 1;
        personBox.setPreferredSize(new Dimension(0, 34));
        form.add(personBox, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        form.add(new JLabel("考核项目*:"), c);
        c.gridx = 1;
        form.add(itemBox, c);

        c.gridx = 0;
        c.gridy = 2;
        form.add(new JLabel("成绩:"), c);
        c.gridx = 1;
        scoreField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "如 13分25秒 / 15个");
        form.add(scoreField, c);

        c.gridx = 0;
        c.gridy = 3;
        form.add(new JLabel("评定等级:"), c);
        c.gridx = 1;
        form.add(gradeBox, c);

        c.gridx = 0;
        c.gridy = 4;
        form.add(new JLabel("考核日期*:"), c);
        c.gridx = 1;
        dateField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "yyyy-MM-dd");
        form.add(dateField, c);

        c.gridx = 0;
        c.gridy = 5;
        form.add(new JLabel("考核人:"), c);
        c.gridx = 1;
        form.add(testerField, c);

        c.gridx = 0;
        c.gridy = 6;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("备注:"), c);
        c.gridx = 1;
        remarkArea.setLineWrap(true);
        JScrollPane remarkScroll = new JScrollPane(remarkArea);
        remarkScroll.setPreferredSize(new Dimension(280, 60));
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
                    JOptionPane.showMessageDialog(FitnessDialog.this,
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

    private void fillForm(FitnessItem r) {
        selectPerson(r.getPersonnelId());
        scoreField.setText(nvl(r.getScore()));
        dateField.setText(nvl(r.getTestDate()));
        testerField.setText(nvl(r.getTester()));
        remarkArea.setText(nvl(r.getRemark()));
    }

    private void doSave() {
        PersonnelOption selected = (PersonnelOption) personBox.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "请选择考核人员", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final FitnessItem r = new FitnessItem();
        r.setPersonnelId(selected.getId());
        r.setItem(String.valueOf(itemBox.getSelectedItem()));
        r.setScore(scoreField.getText().trim());
        r.setGrade(String.valueOf(gradeBox.getSelectedItem()));
        r.setTestDate(dateField.getText().trim());
        r.setTester(testerField.getText().trim());
        r.setRemark(remarkArea.getText().trim());
        if (r.getItem().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写考核项目", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (r.getTestDate().isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写考核日期", "提示", JOptionPane.WARNING_MESSAGE);
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
                    return api.put("/api/fitness/" + editing.getId(), r);
                }
                return api.post("/api/fitness", r);
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
                    JOptionPane.showMessageDialog(FitnessDialog.this, msg, "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
