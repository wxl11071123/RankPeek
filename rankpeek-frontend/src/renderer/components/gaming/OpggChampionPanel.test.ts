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
  assert.match(source, /runes/)
  assert.match(source, /skillOrders/)
  assert.match(source, /starterItems/)
  assert.match(source, /boots/)
  assert.match(source, /coreItems/)
  assert.match(source, /getIconUrl\(section\.iconType, id\)/)
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
  assert.match(source, /toggleSectionExpanded\(section\.key\)/)
  assert.match(source, /'展开'/)
  assert.match(source, /'收起'/)
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

test('OP.GG champion panel uses larger blue labels, white centered rates, and sample size at bottom right', () => {
  const source = readFileSync(new URL('./OpggChampionPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /opgg-build-meta-column/)
  assert.match(source, /opgg-build-pick/)
  assert.match(source, /opgg-build-win/)
  assert.match(source, /选择率/)
  assert.match(source, /胜率/)
  assert.match(source, /样本/)
  assert.match(source, /formatPercent\(option\.pickRate\)/)
  assert.match(source, /formatPercent\(option\.winRate\)/)
  assert.match(source, /formatGameCount\(option\.games\)/)
  assert.match(source, /align-self: center/)
  assert.match(source, /position: relative/)
  assert.match(source, /grid-template-columns: repeat\(2, minmax\(62px, 1fr\)\)/)
  assert.match(source, /\.opgg-build-meta span \{[\s\S]*color: #38bdf8[\s\S]*font-size: 13px/)
  assert.match(source, /\.opgg-build-meta strong \{[\s\S]*color: var\(--text-primary\)[\s\S]*font-size: 17px/)
  assert.match(source, /\.opgg-build-meta-column \{/)
  assert.match(source, /grid-template-rows: 18px 24px/)
  assert.match(source, /\.opgg-build-win em \{[\s\S]*position: absolute[\s\S]*right: 0[\s\S]*bottom: 0/)
})
