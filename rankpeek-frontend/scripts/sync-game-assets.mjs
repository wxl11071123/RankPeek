#!/usr/bin/env node
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(currentDir, '..')
const assetRoot = resolve(projectRoot, 'public/game-assets')
const manifestPath = resolve(assetRoot, 'manifest.json')
const metadataPath = resolve(assetRoot, 'metadata.json')
const ddragonCdn = version => `https://ddragon.leagueoflegends.com/cdn/${version}`
const ddragonImageCdn = 'https://ddragon.leagueoflegends.com/cdn/img'
const cdragonRaw = 'https://raw.communitydragon.org/latest'
const cdragonRoot = 'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default'
const cdragonGameData = `${cdragonRoot}/v1`
const cdragonMinimapIcons = `${cdragonRaw}/game/assets/ux/minimap/icons`
const itemSource = () => `${ddragonCdn(version)}/data/${locale}/item.json`
const traitSource = () => `${ddragonCdn(version)}/data/${locale}/runesReforged.json`
const defaultAugmentSource = () => `${cdragonGameData}/cherry-augments.json`

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

const objectiveSources = {
  turret: 'tower.png',
  turretPlate: 'turret_1plate.png',
  inhibitor: 'inhibitor.png',
  baron: 'baron.png',
  dragon: 'dragon.png',
  infernal: 'dragon_infernal.png',
  mountain: 'dragon_mountain.png',
  ocean: 'dragon_ocean.png',
  cloud: 'dragon_cloud.png',
  hextech: 'dragon_hextech.png',
  chemtech: 'dragon_chemtech.png',
  elder: 'dragon_elder.png',
  herald: 'riftherald.png',
  voidgrub: 'grub.png',
  unknownDragon: 'dragon.png',
  'soul-infernal': 'dragon_infernal.png',
  'soul-mountain': 'dragon_mountain.png',
  'soul-ocean': 'dragon_ocean.png',
  'soul-cloud': 'dragon_cloud.png',
  'soul-hextech': 'dragon_hextech.png',
  'soul-chemtech': 'dragon_chemtech.png'
}

if (args.help) {
  printHelp()
  process.exit(0)
}

const manifest = await readManifest()
const metadata = await readMetadata()
manifest.version = version
manifest.locale = locale
metadata.version = version
metadata.locale = locale
metadata.items = metadata.items || {}
metadata.perks = metadata.perks || {}
metadata.augments = metadata.augments || {}
manifest.objectives = manifest.objectives || {}

for (const [kind, config] of Object.entries(sections)) {
  for (const id of args[kind] || []) {
    await syncAsset(kind, config, id)
  }
}

if (args.allItems) {
  await downloadAllItems()
}

if (args.allPerks) {
  await downloadAllPerks()
}

if (args.allAugments) {
  await downloadAllAugments(args.augmentSource || defaultAugmentSource())
}

