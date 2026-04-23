<template>
  <div v-if="visible && gameDetail && matchHistory" class="match-detail-overlay" @click.self="close">
    <div class="match-detail-modal">
      <button class="close-btn" type="button" @click="close">×</button>

      <header class="match-header">
        <div class="header-main">
          <div class="result-pill" :class="{ win: myPlayer?.stats?.win, lose: !myPlayer?.stats?.win }">
            {{ myPlayer?.stats?.win ? '胜利' : '失败' }}
          </div>

          <div class="header-copy">
            <h3>{{ matchHistory.queueName || gameDetail.gameMode || '对局详情' }}</h3>
            <p>{{ formatDate(gameDetail.gameCreation) }} · {{ formatDuration(gameDetail.gameDuration) }}</p>
          </div>
        </div>

        <div v-if="myPlayer" class="hero-summary">
          <img class="hero-avatar" :src="getChampionUrl(myPlayer.championId)" alt="" />
          <div class="hero-copy">
            <strong>{{ currentSummonerName }}</strong>
            <span>{{ getKdaText(myPlayer) }}</span>
          </div>
        </div>
      </header>

      <section class="teams-grid">
        <div class="team-card">
          <div class="team-header blue">
            <div>
              <strong>蓝色方</strong>
              <span>{{ blueTeamKda }} · {{ formatNumber(blueTeamGold) }} 金币</span>
            </div>
            <span>{{ formatNumber(blueTeamDamage) }} 伤害</span>
          </div>

          <div class="team-list">
            <button
              v-for="player in blueTeamPlayers"
              :key="player.participantId"
              class="player-row"
              :class="{ me: player.puuid === currentPuuid }"
              :disabled="!player.gameName || player.puuid === currentPuuid"
              @click="handlePlayerClick(player)"
            >
              <div class="player-main">
                <img class="player-avatar" :src="getChampionUrl(player.championId)" alt="" />
                <div class="player-copy">
                  <div class="player-name-row">
                    <span class="player-name">{{ getPlayerName(player) }}</span>
                    <span v-if="player.puuid === currentPuuid" class="me-tag">我</span>
                  </div>
                  <UserTagBadgeList
                    compact
                    :record-status="getPlayerSummary(player)?.recordStatus"
                    :tags="getPlayerSummary(player)?.tag"
                    :limit="2"
                  />
                  <div class="player-build">
                    <img
                      v-for="(itemId, index) in getItemIds(player.stats)"
                      :key="index"
                      class="build-icon"
                      :src="getItemUrl(itemId)"
                      alt=""
                    />
                  </div>
                </div>
              </div>

              <div class="player-stats">
                <span>{{ getKdaText(player) }}</span>
                <span>{{ formatNumber(player.stats?.goldEarned) }} 金币</span>
                <span>{{ formatNumber(player.stats?.totalDamageDealtToChampions) }} 伤害</span>
              </div>
            </button>
          </div>
        </div>

        <div class="team-card">
          <div class="team-header red">
            <div>
              <strong>红色方</strong>
              <span>{{ redTeamKda }} · {{ formatNumber(redTeamGold) }} 金币</span>
            </div>
            <span>{{ formatNumber(redTeamDamage) }} 伤害</span>
          </div>

          <div class="team-list">
            <button
              v-for="player in redTeamPlayers"
              :key="player.participantId"
              class="player-row"
              :class="{ me: player.puuid === currentPuuid }"
              :disabled="!player.gameName || player.puuid === currentPuuid"
              @click="handlePlayerClick(player)"
            >
              <div class="player-main">
                <img class="player-avatar" :src="getChampionUrl(player.championId)" alt="" />
                <div class="player-copy">
                  <div class="player-name-row">
                    <span class="player-name">{{ getPlayerName(player) }}</span>
                    <span v-if="player.puuid === currentPuuid" class="me-tag">我</span>
                  </div>
                  <UserTagBadgeList
                    compact
                    :record-status="getPlayerSummary(player)?.recordStatus"
                    :tags="getPlayerSummary(player)?.tag"
                    :limit="2"
                  />
                  <div class="player-build">
                    <img
                      v-for="(itemId, index) in getItemIds(player.stats)"
                      :key="index"
                      class="build-icon"
                      :src="getItemUrl(itemId)"
                      alt=""
                    />
                  </div>
                </div>
              </div>

              <div class="player-stats">
                <span>{{ getKdaText(player) }}</span>
                <span>{{ formatNumber(player.stats?.goldEarned) }} 金币</span>
                <span>{{ formatNumber(player.stats?.totalDamageDealtToChampions) }} 伤害</span>
              </div>
            </button>
          </div>
        </div>
      </section>

      <section class="ai-panel">
        <div class="ai-toolbar">
          <strong>AI 复盘</strong>

          <div class="ai-controls">
            <select v-model="aiMode" class="ai-select">
              <option value="overview">全局复盘</option>
              <option value="player">单人复盘</option>
            </select>

            <select v-if="aiMode === 'player'" v-model="aiTargetParticipantId" class="ai-select">
              <option v-for="player in allPlayers" :key="player.participantId" :value="player.participantId">
                {{ getPlayerName(player) }}
              </option>
            </select>

            <button
              class="ai-run-btn"
              type="button"
              :disabled="aiLoading || (aiMode === 'player' && !aiTargetParticipantId)"
              @click="runAiAnalysis"
            >
              {{ aiLoading ? '分析中...' : '开始 AI 复盘' }}
            </button>
          </div>
        </div>

        <div v-if="aiError" class="ai-error">{{ aiError }}</div>
        <pre v-else-if="aiResult" class="ai-output">{{ aiResult }}</pre>
        <div v-else class="ai-empty">选择模式后，让 AI 来锐评这把。</div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { apiClient } from '@/api/httpClient'
