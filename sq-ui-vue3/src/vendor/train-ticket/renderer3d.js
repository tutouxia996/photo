/*
 * Train Ticket Generator 中国铁路火车票生成器.
 * Copyright (C) 2024-present FoskyM<i@fosky.top>
 * https://github.com/FoskyM/train-ticket-generator
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ported for diary TicketInspect (AGPL-3.0).
 */

import * as THREE from 'three'
import {
  canvasWidth,
  canvasHeight,
  protrusionHeight,
  protrusionWidth
} from './renderer2d.js'

export const createInitialState = () => ({
  renderer: null,
  scene: null,
  camera: null,
  ticketGroup: null,
  frontTexture: null,
  backTexture: null,
  animationFrameId: null,
  isDragging: false,
  previousMousePosition: { x: 0, y: 0 },
  targetRotation: { x: -0.2, y: 0.4 },
  initialized: false
})

export const defaultConfig = {
  autoRotate: false,
  rotationSpeed: 0.5,
  zoom: 1,
  showProtrusions: true
}

export const ZOOM_MIN = 0.55
export const ZOOM_MAX = 1.85
export const ZOOM_STEP = 0.08
/** 票面宽约 3.5；拉远一点，放大后仍能整票入画（含轻微侧转） */
export const CAMERA_BASE_Z = 6.4

export const initThreeJS = (container, frontCanvas, backCanvas, state, config) => {
  if (state.initialized) return false

  const width = container.clientWidth
  const height = container.clientHeight
  if (width === 0 || height === 0) return false

  state.initialized = true

  state.scene = new THREE.Scene()

  state.camera = new THREE.PerspectiveCamera(42, width / height, 0.05, 1000)
  updateCameraZoom(state, config.zoom)

  state.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  state.renderer.setSize(width, height)
  state.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  state.renderer.setClearColor(0x000000, 0)
  container.appendChild(state.renderer.domElement)

  createTicketMesh(state, frontCanvas, backCanvas, config)

  const ambientLight = new THREE.AmbientLight(0xffffff, 1)
  state.scene.add(ambientLight)

  return true
}

export const updateCameraZoom = (state, zoom) => {
  if (state.camera) {
    const z = CAMERA_BASE_Z / Math.max(0.01, zoom)
    state.camera.position.z = z
    state.camera.updateProjectionMatrix()
  }
}

