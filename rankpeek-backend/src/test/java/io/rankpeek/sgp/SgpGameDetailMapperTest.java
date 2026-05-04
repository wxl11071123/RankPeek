package io.rankpeek.sgp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.rankpeek.model.GameDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SgpGameDetailMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SgpGameDetailMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SgpGameDetailMapper();
    }

    @Test
    void mapGameSummary_mapsGameDetailRosterAndPlayerStats() {
        ObjectNode response = objectMapper.createObjectNode();
        response.set("game", gameNode(300001L, true));

        GameDetail detail = mapper.mapGameSummary(response);

        assertThat(detail.getGameId()).isEqualTo(300001L);
        assertThat(detail.getQueueId()).isEqualTo(450);
        assertThat(detail.getGameMode()).isEqualTo("ARAM");
        assertThat(detail.getGameCreation()).isEqualTo(1710001234000L);
        assertThat(detail.getGameDuration()).isEqualTo(1320L);
        assertThat(detail.getMapId()).isEqualTo(12);
        assertThat(detail.getParticipants()).hasSize(10);
        assertThat(detail.getParticipantIdentities()).hasSize(10);

        GameDetail.GameParticipant participant = detail.getParticipants().get(0);
        assertThat(participant.getParticipantId()).isEqualTo(1);
        assertThat(participant.getTeamId()).isEqualTo(100);
        assertThat(participant.getChampionId()).isEqualTo(103);
        assertThat(participant.getSpell1Id()).isEqualTo(4);
        assertThat(participant.getSpell2Id()).isEqualTo(32);
        assertThat(participant.getStats().getWin()).isTrue();
        assertThat(participant.getStats().getKills()).isEqualTo(13);
        assertThat(participant.getStats().getDeaths()).isEqualTo(4);
        assertThat(participant.getStats().getAssists()).isEqualTo(22);
        assertThat(participant.getStats().getItem0()).isEqualTo(6655);
        assertThat(participant.getStats().getItem6()).isEqualTo(3363);
        assertThat(participant.getStats().getTotalDamageDealtToChampions()).isEqualTo(32123L);
        assertThat(participant.getStats().getTotalDamageTaken()).isEqualTo(20321L);
        assertThat(participant.getStats().getGoldEarned()).isEqualTo(15321L);
        assertThat(participant.getStats().getTotalMinionsKilled()).isEqualTo(72);
        assertThat(participant.getStats().getNeutralMinionsKilled()).isEqualTo(0);
        assertThat(participant.getStats().getVisionScore()).isEqualTo(18);
        assertThat(participant.getStats().getDoubleKills()).isEqualTo(3);
        assertThat(participant.getStats().getTripleKills()).isEqualTo(2);
        assertThat(participant.getStats().getQuadraKills()).isEqualTo(1);
        assertThat(participant.getStats().getPentaKills()).isZero();
        assertThat(participant.getStats().getLargestKillingSpree()).isEqualTo(9);
        assertThat(participant.getStats().getLegendaryCount()).isEqualTo(1);
        assertThat(participant.getStats().getPerk0()).isEqualTo(8214);
        assertThat(participant.getStats().getPlayerAugment1()).isEqualTo(20001);

        GameDetail.Player player = detail.getParticipantIdentities().get(0).getPlayer();
        assertThat(player.getPuuid()).isEqualTo("detail-puuid-1");
        assertThat(player.getGameName()).isEqualTo("DetailPlayer1");
        assertThat(player.getTagLine()).isEqualTo("D1");
        assertThat(player.getSummonerName()).isEqualTo("DetailSummoner1");
        assertThat(player.getSummonerId()).isEqualTo(7001L);
    }

    @Test
    void mapGameSummary_mapsTencentWrappedJsonGame() {
        ObjectNode response = objectMapper.createObjectNode();
        response.set("json", gameNode(300003L, true));

        GameDetail detail = mapper.mapGameSummary(response);

        assertThat(detail.getGameId()).isEqualTo(300003L);
        assertThat(detail.getParticipants()).hasSize(10);
        assertThat(detail.getParticipantIdentities()).hasSize(10);
    }

    @Test
    void mapGameSummary_readsLoadoutFieldsFromParticipantWhenStatsOmitsThem() {
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode game = gameNode(300004L, true);
        ObjectNode participant = (ObjectNode) game.withArray("participants").get(0);
        ObjectNode stats = (ObjectNode) participant.get("stats");
        stats.remove(List.of(
                "doubleKills",
                "tripleKills",
                "quadraKills",
                "pentaKills",
                "largestKillingSpree",
                "legendaryCount",
                "perk0",
                "playerAugment1",
                "playerAugment2",
                "playerAugment3",
                "playerAugment4"
        ));
        participant.put("doubleKills", 4);
        participant.put("tripleKills", 2);
        participant.put("quadraKills", 1);
        participant.put("pentaKills", 0);
        participant.put("largestKillingSpree", 11);
        participant.put("legendaryCount", 2);
        participant.put("perk0", 8437);
        participant.put("playerAugment1", 30001);
        participant.put("playerAugment2", 30002);
        participant.put("playerAugment3", 30003);
        participant.put("playerAugment4", 30004);
        response.set("game", game);

        GameDetail detail = mapper.mapGameSummary(response);

        GameDetail.Stats mappedStats = detail.getParticipants().getFirst().getStats();
        assertThat(mappedStats.getDoubleKills()).isEqualTo(4);
        assertThat(mappedStats.getTripleKills()).isEqualTo(2);
        assertThat(mappedStats.getQuadraKills()).isEqualTo(1);
        assertThat(mappedStats.getLargestKillingSpree()).isEqualTo(11);
        assertThat(mappedStats.getLegendaryCount()).isEqualTo(2);
        assertThat(mappedStats.getPerk0()).isEqualTo(8437);
        assertThat(mappedStats.getPlayerAugment1()).isEqualTo(30001);
        assertThat(mappedStats.getPlayerAugment2()).isEqualTo(30002);
        assertThat(mappedStats.getPlayerAugment3()).isEqualTo(30003);
        assertThat(mappedStats.getPlayerAugment4()).isEqualTo(30004);
    }

    @Test
    void mapGameSummary_readsNestedPerksAndChallengesFromLiveSgpSummaryShape() {
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode game = gameNode(300005L, true);
        ObjectNode participant = (ObjectNode) game.withArray("participants").get(0);
        ObjectNode stats = (ObjectNode) participant.get("stats");
        stats.remove(List.of("perk0", "legendaryCount"));
        participant.remove("perk0");
        participant.remove("legendaryCount");

        ObjectNode perks = participant.putObject("perks");
        ArrayNode styles = perks.putArray("styles");
        ObjectNode primaryStyle = styles.addObject();
        primaryStyle.put("style", 8000);
        primaryStyle.putArray("selections")
                .addObject()
                .put("perk", 8128)
                .put("var1", 11)
                .put("var2", 22)
                .put("var3", 33);
        ObjectNode subStyle = styles.addObject();
        subStyle.put("style", 8100);
        subStyle.putArray("selections").addObject().put("perk", 8135);

        ObjectNode challenges = participant.putObject("challenges");
        challenges.put("legendaryCount", 3);
        challenges.put("damagePerMinute", 1234.5);
        participant.put("liveOnlyMetric", 2468);
        response.set("game", game);

        GameDetail detail = mapper.mapGameSummary(response);
        GameDetail.Stats mappedStats = detail.getParticipants().getFirst().getStats();

        assertThat(mappedStats.getPerk0()).isEqualTo(8128);
        assertThat(mappedStats.getPerkPrimaryStyle()).isEqualTo(8000);
        assertThat(mappedStats.getPerkSubStyle()).isEqualTo(8100);
        assertThat(mappedStats.getLegendaryCount()).isEqualTo(3);
        assertThat(mappedStats.getChallenges()).containsEntry("damagePerMinute", 1234.5);
        assertThat(mappedStats.getExtraFields()).containsEntry("liveOnlyMetric", 2468);
        assertThat(mappedStats.getPerks()).isNotEmpty();
        assertThatCode(() -> {
            String serialized = objectMapper.writeValueAsString(detail);
            assertThat(serialized).contains("\"extraFields\"").contains("liveOnlyMetric");
        }).doesNotThrowAnyException();
    }

    @Test
    void mapGameSummary_preservesRawParticipantPositionFields() {
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode game = gameNode(300006L, true);
        ObjectNode participant = (ObjectNode) game.withArray("participants").get(0);
        participant.put("teamPosition", "MIDDLE");
        participant.put("individualPosition", "MIDDLE");
        participant.put("selectedPosition", "MID");
        participant.put("lane", "MIDDLE");
        participant.put("role", "SOLO");
        response.set("game", game);

        GameDetail mapped = mapper.mapGameSummary(response);

        GameDetail.GameParticipant mappedParticipant = mapped.getParticipants().getFirst();
        assertThat(mappedParticipant.getTeamPosition()).isEqualTo("MIDDLE");
        assertThat(mappedParticipant.getIndividualPosition()).isEqualTo("MIDDLE");
        assertThat(mappedParticipant.getSelectedPosition()).isEqualTo("MID");
        assertThat(mappedParticipant.getTimeline()).isNotNull();
        assertThat(mappedParticipant.getTimeline().getRawLane()).isEqualTo("MIDDLE");
        assertThat(mappedParticipant.getTimeline().getRawRole()).isEqualTo("SOLO");
        assertThat(mappedParticipant.getStats().getExtraFields())
                .doesNotContainKeys("teamPosition", "individualPosition", "selectedPosition", "lane", "role");
    }

    @Test
    void mapGameDetails_toleratesMissingFieldsAndParticipants() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("gameId", 300002L);
        response.put("gameDuration", 1400);

        assertThatCode(() -> mapper.mapGameDetails(response)).doesNotThrowAnyException();

        GameDetail detail = mapper.mapGameDetails(response);
        assertThat(detail.getGameId()).isEqualTo(300002L);
        assertThat(detail.getParticipants()).isEmpty();
        assertThat(detail.getParticipantIdentities()).isEmpty();
    }

    @Test
    void mapGameDetails_returnsEmptyDetailWhenJsonIsMissing() {
        GameDetail detail = mapper.mapGameDetails(null);

        assertThat(detail.getGameId()).isNull();
        assertThat(detail.getParticipants()).isEmpty();
        assertThat(detail.getParticipantIdentities()).isEmpty();
    }

    private ObjectNode gameNode(long gameId, boolean withParticipants) {
        ObjectNode game = objectMapper.createObjectNode();
        game.put("gameId", gameId);
        game.put("queueId", 450);
        game.put("gameMode", "ARAM");
        game.put("gameCreation", 1710001234000L);
        game.put("gameDuration", 1320);
        game.put("mapId", 12);
        if (withParticipants) {
            ArrayNode participants = game.putArray("participants");
            for (int i = 1; i <= 10; i++) {
                participants.add(participantNode(i));
            }
        }
        return game;
    }

    private ObjectNode participantNode(int participantId) {
        ObjectNode participant = objectMapper.createObjectNode();
        participant.put("participantId", participantId);
        participant.put("teamId", participantId <= 5 ? 100 : 200);
        participant.put("championId", participantId == 1 ? 103 : 200 + participantId);
        participant.put("spell1Id", participantId == 1 ? 4 : 7);
        participant.put("spell2Id", participantId == 1 ? 32 : 14);

        ObjectNode stats = participant.putObject("stats");
        stats.put("win", participantId <= 5);
        stats.put("kills", participantId == 1 ? 13 : participantId);
        stats.put("deaths", participantId == 1 ? 4 : 5);
        stats.put("assists", participantId == 1 ? 22 : 8);
        stats.put("item0", participantId == 1 ? 6655 : 2000 + participantId);
        stats.put("item1", 3020);
        stats.put("item2", 3089);
        stats.put("item3", 4645);
        stats.put("item4", 3135);
        stats.put("item5", 3157);
        stats.put("item6", participantId == 1 ? 3363 : 3340);
        stats.put("totalDamageDealtToChampions", participantId == 1 ? 32123 : 12000 + participantId);
        stats.put("totalDamageTaken", participantId == 1 ? 20321 : 11000 + participantId);
        stats.put("goldEarned", participantId == 1 ? 15321 : 9000 + participantId);
        stats.put("totalMinionsKilled", participantId == 1 ? 72 : participantId);
        stats.put("neutralMinionsKilled", participantId == 1 ? 0 : participantId);
        stats.put("visionScore", participantId == 1 ? 18 : participantId);
        stats.put("doubleKills", participantId == 1 ? 3 : 0);
        stats.put("tripleKills", participantId == 1 ? 2 : 0);
        stats.put("quadraKills", participantId == 1 ? 1 : 0);
        stats.put("pentaKills", 0);
        stats.put("largestKillingSpree", participantId == 1 ? 9 : 1);
        stats.put("legendaryCount", participantId == 1 ? 1 : 0);
        stats.put("perk0", participantId == 1 ? 8214 : 8005);
        stats.put("playerAugment1", participantId == 1 ? 20001 : 20002);

        ObjectNode player = participant.putObject("player");
        player.put("puuid", "detail-puuid-" + participantId);
        player.put("gameName", "DetailPlayer" + participantId);
        player.put("tagLine", "D" + participantId);
        player.put("summonerName", "DetailSummoner" + participantId);
        player.put("summonerId", 7000L + participantId);
        return participant;
    }
}
