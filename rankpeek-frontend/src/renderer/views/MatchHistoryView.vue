<template>
  <div class="match-history-view">
    <section class="page-shell">
      <div class="page-copy">
        <h1>{{ t('matchHistory.title') }}</h1>
        <p>{{ t('matchHistory.subtitle') }}</p>
      </div>

      <div class="page-actions">
        <button class="ghost-btn" type="button" :disabled="loading || !currentSummoner" @click="loadData">
          {{ loading ? t('common.refreshing') : t('common.refresh') }}
        </button>
      </div>
    </section>

    <section v-if="!currentSummoner" class="state-card">
      <strong>{{ t('matchHistory.noAccountTitle') }}</strong>
      <span>{{ t('matchHistory.noAccountBody') }}</span>
    </section>

    <section v-else class="content-stack">
      <div class="history-shell">
        <SummonerOverviewPanel
          class="overview-embed"
          :summoner="currentSummoner"
          :user-tag="userTag"
          :solo-rank="soloRank"
          :flex-rank="flexRank"
          :ranked-win-rates="rankedWinRates"
          embedded
          @copy-name="handleCopyName"
        />

        <div class="history-toolbar">
          <div>
            <h2>{{ t('matchHistory.recentTitle') }}</h2>
            <p>{{ t('matchHistory.recentBody', { name: currentSummonerName }) }}</p>
          </div>

          <div class="filters">
            <select v-model="filterChampionId" class="filter-select" @change="handleFilterChange">
              <option :value="-1">{{ t('common.allChampions') }}</option>
              <option
                v-for="champion in championOptions"
                :key="champion.value"
                :value="champion.value"
              >
                {{ champion.label }}
              </option>
            </select>

            <select v-model="filterQueueId" class="filter-select" @change="handleFilterChange">
              <option :value="0">{{ t('common.allModes') }}</option>
              <option
                v-for="mode in modeOptions"
                :key="mode.id"
                :value="mode.id"
              >
                {{ mode.name }}
              </option>
            </select>

            <button class="ghost-btn" type="button" @click="resetFilter">{{ t('common.reset') }}</button>
          </div>
        </div>

        <div v-if="loading && !matchHistory.length" class="state-card inner">
          <strong>{{ t('matchHistory.loadingTitle') }}</strong>
          <span>{{ t('matchHistory.loadingBody') }}</span>
        </div>

        <div v-else-if="!matchHistory.length" class="state-card inner">
          <strong>{{ matchStateMeta.title }}</strong>
          <span>{{ matchStateMeta.hint }}</span>
        </div>

        <div v-else class="match-list">
          <article
            v-for="match in matchHistory"
            :key="match.gameId"
            class="match-card"
            @click="showMatchDetail(match)"
          >
            <div class="match-card-main">
              <div class="match-outcome" :class="{ win: isMatchWin(match), lose: !isMatchWin(match) }">
                <span class="outcome-text">{{ isMatchWin(match) ? t('common.win') : t('common.loss') }}</span>
                <span class="outcome-meta">{{ formatShortDate(match.gameCreation) }}</span>
              </div>

              <div class="match-summary">
                <div class="champion-pill">
                  <img
                    class="champion-avatar"
                    :src="getChampionUrl(getCurrentPlayer(match)?.championId)"
                    alt=""
                  />
                  <div class="champion-copy">
                    <strong>{{ match.queueName || match.gameMode || t('common.unknownMode') }}</strong>
                    <span>{{ formatDuration(match.gameDuration) }} · {{ currentSummonerName }}</span>
                  </div>
                </div>

              </div>
            </div>

            <div class="roster-grid">
              <div class="roster-column">
                <div class="roster-title blue">{{ t('common.blueTeam') }}</div>
                <MatchRosterCompact
                  :players="getTeamPlayers(match, 100)"
                  :summaries="userTagSummaries"
                  :current-puuid="currentSummoner.puuid"
                  @navigate-to-player="handleNavigateToPlayer"
                />
              </div>

              <div class="roster-column">
                <div class="roster-title red">{{ t('common.redTeam') }}</div>
                <MatchRosterCompact
                  :players="getTeamPlayers(match, 200)"
                  :summaries="userTagSummaries"
                  :current-puuid="currentSummoner.puuid"
                  @navigate-to-player="handleNavigateToPlayer"
                />
              </div>
            </div>
          </article>
        </div>

        <div class="pagination">
          <button class="ghost-btn" type="button" :disabled="loading || currentPage <= 1" @click="prevPage">
            {{ t('common.previousPage') }}
          </button>
          <span class="page-indicator">{{ t('common.pageIndicator', { current: currentPage, total: totalPages }) }}</span>
          <button class="ghost-btn" type="button" :disabled="!hasNextPage" @click="nextPage">
            {{ t('common.nextPage') }}
          </button>
        </div>
      </div>
    </section>

    <MatchDetailModal
      :visible="showDetailModal"
      :game-detail="selectedGameDetail"
      :match-history="selectedMatchHistory"
      :current-puuid="currentSummoner?.puuid || ''"
      :current-summoner-name="currentSummonerName"
      :user-tag-summaries="userTagSummaries"
      @close="closeDetailModal"
      @navigate-to-player="handleNavigateToPlayer"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { apiClient } from '@/api/httpClient'