const createTicketMesh = (state, frontCanvas, backCanvas, config) => {
  if (!state.scene) return

  // 与官方一致：宽 3.5，厚 0.025 ≈ 真实纸票比例，并带圆角挤出侧边
  const ticketWidth3D = 3.5
  const ticketHeight3D = ticketWidth3D * (canvasHeight / canvasWidth)
  const ticketDepth = 0.025

  const scaleX = ticketWidth3D / canvasWidth
  const scaleY = ticketHeight3D / canvasHeight

  const rectX = 20
  const rectY = 10
  const rectW = canvasWidth - 40
  const rectH = canvasHeight - 20
  const radius = 20
  const trapHeight = protrusionHeight
  const trapWidth = protrusionWidth
  const trapOffset = 5

  const trapY1 = canvasHeight * 0.2
  const trapY2 = canvasHeight * 0.8

  const shape = new THREE.Shape()
  const toShapeX = (x) => (x - canvasWidth / 2) * scaleX
  const toShapeY = (y) => (canvasHeight / 2 - y) * scaleY

  const showProtrusions = config.showProtrusions !== false
  const left = rectX
  const right = rectX + rectW
  const top = rectY
  const bottom = rectY + rectH

  shape.moveTo(toShapeX(left + radius), toShapeY(top))
  shape.lineTo(toShapeX(right - radius), toShapeY(top))
  shape.quadraticCurveTo(toShapeX(right), toShapeY(top), toShapeX(right), toShapeY(top + radius))

  if (showProtrusions) {
    shape.lineTo(toShapeX(right), toShapeY(trapY1 - trapHeight / 2))
    shape.lineTo(toShapeX(right + trapWidth), toShapeY(trapY1 - trapHeight / 2 + trapOffset))
    shape.lineTo(toShapeX(right + trapWidth), toShapeY(trapY1 + trapHeight / 2 - trapOffset))
    shape.lineTo(toShapeX(right), toShapeY(trapY1 + trapHeight / 2))
    shape.lineTo(toShapeX(right), toShapeY(trapY2 - trapHeight / 2))
    shape.lineTo(toShapeX(right + trapWidth), toShapeY(trapY2 - trapHeight / 2 + trapOffset))
    shape.lineTo(toShapeX(right + trapWidth), toShapeY(trapY2 + trapHeight / 2 - trapOffset))
    shape.lineTo(toShapeX(right), toShapeY(trapY2 + trapHeight / 2))
  }

  shape.lineTo(toShapeX(right), toShapeY(bottom - radius))
  shape.quadraticCurveTo(
    toShapeX(right),
    toShapeY(bottom),
    toShapeX(right - radius),
    toShapeY(bottom)
  )

  shape.lineTo(toShapeX(left + radius), toShapeY(bottom))
  shape.quadraticCurveTo(
    toShapeX(left),
    toShapeY(bottom),
    toShapeX(left),
    toShapeY(bottom - radius)
  )

  if (showProtrusions) {
    shape.lineTo(toShapeX(left), toShapeY(trapY2 + trapHeight / 2))
    shape.lineTo(toShapeX(left - trapWidth), toShapeY(trapY2 + trapHeight / 2 - trapOffset))
    shape.lineTo(toShapeX(left - trapWidth), toShapeY(trapY2 - trapHeight / 2 + trapOffset))
    shape.lineTo(toShapeX(left), toShapeY(trapY2 - trapHeight / 2))
    shape.lineTo(toShapeX(left), toShapeY(trapY1 + trapHeight / 2))
    shape.lineTo(toShapeX(left - trapWidth), toShapeY(trapY1 + trapHeight / 2 - trapOffset))
    shape.lineTo(toShapeX(left - trapWidth), toShapeY(trapY1 - trapHeight / 2 + trapOffset))
    shape.lineTo(toShapeX(left), toShapeY(trapY1 - trapHeight / 2))
  }

  shape.lineTo(toShapeX(left), toShapeY(top + radius))
  shape.quadraticCurveTo(toShapeX(left), toShapeY(top), toShapeX(left + radius), toShapeY(top))

  state.frontTexture = new THREE.CanvasTexture(frontCanvas)
  state.frontTexture.colorSpace = THREE.SRGBColorSpace
  state.backTexture = new THREE.CanvasTexture(backCanvas)
  state.backTexture.colorSpace = THREE.SRGBColorSpace

  const frontMaterial = new THREE.MeshBasicMaterial({
    map: state.frontTexture,
    transparent: true,
    side: THREE.FrontSide,
    depthWrite: true,
    polygonOffset: true,
    polygonOffsetFactor: -1,
    polygonOffsetUnits: -1
  })

  state.backTexture.wrapS = THREE.RepeatWrapping
  state.backTexture.repeat.x = -1
  state.backTexture.offset.x = 1

  const backMaterial = new THREE.MeshBasicMaterial({
    map: state.backTexture,
    transparent: true,
    side: THREE.FrontSide,
    depthWrite: true,
    polygonOffset: true,
    polygonOffsetFactor: -1,
    polygonOffsetUnits: -1
  })

  const edgeMaterial = new THREE.MeshBasicMaterial({
    color: 0xadd8e6,
    transparent: true,
    opacity: 0.9,
    side: THREE.DoubleSide
  })

  state.ticketGroup = new THREE.Group()

  const calculateUV = (positions, uvs, flipX = false) => {
    for (let i = 0; i < positions.length / 3; i++) {
      const x = positions[i * 3]
      const y = positions[i * 3 + 1]
      const canvasX = x / scaleX + canvasWidth / 2
      const canvasY = canvasHeight / 2 - y / scaleY
      uvs[i * 2] = flipX ? 1 - canvasX / canvasWidth : canvasX / canvasWidth
      uvs[i * 2 + 1] = 1 - canvasY / canvasHeight
    }
  }

  const frontGeometry = new THREE.ShapeGeometry(shape)
  const frontPositions = frontGeometry.getAttribute('position').array
  const frontUvs = frontGeometry.getAttribute('uv').array
  calculateUV(frontPositions, frontUvs)
  frontGeometry.getAttribute('uv').needsUpdate = true
  const frontMesh = new THREE.Mesh(frontGeometry, frontMaterial)
  frontMesh.position.z = ticketDepth / 2
  state.ticketGroup.add(frontMesh)

  const backGeometry = new THREE.ShapeGeometry(shape)
  const backPositions = backGeometry.getAttribute('position').array
  const backUvs = backGeometry.getAttribute('uv').array
  calculateUV(backPositions, backUvs, true)
  backGeometry.getAttribute('uv').needsUpdate = true
  const backMesh = new THREE.Mesh(backGeometry, backMaterial)
  backMesh.position.z = -ticketDepth / 2
  backMesh.rotation.y = Math.PI
  state.ticketGroup.add(backMesh)

  const edgeGeometry = new THREE.ExtrudeGeometry(shape, {
    depth: ticketDepth,
    bevelEnabled: false,
    curveSegments: 12
  })
  edgeGeometry.translate(0, 0, -ticketDepth / 2)

  const edgeNormals = edgeGeometry.getAttribute('normal').array
  const edgeIndex = edgeGeometry.index

  if (edgeIndex) {
    const indices = edgeIndex.array
    const newIndices = []

    for (let i = 0; i < indices.length; i += 3) {
      const idx0 = indices[i]
      const idx1 = indices[i + 1]
      const idx2 = indices[i + 2]

      const nz0 = Math.abs(edgeNormals[idx0 * 3 + 2])
      const nz1 = Math.abs(edgeNormals[idx1 * 3 + 2])
      const nz2 = Math.abs(edgeNormals[idx2 * 3 + 2])

      if (nz0 < 0.5 && nz1 < 0.5 && nz2 < 0.5) {
        newIndices.push(idx0, idx1, idx2)
      }
    }

    edgeGeometry.setIndex(newIndices)
  }

  const edgeMesh = new THREE.Mesh(edgeGeometry, edgeMaterial)
  state.ticketGroup.add(edgeMesh)

  state.scene.add(state.ticketGroup)
}

