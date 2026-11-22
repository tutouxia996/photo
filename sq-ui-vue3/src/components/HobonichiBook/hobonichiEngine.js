/**
 * Faithful port of Claude's Hobonichi interactive leaf turner
 * (mesh + WebGL2 shaders + canvas-strip fallback + spring physics).
 */

export const PI = Math.PI
const NU = 112
const NV = 28
const PEXP = 2.6
const QEXP = 1.8
const HC = 3600
const HCY = 90000

const VS = `#version 300 es
in vec3 aP;in vec3 aN;in vec2 aUV;in float aF;uniform vec3 uCam;uniform vec2 uView;uniform vec2 uCen;uniform float uHC,uHCY,uShadow,uBias;
out vec3 vP;out vec3 vN;out vec2 vUV;out float vF;out vec2 vS;out vec2 vW;
void main(){vec3 p=aP;if(uShadow>.5){p=vec3(aP.x+.16*aP.z,aP.y+.2*aP.z,0.);}
float f=uHC/(uHC-p.z),fy=uHCY/(uHCY-p.z);vec2 w=vec2(uCam.x+(p.x-uCam.x)*f,uCam.y+(p.y-uCam.y)*fy);
vec2 s=(w-uCam.xy)*uCam.z+uCen;vS=s;vW=p.xy;vP=aP;vN=aN;vUV=aUV;vF=aF;
gl_Position=vec4(s.x/uView.x*2.-1.,1.-s.y/uView.y*2.,uShadow>.5?.0005:-aP.z/4000.+uBias*.00006,1.);}`

const FS = `#version 300 es
precision highp float;
in vec3 vP;in vec3 vN;in vec2 vUV;in float vF;in vec2 vS;in vec2 vW;
uniform sampler2D uFront;uniform sampler2D uBack;uniform sampler2D uUnderR;uniform sampler2D uUnderL;
uniform vec3 uEye;uniform vec3 uL;uniform vec3 uCam;uniform vec2 uView;uniform vec2 uCen;uniform vec2 uWH;uniform vec4 uClip;uniform vec2 uHas;
uniform float uLift,uTrans,uShow,uSpec,uShin,uGlow,uEdge,uBoard,uPx,uShadow,uShA,uLod;
out vec4 o;const vec3 PAPER=vec3(.984,.969,.937);/* Tomoe River cream ≈ #fbf7ef */
float lum(vec3 c){return dot(c,vec3(.299,.587,.114));}
void main(){
 if(uShadow>.5){
  if(vW.x<uClip.x||vW.x>uClip.y||vW.y<uClip.z||vW.y>uClip.w)discard;
  float h=vP.z,pen=4.+h*.09,d=min((1.-vUV.x)*uWH.x,min(vUV.y,1.-vUV.y)*uWH.y);
  float a=uShA*smoothstep(0.,pen,d)*(1.-.55*clamp(h/uWH.x,0.,1.))*smoothstep(.3,6.,h);
  o=vec4(vec3(.33,.24,.14)*a,a);return;}
 vec3 V=normalize(uEye-vP),N=normalize(vN);bool recto=dot(N,V)>=0.;vec3 Nv=recto?N:-N;
 vec3 cF=texture(uFront,vUV,uLod).rgb,cB=texture(uBack,vec2(1.-vUV.x,vUV.y),uLod).rgb;
 vec3 face=recto?cF:cB,rev=recto?cB:cF;
 float h=max(vP.z,0.),air=smoothstep(140.,300.,h);
 face*=1.-uShow*uLift*air*vF*clamp(1.-lum(rev)/lum(PAPER),0.,1.);
 float ndl=dot(Nv,uL),sh=(.87+.13*max(ndl,0.))/(.87+.13*uL.z);
 vec3 col=face*sh*mix(vec3(.985,.968,.94),vec3(1.),smoothstep(.87,1.,sh));
 col*=1.-.035*uLift*(1.-uBoard)*exp(-vUV.x*uWH.x/28.)*smoothstep(4.,40.,h+vUV.x*60.);
 col+=vec3(1.,.9,.74)*uGlow*uLift*max(-ndl,0.);
 vec3 H=normalize(uL+V);float sp=pow(max(dot(Nv,H),0.),uShin),sp0=pow(normalize(uL+vec3(0.,0.,1.)).z,uShin);
 col+=vec3(1.,.98,.93)*uSpec*uLift*max(sp-sp0,0.);
 if(uTrans>0.){vec2 gw=uCam.xy+(vS-uCen)/uCam.z;float lod=.5+log2(1.+h*.05);vec3 bgR=PAPER,bgL=PAPER;
  if(uHas.x>.5)bgR=textureLod(uUnderR,vec2(clamp(gw.x/uWH.x,0.,1.),clamp(gw.y/uWH.y,0.,1.)),lod).rgb;
  if(uHas.y>.5)bgL=textureLod(uUnderL,vec2(clamp((gw.x+uWH.x)/uWH.x,0.,1.),clamp(gw.y/uWH.y,0.,1.)),lod).rgb;
  /* 订口附近柔混左右底页，避免硬切竖缝 */
  float seam=smoothstep(-3.,3.,gw.x);vec3 bg=mix(bgL,bgR,seam);
  if(gw.y<0.||gw.y>uWH.y||abs(gw.x)>uWH.x)bg=PAPER;
  float T=uTrans*uLift*air*vF*mix(.35,1.,abs(dot(Nv,V)));col*=mix(vec3(1.),clamp(bg/PAPER,0.,1.1),T);}
 float ex=(1.-vUV.x)/max(fwidth(vUV.x),1e-6),ey=min(vUV.y,1.-vUV.y)/max(fwidth(vUV.y),1e-6),e=min(ex,ey)/uPx;
 float dk=1.-smoothstep(0.,1.+uBoard,e),pl=smoothstep(.6+uBoard,1.3+uBoard,e)*(1.-smoothstep(1.4+uBoard,2.5+uBoard,e));
 col=mix(col,col*(.8-.1*uBoard),dk*uEdge*uLift);col=mix(col,min(col+.05,vec3(1.)),pl*uEdge*uLift);
 o=vec4(col,1.);}`

const LAMP = (() => {
  const l = [-0.34, -0.42, 1]
  const n = Math.hypot(l[0], l[1], l[2])
  return [l[0] / n, l[1] / n, l[2] / n]
})()

export function clamp(v, a, b) {
  return v < a ? a : v > b ? b : v
}
function sm(u) {
  u = clamp(u, 0, 1)
  return u * u * (3 - 2 * u)
}
function spring(x, v, target, wn, z, dt) {
  const a = wn * wn * (target - x) - 2 * z * wn * v
  v += a * dt
  x += v * dt
  return [x, v]
}
function springS(x, v, target, wn, z, dt) {
  const n = Math.max(1, Math.ceil(dt * wn * 4))
  const h = dt / n
  let q = [x, v]
  for (let i = 0; i < n; i++) q = spring(q[0], q[1], target, wn, z, h)
  return q
}
function liftF(th) {
  return Math.min(1, Math.sin(th) / 0.22)
}

export function makeCanvas(w, h) {
  const c = document.createElement('canvas')
  c.width = Math.max(2, Math.round(w))
  c.height = Math.max(2, Math.round(h))
  return c
}

/** 80g 米黄道林纸（Tomoe River cream） */
export const TOMOE_CREAM = '#fbf7ef'

/** 纯空白米黄纸，仅保留轻微纸纹 */
export function paintBlank(c, _hand = 'right', seed = 11) {
  const g = c.getContext('2d')
  const w = c.width
  const h = c.height
  const r = gRnd(seed)
  g.fillStyle = TOMOE_CREAM
  g.fillRect(0, 0, w, h)
  // 订口侧洗色压淡，避免左右页相接时叠出硬竖线
  const wash = g.createLinearGradient(0, 0, w * 0.12, h)
  wash.addColorStop(0, 'rgba(245,236,214,0.12)')
  wash.addColorStop(0.5, 'rgba(251,247,239,0)')
  wash.addColorStop(1, 'rgba(236,226,204,0.1)')
  g.fillStyle = wash
  g.fillRect(0, 0, w, h)
  g.fillStyle = 'rgba(146,128,92,0.028)'
  for (let i = 0; i < 2200; i++) g.fillRect(r() * w, r() * h, 1, 1)
}

