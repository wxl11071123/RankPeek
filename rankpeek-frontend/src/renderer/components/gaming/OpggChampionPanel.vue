<template>
  <section class="opgg-panel" aria-labelledby="opgg-panel-title">
    <header class="opgg-panel-header">
      <div class="opgg-title-row">
        <img
          v-if="detail?.championId"
          class="opgg-champion-icon"
          :src="getChampionIconUrl(detail.championId)"
          alt=""
          @error="markAssetLoadFailed"
        />
        <div class="opgg-title-copy">
          <div class="opgg-title-heading">
            <h2 id="opgg-panel-title">{{ panelTitle }}</h2>
            <button
              v-if="showBackButton"
              class="opgg-inline-back-btn"
              type="button"
              @click="$emit('back')"
            >
              返回榜单
            </button>
          </div>
          <p>{{ query.filterLabel || 'KR' }}</p>
        </div>
      </div>

      <div v-if="detail" class="opgg-counter-strip" aria-label="劣势对位">
        <span>劣势对位</span>
        <div v-if="counterChampions.length" class="opgg-counter-icons">
          <img
            v-for="counter in counterChampions"
            :key="counter.championId"
            :src="getChampionIconUrl(counter.championId)"
            alt=""
            @error="markAssetLoadFailed"
          />
        </div>
        <strong v-else class="opgg-counter-empty">-</strong>
      </div>
    </header>

    <div v-if="loading" class="opgg-state">
      <span class="opgg-spinner" aria-hidden="true"></span>
      <span>正在读取 OP.GG 数据</span>
    </div>

    <div v-else-if="error" class="opgg-state opgg-state-error">
      <strong>OP.GG 数据读取失败</strong>
      <span>{{ error }}</span>
      <button class="opgg-retry-btn" type="button" @click="$emit('retry')">重试</button>
    </div>

    <div v-else-if="!detail" class="opgg-state">
      <strong>{{ emptyTitle || '请选择英雄' }}</strong>
      <span>{{ emptyDescription || query.filterLabel || '选中英雄后会读取当前筛选条件的数据。' }}</span>
    </div>

    <div v-else class="opgg-content">
      <div class="opgg-summary">
        <article class="opgg-stat-card">
          <span>胜率</span>
          <strong>{{ formatPercent(detail.stats.winRate) }}</strong>
        </article>
        <article class="opgg-stat-card">
          <span>登场率</span>
          <strong>{{ formatPercent(detail.stats.pickRate) }}</strong>
        </article>
        <article class="opgg-stat-card">
          <span>禁用率</span>
          <strong>{{ formatPercent(detail.stats.banRate) }}</strong>
        </article>
        <article class="opgg-stat-card">
          <span>KDA</span>
          <strong>{{ formatNumber(detail.stats.kda) }}</strong>
        </article>
      </div>

      <section
        v-for="section in buildSections"
        :key="section.key"
        class="opgg-section"
        :class="{ expandable: canExpandSection(section), expanded: isSectionExpanded(section.key), 'opgg-last-items-section': isLastItemsSection(section) }"
        :role="canExpandSection(section) ? 'button' : undefined"
        :tabindex="canExpandSection(section) ? 0 : undefined"
        :aria-expanded="canExpandSection(section) ? isSectionExpanded(section.key) : undefined"
        @click="toggleSectionExpandedFromCard(section)"
        @keydown.enter.prevent="toggleSectionExpandedFromCard(section)"
        @keydown.space.prevent="toggleSectionExpandedFromCard(section)"
      >
        <header class="opgg-section-header">
          <h3>{{ section.title }}</h3>
          <span
            v-if="canExpandSection(section)"
            class="opgg-section-expand-indicator"
            aria-hidden="true"
          >
            {{ isSectionExpanded(section.key) ? '收起' : '展开' }}
          </span>
        </header>
        <div v-if="isLastItemsSection(section) && section.options.length" class="opgg-last-items-grid">
          <article
            v-for="column in lastItemColumns(section)"
            :key="column.key"
            class="opgg-last-item-column"
          >
            <h4>{{ column.title }}</h4>
            <div v-if="column.options.length" class="opgg-last-item-list">
              <article
                v-for="(option, index) in visibleLastItemColumnOptions(section, column)"
                :key="`${column.key}-${index}`"
                class="opgg-last-item-row"
              >
                <div class="opgg-icon-chain opgg-last-item-icon-chain">
                  <span
                    v-for="(id, idIndex) in option.ids"
                    :key="`${column.key}-${index}-${id}-${idIndex}`"
                    class="opgg-icon-slot"
                  >
                    <img
                      v-if="getIconUrl(section.iconType, id)"
                      :src="getIconUrl(section.iconType, id)"
                      alt=""
                      @error="markAssetLoadFailed"
                    />
                    <span v-else>{{ formatSkillId(id) }}</span>
                  </span>
                </div>
                <div class="opgg-last-item-metrics">
                  <strong>{{ formatPercent(option.winRate) }}</strong>
                  <span>{{ formatLastItemGames(option.games) }}</span>
                </div>
              </article>
            </div>
            <p v-else class="opgg-empty-section">暂无数据</p>
          </article>
        </div>
        <div v-else-if="section.options.length" class="opgg-build-list">
          <article
            v-for="(option, index) in visibleSectionOptions(section)"
            :key="`${section.key}-${index}`"
            :class="section.key === 'runes' ? 'opgg-build-row opgg-rune-row' : 'opgg-build-row'"
          >
            <div v-if="section.key === 'runes'" class="opgg-rune-groups">
              <div class="opgg-rune-paths">
                <div class="opgg-rune-line opgg-rune-line-primary">
                  <span class="opgg-icon-slot opgg-rune-page-icon">
                    <img
                      v-if="runeGroup(option).primaryPageId && getIconUrl(section.iconType, runeGroup(option).primaryPageId)"
                      :src="getIconUrl(section.iconType, runeGroup(option).primaryPageId)"
                      alt=""
                      @error="markAssetLoadFailed"
                    />
                  </span>
                  <div class="opgg-rune-icon-chain">
                    <span
                      v-for="(id, idIndex) in runeGroup(option).primaryRuneIds"
                      :key="`primary-rune-${index}-${id}-${idIndex}`"
                      class="opgg-icon-slot"
                    >
                      <img
                        v-if="getIconUrl(section.iconType, id)"
                        :src="getIconUrl(section.iconType, id)"
                        alt=""
                        @error="markAssetLoadFailed"
                      />
                    </span>
                  </div>
                </div>
                <div class="opgg-rune-line opgg-rune-line-secondary">
                  <span class="opgg-icon-slot opgg-rune-page-icon">
                    <img
                      v-if="runeGroup(option).secondaryPageId && getIconUrl(section.iconType, runeGroup(option).secondaryPageId)"
                      :src="getIconUrl(section.iconType, runeGroup(option).secondaryPageId)"
                      alt=""
                      @error="markAssetLoadFailed"
                    />
                  </span>
                  <div class="opgg-rune-icon-chain">
                    <span
                      v-for="(id, idIndex) in runeGroup(option).secondaryRuneIds"
                      :key="`secondary-rune-${index}-${id}-${idIndex}`"
                      class="opgg-icon-slot"
                    >
                      <img
                        v-if="getIconUrl(section.iconType, id)"
                        :src="getIconUrl(section.iconType, id)"
                        alt=""
                        @error="markAssetLoadFailed"
                      />
                    </span>
                  </div>
                </div>
              </div>
              <div class="opgg-rune-shards">
                <div class="opgg-rune-icon-chain">
                  <span
                    v-for="(id, idIndex) in runeGroup(option).statModIds"
                    :key="`stat-rune-${index}-${id}-${idIndex}`"
                    class="opgg-icon-slot opgg-rune-shard-icon"
                  >
                    <img
                      v-if="getIconUrl(section.iconType, id)"
                      :src="getIconUrl(section.iconType, id)"
                      alt=""
                      @error="markAssetLoadFailed"
                    />
                  </span>
                </div>
              </div>
            </div>
            <div v-else-if="section.key === 'skillOrders'" class="opgg-skill-order">
              <span class="opgg-skill-index">#{{ index + 1 }}</span>
              <div class="opgg-skill-lines">
                <div class="opgg-skill-priority">
                  <template
                    v-for="(id, idIndex) in option.ids"
                    :key="`skill-priority-${index}-${id}-${idIndex}`"
                  >
                    <span :class="skillChipClass(id)">{{ formatSkillId(id) }}</span>
                    <span v-if="idIndex < option.ids.length - 1" class="opgg-skill-arrow">›</span>
                  </template>
                </div>
                <div v-if="skillOrderSequence(option).length" class="opgg-skill-sequence">
                  <span
                    v-for="(id, idIndex) in skillOrderSequence(option)"
                    :key="`skill-sequence-${index}-${id}-${idIndex}`"
                    :class="skillChipClass(id)"
                  >
                    {{ formatSkillId(id) }}
                  </span>
                </div>
              </div>
            </div>
            <div v-else class="opgg-icon-chain">
              <span
                v-for="(id, idIndex) in option.ids"
                :key="`${section.key}-${index}-${id}-${idIndex}`"
                class="opgg-icon-slot"
              >
                <img
                  v-if="getIconUrl(section.iconType, id)"
                  :src="getIconUrl(section.iconType, id)"
                  alt=""
                  @error="markAssetLoadFailed"
                />
                <span v-else>{{ formatSkillId(id) }}</span>
              </span>
            </div>
            <div class="opgg-build-meta">
              <div class="opgg-build-meta-column opgg-build-pick">
                <span>选择率</span>
                <strong>{{ formatPercent(option.pickRate) }}</strong>
              </div>
              <div class="opgg-build-meta-column opgg-build-win">
                <span>胜率</span>
                <strong>{{ formatPercent(option.winRate) }}</strong>
              </div>
              <div class="opgg-build-meta-column opgg-build-sample">
                <span>样本</span>
                <em>{{ formatGameCount(option.games) }}</em>
              </div>
            </div>
          </article>
        </div>
        <p v-else class="opgg-empty-section">暂无数据</p>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { OpggBuildOption, OpggChampionCounter, OpggChampionDetail } from '@/services/rankpeekServerClient.ts'