export const updateTextures = (state) => {
  if (state.frontTexture) state.frontTexture.needsUpdate = true
  if (state.backTexture) state.backTexture.needsUpdate = true
}

export const onWindowResize = (container, state) => {
  if (!state.camera || !state.renderer) return
  const width = container.clientWidth
  const height = container.clientHeight
  state.camera.aspect = width / height
  state.camera.updateProjectionMatrix()
  state.renderer.setSize(width, height)
}

export const onMouseDown = (event, state) => {
  state.isDragging = true
  state.previousMousePosition = { x: event.clientX, y: event.clientY }
}

export const onMouseMove = (event, state) => {
  if (!state.isDragging) return
  const deltaX = event.clientX - state.previousMousePosition.x
  const deltaY = event.clientY - state.previousMousePosition.y
  state.targetRotation.y += deltaX * 0.01
  state.targetRotation.x += deltaY * 0.01
  state.targetRotation.x = Math.max(-Math.PI / 3, Math.min(Math.PI / 3, state.targetRotation.x))
  state.previousMousePosition = { x: event.clientX, y: event.clientY }
}

export const onMouseUp = (state) => {
  state.isDragging = false
}

export const onWheel = (event, state, config, updateConfig) => {
  event.preventDefault()
  const delta = event.deltaY > 0 ? -ZOOM_STEP : ZOOM_STEP
  const newZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, config.zoom + delta))
  if (newZoom !== config.zoom) {
    updateConfig({ zoom: newZoom })
    updateCameraZoom(state, newZoom)
  }
}

export const resetRotation = (state) => {
  state.targetRotation = { x: -0.2, y: 0.4 }
}

