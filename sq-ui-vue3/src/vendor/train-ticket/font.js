const buildFontString = (family, size, weight) => {
  const weightStr = weight ? `${weight} ` : "";
  return `${weightStr}${size}px ${family}`;
};
const getLocalFonts = async () => {
  try {
    if (window.queryLocalFonts) {
      const fonts = await window.queryLocalFonts();
      const fontFamilies = /* @__PURE__ */ new Set();
      for (const font of fonts) {
        fontFamilies.add(font.family);
      }
      return Array.from(fontFamilies).sort();
    }
  } catch (e) {
    console.warn("\u65E0\u6CD5\u83B7\u53D6\u672C\u5730\u5B57\u4F53\u5217\u8868:", e);
  }
  return [];
};
const fontCheckCache = /* @__PURE__ */ new Map();
const checkFontAvailable = (family) => {
  if (fontCheckCache.has(family)) return fontCheckCache.get(family);
  const generic = ["serif", "sans-serif", "monospace", "cursive", "fantasy"];
  if (generic.includes(family.toLowerCase())) {
    fontCheckCache.set(family, true);
    return true;
  }
  const canvas = document.createElement("canvas");
  const ctx = canvas.getContext("2d");
  const testStr = "abcdefghijklmnopqrstuvwxyz0123456789";
  const size = "72px";
  ctx.font = `${size} monospace`;
  const baseWidth = ctx.measureText(testStr).width;
  ctx.font = `${size} '${family}', monospace`;
  const testWidth = ctx.measureText(testStr).width;
  const available = testWidth !== baseWidth;
  fontCheckCache.set(family, available);
  return available;
};
export {
  buildFontString,
  checkFontAvailable,
  getLocalFonts
};
