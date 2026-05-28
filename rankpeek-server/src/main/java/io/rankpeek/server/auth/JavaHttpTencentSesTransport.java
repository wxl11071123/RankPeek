package io.rankpeek.server.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class JavaHttpTencentSesTransport implements TencentSesTransport {

    private final TencentSesEmailProperties properties;

    public JavaHttpTencentSesTransport(TencentSesEmailProperties properties) {
        this.properties = properties;
    }

    @Override
    public TencentSesTransportResponse post(URI uri, Map<String, String> headers, String body) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach((name, value) -> {
                if (!"Host".equalsIgnoreCase(name)) {
                    requestBuilder.header(name, value);
                }
            });
            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return new TencentSesTransportResponse(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tencent Cloud SES request was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Tencent Cloud SES request failed: " + exception.getMessage(), exception);
        }
    }
}
