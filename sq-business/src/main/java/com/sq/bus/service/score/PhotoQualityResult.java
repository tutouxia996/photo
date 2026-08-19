package com.sq.bus.service.score;

/**
 * 单张照片的出图质量分析结果。
 */
public class PhotoQualityResult {

    private int score;
    private boolean passed;
    private String reason;

    public static PhotoQualityResult of(int score, boolean passed, String reason) {
        PhotoQualityResult r = new PhotoQualityResult();
        r.score = clamp(score);
        r.passed = passed;
        r.reason = reason;
        return r;
    }

    public static PhotoQualityResult fail(int score, String reason) {
        return of(score, false, reason);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 100 ? 100 : v);
    }

    public int getScore() {
        return score;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getReason() {
        return reason;
    }
}
