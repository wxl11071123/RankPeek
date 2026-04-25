import test from 'node:test'
import assert from 'node:assert/strict'
import { inflateSync } from 'node:zlib'
import { readFileSync } from 'node:fs'

const pngSignature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])

function paeth(left: number, up: number, upLeft: number) {
  const estimate = left + up - upLeft
  const leftDistance = Math.abs(estimate - left)
  const upDistance = Math.abs(estimate - up)
  const upLeftDistance = Math.abs(estimate - upLeft)

  if (leftDistance <= upDistance && leftDistance <= upLeftDistance) {
    return left
  }

  if (upDistance <= upLeftDistance) {
    return up
  }

  return upLeft
}

function readPng(buffer: Buffer) {
  assert.equal(buffer.subarray(0, 8).equals(pngSignature), true, 'PNG signature should be present')

  let width = 0
  let height = 0
  let bitDepth = 0
  let colorType = 0
  const idatChunks: Buffer[] = []

  for (let offset = 8; offset < buffer.length;) {
    const length = buffer.readUInt32BE(offset)
    const type = buffer.toString('ascii', offset + 4, offset + 8)
    const data = buffer.subarray(offset + 8, offset + 8 + length)

    if (type === 'IHDR') {
      width = data.readUInt32BE(0)
      height = data.readUInt32BE(4)
      bitDepth = data[8]
      colorType = data[9]
    }

    if (type === 'IDAT') {
      idatChunks.push(data)
    }

    offset += length + 12
  }

  assert.equal(bitDepth, 8)
  assert.equal(colorType, 6)

  const channels = 4
  const stride = width * channels
  const inflated = inflateSync(Buffer.concat(idatChunks))
  const pixels = Buffer.alloc(height * stride)
  let sourceOffset = 0

  for (let y = 0; y < height; y += 1) {
    const filter = inflated[sourceOffset]
    sourceOffset += 1

    for (let x = 0; x < stride; x += 1) {
      const raw = inflated[sourceOffset + x]
      const left = x >= channels ? pixels[y * stride + x - channels] : 0
      const up = y > 0 ? pixels[(y - 1) * stride + x] : 0
      const upLeft = y > 0 && x >= channels ? pixels[(y - 1) * stride + x - channels] : 0
      let value = raw

      if (filter === 1) {
        value = raw + left
      } else if (filter === 2) {
        value = raw + up
      } else if (filter === 3) {
        value = raw + Math.floor((left + up) / 2)
      } else if (filter === 4) {
        value = raw + paeth(left, up, upLeft)
      } else {
        assert.equal(filter, 0)
      }

      pixels[y * stride + x] = value & 0xff
    }

    sourceOffset += stride
  }

  return {
    width,
    height,
    pixel(x: number, y: number) {
      const offset = (y * width + x) * channels

      return {
        r: pixels[offset],
        g: pixels[offset + 1],
        b: pixels[offset + 2],
        a: pixels[offset + 3]
      }
    }
  }
}

function readPngFile(path: string) {
  return readPng(readFileSync(new URL(path, import.meta.url)))
}

function assertRealIconRoundedSquare(image: ReturnType<typeof readPng>) {
  const size = image.width
  assert.equal(image.height, size)

  const corner = image.pixel(Math.round(size * 0.02), Math.round(size * 0.02))
  assert.ok(corner.a < 16, 'rounded outside corner should be transparent')

  const field = image.pixel(Math.round(size * 0.5), Math.round(size * 0.1))
  assert.ok(field.r <= 12 && field.g <= 12 && field.b <= 18 && field.a > 240, 'inside field should be black')

  const ray = image.pixel(Math.round(size * 0.5), Math.round(size * 0.22))
  assert.ok(ray.r >= 225 && ray.g >= 225 && ray.b >= 225 && ray.a > 240, 'top ray should be visible')

  const eyeWhite = image.pixel(Math.round(size * 0.23), Math.round(size * 0.64))
  assert.ok(eyeWhite.r >= 225 && eyeWhite.g >= 225 && eyeWhite.b >= 225 && eyeWhite.a > 240, 'eye white should be visible')

  const pupil = image.pixel(Math.round(size * 0.5), Math.round(size * 0.56))
  assert.ok(pupil.r <= 12 && pupil.g <= 12 && pupil.b <= 16 && pupil.a > 240, 'single-eye pupil should be dark')

  const sparkle = image.pixel(Math.round(size * 0.5), Math.round(size * 0.67))
  assert.ok(sparkle.r >= 225 && sparkle.g >= 225 && sparkle.b >= 225 && sparkle.a > 240, 'center sparkle should be visible')
}

function assertSmallTrayIconIsLegible(image: ReturnType<typeof readPng>) {
  assert.equal(image.width, 16)
  assert.equal(image.height, 16)

  let brightPixels = 0
  let darkOpaquePixels = 0

  for (let y = 0; y < image.height; y += 1) {
    for (let x = 0; x < image.width; x += 1) {
      const pixel = image.pixel(x, y)

      if (pixel.a > 180 && pixel.r >= 180 && pixel.g >= 180 && pixel.b >= 180) {
        brightPixels += 1
      }

      if (pixel.a > 180 && pixel.r <= 30 && pixel.g <= 30 && pixel.b <= 40) {
        darkOpaquePixels += 1
      }
    }
  }

  assert.ok(brightPixels >= 24, '16px tray icon should preserve enough white mark pixels')
  assert.ok(darkOpaquePixels >= 80, '16px tray icon should keep a dark field for contrast')
}

function readIcoEntries(path: string) {
  const ico = readFileSync(new URL(path, import.meta.url))

  assert.equal(ico.readUInt16LE(0), 0)
  assert.equal(ico.readUInt16LE(2), 1)

  const count = ico.readUInt16LE(4)
  const entries = []

  for (let index = 0; index < count; index += 1) {
    const offset = 6 + index * 16
    const width = ico[offset] === 0 ? 256 : ico[offset]
    const height = ico[offset + 1] === 0 ? 256 : ico[offset + 1]
    const byteLength = ico.readUInt32LE(offset + 8)
    const imageOffset = ico.readUInt32LE(offset + 12)
    const payload = ico.subarray(imageOffset, imageOffset + byteLength)

    entries.push({ width, height, payload })
  }

  return entries
}

test('public app and tray pngs use the rounded-square real icon artwork', () => {
  const appIcon = readPngFile('../../public/icon.png')
  const trayIcon = readPngFile('../../public/tray-icon.png')

  assert.equal(appIcon.width, 1024)
  assert.equal(trayIcon.width, 256)
  assertRealIconRoundedSquare(appIcon)
  assertRealIconRoundedSquare(trayIcon)
})

test('ico assets include png entries for common Windows sizes', () => {
  for (const path of ['../../public/icon.ico', '../../public/tray-icon.ico']) {
    const entries = readIcoEntries(path)
    const sizes = entries.map((entry) => entry.width)

    assert.deepEqual(sizes, [16, 32, 48, 64, 128, 256])

    for (const entry of entries) {
      assert.equal(entry.height, entry.width)
      const png = readPng(entry.payload)
      assert.equal(png.width, entry.width)
      assert.equal(png.height, entry.height)

      if (entry.width === 16 && path.includes('tray-icon')) {
        assertSmallTrayIconIsLegible(png)
      }
    }
  }
})
