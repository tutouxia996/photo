package com.sq.core.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * FileName: CodeGenerateUtils
 * Author: hngGeng
 * Date: 2024/7/2 15:39
 * Description:
 */
public class CodeGenerateUtils {

    /**
     * 主要调用方法
     * @param barcodeText 条形码内容
     * @param additionalText 条形码底部附加文本内特
     * @return 返回图片字节
     */
    public static byte[] generateBarcode(String barcodeText, String additionalText) {
        try {
            // 生成带有附加文本的条形码
            BufferedImage combinedImage = generateBarcodeWithText(barcodeText, additionalText); // 增加高度以容纳附加文本
            // 将 BufferedImage 转为字节数组输出流
            ByteArrayOutputStream bass = new ByteArrayOutputStream();
            ImageIO.write(combinedImage, "png", bass);
            return bass.toByteArray();
            // 设置响应头和内容
        } catch (WriterException | IOException e) {
            throw new RuntimeException(e);
        }
    }


    // 生成条形码并在底部添加附加文本
    public static BufferedImage generateBarcodeWithText(String text, String additionalText) throws WriterException, IOException {
        // 生成条形码
        BufferedImage barcodeImage = generateBarcode(text, 200, 100);
        // 创建更高的 BufferedImage，用于容纳条形码和附加文本
        BufferedImage combinedImage = new BufferedImage(200, 100 + 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = combinedImage.createGraphics();
        // 设置整个图像的背景为白色
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 200, 100 + 20);
        // 将条形码绘制到新的 BufferedImage 中
        g2d.drawImage(barcodeImage, 0, 0, null);
        // 设置字体和颜色
        Font font = new Font("Arial", Font.PLAIN, 12);
        g2d.setFont(font);
        g2d.setColor(Color.BLACK);
        // 计算文本位置
        int textWidth = g2d.getFontMetrics().stringWidth(additionalText);
        int x = (200 - textWidth) / 2;
        int y = 100 + 15; // 在条形码下方留出一定的空白
        // 添加附加文本到图片
        g2d.drawString(additionalText, x, y);
        g2d.dispose();
        return combinedImage;
    }

    // 生成条形码
    public static BufferedImage generateBarcode(String text, int width, int height) throws WriterException {
        Code128Writer barcodeWriter = new Code128Writer();
        BitMatrix bitMatrix = barcodeWriter.encode(text, BarcodeFormat.CODE_128, width, height, null);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }
}
