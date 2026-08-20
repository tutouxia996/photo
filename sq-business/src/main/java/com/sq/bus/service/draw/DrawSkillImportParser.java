package com.sq.bus.service.draw;

import com.sq.common.utils.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Cursor/Claude SKILL.md（YAML frontmatter）或纯文本/Markdown 风格描述。
 */
public final class DrawSkillImportParser {

    private static final Pattern FRONTMATTER = Pattern.compile(
            "^---\\s*\\r?\\n([\\s\\S]*?)\\r?\\n---\\s*\\r?\\n?([\\s\\S]*)$");
    private static final Pattern H1 = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern YAML_NAME = Pattern.compile(
            "(?m)^(?:name|title)\\s*:\\s*[\"']?([^\"'\\r\\n]+)[\"']?\\s*$");
    private static final Pattern YAML_DESC = Pattern.compile(
            "(?m)^description\\s*:\\s*[\"']?([^\"'\\r\\n]+)[\"']?\\s*$");

    private DrawSkillImportParser() {
    }

    /**
     * @param raw      文件或粘贴全文
     * @param fileName 可选，用于推断默认名称
     * @return label / panelPrompt / skillRaw / hasFrontmatter
     */
    public static Map<String, Object> parse(String raw, String fileName) {
        if (StringUtils.isEmpty(raw) || StringUtils.isEmpty(raw.trim())) {
            throw new IllegalArgumentException("Skill 内容不能为空");
        }
        String text = raw.replace("\uFEFF", "").trim();
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("skillRaw", text);

        Matcher fm = FRONTMATTER.matcher(text);
        if (fm.matches()) {
            String yaml = fm.group(1);
            String body = fm.group(2) == null ? "" : fm.group(2).trim();
            String name = firstMatch(YAML_NAME, yaml);
            String desc = firstMatch(YAML_DESC, yaml);
            String label = firstNonEmpty(name, heading(body), stemFromFile(fileName), "自定义风格");
            String prompt = firstNonEmpty(body, desc);
            if (StringUtils.isEmpty(prompt)) {
                throw new IllegalArgumentException("Skill 未包含可用的风格描述正文");
            }
            result.put("label", label.trim());
            result.put("panelPrompt", prompt.trim());
            result.put("hasFrontmatter", true);
            return result;
        }

        String label = firstNonEmpty(heading(text), stemFromFile(fileName), firstLine(text), "自定义风格");
        String prompt = stripLeadingHeading(text);
        if (StringUtils.isEmpty(prompt)) {
            prompt = text;
        }
        result.put("label", truncate(label.trim(), 100));
        result.put("panelPrompt", prompt.trim());
        result.put("hasFrontmatter", false);
        return result;
    }

    /**
     * 由显示名生成 preset_key 候选（小写、连字符）。
     */
    public static String suggestKey(String label) {
        if (StringUtils.isEmpty(label)) {
            return "custom-style";
        }
        String s = label.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("^-+|-+$", "");
        if (s.isEmpty()) {
            return "custom-style";
        }
        // 含中文时用时间后缀更稳妥，调用方也可覆盖
        if (!s.matches("^[a-z0-9-]+$")) {
            return "style-" + Integer.toHexString(label.hashCode() & 0xffff);
        }
        if (s.length() > 48) {
            s = s.substring(0, 48);
        }
        return s;
    }

    private static String heading(String body) {
        if (StringUtils.isEmpty(body)) {
            return null;
        }
        Matcher m = H1.matcher(body);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String stripLeadingHeading(String text) {
        if (StringUtils.isEmpty(text)) {
            return text;
        }
        return text.replaceFirst("^#\\s+.+(?:\\r?\\n)+", "").trim();
    }

    private static String firstLine(String text) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        String line = text.split("\\r?\\n", 2)[0].trim();
        if (line.length() > 40) {
            line = line.substring(0, 40);
        }
        return line.isEmpty() ? null : line;
    }

    private static String stemFromFile(String fileName) {
        if (StringUtils.isEmpty(fileName)) {
            return null;
        }
        String name = fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if ("SKILL.md".equalsIgnoreCase(name) || "skill.md".equalsIgnoreCase(name)) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return StringUtils.isEmpty(name) ? null : name;
    }

    private static String firstMatch(Pattern p, String text) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.isNotEmpty(v)) {
                return v;
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
