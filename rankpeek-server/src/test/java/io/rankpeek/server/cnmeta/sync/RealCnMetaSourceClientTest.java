package io.rankpeek.server.cnmeta.sync;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealCnMetaSourceClientTest {

    @Test
    void fetchChampionStatsReplacesConfirmed101TemplatePlaceholdersWithoutCookies() throws Exception {
        try (ServerFixture server = ServerFixture.respond(200, fixtureJson())) {
            RealCnMetaSourceClient client = new RealCnMetaSourceClient(
                    properties(true, server.url("/stats?championid={championId}&time_type={timeType}&tier={tierCode}&dtstatdate={dataDate}")),
                    new RealCnMetaSourceParser()
            );

            CnMetaSourcePayload payload = client.fetchChampionStats("26.09", 420, "PLATINUM", "MID");
            String expectedDataDate = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                    .minusDays(1)
                    .format(DateTimeFormatter.BASIC_ISO_DATE);

            assertThat(payload.source()).isEqualTo("real-101");
            assertThat(payload.sourceUrl()).contains("championid=666");
            assertThat(payload.sourceUrl()).contains("time_type=1");
            assertThat(payload.sourceUrl()).contains("tier=20");
            assertThat(payload.sourceUrl()).contains("dtstatdate=" + expectedDataDate);
            assertThat(payload.requestKey()).isEqualTo("26.09|420|PLATINUM|ALL|666|1|20|" + expectedDataDate);
            assertThat(payload.rows()).singleElement()
                    .satisfies(row -> assertThat(row.championId()).isEqualTo(103));
            assertThat(server.requestCount()).isEqualTo(1);
            assertThat(server.lastRequestUri()).contains("championid=666");
            assertThat(server.lastRequestUri()).contains("time_type=1");
            assertThat(server.lastRequestUri()).contains("tier=20");
            assertThat(server.lastRequestUri()).contains("dtstatdate=" + expectedDataDate);
            assertThat(server.lastRequestUri()).doesNotContain("role=");
            assertThat(server.lastCookieHeader()).isNull();
            assertThat(server.lastUserAgent()).isEqualTo("RankPeek/dev-public-aggregate-client");
        }
    }

    @Test
    void fetchChampionStatsFallsBackToOlderDataDateWhenRecentResultIsEmpty() throws Exception {
        LocalDate firstDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        LocalDate secondDate = firstDate.minusDays(1);
        String firstDateText = firstDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String secondDateText = secondDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        try (ServerFixture server = ServerFixture.responding(uri -> {
            if (uri.contains("dtstatdate=" + firstDateText)) {
                return new ServerResponse(200, "{\"data\":{\"result\":\"\"}}");
            }
            if (uri.contains("dtstatdate=" + secondDateText)) {
                return new ServerResponse(200, fixtureJson());
            }
            return new ServerResponse(500, "{}");
        })) {
            RealCnMetaSourceClient client = new RealCnMetaSourceClient(
                    properties(true, server.url("/stats?championid={championId}&tier={tierCode}&dtstatdate={dataDate}")),
                    new RealCnMetaSourceParser()
            );

            CnMetaSourcePayload payload = client.fetchChampionStats("26.09", 420, "PLATINUM", "MID");

            assertThat(payload.sourceUrl()).contains("dtstatdate=" + secondDateText);
            assertThat(payload.requestKey()).endsWith("|" + secondDateText);
            assertThat(payload.rows()).singleElement()
                    .satisfies(row -> assertThat(row.championId()).isEqualTo(103));
            assertThat(server.requestCount()).isEqualTo(2);
        }
    }

    @Test
    void disabledRealSourceDoesNotOpenHttpRequest() throws Exception {
        try (ServerFixture server = ServerFixture.respond(200, fixtureJson())) {
            RealCnMetaSourceClient client = new RealCnMetaSourceClient(
                    properties(false, server.url("/stats")),
                    new RealCnMetaSourceParser()
            );

            assertThatThrownBy(() -> client.fetchChampionStats("26.09", 420, "GOLD", "MID"))
                    .isInstanceOf(CnMetaSourceException.class)
                    .hasMessageContaining("disabled");
            assertThat(server.requestCount()).isZero();
        }
    }

    @Test
    void missingTierCodeDoesNotOpenHttpRequest() throws Exception {
        try (ServerFixture server = ServerFixture.respond(200, fixtureJson())) {
            RealCnMetaSourceClient client = new RealCnMetaSourceClient(
                    properties(true, server.url("/stats?championid={championId}&tier={tierCode}")),
                    new RealCnMetaSourceParser()
            );

            assertThatThrownBy(() -> client.fetchChampionStats("26.09", 420, "GOLD", "MID"))
                    .isInstanceOf(CnMetaSourceException.class)
                    .hasMessageContaining("CN_META_TIER_CODE_MISSING");
            assertThat(server.requestCount()).isZero();
        }
    }

    @Test
    void emptyEndpointTemplateDoesNotOpenHttpRequest() throws Exception {
        try (ServerFixture server = ServerFixture.respond(200, fixtureJson())) {
            RealCnMetaSourceClient client = new RealCnMetaSourceClient(
                    properties(true, ""),
                    new RealCnMetaSourceParser()
            );

            assertThatThrownBy(() -> client.fetchChampionStats("26.09", 420, "PLATINUM", "MID"))
                    .isInstanceOf(CnMetaSourceException.class)
                    .hasMessageContaining("endpoint template");
            assertThat(server.requestCount()).isZero();
        }
    }

    @Test
    void forbiddenAndTooManyRequestsAreStopSignals() throws Exception {
        try (ServerFixture forbidden = ServerFixture.respond(403, "{}")) {
            RealCnMetaSourceClient client = new RealCnMetaSourceClient(
                    properties(true, forbidden.url("/stats")),
                    new RealCnMetaSourceParser()
            );

            assertThatThrownBy(() -> client.fetchChampionStats("26.09", 420, "PLATINUM", "MID"))
                    .isInstanceOf(CnMetaSourceException.class)
                    .satisfies(exception -> assertThat(((CnMetaSourceException) exception).httpStatus()).isEqualTo(403));
        }

        try (ServerFixture tooManyRequests = ServerFixture.respond(429, "{}")) {
            RealCnMetaSourceClient client = new RealCnMetaSourceClient(
                    properties(true, tooManyRequests.url("/stats")),
                    new RealCnMetaSourceParser()
            );

            assertThatThrownBy(() -> client.fetchChampionStats("26.09", 420, "PLATINUM", "MID"))
                    .isInstanceOf(CnMetaSourceException.class)
                    .satisfies(exception -> assertThat(((CnMetaSourceException) exception).httpStatus()).isEqualTo(429));
        }
    }

    @Test
    void captchaOrRiskControlContentIsAStopSignal() throws Exception {
        try (ServerFixture server = ServerFixture.respond(200, "<html>captcha risk control</html>")) {
            RealCnMetaSourceClient client = new RealCnMetaSourceClient(
                    properties(true, server.url("/stats")),
                    new RealCnMetaSourceParser()
            );

            assertThatThrownBy(() -> client.fetchChampionStats("26.09", 420, "PLATINUM", "MID"))
                    .isInstanceOf(CnMetaSourceException.class)
                    .satisfies(exception -> assertThat(((CnMetaSourceException) exception).stopSignal()).isTrue());
        }
    }

    @Test
    void badJsonIsAFailedSourceResponse() throws Exception {
        try (ServerFixture server = ServerFixture.respond(200, "not-json")) {
            RealCnMetaSourceClient client = new RealCnMetaSourceClient(
                    properties(true, server.url("/stats")),
                    new RealCnMetaSourceParser()
            );

            assertThatThrownBy(() -> client.fetchChampionStats("26.09", 420, "PLATINUM", "MID"))
                    .isInstanceOf(CnMetaSourceException.class)
                    .hasMessageContaining("parse");
        }
    }

    private static CnMetaSyncProperties properties(boolean realEnabled, String endpointTemplate) {
        return new CnMetaSyncProperties(
                false,
                false,
                "mock",
                "0 30 4 * * *",
                "Asia/Shanghai",
                0,
                0,
                List.of(401, 403, 429),
                420,
                List.of("GOLD", "PLATINUM"),
                List.of("MID"),
                realEnabled,
                endpointTemplate,
                1,
                666,
                1,
                Map.of("PLATINUM", "20"),
                "RankPeek/dev-public-aggregate-client",
                500,
                2000,
                20000
        );
    }

    private static String fixtureJson() {
        return """
                {
                  "dataDate": "2026-05-14",
                  "data": {
                    "rows": [
                      {
                        "championId": 103,
                        "winRate": 52.1,
                        "pickRate": 14.3,
                        "banRate": 6.1
                      }
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
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicReference<String> lastRequestUri = new AtomicReference<>();
        private final AtomicReference<String> lastCookieHeader = new AtomicReference<>();
        private final AtomicReference<String> lastUserAgent = new AtomicReference<>();

        private ServerFixture(Function<String, ServerResponse> responder) throws IOException {
            this.responder = responder;
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", this::respond);
            server.start();
        }

        static ServerFixture respond(int status, String body) throws IOException {
            return responding(uri -> new ServerResponse(status, body));
        }

        static ServerFixture responding(Function<String, ServerResponse> responder) throws IOException {
            return new ServerFixture(responder);
        }

        String url(String path) {
            return "http://127.0.0.1:%d%s".formatted(server.getAddress().getPort(), path);
        }

        int requestCount() {
            return requestCount.get();
        }

        String lastRequestUri() {
            return lastRequestUri.get();
        }

        String lastCookieHeader() {
            return lastCookieHeader.get();
        }

        String lastUserAgent() {
            return lastUserAgent.get();
        }

        private void respond(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            lastRequestUri.set(exchange.getRequestURI().toString());
            lastCookieHeader.set(exchange.getRequestHeaders().getFirst("Cookie"));
            lastUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
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
