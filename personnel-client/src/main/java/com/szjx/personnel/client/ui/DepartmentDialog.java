package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.szjx.personnel.client.model.DepartmentNode;
import com.szjx.personnel.client.net.ApiClient;
import com.szjx.personnel.client.net.ApiException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门新增/编辑对话框
 */
public class DepartmentDialog extends JDialog {

    private final ApiClient api;
    private final boolean editMode;
    private final Long editId;
    private final Runnable onSaved;

    private final JTextField nameField = new JTextField(18);
    private final JComboBox<Long> parentBox = new JComboBox<Long>();
    private final JTextField sortField = new JTextField(4);
    private final JTextField remarkField = new JTextField(18);

    private final Map<Long, String> allNames = new HashMap<Long, String>();

    private DepartmentDialog(java.awt.Window parent, ApiClient api, boolean editMode,
                             DepartmentNode editing, Long presetParent,
                             List<DepartmentNode> deptTree, Runnable onSaved) {
        super(parent, editMode ? "编辑部门" : "新增部门", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.editMode = editMode;
        this.editId = editing == null ? null : editing.getId();
        this.onSaved = onSaved;

        collectNames(deptTree);
        initForm(deptTree, editing, presetParent);
        if (editMode && editing != null) {
            nameField.setText(editing.getName());
            sortField.setText(String.valueOf(editing.getSort()));
            remarkField.setText(nvl(editing.getRemark()));
            selectParent(editing.getParentId());
        }
        setSize(440, 330);
        setLocationRelativeTo(parent);
    }

    public static void showAdd(java.awt.Component parent, ApiClient api, Long presetParent,
                               List<DepartmentNode> deptTree, Runnable onSaved) {
        new DepartmentDialog(SwingUtilities.getWindowAncestor(parent), api, false, null, presetParent, deptTree, onSaved)
                .setVisible(true);
    }

    public static void showEdit(java.awt.Component parent, ApiClient api, DepartmentNode editing,
                                List<DepartmentNode> deptTree, Runnable onSaved) {
        new DepartmentDialog(SwingUtilities.getWindowAncestor(parent), api, true, editing, null, deptTree, onSaved)
                .setVisible(true);
    }

    private void collectNames(List<DepartmentNode> tree) {
        for (DepartmentNode n : tree) {
            allNames.put(n.getId(), n.getName());
            if (n.getChildren() != null) {
                collectNames(n.getChildren());
            }
        }
    }

    private void initForm(List<DepartmentNode> deptTree, DepartmentNode editing, Long presetParent) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 8, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("部门名称*:"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(nameField, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        form.add(new JLabel("上级部门:"), c);
        c.gridx = 1;
        parentBox.addItem(0L);
        for (DepartmentNode n : deptTree) {
            addOptions(n, 0, editing == null ? -1 : editing.getId());
        }
        parentBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                                                                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Long) {
                    Long id = (Long) value;
                    label.setText(id == 0L ? "无(顶级部门)" : String.valueOf(allNames.get(id)));
                }
                return label;
            }
        });
        if (presetParent != null && presetParent > 0) {
            selectParent(presetParent);
        }
        form.add(parentBox, c);

        c.gridx = 0;
        c.gridy = 2;
        form.add(new JLabel("排序号:"), c);
        c.gridx = 1;
        sortField.setText("0");
        form.add(sortField, c);

        c.gridx = 0;
        c.gridy = 3;
        form.add(new JLabel("备注:"), c);
        c.gridx = 1;
        form.add(remarkField, c);

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

    private void addOptions(DepartmentNode n, int depth, long excludeId) {
        if (n.getId() == excludeId) {
            return; // 不能选自己
        }
        parentBox.addItem(n.getId());
        if (n.getChildren() != null) {
            for (DepartmentNode child : n.getChildren()) {
                addOptions(child, depth + 1, excludeId);
            }
        }
    }

    private void selectParent(Long id) {
        if (id == null) {
            parentBox.setSelectedItem(0L);
            return;
        }
        for (int i = 0; i < parentBox.getItemCount(); i++) {
            if (id.equals(parentBox.getItemAt(i))) {
                parentBox.setSelectedIndex(i);
                return;
            }
        }
        parentBox.setSelectedItem(0L);
    }

    private void doSave() {
        final String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "部门名称不能为空", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int sort;
        try {
            sort = Integer.parseInt(sortField.getText().trim());
        } catch (NumberFormatException e) {
            sort = 0;
        }
        final DepartmentNode d = new DepartmentNode();
        d.setName(name);
        Object parentSel = parentBox.getSelectedItem();
        d.setParentId(parentSel instanceof Long ? ((Long) parentSel).longValue() : 0L);
        d.setSort(sort);
        d.setRemark(remarkField.getText().trim());
        if (editMode) {
            d.setId(editId);
        }

        setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (editMode) {
                    api.put("/api/departments/" + editId, d);
                } else {
                    api.post("/api/departments", d);
                }
                return null;
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
                    JOptionPane.showMessageDialog(DepartmentDialog.this, msg, "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
