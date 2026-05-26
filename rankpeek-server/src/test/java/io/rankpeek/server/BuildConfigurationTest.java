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
                .contains(
                        "org.flywaydb:flyway-database-postgresql",
                        "org.springframework.boot:spring-boot-starter-mail"
                );
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
                .contains("RANKPEEK_SMOKE_EXPECTED_FLYWAY_VERSION:-9")
                .contains("RANKPEEK_SMOKE_EXPECT_MODE")
                .contains("RANKPEEK_SMOKE_EXPECT_PUBLIC_REGISTRATION_ENABLED")
                .contains("RANKPEEK_SMOKE_EXPECT_PASSWORD_RESET_EMAIL_ENABLED")
                .contains("RANKPEEK_SMOKE_EXPECT_AI_ENABLED")
                .contains("RANKPEEK_SMOKE_EXPECT_RATE_LIMIT_ENABLED");
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
    void ubuntuProductionEnvPreflightRejectsUnsafeDeploymentConfig() throws Exception {
        Path preflightScriptPath = Path.of("deploy/ubuntu/rankpeek-server-preflight.sh");
        assertThat(preflightScriptPath).exists();

        String preflightScript = Files.readString(preflightScriptPath);
        String gitAttributes = Files.readString(Path.of("../.gitattributes"));
        String deploymentGuide = Files.readString(Path.of("../docs/rankpeek-server-ubuntu-deployment.md"));
        String readme = Files.readString(Path.of("README.md"));

        assertThat(preflightScript)
                .contains("RANKPEEK_PREFLIGHT_ENV_FILE")
                .contains("require_equals SPRING_PROFILES_ACTIVE prod")
                .contains("require_equals RANKPEEK_SERVER_ADDRESS 127.0.0.1")
                .contains("require_present RANKPEEK_SERVER_DB_PASSWORD")
                .contains("reject_placeholder RANKPEEK_SERVER_DB_PASSWORD")
                .contains("require_secret RANKPEEK_AUTH_ACCESS_TOKEN_SECRET")
                .contains("require_bool RANKPEEK_PUBLIC_REGISTRATION_ENABLED false")
                .contains("require_bool RANKPEEK_RATE_LIMIT_ENABLED true")
                .contains("RANKPEEK_PASSWORD_RESET_EMAIL_ENABLED")
                .contains("SPRING_MAIL_PASSWORD")
                .contains("RANKPEEK_AI_ENABLED")
                .contains("RANKPEEK_AI_API_KEY")
                .contains("RANKPEEK_INITIAL_ADMIN_ENABLED")
                .contains("CHANGE_ME_INITIAL_ADMIN_PASSWORD")
                .contains("RANKPEEK_CORS_ALLOWED_ORIGINS")
                .contains("Preflight checks passed");
        assertThat(deploymentGuide)
                .contains("rankpeek-server-preflight.sh")
                .contains("sudo /opt/rankpeek/server/rankpeek-server-preflight.sh /etc/rankpeek/rankpeek-server.env")
                .contains("Run the production preflight before the first service start");
        assertThat(gitAttributes)
                .contains("rankpeek-server/deploy/**/*.env.example text eol=lf");
        assertThat(readme)
                .contains("rankpeek-server-preflight.sh")
                .contains("rejects placeholder secrets")
                .contains("before starting the production service");
    }

    @Test
    void productionLaunchChecklistCoversRequiredGoLiveGates() throws Exception {
        String launchChecklist = Files.readString(Path.of("../docs/rankpeek-server-production-launch-checklist.md"));
        String deploymentGuide = Files.readString(Path.of("../docs/rankpeek-server-ubuntu-deployment.md"));
        String readme = Files.readString(Path.of("README.md"));

        assertThat(launchChecklist)
                .contains("Gate 0: External Inputs")
                .contains("Gate 1: Build Artifact")
                .contains("Gate 2: Server Bootstrap")
                .contains("Gate 3: Production Env Preflight")
                .contains("Gate 4: Local Service Smoke")
                .contains("Gate 5: Public HTTPS Smoke")
                .contains("Gate 6: AI Smoke")
                .contains("Gate 7: Backup And Restore Drill")
                .contains("Gate 8: Monitoring Timer")
                .contains("Gate 9: Rollback Readiness")
                .contains("mvn test")
                .contains("rankpeek-server-jar")
                .contains("sha256sum -c rankpeek-server-0.1.0.jar.sha256")
                .contains("rankpeek-server-preflight.sh /etc/rankpeek/rankpeek-server.env")
                .contains("RANKPEEK_SMOKE_BASE_URL=https://api.rankpeek.example.com")
                .contains("rankpeek-postgres-restore-drill.sh")
                .contains("rankpeek-server-monitor.timer")
                .contains("Do not open port `18080`")
                .contains("Do not mark launch complete until");
        assertThat(deploymentGuide)
                .contains("rankpeek-server-production-launch-checklist.md");
        assertThat(readme)
                .contains("rankpeek-server-production-launch-checklist.md");
    }

    @Test
    void serverCiBuildsAndUploadsDeployableJar() throws Exception {
        String workflow = Files.readString(Path.of("../.github/workflows/rankpeek-server-ci.yml"));

        assertThat(workflow)
                .contains("mvn -B test")
                .contains("mvn -B -DskipTests package")
                .contains("(cd target && sha256sum rankpeek-server-0.1.0.jar > rankpeek-server-0.1.0.jar.sha256)")
                .contains("actions/upload-artifact@v4")
                .contains("name: rankpeek-server-jar")
                .contains("rankpeek-server/target/rankpeek-server-0.1.0.jar")
                .contains("rankpeek-server/target/rankpeek-server-0.1.0.jar.sha256")
                .contains("if-no-files-found: error");
    }

    @Test
    void ubuntuAiSmokeScriptExercisesCreditsCoachSummaryAndIdempotency() throws Exception {
        String aiSmokeScript = Files.readString(Path.of("deploy/ubuntu/rankpeek-server-ai-smoke.sh"));
        String envExample = Files.readString(Path.of("deploy/ubuntu/rankpeek-server.env.example"));
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
        assertThat(envExample)
                .contains("RANKPEEK_CREDITS_AI_STREAM_CHARGE_CREDITS=1");
        assertThat(deploymentGuide)
                .contains("rankpeek-server-ai-smoke.sh")
                .contains("RANKPEEK_AI_SMOKE_ADMIN_EMAIL")
                .contains("RANKPEEK_AI_SMOKE_USER_EMAIL")
                .contains("RANKPEEK_CREDITS_AI_STREAM_CHARGE_CREDITS=1")
                .contains("pregame/stream")
                .contains("postgame/stream");
        assertThat(readme)
                .contains("rankpeek-server-ai-smoke.sh")
                .contains("credits, DeepSeek, and coach-summary idempotency")
                .contains("RANKPEEK_CREDITS_AI_STREAM_CHARGE_CREDITS");
    }

    @Test
    void passwordResetEmailDeploymentConfigIsDocumentedAndDisabledByDefault() throws Exception {
        String envExample = Files.readString(Path.of("deploy/ubuntu/rankpeek-server.env.example"));
        String deploymentGuide = Files.readString(Path.of("../docs/rankpeek-server-ubuntu-deployment.md"));
        String readme = Files.readString(Path.of("README.md"));

        assertThat(envExample)
                .contains("RANKPEEK_PASSWORD_RESET_EMAIL_ENABLED=false")
                .contains("RANKPEEK_PASSWORD_RESET_EMAIL_FROM=no-reply@example.com")
                .contains("RANKPEEK_PASSWORD_RESET_URL_BASE=https://rankpeek.example.com/password-reset")
                .contains("RANKPEEK_PASSWORD_RESET_EMAIL_SUBJECT=\"RankPeek password reset\"")
                .contains("RANKPEEK_INITIAL_ADMIN_DISPLAY_NAME=\"RankPeek Admin\"")
                .contains("# SPRING_MAIL_HOST=smtp.example.com")
                .contains("# SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true");
        assertThat(deploymentGuide)
                .contains("RANKPEEK_PASSWORD_RESET_EMAIL_ENABLED=true")
                .contains("SPRING_MAIL_HOST=smtp.example.com")
                .contains("startup fails if `RANKPEEK_PASSWORD_RESET_EMAIL_FROM`")
                .contains("is not logged or returned by the API");
        assertThat(readme)
                .contains("Password reset email delivery is disabled by default")
                .contains("SPRING_MAIL_HOST")
                .contains("fails startup if the sender address or reset URL base is missing");
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
