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
                .contains("location ~ ^/api/auth/(register|login|refresh|password-reset/(request|confirm))$")
                .contains("location = /api/analysis/coach-summary")
                .contains("location = /api/analysis/pregame/stream")
                .contains("location = /api/analysis/postgame/stream")
                .contains("proxy_buffering off")
                .contains("include /etc/nginx/snippets/rankpeek-proxy-headers.conf;");
        assertThat(proxyHeaders)
                .contains("proxy_set_header X-Forwarded-For $remote_addr;")
                .doesNotContain("$proxy_add_x_forwarded_for");
    }

    @Test
    void ubuntuPostgresBackupTemplatesIncludeRetentionAndRestoreDrill() throws Exception {
        String backupScript = Files.readString(Path.of("deploy/ubuntu/postgres/rankpeek-postgres-backup.sh.example"));
        String restoreScript = Files.readString(Path.of("deploy/ubuntu/postgres/rankpeek-postgres-restore-drill.sh.example"));
        String service = Files.readString(Path.of("deploy/ubuntu/postgres/rankpeek-postgres-backup.service.example"));
        String timer = Files.readString(Path.of("deploy/ubuntu/postgres/rankpeek-postgres-backup.timer.example"));

        assertThat(backupScript)
                .contains("pg_dump")
                .contains("--format=custom")
                .contains("sha256sum")
                .contains("RETENTION_DAYS")
                .contains("find \"$BACKUP_DIR\"");
        assertThat(restoreScript)
                .contains("createdb \"$DRILL_DB\"")
                .contains("pg_restore")
                .contains("--clean")
                .contains("--if-exists")
                .contains("flyway_schema_history")
                .contains("dropdb --if-exists \"$DRILL_DB\"");
        assertThat(service)
                .contains("User=postgres")
                .contains("ExecStart=/usr/local/sbin/rankpeek-postgres-backup.sh")
                .contains("ReadWritePaths=/var/backups/rankpeek/postgres");
        assertThat(timer)
                .contains("OnCalendar=*-*-* 03:15:00")
                .contains("Persistent=true");
    }

    @Test
    void ubuntuMonitoringTemplatesCheckHealthBackupsAndEmitAlerts() throws Exception {
        String smokeScript = Files.readString(Path.of("deploy/ubuntu/rankpeek-server-smoke.sh"));
        String monitorScript = Files.readString(Path.of("deploy/ubuntu/monitoring/rankpeek-server-monitor.sh.example"));
        String monitorEnv = Files.readString(Path.of("deploy/ubuntu/monitoring/rankpeek-server-monitor.env.example"));
        String service = Files.readString(Path.of("deploy/ubuntu/monitoring/rankpeek-server-monitor.service.example"));
        String timer = Files.readString(Path.of("deploy/ubuntu/monitoring/rankpeek-server-monitor.timer.example"));

        assertThat(smokeScript)
                .contains("RANKPEEK_SMOKE_EXPECTED_FLYWAY_VERSION:-9");
        assertThat(monitorScript)
                .contains("/api/server/health")
                .contains("/api/server/diagnostics")
                .contains("systemctl is-active --quiet")
                .contains("RANKPEEK_MONITOR_MAX_BACKUP_AGE_HOURS")
                .contains("RANKPEEK_MONITOR_WEBHOOK_URL")
                .contains("send_alert")
                .contains(".data.refreshToken")
                .contains("/api/auth/logout")
                .contains("X-Request-Id: rankpeek-monitor-health");
        assertThat(monitorEnv)
                .contains("RANKPEEK_MONITOR_BASE_URL=http://127.0.0.1:18080")
                .contains("RANKPEEK_MONITOR_EXPECTED_FLYWAY_VERSION=9")
                .contains("RANKPEEK_MONITOR_MAX_BACKUP_AGE_HOURS=30")
                .contains("RANKPEEK_MONITOR_WEBHOOK_URL=");
        assertThat(service)
                .contains("EnvironmentFile=/etc/rankpeek/rankpeek-server-monitor.env")
                .contains("ExecStart=/usr/local/sbin/rankpeek-server-monitor.sh");
        assertThat(timer)
                .contains("OnUnitActiveSec=5m")
                .contains("Persistent=true");
    }

    @Test
    void ubuntuAiSmokeScriptExercisesCreditsCoachSummaryAndIdempotency() throws Exception {
        String aiSmokeScript = Files.readString(Path.of("deploy/ubuntu/rankpeek-server-ai-smoke.sh"));
        String deploymentGuide = Files.readString(Path.of("../docs/rankpeek-server-ubuntu-deployment.md"));
        String readme = Files.readString(Path.of("README.md"));

        assertThat(aiSmokeScript)
                .contains("RANKPEEK_AI_SMOKE_ADMIN_EMAIL")
                .contains("RANKPEEK_AI_SMOKE_USER_EMAIL")
                .contains("RANKPEEK_AI_SMOKE_EXPECTED_CHARGE")
                .contains("/api/admin/credits/grants")
                .contains("/api/credits/balance")
                .contains("/api/credits/ledger")
                .contains("/api/analysis/coach-summary")
                .contains("/api/analysis/runs")
                .contains("X-RankPeek-Idempotency-Key")
                .contains("jq -S '.data.report'")
                .contains("/api/auth/logout");
        assertThat(deploymentGuide)
                .contains("rankpeek-server-ai-smoke.sh")
                .contains("RANKPEEK_AI_SMOKE_ADMIN_EMAIL")
                .contains("RANKPEEK_AI_SMOKE_USER_EMAIL");
        assertThat(readme)
                .contains("rankpeek-server-ai-smoke.sh")
                .contains("credits, DeepSeek, and coach-summary idempotency");
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
