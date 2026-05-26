package io.rankpeek.server;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BuildConfigurationTest {

    @Test
    void pomIncludesFlywayPostgresDatabaseSupport() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile());

        assertThat(dependencyCoordinates(document))
                .contains("org.flywaydb:flyway-database-postgresql");
    }

    @Test
    void ubuntuNginxTemplateNormalizesForwardedForAndProtectsCostlyEndpoints() throws Exception {
        String siteConfig = Files.readString(Path.of("deploy/ubuntu/nginx/rankpeek-server.conf.example"));
        String proxyHeaders = Files.readString(Path.of("deploy/ubuntu/nginx/rankpeek-proxy-headers.conf.example"));

        assertThat(siteConfig)
                .contains("limit_req_zone $binary_remote_addr zone=rankpeek_auth")
                .contains("limit_req_zone $binary_remote_addr zone=rankpeek_ai")
                .contains("location ~ ^/api/auth/(register|login|refresh)$")
                .contains("location = /api/analysis/coach-summary")
                .contains("location = /api/analysis/pregame/stream")
                .contains("location = /api/analysis/postgame/stream")
                .contains("proxy_buffering off")
                .contains("include /etc/nginx/snippets/rankpeek-proxy-headers.conf;");
        assertThat(proxyHeaders)
                .contains("proxy_set_header X-Forwarded-For $remote_addr;")
                .doesNotContain("$proxy_add_x_forwarded_for");
    }

    private static List<String> dependencyCoordinates(Document document) {
        var dependencyNodes = document.getElementsByTagName("dependency");
        List<String> coordinates = new ArrayList<>();
        for (int index = 0; index < dependencyNodes.getLength(); index++) {
            org.w3c.dom.Element dependency = (org.w3c.dom.Element) dependencyNodes.item(index);
            coordinates.add(textOf(dependency, "groupId") + ":" + textOf(dependency, "artifactId"));
        }
        return coordinates;
    }

    private static String textOf(org.w3c.dom.Element element, String tagName) {
        var nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }
}