export const animate = (state, config) => {
  state.animationFrameId = requestAnimationFrame(() => animate(state, config))

  if (state.ticketGroup) {
    if (config.autoRotate && !state.isDragging) {
      state.targetRotation.y += 0.005 * config.rotationSpeed
    }
    state.ticketGroup.rotation.x += (state.targetRotation.x - state.ticketGroup.rotation.x) * 0.1
    state.ticketGroup.rotation.y += (state.targetRotation.y - state.ticketGroup.rotation.y) * 0.1
  }

  if (state.renderer && state.scene && state.camera) {
    state.renderer.render(state.scene, state.camera)
  }
}

export const disposeThreeJS = (container, state) => {
  if (state.animationFrameId) {
    cancelAnimationFrame(state.animationFrameId)
    state.animationFrameId = null
  }

  if (state.ticketGroup) {
    state.ticketGroup.traverse((child) => {
      if (child.isMesh) {
        child.geometry.dispose()
        if (Array.isArray(child.material)) {
          child.material.forEach((m) => m.dispose())
        } else {
          child.material.dispose()
        }
      }
    })
  }

  state.frontTexture?.dispose()
  state.backTexture?.dispose()

  if (state.renderer) {
    if (container && state.renderer.domElement?.parentNode === container) {
      container.removeChild(state.renderer.domElement)
    }
    state.renderer.dispose()
  }

  state.renderer = null
  state.scene = null
  state.camera = null
  state.ticketGroup = null
  state.frontTexture = null
  state.backTexture = null
  state.initialized = false
}

export const bindEvents = (container, state, config, updateConfig) => {
  const handleMouseDown = (e) => onMouseDown(e, state)
  const handleMouseMove = (e) => onMouseMove(e, state)
  const handleMouseUp = () => onMouseUp(state)
  const handleWheel = (e) => onWheel(e, state, config, updateConfig)
  const handleResize = () => onWindowResize(container, state)

  const handlePointerDown = (e) => {
    if (e.button != null && e.button !== 0) return
    state.isDragging = true
    state.previousMousePosition = { x: e.clientX, y: e.clientY }
    container.setPointerCapture?.(e.pointerId)
  }
  const handlePointerMove = (e) => {
    if (!state.isDragging) return
    const deltaX = e.clientX - state.previousMousePosition.x
    const deltaY = e.clientY - state.previousMousePosition.y
    state.targetRotation.y += deltaX * 0.01
    state.targetRotation.x += deltaY * 0.01
    state.targetRotation.x = Math.max(-Math.PI / 3, Math.min(Math.PI / 3, state.targetRotation.x))
    state.previousMousePosition = { x: e.clientX, y: e.clientY }
  }
  const handlePointerUp = () => onMouseUp(state)

  container.addEventListener('pointerdown', handlePointerDown)
  container.addEventListener('pointermove', handlePointerMove)
  container.addEventListener('pointerup', handlePointerUp)
  container.addEventListener('pointercancel', handlePointerUp)
  container.addEventListener('pointerleave', handlePointerUp)
  container.addEventListener('wheel', handleWheel, { passive: false })
  window.addEventListener('resize', handleResize)

  // 兼容旧鼠标 API（部分环境）
  container.addEventListener('mousedown', handleMouseDown)
  container.addEventListener('mousemove', handleMouseMove)
  container.addEventListener('mouseup', handleMouseUp)

  return () => {
    container.removeEventListener('pointerdown', handlePointerDown)
    container.removeEventListener('pointermove', handlePointerMove)
    container.removeEventListener('pointerup', handlePointerUp)
    container.removeEventListener('pointercancel', handlePointerUp)
    container.removeEventListener('pointerleave', handlePointerUp)
    container.removeEventListener('wheel', handleWheel)
    window.removeEventListener('resize', handleResize)
    container.removeEventListener('mousedown', handleMouseDown)
    container.removeEventListener('mousemove', handleMouseMove)
    container.removeEventListener('mouseup', handleMouseUp)
  }
}
