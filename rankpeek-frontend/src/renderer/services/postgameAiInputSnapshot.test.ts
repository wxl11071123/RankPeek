import test from 'node:test'
import assert from 'node:assert/strict'
import type { GameDetail, MatchHistory, MatchTimeline } from '../types/api.ts'
import {
  buildPostgameAiInputSnapshot,
  createPostgameAiInputHash
} from './postgameAiInputSnapshot.ts'

function createMatchHistory(): MatchHistory {
  return {
    gameId: 987654321,
    gameMode: 'CLASSIC',
    gameType: 'MATCHED_GAME',
    queueId: 420,
    queueName: 'Ranked Solo',
    gameDuration: 1800,
    gameCreation: 1710000000000,
    platformId: 'HN1',
    participants: createParticipants(),
    participantIdentities: createIdentities(),
    teamObjectives: [
      {
        teamId: 100,
        turretKills: 8,
        turretPlateKills: 6,
        inhibitorKills: 1,
        dragonKills: 2,
        baronKills: 1,
        heraldKills: 1,
        voidGrubKills: 3
      },
      {
        teamId: 200,
        turretKills: 4,
        turretPlateKills: 2,
        inhibitorKills: 0,
        dragonKills: 1,
        baronKills: 0,
        heraldKills: 0,
        voidGrubKills: 0
      }
    ]
  }
}

function createGameDetail(): GameDetail {
  const match = createMatchHistory()
  return {
    gameId: match.gameId,
    gameMode: match.gameMode,
    gameType: match.gameType,
    mapId: 11,
    queueId: match.queueId,
    gameDuration: match.gameDuration,
    gameCreation: match.gameCreation,
    participantIdentities: match.participantIdentities,
    participants: match.participants.map(participant => ({
      participantId: participant.participantId,
      teamId: participant.teamId,
      championId: participant.championId,
      spell1Id: participant.spell1Id,
      spell2Id: participant.spell2Id,
      teamPosition: participant.teamPosition,
      individualPosition: participant.individualPosition,
      selectedPosition: participant.selectedPosition,
      timeline: {
        lane: participant.teamPosition ?? '',
        role: participant.role ?? '',
        teamPosition: participant.teamPosition,
        rawLane: participant.lane,
        rawRole: participant.role
      },
      stats: {
        ...participant.stats,
        wardsPlaced: 7,
        wardsKilled: 2,
        visionWardsBoughtInGame: 1,
        largestMultiKill: 2,
        doubleKills: participant.stats.doubleKills ?? 0,
        tripleKills: participant.stats.tripleKills ?? 0,
        quadraKills: participant.stats.quadraKills ?? 0,
        pentaKills: participant.stats.pentaKills ?? 0
      }
    })),
    teamObjectives: match.teamObjectives,
    teamBans: []
  }
}

function createParticipants(): MatchHistory['participants'] {
  const positions = ['TOP', 'JUNGLE', 'MIDDLE', 'BOTTOM', 'UTILITY']
  return Array.from({ length: 10 }, (_, index) => {
    const participantId = index + 1
    const blue = participantId <= 5
    const lane = positions[index % 5] ?? 'TOP'
    return {
      participantId,
      teamId: blue ? 100 : 200,
      championId: 10 + participantId,
      spell1Id: 4,
      spell2Id: 14,
      teamPosition: lane,
      individualPosition: lane,
      selectedPosition: lane,
      lane,
      role: lane === 'UTILITY' ? 'SUPPORT' : 'SOLO',
      stats: {
        win: blue,
        kills: blue ? participantId + 1 : participantId - 3,
        deaths: blue ? 3 : 5,
        assists: blue ? 8 : 4,
        goldEarned: blue ? 10000 + participantId * 300 : 9000 + participantId * 200,
        totalMinionsKilled: lane === 'JUNGLE' ? 20 : 170 + participantId,
        neutralMinionsKilled: lane === 'JUNGLE' ? 120 : 8,
        totalDamageDealtToChampions: blue ? 19000 + participantId * 500 : 15000 + participantId * 450,
        totalDamageTaken: blue ? 21000 + participantId * 350 : 23000 + participantId * 300,
        totalHeal: 1000,
        visionScore: 18 + participantId,
        item0: 1001,
        item1: 3006,
        item2: 6672,
        item3: 3031,
        item4: 0,
        item5: 0,
        item6: 3363,
        perk0: 8005,
        perk1: 9111,
        perk2: 9104,
        perk3: 8014,
        perk4: 8304,
        perk5: 8345,
        perkPrimaryStyle: 8000,
        perkSubStyle: 8300,
        turretPlatesTaken: blue ? 2 : 0,
        playerAugment1: participantId === 1 ? 2005 : undefined,
        playerAugment2: participantId === 1 ? 1346 : undefined
      }
    }
  })
}

function createIdentities(): MatchHistory['participantIdentities'] {
  return Array.from({ length: 10 }, (_, index) => {
    const participantId = index + 1
    return {
      participantId,
      player: {
        accountId: participantId,
        summonerId: participantId,
        summonerName: `Summoner ${participantId}`,
        gameName: participantId === 1 ? 'Current' : `Player${participantId}`,
        tagLine: 'CN1',
        puuid: participantId === 1 ? 'current-puuid' : `player-${participantId}-puuid`,
        platformId: 'HN1'
      }
    }
  })
}

