# rankpeek-server Production Launch Checklist

Use this checklist as the final go-live gate for the first supported deployment shape:
one Ubuntu host, local PostgreSQL, systemd, Nginx HTTPS, and the Spring Boot jar bound
to `127.0.0.1:18080`.

This is not a replacement for `docs/rankpeek-server-ubuntu-deployment.md`; it is the
operator checklist for proving the deployment is ready. Do not mark launch complete until
every required gate has a recorded pass or a written product decision that accepts the
remaining limitation.
Use `docs/rankpeek-server-production-launch-notes-template.md` to record the evidence
for each gate without storing real secrets.

## Gate 0: External Inputs

- [ ] Ubuntu host is available and SSH access is verified.
- [ ] DNS `A` record for `api.rankpeek.example.com` points at the host.
- [ ] Production API origin and renderer origin are decided.
- [ ] Real PostgreSQL password is generated with `openssl rand -hex 24`.
- [ ] Real `RANKPEEK_AUTH_ACCESS_TOKEN_SECRET` is generated with `openssl rand -hex 32`.
- [ ] Initial admin email and temporary password are available outside the repo.
- [ ] Initial admin bootstrap is enabled, or `RANKPEEK_PREFLIGHT_EXISTING_ADMIN_CONFIRMED=true` is recorded after an existing `ADMIN` login is verified.
- [ ] At least one internal user or smoke user email and temporary password are available for `POST /api/admin/users`.
- [ ] Product decision is recorded for `RANKPEEK_AI_ENABLED=true|false`.
- [ ] Product decision is recorded for `RANKPEEK_PASSWORD_RESET_EMAIL_ENABLED=true|false`.
- [ ] Product decision is recorded for `RANKPEEK_PUBLIC_REGISTRATION_ENABLED=false`.
- [ ] DeepSeek API key is available if AI is enabled.
- [ ] SMTP host, username, password, sender, and reset URL are available if password reset email is enabled.

Evidence to record:

```text
Host:
API domain:
Renderer origin:
AI enabled:
Password reset email enabled:
Public registration enabled:
```

## Gate 1: Build Artifact

For a main-branch launch, prefer the GitHub Actions artifact named `rankpeek-server-jar`.
Download it from the green `RankPeek Server CI` run and verify the checksum before copying
the jar to the Ubuntu host:

```bash
sha256sum -c rankpeek-server-0.1.0.jar.sha256
```

If building locally, run from the repository root on the build machine:

```bash
cd rankpeek-server
mvn test
mvn -DskipTests package
(cd target && sha256sum rankpeek-server-0.1.0.jar > rankpeek-server-0.1.0.jar.sha256)
(cd target && sha256sum -c rankpeek-server-0.1.0.jar.sha256)
```

Required evidence:

- [ ] `mvn test` passes.
- [ ] CI artifact `rankpeek-server-jar` exists for the commit being deployed.
- [ ] Jar exists at `rankpeek-server/target/rankpeek-server-0.1.0.jar`.
- [ ] `rankpeek-server-0.1.0.jar.sha256` exists.
- [ ] `sha256sum -c rankpeek-server-0.1.0.jar.sha256` passes.
- [ ] Jar SHA-256 is recorded in the launch notes.

## Gate 2: Server Bootstrap

Complete the Ubuntu guide through dependency install, PostgreSQL setup, service user creation,
jar copy, env file installation, and Nginx template installation.

Required evidence:

- [ ] `java -version` reports Java 21.
- [ ] PostgreSQL role `rankpeek` exists.
- [ ] Database `rankpeek_server` exists and is owned by `rankpeek`.
- [ ] `/opt/rankpeek/server/rankpeek-server.jar` is owned by `rankpeek:rankpeek`.
- [ ] `/etc/rankpeek/rankpeek-server.env` is owned by `root:rankpeek` and mode `640`.
- [ ] Do not open port `18080`; Spring Boot must stay reachable only from localhost.

## Gate 3: Production Env Preflight

Run before the first service start:

