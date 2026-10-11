package com.szjx.personnel.client.ui;

import com.google.gson.JsonObject;
import com.szjx.personnel.client.model.StatsSummary;
import com.szjx.personnel.client.net.ApiClient;
import com.szjx.personnel.client.net.ApiException;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;

/**
 * 统计窗口:部门人数柱状图 / 性别分布饼图 / 状态分布
 */
public class StatsDialog extends JDialog {

    private final ApiClient api;

    public StatsDialog(java.awt.Window parent, ApiClient api) {
        super(parent, "人员统计", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        this.api = api;
        setSize(720, 520);
        setLocationRelativeTo(parent);

        final JLabel loading = new JLabel("正在统计...", JLabel.CENTER);
        add(loading, BorderLayout.CENTER);

        new SwingWorker<StatsSummary, Void>() {
            @Override
            protected StatsSummary doInBackground() throws Exception {
                JsonObject resp = api.get("/api/stats/summary");
                return new com.google.gson.Gson().fromJson(resp.get("data"), StatsSummary.class);
            }

            @Override
            protected void done() {
                try {
                    showCharts(get());
                } catch (Exception e) {
                    String msg = e instanceof ApiException ? e.getMessage() : "统计失败:" + e.getMessage();
                    JOptionPane.showMessageDialog(StatsDialog.this, msg, "错误", JOptionPane.ERROR_MESSAGE);
                    dispose();
                }
            }
        }.execute();
    }

    private void showCharts(StatsSummary s) {
        getContentPane().removeAll();

        JPanel top = new JPanel();
        JLabel totalLabel = new JLabel("人员总数:" + s.getTotal());
        totalLabel.setFont(new Font("Dialog", Font.BOLD, 18));
        top.add(totalLabel);
        add(top, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("部门人数", new JScrollPane(new BarChartPanel("部门人数统计", s.getDepartment())));
        tabs.addTab("性别分布", new JScrollPane(new PieChartPanel("性别分布", s.getGender())));
        tabs.addTab("状态分布", new JScrollPane(new BarChartPanel("状态分布", s.getStatus())));
        add(tabs, BorderLayout.CENTER);

        revalidate();
        repaint();
    }

    /** 柱状图 */
    private static class BarChartPanel extends JPanel {
        private final String title;
        private final List<StatsSummary.NameCount> data;

        BarChartPanel(String title, List<StatsSummary.NameCount> data) {
            this.title = title;
            this.data = data;
            setPreferredSize(new Dimension(680, 420));
            setBorder(BorderFactory.createEmptyBorder(20, 40, 50, 40));
            setFont(new Font("Dialog", Font.PLAIN, 13));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            int w = getWidth();
            int h = getHeight();
            g2.drawString(title, 20, 24);

            if (data == null || data.isEmpty()) {
                g2.drawString("暂无数据", w / 2 - 30, h / 2);
                return;
            }
            long max = 1;
            for (StatsSummary.NameCount n : data) {
                max = Math.max(max, n.getCount());
            }
            int left = 60;
            int bottom = h - 40;
            int top = 50;
            int chartH = bottom - top;
            int slot = (w - left - 40) / data.size();
            for (int i = 0; i < data.size(); i++) {
                StatsSummary.NameCount n = data.get(i);
                int barH = (int) (chartH * n.getCount() / (double) max);
                int x = left + i * slot + slot / 6;
                int y = bottom - barH;
                g2.setColor(new Color(66, 133, 244));
                g2.fillRect(x, y, slot * 2 / 3, barH);
                g2.setColor(Color.BLACK);
                g2.drawString(String.valueOf(n.getCount()), x + 6, y - 4);
                String name = n.getName();
                if (name.length() > 6) {
                    name = name.substring(0, 6) + "…";
                }
                g2.drawString(name, x + 2, bottom + 16);
            }
        }
    }

    /** 饼图 */
    private static class PieChartPanel extends JPanel {
        private final String title;
        private final List<StatsSummary.NameCount> data;

        PieChartPanel(String title, List<StatsSummary.NameCount> data) {
            this.title = title;
            this.data = data;
            setPreferredSize(new Dimension(680, 420));
            setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            setFont(new Font("Dialog", Font.PLAIN, 13));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            int w = getWidth();
            int h = getHeight();
            g2.drawString(title, 20, 24);

            if (data == null || data.isEmpty()) {
                g2.drawString("暂无数据", w / 2 - 30, h / 2);
                return;
            }
            long total = 0;
            for (StatsSummary.NameCount n : data) {
                total += n.getCount();
            }
            if (total == 0) {
                g2.drawString("暂无数据", w / 2 - 30, h / 2);
                return;
            }
            int diameter = Math.min(w - 240, h - 80);
            int x = 40;
            int y = (h - diameter) / 2 + 20;

            Color[] colors = {
                    new Color(66, 133, 244), new Color(234, 67, 53), new Color(251, 188, 4),
                    new Color(52, 168, 83), new Color(171, 71, 188), new Color(255, 112, 67),
                    new Color(0, 172, 193), new Color(141, 110, 99)
            };
            double start = 0;
            for (int i = 0; i < data.size(); i++) {
                StatsSummary.NameCount n = data.get(i);
                double angle = 360.0 * n.getCount() / total;
                g2.setColor(colors[i % colors.length]);
                g2.fillArc(x, y, diameter, diameter, (int) Math.round(start), (int) Math.round(angle));
                start += angle;
            }

            // 图例
            int lx = x + diameter + 30;
            int ly = y + 10;
            for (int i = 0; i < data.size(); i++) {
                StatsSummary.NameCount n = data.get(i);
                g2.setColor(colors[i % colors.length]);
                g2.fillRect(lx, ly, 16, 16);
                g2.setColor(Color.BLACK);
                g2.drawString(n.getName() + ":" + n.getCount() + "人(" +
                        Math.round(1000.0 * n.getCount() / total) / 10.0 + "%)", lx + 22, ly + 13);
                ly += 24;
            }
        }
    }
}
