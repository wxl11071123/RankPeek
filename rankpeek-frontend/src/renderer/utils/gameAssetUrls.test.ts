import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import {
  getAssetFallbackClass,
  getAssetPlaceholderUrl,
  getAugmentIconUrl,
  getAugmentAssetDetails,
  getAugmentTooltipDetails,
  getChampionIconUrl,
  getItemIconSlots,
  getItemIconUrl,
  getItemAssetDetails,
  getItemTooltipDetails,
  loadGameAssetMetadata,
  loadLcuGameAssetMetadataOverlay,
  getPerkIconUrl,
  getPerkAssetDetails,
  getPerkTooltipDetails,
  getProfileIconUrl,
  getObjectiveIconUrl,
  getSummonerSpellIconUrl,
  recordAssetLoadFailure,
  resetGameAssetResolverForTest,
  setGameAssetMetadataForTest,
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
    perks: {
      8000: 'perks/8000.png',
      8005: 'perks/8005.png',
      8100: 'perks/8100.png',
      8135: 'perks/8135.png'
    },
    augments: { 12345: 'augments/12345.png' },
    profileIcons: { 29: 'profile-icons/29.png' }
  })

  assert.equal(getChampionIconUrl(103), './game-assets/champions/103.png')
  assert.equal(getItemIconUrl(1001), './game-assets/items/1001.png')
  assert.equal(getSummonerSpellIconUrl(4), './game-assets/summoner-spells/4.png')
  assert.equal(getPerkIconUrl(8000), './game-assets/perks/8000.png')
  assert.equal(getPerkIconUrl(8005), './game-assets/perks/8005.png')
  assert.equal(getPerkIconUrl(8100), './game-assets/perks/8100.png')
  assert.equal(getPerkIconUrl(8135), './game-assets/perks/8135.png')
  assert.equal(getAugmentIconUrl(12345), './game-assets/augments/12345.png')
  assert.equal(getProfileIconUrl(29), './game-assets/profile-icons/29.png')
})