import type { OpggChampionQuery } from '@/services/opggChampionQuery'
import { splitOpggRuneIds, type OpggRuneGroups } from '@/services/opggRuneGroups.ts'
import {
  getAugmentIconUrl,
  getChampionIconUrl,
  getItemIconUrl,
  getPerkIconUrl,
  getSummonerSpellIconUrl,
  markAssetLoadFailed
} from '@/utils/gameAssetUrls'

type IconType = 'spell' | 'perk' | 'item' | 'skill' | 'augment'

interface BuildSection {
  key: string
  title: string
  iconType: IconType
  options: OpggBuildOption[]
}

interface LastItemColumn {
  key: string
  title: string
  options: OpggBuildOption[]
}

const INITIAL_VISIBLE_BUILD_OPTIONS = 2
const LAST_ITEM_COLUMN_SIZE = 5
const LAST_ITEM_COLLAPSED_OPTIONS_PER_COLUMN = 2
const LAST_ITEM_COLUMN_TITLES = ['第四件装备', '第五件装备', '第六件装备']
const LAST_ITEMS_SECTION_KEY = 'lastItems'

const props = defineProps<{
  query: OpggChampionQuery
  title?: string
  detail: OpggChampionDetail | null
  counters?: OpggChampionCounter[]
  showBackButton?: boolean
  loading: boolean
  error: string
  emptyTitle?: string
  emptyDescription?: string
}>()

