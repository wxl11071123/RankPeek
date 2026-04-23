"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getTeamKdaLeaders = getTeamKdaLeaders;
exports.buildMatchDetailItems = buildMatchDetailItems;
function getTeamKdaLeaders(players) {
    return players.reduce((leaders, player) => ({
        kills: Math.max(leaders.kills, player.stats?.kills || 0),
        deaths: Math.max(leaders.deaths, player.stats?.deaths || 0),
        assists: Math.max(leaders.assists, player.stats?.assists || 0)
    }), {
        kills: 0,
        deaths: 0,
        assists: 0
    });
}
function buildMatchDetailItems(stats) {
    if (!stats) {
        return [];
    }
    return [
        { label: '💥', value: formatCompactNumber(stats.totalDamageDealtToChampions) },
        { label: '🪙', value: formatCompactNumber(stats.goldEarned) },
        { label: '🌾', value: String((stats.totalMinionsKilled || 0) + (stats.neutralMinionsKilled || 0)) }
    ];
}
function formatCompactNumber(value) {
    if (value == null) {
        return '0';
    }
    if (value >= 1000000) {
        return `${(value / 1000000).toFixed(1)}m`;
    }
    if (value >= 1000) {
        return `${(value / 1000).toFixed(value >= 10000 ? 1 : 2)}`.replace(/\.0$/, '') + 'k';
    }
    return String(value);
}
