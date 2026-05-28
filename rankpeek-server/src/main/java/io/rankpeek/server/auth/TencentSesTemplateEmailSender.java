package io.rankpeek.server.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TencentSesTemplateEmailSender {

    private static final String SERVICE = "ses";
    private static final String ACTION = "SendEmail";
    private static final String VERSION = "2020-10-02";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final TencentSesEmailProperties properties;
    private final TencentSesTransport transport;
    private final Clock clock;

    public TencentSesTemplateEmailSender(ObjectMapper objectMapper, TencentSesEmailProperties properties) {
        this(objectMapper, properties, new JavaHttpTencentSesTransport(properties), Clock.systemUTC());
    }

    TencentSesTemplateEmailSender(
            ObjectMapper objectMapper,
            TencentSesEmailProperties properties,
            TencentSesTransport transport,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transport = transport;
        this.clock = clock;
    }

    public void sendTemplateEmail(String to, long templateId, String subject, Map<String, String> templateData) {
        String body = requestBody(to, templateId, subject, templateData);
        URI endpoint = URI.create(properties.endpoint());
        Map<String, String> headers = signedHeaders(endpoint, body);
        TencentSesTransportResponse response = transport.post(endpoint, headers, body);
        verifyResponse(response);
    }

    private String requestBody(String to, long templateId, String subject, Map<String, String> templateData) {
        try {
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("TemplateID", templateId);
            template.put("TemplateData", objectMapper.writeValueAsString(templateData));

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("FromEmailAddress", properties.requireFromEmailAddress());
            request.put("Destination", List.of(to));
            request.put("Subject", subject);
            request.put("Template", template);
            request.put("TriggerType", 1);
            return objectMapper.writeValueAsString(request);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build Tencent Cloud SES request body", exception);
        }
    }

    private Map<String, String> signedHeaders(URI endpoint, String body) {
        Instant now = clock.instant();
        String timestamp = String.valueOf(now.getEpochSecond());
        String date = DATE_FORMATTER.format(now);
        String host = endpoint.getHost();
        String contentType = "application/json; charset=utf-8";

        String canonicalHeaders = "content-type:" + contentType + "\n"
                + "host:" + host + "\n"
                + "x-tc-action:" + ACTION.toLowerCase(Locale.ROOT) + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String hashedPayload = sha256Hex(body);
        String canonicalRequest = "POST\n/\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + hashedPayload;

        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = ALGORITHM + "\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);
        String signature = HexFormat.of().formatHex(hmacSha256(signingKey(date), stringToSign));
        String authorization = ALGORITHM
                + " Credential=" + properties.requireSecretId() + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", contentType);
        headers.put("Host", host);
        headers.put("X-TC-Action", ACTION);
        headers.put("X-TC-Timestamp", timestamp);
        headers.put("X-TC-Version", VERSION);
        headers.put("X-TC-Region", properties.region());
        headers.put("Authorization", authorization);
        return headers;
    }

    private byte[] signingKey(String date) {
        byte[] secretDate = hmacSha256(("TC3" + properties.requireSecretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        return hmacSha256(secretService, "tc3_request");
    }

    private void verifyResponse(TencentSesTransportResponse response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Tencent Cloud SES returned HTTP " + response.statusCode());
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode error = root.path("Response").path("Error");
            if (!error.isMissingNode()) {
                String code = error.path("Code").asText("UNKNOWN");
                String message = error.path("Message").asText("Tencent Cloud SES error");
                throw new IllegalStateException("Tencent Cloud SES returned " + code + ": " + message);
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse Tencent Cloud SES response", exception);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate SHA-256", exception);
        }
    }

    private static byte[] hmacSha256(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate HMAC-SHA256", exception);
        }
    }
}