defineEmits<{
  retry: []
  back: []
}>()

const expandedSectionKeys = ref<Set<string>>(new Set())

const panelTitle = computed(() => {
  const explicitTitle = props.title?.trim()
  if (explicitTitle) return explicitTitle
  return props.detail?.championName?.trim() || 'OP.GG'
})
const counterChampions = computed(() => (props.counters || []).slice(0, 3))
const runeGroupCache = new WeakMap<OpggBuildOption, OpggRuneGroups>()

const buildSections = computed<BuildSection[]>(() => {
  const detail = props.detail
  if (!detail) return []
  const sections: BuildSection[] = [
    { key: 'summonerSpells', title: '召唤师技能', iconType: 'spell', options: detail.summonerSpells || [] }
  ]
  if (detail.mode === 'arena') {
    sections.push({ key: 'augments', title: '强化符文', iconType: 'augment', options: detail.augments || [] })
  }
  sections.push(
    { key: 'runes', title: '符文', iconType: 'perk', options: detail.runes || [] },
    { key: 'skillOrders', title: '技能点法', iconType: 'skill', options: detail.skillOrders || [] },
    { key: 'starterItems', title: '出门装', iconType: 'item', options: detail.starterItems || [] },
    { key: 'boots', title: '鞋子', iconType: 'item', options: detail.boots || [] },
    { key: 'coreItems', title: '核心装备', iconType: 'item', options: detail.coreItems || [] },
    { key: LAST_ITEMS_SECTION_KEY, title: '第四/五/六件装备', iconType: 'item', options: detail.lastItems || [] }
  )
  return sections
})

