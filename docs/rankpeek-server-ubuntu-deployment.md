# rankpeek-server Ubuntu Deployment

This guide deploys `rankpeek-server` as a Spring Boot jar on one Ubuntu host with local PostgreSQL, systemd, and an Nginx HTTPS reverse proxy. It does not cover Docker, Kubernetes, managed load balancers, or multi-host deployments. Use `docs/rankpeek-server-production-launch-checklist.md` as the final go-live gate while executing this guide.

## 1. Install Runtime Dependencies

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk postgresql postgresql-contrib nginx certbot python3-certbot-nginx jq ufw
java -version
```

The service expects Java 21. PostgreSQL runs on the same Ubuntu host. Nginx terminates public HTTP/HTTPS traffic and forwards to the local-only Spring Boot port. `jq` is used by the deployment smoke test script.

## 2. Create the Database

Generate a database password first:

```bash
openssl rand -hex 24
```

Create the PostgreSQL role and database. Replace `CHANGE_ME_DATABASE_PASSWORD` with the generated password.

```bash
sudo -u postgres psql
```

```sql
create role rankpeek with login password 'CHANGE_ME_DATABASE_PASSWORD';
create database rankpeek_server owner rankpeek;
\q
```

Flyway creates the application tables when the server starts.

## 3. Create the Service User and Directories

```bash
sudo useradd --system --home /opt/rankpeek --shell /usr/sbin/nologin rankpeek
sudo mkdir -p /opt/rankpeek/server /etc/rankpeek
sudo chown -R rankpeek:rankpeek /opt/rankpeek
sudo chmod 750 /opt/rankpeek /opt/rankpeek/server
```

## 4. Build and Install the Jar

From the repository root on your build machine:

```bash
cd rankpeek-server
mvn test
mvn -DskipTests package
```

Copy the built jar to the Ubuntu host. Replace `ubuntu-host` with the server address:

```bash
scp target/rankpeek-server-0.1.0.jar ubuntu-host:/tmp/rankpeek-server.jar
```

On the Ubuntu host, install the jar:

```bash
sudo cp /tmp/rankpeek-server.jar /opt/rankpeek/server/rankpeek-server.jar
sudo chown rankpeek:rankpeek /opt/rankpeek/server/rankpeek-server.jar
sudo chmod 640 /opt/rankpeek/server/rankpeek-server.jar
```

## 5. Configure Environment Variables

Copy the example file to the Ubuntu host. From the repository root on your build machine:

```bash
scp rankpeek-server/deploy/ubuntu/rankpeek-server.env.example ubuntu-host:/tmp/rankpeek-server.env
```

On the Ubuntu host, install it and restrict permissions:

```bash
sudo cp /tmp/rankpeek-server.env /etc/rankpeek/rankpeek-server.env
sudo chown root:rankpeek /etc/rankpeek/rankpeek-server.env
sudo chmod 640 /etc/rankpeek/rankpeek-server.env
```

Edit `/etc/rankpeek/rankpeek-server.env`:

```bash
sudo nano /etc/rankpeek/rankpeek-server.env
```

Required values:

```bash
SPRING_PROFILES_ACTIVE=prod
RANKPEEK_SERVER_ADDRESS=127.0.0.1
RANKPEEK_SERVER_PORT=18080
RANKPEEK_SERVER_DB_URL=jdbc:postgresql://127.0.0.1:5432/rankpeek_server
RANKPEEK_SERVER_DB_USERNAME=rankpeek
RANKPEEK_SERVER_DB_PASSWORD=CHANGE_ME_DATABASE_PASSWORD
RANKPEEK_CORS_ALLOWED_ORIGINS=http://localhost:5173
RANKPEEK_AUTH_ACCESS_TOKEN_SECRET=CHANGE_ME_GENERATE_WITH_OPENSSL_RAND_HEX_32
RANKPEEK_AUTH_PASSWORD_RESET_TOKEN_TTL_SECONDS=900
RANKPEEK_PASSWORD_RESET_EMAIL_ENABLED=false
RANKPEEK_PASSWORD_RESET_EMAIL_FROM=no-reply@example.com
RANKPEEK_PASSWORD_RESET_URL_BASE=https://rankpeek.example.com/password-reset
RANKPEEK_PASSWORD_RESET_EMAIL_SUBJECT="RankPeek password reset"
RANKPEEK_PUBLIC_REGISTRATION_ENABLED=false
RANKPEEK_INITIAL_ADMIN_ENABLED=true
RANKPEEK_INITIAL_ADMIN_EMAIL=admin@example.com
RANKPEEK_INITIAL_ADMIN_PASSWORD=CHANGE_ME_INITIAL_ADMIN_PASSWORD
RANKPEEK_INITIAL_ADMIN_DISPLAY_NAME="RankPeek Admin"
RANKPEEK_CREDITS_COACH_SUMMARY_CHARGE_CREDITS=1
RANKPEEK_CREDITS_AI_STREAM_CHARGE_CREDITS=1
RANKPEEK_RATE_LIMIT_ENABLED=true
RANKPEEK_RATE_LIMIT_WINDOW_SECONDS=60
RANKPEEK_RATE_LIMIT_AUTH_MAX_REQUESTS=20
RANKPEEK_RATE_LIMIT_AI_MAX_REQUESTS=10
```

Generate the JWT secret with:

```bash
openssl rand -hex 32
```

Keep public registration disabled for internal MVP deployments unless you intentionally want open signup. If the production renderer or reverse proxy is not `http://localhost:5173`, set `RANKPEEK_CORS_ALLOWED_ORIGINS` to the exact trusted origin list.

