package com.szjx.personnel.server.launcher;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 应用图标(程序化绘制,避免依赖图片资源文件):
 * 蓝色圆底 + 白色"军"字,用于启动向导等窗口的任务栏/标题栏图标。
 */
final class AppIcons {

    private AppIcons() {
    }

    static BufferedImage appIcon() {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(22, 67, 126));
        g.fillOval(2, 2, size - 5, size - 5);
        g.setColor(new Color(47, 111, 214));
        g.drawOval(2, 2, size - 5, size - 5);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 36));
        FontMetrics fm = g.getFontMetrics();
        String t = "军";
        int x = (size - fm.stringWidth(t)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(t, x, y);
        g.dispose();
        return img;
    }
}