watch(() => props.detail?.championId, () => {
  expandedSectionKeys.value = new Set()
})

function visibleSectionOptions(section: BuildSection): OpggBuildOption[] {
  if (isSectionExpanded(section.key)) {
    return section.options
  }
  return section.options.slice(0, INITIAL_VISIBLE_BUILD_OPTIONS)
}

function canExpandSection(section: BuildSection): boolean {
  if (isLastItemsSection(section)) {
    return lastItemColumns(section).some(column => column.options.length > LAST_ITEM_COLLAPSED_OPTIONS_PER_COLUMN)
  }
  return section.options.length > INITIAL_VISIBLE_BUILD_OPTIONS
}

function isLastItemsSection(section: BuildSection): boolean {
  return section.key === LAST_ITEMS_SECTION_KEY
}

function lastItemColumns(section: BuildSection): LastItemColumn[] {
  return LAST_ITEM_COLUMN_TITLES.map((title, index) => {
    const start = index * LAST_ITEM_COLUMN_SIZE
    return {
      key: `${LAST_ITEMS_SECTION_KEY}-${index}`,
      title,
      options: section.options.slice(start, start + LAST_ITEM_COLUMN_SIZE)
    }
  })
}

function visibleLastItemColumnOptions(section: BuildSection, column: LastItemColumn): OpggBuildOption[] {
  if (isSectionExpanded(section.key)) {
    return column.options
  }
  return column.options.slice(0, LAST_ITEM_COLLAPSED_OPTIONS_PER_COLUMN)
}

function isSectionExpanded(sectionKey: string): boolean {
  return expandedSectionKeys.value.has(sectionKey)
}

function toggleSectionExpanded(sectionKey: string) {
  const nextKeys = new Set(expandedSectionKeys.value)
  if (nextKeys.has(sectionKey)) {
    nextKeys.delete(sectionKey)
  } else {
    nextKeys.add(sectionKey)
  }
  expandedSectionKeys.value = nextKeys
}

function toggleSectionExpandedFromCard(section: BuildSection) {
  if (!canExpandSection(section)) return
  toggleSectionExpanded(section.key)
}

function runeGroup(option: OpggBuildOption): OpggRuneGroups {
  const cached = runeGroupCache.get(option)
  if (cached) {
    return cached
  }
  const groups = splitOpggRuneIds(option.ids || [])
  runeGroupCache.set(option, groups)
  return groups
}

function skillOrderSequence(option: OpggBuildOption): number[] {
  return (option.order?.length ? option.order : option.ids).filter(id => id >= 1 && id <= 4)
}

function getIconUrl(iconType: IconType, id: number): string {
  if (iconType === 'spell') return getSummonerSpellIconUrl(id)
  if (iconType === 'augment') return getAugmentIconUrl(id)
  if (iconType === 'perk') return getPerkIconUrl(id)
  if (iconType === 'item') return getItemIconUrl(id)
  return ''
}

function formatPercent(value?: number | null): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '-'
  return `${(value * 100).toFixed(1)}%`
}

