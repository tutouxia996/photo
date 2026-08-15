package com.sq.bus.utils;

import com.sq.common.exception.ServiceException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 解析 GPX 1.0/1.1 中的 trkpt / rtept（WGS84）。
 */
public final class GpxParseUtils {

    private GpxParseUtils() {
    }

    public static final class GpxSample {
        public final long timeMs;
        public final double latWgs;
        public final double lngWgs;
        public final Double ele;
        /** 卫星数（如 GPSLogger 的 sat） */
        public final Integer sat;
        /** 原始 time 文本（未做时区偏移） */
        public final String rawTime;
        /** false=无 &lt;time&gt; 时合成的时间，不参与媒体匹配 */
        public final boolean hasRealTime;

        public GpxSample(long timeMs, double latWgs, double lngWgs, Double ele) {
            this(timeMs, latWgs, lngWgs, ele, null, null, true);
        }

        public GpxSample(long timeMs, double latWgs, double lngWgs, Double ele, Integer sat, String rawTime) {
            this(timeMs, latWgs, lngWgs, ele, sat, rawTime, true);
        }

        public GpxSample(long timeMs, double latWgs, double lngWgs, Double ele, Integer sat, String rawTime,
                         boolean hasRealTime) {
            this.timeMs = timeMs;
            this.latWgs = latWgs;
            this.lngWgs = lngWgs;
            this.ele = ele;
            this.sat = sat;
            this.rawTime = rawTime;
            this.hasRealTime = hasRealTime;
        }
    }

    public static List<GpxSample> parse(InputStream in, int timeOffsetHours) {
        return parse(in, timeOffsetHours * 3600L * 1000L);
    }

    public static List<GpxSample> parse(InputStream in, long timeOffsetMs) {
        if (in == null) {
            return Collections.emptyList();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document doc = factory.newDocumentBuilder().parse(in);
            List<GpxSample> list = new ArrayList<GpxSample>();
            collectPoints(doc.getElementsByTagNameNS("*", "trkpt"), list, timeOffsetMs);
            if (list.isEmpty()) {
                collectPoints(doc.getElementsByTagName("trkpt"), list, timeOffsetMs);
            }
            if (list.isEmpty()) {
                collectPoints(doc.getElementsByTagNameNS("*", "rtept"), list, timeOffsetMs);
            }
            if (list.isEmpty()) {
                collectPoints(doc.getElementsByTagName("rtept"), list, timeOffsetMs);
            }
            list.sort(Comparator.comparingLong(s -> s.timeMs));
            return list;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("GPX 解析失败：" + e.getMessage());
        }
    }

    private static void collectPoints(NodeList nodes, List<GpxSample> out, long offsetMs) {
        if (nodes == null) {
            return;
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element)) {
                continue;
            }
            Element el = (Element) nodes.item(i);
            String latStr = el.getAttribute("lat");
            String lonStr = el.getAttribute("lon");
            if (latStr == null || latStr.isEmpty() || lonStr == null || lonStr.isEmpty()) {
                continue;
            }
            double lat;
            double lon;
            try {
                lat = Double.parseDouble(latStr.trim());
                lon = Double.parseDouble(lonStr.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            String timeText = childText(el, "time");
            Long timeMs = null;
            if (timeText != null && !timeText.isEmpty()) {
                timeMs = parseTimeMs(timeText);
            }
            boolean hasRealTime = timeMs != null;
            // 无时间也保留坐标点，按序号回填时间，避免折线丢点（合成时间不参与媒体匹配）
            if (timeMs == null) {
                timeMs = out.isEmpty() ? 0L : out.get(out.size() - 1).timeMs + 1000L;
                timeMs = timeMs - offsetMs; // 后面统一加 offset
            }
            Double ele = parseDoubleChild(el, "ele");
            Integer sat = parseIntChild(el, "sat");
            out.add(new GpxSample(timeMs + offsetMs, lat, lon, ele, sat, timeText, hasRealTime));
        }
    }

    private static Double parseDoubleChild(Element parent, String localName) {
        String text = childText(parent, localName);
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseIntChild(Element parent, String localName) {
        String text = childText(parent, localName);
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(text.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String childText(Element parent, String localName) {
        // 优先直接子节点，避免命名空间/深层节点干扰
        NodeList children = parent.getChildNodes();
        if (children != null) {
            for (int i = 0; i < children.getLength(); i++) {
                if (!(children.item(i) instanceof Element)) {
                    continue;
                }
                Element child = (Element) children.item(i);
                String name = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
                if (localName.equals(name) || name.endsWith(":" + localName)) {
                    return child.getTextContent();
                }
            }
        }
        NodeList list = parent.getElementsByTagNameNS("*", localName);
        if (list == null || list.getLength() == 0) {
            list = parent.getElementsByTagName(localName);
        }
        if (list == null || list.getLength() == 0) {
            return null;
        }
        return list.item(0).getTextContent();
    }

    private static Long parseTimeMs(String text) {
        String s = text.trim();
        try {
            return Instant.parse(s).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            // 2024-01-01 12:00:00
            if (s.length() >= 19 && s.charAt(10) == ' ') {
                s = s.substring(0, 10) + "T" + s.substring(11) + "Z";
                return Instant.parse(s).toEpochMilli();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    public static Date toDate(long timeMs) {
        return new Date(timeMs);
    }
}