import MatchDetailModal from '@/components/summoner/MatchDetailModal.vue'
import MatchRosterCompact from '@/components/summoner/MatchRosterCompact.vue'
import SummonerOverviewPanel from '@/components/summoner/SummonerOverviewPanel.vue'
import { useGameStore } from '@/stores/game'
import { useI18n } from '@/i18n'
import { DEFAULT_ANALYSIS_QUEUE_MODE, getDefaultMatchQueueMode } from '@/utils/matchPreferences'
import type {
  ChampionOption,
  GameDetail,
  GameModeOption,
  MatchHistory,
  Participant,
  QueueInfo,
  UserTag,
  UserTagSummary,
  WinRate
} from '@/types/api'

interface MatchRosterPlayer {
  participantId: number
  championId: number
  puuid: string
  gameName: string
  tagLine: string
  summonerName?: string
  stats?: Participant['stats']
}

interface PlayerInMatch extends Participant {
  playerName: string
}

const router = useRouter()
const gameStore = useGameStore()
const { t } = useI18n()

const matchHistory = ref<MatchHistory[]>([])
const userTag = ref<UserTag | null>(null)
const rankedWinRates = ref<Record<string, WinRate> | null>(null)
const userTagSummaries = ref<Record<string, UserTagSummary>>({})
const championOptions = ref<ChampionOption[]>([])
const modeOptions = ref<GameModeOption[]>([])
const filterChampionId = ref(-1)
const filterQueueId = ref(0)
const defaultMatchQueueMode = ref(0)
const currentPage = ref(1)
const loading = ref(false)
const showDetailModal = ref(false)
const selectedGameDetail = ref<GameDetail | null>(null)
const selectedMatchHistory = ref<MatchHistory | null>(null)
let settingsLoadPromise: Promise<void> | null = null

const pageSize = 10
const maxTotalRecords = 50

const currentSummoner = computed(() => gameStore.currentSummoner)
const currentSummonerName = computed(() => gameStore.summonerName || '')
const soloRank = computed<QueueInfo | null>(() => gameStore.soloRank)
const flexRank = computed<QueueInfo | null>(() => gameStore.flexRank)
const totalPages = computed(() => Math.max(1, Math.ceil(maxTotalRecords / pageSize)))
const hasFilters = computed(() => filterChampionId.value > 0 || filterQueueId.value > 0)
const hasNextPage = computed(() =>
  !loading.value &&
  matchHistory.value.length === pageSize &&
  currentPage.value < totalPages.value
)

const matchStateMeta = computed(() => {
  const status = userTag.value?.recordStatus
  if (status === 'PRIVATE') {
    return {
      title: t('matchHistory.privateTitle'),
      hint: t('matchHistory.privateBody')
    }
  }
  if (status === 'EMPTY') {
    return {
      title: t('matchHistory.emptyTitle'),
      hint: t('matchHistory.emptyBody')
    }
  }
  if (status === 'ERROR') {
    return {
      title: t('matchHistory.errorTitle'),
      hint: t('matchHistory.errorBody')
    }
  }
  if (hasFilters.value) {
    return {
      title: t('matchHistory.filteredEmptyTitle'),
      hint: t('matchHistory.filteredEmptyBody')
    }
  }
  return {
    title: t('matchHistory.noMatchesTitle'),
    hint: t('matchHistory.noMatchesBody')
  }
})

async function ensurePageSettingsLoaded() {
  if (settingsLoadPromise) {
    await settingsLoadPromise
    return
  }

  settingsLoadPromise = (async () => {
    try {
      const [champions, modes, savedDefaultQueueMode] = await Promise.all([
        apiClient.getChampionOptions(),
        apiClient.getGameModes(),
        getDefaultMatchQueueMode()
      ])
      championOptions.value = champions
      modeOptions.value = modes
      defaultMatchQueueMode.value = savedDefaultQueueMode
    } catch (err) {
      console.error('Failed to load page settings', err)
    } finally {
      settingsLoadPromise = null
    }
  })()

  await settingsLoadPromise
}

