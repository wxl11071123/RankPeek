package io.rankpeek.server;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
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