/* —— Hobonichi Cousin cover: soft ivory cloth (from arnilabs reference) —— */
const V_IVORY = '#F4E3C2'
const V_STRIP = '#F2E3CB'
const V_ENDP = '#FAF4E8'
const V_MARK = '#8B7E66'

function gRnd(seed) {
  let s = seed >>> 0
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0
    return s / 4294967296
  }
}
function hexToRgb(h) {
  h = h.replace('#', '')
  if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2]
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)]
}
function s0Hex(c) {
  return `#${c.map((v) => {
    v = Math.max(0, Math.min(255, Math.round(v)))
    return (v < 16 ? '0' : '') + v.toString(16)
  }).join('')}`
}
function mixC(a, b, t) {
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]
}
function tint(hex, t) { return s0Hex(mixC(hexToRgb(hex), [255, 253, 246], t)) }
function shade(hex, t) { return s0Hex(mixC(hexToRgb(hex), [22, 18, 26], t)) }

function cloth(g, w, h, seed, base) {
  const r = gRnd(seed)
  const gr = g.createLinearGradient(0, 0, w * 0.35, h)
  gr.addColorStop(0, tint(base, 0.18))
  gr.addColorStop(0.55, base)
  gr.addColorStop(1, shade(base, 0.035))
  g.fillStyle = gr
  g.fillRect(0, 0, w, h)
  g.globalAlpha = 0.035
  g.fillStyle = '#6B5A3E'
  for (let y = 0; y < h; y += 1.6) g.fillRect(0, y, w, 0.35)
  g.globalAlpha = 0.022
  for (let x = 0; x < w; x += 1.7) g.fillRect(x, 0, 0.35, h)
  g.globalAlpha = 1
  for (let i = 0; i < (w * h) / 55; i++) {
    g.fillStyle = `rgba(107,90,62,${(0.03 + r() * 0.06).toFixed(3)})`
    g.fillRect(r() * w, r() * h, 0.5 + r() * 0.7, 0.5 + r() * 0.7)
  }
  for (let i = 0; i < (w * h) / 140; i++) {
    g.fillStyle = `rgba(255,252,240,${(0.08 + r() * 0.12).toFixed(3)})`
    g.fillRect(r() * w, r() * h, 0.6, 0.6)
  }
}

function spineStrip(g, x0, x1, h, seed, edgeX) {
  const r = gRnd(seed)
  g.fillStyle = V_STRIP
  g.fillRect(x0, 0, x1 - x0, h)
  g.globalAlpha = 0.05
  g.fillStyle = '#6B5A3E'
  for (let y = 0; y < h; y += 1.2) g.fillRect(x0, y, x1 - x0, 0.3)
  g.globalAlpha = 1
  for (let i = 0; i < ((x1 - x0) * h) / 40; i++) {
    g.fillStyle = `rgba(107,90,62,${(0.03 + r() * 0.05).toFixed(3)})`
    g.fillRect(x0 + r() * (x1 - x0), r() * h, 0.5, 0.5)
  }
  g.fillStyle = 'rgba(150,122,78,.32)'
  g.fillRect(edgeX - 0.5, 0, 1, h)
  g.fillStyle = 'rgba(255,250,236,.7)'
  g.fillRect(edgeX + (edgeX > x0 + 1 ? 0.8 : -1.8), 0, 1, h)
}

function txtSpaced(g, s, xr, y, ls) {
  let tw = 0
  for (let i = 0; i < s.length; i++) tw += g.measureText(s[i]).width + (i < s.length - 1 ? ls : 0)
  let x = xr - tw
  g.textAlign = 'left'
  for (let i = 0; i < s.length; i++) {
    g.fillText(s[i], x, y)
    x += g.measureText(s[i]).width + ls
  }
  g.textAlign = 'right'
}

function paintNightWindowSticker(g, cx, cy, rot, sw, sh) {
  const S = 3
  const A = makeCanvas(sw * S, sh * S)
  const a = A.getContext('2d')
  a.setTransform(S, 0, 0, S, 0, 0)
  const x0 = 8
  const y0 = 8
  const x1 = sw - 8
  const y1 = sh - 8
  // night tile
  a.fillStyle = '#2A3358'
  roundRect(a, x0, y0, x1 - x0, y1 - y0, 18)
  a.fill()
  a.fillStyle = 'rgba(60,72,120,0.45)'
  roundRect(a, x0 + 6, y0 + 6, x1 - x0 - 36, y1 - y0 - 48, 14)
  a.fill()
  // moon crescent
  const mx = x1 - 30
  const my = y0 + 26
  a.fillStyle = '#FFF3D6'
  a.beginPath(); a.arc(mx, my, 11, 0, Math.PI * 2); a.fill()
  a.fillStyle = '#2A3358'
  a.beginPath(); a.arc(mx - 6, my - 4, 10, 0, Math.PI * 2); a.fill()
  // stars
  ;[[x0 + 22, y0 + 22, 3.2], [x0 + 44, y0 + 14, 2.2], [x1 - 14, y0 + 54, 2]].forEach(([sx, sy, r]) => {
    a.strokeStyle = '#FFF3D6'
    a.lineWidth = 1.1
    a.beginPath(); a.moveTo(sx, sy - r); a.lineTo(sx, sy + r); a.moveTo(sx - r, sy); a.lineTo(sx + r, sy); a.stroke()
  })
  // lit window
  const wx = x0 + 26
  const wy = y0 + 44
  const ww = x1 - x0 - 52
  const wh = y1 - y0 - 70
  const lg = a.createLinearGradient(0, wy, 0, wy + wh)
  lg.addColorStop(0, '#F2B950')
  lg.addColorStop(1, '#FFE3A1')
  a.fillStyle = lg
  a.fillRect(wx, wy, ww, wh)
  a.fillStyle = '#FFF6E4'
  a.beginPath()
  a.moveTo(wx + 2, wy + 2); a.lineTo(wx + 13, wy + 2); a.lineTo(wx + 10, wy + wh * 0.5)
  a.lineTo(wx + 13, wy + wh - 2); a.lineTo(wx + 2, wy + wh - 2); a.closePath(); a.fill()
  a.fillStyle = '#FFF9EE'
  ;[
    [wx, wy, ww, 4], [wx, wy + wh - 4, ww, 4], [wx, wy, 4, wh], [wx + ww - 4, wy, 4, wh],
    [wx + ww / 2 - 2, wy, 4, wh], [wx, wy + wh / 2 - 2, ww, 4]
  ].forEach(([rx, ry, rw, rh]) => a.fillRect(rx, ry, rw, rh))
  a.strokeStyle = '#2B2A33'
  a.lineWidth = 1.5
  a.strokeRect(wx, wy, ww, wh)
  a.fillStyle = '#FFF9EE'
  a.fillRect(wx - 6, wy + wh, ww + 12, 5)
  a.strokeRect(wx - 6, wy + wh, ww + 12, 5)

  // vinyl keyline + shadow
  placeSticker(g, A, cx, cy, rot, sw, sh, 6)
}