import UserTagBadgeList from '@/components/summoner/UserTagBadgeList.vue'
import type { AIAnalysisResult, GameDetail, GameParticipant, MatchHistory, UserTagSummary } from '@/types/api'

interface PlayerWithIdentity extends GameParticipant {
  puuid: string
  gameName: string
  tagLine: string
}

const props = defineProps<{
  visible: boolean
  gameDetail: GameDetail | null
  matchHistory: MatchHistory | null
  currentPuuid: string
  currentSummonerName: string
  userTagSummaries?: Record<string, UserTagSummary>
}>()

const emit = defineEmits<{
  close: []
  navigateToPlayer: [gameName: string, tagLine: string]
}>()

const aiMode = ref<'overview' | 'player'>('overview')
const aiTargetParticipantId = ref<number | null>(null)
const aiLoading = ref(false)
const aiResult = ref<string | null>(null)
const aiError = ref<string | null>(null)

const allPlayers = computed((): PlayerWithIdentity[] => {
  if (!props.gameDetail) {
    return []
  }

  return props.gameDetail.participants.map(participant => {
    const identity = props.gameDetail!.participantIdentities.find(
      item => item.participantId === participant.participantId
    )

    return {
      ...participant,
      puuid: identity?.player?.puuid || '',
      gameName: identity?.player?.gameName || '',
      tagLine: identity?.player?.tagLine || ''
    }
  })
})

const myPlayer = computed(() => allPlayers.value.find(player => player.puuid === props.currentPuuid) || null)
const blueTeamPlayers = computed(() => allPlayers.value.filter(player => player.teamId === 100))
const redTeamPlayers = computed(() => allPlayers.value.filter(player => player.teamId === 200))

const blueTeamGold = computed(() => sumTeamValue(blueTeamPlayers.value, player => player.stats?.goldEarned || 0))
const redTeamGold = computed(() => sumTeamValue(redTeamPlayers.value, player => player.stats?.goldEarned || 0))
const blueTeamDamage = computed(() => sumTeamValue(blueTeamPlayers.value, player => player.stats?.totalDamageDealtToChampions || 0))
const redTeamDamage = computed(() => sumTeamValue(redTeamPlayers.value, player => player.stats?.totalDamageDealtToChampions || 0))

const blueTeamKda = computed(() => getTeamKda(blueTeamPlayers.value))
const redTeamKda = computed(() => getTeamKda(redTeamPlayers.value))

function close() {
  emit('close')
}

function getPlayerSummary(player: PlayerWithIdentity): UserTagSummary | undefined {
  return player.puuid ? props.userTagSummaries?.[player.puuid] : undefined
}

function getPlayerName(player: PlayerWithIdentity): string {
  if (player.gameName) {
    return player.tagLine ? `${player.gameName}#${player.tagLine}` : player.gameName
  }
  return `玩家 ${player.participantId}`
}

function getChampionUrl(championId?: number): string {
  return championId && championId > 0
    ? `http://127.0.0.1:8080/api/v1/asset/champion/${championId}`
    : ''
}

function getItemUrl(itemId?: number): string {
  return itemId && itemId > 0
    ? `http://127.0.0.1:8080/api/v1/asset/item/${itemId}`
    : ''
}

function getItemIds(stats: any): number[] {
  return [
    stats?.item0 || 0,
    stats?.item1 || 0,
    stats?.item2 || 0,
    stats?.item3 || 0,
    stats?.item4 || 0,
    stats?.item5 || 0,
    stats?.item6 || 0
  ].filter(Boolean)
}

function getKdaText(player: PlayerWithIdentity): string {
  const kills = player.stats?.kills || 0
  const deaths = player.stats?.deaths || 0
  const assists = player.stats?.assists || 0
  const kda = deaths > 0 ? (kills + assists) / deaths : kills + assists
  return `${kills}/${deaths}/${assists} · ${kda.toFixed(1)} KDA`
}

function sumTeamValue(players: PlayerWithIdentity[], picker: (player: PlayerWithIdentity) => number): number {
  return players.reduce((total, player) => total + picker(player), 0)
}

