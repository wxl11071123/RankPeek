import type { CoachSummaryDataQuality, CoachSummaryInputSnapshot } from './coachSummaryInputSnapshot'

export const COACH_SUMMARY_PROMPT_VERSION = 'coach_summary.prompt.v2' as const
export const COACH_SUMMARY_REPORT_SCHEMA_VERSION = 'coach_summary_report.v1' as const

export interface CoachSummaryPromptPayload {
  promptVersion: typeof COACH_SUMMARY_PROMPT_VERSION
  systemPrompt: string
  userPrompt: string
}

export interface BuildCoachSummaryPromptPayloadParams {
  snapshot: Pick<CoachSummaryInputSnapshot, 'schemaVersion' | 'inputHash' | 'analysisBrief' | 'dataQuality' | 'metadata'>
  historicalCoachContext?: string | null
}

interface CoachSummaryPromptMeta {
  inputHash: string
  snapshotSchemaVersion: string
  analysisBriefSchemaVersion: string
  overallState: Pick<CoachSummaryInputSnapshot['analysisBrief']['overallState'], 'state' | 'label' | 'reasons'>
  dataQuality: Pick<
    CoachSummaryDataQuality,
    | 'confidence'
    | 'hasAllTimelines'
    | 'hasAllParticipantDetails'
    | 'hasAllRuneData'
    | 'hasAllItemData'
    | 'missingTimelineMatchRefs'
    | 'missingParticipantDetailMatchRefs'
    | 'missingEconomyDiffMatchRefs'
  >
  generatedAt: string
  matchRefs: string[]
  anchorMatchRefs: string[]
}

const EMPTY_HISTORICAL_CONTEXT = '无历史电子教练报告；这是该账号的基线报告，不要编造成长趋势。'

const SYSTEM_PROMPT_LINES = [
  '你是 RankPeek 的长期电子教练，目标是把用户最近20局排位表现转成可延续追踪的训练报告。',
  '你会收到 currentSnapshotText、currentSnapshotMeta 和 historicalCoachContext。currentSnapshotText 是前端整理过的自然语言事实，不是聊天记录。',
  '只使用输入里明确给出的事实；不要补写没有出现的数据、英雄、分路、对手或结论。',
  '不要逐局流水账。每局 m01、m02 等只作为证据池，最终要归纳稳定模式、重复问题、改善迹象和下一阶段训练重点。',
  '证据引用只能使用 m01、m02 这类 matchRef，或输入里已出现的 eventRef/dataRef；不要输出真实 matchId、gameId、puuid、summonerName、gameName、tagLine。',
  '没有历史上下文时，把本次当成基线报告；不能说“变好了/变差了”。有历史上下文时，判断重复问题、明显改善、英雄池/分路收敛、15分钟经济差、参团率、KDA和优势转化趋势。',
  'historicalCoachContext 只用于趋势对比；如果它和 currentSnapshotMeta/currentSnapshotText 冲突，以当前 snapshot 为准。不要把历史报告里的旧问题直接当成本轮问题复述。',
  '如果 dataQuality 不是 high，要降低 confidence，并在 warnings 里说明哪些结论受影响。',
  '低样本英雄不要过度建议；1局样本的 championAdvice 推荐优先使用 observe_more。',
  '重点关注15分钟对位经济差、优势局结束能力、劣势局止损、英雄池稳定性、分路一致性、视野和参团。',
  '先判断 currentSnapshotMeta.overallState，再写标题和摘要。overallState 为 excellent/good 时，标题和首段必须先承认最近状态好，不要因为单个待优化点把报告写成低分或负面主结论。',
  'overallState 为 excellent/good 时，title、cardTitle、shortTitle 必须是正向主结论，不能出现“但是”“但”“需提升”“短板”“仅”等负面转折词；待优化点只能放在 keyFindings、trainingPlan 或 finalSummary 后半段。',
  '当整体胜率、近期走势或主英雄表现为正向时，分路/英雄低胜率样本只能作为观察项，不能盖过标题、摘要和 verdict 的正向判断。',
  '不要把正常提前占位、主动开团或高风险换节奏直接判定为失误。只有输入明确说明同类问题连续复发并造成明确负面结果时，才作为主要风险。',
  '如果整体胜率、近期走势、KDA 或主英雄表现为正向，训练建议只能作为锦上添花的下一步，不要覆盖整体评价。',
  '语气直接、像教练，不客服、不鸡汤、不羞辱玩家。',
  `只输出 JSON，不要 Markdown，不要代码块。JSON 必须符合 ${COACH_SUMMARY_REPORT_SCHEMA_VERSION}。`,
  '顶层必须包含这些字段：title、summary、verdict、keyFindings、trainingPlan、championAdvice、chartBlocks、warnings、finalSummary。不要把报告包在 report/result/data 字段里。',
  'verdict 必须是对象，至少包含 label、score、confidence、summary；confidence 只能是 high、medium、low。',
  `metadata.promptVersion 必须是 ${COACH_SUMMARY_PROMPT_VERSION}，metadata.snapshotSchemaVersion 使用 currentSnapshotMeta.snapshotSchemaVersion。`,
  'summary、verdict.summary 和 finalSummary 都要用自然语言完整表达核心判断；长度由事实量决定，不要为了压缩而省略关键依据。',
  'keyFindings 优先 2 条，样本不足或没有足够事实时才 1 条；trainingPlan 2-3 条；championAdvice 2-5 条。',
  'keyFindings 不要写成编号清单、小标题清单或模板化体检报告。每条把事实、判断和下一步写进同一段；claim 写完整自然句，evidence 写清关键数据，reasoning 解释为什么重要，advice 补一个具体下一步；不要为了凑数拆成多条。',
  'keyFindings 每条必须包含 claim、evidence、reasoning、advice、confidence、evidenceRefs。'
] as const