function paintLanternSticker(g, cx, cy, rot, sw, sh) {
  const S = 3
  const A = makeCanvas(sw * S, sh * S)
  const a = A.getContext('2d')
  a.setTransform(S, 0, 0, S, 0, 0)
  // soft blob figure (stands in for the reference cat)
  a.fillStyle = '#F7F0E2'
  a.beginPath()
  a.ellipse(sw * 0.42, sh * 0.62, sw * 0.18, sh * 0.22, 0, 0, Math.PI * 2)
  a.fill()
  a.beginPath()
  a.ellipse(sw * 0.42, sh * 0.38, sw * 0.14, sh * 0.14, 0, 0, Math.PI * 2)
  a.fill()
  a.strokeStyle = '#2B2A33'
  a.lineWidth = 1.6
  a.beginPath()
  a.ellipse(sw * 0.42, sh * 0.62, sw * 0.18, sh * 0.22, 0, 0, Math.PI * 2)
  a.stroke()
  a.beginPath()
  a.ellipse(sw * 0.42, sh * 0.38, sw * 0.14, sh * 0.14, 0, 0, Math.PI * 2)
  a.stroke()
  // ears
  a.beginPath()
  a.moveTo(sw * 0.32, sh * 0.3); a.lineTo(sw * 0.28, sh * 0.18); a.lineTo(sw * 0.4, sh * 0.28)
  a.moveTo(sw * 0.52, sh * 0.3); a.lineTo(sw * 0.56, sh * 0.18); a.lineTo(sw * 0.44, sh * 0.28)
  a.stroke()
  // arm + lantern
  a.beginPath()
  a.moveTo(sw * 0.52, sh * 0.48); a.lineTo(sw * 0.68, sh * 0.42); a.lineTo(sw * 0.72, sh * 0.55)
  a.stroke()
  const lx = sw * 0.74
  const ly = sh * 0.62
  a.fillStyle = '#FFE7B0'
  a.beginPath()
  a.moveTo(lx - 10, ly - 14); a.lineTo(lx + 10, ly - 14)
  a.lineTo(lx + 13, ly + 8); a.lineTo(lx + 7, ly + 16)
  a.lineTo(lx - 7, ly + 16); a.lineTo(lx - 13, ly + 8)
  a.closePath(); a.fill()
  a.fillStyle = '#F2B950'
  a.globalAlpha = 0.55
  a.beginPath(); a.ellipse(lx, ly + 2, 18, 18, 0, 0, Math.PI * 2); a.fill()
  a.globalAlpha = 1
  a.strokeStyle = '#2B2A33'
  a.lineWidth = 1.4
  a.beginPath()
  a.moveTo(lx - 10, ly - 14); a.lineTo(lx + 10, ly - 14)
  a.lineTo(lx + 13, ly + 8); a.lineTo(lx + 7, ly + 16)
  a.lineTo(lx - 7, ly + 16); a.lineTo(lx - 13, ly + 8)
  a.closePath(); a.stroke()
  a.beginPath(); a.moveTo(lx - 4, ly - 14); a.lineTo(lx, ly - 22); a.lineTo(lx + 4, ly - 14); a.stroke()
  // spark lines
  a.strokeStyle = '#E07A5F'
  a.lineWidth = 1.3
  ;[[-1.6, -0.2], [1.6, -0.2], [0, 1.7], [-1.2, 1.2], [1.2, 1.2]].forEach(([dx, dy]) => {
    const n = Math.hypot(dx, dy) || 1
    a.beginPath()
    a.moveTo(lx + (dx / n) * 14, ly + (dy / n) * 14)
    a.lineTo(lx + (dx / n) * 22, ly + (dy / n) * 22)
    a.stroke()
  })

  placeSticker(g, A, cx, cy, rot, sw, sh, 7)
}

function roundRect(g, x, y, w, h, r) {
  const rr = Math.min(r, w / 2, h / 2)
  g.beginPath()
  g.moveTo(x + rr, y)
  g.arcTo(x + w, y, x + w, y + h, rr)
  g.arcTo(x + w, y + h, x, y + h, rr)
  g.arcTo(x, y + h, x, y, rr)
  g.arcTo(x, y, x + w, y, rr)
  g.closePath()
}

function placeSticker(g, art, cx, cy, rot, sw, sh, edge) {
  const S = art.width / sw
  const K = makeCanvas(art.width, art.height)
  const k = K.getContext('2d')
  const n = 28
  for (let i = 0; i < n; i++) {
    const an = (i / n) * Math.PI * 2
    k.drawImage(art, Math.cos(an) * edge * S, Math.sin(an) * edge * S)
  }
  k.drawImage(art, 0, 0)
  k.globalCompositeOperation = 'source-in'
  k.fillStyle = '#FFFFFF'
  k.fillRect(0, 0, K.width, K.height)

  g.save()
  g.translate(cx, cy)
  g.rotate(rot)
  g.translate(-sw / 2, -sh / 2)
  g.save()
  g.shadowColor = 'rgba(60,45,25,.28)'
  g.shadowBlur = 5
  g.shadowOffsetX = 1.2
  g.shadowOffsetY = 2.2
  g.drawImage(K, 0, 0, sw, sh)
  g.restore()
  g.drawImage(K, 0, 0, sw, sh)
  g.drawImage(art, 0, 0, sw, sh)
  // gloss only on the sticker silhouette
  const G = makeCanvas(art.width, art.height)
  const gg = G.getContext('2d')
  gg.drawImage(K, 0, 0)
  gg.globalCompositeOperation = 'source-in'
  const lg = gg.createLinearGradient(0, 0, G.width, G.height)
  lg.addColorStop(0, 'rgba(255,255,255,.34)')
  lg.addColorStop(0.28, 'rgba(255,255,255,.1)')
  lg.addColorStop(0.42, 'rgba(255,255,255,0)')
  lg.addColorStop(0.62, 'rgba(255,255,255,0)')
  lg.addColorStop(0.7, 'rgba(255,255,255,.12)')
  lg.addColorStop(0.76, 'rgba(255,255,255,0)')
  gg.fillStyle = lg
  gg.fillRect(0, 0, G.width, G.height)
  g.drawImage(G, 0, 0, sw, sh)
  g.restore()
}

/**
 * Ivory cloth Cousin cover — layout matches arnilabs hobonichi:
 * spine strip · right wordmark · paper label · vinyl stickers.
 */
export function paintCoverFront(c, title, sub, year) {
  const g = c.getContext('2d')
  const w = c.width
  const h = c.height
  const s = w / 524 // scale from reference Cousin units
  const strip = 46 * s
  const yr = String(year || new Date().getFullYear())

  cloth(g, w, h, 701, V_IVORY)
  spineStrip(g, 0, strip, h, 702, strip)

  // wordmark, small, right — as printed on the real Cousin
  g.fillStyle = V_MARK
  g.textBaseline = 'alphabetic'
  const xr = w - 46 * s
  const fs = Math.max(12, 25 * s)
  g.font = `400 ${fs}px Jost,"Helvetica Neue",sans-serif`
  txtSpaced(g, 'HOBONICHI', xr, 330 * s, 2.2 * s)
  g.font = `700 ${fs}px Jost,"Helvetica Neue",sans-serif`
  const wYear = (() => {
    g.font = `700 ${fs}px Jost,"Helvetica Neue",sans-serif`
    return g.measureText(yr).width + 3 * 1.6 * s
  })()
  g.font = `700 ${fs}px Jost,"Helvetica Neue",sans-serif`
  txtSpaced(g, yr, xr, 364 * s, 1.6 * s)
  g.font = `400 ${fs}px Jost,"Helvetica Neue",sans-serif`
  txtSpaced(g, 'TECHO ', xr - wYear, 364 * s, 2.2 * s)
  g.font = `500 ${Math.max(9, 15 * s)}px Jost,"Helvetica Neue",sans-serif`
  txtSpaced(g, 'cousin', xr, 404 * s, 0.8 * s)

  // paper label (hand-style)
  const label = (title && String(title).trim()) || '相册日记'
  const subLine = (sub && String(sub).trim()) || `A5 · ${yr}`
  g.save()
  g.translate(70 * s, 80 * s)
  g.rotate(-0.018)
  const lw = Math.max(120 * s, g.measureText(label).width + 48 * s)
  const lh = 66 * s
  g.save()
  g.shadowColor = 'rgba(80,60,30,.16)'
  g.shadowBlur = 2.5
  g.shadowOffsetY = 1
  g.fillStyle = '#FFFDF6'
  g.fillRect(0, 0, lw + 44 * s, lh)
  g.restore()
  g.strokeStyle = 'rgba(139,126,102,.55)'
  g.lineWidth = 0.8 * s
  g.strokeRect(4 * s, 4 * s, lw + 36 * s, lh - 8 * s)
  g.strokeRect(6.5 * s, 6.5 * s, lw + 31 * s, lh - 13 * s)
  g.fillStyle = '#2B2A33'
  g.textAlign = 'left'
  g.font = `500 ${Math.max(13, 18 * s)}px "Zen Kaku Gothic New","Noto Sans SC",sans-serif`
  g.fillText(label, 22 * s, 32 * s)
  g.font = `400 ${Math.max(10, 12 * s)}px Jost,sans-serif`
  g.fillStyle = 'rgba(43,42,51,0.55)'
  g.fillText(subLine, 22 * s, 52 * s)
  g.restore()

  // vinyl stickers
  paintLanternSticker(g, 156 * s, 588 * s, -0.09, 230 * s, 220 * s)
  paintNightWindowSticker(g, 382 * s, 566 * s, 0.07, 150 * s, 160 * s)
}

