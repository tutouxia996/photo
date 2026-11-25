import {
  canvasWidth,
  canvasHeight,
  drawTicket,
  drawTicketBack
} from "./renderer2d.js";
const receiptConfig = {
  canvasWidth,
  canvasHeight,
  drawFront: drawTicket,
  drawBack: drawTicketBack,
  defaultStyleConfig: {
    fonts: {
      station: { family: "SimHei", size: 45 },
      stationPinyin: { family: "FangSong", size: 30 },
      trainNumber: { family: "SimSun", size: 42 },
      date: { family: "SimHei", size: 40 },
      seatInfo: { family: "SimSun", size: 28 },
      price: { family: "SimHei", size: 40 },
      passenger: { family: "FangSong", size: 40 },
      label: { family: "FangSong", size: 21 },
      notice: { family: "FangSong", size: 27 },
      ticketId: { family: "FangSong", size: 24 },
      redId: { family: "Arial", size: 42 },
      checkGate: { family: "SimSun", size: 24 },
      backTitle: { family: "SimHei", size: 40 },
      backContent: { family: "SimSun", size: 25 }
    },
    wearEffect: {
      enabled: true,
      intensity: 0.7
    },
    showProtrusions: true,
    backgroundOpacity: 0.05,
    qrContent: ""
  },
  styleFieldGroups: [
    {
      fields: [
        { label: "\u7AD9\u540D", key: "station", sizeMin: 20, sizeMax: 60 },
        { label: "\u7AD9\u540D\u62FC\u97F3", key: "stationPinyin", sizeMin: 16, sizeMax: 40 },
        { label: "\u8F66\u6B21", key: "trainNumber", sizeMin: 20, sizeMax: 60 },
        { label: "\u65E5\u671F\u65F6\u95F4", key: "date", sizeMin: 20, sizeMax: 50 },
        { label: "\u5EA7\u4F4D\u4FE1\u606F", key: "seatInfo", sizeMin: 16, sizeMax: 40 },
        { label: "\u7968\u4EF7", key: "price", sizeMin: 20, sizeMax: 50 },
        { label: "\u4E58\u5BA2\u4FE1\u606F", key: "passenger", sizeMin: 20, sizeMax: 50 }
      ]
    },
    {
      fields: [
        { label: "\u6807\u7B7E\u6587\u5B57", key: "label", sizeMin: 14, sizeMax: 30 },
        { label: "\u62A5\u9500\u63D0\u793A", key: "notice", sizeMin: 16, sizeMax: 36 },
        { label: "\u7968\u636EID", key: "ticketId", sizeMin: 14, sizeMax: 32 },
        { label: "\u7EA2\u8272ID", key: "redId", sizeMin: 20, sizeMax: 50 },
        { label: "\u68C0\u7968\u53E3", key: "checkGate", sizeMin: 14, sizeMax: 32 }
      ]
    },
    {
      fields: [
        { label: "\u80CC\u9762\u6807\u9898", key: "backTitle", sizeMin: 24, sizeMax: 50 },
        { label: "\u80CC\u9762\u5185\u5BB9", key: "backContent", sizeMin: 16, sizeMax: 32 }
      ]
    }
  ]
};
export {
  receiptConfig
};
