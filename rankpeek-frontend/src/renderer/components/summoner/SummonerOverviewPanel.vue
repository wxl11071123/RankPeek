<script setup lang="ts">
import type { QueueInfo, Summoner, UserTag, WinRate } from '@/types/api'

import unranked from '@/assets/imgs/tier/unranked.png'
import iron from '@/assets/imgs/tier/iron.png'
import bronze from '@/assets/imgs/tier/bronze.png'
import silver from '@/assets/imgs/tier/silver.png'
import gold from '@/assets/imgs/tier/gold.png'
import platinum from '@/assets/imgs/tier/platinum.png'
import emerald from '@/assets/imgs/tier/emerald.png'
import diamond from '@/assets/imgs/tier/diamond.png'
import master from '@/assets/imgs/tier/master.png'
import grandmaster from '@/assets/imgs/tier/grandmaster.png'
import challenger from '@/assets/imgs/tier/challenger.png'

const props = withDefaults(defineProps<{
  summoner: Summoner
  userTag: UserTag | null
  soloRank: QueueInfo | null
  flexRank: QueueInfo | null
  rankedWinRates: Record<string, WinRate> | null
  embedded?: boolean
}>(), {
  embedded: false
})

const emit = defineEmits<{
  copyName: []
}>()

const tierIconMap: Record<string, string> = {
  unranked,
  iron,
  bronze,
  silver,
  gold,
  platinum,
  emerald,
  diamond,
  master,
  grandmaster,
  challenger
}

const tierLabelMap: Record<string, string> = {
  UNRANKED: '未定级',
  IRON: '黑铁',
  BRONZE: '青铜',
  SILVER: '白银',
  GOLD: '黄金',
  PLATINUM: '铂金',
  EMERALD: '翡翠',
  DIAMOND: '钻石',
  MASTER: '超凡大师',
  GRANDMASTER: '傲世宗师',
  CHALLENGER: '最强王者'
}

const divisionLabelMap: Record<string, string> = {
  I: '一',
  II: '二',
  III: '三',
  IV: '四'
}

function fullName(): string {
  return props.summoner.tagLine
    ? `${props.summoner.gameName}#${props.summoner.tagLine}`
    : props.summoner.gameName
}

function copyName() {
  navigator.clipboard.writeText(fullName())
  emit('copyName')
}

function getTierIcon(tier?: string): string {
  const key = tier?.toLowerCase() || 'unranked'
  return tierIconMap[key] || tierIconMap.unranked
}

function getTierText(queueInfo: QueueInfo | null): string {
  if (!queueInfo) {
    return tierLabelMap.UNRANKED
  }

  const tierKey = queueInfo?.tier?.toUpperCase()
  if (!tierKey || tierKey === 'UNRANKED') {
    return tierLabelMap.UNRANKED
  }

  const tierLabel = tierLabelMap[tierKey] || queueInfo.tier
  if (['MASTER', 'GRANDMASTER', 'CHALLENGER'].includes(tierKey)) {
    return `${tierLabel} ${queueInfo.leaguePoints} LP`
  }

  const divisionKey = queueInfo?.division?.toUpperCase()
  const divisionLabel = divisionKey ? (divisionLabelMap[divisionKey] || queueInfo.division) : ''
  return divisionLabel
    ? `${tierLabel} ${divisionLabel} ${queueInfo.leaguePoints} LP`
    : `${tierLabel} ${queueInfo.leaguePoints} LP`
}

function getWinRatePercent(wins: number, losses: number): number {
  const total = wins + losses
  return total > 0 ? Math.round((wins / total) * 100) : 0
}

function getRateColor(rate?: number): string {
  if (rate == null) {
    return 'var(--text-secondary)'
  }
  if (rate >= 55) {
    return '#3d9b7a'
  }
  if (rate <= 40) {
    return '#c45c5c'
  }
  return 'var(--text-primary)'
}

function getKdaColor(kda?: number): string {
  if (kda == null) {
    return 'var(--text-secondary)'
  }
  if (kda >= 4) {
    return '#3d9b7a'
  }
  if (kda <= 1.5) {
    return '#c45c5c'
  }
  return 'var(--text-primary)'
}

function statusMeta() {
  switch (props.userTag?.recordStatus) {
    case 'PRIVATE':
      return {
        label: '战绩隐藏',
        hint: 'LCU 能识别这个玩家，但近期战绩处于隐藏状态。',
        className: 'private'
      }
    case 'EMPTY':
      return {
        label: '暂无近期对局',
        hint: '近期可用战绩不足，暂时无法在这里展示。',
        className: 'empty'
      }
    case 'ERROR':
      return {
        label: '加载失败',
        hint: '最近一次请求没有返回可用的战绩数据。',
        className: 'error'
      }
    default:
      return null
  }
}
</script>