export function paintCoverInside(c, spineLeft = true) {
  const g = c.getContext('2d')
  const w = c.width
  const h = c.height
  const r = gRnd(spineLeft ? 722 : 721)
  g.fillStyle = V_ENDP
  g.fillRect(0, 0, w, h)
  for (let i = 0; i < (w * h) / 60; i++) {
    g.fillStyle = `rgba(107,90,62,${(0.02 + r() * 0.04).toFixed(3)})`
    g.fillRect(r() * w, r() * h, 0.5, 0.5)
  }
  const x = spineLeft ? 0 : w
  const gr = g.createLinearGradient(x, 0, spineLeft ? 26 : w - 26, 0)
  gr.addColorStop(0, 'rgba(120,96,60,.14)')
  gr.addColorStop(1, 'rgba(120,96,60,0)')
  g.fillStyle = gr
  g.fillRect(spineLeft ? 0 : w - 26, 0, 26, h)
  // 不再画订口 1px 实线（翻页透出时会像竖缝）
}

export function paintCoverBack(c, year) {
  const g = c.getContext('2d')
  const w = c.width
  const h = c.height
  const s = w / 524
  const strip = 46 * s
  const yr = String(year || new Date().getFullYear())
  cloth(g, w, h, 711, V_IVORY)
  spineStrip(g, w - strip, w, h, 712, w - strip)
  g.fillStyle = 'rgba(139,126,102,.8)'
  g.font = `400 ${Math.max(8, 9 * s)}px Jost,"Helvetica Neue",sans-serif`
  g.textAlign = 'center'
  txtSpaced(g, `HOBONICHI TECHO ${yr} · cousin`, (w - strip) / 2 + (w - strip) * 0.18, h - 40 * s, 1.2 * s)
}

export function createUnit(kind) {
  return {
    kind,
    th: 0,
    om: 0,
    de: 0,
    dv: 0,
    tw: 0,
    tv: 0,
    vl: 0.8,
    mode: 'rest',
    goal: 0,
    side: 0,
    tt: 0,
    st: null,
    fresh: true
  }
}

export function isBoard(U) {
  return U.kind !== 'paper'
}

export function stepUnit(U, dt, now, reducedMotion) {
  const b = isBoard(U)
  const th = U.th
  const lf = liftF(th)
  let deT = 0
  let twT = 0
  if (U.mode === 'drag' || U.mode === 'auto' || U.mode === 'peek') {
    let q = springS(U.th, U.om, U.tt, b ? 11 : 30, 1, dt)
    U.th = q[0]
    U.om = q[1]
    if (U.th < 0) { U.th = 0; U.om = Math.max(0, U.om) }
    if (U.th > PI) { U.th = PI; U.om = Math.min(0, U.om) }
    deT = clamp(0.55 * Math.cos(th) * lf + 0.085 * U.om, -1.2, 1.3)
    twT = clamp(0.05 * U.om + 0.62 * Math.cos(th) * lf, -0.9, 0.9)
    if (U.mode === 'peek') deT = Math.max(deT, 0.4 * lf)
  } else if (U.mode === 'free') {
    const G = b ? 17 : 24
    const c = b ? 0.5 : 0.9
    U.om += (-G * Math.cos(U.th) - c * U.om) * dt
    U.th += U.om * dt
    deT = clamp(-0.07 * U.om - 0.25 * Math.cos(th) * lf, -1.1, 1.1)
    twT = clamp(0.05 * U.om + 0.14 * Math.cos(th) * lf, -0.5, 0.5)
    if (U.th >= PI || U.th <= 0) land(U, U.th >= PI ? 1 : 0, now)
  } else if (U.mode === 'settle') {
    if (U.st && now - U.st.t0 > (reducedMotion ? 0.25 : 0.95) && Math.abs(U.de) < 0.004 && Math.abs(U.tw) < 0.004) {
      U.mode = 'rest'
      U.st = null
      U.de = U.tw = U.dv = U.tv = 0
    }
  }
  if (b) { deT = 0; twT = 0 }
  let q = springS(U.de, U.dv, deT, b ? 20 : 13, reducedMotion ? 1 : (b ? 0.8 : 0.5), dt)
  U.de = q[0]
  U.dv = q[1]
  q = springS(U.tw, U.tv, twT, 11, reducedMotion ? 1 : 0.6, dt)
  U.tw = q[0]
  U.tv = q[1]
  if (U.mode === 'rest') U.de = U.tw = U.dv = U.tv = 0
}

export function land(U, side, now) {
  const imp = Math.abs(U.om)
  U.th = side ? PI : 0
  U.om = 0
  U.goal = side
  U.side = side
  U.mode = 'settle'
  U.st = { t0: now, amp: clamp(imp / 8, 0.25, 1), side }
}

/**
 * 松手后自由落体翻页。
 * @param intent optional { fwd: boolean, progress: 0–1 }
 *   fwd=false 表示用户正在回翻；此前对称阈值会导致「快翻过去却弹回」。
 */
export function release(U, intent = null) {
  const b = isBoard(U)
  const G = b ? 17 : 24
  const need = 2 * G * (1 - Math.sin(U.th)) * 1.3
  const prefer =
    intent && typeof intent.fwd === 'boolean' ? (intent.fwd ? 1 : 0) : null
  const progress = intent && intent.progress != null ? intent.progress : 0

  // 清掉与意图相反的残速，避免回翻拖到一半被正角速度判成「继续往前」
  if (prefer === 0 && U.om > 0) U.om = Math.min(U.om, 0.35)
  if (prefer === 1 && U.om < 0) U.om = Math.max(U.om, -0.35)

  // 手势过半：直接按意图补足越过中线所需角速度
  if (prefer != null && progress >= 0.48) {
    const dir = prefer ? 1 : -1
    U.om = dir * Math.max(Math.abs(U.om), Math.sqrt(Math.max(need, 4)))
    U.goal = prefer
    U.mode = 'free'
    return
  }

  // 仅沿当前 om 方向补能；不反向「甩」过去
  if (Math.abs(U.om) > 2.2 && U.om * U.om < need) {
    const sgn = U.om > 0 ? 1 : -1
    if (prefer == null || (prefer === 1 && sgn > 0) || (prefer === 0 && sgn < 0)) {
      U.om = sgn * Math.sqrt(need)
    }
  }
  const over = U.om * U.om >= need * 0.99
  // 回翻阈值略偏后（0.55π），往前略偏前（0.45π），减少「差一点点弹回」
  const mid = prefer === 0 ? PI * 0.55 : prefer === 1 ? PI * 0.45 : PI / 2
  U.goal = over ? (U.om > 0 ? 1 : 0) : (U.th > mid ? 1 : 0)
  U.mode = 'free'
}

export function startAuto(U, fwd, reducedMotion) {
  if (U.mode === 'rest' || U.mode === 'peek') U.fresh = true
  U.mode = 'auto'
  U.st = null
  U.goal = fwd ? 1 : 0
  U.tt = fwd ? PI * 0.66 : PI * 0.34
  U._auto = {
    t: 0,
    from: U.th,
    to: U.tt,
    dur: reducedMotion ? 0.25 : (isBoard(U) ? 0.42 : 0.5),
    dir: fwd ? 1 : -1
  }
}

export function stepAuto(U, dt) {
  if (U.mode !== 'auto' || !U._auto) return
  const A = U._auto
  A.t += dt
  const u = clamp(A.t / A.dur, 0, 1)
  const e2 = u < 0.5 ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2
  U.tt = A.from + (A.to - A.from) * e2
  if (u >= 1) {
    U.om = A.dir * Math.max(Math.abs(U.om), 6.5)
    U.goal = A.dir > 0 ? 1 : 0
    U.mode = 'free'
    U._auto = null
  }
}

function aRow(th, de, W, X, Z) {
  const ds = 1 / NU
  const e = de >= 0 ? PEXP : QEXP
  const al = th - de / (e + 1)
  X[0] = 0
  Z[0] = 0
  for (let i = 0; i < NU; i++) {
    const s = (i + 0.5) * ds
    const ph = clamp(al + de * Math.pow(s, e), 0, PI)
    X[i + 1] = X[i] + Math.cos(ph) * ds * W
    Z[i + 1] = Z[i] + Math.sin(ph) * ds * W
  }
}

