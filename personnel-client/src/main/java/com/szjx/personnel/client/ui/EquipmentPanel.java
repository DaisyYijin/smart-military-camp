package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.szjx.personnel.client.model.EquipmentItem;
import com.szjx.personnel.client.model.PageData;
import com.szjx.personnel.client.net.ApiClient;
import com.szjx.personnel.client.net.ApiException;
import com.szjx.personnel.client.util.DictLoader;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 装备管理面板
 */
public class EquipmentPanel extends JPanel {

    private final ApiClient api;
    private final JTextField keywordField = new JTextField(14);
    private final JComboBox<String> statusBox = new JComboBox<String>();
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel pageLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("就绪");

    private List<EquipmentItem> currentList = new ArrayList<EquipmentItem>();
    private long total = 0;
    private int page = 1;
    private final int pageSize = 20;
    private int loadSeq = 0;

    public EquipmentPanel(ApiClient api) {
        this.api = api;
        setLayout(new BorderLayout());

        tableModel = new DefaultTableModel(new String[]{
                "名称", "编号", "类别", "型号", "状态", "保管人", "存放位置", "购置日期"}, 0) {
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
        DictLoader.load(api, "equipment_status", statusBox, "全部状态", null);
        loadData();
    }

    private void initToolBar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(makeButton("新增装备", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                EquipmentDialog.showAdd(EquipmentPanel.this, api, new Runnable() {
                    @Override
                    public void run() {
                        loadData();
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
                loadData();
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
                loadData();
            }
        });
        filterPanel.add(searchBtn);
        keywordField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                page = 1;
                loadData();
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
                    loadData();
                }
            }
        });
        next.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (page * pageSize < total) {
                    page++;
                    loadData();
                }
            }
        });
        pagePanel.add(prev);
        pagePanel.add(pageLabel);
        pagePanel.add(next);

        add(filterPanel, BorderLayout.NORTH);
        add(tableScroll, BorderLayout.CENTER);
        add(pagePanel, BorderLayout.SOUTH);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void loadData() {
        statusLabel.setText("正在加载...");
        final int seq = ++loadSeq;
        final String keyword = keywordField.getText().trim();
        final String status = String.valueOf(statusBox.getSelectedItem());
        final int targetPage = page;
        new SwingWorker<PageData<EquipmentItem>, Void>() {
            @Override
            protected PageData<EquipmentItem> doInBackground() throws Exception {
                Map<String, String> params = new HashMap<String, String>();
                if (!keyword.isEmpty()) {
                    params.put("keyword", keyword);
                }
                if (!"全部状态".equals(status)) {
                    params.put("status", status);
                }
                params.put("page", String.valueOf(targetPage));
                params.put("size", String.valueOf(pageSize));
                JsonObject resp = api.get("/api/equipment", params);
                return new Gson().fromJson(resp.get("data"),
                        new com.google.gson.reflect.TypeToken<PageData<EquipmentItem>>() {
                        }.getType());
            }

            @Override
            protected void done() {
                if (seq != loadSeq) {
                    return;
                }
                try {
                    PageData<EquipmentItem> data = get();
                    total = data.getTotal();
                    page = targetPage;
                    refreshTable(data.getList());
                } catch (Exception e) {
                    showError(e);
                }
            }
        }.execute();
    }

    private void refreshTable(List<EquipmentItem> list) {
        currentList = list;
        tableModel.setRowCount(0);
        for (EquipmentItem e : list) {
            tableModel.addRow(new Object[]{
                    nvl(e.getName()), nvl(e.getCode()), nvl(e.getCategory()), nvl(e.getModel()),
                    nvl(e.getStatus()), nvl(e.getKeeperName()), nvl(e.getLocation()), nvl(e.getBuyDate())
            });
        }
        long totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
        pageLabel.setText("第 " + page + "/" + totalPages + " 页");
        statusLabel.setText("共 " + total + " 件装备");
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要编辑的记录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final EquipmentItem item = currentList.get(row);
        EquipmentDialog.showEdit(this, api, item, new Runnable() {
            @Override
            public void run() {
                loadData();
            }
        });
    }

    private void onDelete() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择要删除的记录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final EquipmentItem item = currentList.get(row);
        if (JOptionPane.showConfirmDialog(this,
                "确定删除装备 \"" + item.getName() + "\"?", "确认删除", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                api.delete("/api/equipment/" + item.getId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadData();
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
