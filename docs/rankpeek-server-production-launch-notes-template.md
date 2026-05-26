# rankpeek-server Production Launch Notes

Copy this file for each production launch and fill it while running
`docs/rankpeek-server-production-launch-checklist.md`. Do not store real secrets,
passwords, API keys, private SMTP credentials, or full `.env` contents in launch notes.

## Launch Summary

Launch date:
Launch operator:
Reviewer:
Repository commit:
PRs included:
API domain:
Renderer origin:
Launch decision: proceed / hold
Accepted limitations:

## Gate 0: External Inputs

Host:
SSH verified:
DNS A record:
Production API origin:
Production renderer origin:
AI enabled:
Password reset email enabled:
Public registration enabled:
Initial admin bootstrap:
Existing admin confirmed:
DeepSeek key available if AI enabled:
SMTP settings available if email enabled:
Decision owner:

## Gate 1: Build Artifact

CI run URL:
Artifact name:
Artifact source commit:
`mvn test` result:
Jar path:
Checksum file path:
Jar SHA-256:
Checksum verification result before host transfer:
Checksum verification result on host:

## Gate 2: Server Bootstrap

Java version:
PostgreSQL role verified:
Database ownership verified:
Service user verified:
Installed jar ownership/mode:
Env file ownership/mode:
Port `18080` exposure check:

## Gate 3: Production Env Preflight

Preflight command:
Preflight result:
`SPRING_PROFILES_ACTIVE`:
`RANKPEEK_SERVER_ADDRESS`:
`RANKPEEK_PUBLIC_REGISTRATION_ENABLED`:
`RANKPEEK_RATE_LIMIT_ENABLED`:
Trusted CORS origins:
Env file permission result:
Placeholder scan result:

## Gate 4: Local Service Smoke

Service status:
Recent journal summary:
Local health result:
Admin diagnostics result:
Flyway version:
Config flag verification:

## Gate 5: Public HTTPS Smoke

Nginx config test result:
Certificate status:
Public smoke result:
`X-Request-Id` observed:
Direct public port `18080` check:
Firewall status:

## Gate 6: AI Smoke

AI smoke skipped:
Skip decision owner:
AI smoke result:
Admin grant result:
Coach summary result:
Idempotency replay result:
Balance delta:
AI run query result:

## Gate 7: Backup And Restore Drill

Backup service result:
Backup dump path:
Backup checksum path:
Checksum verification result:
Restore drill result:
Backup timer status:

## Gate 8: Monitoring Timer

Monitor service result:
Monitor timer status:
Health check result:
Diagnostics check result:
Backup freshness check result:
Alert webhook configured:
Temporary no-webhook decision:

## Gate 9: Rollback Readiness

Previous jar path:
Current jar path:
Current jar SHA-256:
Secured env backup path:
Named rollback operator:
Rollback command reviewed:
Latest smoke result attached:

```bash
sudo cp /opt/rankpeek/server/rankpeek-server.jar.previous /opt/rankpeek/server/rankpeek-server.jar
sudo chown rankpeek:rankpeek /opt/rankpeek/server/rankpeek-server.jar
sudo chmod 640 /opt/rankpeek/server/rankpeek-server.jar
sudo systemctl restart rankpeek-server
/opt/rankpeek/server/rankpeek-server-smoke.sh
```

## Final Decision

Launch decision:
Decision timestamp:
Approver:
Post-launch watch owner:
Next review time:
Residual risk:
