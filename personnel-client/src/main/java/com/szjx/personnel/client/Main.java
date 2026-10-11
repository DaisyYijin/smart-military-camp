package com.szjx.personnel.client;

import com.formdev.flatlaf.FlatLightLaf;
import com.szjx.personnel.client.config.AppConfig;
import com.szjx.personnel.client.ui.LoginFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;

/**
 * 数智军营人员管理系统 - 桌面客户端入口
 */
public class Main {

    public static void main(String[] args) {
        // 现代扁平化浅色主题(FlatLaf,JDK8/Win7/麒麟兼容)
        FlatLightLaf.setup();

        // 全局样式微调(字号 15px,接近常规桌面软件观感)
        UIManager.put("Component.arc", 10);          // 组件圆角
        UIManager.put("Button.arc", 10);             // 按钮圆角
        UIManager.put("TextComponent.arc", 8);       // 输入框圆角
        UIManager.put("Component.focusWidth", 2);    // 聚焦描边宽度
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("defaultFont", new FontUIResource(new Font("Dialog", Font.PLAIN, 15)));
        UIManager.put("Table.rowHeight", 32);        // 表格行高
        UIManager.put("Table.alternateRowColor", new java.awt.Color(0xF3F6FB)); // 斑马纹
        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("Component.focusColor", new java.awt.Color(0x1D63ED));

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                AppConfig config = AppConfig.load();
                LoginFrame frame = new LoginFrame(config);
                frame.setVisible(true);
            }
        });
    }
}