export function buildCoachSummaryPromptPayload({
  snapshot,
  historicalCoachContext
}: BuildCoachSummaryPromptPayloadParams): CoachSummaryPromptPayload {
  const promptInput = {
    currentSnapshotText: snapshot.analysisBrief.text,
    currentSnapshotMeta: buildPromptMeta(snapshot),
    historicalCoachContext: normalizeHistoricalContext(historicalCoachContext)
  }

  return {
    promptVersion: COACH_SUMMARY_PROMPT_VERSION,
    systemPrompt: SYSTEM_PROMPT_LINES.join('\n'),
    userPrompt: JSON.stringify(promptInput, null, 2)
  }
}

function buildPromptMeta(
  snapshot: BuildCoachSummaryPromptPayloadParams['snapshot']
): CoachSummaryPromptMeta {
  return {
    inputHash: snapshot.inputHash,
    snapshotSchemaVersion: snapshot.schemaVersion,
    analysisBriefSchemaVersion: snapshot.analysisBrief.schemaVersion,
    overallState: snapshot.analysisBrief.overallState,
    dataQuality: {
      confidence: snapshot.dataQuality.confidence,
      hasAllTimelines: snapshot.dataQuality.hasAllTimelines,
      hasAllParticipantDetails: snapshot.dataQuality.hasAllParticipantDetails,
      hasAllRuneData: snapshot.dataQuality.hasAllRuneData,
      hasAllItemData: snapshot.dataQuality.hasAllItemData,
      missingTimelineMatchRefs: snapshot.dataQuality.missingTimelineMatchRefs,
      missingParticipantDetailMatchRefs: snapshot.dataQuality.missingParticipantDetailMatchRefs,
      missingEconomyDiffMatchRefs: snapshot.dataQuality.missingEconomyDiffMatchRefs
    },
    generatedAt: snapshot.metadata.generatedInputAt,
    matchRefs: snapshot.metadata.matchRefs,
    anchorMatchRefs: snapshot.metadata.anchorMatchRefs
  }
}

function normalizeHistoricalContext(value: string | null | undefined): string {
  const trimmed = value?.replace(/\s+/g, ' ').trim()
  return trimmed || EMPTY_HISTORICAL_CONTEXT
}
