import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('OP.GG champion panel renders reusable loading, success, error, and empty states', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /v-if="loading"/)
  assert.match(source, /v-else-if="error"/)
  assert.match(source, /v-else-if="!detail"/)
  assert.match(source, /v-else/)
  assert.match(source, /@click="\$emit\('retry'\)"/)
  assert.doesNotMatch(source, /opgg-modal-overlay/)
  assert.doesNotMatch(source, /aria-modal="true"/)
})

test('OP.GG champion panel keeps the existing core stats and build sections', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /formatPercent\(detail\.stats\.winRate\)/)
  assert.match(source, /formatPercent\(detail\.stats\.pickRate\)/)
  assert.match(source, /formatPercent\(detail\.stats\.banRate\)/)
  assert.match(source, /formatNumber\(detail\.stats\.kda\)/)
  assert.match(source, /summonerSpells/)
  assert.match(source, /augments/)
  assert.match(source, /runes/)
  assert.match(source, /skillOrders/)
  assert.match(source, /starterItems/)
  assert.match(source, /boots/)
  assert.match(source, /coreItems/)
  assert.match(source, /lastItems/)
  assert.match(source, /第四\/五\/六件装备/)
  assert.match(source, /getIconUrl\(section\.iconType, id\)/)
  assert.match(source, /getAugmentIconUrl/)
  assert.match(source, /if \(iconType === 'augment'\) return getAugmentIconUrl\(id\)/)
})

test('OP.GG champion panel only shows augment sections for arena mode', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')
  const buildSections = source.match(/const buildSections = computed<BuildSection\[\]>\(\(\) => \{[\s\S]*?\n\}\)/)?.[0] || ''

  assert.match(buildSections, /if \(detail\.mode === 'arena'\)/)
  assert.match(buildSections, /sections\.push\(\{ key: 'augments'/)
})

test('OP.GG champion panel shows counter champion icons in the detail header', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /counters\?: OpggChampionCounter\[\]/)
  assert.match(source, /counterChampions/)
  assert.match(source, /劣势对位/)
  assert.match(source, /class="opgg-counter-strip"/)
  assert.match(source, /getChampionIconUrl\(counter\.championId\)/)
})

test('OP.GG champion panel can show the back button inline with the detail title', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /showBackButton\?: boolean/)
  assert.match(source, /back: \[\]/)
  assert.match(source, /opgg-title-heading/)
  assert.match(source, /opgg-inline-back-btn/)
  assert.match(source, /v-if="showBackButton"/)
  assert.match(source, /@click="\$emit\('back'\)"/)
})

test('OP.GG champion panel can render the selected champion name as the detail title', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /title\?: string/)
  assert.match(source, /const panelTitle = computed/)
  assert.match(source, /props\.title\?\.trim\(\)/)
  assert.match(source, /props\.detail\?\.championName/)
  assert.match(source, /<h2 id="opgg-panel-title">\{\{ panelTitle \}\}<\/h2>/)
  assert.doesNotMatch(source, /<h2 id="opgg-panel-title">OP\.GG<\/h2>/)
})

test('OP.GG champion panel limits build rows to two until expanded', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /INITIAL_VISIBLE_BUILD_OPTIONS = 2/)
  assert.match(source, /visibleSectionOptions\(section\)/)
  assert.match(source, /section\.options\.slice\(0, INITIAL_VISIBLE_BUILD_OPTIONS\)/)
  assert.match(source, /@click="toggleSectionExpandedFromCard\(section\)"/)
  assert.match(source, /@keydown\.enter\.prevent="toggleSectionExpandedFromCard\(section\)"/)
  assert.match(source, /@keydown\.space\.prevent="toggleSectionExpandedFromCard\(section\)"/)
  assert.match(source, /:aria-expanded="canExpandSection\(section\) \? isSectionExpanded\(section\.key\) : undefined"/)
  assert.match(source, /opgg-section-expand-indicator/)
  assert.doesNotMatch(source, /<button[\s\S]*opgg-section-toggle/)
})

test('OP.GG champion panel renders fourth fifth and sixth items as three columns', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /LAST_ITEM_COLUMN_SIZE = 5/)
  assert.match(source, /LAST_ITEM_COLLAPSED_OPTIONS_PER_COLUMN = 2/)
  assert.match(source, /LAST_ITEM_COLUMN_TITLES = \['第四件装备', '第五件装备', '第六件装备'\]/)
  assert.match(source, /function lastItemColumns\(section: BuildSection\)/)
  assert.match(source, /section\.options\.slice\(start, start \+ LAST_ITEM_COLUMN_SIZE\)/)
  assert.match(source, /function visibleLastItemColumnOptions/)
  assert.match(source, /class="opgg-last-items-grid"/)
  assert.match(source, /class="opgg-last-item-column"/)
  assert.match(source, /class="opgg-last-item-list"/)
  assert.match(source, /class="opgg-last-item-metrics"/)
  assert.match(source, /formatPercent\(option\.winRate\)/)
  assert.match(source, /formatLastItemGames\(option\.games\)/)
  assert.doesNotMatch(source, /section\.key === 'lastItems'[\s\S]*opgg-build-list/)
})

