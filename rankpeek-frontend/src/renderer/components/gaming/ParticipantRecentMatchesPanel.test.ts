import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

test('participant recent matches panel renders compact match rows without header or fetching', () => {
  const source = readFileSync(new URL('./ParticipantRecentMatchesPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /player: SessionSummoner \| null/)
  assert.match(source, /buildParticipantRecentMatchItems\(props\.player\?\.matchHistory, props\.player\?\.summoner\?\.puuid\)/)
  assert.match(source, /getChampionIconUrl\(item\.championId\)/)
  assert.match(source, /markAssetLoadFailed/)
  assert.match(source, /class="recent-match-list"/)
  assert.match(source, /class="recent-match-summary"[\s\S]*{{ item\.resultText }}[\s\S]*{{ item\.timeText }}[\s\S]*{{ item\.durationText }}/)
  assert.match(source, /class="recent-match-kda"[\s\S]*{{ item\.kdaText }}/)
  assert.match(source, /max-height:\s*(?:2[2-8]0)px/)
  assert.match(source, /overflow-y:\s*auto/)
  assert.doesNotMatch(source, /<header|recent-panel-header/)
  assert.doesNotMatch(source, /最近 \{\{ recentItems\.length \}\} 局|{{ playerName }}/)
  assert.doesNotMatch(source, /item\.queueText/)
  assert.doesNotMatch(source, /championLabel/)
  assert.doesNotMatch(source, /英雄 \{\{|CLASSIC|胜率|样本|伤转|单双排|灵活排位/)
  assert.doesNotMatch(source, /apiClient|fetch\(|axios|\/match-history/)
})

test('participant recent matches panel has an empty state for missing twenty-game data', () => {
  const source = readFileSync(new URL('./ParticipantRecentMatchesPanel.vue', import.meta.url), 'utf8')

  assert.match(source, /v-if="!recentItems\.length"/)
  assert.match(source, /\u6682\u65e0\u6700\u8fd1\u6218\u7ee9\u6570\u636e/)
  assert.match(source, /\.participant-recent-panel/)
  assert.match(source, /\.recent-match-row/)
})
