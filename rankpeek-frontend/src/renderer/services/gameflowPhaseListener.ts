import type { GameState } from '../types/api.ts'

export type GameflowPhaseCallback = (phase: string) => void

export interface GameflowPhaseListenerOptions {
  connect?: () => void
  onGameState?: (callback: (state: GameState) => void) => () => void
  getGamePhase?: () => Promise<string>
  logger?: Pick<Console, 'debug' | 'warn'>
}

export function normalizeGameflowPhase(phase: unknown): string {
  return typeof phase === 'string' ? phase.trim() : ''
}

async function getDefaultWsClient() {
  const { wsClient } = await import('../api/websocketClient.ts')
  return wsClient
}

function defaultConnect() {
  void getDefaultWsClient().then((client) => client.connect())
}

function defaultOnGameState(callback: (state: GameState) => void): () => void {
  let active = true
  let unsubscribe: (() => void) | null = null

  void getDefaultWsClient().then((client) => {
    if (!active) return
    unsubscribe = client.onGameState(callback)
  })

  return () => {
    active = false
    unsubscribe?.()
    unsubscribe = null
  }
}

async function getDefaultGamePhase(): Promise<string> {
  const { apiClient } = await import('../api/httpClient.ts')
  return apiClient.getGamePhase()
}

export function listenGameflowPhase(
  onPhaseChange: GameflowPhaseCallback,
  options: GameflowPhaseListenerOptions = {}
): () => void {
  const connect = options.connect ?? defaultConnect
  const onGameState = options.onGameState ?? defaultOnGameState
  const getGamePhase = options.getGamePhase ?? getDefaultGamePhase
  const logger = options.logger ?? console

  let disposed = false
  let lastPhase = ''
  let phaseLookupRequestId = 0

  function emitPhase(phase: string) {
    if (disposed) return

    const normalizedPhase = normalizeGameflowPhase(phase) || 'None'
    if (normalizedPhase === lastPhase) return

    lastPhase = normalizedPhase
    logger.debug(`[gameflow] phase=${normalizedPhase}`)
    onPhaseChange(normalizedPhase)
  }

  connect()

  const unsubscribe = onGameState((state: GameState) => {
    if (!state.connected) {
      phaseLookupRequestId += 1
      emitPhase('None')
      return
    }

    const payloadPhase = normalizeGameflowPhase(state.phase)
    if (payloadPhase) {
      phaseLookupRequestId += 1
      emitPhase(payloadPhase)
      return
    }

    const requestId = ++phaseLookupRequestId
    void getGamePhase()
      .then((phase) => {
        if (requestId !== phaseLookupRequestId) return
        emitPhase(phase)
      })
      .catch((error) => {
        if (requestId !== phaseLookupRequestId) return
        logger.warn('Failed to resolve gameflow phase', error)
      })
  })

  return () => {
    disposed = true
    phaseLookupRequestId += 1
    unsubscribe()
  }
}