function applyDefaultFilters() {
  filterChampionId.value = -1
  filterQueueId.value = defaultMatchQueueMode.value
  currentPage.value = 1
}

async function loadData() {
  if (!currentSummoner.value?.puuid) {
    return
  }

  loading.value = true
  try {
    await gameStore.fetchRank(currentSummoner.value.puuid)
    const [tagData, winRates] = await Promise.all([
      apiClient.getUserTagByPuuid(currentSummoner.value.puuid, DEFAULT_ANALYSIS_QUEUE_MODE),
      apiClient.getRankedWinRates(currentSummoner.value.puuid)
    ])
    userTag.value = tagData
    rankedWinRates.value = winRates
    await loadMatchHistory()
  } catch (err) {
    console.error('Failed to load match history page', err)
    userTag.value = null
    rankedWinRates.value = null
    matchHistory.value = []
    userTagSummaries.value = {}
  } finally {
    loading.value = false
  }
}

async function loadMatchHistory() {
  if (!currentSummoner.value?.puuid) {
    return
  }

  loading.value = true
  try {
    const begIndex = (currentPage.value - 1) * pageSize
    const endIndex = Math.min(begIndex + pageSize - 1, maxTotalRecords - 1)

    const matches = hasFilters.value
      ? await apiClient.getFilteredMatchHistory(currentSummoner.value.puuid, {
          begIndex,
          endIndex,
          championId: filterChampionId.value > 0 ? filterChampionId.value : undefined,
          queueId: filterQueueId.value > 0 ? filterQueueId.value : undefined
        })
      : await apiClient.getMatchHistory(currentSummoner.value.puuid, begIndex, endIndex)

    matchHistory.value = matches
    await loadVisibleUserTagSummaries(matches)
  } catch (err) {
    console.error('Failed to load match history', err)
    matchHistory.value = []
    userTagSummaries.value = {}
  } finally {
    loading.value = false
  }
}

async function loadVisibleUserTagSummaries(matches: MatchHistory[]) {
  const puuids = collectVisiblePuuids(matches)
  if (!puuids.length) {
    userTagSummaries.value = {}
    return
  }

  try {
    userTagSummaries.value = await apiClient.getUserTagSummaryBatch(puuids, DEFAULT_ANALYSIS_QUEUE_MODE)
  } catch (err) {
    console.warn('Failed to load summary tags', err)
    userTagSummaries.value = {}
  }
}

function collectVisiblePuuids(matches: MatchHistory[]): string[] {
  const unique = new Set<string>()
  for (const match of matches) {
    for (const identity of match.participantIdentities || []) {
      const puuid = identity.player?.puuid
      if (puuid) {
        unique.add(puuid)
      }
    }
  }
  return [...unique]
}

function getTeamPlayers(match: MatchHistory, teamId: number): MatchRosterPlayer[] {
  const identityMap = new Map(
    (match.participantIdentities || []).map(identity => [identity.participantId, identity.player])
  )

  return (match.participants || [])
    .filter(participant => participant.teamId === teamId)
    .map(participant => {
      const player = identityMap.get(participant.participantId)
      return {
        participantId: participant.participantId,
        championId: participant.championId,
        puuid: player?.puuid || '',
        gameName: player?.gameName || '',
        tagLine: player?.tagLine || '',
        summonerName: player?.summonerName || '',
        stats: participant.stats
      }
    })
}

function getCurrentPlayer(match: MatchHistory): PlayerInMatch | null {
  if (!currentSummoner.value?.puuid) {
    return null
  }

  const identity = (match.participantIdentities || []).find(
    item => item.player?.puuid === currentSummoner.value?.puuid
  )
  if (!identity) {
    return null
  }

  const participant = (match.participants || []).find(
    item => item.participantId === identity.participantId
  )
  if (!participant) {
    return null
  }

  const playerName = identity.player?.gameName
    ? identity.player.tagLine
      ? `${identity.player.gameName}#${identity.player.tagLine}`
      : identity.player.gameName
    : identity.player?.summonerName || t('common.unknownPlayer')

  return {
    ...participant,
    playerName
  }
}

function isMatchWin(match: MatchHistory): boolean {
  return Boolean(getCurrentPlayer(match)?.stats?.win)
}