if (args.allObjectives) {
  await downloadAllObjectives()
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

if (args.withMetadata || args.allItems || args.allPerks || args.allAugments) {
  await writeMetadata()
}

async function syncAsset(kind, config, id) {
  const normalizedId = normalizeId(id)
  if (!normalizedId) {
    console.warn(`Skipping invalid ${kind} id: ${id}`)
    return false
  }

  const relativePath = `${config.dir}/${normalizedId}.png`
  const target = resolve(assetRoot, relativePath)
  await mkdir(dirname(target), { recursive: true })

  try {
    const url = config.url(normalizedId)
    if (!url) {
      throw new Error('missing source URL')
    }
    const response = await fetch(url)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const buffer = Buffer.from(await response.arrayBuffer())
    await writeFile(target, buffer)
    manifest[config.manifestKey] = manifest[config.manifestKey] || {}
    manifest[config.manifestKey][String(normalizedId)] = relativePath
    console.log(`Synced ${kind} ${normalizedId} -> ${relativePath}`)
    return true
  } catch (error) {
    console.warn(`Failed to sync ${kind} ${normalizedId}: ${error instanceof Error ? error.message : String(error)}`)
    return false
  }
}

async function downloadAllItems() {
  manifest.items = manifest.items || {}
  let payload
  try {
    const response = await fetch(itemSource())
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    payload = await response.json()
  } catch (error) {
    process.exitCode = 1
    console.warn(`Failed to read DDragon item index: ${error instanceof Error ? error.message : String(error)}`)
    return
  }

  const items = Object.entries(payload?.data || {})
    .map(([id, item]) => ({ id: normalizeId(id), item }))
    .filter(entry => entry.id)
    .sort((left, right) => left.id - right.id)
  let synced = 0
  let failed = 0

  for (const entry of items) {
    const ok = await syncAsset('item', sections.item, entry.id)
    const icon = `items/${entry.id}.png`
    metadata.items[String(entry.id)] = {
      id: entry.id,
      name: textValue(entry.item?.name),
      description: textValue(entry.item?.description),
      plaintext: textValue(entry.item?.plaintext),
      icon
    }
    if (ok) {
      synced += 1
    } else {
      failed += 1
    }
  }

  console.log(`Synced ${synced}/${items.length} DDragon item icons from item.json`)
  if (failed > 0) {
    process.exitCode = 1
  }
}

async function downloadAllPerks() {
  manifest.perks = manifest.perks || {}
  let payload
  try {
    const response = await fetch(traitSource())
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    payload = await response.json()
  } catch (error) {
    process.exitCode = 1
    console.warn(`Failed to read DDragon rune index: ${error instanceof Error ? error.message : String(error)}`)
    return
  }

  const perks = extractPerks(payload)
  const iconById = new Map(perks.map(perk => [String(perk.id), perk.icon]))
  let synced = 0
  let failed = 0

  for (const perk of perks) {
    const ok = await syncAsset('perk', {
      manifestKey: 'perks',
      dir: 'perks',
      url: id => {
        const icon = iconById.get(String(id))
        return icon ? `${ddragonImageCdn}/${icon}` : ''
      }
    }, perk.id)
    metadata.perks[String(perk.id)] = {
      id: perk.id,
      name: textValue(perk.name),
      description: textValue(perk.description),
      shortDesc: textValue(perk.shortDesc),
      longDesc: textValue(perk.longDesc),
      icon: `perks/${perk.id}.png`
    }
    if (ok) {
      synced += 1
    } else {
      failed += 1
    }
  }

  console.log(`Synced ${synced}/${perks.length} DDragon perk icons from runesReforged.json`)
  if (failed > 0) {
    process.exitCode = 1
  }
}

async function downloadAllAugments(sourceUrl) {
  manifest.augments = manifest.augments || {}
  let payload
  try {
    const response = await fetch(sourceUrl)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    payload = await response.json()
  } catch (error) {
    console.warn(`Skipped all augments; source unavailable at ${sourceUrl}: ${error instanceof Error ? error.message : String(error)}`)
    return
  }

  const augments = extractAugments(payload)
  let synced = 0
  let failed = 0

  for (const augment of augments) {
    const ok = await syncAsset('augment', {
      manifestKey: 'augments',
      dir: 'augments',
      url: () => augment.sourceUrl
    }, augment.id)
    metadata.augments[String(augment.id)] = {
      id: augment.id,
      name: textValue(augment.name),
      description: textValue(augment.description),
      icon: `augments/${augment.id}.png`
    }
    if (ok) {
      synced += 1
    } else {
      failed += 1
    }
  }

  console.log(`Synced ${synced}/${augments.length} CommunityDragon augment icons from cherry-augments.json`)
  if (failed > 0) {
    console.warn(`Skipped ${failed} augment icons; item and perk sync results are unaffected.`)
  }
}

async function downloadAllObjectives() {
  manifest.objectives = manifest.objectives || {}
  let synced = 0
  let failed = 0

  for (const [key, fileName] of Object.entries(objectiveSources)) {
    const ok = await syncObjectiveAsset(key, fileName)
    if (ok) {
      synced += 1
    } else {
      failed += 1
    }
  }

  console.log(`Synced ${synced}/${Object.keys(objectiveSources).length} CommunityDragon minimap objective icons`)
  if (failed > 0) {
    process.exitCode = 1
  }
}

async function syncObjectiveAsset(key, fileName) {
  const relativePath = `objectives/${fileName}`
  const target = resolve(assetRoot, relativePath)
  await mkdir(dirname(target), { recursive: true })

  try {
    const response = await fetch(`${cdragonMinimapIcons}/${fileName}`)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const buffer = Buffer.from(await response.arrayBuffer())
    await writeFile(target, buffer)
    manifest.objectives[key] = relativePath
    console.log(`Synced objective ${key} -> ${relativePath}`)
    return true
  } catch (error) {
    console.warn(`Failed to sync objective ${key}: ${error instanceof Error ? error.message : String(error)}`)
    return false
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
      profileIcons: {},
      objectives: {}
    }
  }
}

async function readMetadata() {
  try {
    return JSON.parse(await readFile(metadataPath, 'utf8'))
  } catch {
    return {
      version,
      locale,
      items: {},
      perks: {},
      augments: {}
    }
  }
}

async function writeMetadata() {
  await writeFile(metadataPath, `${JSON.stringify(metadata, null, 2)}\n`, 'utf8')
  console.log(`Updated ${metadataPath}`)
}

