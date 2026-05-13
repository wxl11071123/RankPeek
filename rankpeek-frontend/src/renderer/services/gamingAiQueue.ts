import type { SessionData } from '@/types/api'

type GamingQueueInput = Pick<SessionData, 'queueId' | 'typeCn'>

export function normalizeGamingQueueLabel(sessionData: GamingQueueInput): string {
  if (sessionData.queueId === 420) {
    return '单双排位'
  }
  if (sessionData.queueId === 440) {
    return '灵活排位'
  }

  const typeCn = sessionData.typeCn?.trim()
  if (typeCn === '单排/双排' || typeCn === '单双排' || typeCn === '单双排位') {
    return '单双排位'
  }
  if (typeCn === '灵活组排' || typeCn === '灵活排位') {
    return '灵活排位'
  }

  return '未知模式'
}

export function isGamingAiAnalysisEnabledQueue(sessionData: GamingQueueInput): boolean {
  return sessionData.queueId === 420 || sessionData.queueId === 440
}