function getTeamKda(players: PlayerWithIdentity[]): string {
  let kills = 0
  let deaths = 0
  let assists = 0

  for (const player of players) {
    kills += player.stats?.kills || 0
    deaths += player.stats?.deaths || 0
    assists += player.stats?.assists || 0
  }

  return `${kills}/${deaths}/${assists}`
}

function formatDuration(seconds?: number): string {
  const safeSeconds = seconds || 0
  const minutes = Math.floor(safeSeconds / 60)
  const remain = safeSeconds % 60
  return `${minutes}:${String(remain).padStart(2, '0')}`
}

function formatDate(timestamp?: number): string {
  if (!timestamp) {
    return '--'
  }
  return new Date(timestamp).toLocaleString()
}

function formatNumber(value?: number): string {
  if (value == null) {
    return '0'
  }
  if (value >= 1000000) {
    return `${(value / 1000000).toFixed(1)}m`
  }
  if (value >= 1000) {
    return `${(value / 1000).toFixed(value >= 10000 ? 1 : 2)}`.replace(/\.0$/, '') + 'k'
  }
  return String(value)
}

function handlePlayerClick(player: PlayerWithIdentity) {
  if (player.puuid === props.currentPuuid || !player.gameName) {
    return
  }
  emit('navigateToPlayer', player.gameName, player.tagLine)
  close()
}

async function runAiAnalysis() {
  if (!props.gameDetail || aiLoading.value) {
    return
  }

  aiLoading.value = true
  aiError.value = null

  try {
    const result: AIAnalysisResult = await apiClient.analyzeGameDetail({
      gameId: props.gameDetail.gameId,
      mode: aiMode.value,
      participantId: aiMode.value === 'player' ? aiTargetParticipantId.value ?? undefined : undefined
    })

    if (result.success && result.content) {
      aiResult.value = result.content
      return
    }

    aiResult.value = null
    aiError.value = result.error || 'AI 复盘失败。'
  } catch (error: any) {
    aiResult.value = null
    aiError.value = error?.message || 'AI 复盘失败。'
  } finally {
    aiLoading.value = false
  }
}
</script>

<style scoped>
.match-detail-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2100;
}

.match-detail-modal {
  position: relative;
  width: min(1200px, 94vw);
  max-height: 92vh;
  overflow: auto;
  padding: 22px;
  border-radius: 20px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.close-btn {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-primary);
  font-size: 22px;
  cursor: pointer;
}

.match-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-right: 42px;
}

.header-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.result-pill {
  padding: 10px 14px;
  border-radius: 12px;
  font-weight: 700;
}

.result-pill.win {
  background: rgba(61, 155, 122, 0.14);
  color: #3d9b7a;
}

.result-pill.lose {
  background: rgba(196, 92, 92, 0.14);
  color: #c45c5c;
}

.header-copy h3 {
  margin: 0;
  color: var(--text-primary);
}

.header-copy p,
.hero-copy span,
.player-stats span,
.team-header span,
.ai-empty {
  color: var(--text-secondary);
}

.hero-summary {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-avatar,
.player-avatar {
  background: var(--bg-tertiary);
  object-fit: cover;
}

.hero-avatar {
  width: 48px;
  height: 48px;
  border-radius: 12px;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hero-copy strong,
.team-header strong,
.player-name,
.ai-toolbar strong {
  color: var(--text-primary);
}

.teams-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.team-card,
.ai-panel {
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.02);
}

.team-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.team-header.blue strong {
  color: #5ca3ea;
}

.team-header.red strong {
  color: #de6f6f;
}

.team-list {
  display: flex;
  flex-direction: column;
}

.player-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  background: transparent;
  color: inherit;
  text-align: left;
}

.player-row:last-child {
  border-bottom: 0;
}

.player-row:not(:disabled) {
  cursor: pointer;
}

.player-row:not(:disabled):hover {
  background: rgba(92, 163, 234, 0.06);
}

.player-row.me {
  background: rgba(92, 163, 234, 0.08);
}

.player-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.player-avatar {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  flex-shrink: 0;
}

.player-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.player-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.player-name {
  font-size: 13px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.me-tag {
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(92, 163, 234, 0.15);
  color: #5ca3ea;
  font-size: 10px;
}

.player-build {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.build-icon {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  background: var(--bg-tertiary);
}

.player-stats {
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: right;
  font-size: 12px;
}

.ai-panel {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ai-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.ai-controls {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.ai-select,
.ai-run-btn {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--border-color);
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.ai-run-btn {
  cursor: pointer;
}

.ai-error {
  color: #c45c5c;
}

.ai-output {
  margin: 0;
  padding: 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-primary);
  white-space: pre-wrap;
  line-height: 1.6;
  font-family: inherit;
}

@media (max-width: 960px) {
  .match-header,
  .teams-grid,
  .ai-toolbar {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  .player-row {
    grid-template-columns: 1fr;
  }

  .player-stats {
    text-align: left;
  }
}
</style>
