import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  getAssetFallbackClass,
  getAssetPlaceholderUrl,
  getAugmentIconUrl,
  getChampionIconUrl,
  getItemIconSlots,
  getItemIconUrl,
  getPerkIconUrl,
  getProfileIconUrl,
  getSummonerSpellIconUrl,
  recordAssetLoadFailure,
  resetGameAssetResolverForTest,
  setGameAssetManifestForTest
} from './gameAssetUrls.ts'

test('game asset helpers prefer local manifest paths before remote fallbacks', () => {
  resetGameAssetResolverForTest()
  setGameAssetManifestForTest({
    version: 'test',
    locale: 'zh_CN',
    champions: { 103: 'champions/103.png' },
    items: { 1001: 'items/1001.png' },
    summonerSpells: { 4: 'summoner-spells/4.png' },
    perks: { 8005: 'perks/8005.png' },
    augments: { 12345: 'augments/12345.png' },
    profileIcons: { 29: 'profile-icons/29.png' }
  })

  assert.equal(getChampionIconUrl(103), './game-assets/champions/103.png')
  assert.equal(getItemIconUrl(1001), './game-assets/items/1001.png')
  assert.equal(getSummonerSpellIconUrl(4), './game-assets/summoner-spells/4.png')
  assert.equal(getPerkIconUrl(8005), './game-assets/perks/8005.png')
  assert.equal(getAugmentIconUrl(12345), './game-assets/augments/12345.png')
  assert.equal(getProfileIconUrl(29), './game-assets/profile-icons/29.png')
})

test('game asset helpers fall back through local backend before remote URLs', () => {
  resetGameAssetResolverForTest()

  assert.equal(getChampionIconUrl(103), 'http://127.0.0.1:8080/api/v1/asset/champion/103')
  assert.equal(getItemIconUrl(1001), 'http://127.0.0.1:8080/api/v1/asset/item/1001')
  assert.equal(getSummonerSpellIconUrl(4), 'http://127.0.0.1:8080/api/v1/asset/spell/4')
  assert.equal(getProfileIconUrl(29), 'http://127.0.0.1:8080/api/v1/asset/profile/29')
  assert.equal(getAugmentIconUrl(12345), 'http://127.0.0.1:8080/api/v1/asset/augment/12345')
  assert.equal(getPerkIconUrl(8005), 'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/perks/8005.png')
})

test('invalid asset ids do not create broken image URLs', () => {
  resetGameAssetResolverForTest()
  assert.equal(getChampionIconUrl(0), '')
  assert.equal(getItemIconUrl(0), '')
  assert.equal(getItemIconUrl(undefined), '')
  assert.equal(getSummonerSpellIconUrl(null), '')
  assert.equal(getPerkIconUrl(Number.NaN), '')
  assert.equal(getAugmentIconUrl(-1), '')
})

test('item slots preserve seven equipment positions with empty placeholders', () => {
  resetGameAssetResolverForTest()
  const slots = getItemIconSlots({
    item0: 1055,
    item1: 0,
    item2: undefined,
    item3: 3031,
    item4: null,
    item5: 3006,
    item6: 3340
  })

  assert.equal(slots.length, 7)
  assert.deepEqual(slots.map(slot => slot.itemId), [1055, null, null, 3031, null, 3006, 3340])
  assert.equal(slots[0].url, 'http://127.0.0.1:8080/api/v1/asset/item/1055')
  assert.equal(slots[1].url, '')
  assert.equal(slots[1].empty, true)
  assert.equal(slots[6].url, 'http://127.0.0.1:8080/api/v1/asset/item/3340')
})

test('failed DDragon URLs are remembered and not returned again in the same session', () => {
  resetGameAssetResolverForTest()

  const localProxyUrl = getItemIconUrl(1001)
  recordAssetLoadFailure(localProxyUrl)
  const ddragonUrl = getItemIconUrl(1001)
  assert.equal(ddragonUrl, 'https://ddragon.leagueoflegends.com/cdn/15.24.1/img/item/1001.png')

  recordAssetLoadFailure(ddragonUrl)
  assert.equal(getItemIconUrl(1001), getAssetPlaceholderUrl())
})

test('augment icons degrade to placeholder after incomplete sources fail', () => {
  resetGameAssetResolverForTest()

  const localProxyUrl = getAugmentIconUrl(555001)
  assert.equal(localProxyUrl, 'http://127.0.0.1:8080/api/v1/asset/augment/555001')
  recordAssetLoadFailure(localProxyUrl)

  assert.equal(getAugmentIconUrl(555001), getAssetPlaceholderUrl())
})

test('fallback classes distinguish empty and failed asset states', () => {
  assert.equal(getAssetFallbackClass('item', 'empty'), 'asset-slot asset-slot-item asset-slot-empty')
  assert.equal(getAssetFallbackClass('champion', 'failed'), 'asset-slot asset-slot-champion asset-slot-failed')
  assert.equal(getAssetFallbackClass('augment', 'failed'), 'asset-slot asset-slot-augment asset-slot-failed')
})

test('asset-consuming Vue components do not hardcode backend or remote asset URLs', () => {
  const files = [
    '../components/match-history/MatchHistoryCard.vue',
    '../components/summoner/MatchRosterCompact.vue',
    '../components/summoner/MatchDetailModal.vue',
    '../components/summoner/SummonerOverviewPanel.vue',
    '../components/gaming/PlayerCard.vue',
    '../components/HomeChart.vue',
    '../views/HomeView.vue'
  ]

  for (const file of files) {
    const source = readFileSync(new URL(file, import.meta.url), 'utf8')
    assert.doesNotMatch(source, /http:\/\/127\.0\.0\.1:8080\/api\/v1\/asset/)
    assert.doesNotMatch(source, /\/api\/v1\/asset\/(?:champion|item|spell|perk|profile)/)
    assert.doesNotMatch(source, /ddragon\.leagueoflegends\.com|raw\.communitydragon\.org|communitydragon/i)
  }
})

test('backend asset details do not advertise DDragon as the primary icon URL', () => {
  const source = readFileSync(
    new URL('../../../../rankpeek-backend/src/main/java/io/rankpeek/controller/AssetController.java', import.meta.url),
    'utf8'
  )

  assert.doesNotMatch(source, /ddragon\.leagueoflegends\.com\/cdn/)
  assert.match(source, /\/api\/v1\/asset\/item/)
})

test('renderer startup loads the local game asset manifest without making components responsible for it', () => {
  const mainSource = readFileSync(new URL('../main.ts', import.meta.url), 'utf8')
  const manifest = JSON.parse(readFileSync(new URL('../../../public/game-assets/manifest.json', import.meta.url), 'utf8'))

  assert.match(mainSource, /loadGameAssetManifest/)
  assert.equal(manifest.version, 'seed')
  assert.deepEqual(Object.keys(manifest).sort(), [
    'augments',
    'champions',
    'items',
    'locale',
    'perks',
    'profileIcons',
    'summonerSpells',
    'version'
  ])
})

test('match detail and cards hide failed or empty equipment images', () => {
  const files = [
    '../components/match-history/MatchHistoryCard.vue',
    '../components/summoner/MatchDetailModal.vue'
  ]

  for (const file of files) {
    const source = readFileSync(new URL(file, import.meta.url), 'utf8')
    assert.match(source, /getItemIconSlots/)
    assert.match(source, /v-if="slot\.url"/)
    assert.match(source, /@error="markAssetLoadFailed"/)
  }
})
