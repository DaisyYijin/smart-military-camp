package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.model.DepartmentNode;
import com.szjx.personnel.client.model.PageData;
import com.szjx.personnel.client.model.PersonnelItem;
import com.szjx.personnel.client.net.ApiClient;
import com.szjx.personnel.client.net.ApiException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人员管理面板(部门树 + 人员表格)
 */
public class PersonnelPanel extends JPanel {

    private final ApiClient api;

    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("全部部门");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree deptTree = new JTree(treeModel);

    private final JTextField keywordField = new JTextField(14);
    private final JComboBox<String> statusBox = new JComboBox<String>();

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel pageLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("就绪");

    private long total = 0;
    private int page = 1;
    private final int pageSize = 20;

    private final Map<Long, String> deptNames = new HashMap<Long, String>();
    private final List<DepartmentNode> deptTreeData = new ArrayList<DepartmentNode>();
    private List<PersonnelItem> currentList = new ArrayList<PersonnelItem>();
    private int loadSeq = 0;

    public PersonnelPanel(ApiClient api) {
        this.api = api;
        setLayout(new BorderLayout());

        tableModel = new DefaultTableModel(new String[]{
                "姓名", "性别", "身份证号", "部门", "军衔", "职务", "状态", "手机号", "入伍日期"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
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

        initToolBar();
        initCenter();
        // 状态筛选从字典加载
        com.szjx.personnel.client.util.DictLoader.load(api, "personnel_status", statusBox, "全部状态", null);
        loadDepartments();
        loadPersonnel();
    }

    private void initToolBar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(makeButton("新增人员", new ActionListener() {
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
        toolbar.addSeparator();
        toolbar.add(makeButton("刷新", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadDepartments();
                loadPersonnel();
            }
        }));
        toolbar.add(makeButton("导出Excel", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onExport();
            }
        }));
        toolbar.add(makeButton("导入Excel", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onImport();
            }
        }));
        add(toolbar, BorderLayout.NORTH);
    }

    private JButton makeButton(String text, ActionListener listener) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 8; focusWidth: 0; innerFocusWidth: 0;");
        b.addActionListener(listener);
        return b;
    }

    private void initCenter() {
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filterPanel.add(new JLabel("关键字:"));
        filterPanel.add(keywordField);
        filterPanel.add(new JLabel("状态:"));
        filterPanel.add(statusBox);
        JButton searchBtn = new JButton("查询");
        searchBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                page = 1;
                loadPersonnel();
            }
        });
        filterPanel.add(searchBtn);
        keywordField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                page = 1;
                loadPersonnel();
            }
        });
        statusBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                page = 1;
                loadPersonnel();
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);

        JPanel pagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        JButton prev = new JButton("上一页");
        JButton next = new JButton("下一页");
        prev.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (page > 1) {
                    page--;
                    loadPersonnel();
                }
            }
        });
        next.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (page * pageSize < total) {
                    page++;
                    loadPersonnel();
                }
            }
        });
        pagePanel.add(prev);
        pagePanel.add(pageLabel);
        pagePanel.add(next);

        JPanel right = new JPanel(new BorderLayout());
        right.add(filterPanel, BorderLayout.NORTH);
        right.add(tableScroll, BorderLayout.CENTER);
        right.add(pagePanel, BorderLayout.SOUTH);

        // 部门树
        deptTree.setRootVisible(true);
        deptTree.setShowsRootHandles(true);
        deptTree.putClientProperty(FlatClientProperties.STYLE, "rowHeight: 30;");
        JScrollPane deptScroll = new JScrollPane(deptTree);
        deptScroll.setPreferredSize(new Dimension(190, 0));
        deptScroll.setBorder(BorderFactory.createTitledBorder("部门"));

        deptTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = deptTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getUserObject() instanceof DepartmentNode) {
                            DepartmentDialog.showEdit(PersonnelPanel.this, api,
                                    (DepartmentNode) node.getUserObject(), deptTreeData,
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            loadDepartments();
                                        }
                                    });
                        }
                    }
                }
            }
        });

        // 右键菜单
        final JPopupMenu deptMenu = new JPopupMenu();
        JMenuItem addItem = new JMenuItem("新增子部门");
        JMenuItem editItem = new JMenuItem("编辑部门");
        JMenuItem delItem = new JMenuItem("删除部门");
        addItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DefaultMutableTreeNode sel = (DefaultMutableTreeNode) deptTree.getLastSelectedPathComponent();
                Long parentId = sel != null && sel.getUserObject() instanceof DepartmentNode
                        ? ((DepartmentNode) sel.getUserObject()).getId() : 0L;
                DepartmentDialog.showAdd(PersonnelPanel.this, api, parentId, deptTreeData,
                        new Runnable() {
                            @Override
                            public void run() {
                                loadDepartments();
                            }
                        });
            }
        });
        editItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DefaultMutableTreeNode sel = (DefaultMutableTreeNode) deptTree.getLastSelectedPathComponent();
                if (sel != null && sel.getUserObject() instanceof DepartmentNode) {
                    DepartmentDialog.showEdit(PersonnelPanel.this, api,
                            (DepartmentNode) sel.getUserObject(), deptTreeData,
                            new Runnable() {
                                @Override
                                public void run() {
                                    loadDepartments();
                                }
                            });
                }
            }
        });
        delItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DefaultMutableTreeNode sel = (DefaultMutableTreeNode) deptTree.getLastSelectedPathComponent();
                if (sel != null && sel.getUserObject() instanceof DepartmentNode) {
                    DepartmentNode d = (DepartmentNode) sel.getUserObject();
                    if (JOptionPane.showConfirmDialog(PersonnelPanel.this,
                            "确定删除部门 \"" + d.getName() + "\"?", "确认", JOptionPane.YES_NO_OPTION)
                            == JOptionPane.YES_OPTION) {
                        deleteDepartment(d.getId());
                    }
                }
            }
        });
        deptMenu.add(addItem);
        deptMenu.add(editItem);
        deptMenu.add(delItem);
        deptTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMenu(e, deptMenu);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMenu(e, deptMenu);
            }

            private void maybeShowMenu(MouseEvent e, JPopupMenu menu) {
                if (e.isPopupTrigger()) {
                    TreePath path = deptTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        deptTree.setSelectionPath(path);
                        menu.show(deptTree, e.getX(), e.getY());
                    }
                }
            }
        });
        deptTree.addTreeSelectionListener(new javax.swing.event.TreeSelectionListener() {
            @Override
            public void valueChanged(javax.swing.event.TreeSelectionEvent e) {
                page = 1;
                loadPersonnel();
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, deptScroll, right);
        split.setDividerLocation(200);
        add(split, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(statusLabel, BorderLayout.SOUTH);
    }

    // ---------- 部门 ----------

    private void loadDepartments() {
        new SwingWorker<List<DepartmentNode>, Void>() {
            @Override
            protected List<DepartmentNode> doInBackground() throws Exception {
                JsonObject resp = api.get("/api/departments");
                return new com.google.gson.Gson().fromJson(resp.get("data"),
                        new com.google.gson.reflect.TypeToken<List<DepartmentNode>>() {
                        }.getType());
            }

            @Override
            protected void done() {
                try {
                    List<DepartmentNode> tree = get();
                    deptTreeData.clear();
                    deptTreeData.addAll(tree);
                    deptNames.clear();
                    collectNames(tree, deptNames);
                    rebuildTree(tree);
                } catch (Exception e) {
                    showError(e);
                }
            }
        }.execute();
    }

    private void collectNames(List<DepartmentNode> nodes, Map<Long, String> map) {
        for (DepartmentNode n : nodes) {
            map.put(n.getId(), n.getName());
            if (n.getChildren() != null) {
                collectNames(n.getChildren(), map);
            }
        }
    }

    private void rebuildTree(List<DepartmentNode> tree) {
        rootNode.removeAllChildren();
        for (DepartmentNode n : tree) {
            rootNode.add(toTreeNode(n));
        }
        treeModel.reload();
        deptTree.expandRow(0);
    }

    private DefaultMutableTreeNode toTreeNode(DepartmentNode n) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(n);
        if (n.getChildren() != null) {
            for (DepartmentNode child : n.getChildren()) {
                node.add(toTreeNode(child));
            }
        }
        return node;
    }

    private void deleteDepartment(final long id) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                api.delete("/api/departments/" + id);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadDepartments();
                    loadPersonnel();
                } catch (Exception e) {
                    showError(e);
                }
            }
        }.execute();
    }

    // ---------- 人员 ----------

    private Long selectedDepartmentId() {
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode) deptTree.getLastSelectedPathComponent();
        if (sel != null && sel.getUserObject() instanceof DepartmentNode) {
            return ((DepartmentNode) sel.getUserObject()).getId();
        }
        return 0L; // 全部
    }

    private void loadPersonnel() {
        statusLabel.setText("正在加载...");
        final int seq = ++loadSeq;
        final String keyword = keywordField.getText().trim();
        final String status = String.valueOf(statusBox.getSelectedItem());
        final Long departmentId = selectedDepartmentId();
        final int targetPage = page;
        new SwingWorker<PageData<PersonnelItem>, Void>() {
            @Override
            protected PageData<PersonnelItem> doInBackground() throws Exception {
                Map<String, String> params = new HashMap<String, String>();
                if (!keyword.isEmpty()) {
                    params.put("keyword", keyword);
                }
                if (departmentId != null && departmentId > 0) {
                    params.put("departmentId", String.valueOf(departmentId));
                }
                if (!"全部状态".equals(status)) {
                    params.put("status", status);
                }
                params.put("page", String.valueOf(targetPage));
                params.put("size", String.valueOf(pageSize));
                JsonObject resp = api.get("/api/personnel", params);
                return new com.google.gson.Gson().fromJson(resp.get("data"),
                        new com.google.gson.reflect.TypeToken<PageData<PersonnelItem>>() {
                        }.getType());
            }

            @Override
            protected void done() {
                if (seq != loadSeq) {
                    return; // 已有更新的加载请求,丢弃过期响应
                }
                try {
                    PageData<PersonnelItem> data = get();
                    total = data.getTotal();
                    page = targetPage;
                    refreshTable(data.getList());
                } catch (Exception e) {
                    showError(e);
                }
            }
        }.execute();
    }

    private void refreshTable(List<PersonnelItem> list) {
        currentList = list;
        tableModel.setRowCount(0);
        for (PersonnelItem p : list) {
            tableModel.addRow(new Object[]{
                    nvl(p.getName()),
                    nvl(p.getGender()),
                    nvl(p.getIdCard()),
                    nvl(deptNames.get(p.getDepartmentId())),
                    nvl(p.getRank()),
                    nvl(p.getPost()),
                    nvl(p.getStatus()),
                    nvl(p.getPhone()),
                    nvl(p.getEntryDate())
            });
        }
        long totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
        pageLabel.setText("第 " + page + "/" + totalPages + " 页");
        statusLabel.setText("共 " + total + " 人");
    }

    private void onAdd() {
        PersonnelDialog.showAdd(this, api, deptTreeData, new Runnable() {
            @Override
            public void run() {
                loadPersonnel();
            }
        });
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要编辑的人员", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final PersonnelItem item = currentList.get(row);
        PersonnelDialog.showEdit(this, api, item, deptTreeData, new Runnable() {
            @Override
            public void run() {
                loadPersonnel();
            }
        });
    }

    private void onDelete() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要删除的人员", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final PersonnelItem item = currentList.get(row);
        if (JOptionPane.showConfirmDialog(this,
                "确定删除人员 \"" + item.getName() + "\"?", "确认删除", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                api.delete("/api/personnel/" + item.getId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadPersonnel();
                } catch (Exception e) {
                    showError(e);
                }
            }
        }.execute();
    }

    private void onExport() {
        final String keyword = keywordField.getText().trim();
        final String status = String.valueOf(statusBox.getSelectedItem());
        final Long departmentId = selectedDepartmentId();
        new SwingWorker<byte[], Void>() {
            private String fileName = "人员信息.xlsx";

            @Override
            protected byte[] doInBackground() throws Exception {
                Map<String, String> params = new HashMap<String, String>();
                if (!keyword.isEmpty()) {
                    params.put("keyword", keyword);
                }
                if (departmentId != null && departmentId > 0) {
                    params.put("departmentId", String.valueOf(departmentId));
                }
                if (!"全部状态".equals(status)) {
                    params.put("status", status);
                }
                return api.download("/api/personnel/export", params);
            }

            @Override
            protected void done() {
                try {
                    byte[] data = get();
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("导出 Excel");
                    chooser.setSelectedFile(new File(fileName));
                    if (chooser.showSaveDialog(PersonnelPanel.this) == JFileChooser.APPROVE_OPTION) {
                        File target = chooser.getSelectedFile();
                        if (!target.getName().toLowerCase().endsWith(".xlsx")) {
                            target = new File(target.getParentFile(), target.getName() + ".xlsx");
                        }
                        java.io.FileOutputStream out = new java.io.FileOutputStream(target);
                        try {
                            out.write(data);
                        } finally {
                            out.close();
                        }
                        JOptionPane.showMessageDialog(PersonnelPanel.this,
                                "已导出到:\n" + target.getAbsolutePath(), "导出成功", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    showError(e);
                }
            }
        }.execute();
    }

    private void onImport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择要导入的 Excel 文件(第一行为列头)");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Excel 文件(*.xlsx)", "xlsx"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File file = chooser.getSelectedFile();
        new SwingWorker<JsonObject, Void>() {
            @Override
            protected JsonObject doInBackground() throws Exception {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                return api.upload("/api/personnel/import-excel", file.getName(), bytes,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            }

            @Override
            protected void done() {
                try {
                    JsonObject resp = get();
                    JsonObject data = resp.getAsJsonObject("data");
                    JOptionPane.showMessageDialog(PersonnelPanel.this,
                            "导入完成!\n新增:" + data.get("added").getAsInt()
                                    + "  更新:" + data.get("updated").getAsInt()
                                    + "  失败:" + data.get("failed").getAsInt(),
                            "导入结果", JOptionPane.INFORMATION_MESSAGE);
                    loadDepartments();
                    loadPersonnel();
                } catch (Exception e) {
                    showError(e);
                }
            }
        }.execute();
    }

    private void showError(Exception e) {
        String msg = e instanceof ApiException ? e.getMessage() : "操作失败:" + e.getMessage();
        JOptionPane.showMessageDialog(this, msg, "错误", JOptionPane.ERROR_MESSAGE);
        statusLabel.setText("操作失败");
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
