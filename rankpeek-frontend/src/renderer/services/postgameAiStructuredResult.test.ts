import test from 'node:test'
import assert from 'node:assert/strict'
import {
  parsePartialPostgameAiStructuredResult,
  parsePostgameAiStructuredResult,
  POSTGAME_LADU_LEVELS
} from './postgameAiStructuredResult.ts'

function createStructuredResultText(): string {
  return `\`\`\`json
{
  "schemaVersion": "postgame_review_result.v1",
  "levels": [
    {
      "label": "夯",
      "players": [
        { "playerRef": "你｜我方打野｜凯隐", "championName": "凯隐", "phrase": "前期节奏能开局，后续资源判断要更稳。" },
        { "playerRef": "我方中单｜阿狸", "championName": "阿狸", "phrase": "中期支援把节奏撑住。" }
      ]
    },
    {
      "label": "顶级",
      "players": [
        { "playerRef": "敌方上单｜奎桑提", "championName": "奎桑提", "phrase": "边线抗压和团战入口都清楚。" },
        { "playerRef": "我方辅助｜洛", "championName": "洛", "phrase": "开团选择比较干净。" }
      ]
    },
    {
      "label": "人上人",
      "players": [
        { "playerRef": "我方下路｜金克丝", "championName": "金克丝", "phrase": "输出环境好时能接管团战。" },
        { "playerRef": "敌方中单｜维克托", "championName": "维克托", "phrase": "阵地战威胁稳定。" }
      ]
    },
    {
      "label": "NPC",
      "players": [
        { "playerRef": "敌方打野｜盲僧", "championName": "盲僧", "phrase": "前期做事少，资源交换慢。" },
        { "playerRef": "敌方辅助｜璐璐", "championName": "璐璐", "phrase": "保护在线但主动性不足。" }
      ]
    },
    {
      "label": "拉完了",
      "players": [
        { "playerRef": "我方上单｜盖伦", "championName": "盖伦", "phrase": "对线亏损后没有止损。" },
        { "playerRef": "敌方下路｜伊泽瑞尔", "championName": "伊泽瑞尔", "phrase": "伤害转化不足。" }
      ]
    }
  ],
  "summary": "客观总结：本局我方前期依靠打野和中路获得节奏，但中后期资源团处理不够稳定，最终胜负主要由团战入口和边线止损决定。"
}
\`\`\``
}

test('parses fenced DeepSeek postgame review JSON into a five-level ladu chart', () => {
  const parsed = parsePostgameAiStructuredResult(createStructuredResultText())

  assert.equal(parsed.ok, true)
  if (!parsed.ok) {
    return
  }

  assert.deepEqual(parsed.result.levels.map(level => level.label), POSTGAME_LADU_LEVELS)
  assert.equal(parsed.result.players.length, 10)
  assert.equal(parsed.result.players.filter(player => player.playerRef.includes('你｜')).length, 1)
  assert.equal(parsed.result.players[0]?.level, '夯')
  assert.equal(parsed.result.players[0]?.championName, '凯隐')
  assert.match(parsed.result.summary, /客观总结/)
})

test('rejects incomplete structured postgame results', () => {
  const parsed = parsePostgameAiStructuredResult(JSON.stringify({
    schemaVersion: 'postgame_review_result.v1',
    levels: [
      { label: '夯', players: [{ playerRef: '你｜我方打野｜凯隐', championName: '凯隐', phrase: '一句话。' }] }
    ],
    summary: '客观总结：玩家数量不足。'
  }))

  assert.equal(parsed.ok, false)
  if (parsed.ok) {
    return
  }
  assert.match(parsed.error, /10/)
})

test('partially parses completed player objects from streaming review JSON', () => {
  const parsed = parsePartialPostgameAiStructuredResult(`
DeepSeek 分析
{
  "schemaVersion": "postgame_review_result.v1",
  "levels": [
    {
      "label": "${POSTGAME_LADU_LEVELS[0]}",
      "players": [
        { "playerRef": "你｜我方打野｜凯隐", "championName": "凯隐", "phrase": "前期主动找节奏" },
        { "playerRef": "我方中单｜阿狸", "championName": "阿狸", "phrase": "支援到位"
`)

  assert.equal(parsed.ok, true)
  if (!parsed.ok) {
    return
  }
  assert.equal(parsed.partial, true)
  assert.equal(parsed.result.players.length, 1)
  assert.equal(parsed.result.players[0]?.playerRef, '你｜我方打野｜凯隐')
  assert.equal(parsed.result.players[0]?.championName, '凯隐')
  assert.equal(parsed.result.players[0]?.level, POSTGAME_LADU_LEVELS[0])
  assert.equal(parsed.result.summary, '')
})
