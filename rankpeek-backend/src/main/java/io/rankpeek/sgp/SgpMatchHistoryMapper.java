package io.rankpeek.sgp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.model.MatchHistory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SgpMatchHistoryMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public SgpMatchHistoryMapper() {
        this(new ObjectMapper());
    }

    SgpMatchHistoryMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public List<MatchHistory> mapMatchHistorySummary(JsonNode response) {
        List<MatchHistory> matches = new ArrayList<>();
        for (JsonNode game : SgpJsonMapperSupport.extractGames(response)) {
            MatchHistory match = mapGame(game);
            if (match != null) {
                matches.add(match);
            }
        }
        matches.sort(Comparator.comparingLong(this::gameCreationOrMin).reversed());
        return matches;
    }

    public MatchHistory mapGame(JsonNode game) {
        if (!SgpJsonMapperSupport.isObject(game)) {
            return null;
        }
        JsonNode participantsNode = SgpJsonMapperSupport.participants(game);
        if (participantsNode == null || participantsNode.isEmpty()) {
            return null;
        }

        MatchHistory match = new MatchHistory();
        match.setGameId(SgpJsonMapperSupport.readLong(game, "gameId", "id"));
        match.setQueueId(SgpJsonMapperSupport.readInt(game, "queueId", "queue"));
        match.setGameMode(SgpJsonMapperSupport.readText(game, "gameMode", "mode"));
        match.setGameType(SgpJsonMapperSupport.readText(game, "gameType", "type"));
        match.setGameCreation(SgpJsonMapperSupport.readLong(game, "gameCreation", "gameCreationDate", "createdAt"));
        match.setGameDuration(SgpJsonMapperSupport.readInt(game, "gameDuration", "duration"));
        match.setPlatformId(SgpJsonMapperSupport.readText(game, "platformId", "region"));
        match.setMapId(SgpJsonMapperSupport.readInt(game, "mapId", "map"));
        match.setRemake(SgpJsonMapperSupport.readBoolean(game, "isRemake", "remake"));
        match.setParticipants(mapParticipants(participantsNode));
        match.setParticipantIdentities(mapParticipantIdentities(game, participantsNode));
        return match;
    }

    public Map<Long, String> rawSummaryJsonByGameId(JsonNode response) {
        Map<Long, String> rawByGameId = new LinkedHashMap<>();
        for (JsonNode game : SgpJsonMapperSupport.extractGames(response)) {
            Long gameId = SgpJsonMapperSupport.readLong(game, "gameId", "id");
            if (gameId == null) {
                continue;
            }
            try {
                rawByGameId.put(gameId, objectMapper.writeValueAsString(game));
            } catch (Exception ignored) {
                rawByGameId.put(gameId, game.toString());
            }
        }
        return rawByGameId;
    }

    private List<MatchHistory.Participant> mapParticipants(JsonNode participantsNode) {
        List<MatchHistory.Participant> participants = new ArrayList<>();
        for (JsonNode participantNode : participantsNode) {
            if (!SgpJsonMapperSupport.isObject(participantNode)) {
                continue;
            }
            MatchHistory.Participant participant = new MatchHistory.Participant();
            participant.setParticipantId(SgpJsonMapperSupport.readInt(participantNode, "participantId", "participant_id", "id"));
            participant.setTeamId(SgpJsonMapperSupport.readInt(participantNode, "teamId", "team"));
            participant.setChampionId(SgpJsonMapperSupport.readInt(participantNode, "championId", "champion"));
            participant.setSpell1Id(SgpJsonMapperSupport.readInt(participantNode, "spell1Id", "summonerSpell1Id"));
            participant.setSpell2Id(SgpJsonMapperSupport.readInt(participantNode, "spell2Id", "summonerSpell2Id"));
            participant.setTeamPosition(SgpJsonMapperSupport.readText(participantNode, "teamPosition"));
            participant.setIndividualPosition(SgpJsonMapperSupport.readText(participantNode, "individualPosition"));
            participant.setSelectedPosition(SgpJsonMapperSupport.readText(participantNode, "selectedPosition"));
            participant.setLane(SgpJsonMapperSupport.readText(participantNode, "lane"));
            participant.setRole(SgpJsonMapperSupport.readText(participantNode, "role"));
            participant.setStats(mapStats(participantNode));
            participants.add(participant);
        }
        return participants;
    }

    private MatchHistory.Stats mapStats(JsonNode participantNode) {
        JsonNode statsNode = SgpJsonMapperSupport.statsNode(participantNode);
        MatchHistory.Stats stats = new MatchHistory.Stats();
        stats.setWin(readBoolean(statsNode, participantNode, "win", "winner"));
        stats.setKills(readInt(statsNode, participantNode, "kills"));
        stats.setDeaths(readInt(statsNode, participantNode, "deaths"));
        stats.setAssists(readInt(statsNode, participantNode, "assists"));
        stats.setGoldEarned(readInt(statsNode, participantNode, "goldEarned"));
        stats.setTotalMinionsKilled(readInt(statsNode, participantNode, "totalMinionsKilled", "minionsKilled"));
        stats.setNeutralMinionsKilled(readInt(statsNode, participantNode, "neutralMinionsKilled"));
        stats.setTotalDamageDealtToChampions(readInt(statsNode, participantNode, "totalDamageDealtToChampions"));
        stats.setTotalDamageTaken(readInt(statsNode, participantNode, "totalDamageTaken"));
        stats.setTotalHeal(readInt(statsNode, participantNode, "totalHeal"));
        stats.setVisionScore(readInt(statsNode, participantNode, "visionScore"));
        stats.setEarlyGoldDiff(readInt(statsNode, participantNode, "earlyGoldDiff"));
        stats.setLaneGoldDiff15(readInt(statsNode, participantNode, "laneGoldDiff15"));
        stats.setGoldDiff15(readInt(statsNode, participantNode, "goldDiff15"));
        stats.setGoldDiffAt15(readInt(statsNode, participantNode, "goldDiffAt15"));
        stats.setGoldDifferenceAt15(readInt(statsNode, participantNode, "goldDifferenceAt15"));
        stats.setFifteenMinuteGoldDiff(readInt(statsNode, participantNode, "fifteenMinuteGoldDiff"));
        stats.setItem0(readInt(statsNode, participantNode, "item0"));
        stats.setItem1(readInt(statsNode, participantNode, "item1"));
        stats.setItem2(readInt(statsNode, participantNode, "item2"));
        stats.setItem3(readInt(statsNode, participantNode, "item3"));
        stats.setItem4(readInt(statsNode, participantNode, "item4"));
        stats.setItem5(readInt(statsNode, participantNode, "item5"));
        stats.setItem6(readInt(statsNode, participantNode, "item6"));
        stats.setDoubleKills(readInt(statsNode, participantNode, "doubleKills"));
        stats.setTripleKills(readInt(statsNode, participantNode, "tripleKills"));
        stats.setQuadraKills(readInt(statsNode, participantNode, "quadraKills"));
        stats.setPentaKills(readInt(statsNode, participantNode, "pentaKills"));
        stats.setLargestKillingSpree(readInt(statsNode, participantNode, "largestKillingSpree"));
        stats.setLegendaryCount(firstNonNull(
                readInt(statsNode, participantNode, "legendaryCount"),
                readChallengeInt(statsNode, participantNode, "legendaryCount")
        ));
        mapPerks(participantNode, statsNode, stats);
        stats.setPlayerAugment1(readInt(statsNode, participantNode, "playerAugment1"));
        stats.setPlayerAugment2(readInt(statsNode, participantNode, "playerAugment2"));
        stats.setPlayerAugment3(readInt(statsNode, participantNode, "playerAugment3"));
        stats.setPlayerAugment4(readInt(statsNode, participantNode, "playerAugment4"));
        stats.setChallenges(toMap(firstObject(
                SgpJsonMapperSupport.value(statsNode, "challenges"),
                SgpJsonMapperSupport.value(participantNode, "challenges")
        )));
        stats.setExtraFields(extraFields(participantNode));
        return stats;
    }

    private void mapPerks(JsonNode participantNode, JsonNode statsNode, MatchHistory.Stats stats) {
        JsonNode perksNode = firstObject(
                SgpJsonMapperSupport.value(statsNode, "perks"),
                SgpJsonMapperSupport.value(participantNode, "perks")
        );
        stats.setPerks(toMap(perksNode));
        stats.setPerk0(firstNonNull(readInt(statsNode, participantNode, "perk0"), readPerk(perksNode, 0)));
        stats.setPerk1(firstNonNull(readInt(statsNode, participantNode, "perk1"), readPerk(perksNode, 1)));
        stats.setPerk2(firstNonNull(readInt(statsNode, participantNode, "perk2"), readPerk(perksNode, 2)));
        stats.setPerk3(firstNonNull(readInt(statsNode, participantNode, "perk3"), readPerk(perksNode, 3)));
        stats.setPerk4(firstNonNull(readInt(statsNode, participantNode, "perk4"), readPerk(perksNode, 4)));
        stats.setPerk5(firstNonNull(readInt(statsNode, participantNode, "perk5"), readPerk(perksNode, 5)));
        stats.setPerkPrimaryStyle(firstNonNull(
                readInt(statsNode, participantNode, "perkPrimaryStyle"),
                readPerkStyle(perksNode, 0)
        ));
        stats.setPerkSubStyle(firstNonNull(
                readInt(statsNode, participantNode, "perkSubStyle"),
                readPerkStyle(perksNode, 1)
        ));
    }

    private Integer readInt(JsonNode primary, JsonNode fallback, String... fieldNames) {
        Integer value = SgpJsonMapperSupport.readInt(primary, fieldNames);
        return value != null ? value : SgpJsonMapperSupport.readInt(fallback, fieldNames);
    }

    private Boolean readBoolean(JsonNode primary, JsonNode fallback, String... fieldNames) {
        Boolean value = SgpJsonMapperSupport.readBoolean(primary, fieldNames);
        return value != null ? value : SgpJsonMapperSupport.readBoolean(fallback, fieldNames);
    }

    private Integer readChallengeInt(JsonNode statsNode, JsonNode participantNode, String... fieldNames) {
        Integer value = SgpJsonMapperSupport.readInt(SgpJsonMapperSupport.value(statsNode, "challenges"), fieldNames);
        return value != null
                ? value
                : SgpJsonMapperSupport.readInt(SgpJsonMapperSupport.value(participantNode, "challenges"), fieldNames);
    }

    private Integer readPerk(JsonNode perksNode, int index) {
        JsonNode selection = perkSelection(perksNode, index);
        return SgpJsonMapperSupport.readInt(selection, "perk");
    }

    private Integer readPerkStyle(JsonNode perksNode, int styleIndex) {
        JsonNode style = perkStyle(perksNode, styleIndex);
        return SgpJsonMapperSupport.readInt(style, "style");
    }

    private JsonNode perkSelection(JsonNode perksNode, int index) {
        int remaining = index;
        JsonNode styles = SgpJsonMapperSupport.path(perksNode, "styles");
        if (styles == null || !styles.isArray()) {
            return null;
        }
        for (JsonNode style : styles) {
            JsonNode selections = SgpJsonMapperSupport.value(style, "selections");
            if (selections == null || !selections.isArray()) {
                continue;
            }
            for (JsonNode selection : selections) {
                if (remaining == 0) {
                    return selection;
                }
                remaining -= 1;
            }
        }
        return null;
    }

    private JsonNode perkStyle(JsonNode perksNode, int styleIndex) {
        JsonNode styles = SgpJsonMapperSupport.path(perksNode, "styles");
        if (styles == null || !styles.isArray() || styles.size() <= styleIndex) {
            return null;
        }
        return styles.get(styleIndex);
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (!SgpJsonMapperSupport.isObject(node)) {
            return null;
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    private Map<String, Object> extraFields(JsonNode participantNode) {
        Map<String, Object> source = toMap(participantNode);
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> extra = new LinkedHashMap<>(source);
        List.of(
                "participantId", "teamId", "championId", "spell1Id", "spell2Id", "summonerSpell1Id",
                "summonerSpell2Id", "teamPosition", "individualPosition", "selectedPosition", "lane", "role",
                "stats", "player", "puuid", "summonerId", "summonerName", "riotIdGameName", "riotIdTagline",
                "win", "kills", "deaths", "assists", "goldEarned", "totalMinionsKilled",
                "neutralMinionsKilled", "totalDamageDealtToChampions", "totalDamageTaken", "totalHeal",
                "visionScore", "item0", "item1", "item2", "item3", "item4", "item5", "item6",
                "doubleKills", "tripleKills", "quadraKills", "pentaKills", "largestKillingSpree",
                "legendaryCount", "perk0", "perks", "playerAugment1", "playerAugment2", "playerAugment3",
                "playerAugment4", "challenges"
        ).forEach(extra::remove);
        return extra;
    }

    private JsonNode firstObject(JsonNode... nodes) {
        return SgpJsonMapperSupport.firstObject(nodes);
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<MatchHistory.ParticipantIdentity> mapParticipantIdentities(JsonNode game, JsonNode participantsNode) {
        JsonNode identitiesNode = SgpJsonMapperSupport.participantIdentities(game);
        if (identitiesNode != null && !identitiesNode.isEmpty()) {
            return mapIdentityNodes(identitiesNode);
        }
        return mapIdentityNodes(participantsNode);
    }

    private List<MatchHistory.ParticipantIdentity> mapIdentityNodes(JsonNode identityNodes) {
        List<MatchHistory.ParticipantIdentity> identities = new ArrayList<>();
        for (JsonNode identityNode : identityNodes) {
            if (!SgpJsonMapperSupport.isObject(identityNode)) {
                continue;
            }
            MatchHistory.ParticipantIdentity identity = new MatchHistory.ParticipantIdentity();
            identity.setParticipantId(SgpJsonMapperSupport.readInt(identityNode, "participantId", "participant_id", "id"));
            identity.setPlayer(mapPlayer(SgpJsonMapperSupport.playerNode(identityNode)));
            identities.add(identity);
        }
        return identities;
    }

    private MatchHistory.Player mapPlayer(JsonNode playerNode) {
        MatchHistory.Player player = new MatchHistory.Player();
        player.setPuuid(SgpJsonMapperSupport.readText(playerNode, "puuid"));
        player.setGameName(SgpJsonMapperSupport.readText(playerNode, "gameName"));
        player.setTagLine(SgpJsonMapperSupport.readText(playerNode, "tagLine", "tagline"));
        player.setSummonerName(SgpJsonMapperSupport.readText(playerNode, "summonerName", "displayName"));
        player.setSummonerId(SgpJsonMapperSupport.readLong(playerNode, "summonerId"));
        player.setPlatformId(SgpJsonMapperSupport.readText(playerNode, "platformId", "region"));
        return player;
    }

    private long gameCreationOrMin(MatchHistory match) {
        return match.getGameCreation() == null ? Long.MIN_VALUE : match.getGameCreation();
    }
}
