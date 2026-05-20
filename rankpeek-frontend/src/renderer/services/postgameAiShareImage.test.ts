import test from 'node:test'
import assert from 'node:assert/strict'
import type { PostgameAiStructuredResult } from './postgameAiStructuredResult.ts'
import {
  buildPostgameLaduChartShareModel,
  resolvePostgameReviewPlayerIconUrl
} from './postgameAiShareImage.ts'

function createResult(): PostgameAiStructuredResult {
  return {
    schemaVersion: 'postgame_review_result.v1',
    levels: [
      {
        label: '夯',
        players: [
          {
            level: '夯',
            playerRef: '你｜我方打野｜凯隐',
            championName: '凯隐',
            phrase: '开局做事最多。'
          }
        ]
      },
      { label: '顶级', players: [] },
      { label: '人上人', players: [] },
      { label: 'NPC', players: [] },
      {
        label: '拉完了',
        players: [
          {
            level: '拉完了',
            playerRef: '敌方下路｜伊泽瑞尔',
            championName: '伊泽瑞尔',
            championId: 81,
            phrase: '伤害转化不足。'
          }
        ]
      }
    ],
    players: [
      {
        level: '夯',
        playerRef: '你｜我方打野｜凯隐',
        championName: '凯隐',
        phrase: '开局做事最多。'
      },
      {
        level: '拉完了',
        playerRef: '敌方下路｜伊泽瑞尔',
        championName: '伊泽瑞尔',
        championId: 81,
        phrase: '伤害转化不足。'
      }
    ],
    summary: '客观总结：前期节奏清楚，中期资源团处理需要更稳。'
  }
}

test('builds the five-row ladu chart share model with summary inside the image', () => {
  const model = buildPostgameLaduChartShareModel(createResult(), {
    title: '赛后复盘',
    getChampionIconUrl: championId => `icon:${championId}`
  })

  assert.equal(model.title, '赛后复盘')
  assert.equal(model.width, 1080)
  assert.deepEqual(model.rows.map(row => row.label), ['夯', '顶级', '人上人', 'NPC', '拉完了'])
  assert.equal(model.rows[0]?.players[0]?.championName, '凯隐')
  assert.equal(model.rows[4]?.players[0]?.iconUrl, 'icon:81')
  assert.equal(model.summary, '客观总结：前期节奏清楚，中期资源团处理需要更稳。')
})

test('resolves postgame review icons from AI champion names without match detail hydration', () => {
  const iconUrl = resolvePostgameReviewPlayerIconUrl(
    {
      level: '夯',
      playerRef: '敌方下路｜圣枪游侠',
      championName: '圣枪游侠',
      phrase: '输出拉满'
    },
    [],
    championId => `icon:${championId}`,
    { 圣枪游侠: 236 }
  )

  assert.equal(iconUrl, 'icon:236')
})

test('share image model resolves champion ids from AI champion names', () => {
  const result = createResult()
  result.levels[0]!.players[0] = {
    level: '夯',
    playerRef: '敌方下路｜圣枪游侠',
    championName: '圣枪游侠',
    phrase: '输出拉满'
  }

  const model = buildPostgameLaduChartShareModel(result, {
    championIdByName: { 圣枪游侠: 236 },
    getChampionIconUrl: championId => `icon:${championId}`
  })

  assert.equal(model.rows[0]?.players[0]?.championId, 236)
  assert.equal(model.rows[0]?.players[0]?.iconUrl, 'icon:236')
})