function formatNumber(value?: number | null): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '-'
  return value.toFixed(2)
}

function formatGameCount(value?: number | null): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '-'
  return Math.round(value).toLocaleString()
}

function formatLastItemGames(value?: number | null): string {
  const count = formatGameCount(value)
  return count === '-' ? '-' : `${count} 场次`
}

function formatSkillId(id: number): string {
  const labels: Record<number, string> = {
    1: 'Q',
    2: 'W',
    3: 'E',
    4: 'R'
  }
  return labels[id] || String(id)
}

function skillChipClass(id: number): string {
  return `opgg-skill-chip opgg-skill-${formatSkillId(id).toLowerCase()}`
}
</script>

<style scoped>
.opgg-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  color: var(--text-primary);
}

.opgg-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: nowrap;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color);
}

.opgg-title-row {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.opgg-title-copy {
  min-width: 0;
}

.opgg-title-heading {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.opgg-champion-icon {
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid var(--border-color);
}

.opgg-title-row h2 {
  margin: 0;
  font-size: 21px;
  line-height: 1.1;
}

.opgg-inline-back-btn {
  min-height: 26px;
  padding: 0 10px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
  cursor: pointer;
}

.opgg-title-row p {
  margin: 5px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.opgg-counter-strip {
  flex: 0 0 300px;
  min-width: 210px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 10px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-tertiary);
}

.opgg-counter-strip > span {
  color: var(--text-primary);
  font-size: 20px;
  font-weight: 900;
  line-height: 1;
  white-space: nowrap;
}

.opgg-counter-icons {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.opgg-counter-icons img {
  width: 32px;
  height: 32px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  object-fit: cover;
  background: var(--bg-secondary);
}

.opgg-counter-empty {
  color: var(--text-secondary);
  font-size: 16px;
  line-height: 1;
}

.opgg-content {
  min-height: 0;
  overflow: auto;
  display: grid;
  gap: 12px;
  padding: 14px 16px 16px;
}

.opgg-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(112px, 1fr));
  gap: 10px;
}

.opgg-stat-card {
  min-width: 0;
  padding: 11px 12px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-tertiary);
}

.opgg-stat-card span {
  display: block;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
}

.opgg-stat-card strong {
  display: block;
  margin-top: 5px;
  color: var(--text-primary);
  font-size: 20px;
  line-height: 1.1;
}

.opgg-section {
  min-width: 0;
  padding: 13px;
  border: 1px solid var(--border-color);
  border-radius: 11px;
  background: rgba(var(--accent-rgb), 0.035);
}

.opgg-section.expandable {
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease;
}

.opgg-section.expandable:hover,
.opgg-section.expandable:focus-visible {
  border-color: rgba(var(--accent-rgb), 0.45);
  background: rgba(var(--accent-rgb), 0.06);
  outline: none;
}

.opgg-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.opgg-section h3 {
  margin: 0;
  font-size: 15px;
  line-height: 1.2;
}

.opgg-section-expand-indicator {
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  border: 1px solid rgba(var(--accent-rgb), 0.45);
  border-radius: 7px;
  background: rgba(var(--accent-rgb), 0.12);
  color: var(--accent-color);
  font-size: 12px;
  font-weight: 900;
  line-height: 1;
}

.opgg-section-expand-indicator::after {
  content: "";
  width: 6px;
  height: 6px;
  border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  transform: rotate(45deg) translateY(-1px);
  transition: transform 0.16s ease;
}

.opgg-section.expanded .opgg-section-expand-indicator::after {
  transform: rotate(225deg) translateY(-1px);
}

.opgg-build-list {
  display: grid;
  gap: 9px;
}

.opgg-last-items-grid {
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 9px;
  background: var(--border-color);
}

.opgg-last-item-column {
  min-width: 0;
  background: var(--bg-tertiary);
}

.opgg-last-item-column h4 {
  margin: 0;
  padding: 9px 10px;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 900;
  line-height: 1;
}

.opgg-last-item-list {
  display: grid;
}

.opgg-last-item-row {
  min-width: 0;
  min-height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.055);
}

.opgg-last-item-row:first-child {
  border-top: 0;
}