Application-level rate limiting is enabled by default. It applies a fixed window to registration, login, refresh-token, password reset, and AI analysis endpoints. Keep it enabled for MVP deployments; tune the auth and AI request counts only after looking at real traffic and support needs. This does not replace reverse-proxy or firewall rate limits.

Password reset tokens are stored only as SHA-256 hashes and expire after `RANKPEEK_AUTH_PASSWORD_RESET_TOKEN_TTL_SECONDS`. Email delivery is disabled by default. To expose password reset to real users, set `RANKPEEK_PASSWORD_RESET_EMAIL_ENABLED=true`, configure the sender address and reset URL base, and provide SMTP settings through Spring Mail environment variables:

```bash
RANKPEEK_PASSWORD_RESET_EMAIL_ENABLED=true
RANKPEEK_PASSWORD_RESET_EMAIL_FROM=no-reply@example.com
RANKPEEK_PASSWORD_RESET_URL_BASE=https://rankpeek.example.com/password-reset
RANKPEEK_PASSWORD_RESET_EMAIL_SUBJECT="RankPeek password reset"
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=no-reply@example.com
SPRING_MAIL_PASSWORD=CHANGE_ME_SMTP_PASSWORD
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

When password reset email is enabled, startup fails if `RANKPEEK_PASSWORD_RESET_EMAIL_FROM` or `RANKPEEK_PASSWORD_RESET_URL_BASE` is blank. The reset token itself is only sent by email and is not logged or returned by the API.

DeepSeek AI is disabled by default. To test the real provider, set these values in `/etc/rankpeek/rankpeek-server.env`:

```bash
RANKPEEK_AI_ENABLED=true
RANKPEEK_AI_PROVIDER=deepseek
RANKPEEK_AI_BASE_URL=https://api.deepseek.com
RANKPEEK_AI_MODEL=deepseek-v4-flash
RANKPEEK_AI_API_KEY=CHANGE_ME_DEEPSEEK_KEY
RANKPEEK_CREDITS_AI_STREAM_CHARGE_CREDITS=1
```

When DeepSeek is enabled, `POST /api/analysis/pregame/stream`, `POST /api/analysis/postgame/stream`, and `POST /api/analysis/coach-summary` all require a user bearer token and enough credits before the provider is contacted. Stream calls write AI run metadata and token usage, and refund the stream charge if the upstream request fails.

Do not commit `/etc/rankpeek/rankpeek-server.env` or any real secret.

Run the production preflight before the first service start. Copy the script to the Ubuntu host:

```bash
scp rankpeek-server/deploy/ubuntu/rankpeek-server-preflight.sh ubuntu-host:/tmp/rankpeek-server-preflight.sh
```

Install and run it against `/etc/rankpeek/rankpeek-server.env`:

```bash
sudo cp /tmp/rankpeek-server-preflight.sh /opt/rankpeek/server/rankpeek-server-preflight.sh
sudo chown root:rankpeek /opt/rankpeek/server/rankpeek-server-preflight.sh
sudo chmod 750 /opt/rankpeek/server/rankpeek-server-preflight.sh
sudo /opt/rankpeek/server/rankpeek-server-preflight.sh /etc/rankpeek/rankpeek-server.env
```

The preflight rejects placeholder secrets, missing required production values, wildcard CORS, disabled rate limiting, and open public registration for the internal MVP deployment shape. When password reset email, DeepSeek, or initial-admin bootstrap are enabled, it also verifies the dependent variables before systemd starts the service.

## 6. Install and Start the systemd Service

Copy the service file to the Ubuntu host. From the repository root on your build machine:

```bash
scp rankpeek-server/deploy/ubuntu/rankpeek-server.service ubuntu-host:/tmp/rankpeek-server.service
```

On the Ubuntu host, install and start the service:

```bash
sudo cp /tmp/rankpeek-server.service /etc/systemd/system/rankpeek-server.service
sudo systemctl daemon-reload
sudo systemctl enable --now rankpeek-server
```

Check service status and logs:

```bash
sudo systemctl status rankpeek-server
journalctl -u rankpeek-server -n 100 --no-pager
```

Every `/api/**` response includes `X-Request-Id`. Include this value when checking logs; the service writes `api_request` lines with method, path, status, duration, and request id.

## 7. Configure Nginx and HTTPS

Keep the Spring Boot service bound to `127.0.0.1:18080`. Nginx is the public entry point.

Copy the Nginx templates to the Ubuntu host. From the repository root on your build machine:

```bash
scp rankpeek-server/deploy/ubuntu/nginx/rankpeek-server.conf.example ubuntu-host:/tmp/rankpeek-server.conf
scp rankpeek-server/deploy/ubuntu/nginx/rankpeek-proxy-headers.conf.example ubuntu-host:/tmp/rankpeek-proxy-headers.conf
```

On the Ubuntu host, install the shared proxy header snippet:

```bash
sudo cp /tmp/rankpeek-proxy-headers.conf /etc/nginx/snippets/rankpeek-proxy-headers.conf
sudo chown root:root /etc/nginx/snippets/rankpeek-proxy-headers.conf
sudo chmod 644 /etc/nginx/snippets/rankpeek-proxy-headers.conf
```

Install the site config and replace `api.rankpeek.example.com` with the real API host:

```bash
sudo cp /tmp/rankpeek-server.conf /etc/nginx/sites-available/rankpeek-server.conf
sudo nano /etc/nginx/sites-available/rankpeek-server.conf
sudo ln -s /etc/nginx/sites-available/rankpeek-server.conf /etc/nginx/sites-enabled/rankpeek-server.conf
sudo nginx -t
```

Before requesting a certificate, make sure the DNS `A` record points to this host and port `80` is reachable. Then request and install the certificate:

```bash
sudo certbot --nginx -d api.rankpeek.example.com
sudo nginx -t
sudo systemctl reload nginx
```

The provided Nginx snippet sends `X-Forwarded-For` as `$remote_addr`, not `$proxy_add_x_forwarded_for`. This is intentional: the application rate limiter reads `X-Forwarded-For`, so Nginx must strip any client-supplied forwarded chain before proxying. If a trusted outer load balancer or CDN sits in front of Nginx, configure Nginx `real_ip` settings for that trusted proxy first, then still pass only the normalized client address to the app.

Enable a minimal firewall after confirming SSH access:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
sudo ufw status verbose
```

Do not open port `18080`; it should remain reachable only from localhost.

## 8. Verify the Server

The service binds to localhost by default:

```bash
curl http://127.0.0.1:18080/api/server/health
```

Expected response:

```json
{"success":true,"data":{"status":"ok","service":"rankpeek-server","mode":"prod","version":"0.1.0"},"error":null}
```

Confirm Flyway created tables:

```bash
sudo -u postgres psql -d rankpeek_server -c "\dt"
```

The migration history should be at version `9`, including `user_credit_balances`, `credit_ledger_entries`, `ai_analysis_runs`, and `auth_password_reset_tokens`:

```bash
sudo -u postgres psql -d rankpeek_server -c "select version, description, success from flyway_schema_history order by installed_rank desc limit 1;"
```

Copy the smoke test script to the Ubuntu host. From the repository root on your build machine:

```bash
scp rankpeek-server/deploy/ubuntu/rankpeek-server-smoke.sh ubuntu-host:/tmp/rankpeek-server-smoke.sh
```

On the Ubuntu host, install and run it:

```bash
sudo cp /tmp/rankpeek-server-smoke.sh /opt/rankpeek/server/rankpeek-server-smoke.sh
sudo chown root:rankpeek /opt/rankpeek/server/rankpeek-server-smoke.sh
sudo chmod 750 /opt/rankpeek/server/rankpeek-server-smoke.sh
/opt/rankpeek/server/rankpeek-server-smoke.sh
```

That public smoke run checks `/api/server/health`, `/api/server/version`, and the `X-Request-Id` response header. To also verify admin diagnostics and Flyway version `9`, pass the initial admin credentials through environment variables:

```bash
RANKPEEK_SMOKE_ADMIN_EMAIL=admin@example.com \
RANKPEEK_SMOKE_ADMIN_PASSWORD='CHANGE_ME_INITIAL_ADMIN_PASSWORD' \
/opt/rankpeek/server/rankpeek-server-smoke.sh
```

For a stricter production preflight, also assert the deployment-facing configuration reported by admin diagnostics:

```bash
RANKPEEK_SMOKE_ADMIN_EMAIL=admin@example.com \
RANKPEEK_SMOKE_ADMIN_PASSWORD='CHANGE_ME_INITIAL_ADMIN_PASSWORD' \
RANKPEEK_SMOKE_EXPECT_MODE=prod \
RANKPEEK_SMOKE_EXPECT_PUBLIC_REGISTRATION_ENABLED=false \
RANKPEEK_SMOKE_EXPECT_PASSWORD_RESET_EMAIL_ENABLED=true \
RANKPEEK_SMOKE_EXPECT_AI_ENABLED=true \
RANKPEEK_SMOKE_EXPECT_RATE_LIMIT_ENABLED=true \
/opt/rankpeek/server/rankpeek-server-smoke.sh
```

Set `RANKPEEK_SMOKE_EXPECT_PASSWORD_RESET_EMAIL_ENABLED=false` or `RANKPEEK_SMOKE_EXPECT_AI_ENABLED=false` when those capabilities are intentionally disabled for an internal MVP. Leave an expectation unset only when you deliberately do not want the smoke script to gate on that switch.

After Nginx and HTTPS are enabled, run the same smoke script through the public API URL:

```bash
RANKPEEK_SMOKE_BASE_URL=https://api.rankpeek.example.com \
RANKPEEK_SMOKE_ADMIN_EMAIL=admin@example.com \
RANKPEEK_SMOKE_ADMIN_PASSWORD='CHANGE_ME_INITIAL_ADMIN_PASSWORD' \
/opt/rankpeek/server/rankpeek-server-smoke.sh
```

### Verify Real AI, Credits, and Idempotency

Run this smoke only after DeepSeek is intentionally enabled in `/etc/rankpeek/rankpeek-server.env`, the service has been restarted, and a dedicated smoke user already exists. This check makes a real `coach-summary` request and consumes provider quota plus the configured AI credit charge.

Copy the AI smoke script to the Ubuntu host. From the repository root on your build machine:

```bash
scp rankpeek-server/deploy/ubuntu/rankpeek-server-ai-smoke.sh ubuntu-host:/tmp/rankpeek-server-ai-smoke.sh
```

On the Ubuntu host, install it:

```bash
sudo cp /tmp/rankpeek-server-ai-smoke.sh /opt/rankpeek/server/rankpeek-server-ai-smoke.sh
sudo chown root:rankpeek /opt/rankpeek/server/rankpeek-server-ai-smoke.sh
sudo chmod 750 /opt/rankpeek/server/rankpeek-server-ai-smoke.sh
```

Run it against the local service first:

```bash
RANKPEEK_AI_SMOKE_ADMIN_EMAIL=admin@example.com \
RANKPEEK_AI_SMOKE_ADMIN_PASSWORD='CHANGE_ME_INITIAL_ADMIN_PASSWORD' \
RANKPEEK_AI_SMOKE_USER_EMAIL=smoke-user@example.com \
RANKPEEK_AI_SMOKE_USER_PASSWORD='CHANGE_ME_SMOKE_USER_PASSWORD' \
/opt/rankpeek/server/rankpeek-server-ai-smoke.sh
```

The script logs in as the admin and smoke user, grants smoke credits with `X-RankPeek-Idempotency-Key`, calls `POST /api/analysis/coach-summary`, verifies one credit was charged, repeats the same request with the same idempotency key, verifies the balance did not change, and checks that the AI run can be queried through `GET /api/analysis/runs`.

After Nginx and HTTPS are enabled, run the same check through the public API URL:

```bash
RANKPEEK_AI_SMOKE_BASE_URL=https://api.rankpeek.example.com \
RANKPEEK_AI_SMOKE_ADMIN_EMAIL=admin@example.com \
RANKPEEK_AI_SMOKE_ADMIN_PASSWORD='CHANGE_ME_INITIAL_ADMIN_PASSWORD' \
RANKPEEK_AI_SMOKE_USER_EMAIL=smoke-user@example.com \
RANKPEEK_AI_SMOKE_USER_PASSWORD='CHANGE_ME_SMOKE_USER_PASSWORD' \
/opt/rankpeek/server/rankpeek-server-ai-smoke.sh
```

If the smoke user already has enough credits and you do not want the script to create an admin grant, add `RANKPEEK_AI_SMOKE_SKIP_GRANT=true`. Keep this smoke user separate from real users because the script intentionally writes credit ledger entries and AI run records.

## 9. Configure PostgreSQL Backups

Backups are required before any real user data or credit ledger data is trusted to this host. The provided templates create daily custom-format PostgreSQL dumps, checksum each dump, delete old dumps by retention, and include a restore drill script that restores into a separate disposable database.

Copy the backup templates to the Ubuntu host. From the repository root on your build machine:

```bash
scp rankpeek-server/deploy/ubuntu/postgres/rankpeek-postgres-backup.sh.example ubuntu-host:/tmp/rankpeek-postgres-backup.sh
scp rankpeek-server/deploy/ubuntu/postgres/rankpeek-postgres-restore-drill.sh.example ubuntu-host:/tmp/rankpeek-postgres-restore-drill.sh
scp rankpeek-server/deploy/ubuntu/postgres/rankpeek-postgres-backup.service.example ubuntu-host:/tmp/rankpeek-postgres-backup.service
scp rankpeek-server/deploy/ubuntu/postgres/rankpeek-postgres-backup.timer.example ubuntu-host:/tmp/rankpeek-postgres-backup.timer
```

Install the scripts and backup directory:

```bash
sudo install -o postgres -g postgres -m 750 -d /var/backups/rankpeek/postgres
sudo cp /tmp/rankpeek-postgres-backup.sh /usr/local/sbin/rankpeek-postgres-backup.sh
sudo cp /tmp/rankpeek-postgres-restore-drill.sh /usr/local/sbin/rankpeek-postgres-restore-drill.sh
sudo chown root:postgres /usr/local/sbin/rankpeek-postgres-backup.sh /usr/local/sbin/rankpeek-postgres-restore-drill.sh
sudo chmod 750 /usr/local/sbin/rankpeek-postgres-backup.sh /usr/local/sbin/rankpeek-postgres-restore-drill.sh
```

Install and enable the systemd timer:

```bash
sudo cp /tmp/rankpeek-postgres-backup.service /etc/systemd/system/rankpeek-postgres-backup.service
sudo cp /tmp/rankpeek-postgres-backup.timer /etc/systemd/system/rankpeek-postgres-backup.timer
sudo systemctl daemon-reload
sudo systemctl enable --now rankpeek-postgres-backup.timer
systemctl list-timers rankpeek-postgres-backup.timer
```

Run one manual backup and inspect the output:

```bash
sudo systemctl start rankpeek-postgres-backup.service
journalctl -u rankpeek-postgres-backup.service -n 100 --no-pager
sudo -u postgres ls -lh /var/backups/rankpeek/postgres
```

Run a restore drill against the newest dump. This restores into `rankpeek_restore_drill`, checks that Flyway history exists, and drops the drill database when it exits:

```bash
LATEST_BACKUP="$(sudo -u postgres find /var/backups/rankpeek/postgres -type f -name 'rankpeek_server-*.dump' | sort | tail -n 1)"
sudo -u postgres /usr/local/sbin/rankpeek-postgres-restore-drill.sh "$LATEST_BACKUP"
```

Only treat backups as working after the restore drill succeeds. To inspect the restored database manually, run the drill with `RANKPEEK_RESTORE_KEEP_DRILL_DB=true` and drop `rankpeek_restore_drill` yourself afterward.

## 10. Configure Monitoring Checks

The minimal production monitor runs every five minutes from systemd. It checks:

- `rankpeek-server`, `postgresql`, and `nginx` are active;
- `/api/server/health` returns `success=true` and `status=ok`;
- optional admin diagnostics report database and Flyway status `ok`;
- the newest PostgreSQL backup exists, has a matching checksum, and is no older than the configured threshold;
- an optional webhook receives a JSON failure alert when any check fails.

Copy the monitoring templates to the Ubuntu host. From the repository root on your build machine:

```bash
scp rankpeek-server/deploy/ubuntu/monitoring/rankpeek-server-monitor.sh.example ubuntu-host:/tmp/rankpeek-server-monitor.sh
scp rankpeek-server/deploy/ubuntu/monitoring/rankpeek-server-monitor.env.example ubuntu-host:/tmp/rankpeek-server-monitor.env
scp rankpeek-server/deploy/ubuntu/monitoring/rankpeek-server-monitor.service.example ubuntu-host:/tmp/rankpeek-server-monitor.service
scp rankpeek-server/deploy/ubuntu/monitoring/rankpeek-server-monitor.timer.example ubuntu-host:/tmp/rankpeek-server-monitor.timer
```

Install the script and environment file:

```bash
sudo cp /tmp/rankpeek-server-monitor.sh /usr/local/sbin/rankpeek-server-monitor.sh
sudo chown root:root /usr/local/sbin/rankpeek-server-monitor.sh
sudo chmod 750 /usr/local/sbin/rankpeek-server-monitor.sh
sudo cp /tmp/rankpeek-server-monitor.env /etc/rankpeek/rankpeek-server-monitor.env
sudo chown root:rankpeek /etc/rankpeek/rankpeek-server-monitor.env
sudo chmod 640 /etc/rankpeek/rankpeek-server-monitor.env
```

Edit `/etc/rankpeek/rankpeek-server-monitor.env`:

```bash
sudo nano /etc/rankpeek/rankpeek-server-monitor.env
```

Useful values:

```bash
RANKPEEK_MONITOR_BASE_URL=http://127.0.0.1:18080
RANKPEEK_MONITOR_EXPECTED_FLYWAY_VERSION=9
RANKPEEK_MONITOR_SERVICES="rankpeek-server postgresql nginx"
RANKPEEK_MONITOR_BACKUP_DIR=/var/backups/rankpeek/postgres
RANKPEEK_MONITOR_REQUIRE_BACKUP=true
RANKPEEK_MONITOR_MAX_BACKUP_AGE_HOURS=30
RANKPEEK_MONITOR_ADMIN_EMAIL=admin@example.com
RANKPEEK_MONITOR_ADMIN_PASSWORD=CHANGE_ME_INITIAL_ADMIN_PASSWORD
RANKPEEK_MONITOR_WEBHOOK_URL=
```

`RANKPEEK_MONITOR_WEBHOOK_URL` is optional. If set, failed checks POST a small JSON payload containing service, status, host, and message. Keep this URL secret.

Install and enable the timer:

```bash
sudo cp /tmp/rankpeek-server-monitor.service /etc/systemd/system/rankpeek-server-monitor.service
sudo cp /tmp/rankpeek-server-monitor.timer /etc/systemd/system/rankpeek-server-monitor.timer
sudo systemctl daemon-reload
sudo systemctl enable --now rankpeek-server-monitor.timer
systemctl list-timers rankpeek-server-monitor.timer
```

Run one manual monitor check:

```bash
sudo systemctl start rankpeek-server-monitor.service
journalctl -u rankpeek-server-monitor.service -n 100 --no-pager
```

If the monitor fails before the first scheduled backup exists, either run `rankpeek-postgres-backup.service` once or temporarily set `RANKPEEK_MONITOR_REQUIRE_BACKUP=false` until backups are installed and verified.

## Operational Notes

- Keep `RANKPEEK_SERVER_ADDRESS=127.0.0.1`; public traffic should enter through Nginx.
- Do not expose port `18080` directly to the public internet.
- Keep `RANKPEEK_RATE_LIMIT_ENABLED=true`; add Nginx or firewall rate limits before any public exposure.
- Keep PostgreSQL backup retention at `14` days or longer until real storage costs are known; verify restore drills after schema migrations.
- Keep `rankpeek-server-monitor.timer` enabled after backups are installed; treat repeated monitor failures as production incidents.
- `rankpeek.cn-meta.sync.real-source-enabled` remains `false` in production config.
- `rankpeek.ai.enabled` remains `false` unless DeepSeek is intentionally configured for a real integration test.
- Rotate secrets by editing `/etc/rankpeek/rankpeek-server.env` and running `sudo systemctl restart rankpeek-server`.
