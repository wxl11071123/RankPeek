<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from '@/i18n'
import type { MatchHistory, Participant, Stats, UserTagSummary } from '@/types/api'
import {
  getAugmentIconUrl,
  getChampionIconUrl,
  getItemIconSlots,
  getPerkIconUrl,
  getSummonerSpellIconUrl,
  markAssetLoadFailed
} from '@/utils/gameAssetUrls'
import {
  getMatchPerformanceTags,
  type MatchPerformanceTag
} from '@/utils/matchPerformanceTags'

interface MatchLoadoutIconSlot {
  key: string
  kind: 'spell' | 'perk' | 'augment'
  id: number
  url: string
}

const props = withDefaults(defineProps<{
  match: MatchHistory
  currentPuuid?: string
  currentSummonerName?: string
  userTagSummaries?: Record<string, UserTagSummary>
}>(), {
  currentPuuid: '',
  currentSummonerName: '',
  userTagSummaries: () => ({})
})

const emit = defineEmits<{
  'open-detail': [match: MatchHistory]
  'navigate-to-player': [gameName: string, tagLine: string]
}>()

const { t } = useI18n()

const currentPlayer = computed(() => getCurrentPlayer(props.match))
const currentStats = computed(() => currentPlayer.value?.stats)
const isWin = computed(() => Boolean(currentStats.value?.win))
const blueTeamSlots = computed(() => getTeamChampionSlots(props.match, 100))
const redTeamSlots = computed(() => getTeamChampionSlots(props.match, 200))
const currentItemSlots = computed(() => getItemIconSlots(currentStats.value))
const currentSpellSlots = computed(() => getSpellSlots(currentPlayer.value))
const currentPerkSlots = computed(() => getPerkSlots(currentPlayer.value, 1))
const currentAugmentSlots = computed(() => getAugmentSlots(currentPlayer.value, 1))
const hasLoadoutIcons = computed(() =>
  currentSpellSlots.value.length > 0 ||
  currentPerkSlots.value.length > 0 ||
  currentAugmentSlots.value.length > 0
)
const performanceTags = computed<MatchPerformanceTag[]>(() =>
  getMatchPerformanceTags(currentPlayer.value, props.match.participants || [])
)

function getTeamChampionSlots(match: MatchHistory, teamId: number): Array<Participant | null> {
  const players = (match.participants || [])
    .filter(participant => participant.teamId === teamId)
    .sort((left, right) => left.participantId - right.participantId)
    .slice(0, 5)

  return Array.from({ length: 5 }, (_, index) => players[index] || null)
}

function getCurrentPlayer(match: MatchHistory): Participant | null {
  if (!props.currentPuuid) {
    return null
  }

  const identity = (match.participantIdentities || []).find(
    item => item.player?.puuid === props.currentPuuid
  )
  if (!identity) {
    return null
  }

  return (match.participants || []).find(
    item => item.participantId === identity.participantId
  ) || null
}

function getSpellSlots(participant: Participant | null | undefined): MatchLoadoutIconSlot[] {
  return [participant?.spell1Id, participant?.spell2Id]
    .map<MatchLoadoutIconSlot | null>((spellId, index) => {
      const id = normalizePositiveInteger(spellId)
      const url = getSummonerSpellIconUrl(id)
      return id && url
        ? {
            key: `spell-${index}-${id}`,
            kind: 'spell' as const,
            id,
            url
          }
        : null
    })
    .filter((slot): slot is MatchLoadoutIconSlot => slot !== null)
}

function getPerkSlots(participant: Participant | null | undefined, limit: number): MatchLoadoutIconSlot[] {
  return getStatIconSlots(participant, ['perk0'], getPerkIconUrl, 'perk', limit)
}

function getAugmentSlots(participant: Participant | null | undefined, limit: number): MatchLoadoutIconSlot[] {
  return getStatIconSlots(
    participant,
    ['playerAugment1', 'playerAugment2', 'playerAugment3', 'playerAugment4'],
    getAugmentIconUrl,
    'augment',
    limit
  )
}