function aRipple(st, v, s, now) {
  const ts = now - st.t0
  const a = st.amp
  if (a <= 0 || ts > 1.2) return 0
  const mem = 12 * a * Math.sin((PI / 2) * Math.min(ts / 0.08, 1)) * Math.exp(-ts / 0.34)
  const rip = 18 * a * (1 - Math.exp(-ts / 0.03)) * Math.exp(-ts / 0.17)
  const cor = 20 * a * (0.45 + 0.55 * v) * Math.exp(-ts / 0.12) * Math.abs(Math.sin((PI * ts) / 0.14))
  const zm = s < 0.32 ? mem * (s / 0.32) * Math.pow(1 - s / 0.32, 2) * 6.75 : 0
  const zr = rip * Math.pow(Math.max(0, Math.sin(2 * PI * (ts * 3.1 - s * 1.15))), 1.5) * s
  return zm + zr + cor * Math.pow(s, 4)
}

function aBounce(st, now) {
  const ts = now - st.t0
  if (st.amp <= 0 || ts > 0.6) return 0
  return st.amp * 0.02 * Math.exp(-ts / 0.09) * Math.abs(Math.sin((PI * ts) / 0.12))
}

function aLag(th, lag) {
  if (lag > 0) return th * (1 - Math.exp(-lag / Math.max(th, 1e-4)))
  if (lag < 0) {
    const hr = PI - th
    return -hr * (1 - Math.exp(lag / Math.max(hr, 1e-4)))
  }
  return 0
}

function aMesh(slot, G, L, now) {
  const P = slot.P
  const N = slot.N
  const F = slot.F
  const W = G.W
  let zmax = 0
  const board = !!L.board
  for (let j = 0; j <= NV; j++) {
    const v = j / NV
    const d = Math.abs(v - L.vl)
    const thr = L.th - aLag(L.th, L.tw * Math.pow(d, 1.25))
    const der = board ? L.de : L.de * (1 - 0.4 * d)
    aRow(thr, der, W, slot.X, slot.Z)
    const landed = L.st ? (1 - sm(Math.abs(thr - (L.st.side ? PI : 0)) / 0.12)) : 0
    const bo = landed > 0 && board ? aBounce(L.st, now) * W : 0
    for (let i = 0; i <= NU; i++) {
      const k = (j * (NU + 1) + i) * 3
      let z = slot.Z[i]
      if (landed > 0) z += board ? bo * i / NU : landed * aRipple(L.st, v, i / NU, now)
      P[k] = slot.X[i]
      P[k + 1] = G.y0 + v * G.H
      P[k + 2] = z
      if (z > zmax) zmax = z
    }
  }
  for (let j = 0; j <= NV; j++) {
    for (let i = 0; i <= NU; i++) {
      const a = (j * (NU + 1) + Math.max(0, i - 1)) * 3
      const b = (j * (NU + 1) + Math.min(NU, i + 1)) * 3
      const c = (Math.max(0, j - 1) * (NU + 1) + i) * 3
      const dd = (Math.min(NV, j + 1) * (NU + 1) + i) * 3
      const ux = P[b] - P[a]
      const uy = P[b + 1] - P[a + 1]
      const uz = P[b + 2] - P[a + 2]
      const vx = P[dd] - P[c]
      const vy = P[dd + 1] - P[c + 1]
      const vz = P[dd + 2] - P[c + 2]
      let nx = uy * vz - uz * vy
      let ny = uz * vx - ux * vz
      let nz = ux * vy - uy * vx
      const nl = Math.hypot(nx, ny, nz) || 1
      const k = (j * (NU + 1) + i) * 3
      N[k] = nx / nl
      N[k + 1] = ny / nl
      N[k + 2] = nz / nl
      const i0 = Math.max(0, i - 2)
      const i1 = Math.min(NU, i + 2)
      const m = (i0 + i1) >> 1
      const p0 = (j * (NU + 1) + i0) * 3
      const pm = (j * (NU + 1) + m) * 3
      const p1 = (j * (NU + 1) + i1) * 3
      const a0 = Math.atan2(P[pm + 2] - P[p0 + 2], P[pm] - P[p0])
      const a1 = Math.atan2(P[p1 + 2] - P[pm + 2], P[p1] - P[pm])
      let da = Math.abs(a1 - a0)
      if (da > PI) da = 2 * PI - da
      F[j * (NU + 1) + i] = Math.exp((-da / (Math.max(1, i1 - i0) / 2 / NU)) * 0.3)
    }
  }
  return zmax
}

function leafLift(U, now, reducedMotion) {
  if (U.mode === 'rest') return 0
  if (U.mode === 'settle') return 1 - sm((now - U.st.t0) / (reducedMotion ? 0.25 : 0.95))
  if (U.mode === 'peek') return clamp(Math.min(U.th, PI - U.th) / 0.06, 0, 1)
  return 1
}

export function leafState(U, now, reducedMotion) {
  return {
    th: U.th,
    de: U.de,
    tw: U.tw,
    vl: U.vl,
    st: U.st,
    lift: leafLift(U, now, reducedMotion),
    amp: 1,
    board: isBoard(U),
    bias: isBoard(U) ? (U.th < PI / 2 ? -1 : 1) : 0
  }
}

function initGL(cv) {
  let gl = null
  try {
    gl = cv.getContext('webgl2', {
      antialias: true,
      alpha: true,
      premultipliedAlpha: true,
      preserveDrawingBuffer: false
    })
  } catch (e) {
    gl = null
  }
  if (!gl) return null

  function sh(t, src) {
    const q = gl.createShader(t)
    gl.shaderSource(q, src)
    gl.compileShader(q)
    if (!gl.getShaderParameter(q, gl.COMPILE_STATUS)) {
      console.warn('leaf shader', gl.getShaderInfoLog(q))
      return null
    }
    return q
  }
  const vs = sh(gl.VERTEX_SHADER, VS)
  const fs = sh(gl.FRAGMENT_SHADER, FS)
  if (!vs || !fs) return null
  const pr = gl.createProgram()
  gl.attachShader(pr, vs)
  gl.attachShader(pr, fs)
  gl.linkProgram(pr)
  if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) return null
  gl.useProgram(pr)

  const nv = (NU + 1) * (NV + 1)
  const UV = new Float32Array(nv * 2)
  const IX = new Uint16Array(NU * NV * 6)
  let k = 0
  for (let j = 0; j <= NV; j++) {
    for (let i = 0; i <= NU; i++, k++) {
      UV[k * 2] = i / NU
      UV[k * 2 + 1] = j / NV
    }
  }
  k = 0
  for (let j = 0; j < NV; j++) {
    for (let i = 0; i < NU; i++) {
      const a = j * (NU + 1) + i
      const b = a + 1
      const c = a + NU + 1
      const d = c + 1
      IX[k++] = a; IX[k++] = c; IX[k++] = b
      IX[k++] = b; IX[k++] = c; IX[k++] = d
    }
  }

  const R = { cv, gl, nidx: IX.length, slots: [], tex: {}, U: {} }
  const locP = gl.getAttribLocation(pr, 'aP')
  const locN = gl.getAttribLocation(pr, 'aN')
  const locUV = gl.getAttribLocation(pr, 'aUV')
  const locF = gl.getAttribLocation(pr, 'aF')
  const bUV = gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, bUV)
  gl.bufferData(gl.ARRAY_BUFFER, UV, gl.STATIC_DRAW)
  const bI = gl.createBuffer()
  gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, bI)
  gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, IX, gl.STATIC_DRAW)

  for (let sl = 0; sl < 4; sl++) {
    const S = {
      P: new Float32Array(nv * 3),
      N: new Float32Array(nv * 3),
      F: new Float32Array(nv),
      X: new Float32Array(NU + 1),
      Z: new Float32Array(NU + 1),
      vao: gl.createVertexArray()
    }
    gl.bindVertexArray(S.vao)
    function att(loc, size, data) {
      const bb = gl.createBuffer()
      gl.bindBuffer(gl.ARRAY_BUFFER, bb)
      gl.bufferData(gl.ARRAY_BUFFER, data, gl.DYNAMIC_DRAW)
      gl.enableVertexAttribArray(loc)
      gl.vertexAttribPointer(loc, size, gl.FLOAT, false, 0, 0)
      return bb
    }
    S.bP = att(locP, 3, S.P)
    S.bN = att(locN, 3, S.N)
    S.bF = att(locF, 1, S.F)
    gl.bindBuffer(gl.ARRAY_BUFFER, bUV)
    gl.enableVertexAttribArray(locUV)
    gl.vertexAttribPointer(locUV, 2, gl.FLOAT, false, 0, 0)
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, bI)
    R.slots.push(S)
  }
  gl.bindVertexArray(null)
  R.ani = gl.getExtension('EXT_texture_filter_anisotropic')
  ;['uFront', 'uBack', 'uUnderR', 'uUnderL'].forEach((n, u) => {
    gl.uniform1i(gl.getUniformLocation(pr, n), u)
  })
  ;['uCam', 'uView', 'uHC', 'uHCY', 'uEye', 'uL', 'uLift', 'uTrans', 'uShow', 'uSpec', 'uShin', 'uGlow', 'uEdge', 'uBoard', 'uPx', 'uShadow', 'uShA', 'uWH', 'uClip', 'uHas', 'uBias', 'uLod', 'uCen']
    .forEach((n) => { R.U[n] = gl.getUniformLocation(pr, n) })
  gl.enable(gl.DEPTH_TEST)
  gl.depthFunc(gl.LESS)
  gl.enable(gl.BLEND)
  gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA)
  return R
}

