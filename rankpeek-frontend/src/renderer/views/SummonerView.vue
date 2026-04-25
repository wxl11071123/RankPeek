<template>
  <div class="summoner-view">
    <section class="search-shell">
      <div class="search-copy">
        <h1>{{ t('summoner.title') }}</h1>
        <p>{{ t('summoner.subtitle') }}</p>
      </div>

      <div class="search-bar">
        <input
          v-model="searchName"
          class="search-input"
          type="text"
          :placeholder="t('summoner.placeholder')"
          @keyup.enter="searchSummoner()"
        />
        <button class="search-btn" type="button" :disabled="loading || !searchName.trim()" @click="searchSummoner()">
          {{ loading ? t('summoner.searching') : t('summoner.search') }}
        </button>
      </div>
    </section>

    <section v-if="error" class="state-card error">
      <strong>{{ t('summoner.errorTitle') }}</strong>
      <span>{{ error }}</span>
    </section>

    <section v-else-if="!searchResult && !loading" class="state-card">
      <strong>{{ t('summoner.emptyTitle') }}</strong>
      <span>{{ t('summoner.emptyBody') }}</span>
    </section>

    <section v-else-if="searchResult" class="content-stack">
      <div class="history-shell">
        <section class="lookup-account-strip">
          <div class="lookup-account-profile">
            <img class="lookup-avatar" :src="getProfileIconUrl(searchResult.profileIconId)" alt="" />
            <div class="lookup-account-copy">
              <span class="section-eyebrow">{{ t('summoner.lookupAccount') }}</span>
              <h2>{{ selectedSummonerName }}</h2>
              <div class="rank-pills">
                <span>{{ t('summoner.soloRank') }} {{ formatRankText(searchSoloRank) }}</span>
                <span>{{ t('summoner.flexRank') }} {{ formatRankText(searchFlexRank) }}</span>
              </div>
            </div>
          </div>

          <div class="lookup-account-stats">
            <div class="lookup-stat">
              <span>{{ t('summoner.matchSamples') }}</span>
              <strong>{{ searchMatchHistory.length }}</strong>
            </div>
            <div class="lookup-stat">
              <span>{{ t('summoner.currentPage') }}</span>
              <strong>{{ currentPage }} / {{ totalPages }}</strong>
            </div>
            <button class="ghost-btn" type="button" :disabled="loading" @click="refreshCurrentSummoner">
              {{ loading ? t('common.refreshing') : t('common.refresh') }}
            </button>
          </div>
        </section>

        <div class="lookup-filter-bar">
          <div class="lookup-filter-copy">
            <h2>{{ t('matchHistory.recentTitle') }}</h2>
            <p>{{ t('summoner.recentBody', { name: selectedSummonerName }) }}</p>
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

        <div v-if="loading && !searchMatchHistory.length" class="state-card inner">
          <strong>{{ t('matchHistory.loadingTitle') }}</strong>
          <span>{{ t('summoner.loadingBody') }}</span>
        </div>

        <div v-else-if="!searchMatchHistory.length" class="state-card inner">
          <strong>{{ matchStateMeta.title }}</strong>
          <span>{{ matchStateMeta.hint }}</span>
        </div>

        <div v-else class="match-list">
          <article
            v-for="match in searchMatchHistory"
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
                    <span>{{ formatDuration(match.gameDuration) }} · {{ getCurrentPlayerName(match) }}</span>
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
                  :current-puuid="searchResult.puuid"
                  @navigate-to-player="handleNavigateToPlayer"
                />
              </div>

              <div class="roster-column">
                <div class="roster-title red">{{ t('common.redTeam') }}</div>
                <MatchRosterCompact
                  :players="getTeamPlayers(match, 200)"
                  :summaries="userTagSummaries"
                  :current-puuid="searchResult.puuid"
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
          <button class="ghost-btn" type="button" :disabled="loading || currentPage >= totalPages" @click="nextPage">
            {{ t('common.nextPage') }}
          </button>
        </div>
      </div>
    </section>

    <MatchDetailModal
      :visible="showDetailModal"
      :game-detail="selectedGameDetail"
      :match-history="selectedMatchHistory"
      :current-puuid="searchResult?.puuid || ''"
      :current-summoner-name="selectedSummonerName"
      :user-tag-summaries="userTagSummaries"
      @close="closeDetailModal"
      @navigate-to-player="handleNavigateToPlayer"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiClient } from '@/api/httpClient'
import MatchDetailModal from '@/components/summoner/MatchDetailModal.vue'
import MatchRosterCompact from '@/components/summoner/MatchRosterCompact.vue'
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
  Rank,
  Summoner,
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