function getStatIconSlots(
  participant: Participant | null | undefined,
  keys: string[],
  resolveUrl: (id?: number | null) => string,
  kind: MatchLoadoutIconSlot['kind'],
  limit: number
): MatchLoadoutIconSlot[] {
  const statsRecord = participant?.stats as unknown as Record<string, unknown> | null | undefined
  const participantRecord = participant as unknown as Record<string, unknown> | null | undefined
  return keys
    .map(key => {
      const id = normalizePositiveInteger(statsRecord?.[key] ?? participantRecord?.[key])
      const url = resolveUrl(id)
      return id && url
        ? {
            key: `${kind}-${key}-${id}`,
            kind,
            id,
            url
          }
        : null
    })
    .filter((slot): slot is MatchLoadoutIconSlot => slot !== null)
    .slice(0, limit)
}

function normalizePositiveInteger(value: unknown): number | null {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : null
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

function formatKda(stats?: Stats): string {
  return `${stats?.kills || 0} / ${stats?.deaths || 0} / ${stats?.assists || 0}`
}

function displayMode(match: MatchHistory): string {
  return match.queueName || match.gameMode || t('common.unknownMode')
}
</script>

<template>
  <article
    class="match-history-card"
    :class="{ win: isWin, loss: !isWin }"
    @click="emit('open-detail', match)"
  >
    <div class="result-rail" :class="{ win: isWin, loss: !isWin }" aria-hidden="true"></div>

    <div class="match-meta">
      <strong class="result-text">{{ isWin ? t('common.win') : t('common.loss') }}</strong>
      <span>{{ displayMode(match) }}</span>
      <span>{{ formatShortDate(match.gameCreation) }} · {{ formatDuration(match.gameDuration) }}</span>
    </div>

    <div class="player-summary">
      <div class="champion-block">
        <img
          v-if="getChampionIconUrl(currentPlayer?.championId)"
          class="champion-avatar"
          :src="getChampionIconUrl(currentPlayer?.championId)"
          alt=""
          @error="markAssetLoadFailed"
        />
        <span v-else class="champion-avatar placeholder"></span>
      </div>

      <div v-if="hasLoadoutIcons" class="loadout-stack" aria-label="summoner spells and runes">
        <div v-if="currentSpellSlots.length" class="loadout-row spell-row">
          <span
            v-for="slot in currentSpellSlots"
            :key="slot.key"
            class="loadout-slot"
            :class="`loadout-slot-${slot.kind}`"
          >
            <img :src="slot.url" alt="" @error="markAssetLoadFailed" />
          </span>
        </div>
        <div v-if="currentPerkSlots.length || currentAugmentSlots.length" class="loadout-row rune-row">
          <span
            v-for="slot in currentPerkSlots"
            :key="slot.key"
            class="loadout-slot"
            :class="`loadout-slot-${slot.kind}`"
          >
            <img :src="slot.url" alt="" @error="markAssetLoadFailed" />
          </span>
          <span
            v-for="slot in currentAugmentSlots"
            :key="slot.key"
            class="loadout-slot"
            :class="`loadout-slot-${slot.kind}`"
          >
            <img :src="slot.url" alt="" @error="markAssetLoadFailed" />
          </span>
        </div>
      </div>

      <div class="combat-block">
        <strong class="kda-line">{{ formatKda(currentStats) }}</strong>
      </div>

      <div v-if="performanceTags.length" class="performance-tags" aria-label="performance tags">
        <span
          v-for="tag in performanceTags"
          :key="tag.key"
          class="performance-tag"
          :class="`tone-${tag.tone || 'neutral'}`"
        >
          {{ tag.label }}
        </span>
      </div>

      <div class="item-row" aria-label="items">
        <span
          v-for="slot in currentItemSlots"
          :key="`${match.gameId}-item-${slot.index}`"
          class="item-slot"
          :class="{ empty: slot.empty }"
        >
          <img v-if="slot.url" :src="slot.url" alt="" @error="markAssetLoadFailed" />
        </span>
      </div>
    </div>

    <div class="teams-strip" aria-label="5v5 champion thumbnails">
      <div class="team-row blue" aria-label="blue team champions">
        <span
          v-for="(participant, index) in blueTeamSlots"
          :key="`${match.gameId}-blue-${index}`"
          class="mini-champion"
          :class="{ active: participant?.participantId === currentPlayer?.participantId, empty: !participant }"
        >
          <img
            v-if="getChampionIconUrl(participant?.championId)"
            :src="getChampionIconUrl(participant?.championId)"
            alt=""
            @error="markAssetLoadFailed"
          />
        </span>
      </div>

      <div class="team-row red" aria-label="red team champions">
        <span
          v-for="(participant, index) in redTeamSlots"
          :key="`${match.gameId}-red-${index}`"
          class="mini-champion"
          :class="{ active: participant?.participantId === currentPlayer?.participantId, empty: !participant }"
        >
          <img
            v-if="getChampionIconUrl(participant?.championId)"
            :src="getChampionIconUrl(participant?.championId)"
            alt=""
            @error="markAssetLoadFailed"
          />
        </span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.match-history-card {
  --card-bg: rgba(255, 255, 255, 0.82);
  --card-bg-hover: rgba(255, 255, 255, 0.96);
  --card-border: rgba(24, 35, 54, 0.13);
  --card-border-hover: rgba(47, 111, 188, 0.3);
  --slot-bg: rgba(34, 46, 65, 0.08);
  --slot-border: rgba(34, 46, 65, 0.1);
  --avatar-border: rgba(255, 255, 255, 0.88);
  --team-blue: #2f73b8;
  --team-red: #cb4d5a;
  --win-color: #16865a;
  --loss-color: #c54856;
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 86px) minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  width: 100%;
  max-width: 100%;
  min-height: 98px;
  min-width: 0;
  box-sizing: border-box;
  overflow: hidden;
  padding: 10px 12px 10px 18px;
  border: 1px solid var(--card-border);
  border-radius: 8px;
  background: var(--card-bg);
  box-shadow: 0 8px 22px rgba(24, 35, 54, 0.07);
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}

