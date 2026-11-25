const wearTextureCache = /* @__PURE__ */ new Map();
const createWearTexture = (width, height, cacheKey, intensity = 0.7) => {
  const cached = wearTextureCache.get(cacheKey);
  if (cached && cached.width >= width && cached.height >= height) {
    return cached;
  }
  const textureCanvas = document.createElement("canvas");
  textureCanvas.width = Math.max(width, 100);
  textureCanvas.height = Math.max(height, 50);
  const textureCtx = textureCanvas.getContext("2d");
  const imageData = textureCtx.createImageData(textureCanvas.width, textureCanvas.height);
  const data = imageData.data;
  const minAlpha = 1 - intensity * 0.7;
  const alphaRange = 1 - minAlpha;
  for (let i = 0; i < data.length; i += 4) {
    const alpha = Math.floor((Math.random() * alphaRange + minAlpha) * 255);
    data[i] = 255;
    data[i + 1] = 255;
    data[i + 2] = 255;
    data[i + 3] = alpha;
  }
  textureCtx.putImageData(imageData, 0, 0);
  wearTextureCache.set(cacheKey, textureCanvas);
  return textureCanvas;
};
const clearWearTextureCache = () => {
  wearTextureCache.clear();
};
const drawCustomText = (ctx, text, x, y, spacing = 0, color = [0, 0, 0], wearEffect) => {
  if (!text) return;
  const fontSize = parseInt(ctx.font, 10) || 16;
  const extraSpace = 5;
  let totalWidth = 0;
  for (let i = 0; i < text.length; i++) {
    totalWidth += ctx.measureText(text[i]).width + (i < text.length - 1 ? spacing : 0);
  }
  const canvasWidth = Math.ceil(totalWidth) + 10;
  const canvasHeight = fontSize + extraSpace * 2 + 10;
  const offscreenCanvas = document.createElement("canvas");
  offscreenCanvas.width = canvasWidth;
  offscreenCanvas.height = canvasHeight;
  const offCtx = offscreenCanvas.getContext("2d");
  offCtx.font = ctx.font;
  offCtx.textBaseline = ctx.textBaseline;
  offCtx.fillStyle = `rgb(${color[0]}, ${color[1]}, ${color[2]})`;
  let offsetX = 5;
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    offCtx.fillText(char, offsetX, fontSize + extraSpace);
    offsetX += ctx.measureText(char).width + spacing;
  }
  if (wearEffect?.enabled && wearEffect.intensity > 0) {
    const cacheKey = `wear_${canvasWidth}_${canvasHeight}_${wearEffect.intensity.toFixed(2)}`;
    const wearTexture = createWearTexture(canvasWidth, canvasHeight, cacheKey, wearEffect.intensity);
    offCtx.globalCompositeOperation = "destination-in";
    offCtx.drawImage(wearTexture, 0, 0, canvasWidth, canvasHeight);
    offCtx.globalCompositeOperation = "source-over";
  }
  ctx.drawImage(offscreenCanvas, x - 5, y - fontSize - extraSpace);
};
const drawParagraph = (ctx, paragraphs, indent, x, y, lineSpace, maxWidth, spacing = 0, color = [0, 0, 0], wearEffect) => {
  paragraphs.forEach((paragraph) => {
    const words = paragraph.split("");
    let line = "";
    let isFirstLine = true;
    for (let n = 0; n < words.length; n++) {
      const testLine = line + words[n];
      const testWidth = ctx.measureText(testLine).width + (isFirstLine ? indent : 0);
      if (testWidth > maxWidth && line !== "") {
        let textSpacing = spacing;
        const isSymbol = /[，、,.\)!]/.test(words[n]);
        for (let i = 0; i < 2; i++) {
          if (isSymbol) {
            line = line.slice(0, -1);
            n--;
            textSpacing += 0.6;
          }
        }
        drawCustomText(ctx, line, x + (isFirstLine ? indent : 0), y, textSpacing, color, wearEffect);
        line = words[n];
        y += lineSpace;
        isFirstLine = false;
      } else {
        line = testLine;
      }
    }
    drawCustomText(ctx, line, x + (isFirstLine ? indent : 0), y, spacing, color, wearEffect);
    y += lineSpace;
  });
};
const drawTrapezoid = (ctx, x, y, width, height, offset, direction) => {
  ctx.beginPath();
  if (direction === "left") {
    ctx.moveTo(x, y - height / 2 + offset);
    ctx.lineTo(x + width, y - height / 2);
    ctx.lineTo(x + width, y + height / 2);
    ctx.lineTo(x, y + height / 2 - offset);
  } else {
    ctx.moveTo(x, y - height / 2 + offset);
    ctx.lineTo(x - width, y - height / 2);
    ctx.lineTo(x - width, y + height / 2);
    ctx.lineTo(x, y + height / 2 - offset);
  }
  ctx.closePath();
  ctx.fill();
};
const drawRoundRect = (ctx, x, y, width, height, radius, fillStyle) => {
  ctx.fillStyle = fillStyle;
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.arcTo(x + width, y, x + width, y + height, radius);
  ctx.arcTo(x + width, y + height, x, y + height, radius);
  ctx.arcTo(x, y + height, x, y, radius);
  ctx.arcTo(x, y, x + width, y, radius);
  ctx.closePath();
  ctx.fill();
};
const getTextWidth = (ctx, text) => {
  return ctx.measureText(text).width;
};
const createTicketClipPath = (ctx, rectX, rectY, rectWidth, rectHeight, radius, trapezoids) => {
  ctx.beginPath();
  ctx.moveTo(rectX + radius, rectY);
  ctx.arcTo(rectX + rectWidth, rectY, rectX + rectWidth, rectY + rectHeight, radius);
  ctx.arcTo(rectX + rectWidth, rectY + rectHeight, rectX, rectY + rectHeight, radius);
  ctx.arcTo(rectX, rectY + rectHeight, rectX, rectY, radius);
  ctx.arcTo(rectX, rectY, rectX + rectWidth, rectY, radius);
  ctx.closePath();
  trapezoids.forEach((trap) => {
    ctx.moveTo(trap.direction === "left" ? trap.x : trap.x, trap.y - trap.height / 2 + trap.offset);
    if (trap.direction === "left") {
      ctx.lineTo(trap.x + trap.width, trap.y - trap.height / 2);
      ctx.lineTo(trap.x + trap.width, trap.y + trap.height / 2);
      ctx.lineTo(trap.x, trap.y + trap.height / 2 - trap.offset);
    } else {
      ctx.lineTo(trap.x - trap.width, trap.y - trap.height / 2);
      ctx.lineTo(trap.x - trap.width, trap.y + trap.height / 2);
      ctx.lineTo(trap.x, trap.y + trap.height / 2 - trap.offset);
    }
    ctx.closePath();
  });
};
const drawDiagonalPattern = (ctx, canvasWidth, canvasHeight, rectX, rectY, rectWidth, rectHeight, radius, trapezoids, strokeStyle = "rgba(173, 216, 230, .5)", lineWidth = 1, spacing = 5) => {
  const patternCanvas = document.createElement("canvas");
  patternCanvas.width = canvasWidth;
  patternCanvas.height = canvasHeight;
  const patternCtx = patternCanvas.getContext("2d");
  patternCtx.strokeStyle = strokeStyle;
  patternCtx.lineWidth = lineWidth;
  const angle = Math.PI / 6;
  const maxDimension = Math.max(canvasWidth, canvasHeight) * 2;
  patternCtx.beginPath();
  for (let i = -maxDimension; i < maxDimension; i += spacing) {
    const startX = canvasWidth - i;
    const startY = 0;
    const endX = startX - canvasHeight / Math.tan(angle);
    const endY = canvasHeight;
    patternCtx.moveTo(startX, startY);
    patternCtx.lineTo(endX, endY);
  }
  patternCtx.stroke();
  ctx.save();
  createTicketClipPath(ctx, rectX, rectY, rectWidth, rectHeight, radius, trapezoids);
  ctx.clip();
  ctx.drawImage(patternCanvas, 0, 0);
  ctx.restore();
};
const drawImageWithWear = (ctx, image, x, y, width, height, wearEffect) => {
  if (!wearEffect?.enabled || wearEffect.intensity <= 0) {
    ctx.drawImage(image, x, y, width, height);
    return;
  }
  const offscreenCanvas = document.createElement("canvas");
  offscreenCanvas.width = width;
  offscreenCanvas.height = height;
  const offCtx = offscreenCanvas.getContext("2d");
  offCtx.drawImage(image, 0, 0, width, height);
  const cacheKey = `wear_img_${width}_${height}_${wearEffect.intensity.toFixed(2)}`;
  const wearTexture = createWearTexture(width, height, cacheKey, wearEffect.intensity);
  offCtx.globalCompositeOperation = "destination-in";
  offCtx.drawImage(wearTexture, 0, 0, width, height);
  offCtx.globalCompositeOperation = "source-over";
  ctx.drawImage(offscreenCanvas, x, y);
};
export {
  clearWearTextureCache,
  createTicketClipPath,
  drawCustomText,
  drawDiagonalPattern,
  drawImageWithWear,
  drawParagraph,
  drawRoundRect,
  drawTrapezoid,
  getTextWidth
};
