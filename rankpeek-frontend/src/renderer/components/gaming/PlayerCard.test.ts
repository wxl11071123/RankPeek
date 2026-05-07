import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const removedScoutLabels = ['\u66b4\u6bd9', '\u6446\u70c2', '\u5f00\u9ed1\u4ed4']

test('player card is selectable by click and keyboard while preserving tag hover popovers', () => {
  const source = readFileSync(new URL('./PlayerCard.vue', import.meta.url), 'utf8')

  assert.match(source, /selected\?: boolean/)
  assert.match(source, /selectPlayer: \[\]/)
  assert.match(source, /role="button"/)
  assert.match(source, /tabindex="0"/)
  assert.match(source, /:aria-pressed="selected \? 'true' : 'false'"/)
  assert.match(source, /@click="onCardSelect"/)
  assert.match(source, /@keydown\.enter\.prevent="onCardSelect"/)
  assert.match(source, /@keydown\.space\.prevent="onCardSelect"/)
  assert.match(source, /\{ selected \}/)
  assert.match(source, /function onCardSelect\(\)[\s\S]*emit\('selectPlayer'\)/)
  assert.doesNotMatch(source, /class="player-id"[\s\S]{0,180}@click\.stop="onCardSelect"/)
  assert.match(source, /class="more-chip" type="button"[\s\S]*@click\.stop/)
  assert.match(source, /\.tag-overflow:hover \.hidden-tags-popover,/)
  assert.match(source, /\.player-card\.selected/)
})

test('player id navigates to match lookup without toggling the recent panel', () => {
  const source = readFileSync(new URL('./PlayerCard.vue', import.meta.url), 'utf8')

  assert.match(source, /import \{ useRouter \} from 'vue-router'/)
  assert.match(source, /import \{ buildSummonerLookupName, createSummonerLookupRoute \} from '@\/utils\/summonerLookupRoute'/)
  assert.match(source, /const router = useRouter\(\)/)
  assert.match(source, /const summonerLookupName = computed\(\(\) => buildSummonerLookupName\(props\.sessionSummoner\.summoner\)\)/)
  assert.match(source, /const canNavigateToSummonerLookup = computed\(\(\) => Boolean\(summonerLookupName\.value\)\)/)
  assert.match(source, /class="player-id"[\s\S]*@click\.stop="navigateToSummonerLookup"/)
  assert.match(source, /class="player-id"[\s\S]*@keydown\.enter\.stop/)
  assert.match(source, /class="player-id"[\s\S]*@keydown\.space\.stop/)
  assert.match(source, /function navigateToSummonerLookup\(\)[\s\S]*createSummonerLookupRoute\(summonerLookupName\.value\)[\s\S]*router\.push\(route\)/)
  assert.doesNotMatch(source, /function navigateToSummonerLookup\(\)[\s\S]*emit\('selectPlayer'\)/)
})

test('shows the Riot ID on one line and packs tags beside the rank before folding extras', () => {
  const source = readFileSync(new URL('./PlayerCard.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(source, /const INLINE_TAG_LIMIT = 2/)
  assert.match(source, /ref="tagContainerRef"/)
  assert.match(source, /ref="tagMeasureRef"/)
  assert.match(source, /function updateVisibleTagCount/)
  assert.match(source, /new ResizeObserver/)
  assert.match(source, /data-tag-measure/)
  assert.match(source, /data-overflow-measure/)
  assert.match(source, /visibleUserTags/)
  assert.match(source, /hiddenUserTags/)
  assert.match(source, /hiddenUserTagCount/)
  assert.match(source, /class="more-chip"/)
  assert.match(source, /\.name-tags\s*{[\s\S]*flex-wrap:\s*nowrap;/)
  assert.match(source, /class="meta-row"[\s\S]*class="tier-row"[\s\S]*class="name-tags"/)
  assert.match(source, /const summonerLookupName = computed\(\(\) => buildSummonerLookupName\(props\.sessionSummoner\.summoner\)\)/)
  assert.match(source, /const playerIdText = computed\(\(\) => summonerLookupName\.value \|\|/)
  assert.match(source, /{{ playerIdText }}/)
})

test('renders scout tags from sessionSummoner defensively without old label names', () => {
  const source = readFileSync(new URL('./PlayerCard.vue', import.meta.url), 'utf8')

  assert.match(source, /import type \{ QueueInfo, RankTag, RecordStatus, SessionSummoner \}/)
  assert.match(source, /props\.sessionSummoner\.userTag\?\.tag \|\| \[\]/)
  assert.match(source, /filter\(\(tag\): tag is RankTag => Boolean\(tag\?\.tagName\?\.trim\(\)\)\)/)
  assert.match(source, /:title="tag\.tagDesc \|\| tag\.tagName"/)
  assert.match(source, /tag\.good === true \? 'good' : tag\.good === false \? 'bad' : 'neutral'/)
  assert.doesNotMatch(source, new RegExp(removedScoutLabels.join('|')))
})

test('keeps long and many scout tags inside the player card', () => {
  const source = readFileSync(new URL('./PlayerCard.vue', import.meta.url), 'utf8')

  assert.match(source, /\.name-tags\s*{[\s\S]*max-width:\s*100%;[\s\S]*min-width:\s*0;/)
  assert.match(source, /\.visible-tags\s*{[\s\S]*min-width:\s*0;[\s\S]*overflow:\s*hidden;/)
  assert.match(source, /\.hidden-tags-popover\s*{[\s\S]*max-width:\s*min\(260px, 70vw\);[\s\S]*flex-wrap:\s*wrap;/)
  assert.match(source, /\.tag-chip\s*{[\s\S]*box-sizing:\s*border-box;[\s\S]*min-width:\s*0;[\s\S]*max-width:\s*min\(132px, 100%\);[\s\S]*text-overflow:\s*ellipsis;/)
})

test('keeps the damage conversion metric visible in compact gaming cards', () => {
  const source = readFileSync(new URL('./PlayerCard.vue', import.meta.url), 'utf8')

  assert.match(source, /<span>伤转率<\/span>/)
  assert.match(source, /\.scout-metrics\s*{[\s\S]*flex-wrap:\s*wrap;[\s\S]*overflow:\s*visible;/)
  assert.match(source, /\.metric-item\s*{[\s\S]*flex:\s*1 1 76px;[\s\S]*min-width:\s*max-content;/)
  assert.match(source, /\.metric-separator\s*{[\s\S]*display:\s*none;/)
})

test('session tag API types accept nullable or omitted scout fields', () => {
  const source = readFileSync(new URL('../../types/api.ts', import.meta.url), 'utf8')

  assert.match(source, /good\?: boolean \| null/)
  assert.match(source, /tagDesc\?: string/)
  assert.match(source, /userTag\?: UserTag \| null/)
})

test('formats gaming rank labels with Chinese tier names', () => {
  const source = readFileSync(new URL('./PlayerCard.vue', import.meta.url), 'utf8')

  assert.match(source, /PLATINUM:\s*'铂金'/)
  assert.match(source, /DIAMOND:\s*'钻石'/)
  assert.match(source, /MASTER:\s*'超凡大师'/)
  assert.match(source, /GRANDMASTER:\s*'傲世宗师'/)
  assert.match(source, /CHALLENGER:\s*'最强王者'/)
  assert.match(source, /II:\s*'二'/)
  assert.match(source, /return `\$\{tierLabel\} \$\{divisionLabel\} \$\{queueInfo\.leaguePoints\} LP`/)
})