```bash
sudo /opt/rankpeek/server/rankpeek-server-preflight.sh /etc/rankpeek/rankpeek-server.env
```

Required evidence:

- [ ] Output ends with `Preflight checks passed`.
- [ ] `SPRING_PROFILES_ACTIVE=prod`.
- [ ] `RANKPEEK_SERVER_ADDRESS=127.0.0.1`.
- [ ] `RANKPEEK_PUBLIC_REGISTRATION_ENABLED=false`.
- [ ] `RANKPEEK_INITIAL_ADMIN_ENABLED=true`, unless an existing `ADMIN` login was verified and `RANKPEEK_PREFLIGHT_EXISTING_ADMIN_CONFIRMED=true` is set for preflight.
- [ ] `RANKPEEK_RATE_LIMIT_ENABLED=true`.
- [ ] `RANKPEEK_CORS_ALLOWED_ORIGINS` contains only trusted origins and no wildcard.
- [ ] `/etc/rankpeek/rankpeek-server.env` is owned by `root:rankpeek` and mode `640`.
- [ ] No value in `/etc/rankpeek/rankpeek-server.env` contains `CHANGE_ME`.

## Gate 4: Local Service Smoke

Start the service and run local smoke on the Ubuntu host:

```bash
sudo systemctl enable --now rankpeek-server
sudo systemctl status rankpeek-server --no-pager
journalctl -u rankpeek-server -n 100 --no-pager
curl http://127.0.0.1:18080/api/server/health
```

Then run strict diagnostics smoke:

```bash
RANKPEEK_SMOKE_ADMIN_EMAIL=admin@example.com \
RANKPEEK_SMOKE_ADMIN_PASSWORD='<initial-admin-password>' \
RANKPEEK_SMOKE_EXPECT_MODE=prod \
RANKPEEK_SMOKE_EXPECT_PUBLIC_REGISTRATION_ENABLED=false \
RANKPEEK_SMOKE_EXPECT_INITIAL_ADMIN_ENABLED=true \
RANKPEEK_SMOKE_EXPECT_PASSWORD_RESET_EMAIL_ENABLED=false \
RANKPEEK_SMOKE_EXPECT_AI_ENABLED=false \
RANKPEEK_SMOKE_EXPECT_RATE_LIMIT_ENABLED=true \
/opt/rankpeek/server/rankpeek-server-smoke.sh
```

Adjust the initial-admin expectation after disabling bootstrap, and adjust the password reset and AI expectations only when those features are intentionally enabled.

Required evidence:

- [ ] `rankpeek-server` is active.
- [ ] `/api/server/health` returns `status=ok`.
- [ ] `/api/server/diagnostics` reports database `ok`.
- [ ] `/api/server/diagnostics` reports Flyway version `9`.
- [ ] `/api/server/diagnostics` reports the expected `initialAdminEnabled` value for this phase.
- [ ] Diagnostics config flags match the launch decisions.
- [ ] `POST /api/admin/users` creates the first internal user or dedicated smoke user while public registration is disabled.
- [ ] Admin-created internal user login succeeds with the temporary password.

## Gate 5: Public HTTPS Smoke

After DNS, Nginx, and Certbot are complete:

```bash
sudo nginx -t
sudo systemctl reload nginx
RANKPEEK_SMOKE_BASE_URL=https://api.rankpeek.example.com \
RANKPEEK_SMOKE_ADMIN_EMAIL=admin@example.com \
RANKPEEK_SMOKE_ADMIN_PASSWORD='<initial-admin-password>' \
RANKPEEK_SMOKE_EXPECT_MODE=prod \
RANKPEEK_SMOKE_EXPECT_PUBLIC_REGISTRATION_ENABLED=false \
RANKPEEK_SMOKE_EXPECT_INITIAL_ADMIN_ENABLED=false \
RANKPEEK_SMOKE_EXPECT_PASSWORD_RESET_EMAIL_ENABLED=false \
RANKPEEK_SMOKE_EXPECT_AI_ENABLED=false \
RANKPEEK_SMOKE_EXPECT_RATE_LIMIT_ENABLED=true \
/opt/rankpeek/server/rankpeek-server-smoke.sh
```