<template>
  <div class="overview-panel" :class="{ embedded: props.embedded }">
    <div class="user-card">
      <div class="user-card-header">
        <div class="avatar-wrapper">
          <img
            class="avatar-img"
            :src="`http://127.0.0.1:8080/api/v1/asset/profile/${summoner.profileIconId}`"
            alt=""
          />
          <div class="level-badge">{{ summoner.summonerLevel }}</div>
        </div>

        <div class="user-info">
          <div class="user-name-row">
            <span class="user-name">{{ summoner.gameName }}</span>
            <button class="copy-btn" type="button" @click="copyName">复制</button>
          </div>
          <div class="user-tag">#{{ summoner.tagLine }}</div>
        </div>
      </div>

      <div v-if="statusMeta()" class="status-card" :class="statusMeta()!.className">
        <div class="status-title">{{ statusMeta()!.label }}</div>
        <div class="status-hint">{{ statusMeta()!.hint }}</div>
      </div>

      <div v-else-if="userTag?.tag?.length" class="tags-row">
        <span
          v-for="tag in userTag.tag"
          :key="tag.tagName"
          class="tag"
          :class="tag.good === true ? 'good' : tag.good === false ? 'bad' : 'neutral'"
          :title="tag.tagDesc"
        >
          {{ tag.tagName }}
        </span>
      </div>
    </div>

    <div
      v-if="userTag?.recordStatus === 'NORMAL' && userTag?.recentData?.friendAndDispute"
      class="relationship-section"
    >
      <div class="relationship-col">
        <div class="section-header good">最佳队友</div>
        <div class="relationship-list">
          <div
            v-for="friend in userTag.recentData.friendAndDispute.friendsSummoner.slice(0, 5)"
            :key="friend.summoner.puuid"
            class="relationship-item"
          >
            <img
              class="relationship-avatar"
              :src="`http://127.0.0.1:8080/api/v1/asset/profile/${friend.summoner.profileIconId}`"
              alt=""
            />
            <span class="relationship-name">{{ friend.summoner.gameName }}</span>
            <span class="relationship-rate" :style="{ color: getRateColor(friend.winRate) }">
              {{ friend.winRate }}%
            </span>
          </div>
          <div v-if="userTag.recentData.friendAndDispute.friendsSummoner.length === 0" class="empty-text">
            暂时还没有重复组到的高胜率队友。
          </div>
        </div>
      </div>

      <div class="relationship-col">
        <div class="section-header bad">棘手对手</div>
        <div class="relationship-list">
          <div
            v-for="enemy in userTag.recentData.friendAndDispute.disputeSummoner.slice(0, 5)"
            :key="enemy.summoner.puuid"
            class="relationship-item"
          >
            <img
              class="relationship-avatar"
              :src="`http://127.0.0.1:8080/api/v1/asset/profile/${enemy.summoner.profileIconId}`"
              alt=""
            />
            <span class="relationship-name">{{ enemy.summoner.gameName }}</span>
            <span class="relationship-rate" :style="{ color: getRateColor(enemy.winRate) }">
              {{ enemy.winRate }}%
            </span>
          </div>
          <div v-if="userTag.recentData.friendAndDispute.disputeSummoner.length === 0" class="empty-text">
            暂时还没有重复遇到的克制对手。
          </div>
        </div>
      </div>
    </div>

    <div class="rank-cards">
      <div class="rank-card">
        <span class="rank-label">单双排</span>
        <img class="rank-img" :src="getTierIcon(soloRank?.tier)" alt="" />
        <div class="rank-tier">{{ getTierText(soloRank) }}</div>
        <div class="win-rate-badge">
          {{ getWinRatePercent(rankedWinRates?.RANKED_SOLO_5x5?.wins || 0, rankedWinRates?.RANKED_SOLO_5x5?.losses || 0) }}%
        </div>
        <div class="rank-wl">
          <span>{{ rankedWinRates?.RANKED_SOLO_5x5?.wins || 0 }}胜</span>
          <span>{{ rankedWinRates?.RANKED_SOLO_5x5?.losses || 0 }}负</span>
        </div>
      </div>

      <div class="rank-card">
        <span class="rank-label">灵活组排</span>
        <img class="rank-img" :src="getTierIcon(flexRank?.tier)" alt="" />
        <div class="rank-tier">{{ getTierText(flexRank) }}</div>
        <div class="win-rate-badge">
          {{ getWinRatePercent(rankedWinRates?.RANKED_FLEX_SR?.wins || 0, rankedWinRates?.RANKED_FLEX_SR?.losses || 0) }}%
        </div>
        <div class="rank-wl">
          <span>{{ rankedWinRates?.RANKED_FLEX_SR?.wins || 0 }}胜</span>
          <span>{{ rankedWinRates?.RANKED_FLEX_SR?.losses || 0 }}负</span>
        </div>
      </div>
    </div>

    <div v-if="userTag?.recordStatus === 'NORMAL' && userTag?.recentData" class="recent-stats-card">
      <div class="recent-stats-header">近期状态</div>

      <div class="stat-row">
        <span class="stat-label">KDA</span>
        <span class="stat-main" :style="{ color: getKdaColor(userTag.recentData.kda) }">
          {{ userTag.recentData.kda?.toFixed(1) || '0.0' }}
        </span>
        <span class="stat-sub">
          {{ userTag.recentData.kills?.toFixed(1) || '0.0' }}/{{ userTag.recentData.deaths?.toFixed(1) || '0.0' }}/{{ userTag.recentData.assists?.toFixed(1) || '0.0' }}
        </span>
      </div>

      <div class="stat-row">
        <span class="stat-label">胜率</span>
        <span
          class="stat-main"
          :style="{ color: getRateColor(getWinRatePercent(userTag.recentData.selectWins || 0, userTag.recentData.selectLosses || 0)) }"
        >
          {{ getWinRatePercent(userTag.recentData.selectWins || 0, userTag.recentData.selectLosses || 0) }}%
        </span>
        <span class="stat-sub">{{ userTag.recentData.selectWins || 0 }}胜 {{ userTag.recentData.selectLosses || 0 }}负</span>
      </div>

      <div class="stat-row">
        <span class="stat-label">伤害</span>
        <span class="stat-main">{{ userTag.recentData.averageDamageDealtToChampions || 0 }}</span>
        <span class="stat-sub">占比 {{ userTag.recentData.damageDealtToChampionsRate || 0 }}%</span>
      </div>

      <div class="stat-row">
        <span class="stat-label">金币</span>
        <span class="stat-main">{{ userTag.recentData.averageGold || 0 }}</span>
        <span class="stat-sub">占比 {{ userTag.recentData.goldRate || 0 }}%</span>
      </div>

      <div class="stat-row">
        <span class="stat-label">参团</span>
        <span class="stat-main">{{ userTag.recentData.groupRate || 0 }}%</span>
        <span class="stat-sub">{{ userTag.recentData.selectModeCn }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.overview-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-panel.embedded {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  align-items: stretch;
}

.user-card,
.relationship-col,
.rank-card,
.recent-stats-card {
  background: var(--bg-secondary);
  border-radius: 12px;
  border: 1px solid var(--border-color);
}

.user-card {
  padding: 16px;
}

.overview-panel.embedded > * {
  min-width: 0;
}

.overview-panel.embedded > .user-card,
.overview-panel.embedded > .relationship-section {
  height: 100%;
}

.user-card-header {
  display: flex;
  gap: 14px;
  align-items: center;
}

.avatar-wrapper {
  position: relative;
}

.avatar-img {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  border: 2px solid var(--border-color);
}

.level-badge {
  position: absolute;
  bottom: -4px;
  left: 50%;
  transform: translateX(-50%);
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 18px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.user-info {
  min-width: 0;
  flex: 1;
}

.user-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copy-btn {
  padding: 4px 8px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  cursor: pointer;
}

.user-tag,
.status-hint,
.empty-text,
.rank-wl,
.stat-sub {
  color: var(--text-secondary);
}

.status-card {
  margin-top: 14px;
  padding: 12px;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-card.private {
  background: rgba(198, 154, 66, 0.12);
}

.status-card.empty {
  background: rgba(128, 128, 128, 0.12);
}

.status-card.error {
  background: rgba(196, 92, 92, 0.12);
}

.status-title,
.recent-stats-header,
.section-header,
.rank-tier,
.stat-main {
  color: var(--text-primary);
}

.tags-row {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
}

.tag.good {
  background: rgba(61, 155, 122, 0.14);
  color: #3d9b7a;
}

.tag.bad {
  background: rgba(196, 92, 92, 0.14);
  color: #c45c5c;
}

.tag.neutral {
  background: rgba(128, 128, 128, 0.16);
  color: var(--text-secondary);
}

.relationship-section {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}

.relationship-col,
.rank-card,
.recent-stats-card {
  padding: 14px;
}

.relationship-col {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.section-header {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: 10px;
}

.section-header.good {
  color: #3d9b7a;
}

.section-header.bad {
  color: #c45c5c;
}

.relationship-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
}

.relationship-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: 42px;
}

.relationship-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
}

.relationship-name {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.relationship-rate {
  font-size: 16px;
  font-weight: 700;
}

.relationship-list > .empty-text {
  font-size: 15px;
  line-height: 1.6;
  padding-top: 8px;
}

.rank-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  align-self: stretch;
}

.rank-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
  justify-content: flex-start;
  height: 100%;
  padding: 18px;
}

.rank-label {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--text-secondary);
  white-space: nowrap;
}

.stat-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-secondary);
}

.rank-img {
  width: 72px;
  height: 72px;
}

.rank-tier {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -0.02em;
  word-break: keep-all;
}

.win-rate-badge {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(92, 163, 234, 0.12);
  color: #5ca3ea;
  text-align: center;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}

.rank-wl {
  display: flex;
  gap: 8px;
  font-size: 16px;
  line-height: 1.5;
  white-space: nowrap;
}

.stat-row {
  display: grid;
  grid-template-columns: 72px auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.stat-row:last-child {
  border-bottom: 0;
}

@media (max-width: 1080px) {
  .overview-panel.embedded,
  .relationship-section,
  .rank-cards {
    grid-template-columns: 1fr;
  }
}
</style>
