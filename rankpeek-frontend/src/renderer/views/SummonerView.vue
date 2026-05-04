<template>
  <div class="summoner-view">
    <SummonerMatchHistoryPanel
      :summoner="resolvedSearchResult"
      variant="lookup"
      :connected="gameStore.connected"
      :local-cache-enabled="lookupUsesLocalCache"
      :lookup-query="searchName"
      :lookup-loading="loading"
      :lookup-error="error"
      @update:lookup-query="searchName = $event"
      @lookup="searchSummoner()"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import SummonerMatchHistoryPanel from '@/components/summoner/SummonerMatchHistoryPanel.vue'
import { useGameStore } from '@/stores/game'
import { useI18n } from '@/i18n'
import type { Summoner } from '@/types/api'

const gameStore = useGameStore()
const route = useRoute()
const { t } = useI18n()

const searchName = ref('')
const searchResult = ref<Summoner | null>(null)
const loading = ref(false)
const error = ref('')
let searchRequestId = 0

const resolvedSearchResult = computed(() => searchResult.value)
const lookupUsesLocalCache = computed(() =>
  Boolean(
    gameStore.currentSummoner?.puuid &&
    searchResult.value?.puuid &&
    gameStore.currentSummoner.puuid === searchResult.value.puuid
  )
)

function formatSummonerName(summoner: Summoner): string {
  return summoner.tagLine ? `${summoner.gameName}#${summoner.tagLine}` : summoner.gameName
}

function normalizeLookupName(value: string | null | undefined): string {
  return (value ?? '').trim().toLocaleLowerCase()
}

function summonerMatchesLookup(summoner: Summoner, keyword: string): boolean {
  const normalizedKeyword = normalizeLookupName(keyword)
  if (!normalizedKeyword) {
    return false
  }

  return normalizedKeyword === normalizeLookupName(formatSummonerName(summoner)) ||
    normalizedKeyword === normalizeLookupName(summoner.gameName)
}

function resolveCurrentSummonerLookup(keyword: string): Summoner | null {
  const currentSummoner = gameStore.currentSummoner
  if (!currentSummoner?.puuid || !summonerMatchesLookup(currentSummoner, keyword)) {
    return null
  }
  return currentSummoner
}

async function searchSummoner(nameOverride?: string) {
  const keyword = (nameOverride ?? searchName.value).trim()
  if (!keyword) {
    return
  }

  const requestId = ++searchRequestId
  searchName.value = keyword
  error.value = ''
  searchResult.value = null

  const currentSummoner = resolveCurrentSummonerLookup(keyword)
  if (currentSummoner) {
    searchResult.value = currentSummoner
    loading.value = false
    return
  }

  loading.value = true

  try {
    const summoner = await gameStore.fetchSummonerByName(keyword)
    if (requestId !== searchRequestId) {
      return
    }

    if (!summoner) {
      error.value = t('summoner.notFound')
      return
    }

    searchResult.value = summoner
  } catch (err) {
    if (requestId !== searchRequestId) {
      return
    }

    console.error('Failed to search summoner', err)
    searchResult.value = null
    error.value = t('summoner.searchFailed')
  } finally {
    if (requestId === searchRequestId) {
      loading.value = false
    }
  }
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
  await applyRouteQueryName(route.query.name)
})

watch(
  () => route.query.name,
  async value => {
    await applyRouteQueryName(value)
  }
)

watch(
  () => gameStore.currentSummoner,
  currentSummoner => {
    if (!currentSummoner || !searchName.value || !summonerMatchesLookup(currentSummoner, searchName.value)) {
      return
    }
    searchRequestId += 1
    searchResult.value = currentSummoner
    error.value = ''
    loading.value = false
  }
)
</script>

<style scoped>
.summoner-view {
  display: flex;
  flex-direction: column;
  gap: 22px;
}
</style>
