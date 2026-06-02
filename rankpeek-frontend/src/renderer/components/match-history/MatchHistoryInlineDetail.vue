<template>
  <section class="inline-match-detail">
    <div class="inline-detail-toolbar">
      <div class="inline-detail-tabs" role="tablist" aria-label="Match detail tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="inline-detail-tab"
          :class="{ active: activeTabValue === tab.key }"
          role="tab"
          :aria-selected="activeTabValue === tab.key"
          @click="setActiveTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>
      <span v-if="detailStatus === 'loading'" class="detail-status">Loading detail...</span>
      <span v-else-if="detailStatus === 'error'" class="detail-status error">Using cached summary</span>
    </div>

    <div v-if="activeTabValue === 'overview'" class="inline-detail-body">
      <div class="match-summary-strip">
        <span>{{ queueLabel }}</span>
        <span>{{ durationLabel }}</span>
        <span>{{ creationLabel }}</span>
      </div>

      <div class="team-grid">
        <article
          v-for="team in teamGroups"
          :key="team.teamId"
          class="team-detail-table"
          :class="[team.side, team.resultClass]"
        >
          <header class="team-detail-header">
            <strong>{{ team.label }}</strong>
            <span>{{ team.resultLabel }}</span>
          </header>

          <div class="team-row-labels" aria-hidden="true">
            <span>Player</span>
            <span>KDA</span>
            <span>CS</span>
            <span>Gold</span>
            <span>Damage</span>
            <span>Vision</span>
          </div>

          <div
            v-for="player in team.players"
            :key="player.participantId"
            class="participant-row"
            :class="{ current: isCurrentParticipant(player) }"
          >
            <div class="player-cell">
              <img
                class="champion-icon"
                :src="getChampionIconUrl(player.championId)"
                alt=""
                @error="markAssetLoadFailed"
              />
              <button
                v-if="canNavigateToPlayer(player)"
                type="button"
                class="player-name"
                @click="navigateToPlayer(player)"
              >
                {{ getPlayerDisplayName(player) }}
              </button>
              <span v-else class="player-name">{{ getPlayerDisplayName(player) }}</span>
              <small>{{ formatRole(player) }}</small>
            </div>
            <strong>{{ formatKda(player) }}</strong>
            <span>{{ formatCreepScore(player) }}</span>
            <span>{{ formatNumber(readStat(player, 'goldEarned')) }}</span>
            <span>{{ formatNumber(readStat(player, 'totalDamageDealtToChampions')) }}</span>
            <span>{{ formatNumber(readStat(player, 'visionScore')) }}</span>
          </div>
        </article>
      </div>
    </div>

    <div v-else-if="activeTabValue === 'runes'" class="inline-detail-body">
      <div class="loadout-grid">
        <article
          v-for="player in allPlayers"
          :key="`loadout-${player.participantId}`"
          class="loadout-card"
          :class="{ current: isCurrentParticipant(player) }"
        >
          <header>
            <img
              class="champion-icon"
              :src="getChampionIconUrl(player.championId)"
              alt=""
              @error="markAssetLoadFailed"
            />
            <div>
              <strong>{{ getPlayerDisplayName(player) }}</strong>
              <small>{{ formatRole(player) }}</small>
            </div>
          </header>

          <div class="spell-row">
            <img
              v-for="spell in getSpellIds(player)"
              :key="`spell-${player.participantId}-${spell}`"
              :src="getSummonerSpellIconUrl(spell)"
              alt=""
              @error="markAssetLoadFailed"
            />
          </div>

          <div class="item-row">
            <span
              v-for="slot in getItemSlots(player)"
              :key="slot.key"
              class="item-slot"
              :class="{ empty: !slot.itemId }"
            >
              <img
                v-if="slot.itemId"
                :src="getItemIconUrl(slot.itemId)"
                alt=""
                @error="markAssetLoadFailed"
              />
            </span>
          </div>

          <div class="rune-row">
            <span
              v-for="rune in getRuneIds(player)"
              :key="`rune-${player.participantId}-${rune}`"
              class="rune-slot"
            >
              <img :src="getPerkIconUrl(rune)" alt="" @error="markAssetLoadFailed" />
            </span>
            <span v-if="getRuneIds(player).length === 0" class="empty-copy">No rune data</span>
          </div>
        </article>
      </div>
    </div>

    <div v-else class="inline-detail-body">
      <div class="timeline-summary">
        <article>
          <strong>Match</strong>
          <span>{{ queueLabel }}</span>
          <span>{{ durationLabel }}</span>
        </article>

        <article v-for="team in objectiveGroups" :key="`objectives-${team.teamId}`">
          <strong>{{ team.label }}</strong>
          <span>Dragons {{ team.dragons }}</span>
          <span>Barons {{ team.barons }}</span>
          <span>Towers {{ team.towers }}</span>
          <span>Inhibitors {{ team.inhibitors }}</span>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type {
  GameDetail,
  GameParticipant,
  GameParticipantIdentity,
  MatchHistory,
  Participant,
  ParticipantIdentity,
  TeamObjectiveSummary
} from '@/types/api'
import {
  getChampionIconUrl,
  getItemIconUrl,
  getPerkIconUrl,
  getSummonerSpellIconUrl,
  markAssetLoadFailed
} from '@/utils/gameAssetUrls'

