#!/usr/bin/env node
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(currentDir, '..')
const assetRoot = resolve(projectRoot, 'public/game-assets')
const manifestPath = resolve(assetRoot, 'manifest.json')
const ddragonCdn = version => `https://ddragon.leagueoflegends.com/cdn/${version}`
const cdragonGameData = 'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1'

const args = parseArgs(process.argv.slice(2))
const version = args.version || '15.24.1'
const locale = args.locale || 'zh_CN'

const sections = {
  champion: { manifestKey: 'champions', dir: 'champions', url: id => `${cdragonGameData}/champion-icons/${id}.png` },
  item: { manifestKey: 'items', dir: 'items', url: id => `${ddragonCdn(version)}/img/item/${id}.png` },
  spell: { manifestKey: 'summonerSpells', dir: 'summoner-spells', url: id => `${cdragonGameData}/summoner-spells/${id}.png` },
  perk: { manifestKey: 'perks', dir: 'perks', url: id => `${cdragonGameData}/perks/${id}.png` },
  profile: { manifestKey: 'profileIcons', dir: 'profile-icons', url: id => `${ddragonCdn(version)}/img/profileicon/${id}.png` }
}

if (args.help) {
  printHelp()
  process.exit(0)
}

const manifest = await readManifest()
manifest.version = version
manifest.locale = locale

for (const [kind, config] of Object.entries(sections)) {
  for (const id of args[kind] || []) {
    await syncAsset(kind, config, id)
  }
}

for (const augment of args.augment || []) {
  const [id, url] = String(augment).split('=')
  if (!id || !url) {
    console.warn(`Skipping augment ${augment}; pass --augment <id>=<direct-url> when a reliable source is known.`)
    continue
  }
  await syncAsset('augment', {
    manifestKey: 'augments',
    dir: 'augments',
    url: () => url
  }, id)
}

await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
console.log(`Updated ${manifestPath}`)

async function syncAsset(kind, config, id) {
  const normalizedId = normalizeId(id)
  if (!normalizedId) {
    console.warn(`Skipping invalid ${kind} id: ${id}`)
    return
  }

  const relativePath = `${config.dir}/${normalizedId}.png`
  const target = resolve(assetRoot, relativePath)
  await mkdir(dirname(target), { recursive: true })

  try {
    const response = await fetch(config.url(normalizedId))
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const buffer = Buffer.from(await response.arrayBuffer())
    await writeFile(target, buffer)
    manifest[config.manifestKey] = manifest[config.manifestKey] || {}
    manifest[config.manifestKey][String(normalizedId)] = relativePath
    console.log(`Synced ${kind} ${normalizedId} -> ${relativePath}`)
  } catch (error) {
    console.warn(`Failed to sync ${kind} ${normalizedId}: ${error instanceof Error ? error.message : String(error)}`)
  }
}

async function readManifest() {
  try {
    return JSON.parse(await readFile(manifestPath, 'utf8'))
  } catch {
    return {
      version,
      locale,
      items: {},
      summonerSpells: {},
      perks: {},
      augments: {},
      champions: {},
      profileIcons: {}
    }
  }
}

function parseArgs(rawArgs) {
  const parsed = {
    champion: [],
    item: [],
    spell: [],
    perk: [],
    profile: [],
    augment: []
  }

  for (let index = 0; index < rawArgs.length; index += 1) {
    const arg = rawArgs[index]
    if (arg === '--help' || arg === '-h') {
      parsed.help = true
      continue
    }
    if (arg === '--version' || arg === '--locale') {
      parsed[arg.slice(2)] = rawArgs[index + 1]
      index += 1
      continue
    }
    if (arg.startsWith('--')) {
      const key = arg.slice(2)
      if (Array.isArray(parsed[key])) {
        parsed[key].push(rawArgs[index + 1])
        index += 1
      }
    }
  }

  return parsed
}

function normalizeId(value) {
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : null
}

function printHelp() {
  console.log(`
Usage:
  node scripts/sync-game-assets.mjs --version 15.24.1 --item 1001 --spell 4 --perk 8005 --champion 103

Notes:
  - Downloads only explicitly requested IDs and updates public/game-assets/manifest.json.
  - DDragon version is used for item/profile icons; CommunityDragon latest is used for ID-addressable champion/spell/perk icons.
  - Augments are intentionally explicit: pass --augment <id>=<direct-url> only when the source path is verified.
`)
}