test('OP.GG champion panel renders runes as icon-only primary and secondary rows', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /splitOpggRuneIds/)
  assert.match(source, /section\.key === 'runes'/)
  assert.match(source, /opgg-rune-row/)
  assert.match(source, /class="opgg-rune-groups"/)
  assert.match(source, /class="opgg-rune-paths"/)
  assert.match(source, /class="opgg-rune-line opgg-rune-line-primary"/)
  assert.match(source, /class="opgg-rune-line opgg-rune-line-secondary"/)
  assert.doesNotMatch(source, />主系</)
  assert.doesNotMatch(source, />主系天赋</)
  assert.doesNotMatch(source, />副系</)
  assert.doesNotMatch(source, />副系天赋</)
  assert.doesNotMatch(source, />小属性</)
  assert.match(source, /runeGroup\(option\)\.primaryRuneIds/)
  assert.match(source, /runeGroup\(option\)\.secondaryRuneIds/)
  assert.match(source, /runeGroup\(option\)\.statModIds/)
})

test('OP.GG champion panel renders skill orders as QWER chips with detailed sequence', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /title: '技能点法'/)
  assert.match(source, /v-else-if="section\.key === 'skillOrders'"/)
  assert.match(source, /class="opgg-skill-order"/)
  assert.match(source, /class="opgg-skill-priority"/)
  assert.match(source, /class="opgg-skill-sequence"/)
  assert.match(source, /skillOrderSequence\(option\)/)
  assert.match(source, /skillChipClass\(id\)/)
  assert.match(source, /formatSkillId\(id\)/)
  assert.match(source, /opgg-skill-index/)
})

test('OP.GG champion panel keeps build row labels above their own values', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /opgg-build-meta-column/)
  assert.match(source, /opgg-build-pick/)
  assert.match(source, /opgg-build-win/)
  assert.match(source, /opgg-build-sample/)
  assert.match(source, /选择率/)
  assert.match(source, /胜率/)
  assert.match(source, /样本/)
  assert.match(source, /formatPercent\(option\.pickRate\)/)
  assert.match(source, /formatPercent\(option\.winRate\)/)
  assert.match(source, /formatGameCount\(option\.games\)/)
  assert.match(source, /align-self: center/)
  assert.match(source, /\.opgg-build-meta \{[\s\S]*white-space: nowrap/)
  assert.match(source, /\.opgg-build-meta \{[\s\S]*grid-template-columns:\s*repeat\(3,\s*max-content\)/)
  assert.match(source, /\.opgg-build-meta-column \{[\s\S]*display: grid/)
  assert.match(source, /\.opgg-build-meta-column \{[\s\S]*grid-template-rows: auto auto/)
  assert.match(source, /\.opgg-build-meta span \{[\s\S]*color: #38bdf8[\s\S]*font-size: 13px/)
  assert.match(source, /\.opgg-build-meta strong \{[\s\S]*color: var\(--text-primary\)[\s\S]*font-size: 17px/)
  assert.match(source, /\.opgg-build-sample em \{[\s\S]*position: static/)
  assert.doesNotMatch(source, /\.opgg-build-meta-column \{[\s\S]*align-items: baseline/)
})

test('OP.GG champion panel keeps detail cards and build rows compact at minimum width', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /\.opgg-panel \{[\s\S]*min-width:\s*0/)
  assert.match(source, /\.opgg-content \{[\s\S]*padding:\s*14px 16px 16px/)
  assert.match(source, /\.opgg-summary \{[\s\S]*grid-template-columns:\s*repeat\(4, minmax\(112px,\s*1fr\)\)/)
  assert.match(source, /\.opgg-build-row \{[\s\S]*gap:\s*8px[\s\S]*padding:\s*8px/)
  assert.match(source, /\.opgg-build-meta \{[\s\S]*min-width:\s*240px/)
  assert.match(source, /\.opgg-icon-slot \{[\s\S]*width:\s*28px[\s\S]*height:\s*28px/)
})

test('OP.GG champion panel keeps header stats and build rows on one line at narrow width', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /\.opgg-panel-header \{[\s\S]*flex-wrap:\s*nowrap/)
  assert.match(source, /\.opgg-summary \{[\s\S]*grid-template-columns:\s*repeat\(4, minmax\(112px,\s*1fr\)\)/)
  assert.match(source, /\.opgg-build-row \{[\s\S]*flex-wrap:\s*nowrap/)
  assert.match(source, /\.opgg-icon-chain \{[\s\S]*flex-wrap:\s*nowrap/)
  assert.match(source, /\.opgg-build-meta \{[\s\S]*display:\s*grid/)
  assert.doesNotMatch(source, /@media \(max-width: 720px\)[\s\S]*flex-direction:\s*column/)
  assert.doesNotMatch(source, /@media \(max-width: 720px\)[\s\S]*grid-template-columns:\s*repeat\(2, minmax\(0,\s*1fr\)\)/)
})