[data-theme="dark"] .match-history-card {
  --card-bg: rgba(17, 24, 39, 0.74);
  --card-bg-hover: rgba(22, 31, 46, 0.88);
  --card-border: rgba(255, 255, 255, 0.07);
  --card-border-hover: rgba(92, 163, 234, 0.32);
  --slot-bg: rgba(255, 255, 255, 0.055);
  --slot-border: rgba(255, 255, 255, 0.07);
  --avatar-border: rgba(255, 255, 255, 0.12);
  --team-blue: #5ca3ea;
  --team-red: #de6f6f;
  --win-color: #62d49e;
  --loss-color: #ee7a82;
  box-shadow: none;
}

[data-theme="light"] .match-history-card {
  --card-bg: rgba(255, 255, 255, 0.82);
  --card-bg-hover: rgba(255, 255, 255, 0.96);
  --card-border: rgba(24, 35, 54, 0.13);
  --card-border-hover: rgba(47, 111, 188, 0.3);
  --slot-bg: rgba(34, 46, 65, 0.08);
  --slot-border: rgba(34, 46, 65, 0.1);
  --avatar-border: rgba(255, 255, 255, 0.88);
  --team-blue: #2f73b8;
  --team-red: #cb4d5a;
  --win-color: #16865a;
  --loss-color: #c54856;
}

.match-history-card:hover {
  transform: translateY(-1px);
  border-color: var(--card-border-hover);
  background: var(--card-bg-hover);
}

.result-rail {
  position: absolute;
  inset: 0 auto 0 0;
  width: 5px;
}

.result-rail.win {
  background: var(--win-color);
}

.result-rail.loss {
  background: var(--loss-color);
}

.match-meta {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  min-width: 0;
  max-width: 100%;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.25;
}

.match-meta span,
.result-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-text {
  color: var(--text-primary);
  font-size: 15px;
  line-height: 1.15;
}

.match-history-card.win .result-text {
  color: var(--win-color);
}

.match-history-card.loss .result-text {
  color: var(--loss-color);
}

.player-summary {
  display: grid;
  grid-template-columns: auto auto minmax(66px, 1fr);
  grid-template-areas:
    "champ loadout combat"
    "tags tags tags"
    "items items items";
  gap: 6px 10px;
  align-content: center;
  min-width: 0;
  max-width: 100%;
}

.champion-block {
  grid-area: champ;
  display: flex;
  align-items: center;
  min-width: 0;
}

