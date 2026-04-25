package io.rankpeek.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rankpeek.model.GameDetail;
import io.rankpeek.model.MatchHistory;
import io.rankpeek.model.MatchHistoryFetchResult;
import io.rankpeek.model.Rank;
import io.rankpeek.model.Summoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMatchHistoryCacheRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcMatchHistoryCacheRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:rankpeek-cache-" + System.nanoTime() + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        new LocalCacheSchemaInitializer(jdbcTemplate).initializeSchema();
        repository = new JdbcMatchHistoryCacheRepository(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void saveMatchHistory_persistsRecentMatchesParticipantsAndFetchState() {
        repository.saveMatchHistory("target-puuid", List.of(createMatch(1001L, "target-puuid", 10)));

        Optional<MatchHistoryFetchResult> result = repository.findRecentMatchHistory("target-puuid", 50);

        assertThat(result).isPresent();
        assertThat(result.get().getMatches()).extracting(MatchHistory::getGameId).containsExactly(1001L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_participant_cache WHERE game_id = 1001",
                Integer.class
        )).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_match_index WHERE game_id = ?",
                Integer.class,
                1001L
        )).isEqualTo(10);
        assertThat(jdbcTemplate.queryForList(
                "SELECT puuid FROM player_match_index WHERE game_id = ?",
                String.class,
                1001L
        )).containsExactlyInAnyOrder(
                "target-puuid",
                "player-2",
                "player-3",
                "player-4",
                "player-5",
                "player-6",
                "player-7",
                "player-8",
                "player-9",
                "player-10"
        );
        assertThat(repository.getMatchUpdatedAt("target-puuid")).isPresent();
    }

    @Test
    void findRecentMatchHistory_returnsCachedMatchForNonTargetParticipant() {
        repository.saveMatchHistory("target-puuid", List.of(createMatch(1002L, "target-puuid", 10)));

        Optional<MatchHistoryFetchResult> result = repository.findRecentMatchHistory("player-2", 50);

        assertThat(result).isPresent();
        assertThat(result.get().getMatches()).extracting(MatchHistory::getGameId).containsExactly(1002L);
    }

    @Test
    void trimPlayerMatchIndex_keepsOnlyNewestFiftyRowsForEveryInvolvedPlayer() {
        List<MatchHistory> matches = new ArrayList<>();
        for (long i = 1; i <= 55; i++) {
            matches.add(createMatch(i, "target-puuid", 2));
        }

        repository.saveMatchHistory("target-puuid", matches);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_match_index WHERE puuid = ?",
                Integer.class,
                "target-puuid"
        )).isEqualTo(50);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT MIN(game_id) FROM player_match_index WHERE puuid = ?",
                Long.class,
                "target-puuid"
        )).isEqualTo(6L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM player_match_index WHERE puuid = ?",
                Integer.class,
                "player-2"
        )).isEqualTo(50);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT MIN(game_id) FROM player_match_index WHERE puuid = ?",
                Long.class,
                "player-2"
        )).isEqualTo(6L);
    }

    @Test
    void findRecentMatchHistory_skipsCorruptJsonRows() {
        repository.saveMatchHistory("target-puuid", List.of(createMatch(2001L, "target-puuid", 2)));
        jdbcTemplate.update(
                "INSERT INTO match_cache (game_id, game_creation, raw_json, updated_at) VALUES (?, ?, ?, ?)",
                2002L,
                9999999999L,
                "{broken-json",
                System.currentTimeMillis()
        );
        jdbcTemplate.update(
                "INSERT INTO player_match_index (puuid, game_id, game_creation, updated_at) VALUES (?, ?, ?, ?)",
                "target-puuid",
                2002L,
                9999999999L,
                System.currentTimeMillis()
        );

        Optional<MatchHistoryFetchResult> result = repository.findRecentMatchHistory("target-puuid", 50);

        assertThat(result).isPresent();
        assertThat(result.get().getMatches()).extracting(MatchHistory::getGameId).containsExactly(2001L);
    }

    @Test
    void saveAndReadGameDetailSummonerAndRank() {
        GameDetail detail = new GameDetail();
        detail.setGameId(3001L);
        detail.setGameCreation(1710000000000L);
        repository.saveGameDetail(detail);

        Summoner summoner = new Summoner();
        summoner.setPuuid("target-puuid");
        summoner.setGameName("Tester");
        summoner.setTagLine("CN1");
        summoner.setSummonerLevel(30);
        repository.saveSummoner(summoner);

        Rank rank = new Rank();
        Rank.QueueMap queueMap = new Rank.QueueMap();
        Rank.QueueInfo solo = new Rank.QueueInfo();
        solo.setTier("GOLD");
        solo.setWins(12);
        queueMap.setRankedSolo5x5(solo);
        rank.setQueueMap(queueMap);
        repository.saveRank("target-puuid", rank);

        assertThat(repository.findGameDetail(3001L)).isPresent();
        assertThat(repository.findSummonerByPuuid("target-puuid")).map(Summoner::getGameName).contains("Tester");
        assertThat(repository.findSummonerByName("Tester", "CN1")).map(Summoner::getPuuid).contains("target-puuid");
        assertThat(repository.findRank("target-puuid"))
                .map(found -> found.getQueueMap().getRankedSolo5x5().getTier())
                .contains("GOLD");
    }

    private MatchHistory createMatch(long gameId, String targetPuuid, int participantCount) {
        MatchHistory match = new MatchHistory();
        match.setGameId(gameId);
        match.setQueueId(420);
        match.setGameCreation(1710000000000L + gameId);
        match.setGameDuration(1800);
        match.setGameMode("CLASSIC");
        match.setGameType("MATCHED_GAME");
        match.setMapId(11);
        match.setPlatformId("HN1");

        List<MatchHistory.Participant> participants = new ArrayList<>();
        List<MatchHistory.ParticipantIdentity> identities = new ArrayList<>();
        for (int i = 1; i <= participantCount; i++) {
            String puuid = i == 1 ? targetPuuid : "player-" + i;
            MatchHistory.Participant participant = new MatchHistory.Participant();
            participant.setParticipantId(i);
            participant.setTeamId(i <= participantCount / 2 ? 100 : 200);
            participant.setChampionId(10 + i);
            participant.setSpell1Id(4);
            participant.setSpell2Id(14);
            MatchHistory.Stats stats = new MatchHistory.Stats();
            stats.setWin(i <= participantCount / 2);
            stats.setKills(i);
            stats.setDeaths(2);
            stats.setAssists(3);
            stats.setGoldEarned(9000 + i);
            stats.setTotalDamageDealtToChampions(12000 + i);
            stats.setTotalDamageTaken(15000 + i);
            stats.setTotalMinionsKilled(150 + i);
            stats.setNeutralMinionsKilled(5);
            participant.setStats(stats);
            participants.add(participant);

            MatchHistory.ParticipantIdentity identity = new MatchHistory.ParticipantIdentity();
            identity.setParticipantId(i);
            MatchHistory.Player player = new MatchHistory.Player();
            player.setPuuid(puuid);
            player.setGameName("Player" + i);
            player.setTagLine("CN1");
            player.setSummonerName("Player" + i);
            identity.setPlayer(player);
            identities.add(identity);
        }

        match.setParticipants(participants);
        match.setParticipantIdentities(identities);
        return match;
    }
}
