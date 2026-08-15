#!/usr/bin/env node

const fs = require('fs')
const path = require('path')
const zlib = require('zlib')
const { PNG } = require('pngjs')

// Four-bead strips are translated by the native compositor. Forward and reverse
// layers use different fixed top offsets, so both animations start at transform 0.
const WIDTH = 160
const HEIGHT = 654
const CENTER_X = WIDTH / 2
const CENTER_Y = 246
const BEAD_STEP = 164
const BASE_DIAMETER = 140
const BEAD_COUNT = 18
const FRAME_GROUPS = [
  // Brown-only transitions share one frame set. Special sets cover every
  // transition where the gold bead is visible in or near the viewport.
  { name: 'common', startIndex: 5 },
  { name: '00', startIndex: 0 },
  { name: '01', startIndex: 1 },
  { name: '16', startIndex: 16 },
  { name: '17', startIndex: 17 }
]

const projectRoot = path.resolve(__dirname, '..')
const lotus = PNG.sync.read(
  fs.readFileSync(path.join(projectRoot, 'src/common/lotus.png'))
)
const outputDir = path.join(projectRoot, 'src/common/bead_frames')

function clamp(value) {
  return Math.max(0, Math.min(255, Math.round(value)))
}

function setPixel(png, x, y, red, green, blue, alpha = 255) {
  if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) return
  const offset = (y * WIDTH + x) * 4
  const sourceAlpha = alpha / 255
  const destinationAlpha = png.data[offset + 3] / 255
  const combinedAlpha = sourceAlpha + destinationAlpha * (1 - sourceAlpha)
  if (combinedAlpha <= 0) return
  png.data[offset] = clamp(
    (red * sourceAlpha + png.data[offset] * destinationAlpha * (1 - sourceAlpha)) /
      combinedAlpha
  )
  png.data[offset + 1] = clamp(
    (green * sourceAlpha + png.data[offset + 1] * destinationAlpha * (1 - sourceAlpha)) /
      combinedAlpha
  )
  png.data[offset + 2] = clamp(
    (blue * sourceAlpha + png.data[offset + 2] * destinationAlpha * (1 - sourceAlpha)) /
      combinedAlpha
  )
  png.data[offset + 3] = clamp(combinedAlpha * 255)
}

function fillBackground(png) {
  for (let offset = 0; offset < png.data.length; offset += 4) {
    png.data[offset] = 8
    png.data[offset + 1] = 8
    png.data[offset + 2] = 8
    png.data[offset + 3] = 255
  }
}

const CRC_TABLE = (() => {
  const table = new Uint32Array(256)
  for (let index = 0; index < 256; index += 1) {
    let value = index
    for (let bit = 0; bit < 8; bit += 1) {
      value = (value & 1) ? (0xedb88320 ^ (value >>> 1)) : (value >>> 1)
    }
    table[index] = value >>> 0
  }
  return table
})()

function crc32(buffer) {
  let crc = 0xffffffff
  for (const value of buffer) {
    crc = CRC_TABLE[(crc ^ value) & 0xff] ^ (crc >>> 8)
  }
  return (crc ^ 0xffffffff) >>> 0
}

function pngChunk(type, data) {
  const typeBuffer = Buffer.from(type, 'ascii')
  const length = Buffer.alloc(4)
  length.writeUInt32BE(data.length, 0)
  const checksum = Buffer.alloc(4)
  checksum.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])), 0)
  return Buffer.concat([length, typeBuffer, data, checksum])
}

function boxStats(colors) {
  let minRed = 255
  let minGreen = 255
  let minBlue = 255
  let maxRed = 0
  let maxGreen = 0
  let maxBlue = 0
  let population = 0
  for (const color of colors) {
    minRed = Math.min(minRed, color.red)
    minGreen = Math.min(minGreen, color.green)
    minBlue = Math.min(minBlue, color.blue)
    maxRed = Math.max(maxRed, color.red)
    maxGreen = Math.max(maxGreen, color.green)
    maxBlue = Math.max(maxBlue, color.blue)
    population += color.count
  }
  const ranges = [maxRed - minRed, maxGreen - minGreen, maxBlue - minBlue]
  return { ranges, population, score: Math.max(...ranges) * Math.sqrt(population) }
}

