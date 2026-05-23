package io.rankpeek.server.cnmeta.sync;

import io.rankpeek.server.cnmeta.CnMetaRoles;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class RealCnMetaSourceClient implements CnMetaSourceClient {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final CnMetaSyncProperties properties;
    private final RealCnMetaSourceParser parser;

    public RealCnMetaSourceClient(CnMetaSyncProperties properties, RealCnMetaSourceParser parser) {
        this.properties = properties;
        this.parser = parser;
    }

    @Override
    public String source() {
        return "real-101";
    }

    @Override
    public CnMetaSourcePayload fetchChampionStats(String patchKey, Integer queueId, String tierScope, String role) {
        if (!properties.realSourceEnabled()) {
            throw new CnMetaSourceException("Real 101 source is disabled by rankpeek.cn-meta.sync.real-source-enabled=false");
        }
        if (properties.realEndpointTemplate() == null || properties.realEndpointTemplate().isBlank()) {
            throw new CnMetaSourceException("Real 101 endpoint template is not configured");
        }

        String effectiveRole = CnMetaRoles.ALL;
        String championId = String.valueOf(properties.realChampionId());
        String timeType = String.valueOf(properties.realTimeType());
        String tierCode = tierCodeFor(tierScope);
        CnMetaSourceException lastMissingData = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            String dataDate = dataDate(attempt);
            try {
                return fetchChampionStatsForDate(
                        patchKey,
                        queueId,
                        tierScope,
                        effectiveRole,
                        championId,
                        timeType,
                        tierCode,
                        dataDate
                );
            } catch (CnMetaSourceException exception) {
                if (isMissingDataResult(exception) && attempt < 4) {
                    lastMissingData = exception;
                    continue;
                }
                throw exception;
            }
        }
        throw lastMissingData == null
                ? new CnMetaSourceException("Real 101 source returned no usable data")
                : lastMissingData;
    }

    private CnMetaSourcePayload fetchChampionStatsForDate(
            String patchKey,
            Integer queueId,
            String tierScope,
            String effectiveRole,
            String championId,
            String timeType,
            String tierCode,
            String dataDate
    ) {
        String requestKey = "%s|%d|%s|%s|%s|%s|%s|%s".formatted(
                patchKey,
                queueId,
                tierScope,
                effectiveRole,
                championId,
                timeType,
                tierCode,
                dataDate
        );
        URI uri = buildUri(
                properties.realEndpointTemplate(),
                patchKey,
                queueId,
                tierScope,
                effectiveRole,
                championId,
                timeType,
                tierCode,
                dataDate
        );
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.realConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(properties.realReadTimeoutMs()))
                .header("User-Agent", properties.realUserAgent())
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String rawContent = readBounded(response.body(), properties.realMaxResponseBytes());
            int status = response.statusCode();
            if (properties.stopOnHttpStatus().contains(status)) {
                throw new CnMetaSourceException("Stopped real 101 source after HTTP " + status, status);
            }
            if (status < 200 || status >= 300) {
                throw new CnMetaSourceException("Real 101 source returned HTTP " + status, status);
            }
            if (containsRiskControl(rawContent)) {
                throw CnMetaSourceException.stopSignal("Detected CAPTCHA or risk control page from real 101 source");
            }
            return parser.parse(rawContent, uri.toString(), requestKey, tierScope, effectiveRole, status);
        } catch (CnMetaSourceException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw CnMetaSourceException.stopSignal("Real 101 source request interrupted");
        } catch (IOException exception) {
            throw new CnMetaSourceException("Real 101 source request failed: " + exception.getMessage());
        }
    }

    private String tierCodeFor(String tierScope) {
        String tierCode = properties.realTierCodeMap().get(tierScope.toUpperCase(Locale.ROOT));
        if (tierCode == null || tierCode.isBlank()) {
            throw new CnMetaSourceException("CN_META_TIER_CODE_MISSING: real 101 tier code is not configured for tierScope=" + tierScope);
        }
        return tierCode;
    }

    private String dataDate(int fallbackOffset) {
        return LocalDate.now(SHANGHAI)
                .minusDays((long) properties.realDataDateOffsetDays() + fallbackOffset)
                .format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static boolean isMissingDataResult(CnMetaSourceException exception) {
        String message = exception.getMessage();
        return message != null && (
                message.contains("data.result is empty")
                        || message.contains("championdetails is empty")
                        || message.contains("champion stats rows not found")
                        || message.contains("championdetails not found")
        );
    }

    private static URI buildUri(
            String template,
            String patchKey,
            Integer queueId,
            String tierScope,
            String role,
            String championId,
            String timeType,
            String tierCode,
            String dataDate
    ) {
        String url = template
                .replace("{patchKey}", encode(patchKey))
                .replace("{queueId}", encode(String.valueOf(queueId)))
                .replace("{tierScope}", encode(tierScope))
                .replace("{role}", encode(role))
                .replace("{championId}", encode(championId))
                .replace("{timeType}", encode(timeType))
                .replace("{tierCode}", encode(tierCode))
                .replace("{dataDate}", encode(dataDate));
        try {
            return URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new CnMetaSourceException("Real 101 endpoint template produced an invalid URL: " + exception.getMessage());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String readBounded(InputStream inputStream, int maxBytes) throws IOException {
        try (inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new CnMetaSourceException("Real 101 source response exceeded configured byte limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static boolean containsRiskControl(String rawContent) {
        String lower = rawContent.toLowerCase(Locale.ROOT);
        return lower.contains("captcha")
                || lower.contains("risk control")
                || rawContent.contains("验证码")
                || rawContent.contains("风控")
                || rawContent.contains("安全验证")
                || rawContent.contains("访问频繁");
    }
}