test('objective icon helper prefers local manifest paths then verified CommunityDragon minimap objective icons', () => {
  resetGameAssetResolverForTest()
  setGameAssetManifestForTest({
    version: 'test',
    locale: 'zh_CN',
    objectives: {
      baron: 'objectives/baron.png',
      infernal: 'objectives/dragon_infernal.png',
      turret: 'objectives/tower.png'
    }
  })

  assert.equal(getObjectiveIconUrl('baron'), './game-assets/objectives/baron.png')
  assert.equal(getObjectiveIconUrl('infernal'), './game-assets/objectives/dragon_infernal.png')
  assert.equal(getObjectiveIconUrl('turret'), './game-assets/objectives/tower.png')

  resetGameAssetResolverForTest()
  assert.equal(getObjectiveIconUrl('baron'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/baron.png')
  assert.equal(getObjectiveIconUrl('dragon'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon.png')
  assert.equal(getObjectiveIconUrl('infernal'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_infernal.png')
  assert.equal(getObjectiveIconUrl('mountain'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_mountain.png')
  assert.equal(getObjectiveIconUrl('ocean'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_ocean.png')
  assert.equal(getObjectiveIconUrl('cloud'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_cloud.png')
  assert.equal(getObjectiveIconUrl('elder'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_elder.png')
  assert.equal(getObjectiveIconUrl('unknownDragon'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon.png')
  assert.equal(getObjectiveIconUrl('herald'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/riftherald.png')
  assert.equal(getObjectiveIconUrl('voidgrub'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/grub.png')
  assert.equal(getObjectiveIconUrl('hextech'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_hextech.png')
  assert.equal(getObjectiveIconUrl('chemtech'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_chemtech.png')
  assert.equal(getObjectiveIconUrl('turret'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/tower.png')
  assert.equal(getObjectiveIconUrl('turretPlate'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/turret_1plate.png')
  assert.equal(getObjectiveIconUrl('inhibitor'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/inhibitor.png')
  assert.equal(getObjectiveIconUrl('soul-infernal'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_infernal.png')
  assert.equal(getObjectiveIconUrl('soul-mountain'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_mountain.png')
  assert.equal(getObjectiveIconUrl('soul-ocean'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_ocean.png')
  assert.equal(getObjectiveIconUrl('soul-cloud'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_cloud.png')
  assert.equal(getObjectiveIconUrl('soul-hextech'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_hextech.png')
  assert.equal(getObjectiveIconUrl('soul-chemtech'), 'https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon_chemtech.png')
  assert.notEqual(getObjectiveIconUrl('hextech'), getObjectiveIconUrl('dragon'))
  assert.notEqual(getObjectiveIconUrl('chemtech'), getObjectiveIconUrl('dragon'))
  assert.notEqual(getObjectiveIconUrl('voidgrub'), getObjectiveIconUrl('herald'))
  assert.notEqual(getObjectiveIconUrl('voidgrub'), getObjectiveIconUrl('baron'))
})

test('elemental dragon and structure objective icons use verified minimap resource logos', () => {
  resetGameAssetResolverForTest()

  const urls = {
    infernal: getObjectiveIconUrl('infernal'),
    mountain: getObjectiveIconUrl('mountain'),
    ocean: getObjectiveIconUrl('ocean'),
    cloud: getObjectiveIconUrl('cloud'),
    hextech: getObjectiveIconUrl('hextech'),
    chemtech: getObjectiveIconUrl('chemtech'),
    elder: getObjectiveIconUrl('elder'),
    baron: getObjectiveIconUrl('baron'),
    herald: getObjectiveIconUrl('herald'),
    voidgrub: getObjectiveIconUrl('voidgrub'),
    turret: getObjectiveIconUrl('turret'),
    turretPlate: getObjectiveIconUrl('turretPlate'),
    inhibitor: getObjectiveIconUrl('inhibitor')
  }

  for (const url of Object.values(urls)) {
    assert.ok(url)
    assert.match(url, /game\/assets\/ux\/minimap\/icons\//)
    assert.doesNotMatch(url, /plugins\/rcp-fe-lol-match-history\/global\/default/i)
    assert.doesNotMatch(url, /(?:fire|earth|water|air)-100\.png|dragon_square_(?:hextech|chemtech)|right_icons_grub/i)
  }

  assert.match(urls.infernal, /dragon_infernal\.png$/)
  assert.match(urls.mountain, /dragon_mountain\.png$/)
  assert.match(urls.ocean, /dragon_ocean\.png$/)
  assert.match(urls.cloud, /dragon_cloud\.png$/)
  assert.match(urls.hextech, /dragon_hextech\.png$/)
  assert.match(urls.chemtech, /dragon_chemtech\.png$/)
  assert.match(urls.voidgrub, /grub\.png$/)
  assert.match(urls.turret, /tower\.png$/)
  assert.match(urls.turretPlate, /turret_1plate\.png$/)
  assert.match(urls.inhibitor, /inhibitor\.png$/)
  assert.notEqual(urls.voidgrub, getObjectiveIconUrl('herald'))
  assert.notEqual(urls.voidgrub, getObjectiveIconUrl('baron'))
  assert.notEqual(urls.voidgrub, getObjectiveIconUrl('dragon'))
})

test('objective manifest entries keep priority over remote objective fallbacks', () => {
  resetGameAssetResolverForTest()
  setGameAssetManifestForTest({
    version: 'test',
    locale: 'zh_CN',
    objectives: {
      infernal: 'objectives/local-infernal.png',
      hextech: 'objectives/local-hextech.png',
      chemtech: 'objectives/local-chemtech.png',
      voidgrub: 'objectives/local-voidgrub.png',
      turret: 'objectives/local-turret.png',
      turretPlate: 'objectives/local-turret-plate.png',
      inhibitor: 'objectives/local-inhibitor.png',
      'soul-hextech': 'objectives/local-soul-hextech.png',
      'soul-chemtech': 'objectives/local-soul-chemtech.png'
    }
  })

  assert.equal(getObjectiveIconUrl('infernal'), './game-assets/objectives/local-infernal.png')
  assert.equal(getObjectiveIconUrl('hextech'), './game-assets/objectives/local-hextech.png')
  assert.equal(getObjectiveIconUrl('chemtech'), './game-assets/objectives/local-chemtech.png')
  assert.equal(getObjectiveIconUrl('voidgrub'), './game-assets/objectives/local-voidgrub.png')
  assert.equal(getObjectiveIconUrl('turret'), './game-assets/objectives/local-turret.png')
  assert.equal(getObjectiveIconUrl('turretPlate'), './game-assets/objectives/local-turret-plate.png')
  assert.equal(getObjectiveIconUrl('inhibitor'), './game-assets/objectives/local-inhibitor.png')
  assert.equal(getObjectiveIconUrl('soul-hextech'), './game-assets/objectives/local-soul-hextech.png')
  assert.equal(getObjectiveIconUrl('soul-chemtech'), './game-assets/objectives/local-soul-chemtech.png')
})

test('game asset helpers fall back through local backend before remote URLs', () => {
  resetGameAssetResolverForTest()

  assert.equal(getChampionIconUrl(103), 'http://127.0.0.1:8080/api/v1/asset/champion/103')
  assert.equal(getItemIconUrl(1001), 'http://127.0.0.1:8080/api/v1/asset/item/1001')
  assert.equal(getSummonerSpellIconUrl(4), 'http://127.0.0.1:8080/api/v1/asset/spell/4')
  assert.equal(getProfileIconUrl(29), 'http://127.0.0.1:8080/api/v1/asset/profile/29')
  assert.equal(getAugmentIconUrl(12345), 'http://127.0.0.1:8080/api/v1/asset/augment/12345')
  assert.equal(getPerkIconUrl(8005), getAssetPlaceholderUrl())
  assert.doesNotMatch(getPerkIconUrl(8005), /raw\.communitydragon\.org\/latest\/plugins\/rcp-be-lol-game-data\/global\/default\/v1\/perks\/8005\.png/)
})

test('perk icon helper uses metadata local icon paths when manifest is missing', () => {
  resetGameAssetResolverForTest()
  setGameAssetMetadataForTest({
    version: 'test',
    locale: 'zh_CN',
    perks: {
      8135: {
        id: 8135,
        name: 'Treasure Hunter',
        description: 'Earn extra gold.',
        icon: 'perks/8135.png'
      }
    }
  })

  assert.equal(getPerkIconUrl(8135), './game-assets/perks/8135.png')
  assert.equal(getPerkAssetDetails(8135)?.name, 'Treasure Hunter')
  assert.doesNotMatch(getPerkIconUrl(8135), /raw\.communitydragon\.org\/latest\/plugins\/rcp-be-lol-game-data\/global\/default\/v1\/perks\/8135\.png/)
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
  assert.match(mainSource, /loadGameAssetMetadata/)
  assert.match(mainSource, /loadLcuGameAssetMetadataOverlay/)
  assert.match(mainSource, /Promise\.all\(\[loadGameAssetManifest\(\), loadGameAssetMetadata\(\)\]\)[\s\S]*loadLcuGameAssetMetadataOverlay\(\)[\s\S]*app\.mount\('#app'\)/)
  assert.doesNotMatch(mainSource, /Promise\.race|manifestStartupDeadline|setTimeout\(resolve,\s*500\)/)
  assert.equal(typeof manifest.version, 'string')
  assert.equal(manifest.locale, 'zh_CN')
  assert.deepEqual(Object.keys(manifest).sort(), [
    'augments',
    'champions',
    'items',
    'locale',
    'objectives',
    'perks',
    'profileIcons',
    'summonerSpells',
    'version'
  ])
})

test('local game asset manifest contains selective objective icons for normal rendering', () => {
  const manifest = JSON.parse(readFileSync(new URL('../../../public/game-assets/manifest.json', import.meta.url), 'utf8'))
  const objectiveKeys = [
    'turret',
    'turretPlate',
    'inhibitor',
    'baron',
    'dragon',
    'infernal',
    'mountain',
    'ocean',
    'cloud',
    'hextech',
    'chemtech',
    'elder',
    'herald',
    'voidgrub',
    'unknownDragon',
    'soul-infernal',
    'soul-mountain',
    'soul-ocean',
    'soul-cloud',
    'soul-hextech',
    'soul-chemtech'
  ]

  assert.equal(typeof manifest.objectives, 'object')
  resetGameAssetResolverForTest()
  setGameAssetManifestForTest(manifest)

  for (const key of objectiveKeys) {
    assert.equal(typeof manifest.objectives[key], 'string', `missing objective manifest key ${key}`)
    assert.match(manifest.objectives[key], /^objectives\/.+\.png$/, `objective ${key} must be a local small icon`)
    assert.match(getObjectiveIconUrl(key), /^\.\/game-assets\/objectives\/.+\.png$/)
  }

  for (const key of ['infernal', 'mountain', 'ocean', 'cloud']) {
    assert.doesNotMatch(manifest.objectives[key], /(?:fire|earth|water|air)-100\.png$/)
  }
})

test('game asset metadata helpers expose item, perk, and augment text without changing icon lookup', () => {
  resetGameAssetResolverForTest()
  setGameAssetMetadataForTest({
    version: 'test',
    locale: 'zh_CN',
    items: {
      3153: {
        id: 3153,
        name: 'Blade of the Ruined King',
        description: 'Deals damage.',
        plaintext: 'On-hit damage.',
        icon: 'items/3153.png'
      }
    },
    perks: {
      8000: {
        id: 8000,
        name: 'Precision',
        description: 'Improved attacks.',
        icon: 'perks/8000.png'
      },
      8005: {
        id: 8005,
        name: 'Press the Attack',
        shortDesc: 'Hit three times.',
        longDesc: 'Amplifies damage.',
        icon: 'perks/8005.png'
      }
    },
    augments: {
      1205: {
        id: 1205,
        name: 'ADAPt',
        description: 'Adaptive force.',
        icon: 'augments/1205.png'
      }
    }
  })

  assert.equal(getItemAssetDetails(3153)?.name, 'Blade of the Ruined King')
  assert.equal(getPerkIconUrl(8000), './game-assets/perks/8000.png')
  assert.equal(getPerkAssetDetails(8000)?.icon, 'perks/8000.png')
  assert.equal(getPerkIconUrl(8005), './game-assets/perks/8005.png')
  assert.equal(getPerkAssetDetails(8005)?.shortDesc, 'Hit three times.')
  assert.equal(getAugmentAssetDetails(1205)?.description, 'Adaptive force.')
  assert.equal(getPerkAssetDetails(999999), null)
})

test('tooltip details use metadata names, sanitized descriptions, subtitles, and icon helpers', () => {
  resetGameAssetResolverForTest()
  setGameAssetManifestForTest({
    version: 'test',
    locale: 'zh_CN',
    perks: {
      8005: 'perks/8005.png'
    }
  })
  setGameAssetMetadataForTest({
    version: 'test',
    locale: 'zh_CN',
    items: {
      3031: {
        id: 3031,
        name: 'Infinity Edge',
        description: '<mainText><stats>70 Attack Damage</stats><br><br>Critical strikes deal &amp; scale bonus.</mainText>',
        plaintext: 'Critical item.',
        gold: {
          total: 3600,
          base: 625,
          sell: 2520
        }
      }
    },
    perks: {
      8005: {
        id: 8005,
        name: 'Press the Attack',
        shortDesc: 'Hit <b>three</b> times &amp; expose.',
        longDesc: 'This should not win over shortDesc.',
        icon: 'perks/8005.png'
      }
    },
    augments: {
      1205: {
        id: 1205,
        name: 'ADAPt',
        description: 'Gain <font color="#48C4B7">adaptive force</font>.'
      }
    }
  })

  const item = getItemTooltipDetails(3031)
  const perk = getPerkTooltipDetails(8005)
  const augment = getAugmentTooltipDetails(1205)

  assert.equal(item?.kind, 'item')
  assert.equal(item?.id, 3031)
  assert.equal(item?.name, 'Infinity Edge')
  assert.equal(item?.subtitle, '售价 3600')
  assert.equal(item?.description, '70 Attack Damage\nCritical strikes deal & scale bonus.')
  assert.equal(item?.iconUrl, 'http://127.0.0.1:8080/api/v1/asset/item/3031')
  assert.equal(perk?.kind, 'perk')
  assert.equal(perk?.subtitle, '')
  assert.equal(perk?.description, 'Hit three times & expose.')
  assert.equal(perk?.iconUrl, './game-assets/perks/8005.png')
  assert.equal(augment?.kind, 'augment')
  assert.equal(augment?.subtitle, '')
  assert.equal(augment?.description, 'Gain adaptive force.')
  assert.equal(augment?.iconUrl, 'http://127.0.0.1:8080/api/v1/asset/augment/1205')
  assert.doesNotMatch(item?.description || '', /<[^>]+>|&amp;/)
  assert.doesNotMatch(perk?.description || '', /<[^>]+>|&amp;/)
  assert.doesNotMatch(augment?.description || '', /<[^>]+>|font/i)
})

test('tooltip details fall back to readable names and no-detail copy when metadata is missing', () => {
  resetGameAssetResolverForTest()

  assert.deepEqual(getItemTooltipDetails(3031), {
    kind: 'item',
    id: 3031,
    name: '装备 3031',
    subtitle: '',
    description: '暂无详细说明',
    iconUrl: 'http://127.0.0.1:8080/api/v1/asset/item/3031'
  })
  assert.deepEqual(getPerkTooltipDetails(8005), {
    kind: 'perk',
    id: 8005,
    name: '符文 8005',
    subtitle: '',
    description: '暂无详细说明',
    iconUrl: getAssetPlaceholderUrl()
  })
  assert.equal(getAugmentTooltipDetails(0), null)
})

test('local game asset metadata contains tooltip text for common items, perks, and augments', () => {
  const metadata = JSON.parse(readFileSync(new URL('../../../public/game-assets/metadata.json', import.meta.url), 'utf8'))

  assert.equal(typeof metadata.items['3031']?.name, 'string')
  assert.equal(typeof metadata.items['3031']?.description, 'string')
  assert.equal(typeof metadata.items['3153']?.name, 'string')
  assert.equal(typeof metadata.items['3006']?.name, 'string')
  assert.equal(typeof metadata.perks['8005']?.name, 'string')
  assert.equal(typeof metadata.perks['8100']?.name, 'string')
  assert.equal(typeof metadata.perks['8135']?.name, 'string')
  assert.ok(Object.keys(metadata.augments || {}).length > 0)
  assert.ok(Object.values(metadata.augments || {}).some(entry =>
    typeof entry.description === 'string' && entry.description.length > 0
  ))
})

test('local game asset metadata resolves screenshot item, augment, and perk tooltip details', () => {
  const metadata = JSON.parse(readFileSync(new URL('../../../public/game-assets/metadata.json', import.meta.url), 'utf8'))

  resetGameAssetResolverForTest()
  const itemFallback = getItemTooltipDetails(6610)
  const augmentFallback = getAugmentTooltipDetails(2005)
  const perkFallback = getPerkTooltipDetails(8005)

  setGameAssetMetadataForTest(metadata)

  const item = getItemTooltipDetails(6610)
  const augment = getAugmentTooltipDetails(2005)
  const perk = getPerkTooltipDetails(8005)

  assert.equal(typeof metadata.items['6610']?.name, 'string')
  assert.equal(typeof metadata.items['6610']?.description, 'string')
  assert.equal(typeof metadata.items['6610']?.gold?.total, 'number')
  assert.ok(metadata.items['6610'].description.trim())
  assert.equal(item?.name, metadata.items['6610'].name)
  assert.match(item?.subtitle || '', /^售价\s+\d+/)
  assert.doesNotMatch(item?.subtitle || '', /装备 6610/)
  assert.notEqual(item?.name, itemFallback?.name)
  assert.notEqual(item?.description, itemFallback?.description)
  assert.ok(item?.description.trim())
  assert.notEqual(item?.description, '暂无详细说明')
  assert.doesNotMatch(item?.description || '', /<[^>]+>/)

  assert.equal(typeof metadata.augments['2005']?.name, 'string')
  assert.equal(typeof metadata.augments['2005']?.description, 'string')
  assert.ok(metadata.augments['2005'].description.trim())
  assert.equal(augment?.name, metadata.augments['2005'].name)
  assert.notEqual(augment?.subtitle, '海克斯强化 2005')
  assert.notEqual(augment?.name, augmentFallback?.name)
  assert.notEqual(augment?.description, augmentFallback?.description)
  assert.ok(augment?.description.trim())
  assert.notEqual(augment?.description, '暂无详细说明')
  assert.doesNotMatch(augment?.description || '', /<[^>]+>/)

  assert.equal(typeof metadata.perks['8005']?.name, 'string')
  assert.equal(perk?.name, metadata.perks['8005'].name)
  assert.notEqual(perk?.description, perkFallback?.description)
  assert.ok(perk?.description.trim())
  assert.equal(getPerkIconUrl(8005), './game-assets/perks/8005.png')
})

test('tooltip text cleanup keeps useful item and augment text after game HTML tags are removed', () => {
  resetGameAssetResolverForTest()
  setGameAssetMetadataForTest({
    version: 'test',
    locale: 'zh_CN',
    items: {
      6610: {
        id: 6610,
        name: 'Sundered Sky',
        description: '<mainText><stats><attention>40</attention> Attack Damage</stats><br><passive>Passive</passive> keep text<br><active>Active</active> use text<br><rules>Rule text</rules></mainText>'
      }
    },
    augments: {
      2005: {
        id: 2005,
        name: 'Triggered Inferno',
        description: '<mainText><attention>Inferno</attention><br><rules>Useful rule text</rules></mainText>'
      }
    }
  })

  const item = getItemTooltipDetails(6610)
  const augment = getAugmentTooltipDetails(2005)

  assert.equal(item?.description, '40 Attack Damage\nPassive keep text\nActive use text\nRule text')
  assert.equal(augment?.description, 'Inferno\nUseful rule text')
  assert.ok(item?.description.trim())
  assert.ok(augment?.description.trim())
  assert.doesNotMatch(item?.description || '', /<[^>]+>/)
  assert.doesNotMatch(augment?.description || '', /<[^>]+>/)
})

test('tooltip text cleanup preserves client-style line breaks from Riot markup', () => {
  resetGameAssetResolverForTest()
  setGameAssetMetadataForTest({
    version: 'test',
    locale: 'zh_CN',
    items: {
      6610: {
        id: 6610,
        name: '焚天',
        description: '<mainText><stats><attention>40</attention>攻击力<br><attention>400</attention>生命值<br><attention>10</attention>技能急速</stats><br><br><passive>光盾打击</passive><br>你对一个英雄打出的第一次攻击会<attention>暴击</attention>并<healing>回复生命值</healing>。</mainText>',
        gold: { total: 3100 }
      }
    }
  })

  const item = getItemTooltipDetails(6610)

  assert.equal(
    item?.description,
    '40攻击力\n400生命值\n10技能急速\n光盾打击\n你对一个英雄打出的第一次攻击会暴击并回复生命值。'
  )
  assert.equal(item?.subtitle, '售价 3100')
  assert.equal((item?.description.match(/\n/g) || []).length, 4)
})

test('LCU metadata overlay overrides local item and augment metadata by id', async () => {
  resetGameAssetResolverForTest()
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async (url: string) => ({
    ok: true,
    json: async () => url.includes('local')
      ? {
          version: 'static',
          locale: 'zh_CN',
          items: {
            6610: {
              id: 6610,
              name: 'Static Sundered Sky',
              description: 'Old DDragon text.',
              gold: { total: 3000 }
            }
          },
          augments: {
            2005: {
              id: 2005,
              name: 'Static Triggered Inferno',
              description: 'Old augment text.'
            }
          }
        }
      : {
          version: 'lcu',
          locale: 'zh_CN',
          items: {
            6610: {
              id: 6610,
              name: '焚天',
              description: '<stats>40攻击力<br>400生命值</stats>',
              gold: { total: 3100, base: 900, sell: 2170 }
            }
          },
          augments: {
            2005: {
              id: 2005,
              name: '扳机炼狱',
              description: '每回合，你要么变大。',
              rarity: 'gold'
            }
          }
        }
  })) as unknown as typeof fetch

  try {
    await loadGameAssetMetadata('http://asset.test/local-metadata.json')
    assert.equal(getItemTooltipDetails(6610)?.name, 'Static Sundered Sky')
    assert.equal(getItemTooltipDetails(6610)?.subtitle, '售价 3000')

    await loadLcuGameAssetMetadataOverlay('http://asset.test/lcu-metadata')

    assert.equal(getItemTooltipDetails(6610)?.name, '焚天')
    assert.equal(getItemTooltipDetails(6610)?.subtitle, '售价 3100')
    assert.equal(getItemTooltipDetails(6610)?.description, '40攻击力\n400生命值')
    assert.equal(getAugmentTooltipDetails(2005)?.name, '扳机炼狱')
    assert.notEqual(getAugmentTooltipDetails(2005)?.subtitle, '海克斯强化 2005')
    assert.equal(getAugmentTooltipDetails(2005)?.description, '每回合，你要么变大。')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('local metadata remains available when LCU metadata overlay cannot be loaded', async () => {
  resetGameAssetResolverForTest()
  const originalFetch = globalThis.fetch
  globalThis.fetch = (async (url: string) => {
    if (url.includes('lcu')) {
      throw new Error('backend unavailable')
    }
    return {
      ok: true,
      json: async () => ({
        version: 'static',
        locale: 'zh_CN',
        items: {
          6610: {
            id: 6610,
            name: '焚天',
            description: 'Static item text.',
            gold: { total: 3100 }
          }
        },
        augments: {
          2005: {
            id: 2005,
            name: '扳机炼狱',
            description: 'Static augment text.'
          }
        }
      })
    }
  }) as unknown as typeof fetch

  try {
    await loadGameAssetMetadata('http://asset.test/local-metadata.json')
    await loadLcuGameAssetMetadataOverlay('http://asset.test/lcu-metadata')

    assert.equal(getItemTooltipDetails(6610)?.name, '焚天')
    assert.equal(getItemTooltipDetails(6610)?.subtitle, '售价 3100')
    assert.equal(getAugmentTooltipDetails(2005)?.name, '扳机炼狱')
    assert.equal(getAugmentTooltipDetails(2005)?.description, 'Static augment text.')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('local game asset manifest stays selective while including local perk mappings', () => {
  const manifest = JSON.parse(readFileSync(new URL('../../../public/game-assets/manifest.json', import.meta.url), 'utf8'))

  assert.deepEqual(manifest.items, {})
  assert.deepEqual(manifest.augments, {})
  assert.equal(typeof manifest.objectives, 'object')
  assert.ok(Object.keys(manifest.objectives).length > 0)
  assert.equal(typeof manifest.perks, 'object')
  assert.equal(manifest.perks['8005'], 'perks/8005.png')
  assert.equal(manifest.perks['8100'], 'perks/8100.png')
  assert.equal(manifest.perks['8000'], 'perks/8000.png')
  assert.equal(manifest.perks['8135'], 'perks/8135.png')
  assert.ok(existsSync(new URL('../../../public/game-assets/perks/8005.png', import.meta.url)))
  assert.ok(existsSync(new URL('../../../public/game-assets/perks/8100.png', import.meta.url)))
  assert.ok(existsSync(new URL('../../../public/game-assets/perks/8000.png', import.meta.url)))
})

test('game asset sync script hydrates item, perk, augment, and metadata selectively', () => {
  const source = readFileSync(new URL('../../../scripts/sync-game-assets.mjs', import.meta.url), 'utf8')

  assert.match(source, /--all-items/)
  assert.match(source, /--all-item-metadata/)
  assert.match(source, /--all-perks/)
  assert.match(source, /--all-augments/)
  assert.match(source, /--all-augment-metadata/)
  assert.match(source, /--all-objectives/)
  assert.match(source, /--with-metadata/)
  assert.match(source, /metadataPath/)
  assert.match(source, /readMetadata/)
  assert.match(source, /writeMetadata/)
  assert.match(source, /downloadAllItems/)
  assert.match(source, /downloadAllItemMetadata/)
  assert.match(source, /runesReforged\.json/)
  assert.match(source, /downloadAllPerks/)
  assert.match(source, /downloadAllAugments/)
  assert.match(source, /downloadAllAugmentMetadata/)
  assert.match(source, /cdragon\/arena/)
  assert.match(source, /downloadAllObjectives/)
  assert.match(source, /objectiveSources/)
  assert.match(source, /manifest\.objectives/)
  assert.match(source, /cherry-augments\.json/)
  assert.match(source, /manifest\.perks/)
  assert.match(source, /metadata\.items/)
  assert.match(source, /metadata\.perks/)
  assert.match(source, /metadata\.augments/)
  assert.match(source, /ddragonImageCdn\}\/\$\{icon\}/)
  assert.doesNotMatch(source, /dragontail/i)
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
