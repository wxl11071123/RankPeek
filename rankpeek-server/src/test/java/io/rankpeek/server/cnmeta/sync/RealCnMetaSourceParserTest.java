package io.rankpeek.server.cnmeta.sync;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealCnMetaSourceParserTest {

    private final RealCnMetaSourceParser parser = new RealCnMetaSourceParser();

    @Test
    void parsesTencentChampionDetailsCompressedPayloadAsAggregateAllRole() {
        CnMetaSourcePayload payload = parser.parse(
                """
                        {
                          "HttpStatus": 200,
                          "code": 0,
                          "data": {
                            "result": "{\\"championdetails\\":\\"1_666_4.6825_8945_9126_7979_1634_8296_2_18_0.0056_0.3114#2_902_3.10_7000_8000_900_1550_7600_7_9_9.0E-4_0.0123\\"}"
                          },
                          "message": ""
                        }
                        """,
                "http://localhost/public-meta?championid=666&tier=20&dtstatdate=20260514",
                "26.09|420|GOLD|TOP",
                "GOLD",
                "TOP",
                200
        );

        assertThat(payload.source()).isEqualTo("real-101");
        assertThat(payload.rows()).hasSize(2);
        assertThat(payload.rows().get(0).championId()).isEqualTo(666);
        assertThat(payload.rows().get(0).role()).isEqualTo("ALL");
        assertThat(payload.rows().get(0).tierScope()).isEqualTo("GOLD");
        assertThat(payload.rows().get(0).rankIndex()).isEqualTo(1);
        assertThat(payload.rows().get(0).avgKda()).isEqualByComparingTo("4.6825");
        assertThat(payload.rows().get(0).avgDamage()).isEqualByComparingTo("8945");
        assertThat(payload.rows().get(0).avgDamageTaken()).isEqualByComparingTo("9126");
        assertThat(payload.rows().get(0).avgHeal()).isEqualByComparingTo("7979");
        assertThat(payload.rows().get(0).avgDurationSeconds()).isEqualTo(1634);
        assertThat(payload.rows().get(0).avgGold()).isEqualByComparingTo("8296");
        assertThat(payload.rows().get(0).avgKills()).isEqualByComparingTo("2");
        assertThat(payload.rows().get(0).avgAssists()).isEqualByComparingTo("18");
        assertThat(payload.rows().get(0).pickRate()).isEqualByComparingTo("0.0056");
        assertThat(payload.rows().get(0).banRate()).isEqualByComparingTo("0.3114");
        assertThat(payload.rows().get(0).dataSourceNote())
                .isEqualTo("101 getRankFieldAverage aggregate; role=ALL; not lane-specific");
        assertThat(payload.rows().get(1).pickRate()).isEqualByComparingTo("0.00090");
    }

    @Test
    void parsesPublicAggregateChampionRowsAndNormalizesPercentages() {
        CnMetaSourcePayload payload = parser.parse(
                """
                        {
                          "dataDate": "2026-05-14",
                          "updateTime": "2026-05-14 10:30:00",
                          "data": {
                            "rows": [
                              {
                                "championId": 103,
                                "winRate": 52.1,
                                "pickRate": 0.143,
                                "banRate": "6.1%",
                                "avgKda": 3.42,
                                "avgGold": 11890,
                                "avgDamageShare": "28.6",
                                "avgDamageTakenShare": 0.184,
                                "rankIndex": 1,
                                "sampleCount": 12345
                              },
                              {
                                "heroId": "81",
                                "win_rate": "50.75",
                                "pick_rate": "17.1%",
                                "ban_rate": 0.079,
                                "kda": "3.18",
                                "gold": "12110.00",
                                "damageShare": 27.4,
                                "damageTakenShare": "19.1",
                                "rank": 2,
                                "sampleNote": "public aggregate"
                              }
                            ]
                          }
                        }
                        """,
                "http://localhost/public-meta",
                "26.09|420|GOLD|MID",
                "GOLD",
                "MID",
                200
        );

        assertThat(payload.source()).isEqualTo("real-101");
        assertThat(payload.dataDate()).isEqualTo("2026-05-14");
        assertThat(payload.rows()).hasSize(2);
        assertThat(payload.rows().get(0).championId()).isEqualTo(103);
        assertThat(payload.rows().get(0).role()).isEqualTo("MID");
        assertThat(payload.rows().get(0).tierScope()).isEqualTo("GOLD");
        assertThat(payload.rows().get(0).winRate()).isEqualByComparingTo("0.521");
        assertThat(payload.rows().get(0).pickRate()).isEqualByComparingTo("0.143");
        assertThat(payload.rows().get(0).banRate()).isEqualByComparingTo("0.061");
        assertThat(payload.rows().get(0).avgDamageShare()).isEqualByComparingTo("0.286");
        assertThat(payload.rows().get(0).avgDamageTakenShare()).isEqualByComparingTo("0.184");
        assertThat(payload.rows().get(0).sampleNote()).contains("sampleCount=12345");
        assertThat(payload.rows().get(1).championId()).isEqualTo(81);
        assertThat(payload.rows().get(1).winRate()).isEqualByComparingTo("0.5075");
    }

    @Test
    void missingChampionDetailsInTencentResultFailsWithClearError() {
        assertThatThrownBy(() -> parser.parse(
                """
                        {
                          "HttpStatus": 200,
                          "code": 0,
                          "data": {
                            "result": "{\\"other\\":\\"value\\"}"
                          },
                          "message": ""
                        }
                        """,
                "http://localhost/public-meta",
                "26.09|420|GOLD|TOP",
                "GOLD",
                "TOP",
                200
        ))
                .isInstanceOf(CnMetaSourceException.class)
                .hasMessageContaining("championdetails");
    }

    @Test
    void shortChampionDetailsRowFailsWithClearError() {
        assertThatThrownBy(() -> parser.parse(
                """
                        {
                          "data": {
                            "result": "{\\"championdetails\\":\\"1_666_4.6825\\"}"
                          }
                        }
                        """,
                "http://localhost/public-meta",
                "26.09|420|GOLD|TOP",
                "GOLD",
                "TOP",
                200
        ))
                .isInstanceOf(CnMetaSourceException.class)
                .hasMessageContaining("championdetails")
                .hasMessageContaining("12 fields");
    }

    @Test
    void usesShanghaiDateAndNotesWhenSourceDateIsUnavailable() {
        CnMetaSourcePayload payload = parser.parse(
                """
                        {
                          "data": {
                            "rows": [
                              {
                                "championId": 64,
                                "winRate": 49.85,
                                "pickRate": 10.2,
                                "banRate": 13.2
                              }
                            ]
                          }
                        }
                        """,
                "http://localhost/public-meta",
                "26.09|420|GOLD|JUNGLE",
                "GOLD",
                "JUNGLE",
                200
        );

        assertThat(payload.dataDate()).isNotNull();
        assertThat(payload.rows()).singleElement()
                .satisfies(row -> assertThat(row.sampleNote()).contains("source date unavailable"));
    }

    @Test
    void missingChampionIdFailsWithClearError() {
        assertThatThrownBy(() -> parser.parse(
                """
                        {
                          "data": {
                            "rows": [
                              {"winRate": 52.1}
                            ]
                          }
                        }
                        """,
                "http://localhost/public-meta",
                "26.09|420|GOLD|MID",
                "GOLD",
                "MID",
                200
        ))
                .isInstanceOf(CnMetaSourceException.class)
                .hasMessageContaining("championId");
    }
}
