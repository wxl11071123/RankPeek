import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  getObjectiveIconUrl,
  resetGameAssetResolverForTest
} from '../../utils/gameAssetUrls.ts'

function readInlineDetailSource(): string {
  return readFileSync(new URL('./MatchHistoryInlineDetail.vue', import.meta.url), 'utf8')
}

function readFunctionBlock(source: string, signature: string): string {
  const start = source.indexOf(signature)
  assert.notEqual(start, -1, `missing function signature: ${signature}`)
  const firstBrace = source.indexOf('{', start)
  assert.notEqual(firstBrace, -1, `missing function body: ${signature}`)

  let depth = 0
  for (let index = firstBrace; index < source.length; index += 1) {
    const char = source[index]
    if (char === '{') {
      depth += 1
    } else if (char === '}') {
      depth -= 1
      if (depth === 0) {
        return source.slice(start, index + 1)
      }
    }
  }

  assert.fail(`unterminated function body: ${signature}`)
}

function assertOrdered(source: string, snippets: string[]): void {
  const indexes = snippets.map(snippet => source.indexOf(snippet))
  assert.ok(
    indexes.every(index => index >= 0),
    `missing ordered snippet index: ${indexes.join(', ')}`
  )
  assert.deepEqual([...indexes].sort((left, right) => left - right), indexes)
}

test('inline match detail exposes compact overview, rune, and chart tabs', () => {
  const source = readInlineDetailSource()
  const zh = readFileSync(new URL('../../i18n/locales/zh-CN.ts', import.meta.url), 'utf8')
  const en = readFileSync(new URL('../../i18n/locales/en-US.ts', import.meta.url), 'utf8')

  assert.match(source, /type InlineDetailTabKey = 'overview' \| 'runes' \| 'chart'/)
  assert.match(source, /class="inline-match-detail"/)
  assert.match(source, /class="inline-detail-tabs"/)
  assert.match(source, /key: 'overview'[\s\S]*t\('matchDetail\.overviewTab'\)/)
  assert.match(source, /key: 'runes'[\s\S]*t\('matchDetail\.runesTab'\)/)
  assert.match(source, /key: 'chart'[\s\S]*t\('matchDetail\.chartTab'\)/)
  assert.match(zh, /'matchDetail\.runesTab': '符文'/)
  assert.match(zh, /'matchDetail\.chartTab': '线图'/)
  assert.match(en, /'matchDetail\.runesTab': 'Runes'/)
  assert.match(en, /'matchDetail\.chartTab': 'Chart'/)
})

test('inline overview renders two compact team tables without changing detail loading', () => {
  const source = readInlineDetailSource()

  assert.match(source, /const fallbackGameDetail = computed<GameDetail \| null>\(\(\) => toGameDetailFromMatchHistory\(props\.matchHistory\)\)/)
  assert.match(source, /mergeGameDetailWithSummary\(detail, fallbackGameDetail\.value\)/)
  assert.match(source, /const blueTeamPlayers = computed/)
  assert.match(source, /const redTeamPlayers = computed/)
  assert.match(source, /const teamSections = computed/)
  assert.match(source, /v-for="team in teamSections"/)
  assert.match(source, /class="team-detail-table"/)
  assert.match(source, /class="participant-row"/)
  assert.match(source, /class="metric-bar damage-bar"/)
  assert.match(source, /class="metric-bar taken-bar"/)
  assert.match(source, /getKillParticipation/)
  assert.match(source, /getPlayerItemSlots/)
  assert.match(source, /getItemIconSlots/)
  assert.match(source, /@click="handlePlayerClick\(player\)"/)
  assert.match(source, /emit\('navigateToPlayer', player\.gameName, player\.tagLine\)/)
})

test('runes tab shows perk0 and perkSubStyle for normal matches and playerAugment1-6 for augment matches', () => {
  const source = readInlineDetailSource()

  assert.match(source, /activeTabValue === 'runes'/)
  assert.match(source, /function getPlayerTraitSlots/)
  assert.match(source, /function getPerkTraitSlots/)
  assert.match(source, /readTraitId\(player, 'perk0'\)/)
  assert.match(source, /readTraitId\(player, 'perkSubStyle'\)/)
  assert.match(source, /readTraitId\(player, 'perkPrimaryStyle'\)/)
  assert.match(source, /readTraitId\(player, 'perk5'\)/)
  assert.match(source, /perk1/)
  assert.match(source, /perk2/)
  assert.match(source, /perk3/)
  assert.match(source, /perk4/)
  assert.match(source, /function getAugmentTraitSlots/)
  assert.match(source, /playerAugment1/)
  assert.match(source, /playerAugment6/)
  assert.match(source, /getPerkAssetDetails/)
  assert.match(source, /getAugmentAssetDetails/)
  assert.match(source, /:title="slot\.label"/)
  assert.match(source, /\{ empty: slot\.empty \}/)
})

