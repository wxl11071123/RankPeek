package io.rankpeek.server.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "rankpeek.server")
public record ServerProperties(String service, String mode, String version, Cors cors) {

    public ServerProperties {
        if (cors == null) {
            cors = new Cors(List.of("*"));
        }
    }

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            if (allowedOrigins == null || allowedOrigins.isEmpty()) {
                allowedOrigins = List.of("*");
            } else {
                allowedOrigins = allowedOrigins.stream()
                        .map(origin -> origin == null ? "" : origin.trim())
                        .filter(origin -> !origin.isBlank())
                        .toList();
                if (allowedOrigins.isEmpty()) {
                    allowedOrigins = List.of("*");
                }
            }
        }
    }
}
