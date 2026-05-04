package io.rankpeek.sgp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.model.GameDetail;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SgpGameDetailMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public SgpGameDetailMapper() {
        this(new ObjectMapper());
    }

    SgpGameDetailMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public GameDetail mapGameSummary(JsonNode response) {
        return map(response);
    }

    public GameDetail mapGameDetails(JsonNode response) {
        return map(response);
    }

    private GameDetail map(JsonNode response) {
        JsonNode game = SgpJsonMapperSupport.extractGame(response);
        GameDetail detail = new GameDetail();
        if (game == null) {
            detail.setParticipants(List.of());
            detail.setParticipantIdentities(List.of());
            return detail;
        }

        detail.setGameId(SgpJsonMapperSupport.readLong(game, "gameId", "id"));
        detail.setQueueId(SgpJsonMapperSupport.readInt(game, "queueId", "queue"));
        detail.setGameMode(SgpJsonMapperSupport.readText(game, "gameMode", "mode"));
        detail.setGameType(SgpJsonMapperSupport.readText(game, "gameType", "type"));
        detail.setGameCreation(SgpJsonMapperSupport.readLong(game, "gameCreation", "gameCreationDate", "createdAt"));
        detail.setGameDuration(SgpJsonMapperSupport.readLong(game, "gameDuration", "duration"));
        detail.setMapId(SgpJsonMapperSupport.readInt(game, "mapId", "map"));

        JsonNode participantsNode = SgpJsonMapperSupport.participants(game);
        if (participantsNode == null || participantsNode.isEmpty()) {
            detail.setParticipants(List.of());
            detail.setParticipantIdentities(List.of());
            return detail;
        }

        detail.setParticipants(mapParticipants(participantsNode));
        detail.setParticipantIdentities(mapParticipantIdentities(game, participantsNode));
        return detail;
    }

    private List<GameDetail.GameParticipant> mapParticipants(JsonNode participantsNode) {
        List<GameDetail.GameParticipant> participants = new ArrayList<>();
        for (JsonNode participantNode : participantsNode) {
            if (!SgpJsonMapperSupport.isObject(participantNode)) {
                continue;
            }
            GameDetail.GameParticipant participant = new GameDetail.GameParticipant();
            participant.setParticipantId(SgpJsonMapperSupport.readInt(participantNode, "participantId", "participant_id", "id"));
            participant.setTeamId(SgpJsonMapperSupport.readInt(participantNode, "teamId", "team"));
            participant.setChampionId(SgpJsonMapperSupport.readInt(participantNode, "championId", "champion"));
            participant.setSpell1Id(SgpJsonMapperSupport.readInt(participantNode, "spell1Id", "summonerSpell1Id"));
            participant.setSpell2Id(SgpJsonMapperSupport.readInt(participantNode, "spell2Id", "summonerSpell2Id"));
            participant.setTeamPosition(SgpJsonMapperSupport.readText(participantNode, "teamPosition"));
            participant.setIndividualPosition(SgpJsonMapperSupport.readText(participantNode, "individualPosition"));
            participant.setSelectedPosition(SgpJsonMapperSupport.readText(participantNode, "selectedPosition"));
            participant.setTimeline(mapTimeline(participantNode));
            participant.setStats(mapStats(participantNode));
            participants.add(participant);
        }
        return participants;
    }

    private GameDetail.Timeline mapTimeline(JsonNode participantNode) {
        GameDetail.Timeline timeline = new GameDetail.Timeline();
        String lane = SgpJsonMapperSupport.readText(participantNode, "lane");
        String role = SgpJsonMapperSupport.readText(participantNode, "role");
        String teamPosition = SgpJsonMapperSupport.readText(participantNode, "teamPosition");
        timeline.setLane(firstNonNull(teamPosition, lane));
        timeline.setRole(role);
        timeline.setTeamPosition(teamPosition);
        timeline.setPositionCn(SgpJsonMapperSupport.readText(participantNode, "positionCn"));
        timeline.setRawLane(lane);
        timeline.setRawRole(role);
        if (timeline.getLane() == null && timeline.getRole() == null && timeline.getTeamPosition() == null
                && timeline.getPositionCn() == null && timeline.getRawLane() == null && timeline.getRawRole() == null) {
            return null;
        }
        return timeline;
    }

    private GameDetail.Stats mapStats(JsonNode participantNode) {
        JsonNode statsNode = SgpJsonMapperSupport.statsNode(participantNode);
        GameDetail.Stats stats = new GameDetail.Stats();
        stats.setWin(readBoolean(statsNode, participantNode, "win", "winner"));
        stats.setKills(readInt(statsNode, participantNode, "kills"));
        stats.setDeaths(readInt(statsNode, participantNode, "deaths"));
        stats.setAssists(readInt(statsNode, participantNode, "assists"));
        stats.setGoldEarned(readLong(statsNode, participantNode, "goldEarned"));
        stats.setTotalMinionsKilled(readInt(statsNode, participantNode, "totalMinionsKilled", "minionsKilled"));
        stats.setNeutralMinionsKilled(readInt(statsNode, participantNode, "neutralMinionsKilled"));
        stats.setTotalDamageDealtToChampions(readLong(statsNode, participantNode, "totalDamageDealtToChampions"));
        stats.setTotalDamageTaken(readLong(statsNode, participantNode, "totalDamageTaken"));
        stats.setVisionScore(readInt(statsNode, participantNode, "visionScore"));
        stats.setEarlyGoldDiff(readInt(statsNode, participantNode, "earlyGoldDiff"));
        stats.setTotalHeal(readLong(statsNode, participantNode, "totalHeal"));
        stats.setDoubleKills(readInt(statsNode, participantNode, "doubleKills"));
        stats.setTripleKills(readInt(statsNode, participantNode, "tripleKills"));
        stats.setQuadraKills(readInt(statsNode, participantNode, "quadraKills"));
        stats.setPentaKills(readInt(statsNode, participantNode, "pentaKills"));
        stats.setLargestKillingSpree(readInt(statsNode, participantNode, "largestKillingSpree"));
        stats.setLegendaryCount(readInt(statsNode, participantNode, "legendaryCount"));
        stats.setLegendaryCount(firstNonNull(
                stats.getLegendaryCount(),
                readChallengeInt(statsNode, participantNode, "legendaryCount")
        ));
        mapPerks(participantNode, statsNode, stats);
        stats.setPlayerAugment1(readInt(statsNode, participantNode, "playerAugment1"));
        stats.setPlayerAugment2(readInt(statsNode, participantNode, "playerAugment2"));
        stats.setPlayerAugment3(readInt(statsNode, participantNode, "playerAugment3"));
        stats.setPlayerAugment4(readInt(statsNode, participantNode, "playerAugment4"));
        stats.setItem0(readInt(statsNode, participantNode, "item0"));
        stats.setItem1(readInt(statsNode, participantNode, "item1"));
        stats.setItem2(readInt(statsNode, participantNode, "item2"));
        stats.setItem3(readInt(statsNode, participantNode, "item3"));
        stats.setItem4(readInt(statsNode, participantNode, "item4"));
        stats.setItem5(readInt(statsNode, participantNode, "item5"));
        stats.setItem6(readInt(statsNode, participantNode, "item6"));
        stats.setChallenges(toMap(firstObject(
                SgpJsonMapperSupport.value(statsNode, "challenges"),
                SgpJsonMapperSupport.value(participantNode, "challenges")
        )));
        stats.setExtraFields(extraFields(participantNode));
        return stats;
    }

    private void mapPerks(JsonNode participantNode, JsonNode statsNode, GameDetail.Stats stats) {
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

    private Long readLong(JsonNode primary, JsonNode fallback, String... fieldNames) {
        Long value = SgpJsonMapperSupport.readLong(primary, fieldNames);
        return value != null ? value : SgpJsonMapperSupport.readLong(fallback, fieldNames);
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

    private List<GameDetail.ParticipantIdentity> mapParticipantIdentities(JsonNode game, JsonNode participantsNode) {
        JsonNode identitiesNode = SgpJsonMapperSupport.participantIdentities(game);
        if (identitiesNode != null && !identitiesNode.isEmpty()) {
            return mapIdentityNodes(identitiesNode);
        }
        return mapIdentityNodes(participantsNode);
    }

    private List<GameDetail.ParticipantIdentity> mapIdentityNodes(JsonNode identityNodes) {
        List<GameDetail.ParticipantIdentity> identities = new ArrayList<>();
        for (JsonNode identityNode : identityNodes) {
            if (!SgpJsonMapperSupport.isObject(identityNode)) {
                continue;
            }
            GameDetail.ParticipantIdentity identity = new GameDetail.ParticipantIdentity();
            identity.setParticipantId(SgpJsonMapperSupport.readInt(identityNode, "participantId", "participant_id", "id"));
            identity.setPlayer(mapPlayer(SgpJsonMapperSupport.playerNode(identityNode)));
            identities.add(identity);
        }
        return identities;
    }

    private GameDetail.Player mapPlayer(JsonNode playerNode) {
        GameDetail.Player player = new GameDetail.Player();
        player.setPuuid(SgpJsonMapperSupport.readText(playerNode, "puuid"));
        player.setGameName(SgpJsonMapperSupport.readText(playerNode, "gameName"));
        player.setTagLine(SgpJsonMapperSupport.readText(playerNode, "tagLine", "tagline"));
        player.setSummonerName(SgpJsonMapperSupport.readText(playerNode, "summonerName", "displayName"));
        player.setSummonerId(SgpJsonMapperSupport.readLong(playerNode, "summonerId"));
        player.setPlatformId(SgpJsonMapperSupport.readText(playerNode, "platformId", "region"));
        return player;
    }
}