.opgg-last-item-icon-chain {
  flex: 1 1 auto;
}

.opgg-last-item-metrics {
  flex: 0 0 auto;
  min-width: 64px;
  display: grid;
  gap: 3px;
  justify-items: end;
  text-align: right;
  white-space: nowrap;
}

.opgg-last-item-metrics strong {
  color: #4f8cff;
  font-size: 13px;
  font-weight: 950;
  line-height: 1;
}

.opgg-last-item-metrics span {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.opgg-build-row {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: nowrap;
  gap: 8px;
  padding: 8px;
  border-radius: 9px;
  background: var(--bg-tertiary);
  overflow-x: auto;
}

.opgg-rune-row {
  align-items: stretch;
}

.opgg-rune-groups {
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px 12px;
}

.opgg-rune-paths {
  min-width: 0;
  display: grid;
  gap: 8px;
}

.opgg-rune-line,
.opgg-rune-icon-chain {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: nowrap;
}

.opgg-rune-line {
  gap: 7px;
}

.opgg-rune-page-icon {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border-color: rgba(239, 68, 68, 0.62);
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.13);
}

.opgg-rune-shards {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.opgg-rune-shard-icon {
  background: rgba(var(--accent-rgb), 0.08);
}

.opgg-skill-order {
  min-width: 0;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.opgg-skill-index {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 900;
}

.opgg-skill-lines {
  min-width: 0;
  display: grid;
  gap: 8px;
}

.opgg-skill-priority,
.opgg-skill-sequence {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: nowrap;
}

.opgg-skill-arrow {
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 900;
}

.opgg-skill-chip {
  width: 25px;
  height: 25px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 950;
  line-height: 1;
}

.opgg-skill-sequence .opgg-skill-chip {
  width: 22px;
  height: 22px;
  font-size: 11px;
}

.opgg-skill-q {
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.1);
}

.opgg-skill-w {
  color: #22c55e;
  background: rgba(34, 197, 94, 0.1);
}

.opgg-skill-e {
  color: #f97316;
  background: rgba(249, 115, 22, 0.1);
}

.opgg-skill-r {
  color: #8b5cf6;
  background: rgba(139, 92, 246, 0.12);
}

.opgg-icon-chain {
  min-width: 0;
  flex: 1 1 auto;
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: nowrap;
}

.opgg-icon-slot {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 900;
  overflow: hidden;
}

.opgg-icon-slot img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.opgg-build-meta {
  flex: 0 0 auto;
  align-self: center;
  min-width: 240px;
  display: grid;
  grid-template-columns: repeat(3, max-content);
  align-content: center;
  align-items: start;
  justify-content: flex-end;
  column-gap: 16px;
  text-align: right;
  white-space: nowrap;
}

.opgg-build-meta-column {
  min-width: 0;
  display: grid;
  grid-template-rows: auto auto;
  align-content: start;
  gap: 4px;
  justify-items: end;
}

.opgg-build-meta strong {
  color: var(--text-primary);
  font-size: 17px;
  line-height: 1.1;
}

.opgg-build-meta span {
  color: #38bdf8;
  font-size: 13px;
  font-weight: 900;
  font-style: normal;
  line-height: 1.1;
  white-space: nowrap;
}

.opgg-build-sample em {
  position: static;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
  font-style: normal;
  line-height: 1;
  white-space: nowrap;
}

.opgg-state {
  min-height: 220px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  padding: 28px;
  color: var(--text-secondary);
  text-align: center;
}

.opgg-state strong {
  color: var(--text-primary);
  font-size: 17px;
}

.opgg-state-error {
  color: #f59e9e;
}

.opgg-spinner {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  border: 3px solid rgba(var(--accent-rgb), 0.18);
  border-top-color: rgba(var(--accent-rgb), 0.86);
  animation: opgg-spin 0.8s linear infinite;
}

.opgg-retry-btn {
  min-height: 32px;
  padding: 0 14px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-weight: 800;
  cursor: pointer;
}

.opgg-empty-section {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

@keyframes opgg-spin {
  to {
    transform: rotate(360deg);
  }
}

</style>
