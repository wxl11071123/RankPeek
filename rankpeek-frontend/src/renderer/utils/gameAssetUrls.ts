const DATA_DRAGON_VERSION = '15.24.1'
const DATA_DRAGON_CDN = `https://ddragon.leagueoflegends.com/cdn/${DATA_DRAGON_VERSION}`
const COMMUNITY_DRAGON_RAW = 'https://raw.communitydragon.org/latest'
const COMMUNITY_DRAGON_GAME_DATA = `${COMMUNITY_DRAGON_RAW}/plugins/rcp-be-lol-game-data/global/default/v1`
const COMMUNITY_DRAGON_MINIMAP_ICONS = `${COMMUNITY_DRAGON_RAW}/game/assets/ux/minimap/icons`
const BACKEND_ASSET_BASE = 'http://127.0.0.1:8080/api/v1/asset'
const PUBLIC_GAME_ASSET_BASE = `${getViteBaseUrl()}game-assets`
const ASSET_PLACEHOLDER_URL = `data:image/svg+xml,${encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="10" fill="#2f3744"/><path d="M20 42h24L36 28l-6 8-4-5z" fill="#6f7b8d"/><circle cx="25" cy="23" r="4" fill="#8994a5"/></svg>'
)}`

export type GameAssetKind = 'champion' | 'item' | 'spell' | 'perk' | 'augment' | 'profile'
export type ObjectiveIconKind =
  | 'turret'
  | 'turretPlate'
  | 'inhibitor'
  | 'baron'
  | 'dragon'
  | 'infernal'
  | 'mountain'
  | 'ocean'
  | 'cloud'
  | 'hextech'
  | 'chemtech'
  | 'elder'
  | 'herald'
  | 'voidgrub'
  | 'soul-infernal'
  | 'soul-mountain'
  | 'soul-ocean'
  | 'soul-cloud'
  | 'soul-hextech'
  | 'soul-chemtech'
  | 'unknownDragon'
export type GameAssetFallbackState = 'empty' | 'failed'
type GameAssetManifestSection = Record<string, string>
type LocalAssetCacheResolver = (kind: GameAssetKind, id: number) => string | null | undefined