type DetailLoadStatus = 'idle' | 'loading' | 'loaded' | 'error'
type InlineDetailTabKey = 'overview' | 'runes' | 'chart'
type ParticipantLike = Participant | GameParticipant
type IdentityLike = ParticipantIdentity | GameParticipantIdentity

interface ItemSlot {
  key: string
  itemId: number
}

const props = withDefaults(defineProps<{
  matchHistory: MatchHistory
  gameDetail: GameDetail | null
  currentPuuid: string
  currentSummonerName: string
  detailStatus?: DetailLoadStatus
  activeTab?: InlineDetailTabKey
}>(), {
  detailStatus: 'idle',
  activeTab: 'overview'
})

const emit = defineEmits<{
  'update:activeTab': [tab: InlineDetailTabKey]
  navigateToPlayer: [gameName: string, tagLine: string]
}>()

const tabs: Array<{ key: InlineDetailTabKey; label: string }> = [
  { key: 'overview', label: 'Overview' },
  { key: 'runes', label: 'Loadout' },
  { key: 'chart', label: 'Timeline' }
]

const activeTabValue = computed(() =>
  tabs.some(tab => tab.key === props.activeTab) ? props.activeTab : 'overview'
)

const participants = computed<ParticipantLike[]>(() => {
  if (props.gameDetail?.participants?.length) {
    return props.gameDetail.participants
  }
  return props.matchHistory.participants ?? []
})

const identities = computed<IdentityLike[]>(() => {
  if (props.gameDetail?.participantIdentities?.length) {
    return props.gameDetail.participantIdentities
  }
  return props.matchHistory.participantIdentities ?? []
})

const allPlayers = computed(() =>
  participants.value.slice().sort((left, right) => left.participantId - right.participantId)
)

const teamGroups = computed(() => [
  createTeamGroup(100, 'Blue Team', 'blue'),
  createTeamGroup(200, 'Red Team', 'red')
].filter(team => team.players.length > 0))

const objectiveGroups = computed(() => [
  createObjectiveGroup(100, 'Blue Team'),
  createObjectiveGroup(200, 'Red Team')
])

const queueLabel = computed(() =>
  props.matchHistory.queueName || props.gameDetail?.gameMode || props.matchHistory.gameMode || 'Unknown queue'
)

const durationLabel = computed(() =>
  formatDuration(props.gameDetail?.gameDuration || props.matchHistory.gameDuration || 0)
)

