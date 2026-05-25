import { reactive } from 'vue'
import type { GamingAiAnalysisMode } from './gamingAiAnalysisPreview.ts'
import type {
  GamingAiPlayerInsightEvent,
  GamingAiPlayerStreamVerdict,
  GamingAiStreamState
} from './gamingAiServerStream.ts'

export interface GamingAiInlineModeState {
  streamState: GamingAiStreamState
  streamError: string
  playerVerdicts: Record<string, GamingAiPlayerStreamVerdict>
  playerInsights: Record<string, GamingAiPlayerInsightEvent>
  requestKey: string
  requestId: number
}

export interface GamingAiInlineRun {
  controller: AbortController
  requestId: number
}

export const gamingAiInlineState = reactive<Record<GamingAiAnalysisMode, GamingAiInlineModeState>>({
  teammate: createModeState(),
  opponent: createModeState()
})

let requestSerial = 0
const activeControllers: Record<GamingAiAnalysisMode, AbortController | null> = {
  teammate: null,
  opponent: null
}

export function beginGamingAiInlineRun(mode: GamingAiAnalysisMode, requestKey: string): GamingAiInlineRun {
  cancelGamingAiInlineRun(mode)
  const controller = new AbortController()
  const requestId = ++requestSerial
  activeControllers[mode] = controller
  Object.assign(gamingAiInlineState[mode], {
    streamState: 'preparing',
    streamError: '',
    playerVerdicts: {},
    playerInsights: {},
    requestKey,
    requestId
  })
  return { controller, requestId }
}

export function isGamingAiInlineRunCurrent(
  mode: GamingAiAnalysisMode,
  requestId: number,
  controller: AbortController
): boolean {
  return activeControllers[mode] === controller && gamingAiInlineState[mode].requestId === requestId
}

export function setGamingAiInlineStreamState(
  mode: GamingAiAnalysisMode,
  requestId: number,
  streamState: GamingAiStreamState
): void {
  if (gamingAiInlineState[mode].requestId === requestId) {
    gamingAiInlineState[mode].streamState = streamState
  }
}

export function setGamingAiInlineError(mode: GamingAiAnalysisMode, requestId: number, message: string): void {
  if (gamingAiInlineState[mode].requestId !== requestId) {
    return
  }
  gamingAiInlineState[mode].streamError = message
  gamingAiInlineState[mode].streamState = 'failed'
}

export function upsertGamingAiInlineInsight(
  mode: GamingAiAnalysisMode,
  requestId: number,
  insight: GamingAiPlayerInsightEvent
): void {
  if (gamingAiInlineState[mode].requestId !== requestId) {
    return
  }
  gamingAiInlineState[mode].streamState = 'streaming'
  gamingAiInlineState[mode].playerInsights = {
    ...gamingAiInlineState[mode].playerInsights,
    [insight.playerKey]: insight
  }
}

export function upsertGamingAiInlineVerdict(
  mode: GamingAiAnalysisMode,
  requestId: number,
  verdict: GamingAiPlayerStreamVerdict
): void {
  if (gamingAiInlineState[mode].requestId !== requestId) {
    return
  }
  gamingAiInlineState[mode].streamState = 'streaming'
  gamingAiInlineState[mode].playerVerdicts = {
    ...gamingAiInlineState[mode].playerVerdicts,
    [verdict.playerKey]: verdict
  }
}

export function completeGamingAiInlineRun(
  mode: GamingAiAnalysisMode,
  requestId: number,
  controller: AbortController
): void {
  if (!isGamingAiInlineRunCurrent(mode, requestId, controller)) {
    return
  }
  activeControllers[mode] = null
  if (gamingAiInlineState[mode].streamState !== 'failed') {
    gamingAiInlineState[mode].streamState = 'completed'
  }
}

export function cancelGamingAiInlineRun(mode: GamingAiAnalysisMode): void {
  const controller = activeControllers[mode]
  if (controller) {
    controller.abort()
    activeControllers[mode] = null
  }
  if (gamingAiInlineState[mode].streamState === 'preparing' || gamingAiInlineState[mode].streamState === 'streaming') {
    gamingAiInlineState[mode].streamState = 'idle'
  }
}

export function clearGamingAiInlineMode(mode: GamingAiAnalysisMode): void {
  cancelGamingAiInlineRun(mode)
  Object.assign(gamingAiInlineState[mode], createModeState())
}

function createModeState(): GamingAiInlineModeState {
  return {
    streamState: 'idle',
    streamError: '',
    playerVerdicts: {},
    playerInsights: {},
    requestKey: '',
    requestId: 0
  }
}