function buildPalette(colors, maximumColors = 256) {
  const boxes = [{ colors, ...boxStats(colors) }]
  while (boxes.length < maximumColors) {
    let splitIndex = -1
    let bestScore = -1
    for (let index = 0; index < boxes.length; index += 1) {
      if (boxes[index].colors.length > 1 && boxes[index].score > bestScore) {
        splitIndex = index
        bestScore = boxes[index].score
      }
    }
    if (splitIndex < 0) break
    const box = boxes.splice(splitIndex, 1)[0]
    const channel = box.ranges.indexOf(Math.max(...box.ranges))
    const channelName = ['red', 'green', 'blue'][channel]
    box.colors.sort((left, right) => left[channelName] - right[channelName])
    const halfway = box.population / 2
    let accumulated = 0
    let cut = 1
    for (; cut < box.colors.length; cut += 1) {
      accumulated += box.colors[cut - 1].count
      if (accumulated >= halfway) break
    }
    const first = box.colors.slice(0, cut)
    const second = box.colors.slice(cut)
    boxes.push({ colors: first, ...boxStats(first) })
    boxes.push({ colors: second, ...boxStats(second) })
  }
  return boxes.map((box) => {
    let red = 0
    let green = 0
    let blue = 0
    let total = 0
    for (const color of box.colors) {
      red += color.red * color.count
      green += color.green * color.count
      blue += color.blue * color.count
      total += color.count
    }
    return [clamp(red / total), clamp(green / total), clamp(blue / total)]
  })
}

function colorsForPngs(pngs) {
  const histogram = new Map()
  for (const png of pngs) {
    for (let offset = 0; offset < png.data.length; offset += 4) {
      const key = (png.data[offset] << 16) |
        (png.data[offset + 1] << 8) |
        png.data[offset + 2]
      histogram.set(key, (histogram.get(key) || 0) + 1)
    }
  }
  const colors = []
  for (const [key, count] of histogram) {
    colors.push({
      key,
      count,
      red: (key >>> 16) & 0xff,
      green: (key >>> 8) & 0xff,
      blue: key & 0xff
    })
  }
  return colors
}

function encodePng8(png, sharedPalette) {
  const colors = colorsForPngs([png])
  const palette = sharedPalette || buildPalette(colors)
  const paletteData = Buffer.from(palette.flat())
  const colorIndices = new Map()
  for (const color of colors) {
    let nearestIndex = 0
    let nearestDistance = Infinity
    for (let index = 0; index < palette.length; index += 1) {
      const deltaRed = color.red - palette[index][0]
      const deltaGreen = color.green - palette[index][1]
      const deltaBlue = color.blue - palette[index][2]
      const distance = deltaRed * deltaRed + deltaGreen * deltaGreen + deltaBlue * deltaBlue
      if (distance < nearestDistance) {
        nearestDistance = distance
        nearestIndex = index
      }
    }
    colorIndices.set(color.key, nearestIndex)
  }
  const scanlines = Buffer.alloc((png.width + 1) * png.height)
  for (let y = 0; y < png.height; y += 1) {
    const scanlineOffset = y * (png.width + 1)
    scanlines[scanlineOffset] = 0
    for (let x = 0; x < png.width; x += 1) {
      const sourceOffset = (y * png.width + x) * 4
      const key = (png.data[sourceOffset] << 16) |
        (png.data[sourceOffset + 1] << 8) |
        png.data[sourceOffset + 2]
      scanlines[scanlineOffset + x + 1] = colorIndices.get(key)
    }
  }
  const header = Buffer.alloc(13)
  header.writeUInt32BE(png.width, 0)
  header.writeUInt32BE(png.height, 4)
  header[8] = 8
  header[9] = 3
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    pngChunk('IHDR', header),
    pngChunk('PLTE', paletteData),
    pngChunk('IDAT', zlib.deflateSync(scanlines, { level: 9 })),
    pngChunk('IEND', Buffer.alloc(0))
  ])
}

function drawEllipse(png, centerX, centerY, radiusX, radiusY, color) {
  const left = Math.floor(centerX - radiusX - 1)
  const right = Math.ceil(centerX + radiusX + 1)
  const top = Math.floor(centerY - radiusY - 1)
  const bottom = Math.ceil(centerY + radiusY + 1)
  for (let y = top; y <= bottom; y += 1) {
    for (let x = left; x <= right; x += 1) {
      const normalized = Math.sqrt(
        ((x + 0.5 - centerX) / radiusX) ** 2 +
          ((y + 0.5 - centerY) / radiusY) ** 2
      )
      if (normalized > 1.08) continue
      const alpha = normalized <= 0.94 ? 255 : 255 * (1.08 - normalized) / 0.14
      setPixel(png, x, y, color[0], color[1], color[2], alpha)
    }
  }
}

