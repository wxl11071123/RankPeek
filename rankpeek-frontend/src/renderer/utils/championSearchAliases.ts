import type { ChampionOption } from '@/types/api'

export const CHAMPION_SEARCH_ALIASES_BY_ID: Record<number, string[]> = {
  11: ['剑圣'],
  16: ['奶妈'],
  37: ['琴女'],
  39: ['女刀', '刀妹'],
  40: ['风女'],
  58: ['鳄鱼'],
  62: ['猴子'],
  63: ['火男'],
  64: ['盲僧'],
  69: ['蛇女'],
  75: ['狗头'],
  76: ['豹女'],
  89: ['日女'],
  91: ['男刀'],
  102: ['龙女'],
  106: ['狗熊'],
  127: ['冰女']
}

export function getChampionSearchAliases(championId: number): string[] {
  if (!Number.isFinite(championId)) {
    return []
  }
  return CHAMPION_SEARCH_ALIASES_BY_ID[championId] || []
}

export function championOptionMatchesSearch(champion: ChampionOption, rawKeyword: string): boolean {
  const keyword = rawKeyword.trim().toLowerCase()
  if (!keyword) {
    return true
  }

  return [
    champion.label,
    champion.realName,
    champion.nickname,
    String(champion.value),
    ...getChampionSearchAliases(champion.value)
  ].some(value => String(value || '').toLowerCase().includes(keyword))
}
