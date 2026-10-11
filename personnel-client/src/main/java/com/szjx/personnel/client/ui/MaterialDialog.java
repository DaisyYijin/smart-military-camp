package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.model.MaterialItem;
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
 * 物资新增/编辑对话框
 */
public class MaterialDialog extends JDialog {

    private final ApiClient api;
    private final boolean editMode;
    private final MaterialItem editing;
    private final Runnable onSaved;

    private final JTextField nameField = new JTextField(16);
    private final JComboBox<String> categoryBox = new JComboBox<String>();
    private final JTextField specField = new JTextField(12);
    private final JTextField unitField = new JTextField(6);
    private final JTextField quantityField = new JTextField(8);
    private final JTextField minField = new JTextField(8);
    private final JComboBox<PersonnelOption> keeperBox = new JComboBox<PersonnelOption>();
    private final JTextField locationField = new JTextField(16);
    private final JTextArea remarkArea = new JTextArea(2, 16);

    public static void showAdd(Component parent, ApiClient api, Runnable onSaved) {
        new MaterialDialog(SwingUtilities.getWindowAncestor(parent), api, false, null, onSaved)
                .setVisible(true);
    }

    public static void showEdit(Component parent, ApiClient api, MaterialItem item, Runnable onSaved) {
        new MaterialDialog(SwingUtilities.getWindowAncestor(parent), api, true, item, onSaved)
                .setVisible(true);
    }

    private MaterialDialog(java.awt.Window parent, ApiClient api, boolean editMode,
                           MaterialItem editing, Runnable onSaved) {
        super(parent, editMode ? "编辑物资" : "新增物资", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.editMode = editMode;
        this.editing = editing;
        this.onSaved = onSaved;

        categoryBox.setEditable(true);

        initForm();
        loadPersonnel();
        DictLoader.load(api, "material_category", categoryBox, null, new Runnable() {
            @Override
            public void run() {
                if (editMode && editing != null) {
                    categoryBox.setSelectedItem(editing.getCategory());
                }
            }
        });
        if (editMode && editing != null) {
            fillForm(editing);
        }
        setSize(460, 500);
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
        form.add(new JLabel("物资名称*:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(nameField, c);
        c.gridx = 2;
        c.weightx = 0;
        form.add(new JLabel("分类:"), c);
        c.gridx = 3;
        form.add(categoryBox, c);

        y++;
        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("规格:"), c);
        c.gridx = 1;
        form.add(specField, c);
        c.gridx = 2;
        form.add(new JLabel("单位:"), c);
        c.gridx = 3;
        form.add(unitField, c);

        y++;
        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("数量*:"), c);
        c.gridx = 1;
        quantityField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "如 50");
        form.add(quantityField, c);
        c.gridx = 2;
        form.add(new JLabel("预警下限:"), c);
        c.gridx = 3;
        minField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "低于此值预警");
        form.add(minField, c);

        y++;
        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("责任人:"), c);
        c.gridx = 1;
        form.add(keeperBox, c);
        c.gridx = 2;
        form.add(new JLabel("存放位置:"), c);
        c.gridx = 3;
        form.add(locationField, c);

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
                        EquipmentDialog.selectPerson(keeperBox, editing.getKeeperId());
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
        }.execute();
    }

    private void fillForm(MaterialItem m) {
        nameField.setText(nvl(m.getName()));
        specField.setText(nvl(m.getSpec()));
        unitField.setText(nvl(m.getUnit()));
        quantityField.setText(m.getQuantity() == null ? "" : String.valueOf(m.getQuantity()));
        minField.setText(m.getMinQuantity() == null ? "" : String.valueOf(m.getMinQuantity()));
        locationField.setText(nvl(m.getLocation()));
        remarkArea.setText(nvl(m.getRemark()));
        EquipmentDialog.selectPerson(keeperBox, m.getKeeperId());
    }

    private void doSave() {
        final MaterialItem m = new MaterialItem();
        m.setName(nameField.getText().trim());
        m.setCategory(String.valueOf(categoryBox.getSelectedItem()));
        m.setSpec(specField.getText().trim());
        m.setUnit(unitField.getText().trim());
        try {
            m.setQuantity(parseDouble(quantityField.getText(), "数量"));
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!minField.getText().trim().isEmpty()) {
            try {
                m.setMinQuantity(parseDouble(minField.getText(), "预警下限"));
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        PersonnelOption keeper = (PersonnelOption) keeperBox.getSelectedItem();
        m.setKeeperId(keeper == null ? null : keeper.getId());
        m.setLocation(locationField.getText().trim());
        m.setRemark(remarkArea.getText().trim());
        if (m.getName().isEmpty()) {
            JOptionPane.showMessageDialog(this, "物资名称不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (editMode) {
            m.setId(editing.getId());
        }

        setEnabled(false);
        new SwingWorker<JsonObject, Void>() {
            @Override
            protected JsonObject doInBackground() throws Exception {
                if (editMode) {
                    return api.put("/api/material/" + editing.getId(), m);
                }
                return api.post("/api/material", m);
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
                    JOptionPane.showMessageDialog(MaterialDialog.this, msg, "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private Double parseDouble(String s, String label) {
        try {
            double v = Double.parseDouble(s.trim());
            if (v < 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "必须是大于等于 0 的数字");
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
