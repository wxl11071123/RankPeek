package io.rankpeek.server.auth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JavaHttpTencentSesTransportTest {

    @Test
    void postsSignedRequestWithoutTryingToSetRestrictedHostHeader() throws Exception {
        CapturingServer server = CapturingServer.start();

        try {
            JavaHttpTencentSesTransport transport = new JavaHttpTencentSesTransport(properties());
            TencentSesTransportResponse response = transport.post(
                    server.uri(),
                    Map.of(
                            "Content-Type", "application/json; charset=utf-8",
                            "Host", "ses.tencentcloudapi.com",
                            "X-TC-Action", "SendEmail"
                    ),
                    "{\"hello\":\"rankpeek\"}"
            );

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"ok\":true}");
            assertThat(server.capturedBody).isEqualTo("{\"hello\":\"rankpeek\"}");
            assertThat(server.capturedAction).isEqualTo("SendEmail");
        } finally {
            server.stop();
        }
    }

    private static TencentSesEmailProperties properties() {
        return new TencentSesEmailProperties(
                true,
                "test-secret-id",
                "test-secret-key",
                "ap-hongkong",
                "https://ses.tencentcloudapi.com",
                "RankPeek <RankPeek@notify.rankpeek.cn>",
                "RankPeek 注册验证码",
                180489L,
                "RankPeek 密码重置",
                180490L,
                5000,
                10000
        );
    }

    private static class CapturingServer {
        private final HttpServer server;
        private String capturedBody;
        private String capturedAction;

        private CapturingServer(HttpServer server) {
            this.server = server;
        }

        static CapturingServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            CapturingServer capturingServer = new CapturingServer(server);
            server.createContext("/", exchange -> {
                capturingServer.capturedAction = exchange.getRequestHeaders().getFirst("X-TC-Action");
                capturingServer.capturedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return capturingServer;
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        void stop() {
            server.stop(0);
        }
    }
}
