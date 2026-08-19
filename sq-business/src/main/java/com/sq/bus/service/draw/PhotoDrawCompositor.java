package com.sq.bus.service.draw;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * 将万相生成的面板与原图拼成最终海报；画布尺寸跟随原图。
 */
public final class PhotoDrawCompositor {

    /** 版式基准宽度，用于按比例缩放字号与间距 */
    private static final float BASE_W = 960f;
    private static final Color CREAM = new Color(245, 240, 232);
    private static final Color INK = new Color(45, 42, 38);

    private PhotoDrawCompositor() {
    }

    private static final class Layout {
        private final int w;
        private final int h;
        private final float scale;

        Layout(BufferedImage original) {
            this.w = Math.max(1, original.getWidth());
            this.h = Math.max(1, original.getHeight());
            this.scale = this.w / BASE_W;
        }

        int px(int base) {
            return Math.max(1, Math.round(base * scale));
        }

        Font font(String name, int style, int baseSize) {
            return new Font(name, style, px(baseSize));
        }
    }

    public static BufferedImage compose(PhotoDrawPreset preset, BufferedImage original, BufferedImage panel,
                                        String title, String subtitle, String keywords) {
        Layout layout = new Layout(original);
        switch (preset.getLayout()) {
            case FULL_CANVAS:
                return fullCanvas(panel, layout);
            case FULL_WITH_TITLES:
                return inkWashFlat(panel, title, subtitle, layout);
            case TOP_PANEL_BOTTOM_PHOTO:
                return travelPoster(original, panel, title, keywords, layout);
            case MINIMAL_ZINE:
                return minimalZine(original, panel, title, layout);
            case TOP_PHOTO_BOTTOM_PANEL:
                boolean graded = preset != PhotoDrawPreset.PHOTO_ABSTRACT && preset != PhotoDrawPreset.PHOTO_RELIC;
                return topPhotoBottomPanel(original, panel, title, graded, layout);
            case LEFT_PHOTO_RIGHT_PANEL:
                return leftPhotoRightPanel(original, panel, title, layout);
            default:
                return fullCanvas(panel, layout);
        }
    }

    private static BufferedImage inkWashFlat(BufferedImage panel, String title, String subtitle, Layout layout) {
        BufferedImage canvas = newCanvas(layout);
        Graphics2D g = canvas.createGraphics();
        setup(g);
        int textBand = layout.px(120);
        drawCoverRegion(g, panel, 0, 0, layout.w, layout.h - textBand);
        drawCenteredText(g, safeTitle(title, "Quiet Journey"), layout.h - layout.px(88),
                layout.font("Serif", Font.ITALIC, 32), INK, layout.w);
        drawCenteredText(g, safeLower(subtitle, "memory on rice paper"), layout.h - layout.px(52),
                layout.font("SansSerif", Font.PLAIN, 18), new Color(90, 86, 78), layout.w);
        g.dispose();
        return canvas;
    }

    private static BufferedImage travelPoster(BufferedImage original, BufferedImage panel,
                                              String title, String keywords, Layout layout) {
        BufferedImage canvas = newCanvas(layout);
        Graphics2D g = canvas.createGraphics();
        setup(g);

        int halfH = layout.h / 2;
        g.setColor(CREAM);
        g.fillRect(0, 0, layout.w, halfH);

        if (panel != null) {
            int maxIllH = (int) (halfH * 0.48);
            int maxIllW = (int) (layout.w * 0.62);
            BufferedImage ill = fitInside(panel, maxIllW, maxIllH);
            int ix = (layout.w - ill.getWidth()) / 2;
            int iy = (int) (halfH * 0.12);
            g.drawImage(ill, ix, iy, null);
        }

        drawCenteredText(g, safeUpper(title, "JOURNEY"), (int) (halfH * 0.62),
                layout.font("Monospaced", Font.PLAIN, 24), INK, layout.w);
        drawCenteredText(g, safeLower(keywords, "memory / light / place"), (int) (halfH * 0.72),
                layout.font("Monospaced", Font.PLAIN, 16), new Color(90, 86, 78), layout.w);

        drawCoverRegion(g, original, 0, halfH, layout.w, layout.h - halfH);
        g.dispose();
        return canvas;
    }

    private static BufferedImage minimalZine(BufferedImage original, BufferedImage focal, String title, Layout layout) {
        BufferedImage canvas = newCanvas(layout);
        Graphics2D g = canvas.createGraphics();
        setup(g);
        g.setColor(CREAM);
        g.fillRect(0, 0, layout.w, layout.h);

        if (focal != null) {
            int fw = (int) (layout.w * 0.55);
            int fh = (int) (layout.h * 0.38);
            BufferedImage fit = fitInside(focal, fw, fh);
            g.drawImage(fit, (layout.w - fit.getWidth()) / 2, (int) (layout.h * 0.08), null);
        }

        int photoMaxW = (int) (layout.w * 0.42);
        int photoMaxH = (int) (layout.h * 0.28);
        BufferedImage crop = fitInside(original, photoMaxW, photoMaxH);
        g.drawImage(crop, (int) (layout.w * 0.08), (int) (layout.h * 0.52), null);

        drawLeftText(g, safeUpper(title, "ZINE"), (int) (layout.h * 0.86),
                layout.font("Monospaced", Font.BOLD, 22), INK, (int) (layout.w * 0.84), (int) (layout.w * 0.08));
        g.dispose();
        return canvas;
    }

