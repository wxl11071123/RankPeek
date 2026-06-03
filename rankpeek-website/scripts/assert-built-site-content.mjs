import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const buildDir = process.argv[2] ?? 'dist'

const requiredText = [
  'AI复盘、数据分析、性能强大的本地工具',
  '赛前风险提示',
  '内置数据分析：RP指数',
  '赛后复盘/夸夸机',
  '常见问题'
]

const requiredAssets = [
  'assets/rankpeek-logo.png',
  'assets/rankpeek-white-glow.png',
  'assets/rankpeek-pregame-analysis-safe.png',
  'assets/rankpeek-opgg-list-safe.png',
  'assets/rankpeek-opgg-detail-safe.png',
  'assets/rankpeek-rp-index-safe.png',
  'assets/rankpeek-postgame-review-safe.png',
  'assets/rankpeek-postgame-praise-safe.png',
  'assets/rankpeek-coach-report-safe.png',
  'assets/rankpeek-hero-poster.png'
]

function collectTextFiles(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const filePath = join(dir, entry)
    const stats = statSync(filePath)

    if (stats.isDirectory()) {
      return collectTextFiles(filePath)
    }

    return /\.(?:css|html|js)$/i.test(entry) ? [filePath] : []
  })
}

if (!existsSync(buildDir)) {
  throw new Error(`Build directory not found: ${buildDir}`)
}

const haystack = collectTextFiles(buildDir)
  .map((filePath) => readFileSync(filePath, 'utf8'))
  .join('\n')

const missingText = requiredText.filter((text) => !haystack.includes(text))
const missingAssets = requiredAssets.filter((asset) => !existsSync(join(buildDir, asset)))

if (missingText.length || missingAssets.length) {
  console.error('Built site content check failed.')

  if (missingText.length) {
    console.error(`Missing text: ${missingText.join(', ')}`)
  }

  if (missingAssets.length) {
    console.error(`Missing assets: ${missingAssets.join(', ')}`)
  }

  process.exit(1)
}

console.log('Built site content check passed.')
