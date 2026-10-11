package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.model.DictItem;
import com.szjx.personnel.client.model.DictType;
import com.szjx.personnel.client.net.ApiClient;
import com.szjx.personnel.client.net.ApiException;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 字典管理面板:左侧字典类型,右侧该类型的选项
 */
public class DictPanel extends JPanel {

    private final ApiClient api;

    private final DefaultListModel<DictType> typeModel = new DefaultListModel<DictType>();
    private final JList<DictType> typeList = new JList<DictType>(typeModel);

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"内容", "排序", "启用", "备注"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);

    private List<DictItem> currentItems = new ArrayList<DictItem>();

    public DictPanel(ApiClient api) {
        this.api = api;
        setLayout(new BorderLayout());

        initUI();
        loadTypes();
    }

    private void initUI() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(makeButton("新增", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onAdd();
            }
        }));
        toolbar.add(makeButton("编辑", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editSelected();
            }
        }));
        toolbar.add(makeButton("删除", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onDelete();
            }
        }));
        toolbar.add(makeButton("启用/停用", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleEnabled();
            }
        }));
        toolbar.addSeparator();
        toolbar.add(makeButton("刷新", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadTypes();
            }
        }));
        add(toolbar, BorderLayout.NORTH);

        // 左侧类型
        typeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        typeList.setFixedCellHeight(34);
        typeList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            @Override
            public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                DictType t = typeList.getSelectedValue();
                if (t != null) {
                    refreshTable(t.getItems());
                }
            }
        });
        JScrollPane typeScroll = new JScrollPane(typeList);
        typeScroll.setPreferredSize(new Dimension(190, 0));
        typeScroll.setBorder(BorderFactory.createTitledBorder("字典类型"));

        // 右侧表格
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    editSelected();
                }
            }
        });
        JScrollPane tableScroll = new JScrollPane(table);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, typeScroll, tableScroll);
        split.setDividerLocation(200);
        add(split, BorderLayout.CENTER);
    }

    private JButton makeButton(String text, ActionListener listener) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 8; focusWidth: 0; innerFocusWidth: 0;");
        b.addActionListener(listener);
        return b;
    }

    private void loadTypes() {
        new SwingWorker<List<DictType>, Void>() {
            @Override
            protected List<DictType> doInBackground() throws Exception {
                JsonObject resp = api.get("/api/dict/types");
                return new Gson().fromJson(resp.get("data"),
                        new com.google.gson.reflect.TypeToken<List<DictType>>() {
                        }.getType());
            }

            @Override
            protected void done() {
                try {
                    List<DictType> list = get();
                    DictType selected = typeList.getSelectedValue();
                    typeModel.clear();
                    for (DictType t : list) {
                        typeModel.addElement(t);
                    }
                    if (list.size() > 0) {
                        // 尽量保持原选中
                        int idx = -1;
                        if (selected != null) {
                            for (int i = 0; i < list.size(); i++) {
                                if (list.get(i).getType().equals(selected.getType())) {
                                    idx = i;
                                    break;
                                }
                            }
                        }
                        typeList.setSelectedIndex(idx < 0 ? 0 : idx);
                    } else {
                        refreshTable(new ArrayList<DictItem>());
                    }
                } catch (Exception e) {
                    showError(e);
                }
            }
        }.execute();
    }

    private void refreshTable(List<DictItem> items) {
        currentItems = items == null ? new ArrayList<DictItem>() : items;
        tableModel.setRowCount(0);
        for (DictItem d : currentItems) {
            tableModel.addRow(new Object[]{
                    nvl(d.getDictLabel()),
                    d.getSort() == null ? "" : d.getSort(),
                    d.getEnabled() != null && d.getEnabled() ? "是" : "否",
                    nvl(d.getRemark())
            });
        }
    }

    private DictType currentType() {
        return typeList.getSelectedValue();
    }

    private DictItem selectedItem() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= currentItems.size()) {
            return null;
        }
        return currentItems.get(row);
    }

    private void onAdd() {
        DictType t = currentType();
        if (t == null) {
            JOptionPane.showMessageDialog(this, "请先在左侧选择字典类型", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        DictEditDialog.showAdd(this, api, t.getType(), t.getName(), new Runnable() {
            @Override
            public void run() {
                loadTypes();
            }
        });
    }

    private void editSelected() {
        DictItem item = selectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "请先选择要编辑的字典项", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        DictType t = currentType();
        DictEditDialog.showEdit(this, api, item, t == null ? "" : t.getName(), new Runnable() {
            @Override
            public void run() {
                loadTypes();
            }
        });
    }

    private void onDelete() {
        final DictItem item = selectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "请先选择要删除的字典项", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "确定删除字典项 \"" + item.getDictLabel() + "\"?\n"
                        + "业务数据中引用该选项的记录将被清空(不可恢复)",
                "确认删除", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        new SwingWorker<JsonObject, Void>() {
            @Override
            protected JsonObject doInBackground() throws Exception {
                return api.delete("/api/dict/" + item.getId());
            }

            @Override
            protected void done() {
                try {
                    JsonObject resp = get();
                    int cleared = resp.getAsJsonObject("data").get("cleared").getAsInt();
                    if (cleared > 0) {
                        JOptionPane.showMessageDialog(DictPanel.this,
                                "已删除,并同步清空 " + cleared + " 条业务记录中的该选项",
                                "删除完成", JOptionPane.INFORMATION_MESSAGE);
                    }
                    loadTypes();
                } catch (Exception e) {
                    showError(e);
                }
            }
        }.execute();
    }

    private void toggleEnabled() {
        final DictItem item = selectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "请先选择字典项", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final boolean target = item.getEnabled() == null || !item.getEnabled();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                DictItem copy = new DictItem();
                copy.setDictType(item.getDictType());
                copy.setDictLabel(item.getDictLabel());
                copy.setSort(item.getSort());
                copy.setEnabled(target);
                copy.setRemark(item.getRemark());
                api.put("/api/dict/" + item.getId(), copy);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadTypes();
                } catch (Exception e) {
                    showError(e);
                }
            }
        }.execute();
    }

    private void showError(Exception e) {
        String msg = e instanceof ApiException ? e.getMessage() : "操作失败:" + e.getMessage();
        JOptionPane.showMessageDialog(this, msg, "错误", JOptionPane.ERROR_MESSAGE);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 字典项新增/编辑对话框 */
    private static class DictEditDialog extends JDialog {

        private final ApiClient api;
        private final boolean editMode;
        private final String dictType;
        private final DictItem editing;
        private final Runnable onSaved;

        private final JTextField labelField = new JTextField(16);
        private final JTextField sortField = new JTextField("0", 4);
        private final javax.swing.JCheckBox enabledBox = new javax.swing.JCheckBox("启用", true);
        private final JTextField remarkField = new JTextField(16);

        static void showAdd(Component parent, ApiClient api, String dictType,
                            String typeName, Runnable onSaved) {
            new DictEditDialog(SwingUtilities.getWindowAncestor(parent), api, false,
                    dictType, null, typeName, onSaved).setVisible(true);
        }

        static void showEdit(Component parent, ApiClient api, DictItem item,
                             String typeName, Runnable onSaved) {
            new DictEditDialog(SwingUtilities.getWindowAncestor(parent), api, true,
                    item.getDictType(), item, typeName, onSaved).setVisible(true);
        }

        private DictEditDialog(java.awt.Window parent, ApiClient api, boolean editMode,
                               String dictType, DictItem editing, String typeName, Runnable onSaved) {
            super(parent, (editMode ? "编辑" : "新增") + "字典项", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
            this.api = api;
            this.editMode = editMode;
            this.dictType = dictType;
            this.editing = editing;
            this.onSaved = onSaved;

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createEmptyBorder(14, 16, 8, 16));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 6, 6, 6);
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;

            c.gridx = 0;
            c.gridy = 0;
            form.add(new JLabel("字典类型:"), c);
            c.gridx = 1;
            c.weightx = 1;
            form.add(new JLabel(typeName == null ? dictType : typeName), c);

            c.gridx = 0;
            c.gridy = 1;
            c.weightx = 0;
            form.add(new JLabel("内容*:"), c);
            c.gridx = 1;
            form.add(labelField, c);

            c.gridx = 0;
            c.gridy = 2;
            form.add(new JLabel("排序:"), c);
            c.gridx = 1;
            form.add(sortField, c);

            c.gridx = 0;
            c.gridy = 3;
            form.add(new JLabel("启用:"), c);
            c.gridx = 1;
            form.add(enabledBox, c);

            c.gridx = 0;
            c.gridy = 4;
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

            if (editMode && editing != null) {
                labelField.setText(nvl(editing.getDictLabel()));
                sortField.setText(editing.getSort() == null ? "0" : String.valueOf(editing.getSort()));
                enabledBox.setSelected(editing.getEnabled() == null || editing.getEnabled());
                remarkField.setText(nvl(editing.getRemark()));
            }
            setSize(380, 300);
            setLocationRelativeTo(parent);
        }

        private void doSave() {
            final String label = labelField.getText().trim();
            if (label.isEmpty()) {
                JOptionPane.showMessageDialog(this, "内容不能为空", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int sort;
            try {
                sort = Integer.parseInt(sortField.getText().trim());
            } catch (NumberFormatException e) {
                sort = 0;
            }
            final DictItem d = new DictItem();
            d.setDictType(dictType);
            d.setDictLabel(label);
            d.setSort(sort);
            d.setEnabled(enabledBox.isSelected());
            d.setRemark(remarkField.getText().trim());

            setEnabled(false);
            new SwingWorker<JsonObject, Void>() {
                @Override
                protected JsonObject doInBackground() throws Exception {
                    if (editMode) {
                        return api.put("/api/dict/" + editing.getId(), d);
                    }
                    return api.post("/api/dict", d);
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
                        JOptionPane.showMessageDialog(DictEditDialog.this, msg, "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }

        private String nvl(String s) {
            return s == null ? "" : s;
        }
    }
}
