package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.model.DepartmentNode;
import com.szjx.personnel.client.net.ApiClient;
import com.szjx.personnel.client.net.ApiException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 单位管理面板:树形维护组织架构(顶层单位 → 下级单位)
 */
public class UnitPanel extends JPanel {

    private final ApiClient api;

    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("单位");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree tree = new JTree(treeModel);
    private final JLabel statusLabel = new JLabel("就绪");

    private final List<DepartmentNode> deptTreeData = new ArrayList<DepartmentNode>();

    public UnitPanel(ApiClient api) {
        this.api = api;
        setLayout(new BorderLayout());

        initToolBar();
        initTree();
        loadTree();
    }

    private void initToolBar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(makeButton("新增顶级单位", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DepartmentDialog.showAdd(UnitPanel.this, api, 0L, deptTreeData, new Runnable() {
                    @Override
                    public void run() {
                        loadTree();
                    }
                });
            }
        }));
        toolbar.add(makeButton("新增下级单位", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Long parentId = selectedUnitId();
                if (parentId == null) {
                    JOptionPane.showMessageDialog(UnitPanel.this,
                            "请先选择上级单位(或选根节点\"单位\"作为顶级)", "提示", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                DepartmentDialog.showAdd(UnitPanel.this, api, parentId, deptTreeData, new Runnable() {
                    @Override
                    public void run() {
                        loadTree();
                    }
                });
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
                loadTree();
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

    private void initTree() {
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.putClientProperty(FlatClientProperties.STYLE, "rowHeight: 30;");
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createTitledBorder("单位结构(双击编辑)"));
        add(scroll, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(statusLabel, BorderLayout.SOUTH);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getUserObject() instanceof DepartmentNode) {
                            DepartmentDialog.showEdit(UnitPanel.this, api,
                                    (DepartmentNode) node.getUserObject(), deptTreeData,
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            loadTree();
                                        }
                                    });
                        }
                    }
                }
            }
        });

        // 右键菜单
        final JPopupMenu menu = new JPopupMenu();
        JMenuItem addChild = new JMenuItem("新增下级单位");
        JMenuItem addTop = new JMenuItem("新增顶级单位");
        JMenuItem edit = new JMenuItem("编辑单位");
        JMenuItem del = new JMenuItem("删除单位");
        addChild.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Long parentId = selectedUnitId();
                if (parentId == null) {
                    parentId = 0L;
                }
                final Long pid = parentId;
                DepartmentDialog.showAdd(UnitPanel.this, api, pid, deptTreeData, new Runnable() {
                    @Override
                    public void run() {
                        loadTree();
                    }
                });
            }
        });
        addTop.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DepartmentDialog.showAdd(UnitPanel.this, api, 0L, deptTreeData, new Runnable() {
                    @Override
                    public void run() {
                        loadTree();
                    }
                });
            }
        });
        edit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editSelected();
            }
        });
        del.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onDelete();
            }
        });
        menu.add(addChild);
        menu.add(addTop);
        menu.addSeparator();
        menu.add(edit);
        menu.add(del);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMenu(e, menu);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMenu(e, menu);
            }

            private void maybeShowMenu(MouseEvent e, JPopupMenu popup) {
                if (e.isPopupTrigger()) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        tree.setSelectionPath(path);
                        popup.show(tree, e.getX(), e.getY());
                    }
                }
            }
        });
    }

    private void loadTree() {
        statusLabel.setText("正在加载...");
        new SwingWorker<List<DepartmentNode>, Void>() {
            @Override
            protected List<DepartmentNode> doInBackground() throws Exception {
                JsonObject resp = api.get("/api/departments");
                return new Gson().fromJson(resp.get("data"),
                        new com.google.gson.reflect.TypeToken<List<DepartmentNode>>() {
                        }.getType());
            }

            @Override
            protected void done() {
                try {
                    List<DepartmentNode> data = get();
                    deptTreeData.clear();
                    deptTreeData.addAll(data);
                    rootNode.removeAllChildren();
                    for (DepartmentNode n : data) {
                        rootNode.add(toTreeNode(n));
                    }
                    treeModel.reload();
                    tree.expandRow(0);
                    statusLabel.setText("共 " + countNodes(data) + " 个单位");
                } catch (Exception e) {
                    showError(e);
                }
            }
        }.execute();
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

    private int countNodes(List<DepartmentNode> nodes) {
        int c = nodes.size();
        for (DepartmentNode n : nodes) {
            if (n.getChildren() != null) {
                c += countNodes(n.getChildren());
            }
        }
        return c;
    }

    private Long selectedUnitId() {
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (sel != null && sel.getUserObject() instanceof DepartmentNode) {
            return ((DepartmentNode) sel.getUserObject()).getId();
        }
        return null;
    }

    private DepartmentNode selectedUnit() {
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (sel != null && sel.getUserObject() instanceof DepartmentNode) {
            return (DepartmentNode) sel.getUserObject();
        }
        return null;
    }

    private void editSelected() {
        DepartmentNode d = selectedUnit();
        if (d == null) {
            JOptionPane.showMessageDialog(this, "请先选择要编辑的单位", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        DepartmentDialog.showEdit(this, api, d, deptTreeData, new Runnable() {
            @Override
            public void run() {
                loadTree();
            }
        });
    }

    private void onDelete() {
        final DepartmentNode d = selectedUnit();
        if (d == null) {
            JOptionPane.showMessageDialog(this, "请先选择要删除的单位", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "确定删除单位 \"" + d.getName() + "\"?\n(该单位下有下级单位或人员时无法删除)",
                "确认删除", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                api.delete("/api/departments/" + d.getId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadTree();
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
}