function createTimeline(): MatchTimeline {
  return {
    gameId: 987654321,
    frames: [
      createFrame(0, 500, 500),
      createFrame(900000, 6500, 5400),
      {
        ...createFrame(1200000, 8500, 7600),
        events: [
          {
            eventType: 'CHAMPION_KILL',
            timestamp: 960000,
            killerId: 7,
            victimId: 1
          },
          {
            eventType: 'ELITE_MONSTER_KILL',
            timestamp: 990000,
            killerId: 3,
            teamId: 100,
            monsterType: 'DRAGON'
          }
        ]
      }
    ]
  }
}

function createFrame(timestamp: number, blueBaseGold: number, redBaseGold: number): NonNullable<MatchTimeline['frames']>[number] {
  const participantFrames: NonNullable<NonNullable<MatchTimeline['frames']>[number]['participantFrames']> = {}
  for (let participantId = 1; participantId <= 10; participantId += 1) {
    const blue = participantId <= 5
    participantFrames[String(participantId)] = {
      participantId,
      totalGold: (blue ? blueBaseGold : redBaseGold) + participantId * 100,
      minionsKilled: 80 + participantId
    }
  }
  return { timestamp, participantFrames, events: [] }
}

test('builds a compact postgame AI input snapshot from match history, detail, and timeline', () => {
  const snapshot = buildPostgameAiInputSnapshot({
    mode: 'review',
    matchHistory: createMatchHistory(),
    gameDetail: createGameDetail(),
    timeline: createTimeline(),
    currentPuuid: 'current-puuid',
    currentSummonerName: 'Current#CN1'
  })

  assert.equal(snapshot.schemaVersion, 'postgame_ai_input_snapshot.v1')
  assert.equal(snapshot.mode, 'review')
  assert.equal(snapshot.analysisType, 'postgame_review')
  assert.match(snapshot.inputHash, /^[a-f0-9]{8,}$/)
  assert.notEqual(snapshot.match.gameIdHash, '987654321')
  assert.equal(snapshot.match.queueId, 420)
  assert.equal(snapshot.match.isRanked, true)
  assert.equal(snapshot.teams.length, 2)
  assert.equal(snapshot.players.length, 10)
  assert.equal(snapshot.currentPlayerKey, snapshot.players[0]?.playerKey)
  assert.equal(snapshot.players[0]?.isCurrentPlayer, true)
  assert.equal(snapshot.players[0]?.stats.kda, 3.33)
  assert.equal(snapshot.players[0]?.stats.killParticipation, 0.5)
  assert.equal(snapshot.players[0]?.loadout.itemIds[0], 1001)
  assert.deepEqual(snapshot.players[0]?.loadout.augmentIds, [2005, 1346])
  assert.equal(snapshot.players[0]?.rankedMetrics?.teamGoldDiffAt15, 3000)
  assert.equal(snapshot.teams[0]?.objectives?.turretPlates, 6)
  assert.equal(snapshot.timeline?.hasTimeline, true)
  assert.ok((snapshot.timeline?.goldDiffPoints ?? []).some(point => point.minute === 15 && point.teamGoldDiff === 3000))
  assert.equal(snapshot.dataQuality.hasGameDetail, true)
  assert.equal(snapshot.dataQuality.hasTimeline, true)
  assert.equal(snapshot.dataQuality.participantCount, 10)
  assert.equal(snapshot.dataQuality.hasRankedTimelineMetrics, true)
  assert.equal(snapshot.dataQuality.hasArenaAugments, true)
})

test('maps praise mode to a praise analysis type and keeps hash stable apart from timestamps', () => {
  const base = {
    matchHistory: createMatchHistory(),
    gameDetail: createGameDetail(),
    timeline: createTimeline(),
    currentPuuid: 'current-puuid',
    currentSummonerName: 'Current#CN1'
  }
  const review = buildPostgameAiInputSnapshot({ mode: 'review', ...base })
  const praise = buildPostgameAiInputSnapshot({ mode: 'praise', ...base })

  assert.equal(review.analysisType, 'postgame_review')
  assert.equal(praise.analysisType, 'postgame_praise')
  assert.notEqual(review.inputHash, praise.inputHash)
  assert.equal(createPostgameAiInputHash({ ...review, builtAt: '2099-01-01T00:00:00.000Z', inputHash: 'changed' }), review.inputHash)
})

test('does not copy raw match history, game detail, timeline, or puuids into the snapshot', () => {
  const snapshot = buildPostgameAiInputSnapshot({
    mode: 'review',
    matchHistory: createMatchHistory(),
    gameDetail: createGameDetail(),
    timeline: createTimeline(),
    currentPuuid: 'current-puuid',
    currentSummonerName: 'Current#CN1'
  })
  const serialized = JSON.stringify(snapshot)

  assert.doesNotMatch(serialized, /matchHistory|gameDetail|rawTimeline|participantIdentities|rawFrameJson|rawEventJson/)
  assert.doesNotMatch(serialized, /current-puuid|player-2-puuid|987654321/)
})

test('records timeline data quality warnings without inventing missing timeline metrics', () => {
  const snapshot = buildPostgameAiInputSnapshot({
    mode: 'review',
    matchHistory: createMatchHistory(),
    gameDetail: createGameDetail(),
    timeline: null,
    currentPuuid: 'current-puuid',
    currentSummonerName: 'Current#CN1'
  })

  assert.equal(snapshot.timeline?.hasTimeline, false)
  assert.equal(snapshot.dataQuality.hasTimeline, false)
  assert.equal(snapshot.dataQuality.hasRankedTimelineMetrics, false)
  assert.ok(snapshot.dataQuality.warnings.some(warning => /timeline/i.test(warning)))
  assert.equal(snapshot.players[0]?.rankedMetrics?.teamGoldDiffAt15, undefined)
})
