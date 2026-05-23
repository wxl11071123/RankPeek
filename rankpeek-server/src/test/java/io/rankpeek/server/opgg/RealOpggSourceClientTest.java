package io.rankpeek.server.opgg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RealOpggSourceClientTest {

    @Test
    void fetchChampionDetailUsesLatestVersionAndParsesCoreOpggSections() throws Exception {
        try (ServerFixture server = ServerFixture.responding(uri -> {
            if (uri.equals("/api/kr/champions/ranked/versions")) {
                return new ServerResponse(200, "{\"data\":[\"16.10\",\"16.09\"]}");
            }
            if (uri.startsWith("/api/kr/champions/ranked/103/mid?")) {
                return new ServerResponse(200, fixtureJson());
            }
            return new ServerResponse(404, "{}");
        })) {
            RealOpggSourceClient client = new RealOpggSourceClient(
                    properties(server.url("")),
                    new ObjectMapper(),
                    Clock.fixed(Instant.parse("2026-05-23T04:00:00Z"), ZoneOffset.UTC)
            );

            OpggChampionDetail detail = client.fetchChampionDetail(new OpggChampionDetailQuery(
                    103,
                    "ranked",
                    "kr",
                    "emerald_plus",
                    "mid"
            ));

            assertThat(server.requestUris()).containsExactly(
                    "/api/kr/champions/ranked/versions",
                    "/api/kr/champions/ranked/103/mid?tier=emerald_plus&version=16.10"
            );
            assertThat(server.lastCookieHeader()).isNull();
            assertThat(server.lastUserAgent()).isEqualTo("RankPeek/opgg-test-client");
            assertThat(detail.championId()).isEqualTo(103);
            assertThat(detail.mode()).isEqualTo("ranked");
            assertThat(detail.region()).isEqualTo("kr");
            assertThat(detail.tier()).isEqualTo("emerald_plus");
            assertThat(detail.position()).isEqualTo("mid");
            assertThat(detail.version()).isEqualTo("16.10");
            assertThat(detail.updatedAt()).isEqualTo(Instant.parse("2026-05-23T04:00:00Z"));
            assertThat(detail.stats().games()).isEqualTo(920_524);
            assertThat(detail.stats().winRate()).isEqualTo(0.506869);
            assertThat(detail.stats().pickRate()).isEqualTo(0.0972002);
            assertThat(detail.stats().banRate()).isEqualTo(0.0308764);
            assertThat(detail.stats().kda()).isEqualTo(2.565002);
            assertThat(detail.summonerSpells()).singleElement()
                    .satisfies(option -> {
                        assertThat(option.ids()).containsExactly(4, 12);
                        assertThat(option.games()).isEqualTo(16_712);
                        assertThat(option.winRate()).isCloseTo(0.501555, within(0.000001));
                        assertThat(option.pickRate()).isEqualTo(0.5781);
                    });
            assertThat(detail.runes()).singleElement()
                    .satisfies(option -> assertThat(option.ids()).containsExactly(8000, 8100, 8005, 9111, 8138, 8135, 5008, 5008, 5002));
            assertThat(detail.skillOrders()).singleElement()
                    .satisfies(option -> assertThat(option.ids()).containsExactly(3, 1, 2));
            assertThat(detail.coreItems()).singleElement()
                    .satisfies(option -> assertThat(option.ids()).containsExactly(3118, 3152, 4645));
        }
    }

    @Test
    void aramChampionDetailUsesNonePositionAndArenaOmitsPositionSegment() throws Exception {
        try (ServerFixture server = ServerFixture.responding(uri -> {
            if (uri.equals("/api/kr/champions/aram/versions") || uri.equals("/api/kr/champions/arena/versions")) {
                return new ServerResponse(200, "{\"data\":[\"16.10\"]}");
            }
            if (uri.startsWith("/api/kr/champions/aram/103/none?") || uri.startsWith("/api/kr/champions/arena/103?")) {
                return new ServerResponse(200, fixtureJson());
            }
            return new ServerResponse(404, "{}");
        })) {
            RealOpggSourceClient client = new RealOpggSourceClient(
                    properties(server.url("")),
                    new ObjectMapper(),
                    Clock.fixed(Instant.parse("2026-05-23T04:00:00Z"), ZoneOffset.UTC)
            );

            client.fetchChampionDetail(new OpggChampionDetailQuery(103, "aram", "kr", "all", "none"));
            client.fetchChampionDetail(new OpggChampionDetailQuery(103, "arena", "kr", "all", "none"));

            assertThat(server.requestUris()).contains(
                    "/api/kr/champions/aram/103/none?tier=all&version=16.10",
                    "/api/kr/champions/arena/103?tier=all&version=16.10"
            );
        }
    }

    private static OpggSourceProperties properties(String baseUrl) {
        return new OpggSourceProperties(
                baseUrl,
                "RankPeek/opgg-test-client",
                500,
                2_000,
                200_000,
                1_800
        );
    }

    private static String fixtureJson() {
        return """
                {
                  "data": {
                    "summary": {
                      "id": 103,
                      "average_stats": {
                        "play": 940925,
                        "win_rate": 0.507389,
                        "pick_rate": 0.098344,
                        "ban_rate": 0.0308764,
                        "kda": 2.556245
                      },
                      "positions": [
                        {
                          "name": "MID",
                          "stats": {
                            "play": 920524,
                            "win_rate": 0.506869,
                            "pick_rate": 0.0972002,
                            "ban_rate": 0.0308764,
                            "kda": 2.565002
                          }
                        }
                      ]
                    },
                    "summoner_spells": [
                      { "ids": [4, 12], "play": 16712, "win": 8382, "pick_rate": 0.5781 }
                    ],
                    "runes": [
                      {
                        "primary_page_id": 8000,
                        "secondary_page_id": 8100,
                        "primary_rune_ids": [8005, 9111],
                        "secondary_rune_ids": [8138, 8135],
                        "stat_mod_ids": [5008, 5008, 5002],
                        "play": 9000,
                        "win": 5000,
                        "pick_rate": 0.45
                      }
                    ],
                    "skill_masteries": [
                      { "ids": [3, 1, 2], "play": 8000, "win": 4200, "pick_rate": 0.4 }
                    ],
                    "core_items": [
                      { "ids": [3118, 3152, 4645], "play": 3016, "win": 1623, "pick_rate": 0.1913 }
                    ]
                  }
                }
                """;
    }

    private record ServerResponse(int status, String body) {
    }

    private static class ServerFixture implements AutoCloseable {
        private final HttpServer server;
        private final Function<String, ServerResponse> responder;
        private final List<String> requestUris = new ArrayList<>();
        private String lastCookieHeader;
        private String lastUserAgent;

        private ServerFixture(Function<String, ServerResponse> responder) throws IOException {
            this.responder = responder;
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", this::respond);
            server.start();
        }

        static ServerFixture responding(Function<String, ServerResponse> responder) throws IOException {
            return new ServerFixture(responder);
        }

        String url(String path) {
            return "http://127.0.0.1:%d%s".formatted(server.getAddress().getPort(), path);
        }

        List<String> requestUris() {
            return requestUris;
        }

        String lastCookieHeader() {
            return lastCookieHeader;
        }

        String lastUserAgent() {
            return lastUserAgent;
        }

        private void respond(HttpExchange exchange) throws IOException {
            requestUris.add(exchange.getRequestURI().toString());
            lastCookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            lastUserAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            ServerResponse response = responder.apply(exchange.getRequestURI().toString());
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
