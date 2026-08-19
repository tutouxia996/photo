package com.sq.bus.utils;

import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;
import java.util.Iterator;

/**
 * 送检万相前的图片编码。
 */
public final class ImageEncodeUtils {

    private ImageEncodeUtils() {
    }

    public static String toDataUrlJpeg(File file, int maxEdge) throws Exception {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("图片不存在");
        }
        int edge = Math.max(256, maxEdge);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Thumbnails.of(file)
                .size(edge, edge)
                .keepAspectRatio(true)
                .outputFormat("jpg")
                .outputQuality(0.88f)
                .toOutputStream(bos);
        String b64 = Base64.getEncoder().encodeToString(bos.toByteArray());
        return "data:image/jpeg;base64," + b64;
    }

    /** 高画质 JPEG 落盘，quality 建议 0.90~0.98 */
    public static void writeJpeg(BufferedImage image, File dest, float quality) throws Exception {
        if (image == null || dest == null) {
            throw new IllegalArgumentException("图片或目标文件为空");
        }
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        float q = quality <= 0 ? 0.95f : Math.min(1f, quality);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpg", dest);
            return;
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(q);
        }
        try (FileOutputStream fos = new FileOutputStream(dest);
             ImageOutputStream ios = ImageIO.createImageOutputStream(fos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