const VERIFIED_OBJECTIVE_MINIMAP_FILES: Partial<Record<ObjectiveIconKind, string>> = {
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

export interface GameAssetManifest {
  version: string
  locale: string
  items: GameAssetManifestSection
  summonerSpells: GameAssetManifestSection
  perks: GameAssetManifestSection
  augments: GameAssetManifestSection
  champions: GameAssetManifestSection
  profileIcons?: GameAssetManifestSection
  objectives?: GameAssetManifestSection
}

export interface GameAssetMetadataEntry {
  id: number
  name?: string
  description?: string
  plaintext?: string
  shortDesc?: string
  longDesc?: string
  icon?: string
}

export interface GameAssetMetadata {
  version: string
  locale: string
  items: Record<string, GameAssetMetadataEntry>
  perks: Record<string, GameAssetMetadataEntry>
  augments: Record<string, GameAssetMetadataEntry>
}

export interface ItemIconSlot {
  index: number
  itemId: number | null
  url: string
  empty: boolean
}

const EMPTY_MANIFEST: GameAssetManifest = {
  version: 'seed',
  locale: 'zh_CN',
  items: {},
  summonerSpells: {},
  perks: {},
  augments: {},
  champions: {},
  profileIcons: {},
  objectives: {}
}

const EMPTY_METADATA: GameAssetMetadata = {
  version: 'seed',
  locale: 'zh_CN',
  items: {},
  perks: {},
  augments: {}
}

let manifest: GameAssetManifest = normalizeManifest(EMPTY_MANIFEST)
let metadata: GameAssetMetadata = normalizeMetadata(EMPTY_METADATA)
let localAssetCacheResolver: LocalAssetCacheResolver | null = null
const failedAssetUrls = new Set<string>()
const fallbackChains = new Map<string, string[]>()

export function getChampionIconUrl(championId?: number | null): string {
  return resolveAssetUrl('champion', championId)
}

export function getItemIconUrl(itemId?: number | null): string {
  return resolveAssetUrl('item', itemId)
}

export function getSummonerSpellIconUrl(spellId?: number | null): string {
  return resolveAssetUrl('spell', spellId)
}

export function getPerkIconUrl(perkId?: number | null): string {
  return resolveAssetUrl('perk', perkId)
}

export function getAugmentIconUrl(augmentId?: number | null): string {
  return resolveAssetUrl('augment', augmentId)
}

export function getProfileIconUrl(profileIconId?: number | null): string {
  return resolveAssetUrl('profile', profileIconId)
}

export function getObjectiveIconUrl(kind: ObjectiveIconKind): string {
  return getManifestObjectiveIconUrl(kind) || getCommunityDragonObjectiveIconUrl(kind)
}

export function getItemAssetDetails(itemId?: number | null): GameAssetMetadataEntry | null {
  return getAssetDetails('item', itemId)
}

export function getPerkAssetDetails(perkId?: number | null): GameAssetMetadataEntry | null {
  return getAssetDetails('perk', perkId)
}

export function getAugmentAssetDetails(augmentId?: number | null): GameAssetMetadataEntry | null {
  return getAssetDetails('augment', augmentId)
}

export function getItemIconSlots(stats: unknown): ItemIconSlot[] {
  const record = isRecord(stats) ? stats : {}
  return Array.from({ length: 7 }, (_, index) => {
    const itemId = normalizeAssetId(record[`item${index}`])
    const url = itemId === null ? '' : getItemIconUrl(itemId)
    return {
      index,
      itemId,
      url,
      empty: itemId === null
    }
  })
}

export function getAssetFallbackClass(kind: GameAssetKind, state: GameAssetFallbackState): string {
  return `asset-slot asset-slot-${kind} asset-slot-${state}`
}

export function getAssetPlaceholderUrl(): string {
  return ASSET_PLACEHOLDER_URL
}

export function setGameAssetManifest(nextManifest: Partial<GameAssetManifest>): void {
  manifest = normalizeManifest({
    ...EMPTY_MANIFEST,
    ...nextManifest
  })
  fallbackChains.clear()
}

export function setLocalAssetCacheResolver(resolver: LocalAssetCacheResolver | null): void {
  localAssetCacheResolver = resolver
  fallbackChains.clear()
}

export async function loadGameAssetManifest(url = `${PUBLIC_GAME_ASSET_BASE}/manifest.json`): Promise<void> {
  if (typeof fetch !== 'function') {
    return
  }

  try {
    const response = await fetch(url, { cache: 'no-cache' })
    if (!response.ok) {
      return
    }
    setGameAssetManifest(await response.json() as Partial<GameAssetManifest>)
  } catch {
    // Local asset metadata is an optimization. A missing or unreadable manifest must not block app startup.
  }
}

export async function loadGameAssetMetadata(url = `${PUBLIC_GAME_ASSET_BASE}/metadata.json`): Promise<void> {
  if (typeof fetch !== 'function') {
    return
  }

  try {
    const response = await fetch(url, { cache: 'no-cache' })
    if (!response.ok) {
      return
    }
    setGameAssetMetadata(await response.json() as Partial<GameAssetMetadata>)
  } catch {
    // Local text metadata is optional. Missing metadata must not block app startup.
  }
}

export function recordAssetLoadFailure(url: string): void {
  const normalized = normalizeFailureUrl(url)
  if (normalized) {
    failedAssetUrls.add(normalized)
  }
}

export function markAssetLoadFailed(event: Event): void {
  const image = event.currentTarget
  if (!isHtmlImageElement(image)) {
    return
  }

  const failedUrl = image.currentSrc || image.src || image.getAttribute('src') || ''
  recordAssetLoadFailure(failedUrl)

  const nextUrl = getNextAssetFallbackUrl(failedUrl)
  if (nextUrl) {
    delete image.dataset.assetFailed
    image.removeAttribute('aria-hidden')
    image.src = nextUrl
    return
  }

  markImageAsUnavailable(image)
}

export function resetGameAssetResolverForTest(): void {
  manifest = normalizeManifest(EMPTY_MANIFEST)
  metadata = normalizeMetadata(EMPTY_METADATA)
  localAssetCacheResolver = null
  failedAssetUrls.clear()
  fallbackChains.clear()
}

export function setGameAssetManifestForTest(nextManifest: Partial<GameAssetManifest>): void {
  setGameAssetManifest(nextManifest)
}

export function setGameAssetMetadataForTest(nextMetadata: Partial<GameAssetMetadata>): void {
  setGameAssetMetadata(nextMetadata)
}

function resolveAssetUrl(kind: GameAssetKind, rawId?: number | null): string {
  const id = normalizeAssetId(rawId)
  if (id === null) {
    return ''
  }

  const candidates = buildAssetCandidates(kind, id)
  registerFallbackChain(candidates)

  return candidates.find(candidate => !isAssetUrlFailed(candidate)) || ASSET_PLACEHOLDER_URL
}

function setGameAssetMetadata(nextMetadata: Partial<GameAssetMetadata>): void {
  metadata = normalizeMetadata({
    ...EMPTY_METADATA,
    ...nextMetadata
  })
}

function getAssetDetails(kind: 'item' | 'perk' | 'augment', rawId?: number | null): GameAssetMetadataEntry | null {
  const id = normalizeAssetId(rawId)
  if (id === null) {
    return null
  }

  const section = getMetadataSection(kind)
  return section[String(id)] || null
}

function getMetadataSection(kind: 'item' | 'perk' | 'augment'): Record<string, GameAssetMetadataEntry> {
  switch (kind) {
    case 'item':
      return metadata.items
    case 'perk':
      return metadata.perks
    case 'augment':
      return metadata.augments
  }
}

function buildAssetCandidates(kind: GameAssetKind, id: number): string[] {
  return uniqueNonEmpty([
    getManifestAssetUrl(kind, id),
    localAssetCacheResolver?.(kind, id) || '',
    getMetadataAssetUrl(kind, id),
    getBackendAssetUrl(kind, id),
    ...getRemoteAssetUrls(kind, id)
  ])
}

function getManifestAssetUrl(kind: GameAssetKind, id: number): string {
  const section = getManifestSection(kind)
  const value = section[String(id)]
  if (!value) {
    return ''
  }

  return normalizeManifestAssetPath(value)
}

function getManifestSection(kind: GameAssetKind): GameAssetManifestSection {
  switch (kind) {
    case 'champion':
      return manifest.champions
    case 'item':
      return manifest.items
    case 'spell':
      return manifest.summonerSpells
    case 'perk':
      return manifest.perks
    case 'augment':
      return manifest.augments
    case 'profile':
      return manifest.profileIcons || {}
  }
}

function getMetadataAssetUrl(kind: GameAssetKind, id: number): string {
  if (kind !== 'perk') {
    return ''
  }

  const value = metadata.perks[String(id)]?.icon
  return value ? normalizeManifestAssetPath(value) : ''
}

function getManifestObjectiveIconUrl(kind: ObjectiveIconKind): string {
  const value = manifest.objectives?.[kind]
  return value ? normalizeManifestAssetPath(value) : ''
}

function normalizeManifestAssetPath(path: string): string {
  const trimmed = path.trim()
  if (!trimmed) {
    return ''
  }
  if (/^(?:https?:|data:|file:)/i.test(trimmed)) {
    return trimmed
  }
  if (trimmed.startsWith('/game-assets/')) {
    return `${PUBLIC_GAME_ASSET_BASE}/${trimmed.slice('/game-assets/'.length)}`
  }
  if (trimmed.startsWith('game-assets/')) {
    return `${getViteBaseUrl()}${trimmed}`
  }
  if (trimmed.startsWith('./game-assets/')) {
    return `${getViteBaseUrl()}${trimmed.slice(2)}`
  }
  return `${PUBLIC_GAME_ASSET_BASE}/${trimmed.replace(/^\/+/, '')}`
}

function getCommunityDragonObjectiveIconUrl(kind: ObjectiveIconKind): string {
  const minimapFileName = VERIFIED_OBJECTIVE_MINIMAP_FILES[kind]
  return minimapFileName ? `${COMMUNITY_DRAGON_MINIMAP_ICONS}/${minimapFileName}` : ''
}

function getBackendAssetUrl(kind: GameAssetKind, id: number): string {
  switch (kind) {
    case 'champion':
      return `${BACKEND_ASSET_BASE}/champion/${id}`
    case 'item':
      return `${BACKEND_ASSET_BASE}/item/${id}`
    case 'spell':
      return `${BACKEND_ASSET_BASE}/spell/${id}`
    case 'augment':
      return `${BACKEND_ASSET_BASE}/augment/${id}`
    case 'profile':
      return `${BACKEND_ASSET_BASE}/profile/${id}`
    case 'perk':
      return ''
  }
}

function getRemoteAssetUrls(kind: GameAssetKind, id: number): string[] {
  switch (kind) {
    case 'champion':
      return [`${COMMUNITY_DRAGON_GAME_DATA}/champion-icons/${id}.png`]
    case 'item':
      return [`${DATA_DRAGON_CDN}/img/item/${id}.png`]
    case 'spell':
      return [`${COMMUNITY_DRAGON_GAME_DATA}/summoner-spells/${id}.png`]
    case 'perk':
      return []
    case 'profile':
      return [`${DATA_DRAGON_CDN}/img/profileicon/${id}.png`]
    case 'augment':
      return []
  }
}

function registerFallbackChain(candidates: string[]): void {
  if (!candidates.length) {
    return
  }
  for (const candidate of candidates) {
    fallbackChains.set(normalizeFailureUrl(candidate), candidates)
    fallbackChains.set(candidate, candidates)
  }
}

function getNextAssetFallbackUrl(failedUrl: string): string {
  const normalizedFailedUrl = normalizeFailureUrl(failedUrl)
  const chain = fallbackChains.get(normalizedFailedUrl) || fallbackChains.get(failedUrl)
  if (!chain?.length) {
    return ''
  }

  const failedIndex = chain.findIndex(candidate => normalizeFailureUrl(candidate) === normalizedFailedUrl)
  const candidates = failedIndex >= 0 ? chain.slice(failedIndex + 1) : chain
  return candidates.find(candidate => !isAssetUrlFailed(candidate)) || ASSET_PLACEHOLDER_URL
}

function isAssetUrlFailed(url: string): boolean {
  return failedAssetUrls.has(normalizeFailureUrl(url))
}

function normalizeFailureUrl(url: string): string {
  if (!url) {
    return ''
  }
  if (typeof window === 'undefined' || !window.location?.href) {
    return url
  }
  try {
    return new URL(url, window.location.href).href
  } catch {
    return url
  }
}

function markImageAsUnavailable(image: HTMLImageElement): void {
  image.dataset.assetFailed = 'true'
  image.removeAttribute('src')
  image.setAttribute('aria-hidden', 'true')
}

function isHtmlImageElement(value: unknown): value is HTMLImageElement {
  return typeof HTMLImageElement !== 'undefined' && value instanceof HTMLImageElement
}

function normalizeAssetId(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : null
}

function normalizeManifest(nextManifest: Partial<GameAssetManifest>): GameAssetManifest {
  return {
    version: nextManifest.version || EMPTY_MANIFEST.version,
    locale: nextManifest.locale || EMPTY_MANIFEST.locale,
    items: normalizeManifestSection(nextManifest.items),
    summonerSpells: normalizeManifestSection(nextManifest.summonerSpells),
    perks: normalizeManifestSection(nextManifest.perks),
    augments: normalizeManifestSection(nextManifest.augments),
    champions: normalizeManifestSection(nextManifest.champions),
    profileIcons: normalizeManifestSection(nextManifest.profileIcons),
    objectives: normalizeManifestSection(nextManifest.objectives)
  }
}

function normalizeMetadata(nextMetadata: Partial<GameAssetMetadata>): GameAssetMetadata {
  return {
    version: nextMetadata.version || EMPTY_METADATA.version,
    locale: nextMetadata.locale || EMPTY_METADATA.locale,
    items: normalizeMetadataSection(nextMetadata.items),
    perks: normalizeMetadataSection(nextMetadata.perks),
    augments: normalizeMetadataSection(nextMetadata.augments)
  }
}

function normalizeManifestSection(section?: GameAssetManifestSection): GameAssetManifestSection {
  return section ? { ...section } : {}
}

function normalizeMetadataSection(section?: Record<string, GameAssetMetadataEntry>): Record<string, GameAssetMetadataEntry> {
  return section ? { ...section } : {}
}

function uniqueNonEmpty(values: string[]): string[] {
  return Array.from(new Set(values.filter(Boolean)))
}

function getViteBaseUrl(): string {
  const baseUrl = import.meta.env?.BASE_URL || './'
  return baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