function uploadTex(R, key, img, force) {
  const gl = R.gl
  let T = R.tex[key]
  if (!T) {
    T = R.tex[key] = { t: gl.createTexture(), img: null }
    gl.bindTexture(gl.TEXTURE_2D, T.t)
    if (R.ani) {
      gl.texParameterf(
        gl.TEXTURE_2D,
        R.ani.TEXTURE_MAX_ANISOTROPY_EXT,
        Math.min(16, gl.getParameter(R.ani.MAX_TEXTURE_MAX_ANISOTROPY_EXT))
      )
    }
    // 不用 mipmap：翻页时贴图/胶条更利落，避免被自动降采样抹糊
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
  }
  if (force || T.img !== img) {
    gl.bindTexture(gl.TEXTURE_2D, T.t)
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img)
    T.img = img
  } else {
    gl.bindTexture(gl.TEXTURE_2D, T.t)
  }
  return T.t
}

let stripFB = null
let stripLayer = null

function drawStrips(g, cam, front, back, G, L, clip, now) {
  if (!stripFB) {
    stripFB = {
      P: new Float32Array((NU + 1) * (NV + 1) * 3),
      N: new Float32Array((NU + 1) * (NV + 1) * 3),
      F: new Float32Array((NU + 1) * (NV + 1)),
      X: new Float32Array(NU + 1),
      Z: new Float32Array(NU + 1)
    }
  }
  const zmax = aMesh(stripFB, G, L, now)
  const P = stripFB.P
  const j = NV >> 1
  const sx = front.width / G.W
  const W = g.canvas.width
  const H = g.canvas.height
  if (!stripLayer || stripLayer.width !== W || stripLayer.height !== H) stripLayer = makeCanvas(W, H)
  const lc = stripLayer.getContext('2d')
  lc.setTransform(1, 0, 0, 1, 0, 0)
  lc.clearRect(0, 0, W, H)
  lc.setTransform(g.getTransform())

  // shadow
  if (zmax >= 0.6) {
    const Ls = [-0.16, -0.2]
    const pts = []
    const at = (i, jj) => {
      const kk = (jj * (NU + 1) + i) * 3
      const z = P[kk + 2]
      return [P[kk] - Ls[0] * z, P[kk + 1] - Ls[1] * z]
    }
    for (let i = 0; i <= NU; i += 2) pts.push(at(i, 0))
    for (let jj = 0; jj <= NV; jj++) pts.push(at(NU, jj))
    for (let i = NU; i >= 0; i -= 2) pts.push(at(i, NV))
    const hi = clamp(zmax / G.W, 0, 1)
    const a = L.lift * (0.3 - 0.12 * hi)
    const bl = (2 + zmax * 0.035) * cam.k
    g.save()
    g.beginPath()
    g.rect(clip[0], G.y0, clip[1] - clip[0], G.H)
    g.clip()
    g.filter = `blur(${bl.toFixed(1)}px)`
    g.fillStyle = `rgba(84,62,36,${a.toFixed(3)})`
    g.beginPath()
    pts.forEach((q, n) => { if (n) g.lineTo(q[0], q[1]); else g.moveTo(q[0], q[1]) })
    g.closePath()
    g.fill()
    g.restore()
  }

  const order = []
  for (let i = 0; i < NU; i++) order.push(i)
  order.sort((a, b) => P[(j * (NU + 1) + a) * 3 + 2] - P[(j * (NU + 1) + b) * 3 + 2])
  const px = (x, z) => cam.x + (x - cam.x) * HC / (HC - z)
  const stops = []
  const lx = -0.32
  const lz = 0.95
  order.forEach((i) => {
    const a = (j * (NU + 1) + i) * 3
    const b = a + 3
    const x0 = px(P[a], P[a + 2])
    const x1 = px(P[b], P[b + 2])
    const dx = P[b] - P[a]
    const dz = P[b + 2] - P[a + 2]
    const facing = dx >= 0
    const src = facing ? front : back
    const u = facing ? (i / NU) * G.W : G.W - ((i + 1) / NU) * G.W
    const L0 = Math.min(x0, x1)
    const w = Math.abs(x1 - x0) + 0.85
    if (w < 0.05) return
    lc.drawImage(src, u * sx, 0, (G.W / NU) * sx, src.height, L0 - 0.4, G.y0, w, G.H)
    const len = Math.hypot(dx, dz) || 1
    const nx = facing ? -dz / len : dz / len
    const nz = Math.abs(dx) / len
    const lam = Math.max(0, nx * lx + nz * lz)
    stops.push([(x0 + x1) / 2, (1 - lam) * 0.3 * L.lift])
  })
  stops.sort((a, b) => a[0] - b[0])
  if (stops.length > 1 && stops[stops.length - 1][0] - stops[0][0] > 0.5) {
    const X0 = stops[0][0]
    const X1 = stops[stops.length - 1][0]
    const gr = lc.createLinearGradient(X0, 0, X1, 0)
    let last = -1
    stops.forEach((q) => {
      const t = (q[0] - X0) / (X1 - X0)
      if (t <= last) return
      last = t
      gr.addColorStop(t, `rgba(70,54,32,${Math.min(0.4, q[1]).toFixed(3)})`)
    })
    lc.globalCompositeOperation = 'source-atop'
    lc.fillStyle = gr
    lc.fillRect(X0 - 40, G.y0 - 60, X1 - X0 + 80, G.H + 120)
    lc.globalCompositeOperation = 'source-over'
  }
  g.save()
  g.setTransform(1, 0, 0, 1, 0, 0)
  g.drawImage(stripLayer, 0, 0)
  g.restore()
}

