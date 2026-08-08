package com.sq.bus.utils;

import com.sq.bus.domain.BizPhoto;

/**
 * 按库表字段长度收敛照片字符串，避免 Data truncation 导致整条入库失败。
 */
public final class PhotoFieldUtils {

    private PhotoFieldUtils() {
    }

    public static void clamp(BizPhoto photo) {
        if (photo == null) {
            return;
        }
        photo.setFileName(trim(photo.getFileName(), 200));
        photo.setFilePath(trim(photo.getFilePath(), 500));
        photo.setFileUrl(trim(photo.getFileUrl(), 255));
        photo.setThumbUrl(trim(photo.getThumbUrl(), 255));
        photo.setAddress(trim(photo.getAddress(), 200));
        photo.setProvince(trim(photo.getProvince(), 50));
        photo.setCity(trim(photo.getCity(), 50));
        photo.setDistrict(trim(photo.getDistrict(), 50));
        photo.setCameraModel(trim(photo.getCameraModel(), 100));
        photo.setLensInfo(trim(photo.getLensInfo(), 100));
        photo.setAperture(trim(photo.getAperture(), 20));
        photo.setShutterSpeed(trim(photo.getShutterSpeed(), 20));
        photo.setFocalLength(trim(photo.getFocalLength(), 20));
        photo.setMd5(trim(photo.getMd5(), 32));
        photo.setRemark(trim(photo.getRemark(), 500));
    }

    public static String trim(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    /** 保留扩展名的安全短文件名，避免上传路径/URL 超长 */
    public static String safeFileName(String original, int maxLen) {
        String name = original == null || original.trim().isEmpty() ? "unnamed" : original.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (name.length() <= maxLen) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 && dot > name.length() - 12 ? name.substring(dot) : "";
        int keep = Math.max(1, maxLen - ext.length());
        return name.substring(0, keep) + ext;
    }
}