    private static BufferedImage topPhotoBottomPanel(BufferedImage original, BufferedImage panel,
                                                     String title, boolean magazineGrade, Layout layout) {
        BufferedImage canvas = newCanvas(layout);
        Graphics2D g = canvas.createGraphics();
        setup(g);

        int halfH = layout.h / 2;
        BufferedImage top = magazineGrade ? magazineGrade(original) : toRgb(original);
        drawCoverRegion(g, top, 0, 0, layout.w, halfH);

        if (panel != null) {
            drawCoverRegion(g, panel, 0, halfH, layout.w, layout.h - halfH);
        } else {
            g.setColor(new Color(230, 228, 222));
            g.fillRect(0, halfH, layout.w, layout.h - halfH);
        }

        if (title != null) {
            drawCenteredText(g, title, layout.h - layout.px(56),
                    layout.font("Serif", Font.ITALIC, 26), new Color(255, 252, 245), layout.w);
        }
        g.dispose();
        return canvas;
    }

    private static BufferedImage leftPhotoRightPanel(BufferedImage original, BufferedImage panel, String title,
                                                     Layout layout) {
        BufferedImage canvas = newCanvas(layout);
        Graphics2D g = canvas.createGraphics();
        setup(g);

        int halfW = layout.w / 2;
        drawCoverRegion(g, magazineGrade(original), 0, 0, halfW, layout.h);
        if (panel != null) {
            drawCoverRegion(g, panel, halfW, 0, layout.w - halfW, layout.h);
        } else {
            g.setColor(new Color(230, 228, 222));
            g.fillRect(halfW, 0, layout.w - halfW, layout.h);
        }

        if (title != null) {
            drawCenteredText(g, title, layout.h - layout.px(48),
                    layout.font("Serif", Font.ITALIC, 24), INK, layout.w);
        }
        g.dispose();
        return canvas;
    }

    private static BufferedImage fullCanvas(BufferedImage panel, Layout layout) {
        BufferedImage canvas = newCanvas(layout);
        Graphics2D g = canvas.createGraphics();
        setup(g);
        drawCoverRegion(g, panel, 0, 0, layout.w, layout.h);
        g.dispose();
        return canvas;
    }

    public static BufferedImage readImage(byte[] bytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) {
            throw new IOException("无法解析图片");
        }
        return img;
    }

    public static BufferedImage readImage(java.io.File file) throws IOException {
        BufferedImage img = ImageIO.read(file);
        if (img == null) {
            throw new IOException("无法读取图片：" + file.getAbsolutePath());
        }
        return img;
    }

    private static BufferedImage newCanvas(Layout layout) {
        BufferedImage canvas = new BufferedImage(layout.w, layout.h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, layout.w, layout.h);
        g.dispose();
        return canvas;
    }

    private static void setup(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static BufferedImage toRgb(BufferedImage src) {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static BufferedImage magazineGrade(BufferedImage src) {
        BufferedImage rgb = toRgb(src);
        RescaleOp op = new RescaleOp(1.04f, 6f, null);
        return op.filter(rgb, null);
    }

    private static void drawCoverRegion(Graphics2D g, BufferedImage src, int dx, int dy, int dw, int dh) {
        if (src == null) {
            return;
        }
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) {
            return;
        }
        float scale = Math.max((float) dw / sw, (float) dh / sh);
        int rw = Math.round(sw * scale);
        int rh = Math.round(sh * scale);
        int sx = (rw - dw) / 2;
        int sy = (rh - dh) / 2;
        g.drawImage(src, dx, dy, dx + dw, dy + dh, sx, sy, sx + dw, sy + dh, null);
    }

    private static BufferedImage fitInside(BufferedImage src, int maxW, int maxH) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        float scale = Math.min((float) maxW / sw, (float) maxH / sh);
        int w = Math.max(1, Math.round(sw * scale));
        int h = Math.max(1, Math.round(sh * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        setup(g);
        g.setColor(CREAM);
        g.fillRect(0, 0, w, h);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static void drawCenteredText(Graphics2D g, String text, int y, Font font, Color color, int width) {
        if (text == null || text.isEmpty()) {
            return;
        }
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        int x = (width - fm.stringWidth(text)) / 2;
        g.drawString(text, Math.max(8, x), y);
    }

    private static void drawLeftText(Graphics2D g, String text, int y, Font font, Color color, int maxW, int left) {
        if (text == null || text.isEmpty()) {
            return;
        }
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, left, y);
    }

    private static String safeUpper(String s, String fallback) {
        String v = trimOrNull(s);
        return v == null ? fallback : v.toUpperCase();
    }

    private static String safeLower(String s, String fallback) {
        String v = trimOrNull(s);
        return v == null ? fallback : v.toLowerCase();
    }

    private static String safeTitle(String s, String fallback) {
        String v = trimOrNull(s);
        return v == null ? fallback : v;
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