function drawGLFrame(R, items, cam, W, H, now, faces, under, dpr) {
  const gl = R.gl
  const U = R.U
  if (R.cv.width !== W || R.cv.height !== H) {
    R.cv.width = W
    R.cv.height = H
  }
  gl.viewport(0, 0, W, H)
  gl.clearColor(0, 0, 0, 0)
  gl.clearDepth(1)
  gl.depthMask(true)
  gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)
  if (!items.length) return

  gl.uniform3f(U.uCam, cam.x, cam.y, cam.k)
  gl.uniform2f(U.uView, W, H)
  gl.uniform2f(U.uCen, cam.cx, cam.cy)
  gl.uniform1f(U.uHC, HC)
  gl.uniform1f(U.uHCY, HCY)
  gl.uniform3f(U.uEye, cam.x, cam.y, HC)
  gl.uniform3f(U.uL, LAMP[0], LAMP[1], LAMP[2])
  gl.uniform1f(U.uPx, Math.max(1, dpr * 0.75))
  // LOD bias 会让翻页中贴图/胶条发糊；固定 0 保持锐利采样
  gl.uniform1f(U.uLod, 0)

  let hasR = 0
  let hasL = 0
  if (under.r) {
    gl.activeTexture(gl.TEXTURE2)
    gl.bindTexture(gl.TEXTURE_2D, uploadTex(R, under.r.key, under.r.img))
    hasR = 1
  }
  if (under.l) {
    gl.activeTexture(gl.TEXTURE3)
    gl.bindTexture(gl.TEXTURE_2D, uploadTex(R, under.l.key, under.l.img))
    hasL = 1
  }
  gl.uniform2f(U.uHas, hasR, hasL)

  items.forEach((it, n) => {
    it.slot = R.slots[n]
    it.zmax = aMesh(it.slot, it.G, it.s, now)
    const S = it.slot
    gl.bindBuffer(gl.ARRAY_BUFFER, S.bP)
    gl.bufferSubData(gl.ARRAY_BUFFER, 0, S.P)
    gl.bindBuffer(gl.ARRAY_BUFFER, S.bN)
    gl.bufferSubData(gl.ARRAY_BUFFER, 0, S.N)
    gl.bindBuffer(gl.ARRAY_BUFFER, S.bF)
    gl.bufferSubData(gl.ARRAY_BUFFER, 0, S.F)
  })

  gl.uniform1f(U.uShadow, 1)
  gl.uniform1f(U.uBias, 0)
  items.forEach((it) => {
    if (it.zmax < 0.6) return
    gl.uniform2f(U.uWH, it.G.W, it.G.H)
    gl.uniform4f(U.uClip, it.clip[0], it.clip[1], it.G.y0, it.G.y0 + it.G.H)
    gl.uniform1f(U.uShA, it.s.lift * (it.s.board ? 0.26 : 0.3))
    gl.bindVertexArray(it.slot.vao)
    gl.drawElements(gl.TRIANGLES, R.nidx, gl.UNSIGNED_SHORT, 0)
  })
  gl.uniform1f(U.uShadow, 0)
  items.forEach((it) => {
    const L = it.s
    const b = !!L.board
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, uploadTex(R, it.fKey, faces[it.fKey], it.fresh))
    gl.activeTexture(gl.TEXTURE1)
    gl.bindTexture(gl.TEXTURE_2D, uploadTex(R, it.bKey, faces[it.bKey], it.fresh))
    gl.uniform2f(U.uWH, it.G.W, it.G.H)
    gl.uniform1f(U.uLift, L.lift)
    gl.uniform1f(U.uTrans, b ? 0 : 0.11 * L.amp)
    gl.uniform1f(U.uShow, b ? 0 : 0.08 * L.amp)
    gl.uniform1f(U.uSpec, b ? 0.1 : 0.24)
    gl.uniform1f(U.uShin, 34)
    gl.uniform1f(U.uGlow, b ? 0 : 0.3 * L.amp)
    gl.uniform1f(U.uEdge, 1)
    gl.uniform1f(U.uBoard, b ? 0.8 : 0)
    gl.uniform1f(U.uBias, L.bias || 0)
    gl.bindVertexArray(it.slot.vao)
    gl.drawElements(gl.TRIANGLES, R.nidx, gl.UNSIGNED_SHORT, 0)
  })
  gl.bindVertexArray(null)
}

/**
 * Create engine bound to book + leaf canvases.
 */