function drawLotus(png, centerX, centerY, scale) {
  const targetWidth = Math.round(82 * scale)
  const targetHeight = Math.round(70 * scale)
  const startX = Math.round(centerX - targetWidth / 2)
  const startY = Math.round(centerY - targetHeight / 2)
  for (let y = 0; y < targetHeight; y += 1) {
    const sourceY = Math.min(lotus.height - 1, Math.floor(y * lotus.height / targetHeight))
    for (let x = 0; x < targetWidth; x += 1) {
      const sourceX = Math.min(lotus.width - 1, Math.floor(x * lotus.width / targetWidth))
      const sourceOffset = (sourceY * lotus.width + sourceX) * 4
      const alpha = lotus.data[sourceOffset + 3]
      if (alpha === 0) continue
      setPixel(
        png,
        startX + x,
        startY + y,
        lotus.data[sourceOffset],
        lotus.data[sourceOffset + 1],
        lotus.data[sourceOffset + 2],
        alpha
      )
    }
  }
}

function drawBead(png, centerY, logicalIndex) {
  const scale = 1
  const radius = BASE_DIAMETER * scale / 2
  if (centerY + radius < 0 || centerY - radius > HEIGHT) return

  const isGold = logicalIndex === 0
  const fill = isGold ? [199, 143, 47] : [68, 39, 24]
  const border = isGold ? [226, 185, 89] : [106, 64, 42]
  const borderWidth = 3 * scale
  const left = Math.floor(CENTER_X - radius - 1)
  const right = Math.ceil(CENTER_X + radius + 1)
  const top = Math.floor(centerY - radius - 1)
  const bottom = Math.ceil(centerY + radius + 1)

  for (let y = top; y <= bottom; y += 1) {
    for (let x = left; x <= right; x += 1) {
      const deltaX = x + 0.5 - CENTER_X
      const deltaY = y + 0.5 - centerY
      const distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY)
      if (distance > radius + 0.8) continue
      const edgeAlpha = distance <= radius - 0.8
        ? 255
        : 255 * (radius + 0.8 - distance) / 1.6
      const base = distance >= radius - borderWidth ? border : fill
      const normalizedX = deltaX / radius
      const normalizedY = deltaY / radius
      const highlightDistance = Math.sqrt(
        (normalizedX + 0.34) ** 2 + (normalizedY + 0.38) ** 2
      )
      const highlight = Math.max(0, 1 - highlightDistance / 0.9) * 18
      const edgeShade = Math.max(0.72, 1 - 0.18 * (distance / radius) ** 2)
      setPixel(
        png,
        x,
        y,
        base[0] * edgeShade + highlight,
        base[1] * edgeShade + highlight,
        base[2] * edgeShade + highlight,
        edgeAlpha
      )
    }
  }

  drawEllipse(
    png,
    CENTER_X - radius * 0.4,
    centerY - radius * 0.58,
    13 * scale,
    7 * scale,
    isGold ? [237, 201, 103] : [128, 82, 56]
  )
  if (isGold) drawLotus(png, CENTER_X, centerY + 3 * scale, scale)
}

function renderStrip(startIndex) {
  const png = new PNG({ width: WIDTH, height: HEIGHT })
  fillBackground(png)
  for (let relativeIndex = -1; relativeIndex <= 2; relativeIndex += 1) {
    const logicalIndex = (startIndex + relativeIndex + BEAD_COUNT) % BEAD_COUNT
    const centerY = CENTER_Y + relativeIndex * BEAD_STEP
    drawBead(png, centerY, logicalIndex)
  }
  return png
}

fs.mkdirSync(outputDir, { recursive: true })
for (const fileName of fs.readdirSync(outputDir)) {
  if (/^beads_.*\.png$/.test(fileName)) {
    fs.unlinkSync(path.join(outputDir, fileName))
  }
}
const strips = FRAME_GROUPS.map((group) => ({
  fileName: `beads_${group.name}_strip.png`,
  png: renderStrip(group.startIndex)
}))
const sharedPalette = buildPalette(colorsForPngs(strips.map((strip) => strip.png)))
for (const strip of strips) {
  fs.writeFileSync(
    path.join(outputDir, strip.fileName),
    encodePng8(strip.png, sharedPalette)
  )
}

console.log(`Generated ${FRAME_GROUPS.length} compositor strips in ${outputDir}`)
