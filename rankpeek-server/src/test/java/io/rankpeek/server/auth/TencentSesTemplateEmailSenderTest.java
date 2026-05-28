package io.rankpeek.server.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TencentSesTemplateEmailSenderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsTencentSesTemplateRequestWithTemplateVariablesAndSignedHeaders() throws Exception {
        CapturingTransport transport = new CapturingTransport();
        TencentSesTemplateEmailSender sender = new TencentSesTemplateEmailSender(
                objectMapper,
                properties(),
                transport,
                Clock.fixed(Instant.parse("2026-05-28T08:00:00Z"), ZoneOffset.UTC)
        );

        sender.sendTemplateEmail(
                "user@example.com",
                180489L,
                "RankPeek 注册验证码",
                Map.of("code", "839204", "expire_minutes", "15")
        );

        assertThat(transport.uri).isEqualTo(URI.create("https://ses.tencentcloudapi.com"));
        assertThat(transport.headers)
                .containsEntry("Content-Type", "application/json; charset=utf-8")
                .containsEntry("Host", "ses.tencentcloudapi.com")
                .containsEntry("X-TC-Action", "SendEmail")
                .containsEntry("X-TC-Version", "2020-10-02")
                .containsEntry("X-TC-Region", "ap-hongkong")
                .containsEntry("X-TC-Timestamp", "1779955200");
        assertThat(transport.headers.get("Authorization"))
                .startsWith("TC3-HMAC-SHA256 Credential=test-secret-id/2026-05-28/ses/tc3_request");
        assertThat(transport.headers.get("Authorization")).doesNotContain("test-secret-key");

        JsonNode body = objectMapper.readTree(transport.body);
        assertThat(body.path("FromEmailAddress").asText()).isEqualTo("RankPeek <RankPeek@notify.rankpeek.cn>");
        assertThat(body.path("Destination").get(0).asText()).isEqualTo("user@example.com");
        assertThat(body.path("Subject").asText()).isEqualTo("RankPeek 注册验证码");
        assertThat(body.path("Template").path("TemplateID").asLong()).isEqualTo(180489L);
        assertThat(body.path("TriggerType").asInt()).isEqualTo(1);

        JsonNode templateData = objectMapper.readTree(body.path("Template").path("TemplateData").asText());
        assertThat(templateData.path("code").asText()).isEqualTo("839204");
        assertThat(templateData.path("expire_minutes").asText()).isEqualTo("15");
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

    private static class CapturingTransport implements TencentSesTransport {
        private URI uri;
        private Map<String, String> headers;
        private String body;

        @Override
        public TencentSesTransportResponse post(URI uri, Map<String, String> headers, String body) {
            this.uri = uri;
            this.headers = headers;
            this.body = body;
            return new TencentSesTransportResponse(200, """
                    {"Response":{"RequestId":"request-id","MessageId":"message-id"}}
                    """);
        }
    }
}
