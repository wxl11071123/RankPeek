"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const node_test_1 = __importDefault(require("node:test"));
const strict_1 = __importDefault(require("node:assert/strict"));
const matchRosterDisplay_1 = require("./matchRosterDisplay");
(0, node_test_1.default)('builds compact current-match detail items with emoji labels', () => {
    const items = (0, matchRosterDisplay_1.buildMatchDetailItems)({
        kills: 18,
        deaths: 9,
        assists: 31,
        goldEarned: 20400,
        totalDamageDealtToChampions: 44900,
        totalMinionsKilled: 216,
        neutralMinionsKilled: 0
    });
    strict_1.default.deepEqual(items, [
        { label: '💥', value: '44.9k' },
        { label: '🪙', value: '20.4k' },
        { label: '🌾', value: '216' }
    ]);
});
(0, node_test_1.default)('computes team leader thresholds for kills deaths and assists', () => {
    const leaders = (0, matchRosterDisplay_1.getTeamKdaLeaders)([
        { stats: { kills: 6, deaths: 14, assists: 3, goldEarned: 0, totalDamageDealtToChampions: 0, totalMinionsKilled: 0, neutralMinionsKilled: 0 } },
        { stats: { kills: 10, deaths: 10, assists: 3, goldEarned: 0, totalDamageDealtToChampions: 0, totalMinionsKilled: 0, neutralMinionsKilled: 0 } },
        { stats: { kills: 9, deaths: 11, assists: 7, goldEarned: 0, totalDamageDealtToChampions: 0, totalMinionsKilled: 0, neutralMinionsKilled: 0 } }
    ]);
    strict_1.default.deepEqual(leaders, {
        kills: 10,
        deaths: 14,
        assists: 7
    });
});
