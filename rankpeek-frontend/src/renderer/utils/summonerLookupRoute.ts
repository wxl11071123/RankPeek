export interface SummonerLookupIdentity {
  displayName?: string | null
  fullName?: string | null
  riotId?: string | null
  gameName?: string | null
  summonerName?: string | null
  tagLine?: string | null
}

export interface SummonerLookupRoute {
  path: '/summoner'
  query: {
    name: string
  }
}

function normalizeLookupPart(value: string | null | undefined): string {
  return (value ?? '').trim()
}

export function buildSummonerLookupName(identity: SummonerLookupIdentity | null | undefined): string {
  if (!identity) {
    return ''
  }

  const displayName = normalizeLookupPart(identity.displayName ?? identity.fullName ?? identity.riotId)
  if (displayName.includes('#')) {
    return displayName
  }

  const gameName = normalizeLookupPart(identity.gameName ?? identity.summonerName ?? displayName)
  if (!gameName) {
    return ''
  }

  const tagLine = normalizeLookupPart(identity.tagLine)
  return tagLine ? `${gameName}#${tagLine}` : gameName
}

export function createSummonerLookupRoute(name: string | null | undefined): SummonerLookupRoute | null {
  const lookupName = normalizeLookupPart(name)
  if (!lookupName) {
    return null
  }

  return {
    path: '/summoner',
    query: { name: lookupName }
  }
}