.champion-avatar {
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  border: 1px solid var(--avatar-border);
  border-radius: 7px;
  object-fit: cover;
  background: var(--bg-tertiary);
}

.champion-avatar[data-asset-failed='true'],
.loadout-slot img[data-asset-failed='true'],
.item-slot img[data-asset-failed='true'],
.mini-champion img[data-asset-failed='true'] {
  display: none;
}

.placeholder {
  display: inline-block;
}

.combat-block {
  grid-area: combat;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.loadout-stack {
  grid-area: loadout;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 3px;
  min-width: 0;
}

.loadout-row {
  display: flex;
  gap: 3px;
  min-width: 0;
}

.loadout-slot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 19px;
  height: 19px;
  flex: 0 0 19px;
  overflow: hidden;
  border: 1px solid var(--slot-border);
  border-radius: 4px;
  background: var(--slot-bg);
}

.loadout-slot-perk {
  border-radius: 50%;
}

.loadout-slot img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.kda-line {
  color: var(--text-primary);
  font-size: 17px;
  line-height: 1.12;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.performance-tags {
  grid-area: tags;
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.performance-tag {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  padding: 2px 5px;
  border: 1px solid rgba(124, 139, 164, 0.18);
  border-radius: 999px;
  background: rgba(124, 139, 164, 0.1);
  color: var(--text-secondary);
  font-size: 10px;
  font-weight: 700;
  line-height: 1.1;
  white-space: nowrap;
}

.performance-tag.tone-accent {
  border-color: rgba(var(--accent-rgb), 0.28);
  background: rgba(var(--accent-rgb), 0.13);
  color: var(--accent-color);
}

.performance-tag.tone-success {
  border-color: rgba(40, 170, 112, 0.26);
  background: rgba(40, 170, 112, 0.12);
  color: var(--win-color);
}

.performance-tag.tone-warning {
  border-color: rgba(219, 164, 60, 0.3);
  background: rgba(219, 164, 60, 0.13);
  color: #d89b35;
}

.performance-tag.tone-danger {
  border-color: rgba(207, 83, 96, 0.28);
  background: rgba(207, 83, 96, 0.12);
  color: var(--loss-color);
}

.item-row {
  grid-area: items;
  display: flex;
  flex-wrap: nowrap;
  gap: 3px;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.item-slot {
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  border-radius: 4px;
  background: var(--slot-bg);
  overflow: hidden;
}

.item-slot img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.teams-strip {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.team-row {
  display: flex;
  gap: 4px;
  min-width: 0;
}

.mini-champion {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border: 1px solid var(--slot-border);
  border-radius: 5px;
  background: var(--slot-bg);
  overflow: hidden;
}

.mini-champion.active {
  outline: 2px solid var(--accent-color);
  outline-offset: -2px;
  box-shadow: inset 0 0 0 1px rgba(var(--accent-rgb), 0.42);
}

.mini-champion img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.mini-champion.empty {
  opacity: 0.55;
}

@media (max-width: 1100px) {
  .match-history-card {
    grid-template-columns: minmax(0, 82px) minmax(0, 1fr) auto;
    gap: 10px;
  }

  .mini-champion {
    width: 24px;
    height: 24px;
    flex-basis: 24px;
  }
}

@media (max-width: 720px) {
  .match-history-card {
    grid-template-columns: minmax(0, 68px) minmax(0, 1fr) auto;
    gap: 8px;
    min-height: 96px;
    padding-right: 10px;
  }

  .player-summary {
    grid-template-columns: auto auto minmax(42px, 1fr);
    gap: 6px 8px;
  }

  .champion-avatar {
    width: 36px;
    height: 36px;
  }

  .kda-line {
    font-size: 15px;
  }

  .loadout-slot {
    width: 17px;
    height: 17px;
    flex-basis: 17px;
  }

  .performance-tag {
    padding-inline: 4px;
    font-size: 9px;
  }

  .item-row {
    gap: 2px;
  }

  .item-slot {
    width: 18px;
    height: 18px;
    flex-basis: 18px;
  }

  .mini-champion {
    width: 24px;
    height: 24px;
    flex-basis: 24px;
  }

  .team-row {
    gap: 3px;
  }
}
</style>
