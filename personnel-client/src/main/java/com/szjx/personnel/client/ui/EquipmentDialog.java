package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.model.EquipmentItem;
import com.szjx.personnel.client.model.PersonnelOption;
import com.szjx.personnel.client.net.ApiClient;
import com.szjx.personnel.client.net.ApiException;
import com.szjx.personnel.client.util.DictLoader;

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
 * 装备新增/编辑对话框
 */
public class EquipmentDialog extends JDialog {

    private final ApiClient api;
    private final boolean editMode;
    private final EquipmentItem editing;
    private final Runnable onSaved;

    private final JTextField nameField = new JTextField(16);
    private final JTextField codeField = new JTextField(12);
    private final JComboBox<String> categoryBox = new JComboBox<String>();
    private final JTextField modelField = new JTextField(12);
    private final JComboBox<String> statusBox = new JComboBox<String>();
    private final JComboBox<PersonnelOption> keeperBox = new JComboBox<PersonnelOption>();
    private final JTextField locationField = new JTextField(16);
    private final JTextField buyDateField = new JTextField(10);
    private final JTextArea remarkArea = new JTextArea(2, 16);

    public static void showAdd(Component parent, ApiClient api, Runnable onSaved) {
        new EquipmentDialog(SwingUtilities.getWindowAncestor(parent), api, false, null, onSaved)
                .setVisible(true);
    }

    public static void showEdit(Component parent, ApiClient api, EquipmentItem item, Runnable onSaved) {
        new EquipmentDialog(SwingUtilities.getWindowAncestor(parent), api, true, item, onSaved)
                .setVisible(true);
    }

    private EquipmentDialog(java.awt.Window parent, ApiClient api, boolean editMode,
                            EquipmentItem editing, Runnable onSaved) {
        super(parent, editMode ? "编辑装备" : "新增装备", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.editMode = editMode;
        this.editing = editing;
        this.onSaved = onSaved;

        categoryBox.setEditable(true);
        statusBox.setEditable(true);

        initForm();
        loadPersonnel();
        DictLoader.load(api, "equipment_category", categoryBox, null, new Runnable() {
            @Override
            public void run() {
                if (editMode && editing != null) {
                    categoryBox.setSelectedItem(editing.getCategory());
                }
            }
        });
        DictLoader.load(api, "equipment_status", statusBox, null, new Runnable() {
            @Override
            public void run() {
                if (editMode && editing != null) {
                    statusBox.setSelectedItem(editing.getStatus());
                }
            }
        });
        if (editMode && editing != null) {
            fillForm(editing);
        }
        setSize(460, 480);
        setLocationRelativeTo(parent);
    }

    private void initForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 8, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        int y = 0;

        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("装备名称*:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(nameField, c);
        c.gridx = 2;
        c.weightx = 0;
        form.add(new JLabel("编号:"), c);
        c.gridx = 3;
        form.add(codeField, c);

        y++;
        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("类别:"), c);
        c.gridx = 1;
        form.add(categoryBox, c);
        c.gridx = 2;
        form.add(new JLabel("型号:"), c);
        c.gridx = 3;
        form.add(modelField, c);

        y++;
        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("状态:"), c);
        c.gridx = 1;
        form.add(statusBox, c);
        c.gridx = 2;
        form.add(new JLabel("保管人:"), c);
        c.gridx = 3;
        form.add(keeperBox, c);

        y++;
        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("存放位置:"), c);
        c.gridx = 1;
        form.add(locationField, c);
        c.gridx = 2;
        form.add(new JLabel("购置日期:"), c);
        c.gridx = 3;
        buyDateField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "yyyy-MM-dd");
        form.add(buyDateField, c);

        y++;
        c.gridx = 0;
        c.gridy = y;
        c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("备注:"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        remarkArea.setLineWrap(true);
        JScrollPane remarkScroll = new JScrollPane(remarkArea);
        remarkScroll.setPreferredSize(new Dimension(300, 56));
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
                    for (PersonnelOption o : get()) {
                        keeperBox.addItem(o);
                    }
                    if (editMode && editing != null) {
                        selectPerson(keeperBox, editing.getKeeperId());
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
        }.execute();
    }

    static void selectPerson(JComboBox<PersonnelOption> box, Long id) {
        if (id == null || id <= 0) {
            return;
        }
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).getId() == id) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    private void fillForm(EquipmentItem e) {
        nameField.setText(nvl(e.getName()));
        codeField.setText(nvl(e.getCode()));
        modelField.setText(nvl(e.getModel()));
        locationField.setText(nvl(e.getLocation()));
        buyDateField.setText(nvl(e.getBuyDate()));
        remarkArea.setText(nvl(e.getRemark()));
        selectPerson(keeperBox, e.getKeeperId());
    }

    private void doSave() {
        final EquipmentItem e = new EquipmentItem();
        e.setName(nameField.getText().trim());
        e.setCode(codeField.getText().trim());
        e.setCategory(String.valueOf(categoryBox.getSelectedItem()));
        e.setModel(modelField.getText().trim());
        e.setStatus(String.valueOf(statusBox.getSelectedItem()));
        PersonnelOption keeper = (PersonnelOption) keeperBox.getSelectedItem();
        e.setKeeperId(keeper == null ? null : keeper.getId());
        e.setLocation(locationField.getText().trim());
        e.setBuyDate(buyDateField.getText().trim());
        e.setRemark(remarkArea.getText().trim());
        if (e.getName().isEmpty()) {
            JOptionPane.showMessageDialog(this, "装备名称不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (editMode) {
            e.setId(editing.getId());
        }

        setEnabled(false);
        new SwingWorker<JsonObject, Void>() {
            @Override
            protected JsonObject doInBackground() throws Exception {
                if (editMode) {
                    return api.put("/api/equipment/" + editing.getId(), e);
                }
                return api.post("/api/equipment", e);
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (onSaved != null) {
                        onSaved.run();
                    }
                    dispose();
                } catch (Exception ex) {
                    setEnabled(true);
                    String msg = ex instanceof ApiException ? ex.getMessage() : "保存失败:" + ex.getMessage();
                    JOptionPane.showMessageDialog(EquipmentDialog.this, msg, "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