export function createEngine({ bookCanvas, leafCanvas }) {
  const glR = initGL(leafCanvas)
  const cams = { x: 0, y: 0, k: 1, cx: 0, cy: 0, vx: 0, vk: 0, vy: 0, vcx: 0, vcy: 0, init: false }
  let lastNow = 0
  return {
    useGL: !!glR,
    glR,
    cams,
    /** pointer canvas → book space */
    toBook(sx, sy, pageW, pageH) {
      const VW = bookCanvas.width
      const VH = bookCanvas.height
      return {
        x: cams.x + (sx - cams.cx) / Math.max(1e-6, cams.k),
        y: cams.y + (sy - cams.cy) / Math.max(1e-6, cams.k),
        W: pageW,
        H: pageH,
        VW,
        VH
      }
    },
    /** book space → canvas pixel */
    fromBook(bx, by) {
      return {
        x: cams.cx + (bx - cams.x) * cams.k,
        y: cams.cy + (by - cams.y) * cams.k
      }
    },
    /** 强制下一帧重新上传某张 face 纹理（同 canvas 原地重绘后调用） */
    invalidateFace(key) {
      if (glR && key && glR.tex[key]) glR.tex[key].img = null
    },
    /** 左右页在 canvas 上的像素矩形（用于 HTML 覆盖层对齐） */
    pageRects(pageW, pageH) {
      const tl = this.fromBook(-pageW, 0)
      const br = this.fromBook(pageW, pageH)
      const mid = this.fromBook(0, 0)
      const midB = this.fromBook(0, pageH)
      return {
        left: {
          x: tl.x,
          y: tl.y,
          w: mid.x - tl.x,
          h: midB.y - tl.y
        },
        right: {
          x: mid.x,
          y: mid.y,
          w: br.x - mid.x,
          h: br.y - mid.y
        }
      }
    },
    render({
      units,
      faces,
      pageW,
      pageH,
      now,
      reducedMotion,
      dpr
    }) {
      const g = bookCanvas.getContext('2d')
      const VW = bookCanvas.width
      const VH = bookCanvas.height
      const W = pageW
      const H = pageH
      const last = units.length - 1
      const dt = Math.min(0.033, lastNow ? now - lastNow : 0.016)
      lastNow = now

      // match reference aCam: closed cover → look at mid-cover; open → spine
      const th0 = units[0] ? units[0].th : 0
      const thL = units[last] ? units[last].th : 0
      const u0 = sm(th0 / PI)
      const u4 = sm(thL / PI)
      // canvas is larger than the page spread — pad keeps fore-edge + foot shadow on-screen
      const coverOpen = u0
      const target = {
        x: (W / 2) * (1 - u0) - (W / 2) * u4,
        y: H / 2,
        k: 1,
        cx: VW / 2,
        cy: H / 2 + Math.max(0, (VH - H) * 0.2)
      }
      if (!cams.init) {
        Object.assign(cams, target, { vx: 0, vk: 0, vy: 0, vcx: 0, vcy: 0, init: true })
      } else {
        let q = springS(cams.x, cams.vx, target.x, 9, 1, dt)
        cams.x = q[0]; cams.vx = q[1]
        q = springS(cams.k, cams.vk, target.k, 9, 1, dt)
        cams.k = q[0]; cams.vk = q[1]
        q = springS(cams.y, cams.vy, target.y, 9, 1, dt)
        cams.y = q[0]; cams.vy = q[1]
        q = springS(cams.cx, cams.vcx, target.cx, 14, 1, dt)
        cams.cx = q[0]; cams.vcx = q[1]
        q = springS(cams.cy, cams.vcy, target.cy, 14, 1, dt)
        cams.cy = q[0]; cams.vcy = q[1]
      }
      const cam = { x: cams.x, y: cams.y, k: cams.k, cx: cams.cx, cy: cams.cy }

      g.setTransform(1, 0, 0, 1, 0, 0)
      g.clearRect(0, 0, VW, VH)
      g.setTransform(cam.k, 0, 0, cam.k, cam.cx - cam.x * cam.k, cam.cy - cam.y * cam.k)

      // 仅 rest 画进静止书页。settle 仍走 leaf，避免与 book-canvas 叠出重影
      const liesFlat = (U, side) =>
        U.mode === 'rest' &&
        (side === 0 ? U.th < 0.02 : U.th > PI - 0.02)

      let restR = null
      let restL = null
      for (let i = 0; i <= last; i++) {
        if (liesFlat(units[i], 0)) { restR = i; break }
      }
      for (let i = last; i >= 0; i--) {
        if (liesFlat(units[i], 1)) { restL = i; break }
      }

      const under = { r: null, l: null }
      // count sheets still on each side by angle (incl. the one mid-turn)
      let nR = 0
      let nL = 0
      for (let i = 1; i < last; i++) {
        if (units[i].th < PI * 0.5) nR++
        else nL++
      }

      // desk shadow: keep while that half is down (settle counts as down)
      const UL = units[last]
      const U0 = units[0]
      const rightDown = UL.th < PI * 0.12
      const leftDown = U0.th > PI * 0.88
      const rA = rightDown ? clamp(1 - UL.th / (PI * 0.12), 0, 1) : 0
      const lA = leftDown ? clamp((U0.th - PI * 0.88) / (PI * 0.12), 0, 1) : 0

      // painted soft shadow (no canvas shadowBlur — that gets clipped by overflow)
      const shadowHalf = (x0, x1, al) => {
        if (al <= 0.04 || x1 <= x0) return
        const bw = x1 - x0
        g.save()
        g.globalAlpha = 0.55 * al
        const rg = g.createRadialGradient(
          (x0 + x1) / 2, H * 0.92, bw * 0.08,
          (x0 + x1) / 2, H * 1.02, bw * 0.62
        )
        rg.addColorStop(0, 'rgba(70,56,36,0.38)')
        rg.addColorStop(0.45, 'rgba(70,56,36,0.16)')
        rg.addColorStop(1, 'rgba(70,56,36,0)')
        g.fillStyle = rg
        g.fillRect(x0 - bw * 0.06, H * 0.72, bw * 1.12, H * 0.45)
        // close contact shadow under the foot
        g.globalAlpha = 0.4 * al
        const near = g.createLinearGradient(0, H - 4, 0, H + 18)
        near.addColorStop(0, 'rgba(70,56,36,0.28)')
        near.addColorStop(1, 'rgba(70,56,36,0)')
        g.fillStyle = near
        g.fillRect(x0 + 4, H - 2, bw - 8, 22)
        g.restore()
      }
      shadowHalf(0, W, Math.max(rA, coverOpen > 0.5 && rightDown ? 0.85 : rA))
      shadowHalf(-W, 0, Math.max(lA, coverOpen > 0.5 && leftDown ? 0.85 : lA))
      g.globalAlpha = 1

      /*
        Realistic page-block edge: sheets don't align flush.
        Binding thickness + open fan → each lower leaf peeks out a step
        at the fore-edge and foot (staircase of cream paper + dark seams).
      */
      const sheetThick = Math.max(1.15, W / 420)
      const stepOut = Math.max(1.05, W / 480)
      const drawPageBlock = (side, n) => {
        const layers = Math.max(2, Math.min(8, n + 1))
        g.save()
        // deepest sheet first
        for (let i = layers; i >= 1; i--) {
          // slight irregular stagger (deterministic) — real paper never lines up perfectly
          const wobble = ((i * 37) % 5) * 0.12 * sheetThick
          const out = i * stepOut + wobble
          const drop = i * sheetThick * 0.55
          const lift = i * sheetThick * 0.35
          // cream face of the sheet edge
          const shade = 0.92 - i * 0.03
          g.fillStyle = `rgb(${Math.round(251 * shade)},${Math.round(247 * shade)},${Math.round(239 * shade)})`
          if (side > 0) {
            // fore-edge strip peeking past the top page
            g.fillRect(W - sheetThick * 0.3, -lift, out + sheetThick, H + lift + drop)
            // foot of the block
            g.fillRect(-out * 0.15, H - sheetThick * 0.2, W + out * 1.05, drop + sheetThick)
          } else {
            g.fillRect(-W - out, -lift, out + sheetThick, H + lift + drop)
            g.fillRect(-W - out * 0.9, H - sheetThick * 0.2, W + out * 1.05, drop + sheetThick)
          }
          // seam between sheets (the "layer line")
          g.fillStyle = `rgba(120,95,60,${(0.22 + i * 0.02).toFixed(3)})`
          if (side > 0) {
            g.fillRect(W + out - 0.55, -lift * 0.5, 0.7, H + lift * 0.5 + drop)
          } else {
            g.fillRect(-W - out - 0.15, -lift * 0.5, 0.7, H + lift * 0.5 + drop)
          }
        }
        // outermost paper lip (catch light)
        const outer = layers * stepOut + sheetThick
        g.fillStyle = 'rgba(255,252,244,0.65)'
        if (side > 0) g.fillRect(W + outer - 0.4, 1, 0.9, H - 2)
        else g.fillRect(-W - outer - 0.5, 1, 0.9, H - 2)
        g.restore()
      }

      // 订口重叠 2px，盖住对开笔迹接缝，避免红色框那种硬竖线
      const seam = 2
      if (restR != null) {
        const fr = faces[units[restR].frontKey]
        if (restR === 0) g.drawImage(fr, -seam, 0, W + seam, H)
        else if (restR === last) g.drawImage(fr, -seam, 0, W + seam, H)
        else {
          g.drawImage(faces.coverBI, 0, 0, W, H)
          if (nR > 0) drawPageBlock(1, nR)
          g.drawImage(fr, -seam, 0, W + seam, H)
          under.r = { key: units[restR].frontKey, img: fr }
        }
      }
      if (restL != null) {
        if (restL === last) g.drawImage(faces[units[restL].backKey], -W, 0, W + seam, H)
        else {
          g.drawImage(faces.coverFI, -W, 0, W, H)
          if (restL > 0) {
            if (nL > 0) drawPageBlock(-1, nL)
            const bk = faces[units[restL].backKey]
            g.drawImage(bk, -W, 0, W + seam, H)
            under.l = { key: units[restL].backKey, img: bk }
          }
        }
      }

      // while a leaf is turning, the top sheet no longer covers the block —
      // redraw the proud steps so the cascade stays readable
      const turning = units.some(u => u.mode !== 'rest' && u.mode !== 'settle' && !isBoard(u))
      if (turning) {
        if (nR > 1) drawPageBlock(1, nR - 1)
        if (nL > 0) drawPageBlock(-1, nL)
      }

      // 订口谷影：柔和阴影表达深度，避免实线竖缝
      if (restL != null && restL < last && restR != null && restR > 0) {
        const gr = g.createLinearGradient(0, 0, 28, 0)
        gr.addColorStop(0, 'rgba(90,70,40,0.055)')
        gr.addColorStop(0.45, 'rgba(90,70,40,0.02)')
        gr.addColorStop(1, 'rgba(90,70,40,0)')
        g.fillStyle = gr
        g.fillRect(0, 0, 28, H)
        const gl2 = g.createLinearGradient(0, 0, -28, 0)
        gl2.addColorStop(0, 'rgba(90,70,40,0.055)')
        gl2.addColorStop(0.45, 'rgba(90,70,40,0.02)')
        gl2.addColorStop(1, 'rgba(90,70,40,0)')
        g.fillStyle = gl2
        g.fillRect(-28, 0, 28, H)
      }

      const moving = units.filter(u => u.mode !== 'rest')
      const items = moving.map((U) => {
        const idx = units.indexOf(U)
        const board = isBoard(U)
        return {
          s: leafState(U, now, reducedMotion),
          G: { W, y0: 0, H, board },
          fKey: U.frontKey,
          bKey: U.backKey,
          clip: idx === 0 ? [0, W] : (idx === last ? [-W, 0] : [-W, W]),
          fresh: U.fresh
        }
      })
      moving.forEach(U => { U.fresh = false })

      // warm light through standing leaf
      items.forEach((it) => {
        if (it.s.board) return
        const st = Math.sin(clamp(it.s.th, 0, PI)) * it.s.lift
        const side = it.s.th < PI / 2 ? 1 : -1
        const w = 150
        if (st < 0.08) return
        const gr = g.createLinearGradient(0, 0, side * w, 0)
        gr.addColorStop(0, `rgba(255,222,170,${(0.12 * st).toFixed(3)})`)
        gr.addColorStop(1, 'rgba(255,222,170,0)')
        g.fillStyle = gr
        g.fillRect(Math.min(0, side * w), 0, w, H)
      })

      items.sort((a, b) => Math.abs(b.s.th - PI / 2) - Math.abs(a.s.th - PI / 2))

      if (glR) {
        drawGLFrame(glR, items, cam, VW, VH, now, faces, under, dpr)
      } else {
        const l2 = leafCanvas.getContext('2d')
        l2.setTransform(1, 0, 0, 1, 0, 0)
        l2.clearRect(0, 0, VW, VH)
        l2.setTransform(cam.k, 0, 0, cam.k, cam.cx - cam.x * cam.k, cam.cy - cam.y * cam.k)
        items.forEach((it) => {
          drawStrips(l2, cam, faces[it.fKey], faces[it.bKey], it.G, it.s, it.clip, now)
        })
      }

      return { cam, coverOpen }
    }
  }
}
