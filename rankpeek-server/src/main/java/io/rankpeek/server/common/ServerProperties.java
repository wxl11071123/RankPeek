package io.rankpeek.server.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rankpeek.server")
public record ServerProperties(String service, String mode, String version) {
}