test('inline detail uses rich asset tooltips for overview items and trait icons', () => {
  const source = readInlineDetailSource()
  const overviewBlock = source.match(/<div v-if="activeTabValue === 'overview'"[\s\S]*?<div v-else-if="activeTabValue === 'runes'"/)?.[0] || ''
  const runesBlock = source.match(/<div v-else-if="activeTabValue === 'runes'"[\s\S]*?<div v-else-if="activeTabValue === 'chart'"/)?.[0] || ''

  assert.match(source, /import AssetHoverTooltip from '@\/components\/common\/AssetHoverTooltip\.vue'/)
  assert.match(source, /getItemTooltipDetails/)
  assert.match(source, /getPerkTooltipDetails/)
  assert.match(source, /getAugmentTooltipDetails/)
  assert.match(source, /type GameAssetTooltipDetails/)
  assert.match(source, /function getTraitTooltipDetails\(slot: TraitSlot\): GameAssetTooltipDetails \| null \{[\s\S]*slot\.kind === 'augment'[\s\S]*getAugmentTooltipDetails\(slot\.id\)[\s\S]*getPerkTooltipDetails\(slot\.id\)/)

  assert.match(overviewBlock, /class="trait-pair"[\s\S]*<AssetHoverTooltip[\s\S]*v-if="slot\.url && !slot\.empty && getTraitTooltipDetails\(slot\)"[\s\S]*:details="getTraitTooltipDetails\(slot\)!"/)
  assert.match(overviewBlock, /class="item-row compact" aria-label="items"[\s\S]*v-for="slot in getPlayerItemSlots\(player\)"[\s\S]*<AssetHoverTooltip[\s\S]*v-if="slot\.url && !slot\.empty && slot\.itemId !== null"[\s\S]*:details="getItemTooltipDetails\(slot\.itemId\)!"/)
  assert.match(runesBlock, /class="trait-list"[\s\S]*<AssetHoverTooltip[\s\S]*v-if="slot\.url && !slot\.empty && getTraitTooltipDetails\(slot\)"[\s\S]*:details="getTraitTooltipDetails\(slot\)!"/)
  assert.match(source, /:title="getItemSlotLabel\(slot\)"/)
  assert.match(source, /:title="slot\.label"/)
  assert.match(overviewBlock, /class="item-row compact" aria-label="items"/)
  assert.match(overviewBlock, /:class="\{ empty: slot\.empty \}"/)
})

test('chart tab uses an honest empty state when timeline frames are unavailable', () => {
  const source = readInlineDetailSource()

  assert.match(source, /activeTabValue === 'chart'/)
  assert.match(source, /const hasTimelineData = computed\(\(\) => false\)/)
  assert.match(source, /matchDetail\.timelineEmptyTitle/)
  assert.match(source, /matchDetail\.timelineEmptyBody/)
  assert.match(source, /staticTeamGoldDiff/)
  assert.doesNotMatch(source, /polyline|fakeTimeline|mockTimeline|sampleTimeline/)
})

test('inline detail only renders lane positions for lane-based Summoner Rift modes', () => {
  const source = readInlineDetailSource()

  assert.match(source, /function isLaneBasedMode\(match: MatchHistory \| GameDetail \| null \| undefined\): boolean/)
  assert.match(source, /queueId/)
  assert.match(source, /gameMode/)
  assert.match(source, /queueName/)
  assert.match(source, /450/)
  assert.match(source, /ARAM/)
  assert.match(source, /CHERRY/)
  assert.match(source, /大乱斗/)
  assert.match(source, /海克斯大乱斗/)
  assert.match(source, /斗魂/)
  assert.match(source, /竞技场/)
  assert.match(source, /无限火力/)
  assert.match(source, /克隆/)
  assert.match(source, /RANKED_SOLO_5x5/)
  assert.match(source, /RANKED_FLEX_SR/)
  assert.match(source, /CLASSIC/)
  assert.match(source, /召唤师峡谷/)
  assert.match(source, /function getDisplayPosition\(player: MatchDetailParticipant\): string/)
  assert.match(source, /if \(!isLaneBasedMode\(displayGameDetail\.value \|\| props\.matchHistory\)\) \{[\s\S]*return ''/)
  assert.match(source, /v-if="getDisplayPosition\(player\)"/)
  assert.doesNotMatch(source, /\{\{ getPositionLabel\(player\) \}\}/)
})

test('inline detail does not render history player tags or empty-match badges', () => {
  const source = readInlineDetailSource()

  assert.doesNotMatch(source, /UserTagBadgeList/)
  assert.doesNotMatch(source, /userTagSummaries/)
  assert.doesNotMatch(source, /getPlayerSummary/)
  assert.doesNotMatch(source, /record-status/)
  assert.doesNotMatch(source, /badge\.empty|暂无对局|No Matches/)
})

test('kill participation label is localized and guards zero team kills', () => {
  const source = readInlineDetailSource()
  const zh = readFileSync(new URL('../../i18n/locales/zh-CN.ts', import.meta.url), 'utf8')
  const en = readFileSync(new URL('../../i18n/locales/en-US.ts', import.meta.url), 'utf8')

  assert.match(source, /t\('matchDetail\.killParticipation'\) \}\} \{\{ getKillParticipation\(player, team\) \}\}/)
  assert.doesNotMatch(source, />KP\s*\{\{ getKillParticipation/)
  assert.match(source, /if \(!team\.totals\.kills\) \{[\s\S]*return '--'/)
  assert.doesNotMatch(source, /NaN|Infinity/)
  assert.match(zh, /'matchDetail\.killParticipation': '参团'/)
  assert.match(en, /'matchDetail\.killParticipation': '(KP|Kill Participation)'/)
})

test('inline detail keeps a compact gap below the match card', () => {
  const source = readInlineDetailSource()
  const inlineStyleBlock = source.match(/\.inline-match-detail \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /class="inline-match-detail"/)
  assert.match(inlineStyleBlock, /margin-top:\s*8px/)
  assert.doesNotMatch(inlineStyleBlock, /margin-top:\s*-/)
})

test('player rows show segmented raw KDA score and localized participation without ratio suffix', () => {
  const source = readInlineDetailSource()
  const kdaCellBlock = source.match(/<div class="kda-cell">[\s\S]*?<\/div>/)?.[0] || ''

  assert.match(source, /function getPlayerKills\(player: MatchDetailParticipant\): number/)
  assert.match(source, /function getPlayerDeaths\(player: MatchDetailParticipant\): number/)
  assert.match(source, /function getPlayerAssists\(player: MatchDetailParticipant\): number/)
  assert.match(kdaCellBlock, /class="player-kda-score"/)
  assert.match(kdaCellBlock, /class="kda-kills"[\s\S]*getPlayerKills\(player\)/)
  assert.match(kdaCellBlock, /class="kda-deaths"[\s\S]*getPlayerDeaths\(player\)/)
  assert.match(kdaCellBlock, /class="kda-assists"[\s\S]*getPlayerAssists\(player\)/)
  assert.match(kdaCellBlock, /class="kda-separator">\/<\/span>/)
  assert.match(kdaCellBlock, /t\('matchDetail\.killParticipation'\)[\s\S]*getKillParticipation\(player, team\)/)
  assert.doesNotMatch(kdaCellBlock, /\(\d+(?:\.\d+)?\)/)
  assert.doesNotMatch(kdaCellBlock, /\{\{ getKdaScoreText\(player\) \}\}/)
  assert.doesNotMatch(source, /\$\{kills\}\/\$\{deaths\}\/\$\{assists\} \(\$\{kda\}\)/)
})

test('team headers show compact team KDA and objective icon strip without team gold', () => {
  const source = readInlineDetailSource()
  const headerBlock = source.match(/<header class="team-detail-header">[\s\S]*?<\/header>/)?.[0] || ''

  assert.doesNotMatch(headerBlock, /KDA/)
  assert.doesNotMatch(headerBlock, /goldEarned|common\.gold|金币/)
  assert.doesNotMatch(headerBlock, /65\.8k|team-gold-summary|getTeamGold|aria-label="team gold"/)
  assert.match(headerBlock, /class="team-kda-summary"/)
  assert.match(headerBlock, /\{\{ getTeamKda\(team\.totals\) \}\}/)
  assert.match(headerBlock, /class="team-header-resources"/)
  assert.match(headerBlock, /v-for="item in getTeamObjectiveItems\(team\.teamId\)"/)
  assert.match(headerBlock, /class="objective-pill compact-objective-pill"/)
  assert.match(headerBlock, /class="objective-icon objective-icon-img"[\s\S]*:src="icon\.iconUrl"/)
  assert.doesNotMatch(headerBlock, /team-structure-stats|team-structure-chip|getTeamStructureItems/)
  assert.doesNotMatch(headerBlock, /\{\{ item\.label \}\}/)
  assert.match(headerBlock, /\{\{ getObjectiveCountText\(item\) \}\}/)
  assert.match(source, /function getTeamKda\(totals: TeamStatsSummary\): string \{[\s\S]*return `\$\{totals\.kills\}\/\$\{totals\.deaths\}\/\$\{totals\.assists\}`/)
  assert.doesNotMatch(source, /function getTeamGold\(totals: TeamStatsSummary\)/)
  assert.doesNotMatch(source, /interface TeamStructureItem|function getTeamStructureItems/)
  assert.doesNotMatch(headerBlock, /team-kills|team-deaths|team-assists|team-kda-separator/)
  assert.doesNotMatch(source, /\.team-kills|\.team-deaths|\.team-assists|\.team-kda-separator/)
  assert.match(source, /class="number-cell gold-cell"/)
})

test('ranked overview team header renders draft bans and objective resources in the title row', () => {
  const source = readInlineDetailSource()
  const headerBlock = source.match(/<header class="team-detail-header">[\s\S]*?<\/header>/)?.[0] || ''

  assert.match(source, /const showDraftAndObjectiveSummary = computed\(\(\) => isRankedMode\(props\.matchHistory\) \|\| isRankedMode\(displayGameDetail\.value\)\)/)
  assert.match(source, /interface TeamSection \{[\s\S]*teamId: number/)
  assert.match(source, /createTeamSection\('blue', 100/)
  assert.match(source, /createTeamSection\('red', 200/)
  assert.match(headerBlock, /class="team-header-main"[\s\S]*<strong>[\s\S]*\{\{ team\.result \}\}[\s\S]*\{\{ team\.label \}\}[\s\S]*<\/strong>/)
  assert.match(headerBlock, /class="team-header-summary"[\s\S]*class="team-kda-summary"[\s\S]*\{\{ getTeamKda\(team\.totals\) \}\}[\s\S]*class="team-header-resources"[\s\S]*getTeamObjectiveItems\(team\.teamId\)/)
  assert.match(headerBlock, /v-if="showDraftAndObjectiveSummary && getTeamObjectiveItems\(team\.teamId\)\.length"[\s\S]*class="team-header-resources"[\s\S]*class="team-objective-icons"/)
  assert.match(headerBlock, /v-if="showDraftAndObjectiveSummary && getTeamBans\(team\.teamId\)\.length"[\s\S]*class="team-draft-row"/)
  assert.match(headerBlock, /class="draft-objective-label"[\s\S]*禁用/)
  assert.match(headerBlock, /v-if="getTeamBans\(team\.teamId\)\.length"[\s\S]*class="team-ban-icons"/)
  assert.match(headerBlock, /v-for="championId in getTeamBans\(team\.teamId\)"[\s\S]*class="ban-champion-icon"[\s\S]*:src="getChampionIconUrl\(championId\)"/)
  assert.match(source, /function getTeamBans\(teamId: number\): number\[\] \{[\s\S]*normalizePositiveInteger[\s\S]*slice\(0, 5\)/)
  assert.match(source, /\.ban-champion-icon::after \{[\s\S]*transform:\s*rotate\(-45deg\)/)
})

test('team header keeps title, KDA, objectives, and bans in one left-start flow', () => {
  const source = readInlineDetailSource()
  const headerBlock = source.match(/<header class="team-detail-header">[\s\S]*?<\/header>/)?.[0] || ''
  const mainRule = source.match(/\.team-header-main \{[\s\S]*?\n\}/)?.[0] || ''
  const summaryRule = source.match(/\.team-header-summary \{[\s\S]*?\n\}/)?.[0] || ''
  const draftRule = source.match(/\.team-draft-row,\s*\n\.team-draft-objective-row \{[\s\S]*?\n\}/)?.[0] || ''
  const iconRule = source.match(/\.team-ban-icons,\s*\n\.team-objective-icons \{[\s\S]*?\n\}/)?.[0] || ''
  const orderedSelectors = [
    '<strong>{{ team.result }}',
    'class="team-kda-summary"',
    'class="team-header-resources"',
    'class="team-draft-row"'
  ]
  const orderedIndexes = orderedSelectors.map(selector => headerBlock.indexOf(selector))

  assert.ok(orderedIndexes.every(index => index >= 0), `missing selector order: ${orderedIndexes.join(',')}`)
  assert.deepEqual([...orderedIndexes].sort((left, right) => left - right), orderedIndexes)
  assert.match(headerBlock, /class="team-header-summary"[\s\S]*class="team-kda-summary"[\s\S]*class="team-header-resources"[\s\S]*class="team-draft-row"/)
  assert.doesNotMatch(headerBlock, /team-gold-summary|getTeamGold/)
  assert.doesNotMatch(headerBlock, /team-structure-stats|team-structure-chip/)
  assert.doesNotMatch(headerBlock, /team-header-side|header-actions/)
  assert.match(mainRule, /justify-content:\s*flex-start/)
  assert.doesNotMatch(mainRule, /justify-content:\s*space-between/)
  assert.match(summaryRule, /justify-content:\s*flex-start/)
  assert.doesNotMatch(summaryRule, /margin-left:\s*auto|justify-content:\s*flex-end|text-align:\s*right/)
  assert.match(draftRule, /justify-content:\s*flex-start/)
  assert.match(iconRule, /justify-content:\s*flex-start/)
})

test('objective header icons use non-empty distinct URLs for non-generic objectives', () => {
  resetGameAssetResolverForTest()

  const dragonUrl = getObjectiveIconUrl('dragon')
  const baronUrl = getObjectiveIconUrl('baron')
  const heraldUrl = getObjectiveIconUrl('herald')
  const voidgrubUrl = getObjectiveIconUrl('voidgrub')
  const infernalUrl = getObjectiveIconUrl('infernal')
  const mountainUrl = getObjectiveIconUrl('mountain')
  const oceanUrl = getObjectiveIconUrl('ocean')
  const cloudUrl = getObjectiveIconUrl('cloud')
  const hextechUrl = getObjectiveIconUrl('hextech')
  const chemtechUrl = getObjectiveIconUrl('chemtech')
  const turretUrl = getObjectiveIconUrl('turret')
  const inhibitorUrl = getObjectiveIconUrl('inhibitor')
  const turretPlateUrl = getObjectiveIconUrl('turretPlate')
  const soulHextechUrl = getObjectiveIconUrl('soul-hextech')
  const soulChemtechUrl = getObjectiveIconUrl('soul-chemtech')

  for (const url of [
    heraldUrl,
    voidgrubUrl,
    infernalUrl,
    mountainUrl,
    oceanUrl,
    cloudUrl,
    hextechUrl,
    chemtechUrl,
    turretUrl,
    inhibitorUrl,
    turretPlateUrl,
    soulHextechUrl,
    soulChemtechUrl
  ]) {
    assert.ok(url)
    assert.doesNotMatch(url, /plugins\/rcp-fe-lol-match-history\/global\/default/i)
  }
  for (const url of [infernalUrl, mountainUrl, oceanUrl, cloudUrl, hextechUrl, chemtechUrl, soulHextechUrl, soulChemtechUrl]) {
    assert.doesNotMatch(url, /dragon_square_(?:hextech|chemtech)/i)
    assert.doesNotMatch(url, /(?:fire|earth|water|air)-100\.png/i)
  }
  assert.doesNotMatch(voidgrubUrl, /right_icons_grub/i)
  assert.notEqual(hextechUrl, dragonUrl)
  assert.notEqual(chemtechUrl, dragonUrl)
  assert.notEqual(voidgrubUrl, heraldUrl)
  assert.notEqual(voidgrubUrl, baronUrl)
})

test('draft and objective summary matches numeric and string team ids', () => {
  const source = readInlineDetailSource()
  const objectiveSummaryBlock = source.match(/function getTeamObjectiveSummary\(teamId: number\): TeamObjectiveSummary \| null \{[\s\S]*?\n\}/)?.[0] || ''
  const banSummaryBlock = source.match(/function getTeamBanSummary\(teamId: number\): TeamBanSummary \| null \{[\s\S]*?\n\}/)?.[0] || ''
  const normalizeNumberBlock = source.match(/function normalizeFiniteNumber\(value: unknown\): number \| null \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(source, /function normalizeTeamId\(value: unknown\): number \| null/)
  assert.match(normalizeNumberBlock, /typeof value === 'string'[\s\S]*Number\(value\.trim\(\)\)/)
  assert.match(objectiveSummaryBlock, /normalizeTeamId\(summary\?\.teamId\) === teamId/)
  assert.match(banSummaryBlock, /normalizeTeamId\(summary\?\.teamId\) === teamId/)
})

test('objective display builder keeps required header order and bans after objectives', () => {
  const source = readInlineDetailSource()
  const headerBlock = source.match(/<header class="team-detail-header">[\s\S]*?<\/header>/)?.[0] || ''
  const objectiveBuilderBlock = readFunctionBlock(
    source,
    'function buildObjectiveDisplayItems(teamId: number, summary: TeamObjectiveSummary): ObjectiveDisplayItem[]'
  )

  assertOrdered(objectiveBuilderBlock, [
    "addStructureObjectiveItem(items, teamId, 'turret', 'turret', '塔', readStructureObjectiveCount(teamId, summary, 'turret'))",
    "addStructureObjectiveItem(items, teamId, 'inhibitor', 'inhibitor', '水晶', readStructureObjectiveCount(teamId, summary, 'inhibitor'))",
    "addStructureObjectiveItem(items, teamId, 'turret-plate', 'turretPlate', '镀层', readStructureObjectiveCount(teamId, summary, 'turretPlate'))",
    "addObjectiveItem(items, teamId, 'baron', 'baron', '男爵', readObjectiveCount(summary.baronKills))",
    "addObjectiveItem(items, teamId, 'elder', 'elder', '远古龙', readObjectiveCount(summary.elderDragonKills))",
    'addDragonObjectiveItems(items, teamId, summary)',
    "addObjectiveItem(items, teamId, 'herald', 'herald', '先锋', readObjectiveCount(summary.heraldKills))",
    "addObjectiveItem(items, teamId, 'voidgrub', 'voidgrub', '虚空巢虫', readObjectiveCount(summary.voidGrubKills))"
  ])
  assertOrdered(headerBlock, [
    'class="team-header-resources"',
    'class="team-draft-row"'
  ])
})

test('structure objective counts fall back from summary to objective events and participant stats', () => {
  const source = readInlineDetailSource()
  const countBlock = readFunctionBlock(
    source,
    "function readStructureObjectiveCount(teamId: number, summary: TeamObjectiveSummary, sourceKey: StructureObjectiveSourceKey): number | null"
  )
  const eventCountBlock = readFunctionBlock(
    source,
    'function countObjectiveEvents(summary: TeamObjectiveSummary, teamId: number, kind: TeamObjectiveEvent[\'kind\']): number | null'
  )
  const participantFallbackBlock = readFunctionBlock(
    source,
    'function sumTeamParticipantObjectiveStats(teamId: number, fieldKeys: string[]): number | null'
  )

  assert.match(countBlock, /readNullableObjectiveCount\(summary\[source\.summaryKey\]\)/)
  assert.match(countBlock, /countObjectiveEvents\(summary, teamId, source\.eventKind\)/)
  assert.match(countBlock, /sumTeamParticipantObjectiveStats\(teamId, source\.directStatKeys\)/)
  assert.match(countBlock, /sumTeamParticipantObjectiveStats\(teamId, source\.lastFallbackStatKeys\)/)
  assert.match(eventCountBlock, /matchesObjectiveEvent\(event, \{ kind \}, teamId\)/)
  assert.match(participantFallbackBlock, /for \(const player of allPlayers\.value\)[\s\S]*normalizeTeamId\(player\.teamId\) !== teamId/)
  assert.match(participantFallbackBlock, /readParticipantObjectiveStat\(player, fieldKeys\)/)
  assert.match(source, /turret:[\s\S]*summaryKey: 'turretKills'[\s\S]*eventKind: 'turret'[\s\S]*directStatKeys: \['turretKills'\][\s\S]*lastFallbackStatKeys: \['turretTakedowns'\]/)
  assert.match(source, /inhibitor:[\s\S]*summaryKey: 'inhibitorKills'[\s\S]*eventKind: 'inhibitor'[\s\S]*directStatKeys: \['inhibitorKills'\][\s\S]*lastFallbackStatKeys: \['inhibitorTakedowns'\]/)
  assert.match(source, /turretPlate:[\s\S]*summaryKey: 'turretPlateKills'[\s\S]*eventKind: 'turretPlate'[\s\S]*directStatKeys: \['turretPlatesTaken'\][\s\S]*lastFallbackStatKeys: \[\]/)
  assert.match(source, /function readParticipantObjectiveField\(player: MatchDetailParticipant, key: string\): number \| null[\s\S]*readStatNumber\(player, key\)[\s\S]*player\.stats\?\.challenges/)
})

test('missing turret plate fields stay unknown instead of rendering a synthetic zero', () => {
  const source = readInlineDetailSource()
  const headerBlock = source.match(/<header class="team-detail-header">[\s\S]*?<\/header>/)?.[0] || ''
  const countBlock = readFunctionBlock(
    source,
    "function readStructureObjectiveCount(teamId: number, summary: TeamObjectiveSummary, sourceKey: StructureObjectiveSourceKey): number | null"
  )
  const structureItemBlock = readFunctionBlock(
    source,
    'function addStructureObjectiveItem('
  )

  assert.match(countBlock, /readNullableObjectiveCount\(summary\[source\.summaryKey\]\)/)
  assert.match(countBlock, /return null/)
  assert.doesNotMatch(countBlock, /return sumTeamParticipantObjectiveStats\(teamId, source\.lastFallbackStatKeys\)/)
  assert.match(structureItemBlock, /count: number \| null/)
  assert.match(structureItemBlock, /formatObjectiveTitle/)
  assert.match(headerBlock, /\{\{ getObjectiveCountText\(item\) \}\}/)
  assert.doesNotMatch(headerBlock, /\{\{ item\.count \}\}/)
  assert.match(source, /function readNullableObjectiveCount\(value: unknown\): number \| null/)
  assert.match(source, /turretPlate:[\s\S]*directStatKeys: \['turretPlatesTaken'\][\s\S]*lastFallbackStatKeys: \[\]/)
})

test('structure objective tooltips use actor ownership before event team id and fall back to participant stats', () => {
  const source = readInlineDetailSource()
  const tooltipBlock = readFunctionBlock(
    source,
    'function getObjectiveTooltipGroups(teamId: number, itemKey: string, itemLabel: string): ObjectiveTooltipGroup[]'
  )
  const matchesBlock = readFunctionBlock(
    source,
    'function matchesObjectiveEvent('
  )
  const ownerBlock = readFunctionBlock(
    source,
    'function getObjectiveEventOwnerTeamId(event: TeamObjectiveEvent): number | null'
  )
  const participantGroupsBlock = readFunctionBlock(
    source,
    'function getParticipantObjectiveTooltipGroups(teamId: number, itemKey: string, itemLabel: string): ObjectiveTooltipGroup[]'
  )

  assert.match(tooltipBlock, /getParticipantObjectiveTooltipGroups\(teamId, itemKey, itemLabel\)/)
  assert.match(matchesBlock, /getObjectiveEventOwnerTeamId\(event\)/)
  assert.doesNotMatch(matchesBlock, /eventTeamId !== null && eventTeamId !== teamId/)
  assert.match(ownerBlock, /event\.participantId/)
  assert.match(ownerBlock, /participant\?\.teamId/)
  assert.match(ownerBlock, /event\.teamId/)
  assert.match(participantGroupsBlock, /getObjectiveParticipantStatKeys\(itemKey\)/)
  assert.match(participantGroupsBlock, /readParticipantObjectiveStat\(player, fieldKeys\)/)
  assert.match(source, /turret:[\s\S]*directStatKeys: \['turretKills'\]/)
  assert.match(source, /inhibitor:[\s\S]*directStatKeys: \['inhibitorKills'\]/)
  assert.match(source, /turretPlate:[\s\S]*directStatKeys: \['turretPlatesTaken'\]/)
})

test('ranked overview objective pills render structure icon counts and keep an inline svg fallback', () => {
  const source = readInlineDetailSource()
  const headerBlock = source.match(/<header class="team-detail-header">[\s\S]*?<\/header>/)?.[0] || ''
  const objectiveBuilderBlock = readFunctionBlock(
    source,
    'function buildObjectiveDisplayItems(teamId: number, summary: TeamObjectiveSummary): ObjectiveDisplayItem[]'
  )

  assert.match(headerBlock, /v-if="showDraftAndObjectiveSummary && getTeamObjectiveItems\(team\.teamId\)\.length"[\s\S]*class="team-header-resources"[\s\S]*class="team-objective-icons"/)
  assert.match(headerBlock, /v-for="item in getTeamObjectiveItems\(team\.teamId\)"[\s\S]*class="objective-pill compact-objective-pill"/)
  assert.match(headerBlock, /:title="item\.title"/)
  assert.match(headerBlock, /:aria-label="item\.title"/)
  assert.match(headerBlock, /tabindex="0"/)
  assert.match(headerBlock, /v-for="icon in getObjectiveItemIcons\(item\)"[\s\S]*v-if="shouldUseObjectiveIconImage\(icon\)"[\s\S]*class="objective-icon objective-icon-img"[\s\S]*:src="icon\.iconUrl"/)
  assert.match(headerBlock, /@error="handleObjectiveIconLoadFailed\(\$event, icon\.key\)"/)
  assert.match(headerBlock, /v-else[\s\S]*class="objective-icon objective-fallback-icon"/)
  assert.match(headerBlock, /<svg[\s\S]*viewBox="0 0 16 16"[\s\S]*aria-hidden="true"[\s\S]*focusable="false"/)
  assert.match(headerBlock, /<strong[\s\S]*v-if="item\.showCount"[\s\S]*class="objective-count"[\s\S]*\{\{ getObjectiveCountText\(item\) \}\}/)
  assert.match(headerBlock, /class="objective-tooltip"[\s\S]*role="tooltip"[\s\S]*v-for="group in item\.tooltipGroups"/)
  assert.match(headerBlock, /class="objective-tooltip-avatar"[\s\S]*:src="getChampionIconUrl\(group\.championId\)"/)
  assert.match(headerBlock, /class="objective-tooltip-count"[\s\S]*group\.count/)
  assert.doesNotMatch(headerBlock, /class="objective-pill compact-objective-pill"[\s\S]*\{\{ item\.label \}\}/)
  assert.doesNotMatch(headerBlock, /class="team-structure-chip"[\s\S]*\{\{ item\.label \}\}[\s\S]*\{\{ item\.count \}\}/)
  assert.doesNotMatch(headerBlock, /x\{\{ item\.count \}\}|男爵x|小龙x/)
  assert.match(source, /import \{[\s\S]*getObjectiveIconUrl/)
  assert.match(source, /type ObjectiveIconKind/)
  assert.match(source, /turretKills/)
  assert.match(source, /turretPlateKills/)
  assert.match(source, /inhibitorKills/)
  assert.match(source, /objectiveEvents/)
  assert.match(source, /baronKills/)
  assert.match(source, /heraldKills/)
  assert.match(source, /voidGrubKills/)
  assert.match(source, /dragonKills/)
  assert.match(source, /dragonKillsByType/)
  assert.match(source, /elderDragonKills/)
  assert.match(source, /interface ObjectiveDisplayItem \{[\s\S]*count: number \| null[\s\S]*showCount: boolean[\s\S]*title: string[\s\S]*tooltipGroups: ObjectiveTooltipGroup\[\]/)
  assert.match(source, /interface ObjectiveTooltipGroup \{[\s\S]*championId: number[\s\S]*count: number/)
  assert.match(source, /function getTeamObjectiveItems\(teamId: number\): ObjectiveDisplayItem\[\] \{[\s\S]*buildObjectiveDisplayItems\(teamId, summary\)/)
  assert.match(source, /function addStructureObjectiveItem\(/)
  assert.match(objectiveBuilderBlock, /addStructureObjectiveItem\(items, teamId, 'turret', 'turret', '塔', readStructureObjectiveCount\(teamId, summary, 'turret'\)\)/)
  assert.match(objectiveBuilderBlock, /addStructureObjectiveItem\(items, teamId, 'inhibitor', 'inhibitor', '水晶', readStructureObjectiveCount\(teamId, summary, 'inhibitor'\)\)/)
  assert.match(objectiveBuilderBlock, /addStructureObjectiveItem\(items, teamId, 'turret-plate', 'turretPlate', '镀层', readStructureObjectiveCount\(teamId, summary, 'turretPlate'\)\)/)
  assert.match(source, /iconUrl: getObjectiveIconUrl\(kind\)/)
  assert.match(source, /addObjectiveItem\(items, teamId, 'baron', 'baron', '男爵', readObjectiveCount\(summary\.baronKills\)\)/)
  assert.match(source, /addObjectiveItem\(items, teamId, 'herald', 'herald', '先锋', readObjectiveCount\(summary\.heraldKills\)\)/)
  assert.match(source, /addObjectiveItem\(items, teamId, 'voidgrub', 'voidgrub', '虚空巢虫', readObjectiveCount\(summary\.voidGrubKills\)\)/)
  assert.match(source, /addObjectiveItem\(items, teamId, 'elder', 'elder', '远古龙', readObjectiveCount\(summary\.elderDragonKills\)\)/)
  assert.doesNotMatch(objectiveBuilderBlock, /addDragonSoulItem/)
  assert.match(source, /function getObjectiveTooltipGroups\(/)
  assert.match(source, /kind === 'dragon'[\s\S]*normalizeDragonTypeKey\(event\.subType\)/)
  assert.match(source, /showCount: true/)
  assert.match(source, /showCount: false/)
  assert.match(source, /function formatObjectiveTitle\(label: string, count: number \| null\): string/)
  assert.doesNotMatch(headerBlock, /🐉|🐲|🔥|🌊|⛰|🌪|⚡|🧪/)
})

test('dragon objective renders as one pill with icons ordered by event timestamp and aggregate fallback', () => {
  const source = readInlineDetailSource()
  const headerBlock = source.match(/<header class="team-detail-header">[\s\S]*?<\/header>/)?.[0] || ''
  const dragonBuilderBlock = readFunctionBlock(
    source,
    'function addDragonObjectiveItems(items: ObjectiveDisplayItem[], teamId: number, summary: TeamObjectiveSummary): void'
  )
  const timelineIconsBlock = readFunctionBlock(
    source,
    'function getDragonTimelineObjectiveIcons(teamId: number, summary: TeamObjectiveSummary): ObjectiveDisplayIcon[]'
  )
  const fallbackIconsBlock = readFunctionBlock(
    source,
    'function getFallbackDragonObjectiveIcons(teamId: number, summary: TeamObjectiveSummary): ObjectiveDisplayIcon[]'
  )
  const sortedEventsBlock = readFunctionBlock(
    source,
    'function getSortedTimestampedDragonEvents(summary: TeamObjectiveSummary, teamId: number): TimestampedDragonObjectiveEvent[]'
  )

  assert.match(source, /interface ObjectiveDisplayIcon \{[\s\S]*timestamp: number \| null/)
  assert.match(source, /interface ObjectiveDisplayItem \{[\s\S]*icons\?: ObjectiveDisplayIcon\[\]/)
  assert.match(headerBlock, /v-for="icon in getObjectiveItemIcons\(item\)"/)
  assert.match(headerBlock, /:key="icon\.key"/)
  assert.match(headerBlock, /:src="icon\.iconUrl"/)
  assert.match(dragonBuilderBlock, /const icons = getDragonTimelineObjectiveIcons\(teamId, summary\)/)
  assert.match(dragonBuilderBlock, /icons\.length \? icons : getFallbackDragonObjectiveIcons\(teamId, summary\)/)
  assert.match(dragonBuilderBlock, /addDragonObjectiveItem\(items, teamId, dragonIcons\)/)
  assert.doesNotMatch(dragonBuilderBlock, /items\.push\(\.\.\.dragonTimelineItems\)/)
  assert.match(timelineIconsBlock, /getSortedTimestampedDragonEvents\(summary, teamId\)/)
  assert.match(sortedEventsBlock, /\.sort\(\(left, right\) => left\.timestamp - right\.timestamp \|\| left\.index - right\.index\)/)
  assert.match(fallbackIconsBlock, /normalizeDragonKillsByType\(summary\.dragonKillsByType\)/)
  assert.match(fallbackIconsBlock, /DRAGON_TYPE_ORDER/)
  assert.match(fallbackIconsBlock, /DRAGON_GROUP_LABEL,[\s\S]*null/)
})

test('objective item builder uses dragon event timestamps before falling back to aggregate dragon counts', () => {
  const source = readInlineDetailSource()
  const dragonBuilderBlock = readFunctionBlock(
    source,
    'function addDragonObjectiveItems(items: ObjectiveDisplayItem[], teamId: number, summary: TeamObjectiveSummary): void'
  )
  const timelineIconsBlock = readFunctionBlock(
    source,
    'function getDragonTimelineObjectiveIcons(teamId: number, summary: TeamObjectiveSummary): ObjectiveDisplayIcon[]'
  )
  const fallbackIconsBlock = readFunctionBlock(
    source,
    'function getFallbackDragonObjectiveIcons(teamId: number, summary: TeamObjectiveSummary): ObjectiveDisplayIcon[]'
  )
  const sortedEventsBlock = readFunctionBlock(
    source,
    'function getSortedTimestampedDragonEvents(summary: TeamObjectiveSummary, teamId: number): TimestampedDragonObjectiveEvent[]'
  )
  const dragonTypeLabelsBlock = source.match(/const DRAGON_TYPE_LABELS: Record<DragonType, string> = \{[\s\S]*?\n\}/)?.[0] || ''

  assert.match(dragonTypeLabelsBlock, /infernal: '炼狱龙'/)
  assert.match(dragonTypeLabelsBlock, /mountain: '山脉龙'/)
  assert.match(dragonTypeLabelsBlock, /ocean: '海洋龙'/)
  assert.match(dragonTypeLabelsBlock, /cloud: '云端龙'/)
  assert.match(dragonTypeLabelsBlock, /hextech: '海克斯龙'/)
  assert.match(dragonTypeLabelsBlock, /chemtech: '炼金龙'/)
  assert.match(dragonTypeLabelsBlock, /unknown: '小龙'/)
  assert.match(dragonBuilderBlock, /const dragonIcons = icons\.length \? icons : getFallbackDragonObjectiveIcons\(teamId, summary\)/)
  assert.match(dragonBuilderBlock, /const icons = getDragonTimelineObjectiveIcons\(teamId, summary\)/)
  assert.match(dragonBuilderBlock, /addDragonObjectiveItem\(items, teamId, dragonIcons\)/)
  assert.match(fallbackIconsBlock, /normalizeDragonKillsByType\(summary\.dragonKillsByType\)/)
  assert.match(timelineIconsBlock, /getSortedTimestampedDragonEvents\(summary, teamId\)/)
  assert.match(timelineIconsBlock, /totalDragonKills > 0 && events\.length < totalDragonKills/)
  assert.match(timelineIconsBlock, /createObjectiveDisplayIcon/)
  assert.match(source, /function addDragonObjectiveItem\(items: ObjectiveDisplayItem\[\], teamId: number, icons: ObjectiveDisplayIcon\[\]\): void/)
  assert.match(source, /iconUrl: getObjectiveIconUrl\(kind\)/)
  assert.match(sortedEventsBlock, /summary\.objectiveEvents/)
  assert.match(sortedEventsBlock, /event\.kind !== 'dragon'/)
  assert.match(sortedEventsBlock, /normalizeFiniteNumber\(event\.timestamp\)/)
  assert.match(sortedEventsBlock, /\.sort\(\(left, right\) => left\.timestamp - right\.timestamp \|\| left\.index - right\.index\)/)
  assert.doesNotMatch(dragonBuilderBlock, /dragon-remaining|unknownDragon[\s\S]*totalDragonKills - typedDragonTotal/)
  assert.doesNotMatch(dragonBuilderBlock, /typedDragonTotal > 0[\s\S]*addObjectiveItem\(items, teamId, 'dragon'/)
  assert.match(source, /function addObjectiveItem\([\s\S]*if \(count <= 0\) \{[\s\S]*return/)
  assert.doesNotMatch(source, /addDragonSoulItem\(items/)
})

test('objective pills normalize dragon type aliases before building tooltip titles', () => {
  const source = readInlineDetailSource()
  const normalizeKeyBlock = source.match(/function normalizeDragonTypeKey\(value: unknown\): DragonType \| 'unknown' \{[\s\S]*?\n\}/)?.[0] || ''
  const normalizeKillsBlock = source.match(/function normalizeDragonKillsByType\([\s\S]*?\n\}/)?.[0] || ''
  const headerBlock = source.match(/<header class="team-detail-header">[\s\S]*?<\/header>/)?.[0] || ''

  assert.match(source, /const DRAGON_TYPE_ALIASES: Record<Exclude<DragonType, 'unknown'>, string\[\]>/)
  for (const alias of [
    'infernal',
    'fire',
    'fire_dragon',
    'FIRE_DRAGON',
    'INFERNAL_DRAGON',
    'ocean',
    'water',
    'water_dragon',
    'WATER_DRAGON',
    'mountain',
    'earth',
    'EARTH_DRAGON',
    'MOUNTAIN_DRAGON',
    'cloud',
    'air',
    'AIR_DRAGON',
    'CLOUD_DRAGON',
    'hextech',
    'HEXTECH_DRAGON',
    'chemtech',
    'CHEMTECH_DRAGON'
  ]) {
    assert.match(source, new RegExp(`['"]${alias}['"]`))
  }
  assert.match(normalizeKeyBlock, /return 'unknown'/)
  assert.match(normalizeKillsBlock, /Object\.entries\(source/)
  assert.match(normalizeKillsBlock, /const dragonType = normalizeDragonTypeKey\(rawType\)/)
  assert.match(normalizeKillsBlock, /result\[dragonType\] = \(result\[dragonType\] \|\| 0\) \+ count/)
  assert.doesNotMatch(normalizeKillsBlock, /source\[type\]/)
  assert.match(headerBlock, /class="objective-icon objective-icon-img"[\s\S]*:src="icon\.iconUrl"[\s\S]*alt=""/)
  assert.match(headerBlock, /class="objective-icon objective-fallback-icon"[\s\S]*:class="`objective-fallback-\$\{icon\.kind\}`"/)
  assert.match(headerBlock, /:title="item\.title"/)
})

test('draft and objective summary is hidden for ARAM and CHERRY even when data exists', () => {
  const source = readInlineDetailSource()
  const rankedHelperBlock = source.match(/function isRankedMode\(match: MatchHistory \| GameDetail \| null \| undefined\): boolean \{[\s\S]*?\n\}/)?.[0] || ''
  const headerBlock = source.match(/<header class="team-detail-header">[\s\S]*?<\/header>/)?.[0] || ''

  assert.match(source, /const NON_LANE_BASED_QUEUE_IDS = new Set\(\[450, 900, 1020, 1700, 1710\]\)/)
  assert.match(source, /const NON_LANE_BASED_GAME_MODES = new Set\(\['ARAM', 'CHERRY'\]\)/)
  assert.match(source, /'大乱斗'/)
  assert.match(source, /'斗魂'/)
  assert.match(source, /'竞技场'/)
  assert.match(source, /const showDraftAndObjectiveSummary = computed\(\(\) => isRankedMode\(props\.matchHistory\) \|\| isRankedMode\(displayGameDetail\.value\)\)/)
  assert.match(headerBlock, /v-if="showDraftAndObjectiveSummary && getTeamObjectiveItems\(team\.teamId\)\.length"/)
  assert.doesNotMatch(rankedHelperBlock, /450|ARAM|CHERRY|大乱斗|斗魂|竞技场/)
  assert.doesNotMatch(source, /hasDraftOrObjectives\(team\.teamId\)/)
})

test('overview localizes items header and shows vision score only for ranked modes', () => {
  const source = readInlineDetailSource()
  const zh = readFileSync(new URL('../../i18n/locales/zh-CN.ts', import.meta.url), 'utf8')
  const en = readFileSync(new URL('../../i18n/locales/en-US.ts', import.meta.url), 'utf8')
  const headerBlock = source.match(/<div class="team-row-labels"[\s\S]*?<\/div>/)?.[0] || ''
  const overviewBlock = source.match(/<div v-if="activeTabValue === 'overview'"[\s\S]*?<div v-else-if="activeTabValue === 'runes'"/)?.[0] || ''
  const baseGridBlock = source.match(/\.team-row-labels,\s*\n\.participant-row \{[\s\S]*?\n\}/)?.[0] || ''
  const rankedGridBlock = source.match(/\.team-detail-table\.with-vision-score \.team-row-labels,[\s\S]*?\n\}/)?.[0] || ''
  const rankedHelperBlock = source.match(/function isRankedMode\(match: MatchHistory \| GameDetail \| null \| undefined\): boolean \{[\s\S]*?\n\}/)?.[0] || ''

  assert.doesNotMatch(headerBlock, />Items<\/span>|>ITEMS<\/span>/)
  assert.match(headerBlock, /v-if="showVisionScoreColumn"[\s\S]*class="vision-score-head"[\s\S]*t\('matchDetail\.visionScore'\)[\s\S]*class="items-head"[\s\S]*t\('matchDetail\.itemsTab'\)/)
  assert.match(zh, /'matchDetail\.itemsTab': '装备'/)
  assert.match(en, /'matchDetail\.itemsTab': 'Items'/)
  assert.match(zh, /'matchDetail\.visionScore': '视野得分'/)
  assert.match(en, /'matchDetail\.visionScore': 'Vision'/)
  assert.match(source, /const RANKED_QUEUE_IDS = new Set\(\[420, 440\]\)/)
  assert.match(source, /const RANKED_QUEUE_KEYWORDS = \[[\s\S]*'排位'[\s\S]*'单排'[\s\S]*'双排'[\s\S]*'灵活'[\s\S]*'RANKED'[\s\S]*'SOLO'[\s\S]*'FLEX'[\s\S]*\]/)
  assert.match(rankedHelperBlock, /RANKED_QUEUE_IDS\.has\(queueId\)/)
  assert.match(rankedHelperBlock, /containsModeKeyword\(queueName, RANKED_QUEUE_KEYWORDS\)/)
  assert.match(rankedHelperBlock, /return false/)
  assert.doesNotMatch(rankedHelperBlock, /ARAM|CHERRY|匹配|大乱斗|斗魂|竞技场/)
  assert.match(source, /const showVisionScoreColumn = computed\(\(\) => isRankedMode\(props\.matchHistory\) \|\| isRankedMode\(displayGameDetail\.value\)\)/)
  assert.match(source, /function getVisionScoreText\(player: MatchDetailParticipant\): string \{[\s\S]*const value = readStatNumber\(player, 'visionScore'\)[\s\S]*return value === null \? '--' : formatNumber\(value\)/)
  assert.match(overviewBlock, /'with-vision-score': showVisionScoreColumn/)
  assert.match(overviewBlock, /class="number-cell gold-cell"[\s\S]*v-if="showVisionScoreColumn"[\s\S]*class="number-cell vision-score-cell"[\s\S]*class="item-row compact"/)
  assert.match(source, /<span\s+v-if="showVisionScoreColumn"\s+class="number-cell vision-score-cell">\s*\{\{ getVisionScoreText\(player\) \}\}\s*<\/span>/)
  assert.match(source, /\.items-head,[\s\S]*\.vision-score-head \{[\s\S]*text-transform:\s*none/)
  assert.match(baseGridBlock, /minmax\(52px, 0\.38fr\)[\s\S]*minmax\(154px, 0\.9fr\)/)
  assert.doesNotMatch(baseGridBlock, /minmax\(58px, 0\.38fr\)/)
  assert.match(rankedGridBlock, /minmax\(52px, 0\.38fr\)[\s\S]*minmax\(58px, 0\.38fr\)[\s\S]*minmax\(154px, 0\.9fr\)/)
  assert.match(overviewBlock, /<div class="item-row compact" aria-label="items">[\s\S]*v-for="slot in getPlayerItemSlots\(player\)"[\s\S]*class="item-slot"[\s\S]*<img v-if="slot\.url"/)
})

test('overview highlights top kills, deaths, and assists separately instead of top KDA ratio', () => {
  const source = readInlineDetailSource()
  const kdaCellBlock = source.match(/<div class="kda-cell">[\s\S]*?<\/div>/)?.[0] || ''

  assert.match(source, /const topKillValue = computed\(\(\) => getTopMetricValue\(allPlayers\.value, player => readStatNumber\(player, 'kills'\)\)\)/)
  assert.match(source, /const topDeathValue = computed\(\(\) => getTopMetricValue\(allPlayers\.value, player => readStatNumber\(player, 'deaths'\)\)\)/)
  assert.match(source, /const topAssistValue = computed\(\(\) => getTopMetricValue\(allPlayers\.value, player => readStatNumber\(player, 'assists'\)\)\)/)
  assert.match(source, /function getTopMetricValue\([\s\S]*value > 0[\s\S]*Math\.max\(\.\.\.values\)/)
  assert.match(source, /function isTopKillPlayer\(player: MatchDetailParticipant\): boolean \{[\s\S]*return isTopMetricPlayer\(player, topKillValue\.value, target => readStatNumber\(target, 'kills'\)\)/)
  assert.match(source, /function isTopDeathPlayer\(player: MatchDetailParticipant\): boolean \{[\s\S]*return isTopMetricPlayer\(player, topDeathValue\.value, target => readStatNumber\(target, 'deaths'\)\)/)
  assert.match(source, /function isTopAssistPlayer\(player: MatchDetailParticipant\): boolean \{[\s\S]*return isTopMetricPlayer\(player, topAssistValue\.value, target => readStatNumber\(target, 'assists'\)\)/)
  assert.match(kdaCellBlock, /class="kda-kills"[\s\S]*'top-kills': isTopKillPlayer\(player\)[\s\S]*getPlayerKills\(player\)/)
  assert.match(kdaCellBlock, /class="kda-deaths"[\s\S]*'top-deaths': isTopDeathPlayer\(player\)[\s\S]*getPlayerDeaths\(player\)/)
  assert.match(kdaCellBlock, /class="kda-assists"[\s\S]*'top-assists': isTopAssistPlayer\(player\)[\s\S]*getPlayerAssists\(player\)/)
  assert.match(kdaCellBlock, /t\('matchDetail\.killParticipation'\)[\s\S]*getKillParticipation\(player, team\)/)
  assert.doesNotMatch(kdaCellBlock, /killParticipation[\s\S]*top-kills|killParticipation[\s\S]*top-deaths|killParticipation[\s\S]*top-assists/)
  assert.doesNotMatch(source, /topKdaValue|top-kda-value|isTopKdaPlayer|getPlayerKdaRatio|calculateKda/)
  assert.match(source, /\.top-kills \{[\s\S]*#ef6f7a/)
  assert.match(source, /\.top-deaths \{[\s\S]*#f0c05a/)
  assert.match(source, /\.top-assists \{[\s\S]*#62d49e/)
})

test('overview marks only positive top damage, taken, and gold values with inline svg icons', () => {
  const source = readInlineDetailSource()
  const overviewBlock = source.match(/<div v-if="activeTabValue === 'overview'"[\s\S]*?<div v-else-if="activeTabValue === 'runes'"/)?.[0] || ''

  assert.match(source, /function getTopMetricValue\([\s\S]*value > 0[\s\S]*Math\.max\(\.\.\.values\)/)
  assert.match(source, /const topDamageValue = computed\(\(\) => getTopMetricValue\(allPlayers\.value, player => readStatNumber\(player, 'totalDamageDealtToChampions'\)\)\)/)
  assert.match(source, /const topTakenValue = computed\(\(\) => getTopMetricValue\(allPlayers\.value, player => readStatNumber\(player, 'totalDamageTaken'\)\)\)/)
  assert.match(source, /const topGoldValue = computed\(\(\) => getTopMetricValue\(allPlayers\.value, player => readStatNumber\(player, 'goldEarned'\)\)\)/)
  assert.match(source, /function isTopDamagePlayer\(player: MatchDetailParticipant\): boolean/)
  assert.match(source, /function isTopTakenPlayer\(player: MatchDetailParticipant\): boolean/)
  assert.match(source, /function isTopGoldPlayer\(player: MatchDetailParticipant\): boolean/)
  assert.match(overviewBlock, /class="top-metric-icon top-damage-icon"[\s\S]*v-if="isTopDamagePlayer\(player\)"[\s\S]*title="全场最高伤害"[\s\S]*aria-label="全场最高伤害"[\s\S]*<svg[\s\S]*viewBox="0 0 16 16"[\s\S]*<path/)
  assert.match(overviewBlock, /class="top-metric-icon top-taken-icon"[\s\S]*v-if="isTopTakenPlayer\(player\)"[\s\S]*title="全场最高承伤"[\s\S]*aria-label="全场最高承伤"[\s\S]*<svg[\s\S]*viewBox="0 0 16 16"[\s\S]*<path/)
  assert.match(overviewBlock, /class="top-metric-icon top-gold-icon"[\s\S]*v-if="isTopGoldPlayer\(player\)"[\s\S]*title="全场最高金币"[\s\S]*aria-label="全场最高金币"[\s\S]*<svg[\s\S]*viewBox="0 0 16 16"[\s\S]*<circle[\s\S]*<path/)
  assert.doesNotMatch(overviewBlock, /🔥|🛡|🪙/)
  assert.match(source, /\.metric-value-with-icon \{[\s\S]*inline-flex/)
  assert.match(source, /\.top-metric-icon \{[\s\S]*width:\s*12px[\s\S]*height:\s*12px/)
  assert.match(source, /\.top-metric-icon svg \{[\s\S]*fill:\s*currentColor/)
  assert.match(source, /\.top-damage-icon \{[\s\S]*#ff7a45/)
  assert.match(source, /\.top-taken-icon \{[\s\S]*#7bb7ff/)
  assert.match(source, /\.top-gold-icon \{[\s\S]*#f0c05a/)
})