const creationLabel = computed(() => {
  const value = props.gameDetail?.gameCreation || props.matchHistory.gameCreation || 0
  if (!value) {
    return 'Unknown time'
  }
  const millis = value > 10_000_000_000 ? value : value * 1000
  return new Date(millis).toLocaleString()
})

function setActiveTab(tab: InlineDetailTabKey): void {
  emit('update:activeTab', tab)
}

function createTeamGroup(teamId: number, label: string, side: string) {
  const players = allPlayers.value.filter(player => player.teamId === teamId)
  const won = players.some(player => Boolean(player.stats?.win))
  return {
    teamId,
    label,
    side,
    players,
    resultClass: won ? 'win' : 'loss',
    resultLabel: won ? 'Victory' : 'Defeat'
  }
}

function createObjectiveGroup(teamId: number, label: string) {
  const objectives = getObjectivesForTeam(teamId)
  return {
    teamId,
    label,
    dragons: objectives?.dragonKills ?? 0,
    barons: objectives?.baronKills ?? 0,
    towers: objectives?.turretKills ?? objectives?.turretPlateKills ?? 0,
    inhibitors: objectives?.inhibitorKills ?? 0
  }
}

function getObjectivesForTeam(teamId: number): TeamObjectiveSummary | null {
  const objectives = props.gameDetail?.teamObjectives?.length
    ? props.gameDetail.teamObjectives
    : props.matchHistory.teamObjectives
  return objectives?.find(item => item.teamId === teamId) ?? null
}

function getIdentity(player: ParticipantLike): IdentityLike['player'] | null {
  return identities.value.find(identity => identity.participantId === player.participantId)?.player ?? null
}

function getPlayerDisplayName(player: ParticipantLike): string {
  const identity = getIdentity(player)
  const gameName = identity?.gameName?.trim()
  const tagLine = identity?.tagLine?.trim()
  if (gameName && tagLine) {
    return `${gameName}#${tagLine}`
  }
  return gameName || identity?.summonerName?.trim() || `Player ${player.participantId}`
}

function canNavigateToPlayer(player: ParticipantLike): boolean {
  const identity = getIdentity(player)
  return Boolean(identity?.gameName?.trim() && identity?.tagLine?.trim())
}

function navigateToPlayer(player: ParticipantLike): void {
  const identity = getIdentity(player)
  const gameName = identity?.gameName?.trim()
  const tagLine = identity?.tagLine?.trim()
  if (!gameName || !tagLine) {
    return
  }
  emit('navigateToPlayer', gameName, tagLine)
}

function isCurrentParticipant(player: ParticipantLike): boolean {
  const identity = getIdentity(player)
  const currentPuuid = props.currentPuuid.trim()
  if (currentPuuid && identity?.puuid === currentPuuid) {
    return true
  }
  return getPlayerDisplayName(player).toLowerCase() === props.currentSummonerName.trim().toLowerCase()
}

function readStat(player: ParticipantLike, key: string): number {
  const value = (player.stats as Record<string, unknown> | undefined)?.[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
}

function formatKda(player: ParticipantLike): string {
  return `${readStat(player, 'kills')} / ${readStat(player, 'deaths')} / ${readStat(player, 'assists')}`
}

function formatCreepScore(player: ParticipantLike): string {
  return formatNumber(readStat(player, 'totalMinionsKilled') + readStat(player, 'neutralMinionsKilled'))
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-US').format(Math.round(value))
}

function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return 'Unknown duration'
  }
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = Math.floor(seconds % 60)
  return `${minutes}:${String(remainingSeconds).padStart(2, '0')}`
}

function formatRole(player: ParticipantLike): string {
  const raw = player.teamPosition || player.individualPosition || player.selectedPosition || player.timeline?.teamPosition || player.timeline?.lane || ''
  const normalized = raw.toUpperCase()
  const labels: Record<string, string> = {
    TOP: 'Top',
    JUNGLE: 'Jungle',
    MIDDLE: 'Mid',
    MID: 'Mid',
    BOTTOM: 'Bot',
    UTILITY: 'Support',
    SUPPORT: 'Support'
  }
  return labels[normalized] || raw || 'Unknown role'
}