Required evidence:

- [ ] Public smoke passes through `https://api.rankpeek.example.com`.
- [ ] Response includes `X-Request-Id`.
- [ ] Direct public access to port `18080` is not possible.
- [ ] `ufw status verbose` allows OpenSSH and Nginx only.

## Gate 6: AI Smoke

Skip this gate only when `RANKPEEK_AI_ENABLED=false` is an explicit internal MVP decision.

When AI is enabled:

```bash
RANKPEEK_AI_SMOKE_BASE_URL=https://api.rankpeek.example.com \
RANKPEEK_AI_SMOKE_ADMIN_EMAIL=admin@example.com \
RANKPEEK_AI_SMOKE_ADMIN_PASSWORD='<initial-admin-password>' \
RANKPEEK_AI_SMOKE_USER_EMAIL=smoke-user@example.com \
RANKPEEK_AI_SMOKE_USER_PASSWORD='<smoke-user-password>' \
/opt/rankpeek/server/rankpeek-server-ai-smoke.sh
```

Required evidence:

- [ ] Admin grant succeeds with an idempotency key.
- [ ] AI smoke user was created through `POST /api/admin/users` or was already verified before the run.
- [ ] `POST /api/analysis/coach-summary` succeeds.
- [ ] Repeating the same idempotency key replays the stored result.
- [ ] Balance changes by exactly the configured charge once.
- [ ] `GET /api/analysis/runs` shows the AI run.

## Gate 7: Backup And Restore Drill

Install backup scripts and timer, then run:

```bash
sudo systemctl start rankpeek-postgres-backup.service
journalctl -u rankpeek-postgres-backup.service -n 100 --no-pager
LATEST_BACKUP="$(sudo -u postgres find /var/backups/rankpeek/postgres -type f -name 'rankpeek_server-*.dump' | sort | tail -n 1)"
sudo -u postgres /usr/local/sbin/rankpeek-postgres-restore-drill.sh "$LATEST_BACKUP"
systemctl list-timers rankpeek-postgres-backup.timer
```

Required evidence:

- [ ] At least one backup dump exists.
- [ ] Matching `.sha256` file exists.
- [ ] `rankpeek-postgres-restore-drill.sh` succeeds.
- [ ] `rankpeek-postgres-backup.timer` is enabled and scheduled.

## Gate 8: Monitoring Timer

Install monitor templates and run:

```bash
sudo systemctl enable --now rankpeek-server-monitor.timer
sudo systemctl start rankpeek-server-monitor.service
journalctl -u rankpeek-server-monitor.service -n 100 --no-pager
systemctl list-timers rankpeek-server-monitor.timer
```

Required evidence:

- [ ] `rankpeek-server-monitor.service` exits successfully.
- [ ] `rankpeek-server-monitor.timer` is enabled and scheduled.
- [ ] Monitor checks service health, diagnostics, and backup freshness.
- [ ] Monitor asserts `RANKPEEK_MONITOR_EXPECT_INITIAL_ADMIN_ENABLED=false` after initial-admin bootstrap is disabled.
- [ ] Alert webhook is configured or the temporary no-webhook decision is recorded.

## Gate 9: Rollback Readiness

Before sending real users to the API:

- [ ] Previous jar, current jar, and current jar SHA-256 are recorded.
- [ ] `/etc/rankpeek/rankpeek-server.env` has a secured backup copy.
- [ ] Manual rollback command is written in the launch notes:

```bash
sudo cp /opt/rankpeek/server/rankpeek-server.jar.previous /opt/rankpeek/server/rankpeek-server.jar
sudo chown rankpeek:rankpeek /opt/rankpeek/server/rankpeek-server.jar
sudo chmod 640 /opt/rankpeek/server/rankpeek-server.jar
sudo systemctl restart rankpeek-server
/opt/rankpeek/server/rankpeek-server-smoke.sh
```

Do not mark launch complete until rollback has a named operator and the latest smoke result is attached to the launch notes.