function parseArgs(rawArgs) {
  const parsed = {
    champion: [],
    item: [],
    spell: [],
    perk: [],
    profile: [],
    augment: [],
    allItems: false,
    allPerks: false,
    allAugments: false,
    allObjectives: false,
    withMetadata: false,
    augmentSource: ''
  }

  for (let index = 0; index < rawArgs.length; index += 1) {
    const arg = rawArgs[index]
    if (arg === '--help' || arg === '-h') {
      parsed.help = true
      continue
    }
    if (arg === '--all-items') {
      parsed.allItems = true
      continue
    }
    if (arg === '--all-perks') {
      parsed.allPerks = true
      continue
    }
    if (arg === '--all-augments') {
      parsed.allAugments = true
      continue
    }
    if (arg === '--all-objectives') {
      parsed.allObjectives = true
      continue
    }
    if (arg === '--with-metadata') {
      parsed.withMetadata = true
      continue
    }
    if (arg === '--version' || arg === '--locale' || arg === '--augment-source') {
      const key = arg === '--augment-source' ? 'augmentSource' : arg.slice(2)
      parsed[key] = rawArgs[index + 1]
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

function extractPerks(payload) {
  const unique = new Map()
  for (const style of Array.isArray(payload) ? payload : []) {
    const styleId = normalizeId(style?.id)
    const styleIcon = normalizeIconPath(style?.icon)
    if (styleId && styleIcon) {
      unique.set(String(styleId), {
        id: styleId,
        name: style.name,
        description: style.tooltip,
        icon: styleIcon
      })
    }
    for (const slot of Array.isArray(style?.slots) ? style.slots : []) {
      for (const rune of Array.isArray(slot?.runes) ? slot.runes : []) {
        const id = normalizeId(rune?.id)
        const icon = normalizeIconPath(rune?.icon)
        if (id && icon) {
          unique.set(String(id), {
            id,
            name: rune.name,
            shortDesc: rune.shortDesc,
            longDesc: rune.longDesc,
            icon
          })
        }
      }
    }
  }
  return Array.from(unique.values()).sort((left, right) => left.id - right.id)
}

function extractAugments(payload) {
  const unique = new Map()
  for (const augment of Array.isArray(payload) ? payload : []) {
    const id = normalizeId(augment?.id)
    const iconPath = normalizeCdragonAssetPath(
      augment?.augmentSmallIconPath ||
      augment?.augmentLargeIconPath ||
      augment?.iconPath ||
      augment?.icon
    )
    if (!id || !iconPath) {
      continue
    }
    unique.set(String(id), {
      id,
      name: firstText(augment.name, augment.nameTRA, augment.simpleName, augment.simpleNameTRA),
      description: firstText(augment.description, augment.descriptionTRA, augment.tooltip, augment.tooltipTRA),
      sourceUrl: `${cdragonRoot}/${iconPath}`
    })
  }
  return Array.from(unique.values()).sort((left, right) => left.id - right.id)
}

function normalizeIconPath(value) {
  return typeof value === 'string' && value.trim()
    ? value.trim().replace(/^\/+/, '')
    : ''
}

function normalizeCdragonAssetPath(value) {
  const path = normalizeIconPath(value)
  if (!path) {
    return ''
  }
  return path
    .replace(/^lol-game-data\/assets\//i, '')
    .toLowerCase()
}

function textValue(value) {
  return typeof value === 'string' ? value : ''
}

function firstText(...values) {
  return values.find(value => typeof value === 'string' && value.trim()) || ''
}

function printHelp() {
  console.log(`
Usage:
  node scripts/sync-game-assets.mjs --version 15.24.1 --item 1001 --spell 4 --perk 8005 --champion 103
  node scripts/sync-game-assets.mjs --version 15.24.1 --locale zh_CN --all-items --all-perks --all-augments --with-metadata
  node scripts/sync-game-assets.mjs --version 15.24.1 --locale zh_CN --all-items --all-perks --with-metadata
  node scripts/sync-game-assets.mjs --version 15.24.1 --locale zh_CN --all-objectives

Notes:
  - manifest.json maps IDs to local icon paths.
  - metadata.json stores item, perk, and augment text details.
  - DDragon version is used for item/profile icons and item/perk metadata.
  - --all-perks reads runesReforged.json and downloads style plus rune icons from the small icon CDN.
  - --all-augments reads CommunityDragon cherry-augments.json by default and can be overridden with --augment-source.
  - --all-objectives downloads the small CommunityDragon minimap objective icons referenced by manifest.objectives.
  - Augment sync failures are skipped so item and perk syncs are not blocked.
  - Do not commit full archive packs.
`)
}
