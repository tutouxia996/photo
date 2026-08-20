package com.sq.bus.service.draw;

/**
 * AI 出图预设（历史内置枚举，仅作文档/对照；运行时请使用 biz_photo_draw_preset）。
 */
public enum PhotoDrawPreset {

    INK_WASH_FLAT(
            "ink-wash-flat",
            "水墨扁平重构",
            "960*1280",
            PhotoDrawLayout.FULL_WITH_TITLES,
            "Transform the reference photo into a matte off-white rice paper ink-wash flat illustration. "
                    + "Preserve overall color tone of the scene. Simplify forms into soft relaxed color blocks with light ink wash edges, "
                    + "no hard outlines, remove fine texture clutter and heavy shadows, keep structural essence. "
                    + "Large negative space, main subject centered upper area on cream paper. "
                    + "No text, no border, no decoration."
    ),

    TRAVEL_POSTER(
            "travel-poster",
            "旅行摄影海报 3:4",
            "960*640",
            PhotoDrawLayout.TOP_PANEL_BOTTOM_PHOTO,
            "Based on the reference photo scene, create a small risograph-style travel illustration on warm ivory cream paper. "
                    + "Keep the main subject silhouette recognizable, simplify details, use 2-4 muted desaturated colors from the photo, "
                    + "subtle print noise and low-fi risograph texture. Small centered illustration with large empty cream margins. "
                    + "No text, no border, no photorealism."
    ),

    MINIMAL_ZINE(
            "minimal-zine",
            "极简 Zine 海报",
            "960*1280",
            PhotoDrawLayout.MINIMAL_ZINE,
            "Create a sparse vertical minimal zine poster on warm paper texture with large negative space. "
                    + "One small abstract editorial focal element inspired by the reference photo mood and colors, "
                    + "one clear color accent, experimental but restrained typography area left empty. "
                    + "Print/scan reproduction feel, not a commercial ad. No photo collage, no watermark, no long text."
    ),

    PHOTO_DIPTYCH(
            "photo-diptych",
            "摄影+抽象双联（上下）",
            "960*640",
            PhotoDrawLayout.TOP_PHOTO_BOTTOM_PANEL,
            "Create an abstract geometric flat design panel inspired by the mood, light and colors of the reference photo. "
                    + "Minimal memory-like shapes, soft muted palette, clean editorial magazine layout for the lower half of a poster. "
                    + "No photo realism, no faces, no text, no decorative clutter."
    ),

    PHOTO_DIPTYCH_COLUMN(
            "photo-diptych-column",
            "摄影+抽象双列",
            "960*1280",
            PhotoDrawLayout.LEFT_PHOTO_RIGHT_PANEL,
            "Create a vertical abstract geometric flat design panel inspired by the reference photo mood and palette. "
                    + "Minimal editorial shapes, soft muted colors, modern design magazine aesthetic. "
                    + "No photorealism, no faces, no text."
    ),

    PHOTO_ABSTRACT(
            "photo-abstract",
            "摄影+抽象编辑",
            "960*640",
            PhotoDrawLayout.TOP_PHOTO_BOTTOM_PANEL,
            "Create an abstract geometric flat design panel inspired by the mood, light and colors of the reference photo. "
                    + "Minimal memory-like shapes, soft muted palette, clean editorial magazine layout. "
                    + "No photo realism, no faces, no text, no decorative clutter."
    ),

    SCENE_TO_ART(
            "scene-to-art",
            "场景蒸馏艺术",
            "960*1280",
            PhotoDrawLayout.FULL_CANVAS,
            "Distill the reference scene into a standalone fine art piece on warm paper. "
                    + "Independent ink-wash or flat illustration with structural essence of the scene, "
                    + "soft blocks and quiet ink atmosphere, academic minimal composition. "
                    + "The original photograph should not appear; only artistic reinterpretation. No text."
    ),

    RDR2_JOURNAL(
            "rdr2-journal",
            "荒野大镖客2 · 日记炭笔",
            "960*1280",
            PhotoDrawLayout.FULL_CANVAS,
            "Redraw the reference photo as a full-page charcoal pencil sketch from Arthur Morgan's journal in Red Dead Redemption 2. "
                    + "Aged cream journal paper with faint stains, soft fiber texture, and mild yellowing. "
                    + "Loose but skilled observational charcoal and graphite drawing: visible strokes, cross-hatching, smudged shading, "
                    + "soft edges, uneven pressure, hand-drawn imperfections. Monochrome graphite and charcoal only, slight sepia cast allowed. "
                    + "Keep the scene composition and subject recognizable; simplify fine textures into sketch marks. "
                    + "Western frontier field-journal aesthetic, intimate diary illustration, not a clean digital line art. "
                    + "No photorealism, no color painting, no modern UI, no watermark. Absolutely no handwritten text, signatures, dates, or captions."
    ),

    PHOTO_RELIC(
            "photo-relic",
            "Photo Relic 编辑",
            "960*640",
            PhotoDrawLayout.TOP_PHOTO_BOTTOM_PANEL,
            "Create only the lower memory-print panel for a photo-relic editorial artwork. "
                    + "Warm ivory paper with a recognizable primary ink relic shape derived from the reference scene: "
                    + "flat modern printmaking blocks, deep blue and ink black, soft edges, negative-space cuts, "
                    + "one small warm accent from source light. Few deliberate marks, generous blank space. No text, no photo realism."
    );

    private final String id;
    private final String label;
    private final String panelSize;
    private final PhotoDrawLayout layout;
    private final String panelPrompt;

    PhotoDrawPreset(String id, String label, String panelSize, PhotoDrawLayout layout, String panelPrompt) {
        this.id = id;
        this.label = label;
        this.panelSize = panelSize;
        this.layout = layout;
        this.panelPrompt = panelPrompt;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getPanelSize() {
        return panelSize;
    }

    public PhotoDrawLayout getLayout() {
        return layout;
    }

    public String getPanelPrompt() {
        return panelPrompt;
    }

    public static PhotoDrawPreset fromId(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase();
        for (PhotoDrawPreset p : values()) {
            if (p.id.equals(key)) {
                return p;
            }
        }
        return null;
    }
}
