package com.sq.bus.service.score;

import net.coobird.thumbnailator.Thumbnails;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 本地出图质量打分：清晰度、曝光、对比、色彩、分辨率。
 * 不调用外部模型，避免图生图前再花一遍 API。
 */
public final class PhotoQualityAnalyzer {

    private PhotoQualityAnalyzer() {
    }

    public static PhotoQualityResult analyze(File file, int analyzeWidth, int minShortSide, int passScore) {
        if (file == null || !file.exists() || !file.isFile()) {
            return PhotoQualityResult.fail(0, "文件不存在");
        }
        BufferedImage src;
        int origW = 0;
        int origH = 0;
        try {
            int[] wh = readDimension(file);
            origW = wh[0];
            origH = wh[1];
            int w = Math.max(64, analyzeWidth);
            src = Thumbnails.of(file).size(w, w).keepAspectRatio(true).asBufferedImage();
        } catch (Exception e) {
            return PhotoQualityResult.fail(0, "无法解码");
        }
        if (src == null || src.getWidth() < 8 || src.getHeight() < 8) {
            return PhotoQualityResult.fail(0, "图像过小");
        }

        int width = src.getWidth();
        int height = src.getHeight();
        int n = width * height;
        int[] lum = new int[n];
        long sumL = 0;
        long sumL2 = 0;
        double sumRg = 0;
        double sumYb = 0;
        double sumRg2 = 0;
        double sumYb2 = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int l = (77 * r + 150 * g + 29 * b) >> 8;
                int i = y * width + x;
                lum[i] = l;
                sumL += l;
                sumL2 += (long) l * l;
                double rg = r - g;
                double yb = 0.5 * (r + g) - b;
                sumRg += rg;
                sumYb += yb;
                sumRg2 += rg * rg;
                sumYb2 += yb * yb;
            }
        }

        double meanL = sumL / (double) n;
        double varL = Math.max(0, sumL2 / (double) n - meanL * meanL);
        double stdL = Math.sqrt(varL);

        double meanRg = sumRg / n;
        double meanYb = sumYb / n;
        double stdRg = Math.sqrt(Math.max(0, sumRg2 / n - meanRg * meanRg));
        double stdYb = Math.sqrt(Math.max(0, sumYb2 / n - meanYb * meanYb));
        double colorfulness = Math.sqrt(stdRg * stdRg + stdYb * stdYb)
                + 0.3 * Math.sqrt(meanRg * meanRg + meanYb * meanYb);

        double lapVar = laplacianVar(lum, width, height);
        int sharpness = scale(lapVar, 8, 220);
        int exposure = exposureScore(meanL);
        int contrast = scale(stdL, 12, 70);
        int color = scale(colorfulness, 8, 55);
        int resolution = resolutionScore(origW, origH, minShortSide);

        int score = (int) Math.round(
                sharpness * 0.34
                        + exposure * 0.22
                        + contrast * 0.16
                        + color * 0.12
                        + resolution * 0.16
        );
        if (score < 0) {
            score = 0;
        }
        if (score > 100) {
            score = 100;
        }

        String reason;
        boolean pass = score >= passScore;
        if (origW > 0 && origH > 0 && Math.min(origW, origH) < minShortSide) {
            reason = "分辨率偏低";
            pass = false;
            if (score > 58) {
                score = 58;
            }
        } else if (sharpness < 38) {
            reason = "画面偏糊";
            pass = false;
        } else if (exposure < 32) {
            reason = meanL < 70 ? "过暗" : "过曝";
            pass = false;
        } else if (pass) {
            reason = "合格";
        } else if (contrast < 40) {
            reason = "反差不足";
        } else {
            reason = "综合分不足";
        }
        if (pass && score < passScore) {
            pass = false;
        }
        return PhotoQualityResult.of(score, pass, reason);
    }

    private static double laplacianVar(int[] lum, int w, int h) {
        if (w < 3 || h < 3) {
            return 0;
        }
        long sum = 0;
        long sum2 = 0;
        int count = 0;
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int c = lum[row + x];
                int lap = (c << 2) - lum[row - w + x] - lum[row + w + x] - lum[row + x - 1] - lum[row + x + 1];
                sum += lap;
                sum2 += (long) lap * lap;
                count++;
            }
        }
        if (count == 0) {
            return 0;
        }
        double mean = sum / (double) count;
        return Math.max(0, sum2 / (double) count - mean * mean);
    }

    private static int exposureScore(double meanL) {
        // 理想中灰约 110-150
        double dist = Math.abs(meanL - 128);
        if (meanL < 18 || meanL > 240) {
            return 8;
        }
        if (dist <= 36) {
            return 92;
        }
        if (dist <= 60) {
            return 78;
        }
        if (dist <= 85) {
            return 58;
        }
        return 32;
    }

    private static int[] readDimension(File file) {
        javax.imageio.stream.ImageInputStream in = null;
        javax.imageio.ImageReader reader = null;
        try {
            in = javax.imageio.ImageIO.createImageInputStream(file);
            if (in == null) {
                return new int[] { 0, 0 };
            }
            java.util.Iterator<javax.imageio.ImageReader> it = javax.imageio.ImageIO.getImageReaders(in);
            if (!it.hasNext()) {
                return new int[] { 0, 0 };
            }
            reader = it.next();
            reader.setInput(in, true, true);
            return new int[] { reader.getWidth(0), reader.getHeight(0) };
        } catch (Exception e) {
            return new int[] { 0, 0 };
        } finally {
            if (reader != null) {
                reader.dispose();
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static int resolutionScore(int origW, int origH, int minShortSide) {
        int shortSide = Math.min(origW, origH);
        if (shortSide <= 0) {
            return 50;
        }
        if (shortSide >= 2000) {
            return 95;
        }
        if (shortSide >= minShortSide) {
            return 80;
        }
        if (shortSide >= 480) {
            return 45;
        }
        return 20;
    }

    private static int scale(double value, double low, double high) {
        if (high <= low) {
            return 0;
        }
        double t = (value - low) / (high - low);
        if (t < 0) {
            t = 0;
        }
        if (t > 1) {
            t = 1;
        }
        return (int) Math.round(t * 100);
    }
}