function getChampionUrl(championId?: number): string {
  return championId && championId > 0
    ? `http://127.0.0.1:8080/api/v1/asset/champion/${championId}`
    : ''
}

function formatDuration(seconds?: number): string {
  const safeSeconds = seconds || 0
  const minutes = Math.floor(safeSeconds / 60)
  const remain = safeSeconds % 60
  return `${minutes}:${String(remain).padStart(2, '0')}`
}

function formatShortDate(timestamp?: number): string {
  if (!timestamp) {
    return '--'
  }
  const date = new Date(timestamp)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

async function handleFilterChange() {
  currentPage.value = 1
  await loadMatchHistory()
}

async function resetFilter() {
  if (filterChampionId.value === -1 && filterQueueId.value === defaultMatchQueueMode.value) {
    return
  }
  applyDefaultFilters()
  await loadMatchHistory()
}

async function prevPage() {
  if (currentPage.value <= 1) {
    return
  }
  currentPage.value -= 1
  await loadMatchHistory()
}

async function nextPage() {
  if (!hasNextPage.value) {
    return
  }
  currentPage.value += 1
  await loadMatchHistory()
}

async function showMatchDetail(match: MatchHistory) {
  showDetailModal.value = true
  selectedMatchHistory.value = match
  selectedGameDetail.value = null

  try {
    selectedGameDetail.value = await apiClient.getGameDetail(match.gameId)
  } catch (err) {
    console.error('Failed to load game detail', err)
  }
}

function closeDetailModal() {
  showDetailModal.value = false
  selectedGameDetail.value = null
  selectedMatchHistory.value = null
}

function handleNavigateToPlayer(gameName: string, tagLine: string) {
  const target = tagLine ? `${gameName}#${tagLine}` : gameName
  if (!target) {
    return
  }

  router.push({
    path: '/summoner',
    query: { name: target }
  })
}

function handleCopyName() {
  if (currentSummonerName.value) {
    console.info('Copied summoner name', currentSummonerName.value)
  }
}

onMounted(async () => {
  await ensurePageSettingsLoaded()
})

watch(
  () => currentSummoner.value?.puuid,
  async puuid => {
    if (!puuid) {
      return
    }
    await ensurePageSettingsLoaded()
    applyDefaultFilters()
    await loadData()
  },
  { immediate: true }
)
</script>

<style scoped>
.match-history-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-shell,
.history-shell,
.state-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 16px;
}

.page-shell {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
}

.page-copy h1,
.history-toolbar h2 {
  margin: 0;
  color: var(--text-primary);
}

.page-copy p,
.history-toolbar p,
.state-card span {
  margin: 6px 0 0;
  color: var(--text-secondary);
}

.page-actions {
  display: flex;
  align-items: center;
}

.ghost-btn,
.filter-select {
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.ghost-btn {
  cursor: pointer;
  transition: transform 0.15s ease, opacity 0.15s ease;
}

.ghost-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.ghost-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.content-stack {
  min-width: 0;
}

.history-shell {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-embed {
  margin-bottom: 2px;
}

.history-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.filters {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.filter-select {
  min-width: 140px;
}

.state-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.state-card.inner {
  background: var(--bg-tertiary);
}

.state-card strong {
  color: var(--text-primary);
}

.match-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.match-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.03), rgba(255, 255, 255, 0.015));
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.match-card:hover {
  transform: translateY(-1px);
  border-color: rgba(92, 163, 234, 0.3);
}

.match-card-main {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.match-outcome {
  min-width: 72px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}

.match-outcome.win {
  background: rgba(61, 155, 122, 0.14);
  color: #3d9b7a;
}

.match-outcome.lose {
  background: rgba(196, 92, 92, 0.14);
  color: #c45c5c;
}

.outcome-text {
  font-size: 14px;
  font-weight: 700;
}

.outcome-meta {
  font-size: 12px;
}

.match-summary {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.champion-pill {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.champion-avatar {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  object-fit: cover;
  background: var(--bg-tertiary);
}

.champion-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.champion-copy strong {
  color: var(--text-primary);
}

.champion-copy span {
  color: var(--text-secondary);
}

.roster-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.roster-column {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.roster-title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.roster-title.blue {
  color: #5ca3ea;
}

.roster-title.red {
  color: #de6f6f;
}

.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.page-indicator {
  color: var(--text-secondary);
  font-size: 13px;
}

@media (max-width: 720px) {
  .page-shell,
  .history-toolbar,
  .pagination {
    flex-direction: column;
    align-items: stretch;
  }

  .match-card-main,
  .roster-grid {
    grid-template-columns: 1fr;
  }
}
</style>
