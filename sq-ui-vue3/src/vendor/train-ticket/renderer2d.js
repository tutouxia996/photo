import { pinyin } from "pinyin-pro";
import QRCode from "qrcode";
import {
  drawCustomText,
  getTextWidth,
  drawParagraph,
  drawTrapezoid,
  drawRoundRect,
  drawDiagonalPattern,
  drawImageWithWear,
  createTicketClipPath
} from "./canvas.js";
import { buildFontString } from "./font.js";
import { maskedId, getStationSpacing } from "./common.js";
import CRHImage from "./assets/CRH.jpg";
const canvasWidth = 876;
const canvasHeight = 539;
const protrusionHeight = 40;
const protrusionWidth = 10;
const leftOffset = 80;
const ticketTrapezoids = [
  {
    x: 10,
    y: canvasHeight * 0.2,
    width: protrusionWidth,
    height: protrusionHeight,
    offset: 5,
    direction: "left"
  },
  {
    x: canvasWidth - 10,
    y: canvasHeight * 0.2,
    width: protrusionWidth,
    height: protrusionHeight,
    offset: 5,
    direction: "right"
  },
  {
    x: 10,
    y: canvasHeight * 0.8,
    width: protrusionWidth,
    height: protrusionHeight,
    offset: 5,
    direction: "left"
  },
  {
    x: canvasWidth - 10,
    y: canvasHeight * 0.8,
    width: protrusionWidth,
    height: protrusionHeight,
    offset: 5,
    direction: "right"
  }
];
const drawTicketDetails = (canvas, ctx, ticketInfo, styleConfig, callback) => {
  const fonts = styleConfig.fonts;
  const wearEffect = styleConfig.wearEffect;
  const showProtrusions = styleConfig.showProtrusions !== false;
  const topOffset = ticketInfo.checkGate !== "" ? 35 : 20;
  const drawText = (text, x, y, spacing = 0, color = [0, 0, 0]) => {
    drawCustomText(ctx, text, x, y, spacing, color, wearEffect);
  };
  drawRoundRect(ctx, 20, 10, canvasWidth - 40, canvasHeight - 20, 20, "rgba(173, 216, 230, .2)");
  if (showProtrusions) {
    ctx.fillStyle = "rgba(173, 216, 230, .2)";
    drawTrapezoid(ctx, 10, canvasHeight * 0.2, protrusionWidth, protrusionHeight, 5, "left");
    drawTrapezoid(
      ctx,
      canvasWidth - 10,
      canvasHeight * 0.2,
      protrusionWidth,
      protrusionHeight,
      5,
      "right"
    );
    drawTrapezoid(ctx, 10, canvasHeight * 0.8, protrusionWidth, protrusionHeight, 5, "left");
    drawTrapezoid(
      ctx,
      canvasWidth - 10,
      canvasHeight * 0.8,
      protrusionWidth,
      protrusionHeight,
      5,
      "right"
    );
  }
  const activeTrapezoids = showProtrusions ? ticketTrapezoids : [];
  drawDiagonalPattern(
    ctx,
    canvasWidth,
    canvasHeight,
    20,
    // rectX
    10,
    // rectY
    canvasWidth - 40,
    // rectWidth
    canvasHeight - 20,
    // rectHeight
    20,
    // radius
    activeTrapezoids,
    "rgba(173, 216, 230, .5)",
    // strokeStyle
    1,
    // lineWidth
    5
    // spacing
  );
  ctx.fillStyle = "#94cae0";
  ctx.beginPath();
  ctx.moveTo(30, canvasHeight * 0.9);
  ctx.lineTo(canvasWidth - 20, canvasHeight * 0.9);
  ctx.lineTo(canvasWidth - 20, canvasHeight - 20);
  ctx.arcTo(canvasWidth - 20, canvasHeight - 10, canvasWidth - 50, canvasHeight - 10, 20);
  ctx.lineTo(50, canvasHeight - 10);
  ctx.arcTo(20, canvasHeight - 10, 20, canvasHeight - 30, 20);
  ctx.lineTo(20, canvasHeight * 0.9);
  ctx.closePath();
  ctx.fill();
  const watermarkCanvas = document.createElement("canvas");
  watermarkCanvas.width = 16;
  watermarkCanvas.height = 18;
  const watermarkCtx = watermarkCanvas.getContext("2d");
  watermarkCtx.font = "8px Arial";
  watermarkCtx.fillStyle = "#94cae0";
  watermarkCtx.fillText("CR", 5, 12);
  const pattern = ctx.createPattern(watermarkCanvas, "repeat");
  ctx.fillStyle = pattern;
  ctx.fillRect(20, canvasHeight * 0.88, canvasWidth - 40, 20);
  ctx.fillStyle = "#000";
  ctx.font = buildFontString(fonts.checkGate.family, fonts.checkGate.size);
  if (ticketInfo.checkGate !== "") {
    const checkGateText = "\u68C0\u7968:" + ticketInfo.checkGate;
    const checkGateWidth = getTextWidth(ctx, checkGateText);
    drawText(checkGateText, canvasWidth - checkGateWidth - 100, 60);
  }
  const startStation = ticketInfo.startStation;
  const endStation = ticketInfo.endStation;
  ctx.font = buildFontString(fonts.station.family, fonts.station.size);
  let left = 110;
  if (startStation.length == 5) left = 70;
  drawText(startStation, left, topOffset + 80, getStationSpacing(startStation));
  left = canvasWidth / 2 + 120;
  if (endStation.length == 5) left = canvasWidth / 2 + 80;
  drawText(endStation, left, topOffset + 80, getStationSpacing(endStation));
  ctx.font = buildFontString(fonts.stationPinyin.family, fonts.stationPinyin.size);
  let startStationPinyin = pinyin(startStation, { toneType: "none" }).replace(/ /g, "");
  let endStationPinyin = pinyin(endStation, { toneType: "none" }).replace(/ /g, "");
  if (startStationPinyin)
    startStationPinyin = startStationPinyin[0].toUpperCase() + startStationPinyin.slice(1);
  if (endStationPinyin)
    endStationPinyin = endStationPinyin[0].toUpperCase() + endStationPinyin.slice(1);
  const startStationPinyinWidth = getTextWidth(ctx, startStationPinyin);
  const endStationPinyinWidth = getTextWidth(ctx, endStationPinyin);
  drawText(startStationPinyin, 200 - startStationPinyinWidth / 2, topOffset + 115, -1);
  drawText(endStationPinyin, canvasWidth / 2 + 220 - endStationPinyinWidth / 2, topOffset + 115, -1);
  ctx.font = buildFontString(fonts.trainNumber.family, fonts.trainNumber.size);
  const trainNumber = ticketInfo.trainNumber;
  const trainNumberWidth = getTextWidth(ctx, trainNumber) + 2 * trainNumber.length;
  drawText(trainNumber, canvasWidth / 2 - trainNumberWidth / 2, topOffset + 75, 2);
  const arrowStartX = canvasWidth / 2 - 60;
  const arrowStartY = topOffset + 82;
  const arrowLength = 120;
  const arrowHeight = 5;
  const arrowWidth = 20;
  ctx.beginPath();
  ctx.moveTo(arrowStartX, arrowStartY);
  ctx.lineTo(arrowStartX + arrowLength, arrowStartY);
  ctx.lineTo(arrowStartX + arrowLength - arrowWidth, arrowStartY - arrowHeight);
  ctx.stroke();
  ctx.font = buildFontString(fonts.seatInfo.family, fonts.seatInfo.size);
  drawText("\u7AD9", 270, topOffset + 75);
  drawText("\u7AD9", canvasWidth - 160, topOffset + 75);
  ctx.font = buildFontString(fonts.date.family, fonts.date.size);
  const date = new Date(ticketInfo.date);
  const year = date.getFullYear().toString();
  const month = date.getMonth() + 1 > 9 ? (date.getMonth() + 1).toString() : "0" + (date.getMonth() + 1);
  const day = date.getDate() > 9 ? date.getDate().toString() : "0" + date.getDate();
  drawText(year, leftOffset + 10, topOffset + 170, -2);
  drawText(month, leftOffset + 118, topOffset + 170, -2);
  drawText(day, leftOffset + 180, topOffset + 170, -2);
  drawText(ticketInfo.time, leftOffset + 248, topOffset + 170, -2);
  const seatCarriage = ticketInfo.seatCarriage.toString().padStart(2, "0");
  let seatNumber = ticketInfo.seatNumber.toString().padStart(3, "0");
  drawText(seatCarriage, canvasWidth / 2 + 120, topOffset + 170, -2);
  if (isNaN(parseInt(seatNumber.slice(-1)))) {
    const seatNumberLast = seatNumber.slice(-1);
    seatNumber = seatNumber.slice(0, -1);
    drawText(seatNumber, canvasWidth / 2 + 182, topOffset + 170, -3);
    ctx.font = buildFontString(fonts.seatInfo.family, fonts.seatInfo.size - 8);
    drawText(seatNumberLast, canvasWidth / 2 + 182 + 38, topOffset + 167, -3);
  } else {
    drawText(seatNumber, canvasWidth / 2 + 182, topOffset + 170, -3);
  }
  ctx.font = buildFontString(fonts.seatInfo.family, fonts.seatInfo.size);
  const seatType = ticketInfo.seatTypeCustom == "" ? ticketInfo.seatType : ticketInfo.seatTypeCustom;
  const seatTypeWidth = getTextWidth(ctx, seatType);
  drawText(seatType, 650 - seatTypeWidth / 2, topOffset + 210);
  ctx.font = buildFontString(fonts.label.family, fonts.label.size);
  drawText("\u5E74", leftOffset + 90, topOffset + 164);
  drawText("\u6708", leftOffset + 155, topOffset + 164);
  drawText("\u65E5", leftOffset + 218, topOffset + 164);
  drawText("\u5F00", leftOffset + 345, topOffset + 164);
  drawText("\u8F66", leftOffset + 515, topOffset + 164);
  drawText("\u53F7", leftOffset + 594, topOffset + 164);
  ctx.font = buildFontString(fonts.price.family, fonts.price.size);
  drawText("\uFFE5", leftOffset + 15, topOffset + 215);
  const price = (ticketInfo.price || 0).toFixed(1).toString();
  const priceWidth = getTextWidth(ctx, price);
  drawText(price, leftOffset + 50, topOffset + 215, -2);
  ctx.font = buildFontString(fonts.label.family, fonts.label.size);
  drawText("\u5143", leftOffset + 42 + priceWidth, topOffset + 210);
  ctx.font = buildFontString(fonts.seatInfo.family, fonts.seatInfo.size + 4);
  const identity = ticketInfo.identity || "adult";
  const isDiscount = ticketInfo.isDiscount;
  const identityMarkMap = {
    adult: "\u6210",
    student: "\u5B66",
    child: "\u5B69",
    military: "\u519B",
    disabled: "\u6B8B"
  };
  const marks = [];
  if (identityMarkMap[identity]) marks.push(identityMarkMap[identity]);
  if (isDiscount) marks.push("\u60E0");
  const payMethod = ticketInfo.payMethod || "";
  if (payMethod) marks.push(payMethod);
  if (marks.length > 0) {
    const circleR = 17;
    const gap = 50;
    const totalWidth = marks.length * circleR * 2 + (marks.length - 1) * (gap - circleR * 2);
    const centerX = canvasWidth / 2;
    const startCx = centerX - totalWidth / 2 + circleR;
    for (let i = 0; i < marks.length; i++) {
      const cx = startCx + i * gap;
      const markText = marks[i];
      const markWidth = getTextWidth(ctx, markText);
      drawText(markText, cx - markWidth / 2, topOffset + 210);
      ctx.beginPath();
      ctx.arc(cx, topOffset + 198, circleR, 0, 2 * Math.PI);
      ctx.stroke();
    }
  }
  ctx.font = buildFontString(fonts.seatInfo.family, fonts.seatInfo.size + 4);
  drawText("\u4EC5\u4F9B\u62A5\u9500\u4F7F\u7528", leftOffset, 330);
  ctx.font = buildFontString(fonts.passenger.family, fonts.passenger.size);
  const id = maskedId(ticketInfo.passengerId);
  const idWidth = getTextWidth(ctx, id);
  drawText(id, leftOffset, 370, -3);
  ctx.font = buildFontString(fonts.passenger.family, fonts.passenger.size - 4);
  drawText(ticketInfo.passengerName, leftOffset + idWidth + 10 - 48, 370);
  const dashWidth = 440;
  const dashHeight = 70;
  const dashLeft = 108;
  ctx.font = buildFontString(fonts.notice.family, fonts.notice.size);
  ctx.setLineDash([8, 2]);
  ctx.strokeRect(dashLeft, 380, dashWidth, dashHeight);
  ctx.setLineDash([]);
  const text1 = "\u62A5\u9500\u51ED\u8BC1 \u9057\u5931\u4E0D\u8865";
  const text1Width = getTextWidth(ctx, text1);
  const text2 = "\u9000\u7968\u6539\u7B7E\u65F6\u987B\u4EA4\u56DE\u8F66\u7AD9";
  const text2Width = getTextWidth(ctx, text2);
  drawText(text1, dashLeft + dashWidth / 2 - text1Width / 2, 408);
  drawText(text2, dashLeft + dashWidth / 2 - text2Width / 2, 440);
  const qrCodeText = styleConfig.qrContent || "https://github.com/FoskyM/train-ticket-generator";
  const qrCodeWidth = 120;
  const qrCodeOptions = {
    width: qrCodeWidth,
    margin: 0,
    color: {
      dark: "#000000b0",
      light: "#FFFFFF00"
    }
  };
  QRCode.toDataURL(qrCodeText, qrCodeOptions, (err, url) => {
    if (err) throw err;
    const qrImage = new Image();
    qrImage.src = url;
    qrImage.onload = () => {
      const qrWearEffect = wearEffect ? {
        ...wearEffect,
        intensity: wearEffect.intensity * 0.3
      } : void 0;
      drawImageWithWear(
        ctx,
        qrImage,
        dashLeft + dashWidth + 60,
        330,
        qrCodeWidth,
        qrCodeWidth,
        qrWearEffect
      );
      callback?.();
    };
  });
  ctx.font = buildFontString(fonts.ticketId.family, fonts.ticketId.size);
  // 日记本内每次重绘需稳定：用票号派生偏移，避免闪烁（原实现为 Math.random）
  let _h = 2166136261;
  const _seed = String(ticketInfo.id || ticketInfo.redId || "ticket");
  for (let _i = 0; _i < _seed.length; _i++) _h = Math.imul(_h ^ _seed.charCodeAt(_i), 16777619);
  const bottomOffset = (_h >>> 0) % 25 + 10;
  drawText(ticketInfo.id + " JM", leftOffset, canvasHeight - 50 + bottomOffset);
  ctx.fillStyle = "rgba(255, 0, 0, 0.5)";
  ctx.font = buildFontString(fonts.redId.family, fonts.redId.size);
  ctx.fillText(ticketInfo.redId, 80, 65);
};
const drawTicket = (canvas, ticketInfo, styleConfig, callback) => {
  const ctx = canvas.getContext("2d");
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const showProtrusions = styleConfig.showProtrusions !== false;
  const activeTrapezoids = showProtrusions ? ticketTrapezoids : [];
  ctx.save();
  createTicketClipPath(ctx, 20, 10, canvasWidth - 40, canvasHeight - 20, 20, activeTrapezoids);
  ctx.clip();
  ctx.fillStyle = "#fff";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.restore();
  const backgroundImage = new Image();
  backgroundImage.src = CRHImage;
  backgroundImage.onload = () => {
    const paddingX = 40;
    const paddingY = 100;
    const imgWidth = canvasWidth - 2 * paddingX;
    const imgHeight = canvasHeight - 2 * paddingY;
    const centerX = paddingX;
    const centerY = paddingY;
    const bgOpacity = styleConfig.backgroundOpacity ?? 0.05;
    ctx.globalAlpha = bgOpacity;
    ctx.drawImage(backgroundImage, centerX, centerY, imgWidth, imgHeight);
    ctx.globalAlpha = 1;
    drawTicketDetails(canvas, ctx, ticketInfo, styleConfig, callback);
  };
};
const drawTicketBack = (canvas, styleConfig, callback) => {
  const ctx = canvas.getContext("2d");
  const fonts = styleConfig.fonts;
  const wearEffect = styleConfig.wearEffect;
  const showProtrusions = styleConfig.showProtrusions !== false;
  const activeTrapezoids = showProtrusions ? ticketTrapezoids : [];
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.save();
  createTicketClipPath(ctx, 20, 10, canvasWidth - 40, canvasHeight - 20, 20, activeTrapezoids);
  ctx.clip();
  ctx.fillStyle = "#fff";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.restore();
  drawRoundRect(ctx, 20, 10, canvasWidth - 40, canvasHeight - 20, 20, "rgba(0, 0, 0, .9)");
  if (showProtrusions) {
    drawTrapezoid(ctx, 10, canvasHeight * 0.2, protrusionWidth, protrusionHeight, 5, "left");
    drawTrapezoid(
      ctx,
      canvasWidth - 10,
      canvasHeight * 0.2,
      protrusionWidth,
      protrusionHeight,
      5,
      "right"
    );
    drawTrapezoid(ctx, 10, canvasHeight * 0.8, protrusionWidth, protrusionHeight, 5, "left");
    drawTrapezoid(
      ctx,
      canvasWidth - 10,
      canvasHeight * 0.8,
      protrusionWidth,
      protrusionHeight,
      5,
      "right"
    );
  }
  ctx.font = buildFontString(fonts.backTitle.family, fonts.backTitle.size);
  const text = "\u62A5\u9500\u51ED\u8BC1\u4F7F\u7528\u987B\u77E5";
  const textWidth = getTextWidth(ctx, text);
  const color = [180, 180, 180];
  drawCustomText(ctx, text, canvasWidth / 2 - textWidth / 2, 80, 0, color, wearEffect);
  ctx.font = buildFontString(fonts.backContent.family, fonts.backContent.size);
  const paragraph = "\u2606\u8D2D\u7968\u540E\u5982\u9700\u62A5\u9500\u51ED\u8BC1\u7684\uFF0C\u5E94\u5728\u5F00\u8F66\u524D\u6216\u4E58\u8F66\u65E5\u671F\u4E4B\u65E5\u8D77180\u65E5\u4EE5\u5185(\u542B\u5F53\u65E5)\uFF0C\u6301\u8D2D\u7968\u65F6\u6240\u4F7F\u7528\u7684\u6709\u6548\u8EAB\u4EFD\u8BC1\u4EF6\u539F\u4EF6\u5230\u8F66\u7AD9\u552E\u7968\u7A97\u53E3\u3001\u81EA\u52A8\u552E\u7968\u673A\u9886\u53D6\u3002\u2606\u9000\u7968\u540E\u5982\u9700\u9000\u7968\u8D39\u62A5\u9500\u51ED\u8BC1\uFF0C\u5E94\u5728\u529E\u7406\u4E4B\u65E5\u8D77180\u5929\u4EE5\u5185(\u542B\u5F53\u65E5)\uFF0C\u6301\u8D2D\u7968\u65F6\u6240\u4F7F\u7528\u7684\u6709\u6548\u8EAB\u4EFD\u8BC1\u4EF6\u539F\u4EF6\u5230\u8F66\u7AD9\u9000\u7968\u7A97\u53E3\u9886\u53D6\u3002\u2606\u62A5\u9500\u51ED\u8BC1\u5F00\u5177\u540E\u8BF7\u59A5\u5584\u4FDD\u7BA1\uFF0C\u4E22\u5931\u540E\u5C06\u65E0\u6CD5\u529E\u7406\u8865\u529E\u7533\u9886\u624B\u7EED\u3002\u2606\u5DF2\u9886\u53D6\u62A5\u9500\u51ED\u8BC1\u7684\u8F66\u7968\u529E\u7406\u6539\u7B7E\u3001\u9000\u7968\u6216\u9000\u6B3E\u624B\u7EED\u65F6\uFF0C\u987B\u4EA4\u56DE\u62A5\u9500\u51ED\u8BC1\u65B9\u53EF\u529E\u7406\u3002\u2606\u62A5\u9500\u51ED\u8BC1\u4E0D\u80FD\u4F5C\u4E3A\u4E58\u8F66\u51ED\u8BC1\u4F7F\u7528\u3002\u2606\u672A\u5C3D\u4E8B\u5B9C\u89C1\u300A\u56FD\u94C1\u96C6\u56E2\u94C1\u8DEF\u65C5\u5BA2\u8FD0\u8F93\u89C4\u7A0B\u300B\u7B49\u6709\u5173\u89C4\u5B9A\u548C\u8F66\u7AD9\u516C\u544A\u3002\u8DE8\u5883\u65C5\u5BA2\u4E8B\u5B9C\u89C1\u94C1\u8DEF\u8DE8\u5883\u65C5\u5BA2\u76F8\u5173\u8FD0\u8F93\u7EC4\u7EC7\u89C4\u5219\u548C\u8F66\u7AD9\u516C\u544A\u3002";
  const paragraphs = paragraph.split("\u2606").filter((p) => p !== "").map((p) => "\u2606" + p);
  drawParagraph(ctx, paragraphs, 40, 45, 140, 35, canvasWidth - 90, -1, color, wearEffect);
  callback?.();
};
export {
  canvasHeight,
  canvasWidth,
  drawTicket,
  drawTicketBack,
  drawTicketDetails,
  protrusionHeight,
  protrusionWidth
};