function getSpellIds(player: ParticipantLike): number[] {
  return [player.spell1Id, player.spell2Id].filter(id => Number.isFinite(id) && id > 0)
}

function getItemSlots(player: ParticipantLike): ItemSlot[] {
  return [0, 1, 2, 3, 4, 5, 6].map(index => ({
    key: `item-${player.participantId}-${index}`,
    itemId: readStat(player, `item${index}`)
  }))
}

function getRuneIds(player: ParticipantLike): number[] {
  return [0, 1, 2, 3, 4, 5]
    .map(index => readStat(player, `perk${index}`))
    .filter(id => Number.isFinite(id) && id > 0)
}
</script>

<style scoped>
.inline-match-detail {
  margin: 10px 0 18px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-secondary);
  color: var(--text-primary);
  overflow: hidden;
}

.inline-detail-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-color);
}

.inline-detail-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.inline-detail-tab {
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-primary);
  color: var(--text-secondary);
  padding: 7px 12px;
  font: inherit;
  cursor: pointer;
}

.inline-detail-tab.active {
  color: var(--text-primary);
  border-color: var(--accent-primary);
}

.detail-status {
  align-self: center;
  color: var(--text-secondary);
  font-size: 12px;
}

.detail-status.error {
  color: var(--warning-color, #d99a32);
}

.inline-detail-body {
  padding: 14px;
}

.match-summary-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
  color: var(--text-secondary);
  font-size: 13px;
}

.match-summary-strip span,
.timeline-summary span,
.detail-status {
  white-space: nowrap;
}

.team-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.team-detail-table,
.loadout-card,
.timeline-summary article {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-primary);
}

.team-detail-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color);
}

.team-detail-header span {
  color: var(--text-secondary);
}

.team-detail-table.win .team-detail-header span {
  color: var(--success-color, #38a169);
}

.team-detail-table.loss .team-detail-header span {
  color: var(--danger-color, #e05252);
}

.team-row-labels,
.participant-row {
  display: grid;
  grid-template-columns: minmax(150px, 1.5fr) repeat(5, minmax(54px, 0.7fr));
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
}

.team-row-labels {
  color: var(--text-tertiary, var(--text-secondary));
  font-size: 11px;
  text-transform: uppercase;
}

.participant-row {
  border-top: 1px solid rgba(255, 255, 255, 0.04);
  color: var(--text-secondary);
  font-size: 13px;
}

.participant-row.current,
.loadout-card.current {
  border-color: var(--accent-primary);
}

.player-cell,
.loadout-card header {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.champion-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  object-fit: cover;
  background: rgba(255, 255, 255, 0.08);
}

.player-name {
  min-width: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-weight: 700;
  padding: 0;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

button.player-name {
  cursor: pointer;
}

.player-cell small,
.loadout-card small,
.empty-copy {
  color: var(--text-tertiary, var(--text-secondary));
  font-size: 12px;
}

.loadout-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.loadout-card {
  display: grid;
  gap: 12px;
  padding: 12px;
}

.spell-row,
.item-row,
.rune-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.spell-row img,
.item-slot,
.rune-slot {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
}

.item-slot.empty {
  border: 1px dashed var(--border-color);
}

.item-slot img,
.rune-slot img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.timeline-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.timeline-summary article {
  display: grid;
  gap: 6px;
  padding: 12px;
}

@media (max-width: 980px) {
  .team-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .team-row-labels {
    display: none;
  }

  .participant-row {
    grid-template-columns: minmax(140px, 1fr) repeat(2, minmax(48px, auto));
  }

  .participant-row span:nth-of-type(n + 3) {
    display: none;
  }
}
</style>
