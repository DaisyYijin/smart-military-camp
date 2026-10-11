package com.szjx.personnel.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.szjx.personnel.client.net.ApiClient;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 主窗口:左侧导航栏 + 右侧内容区(CardLayout)
 */
public class MainFrame extends JFrame {

    private static final Color BRAND_BLUE = new Color(0x1D63ED);

    private final ApiClient api;
    private final String username;

    private final CardLayout cards = new CardLayout();
    private final JPanel contentPanel = new JPanel(cards);

    private final PersonnelPanel personnelPanel;
    private final UnitPanel unitPanel;
    private final LeavePanel leavePanel;
    private final FitnessPanel fitnessPanel;
    private final EquipmentPanel equipmentPanel;
    private final MaterialPanel materialPanel;
    private final VehiclePanel vehiclePanel;
    private final DictPanel dictPanel;

    public MainFrame(ApiClient api, String username) {
        this.api = api;
        this.username = username;
        setTitle("数智军营人员管理系统 - 登录用户:" + username);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1200, 740);
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);

        personnelPanel = new PersonnelPanel(api);
        unitPanel = new UnitPanel(api);
        leavePanel = new LeavePanel(api);
        fitnessPanel = new FitnessPanel(api);
        equipmentPanel = new EquipmentPanel(api);
        materialPanel = new MaterialPanel(api);
        vehiclePanel = new VehiclePanel(api);
        dictPanel = new DictPanel(api);

        initNavBar();
        initContent();
        cards.show(contentPanel, "unit");
    }

    private void initNavBar() {
        JPanel nav = new JPanel(new BorderLayout());
        nav.setPreferredSize(new Dimension(176, 0));
        nav.setBackground(new Color(0xF5F7FA));
        nav.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xE5E7EB)));

        // 顶部品牌区
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 14));
        brand.setOpaque(false);
        JLabel logo = new JLabel("★");
        logo.setFont(new Font("Dialog", Font.BOLD, 18));
        logo.setForeground(BRAND_BLUE);
        JLabel name = new JLabel("数智军营");
        name.setFont(new Font("Dialog", Font.BOLD, 15));
        name.setForeground(new Color(0x1F2937));
        brand.add(logo);
        brand.add(name);
        nav.add(brand, BorderLayout.NORTH);

        // 中部导航按钮
        JPanel btns = new JPanel();
        btns.setOpaque(false);
        btns.setLayout(new java.awt.GridLayout(0, 1, 0, 6));
        btns.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        ButtonGroup group = new ButtonGroup();
        btns.add(navButton(group, "单位管理", "unit", true));
        btns.add(navButton(group, "人员管理", "personnel", false));
        btns.add(navButton(group, "休假管理", "leave", false));
        btns.add(navButton(group, "体能管理", "fitness", false));
        btns.add(navButton(group, "装备管理", "equipment", false));
        btns.add(navButton(group, "物资管理", "material", false));
        btns.add(navButton(group, "车辆管理", "vehicle", false));
        btns.add(navButton(group, "字典管理", "dict", false));

        JButton statsBtn = new JButton("统计中心");
        styleNavButton(statsBtn);
        statsBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new StatsDialog(MainFrame.this, api).setVisible(true);
            }
        });
        btns.add(statsBtn);

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setOpaque(false);
        centerWrap.add(btns, BorderLayout.NORTH);
        nav.add(centerWrap, BorderLayout.CENTER);

        // 底部:用户 + 修改密码 + 退出
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new java.awt.GridLayout(0, 1, 0, 6));
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));

        JLabel userLabel = new JLabel("用户:" + username);
        userLabel.setForeground(new Color(0x6B7280));
        userLabel.setHorizontalAlignment(JLabel.CENTER);
        bottom.add(userLabel);

        JButton pwdBtn = new JButton("修改密码");
        styleNavButton(pwdBtn);
        pwdBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new ChangePasswordDialog(MainFrame.this, api).setVisible(true);
            }
        });
        bottom.add(pwdBtn);

        JButton exitBtn = new JButton("退出系统");
        styleNavButton(exitBtn);
        exitBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        bottom.add(exitBtn);

        nav.add(bottom, BorderLayout.SOUTH);
        add(nav, BorderLayout.WEST);
    }

    private JToggleButton navButton(ButtonGroup group, final String text,
                                    final String card, boolean selected) {
        final JToggleButton b = new JToggleButton(text, selected);
        styleNavButton(b);
        group.add(b);
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cards.show(contentPanel, card);
            }
        });
        return b;
    }

    private void styleNavButton(AbstractButton b) {
        b.setFocusable(false);
        b.setHorizontalAlignment(JButton.LEFT);
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        b.putClientProperty(FlatClientProperties.STYLE,
                "arc: 10; focusWidth: 0; innerFocusWidth: 0;"
                        + " selectedBackground: #1D63ED; selectedForeground: #FFFFFF;");
    }

    private void initContent() {
        contentPanel.add(personnelPanel, "personnel");
        contentPanel.add(unitPanel, "unit");
        contentPanel.add(leavePanel, "leave");
        contentPanel.add(fitnessPanel, "fitness");
        contentPanel.add(equipmentPanel, "equipment");
        contentPanel.add(materialPanel, "material");
        contentPanel.add(vehiclePanel, "vehicle");
        contentPanel.add(dictPanel, "dict");
        add(contentPanel, BorderLayout.CENTER);
    }
}
