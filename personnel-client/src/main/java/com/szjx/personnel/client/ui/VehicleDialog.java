package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.model.PersonnelOption;
import com.szjx.personnel.client.model.VehicleItem;
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
 * 车辆新增/编辑对话框
 */
public class VehicleDialog extends JDialog {

    private final ApiClient api;
    private final boolean editMode;
    private final VehicleItem editing;
    private final Runnable onSaved;

    private final JTextField plateField = new JTextField(10);
    private final JTextField brandField = new JTextField(14);
    private final JComboBox<String> typeBox = new JComboBox<String>();
    private final JComboBox<String> statusBox = new JComboBox<String>();
    private final JComboBox<PersonnelOption> driverBox = new JComboBox<PersonnelOption>();
    private final JTextField seatsField = new JTextField(6);
    private final JTextField mileageField = new JTextField(10);
    private final JTextArea remarkArea = new JTextArea(2, 16);

    public static void showAdd(Component parent, ApiClient api, Runnable onSaved) {
        new VehicleDialog(SwingUtilities.getWindowAncestor(parent), api, false, null, onSaved)
                .setVisible(true);
    }

    public static void showEdit(Component parent, ApiClient api, VehicleItem item, Runnable onSaved) {
        new VehicleDialog(SwingUtilities.getWindowAncestor(parent), api, true, item, onSaved)
                .setVisible(true);
    }

    private VehicleDialog(java.awt.Window parent, ApiClient api, boolean editMode,
                          VehicleItem editing, Runnable onSaved) {
        super(parent, editMode ? "编辑车辆" : "新增车辆", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.editMode = editMode;
        this.editing = editing;
        this.onSaved = onSaved;

        typeBox.setEditable(true);
        statusBox.setEditable(true);

        initForm();
        loadPersonnel();
        DictLoader.load(api, "vehicle_type", typeBox, null, new Runnable() {
            @Override
            public void run() {
                if (editMode && editing != null) {
                    typeBox.setSelectedItem(editing.getType());
                }
            }
        });
        DictLoader.load(api, "vehicle_status", statusBox, null, new Runnable() {
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
        setSize(460, 470);
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
        form.add(new JLabel("车牌号*:"), c);
        c.gridx = 1;
        c.weightx = 1;
        plateField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "如 京A12345");
        form.add(plateField, c);
        c.gridx = 2;
        c.weightx = 0;
        form.add(new JLabel("品牌型号:"), c);
        c.gridx = 3;
        form.add(brandField, c);

        y++;
        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("车型:"), c);
        c.gridx = 1;
        form.add(typeBox, c);
        c.gridx = 2;
        form.add(new JLabel("状态:"), c);
        c.gridx = 3;
        form.add(statusBox, c);

        y++;
        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("驾驶员:"), c);
        c.gridx = 1;
        form.add(driverBox, c);
        c.gridx = 2;
        form.add(new JLabel("核定人数:"), c);
        c.gridx = 3;
        form.add(seatsField, c);

        y++;
        c.gridx = 0;
        c.gridy = y;
        form.add(new JLabel("里程(公里):"), c);
        c.gridx = 1;
        form.add(mileageField, c);

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
                        driverBox.addItem(o);
                    }
                    if (editMode && editing != null) {
                        EquipmentDialog.selectPerson(driverBox, editing.getDriverId());
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
        }.execute();
    }

    private void fillForm(VehicleItem v) {
        plateField.setText(nvl(v.getPlateNo()));
        brandField.setText(nvl(v.getBrand()));
        seatsField.setText(v.getSeats() == null ? "" : String.valueOf(v.getSeats()));
        mileageField.setText(v.getMileage() == null ? "" : String.valueOf(v.getMileage()));
        remarkArea.setText(nvl(v.getRemark()));
        EquipmentDialog.selectPerson(driverBox, v.getDriverId());
    }

    private void doSave() {
        final VehicleItem v = new VehicleItem();
        v.setPlateNo(plateField.getText().trim());
        v.setBrand(brandField.getText().trim());
        v.setType(String.valueOf(typeBox.getSelectedItem()));
        v.setStatus(String.valueOf(statusBox.getSelectedItem()));
        PersonnelOption driver = (PersonnelOption) driverBox.getSelectedItem();
        v.setDriverId(driver == null ? null : driver.getId());
        if (!seatsField.getText().trim().isEmpty()) {
            try {
                v.setSeats(Integer.parseInt(seatsField.getText().trim()));
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "核定人数必须是整数", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        if (!mileageField.getText().trim().isEmpty()) {
            try {
                v.setMileage(Double.parseDouble(mileageField.getText().trim()));
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "里程必须是数字", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        v.setRemark(remarkArea.getText().trim());
        if (v.getPlateNo().isEmpty()) {
            JOptionPane.showMessageDialog(this, "车牌号不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (editMode) {
            v.setId(editing.getId());
        }

        setEnabled(false);
        new SwingWorker<JsonObject, Void>() {
            @Override
            protected JsonObject doInBackground() throws Exception {
                if (editMode) {
                    return api.put("/api/vehicle/" + editing.getId(), v);
                }
                return api.post("/api/vehicle", v);
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
                    JOptionPane.showMessageDialog(VehicleDialog.this, msg, "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