const gameStore = useGameStore()
const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const searchName = ref('')
const searchResult = ref<Summoner | null>(null)
const searchRank = ref<Rank | null>(null)
const searchMatchHistory = ref<MatchHistory[]>([])
const searchUserTag = ref<UserTag | null>(null)
const searchRankedWinRates = ref<Record<string, WinRate> | null>(null)
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
const error = ref('')
let settingsLoadPromise: Promise<void> | null = null

const pageSize = 10
const maxTotalRecords = 50

const totalPages = computed(() => Math.max(1, Math.ceil(maxTotalRecords / pageSize)))
const selectedSummonerName = computed(() => formatSummonerName(searchResult.value))
const searchSoloRank = computed<QueueInfo | null>(() => searchRank.value?.queueMap?.RANKED_SOLO_5x5 || null)
const searchFlexRank = computed<QueueInfo | null>(() => searchRank.value?.queueMap?.RANKED_FLEX_SR || null)
const hasFilters = computed(() => filterChampionId.value > 0 || filterQueueId.value > 0)

const matchStateMeta = computed(() => {
  const status = searchUserTag.value?.recordStatus
  if (status === 'PRIVATE') {
    return {
      title: t('matchHistory.privateTitle'),
      hint: t('summoner.privateBody')
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
      hint: t('summoner.errorBody')
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

async function searchSummoner(nameOverride?: string) {
  const keyword = (nameOverride ?? searchName.value).trim()
  if (!keyword) {
    return
  }

  await ensurePageSettingsLoaded()
  searchName.value = keyword
  loading.value = true
  error.value = ''
  applyDefaultFilters()
  userTagSummaries.value = {}
  showDetailModal.value = false
  selectedGameDetail.value = null
  selectedMatchHistory.value = null

  try {
    const summoner = await gameStore.fetchSummonerByName(keyword)
    if (!summoner) {
      searchResult.value = null
      searchRank.value = null
      searchUserTag.value = null
      searchRankedWinRates.value = null
      searchMatchHistory.value = []
      error.value = t('summoner.notFound')
      return
    }

    searchResult.value = summoner
    const [rank, userTag, winRates] = await Promise.all([
      apiClient.getRank(summoner.puuid),
      apiClient.getUserTagByPuuid(summoner.puuid, DEFAULT_ANALYSIS_QUEUE_MODE),
      apiClient.getRankedWinRates(summoner.puuid)
    ])

    searchRank.value = rank
    searchUserTag.value = userTag
    searchRankedWinRates.value = winRates

    await loadMatchHistory()
  } catch (err) {
    console.error('Failed to search summoner', err)
    error.value = t('summoner.searchFailed')
  } finally {
    loading.value = false
  }
}

async function refreshCurrentSummoner() {
  if (!searchResult.value) {
    return
  }

  loading.value = true
  error.value = ''

  try {
    await loadMatchHistory({ forceRefresh: true })

    const [rank, userTag, winRates] = await Promise.all([
      apiClient.getRank(searchResult.value.puuid),
      apiClient.getUserTagByPuuid(searchResult.value.puuid, DEFAULT_ANALYSIS_QUEUE_MODE),
      apiClient.getRankedWinRates(searchResult.value.puuid)
    ])

    searchRank.value = rank
    searchUserTag.value = userTag
    searchRankedWinRates.value = winRates
  } catch (err) {
    console.error('Failed to refresh summoner', err)
    error.value = t('summoner.searchFailed')
  } finally {
    loading.value = false
  }
}

async function loadMatchHistory(options?: { forceRefresh?: boolean }) {
  if (!searchResult.value) {
    return
  }

  loading.value = true
  try {
    const begIndex = (currentPage.value - 1) * pageSize
    const endIndex = Math.min(begIndex + pageSize - 1, maxTotalRecords - 1)

    const matches = hasFilters.value
      ? await apiClient.getFilteredMatchHistory(searchResult.value.puuid, {
          begIndex,
          endIndex,
          championId: filterChampionId.value > 0 ? filterChampionId.value : undefined,
          queueId: filterQueueId.value > 0 ? filterQueueId.value : undefined,
          forceRefresh: options?.forceRefresh === true
        })
      : await apiClient.getMatchHistory(searchResult.value.puuid, begIndex, endIndex, {
          forceRefresh: options?.forceRefresh === true
        })

    searchMatchHistory.value = matches
    await loadVisibleUserTagSummaries(matches)
  } catch (err) {
    console.error('Failed to load match history', err)
    searchMatchHistory.value = []
    userTagSummaries.value = {}
    if (!error.value) {
      error.value = t('summoner.historyLoadFailed')
    }
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
  if (!searchResult.value) {
    return null
  }

  const identity = (match.participantIdentities || []).find(
    item => item.player?.puuid === searchResult.value?.puuid
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

function getCurrentPlayerName(match: MatchHistory): string {
  return getCurrentPlayer(match)?.playerName || selectedSummonerName.value
}

function isMatchWin(match: MatchHistory): boolean {
  return Boolean(getCurrentPlayer(match)?.stats?.win)
}

function getChampionUrl(championId?: number): string {
  return championId && championId > 0
    ? `http://127.0.0.1:8080/api/v1/asset/champion/${championId}`
    : ''
}

function getProfileIconUrl(profileIconId?: number): string {
  return profileIconId ? `http://127.0.0.1:8080/api/v1/asset/profile/${profileIconId}` : ''
}

function formatRankText(queueInfo: QueueInfo | null): string {
  if (!queueInfo || !queueInfo.tier || queueInfo.tier === 'UNRANKED') {
    return t('tier.UNRANKED')
  }
  if (queueInfo.displayRank) {
    return queueInfo.displayRank
  }
  const tier = queueInfo.tierCn || queueInfo.tier
  const division = queueInfo.division ? ` ${queueInfo.division}` : ''
  return `${tier}${division} ${queueInfo.leaguePoints || 0} LP`
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

function formatSummonerName(summoner: Summoner | null): string {
  if (!summoner) {
    return ''
  }
  return summoner.tagLine ? `${summoner.gameName}#${summoner.tagLine}` : summoner.gameName
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
  if (currentPage.value >= totalPages.value) {
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

async function applyRouteQueryName(value: unknown) {
  if (typeof value !== 'string' || !value.trim()) {
    return
  }
  if (value === searchName.value && searchResult.value) {
    return
  }
  await searchSummoner(value)
}

onMounted(async () => {
  await ensurePageSettingsLoaded()
  await applyRouteQueryName(route.query.name)
})

watch(
  () => route.query.name,
  async value => {
    await applyRouteQueryName(value)
  }
)
</script>

<style scoped>
.summoner-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-shell,
.history-shell,
.state-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 16px;
}

.search-shell {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
}

.search-copy h1,
.lookup-filter-copy h2 {
  margin: 0;
  color: var(--text-primary);
}

.search-copy p,
.lookup-filter-copy p,
.state-card span {
  margin: 6px 0 0;
  color: var(--text-secondary);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: min(460px, 100%);
}

.search-input,
.filter-select {
  min-width: 0;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.search-input {
  flex: 1;
}

.search-btn,
.ghost-btn {
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease, opacity 0.15s ease;
}

.search-btn {
  background: linear-gradient(135deg, #5ca3ea, #3d9b7a);
  color: #fff;
}

.ghost-btn {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border-color: var(--border-color);
}

.search-btn:hover:not(:disabled),
.ghost-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.search-btn:disabled,
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

.state-card.error {
  border-color: rgba(196, 92, 92, 0.4);
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

.lookup-account-strip,
.lookup-filter-bar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.035), rgba(255, 255, 255, 0.018));
}

.lookup-account-strip {
  align-items: center;
  padding: 16px;
}

.lookup-account-profile {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.lookup-avatar {
  width: 68px;
  height: 68px;
  flex: 0 0 68px;
  border-radius: 16px;
  object-fit: cover;
  background: var(--bg-tertiary);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.lookup-account-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.section-eyebrow {
  color: var(--text-secondary);
  font-size: 12px;
}

.lookup-account-copy h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 26px;
  line-height: 1.1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-pills {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.rank-pills span {
  padding: 6px 9px;
  border-radius: 999px;
  background: rgba(92, 163, 234, 0.12);
  color: var(--text-secondary);
  font-size: 12px;
}

.lookup-account-stats {
  display: flex;
  align-items: stretch;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.lookup-stat {
  min-width: 96px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.035);
}

.lookup-stat span {
  display: block;
  color: var(--text-secondary);
  font-size: 12px;
}

.lookup-stat strong {
  display: block;
  margin-top: 4px;
  color: var(--text-primary);
  font-size: 18px;
}

.lookup-filter-bar {
  align-items: flex-end;
  padding: 14px 16px;
}

.lookup-filter-copy {
  min-width: 0;
}

.lookup-filter-copy h2 {
  font-size: 20px;
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
  .search-shell,
  .lookup-account-strip,
  .lookup-filter-bar,
  .pagination {
    flex-direction: column;
    align-items: stretch;
  }

  .search-bar,
  .filters {
    min-width: 0;
  }

  .lookup-account-stats,
  .filters {
    justify-content: flex-start;
  }

  .lookup-account-copy h2 {
    font-size: 22px;
  }

  .filter-select,
  .filters .ghost-btn {
    flex: 1 1 150px;
  }

  .match-card-main,
  .roster-grid {
    grid-template-columns: 1fr;
  }

  .pagination {
    gap: 10px;
  }
}

@media (max-width: 430px) {
  .search-shell,
  .history-shell {
    padding: 14px;
  }

  .search-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .lookup-account-profile {
    align-items: flex-start;
  }

  .lookup-avatar {
    width: 56px;
    height: 56px;
    flex-basis: 56px;
    border-radius: 14px;
  }

}
</style>
