# rankpeek-server Ubuntu Deployment

This guide deploys `rankpeek-server` as a Spring Boot jar on one Ubuntu host with local PostgreSQL and systemd. It does not expose the service directly to the public internet and does not cover Nginx, HTTPS, Docker, or Kubernetes.

## 1. Install Runtime Dependencies

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk postgresql postgresql-contrib jq
java -version
```

The service expects Java 21. PostgreSQL runs on the same Ubuntu host. `jq` is used by the deployment smoke test script.

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
RANKPEEK_PUBLIC_REGISTRATION_ENABLED=false
RANKPEEK_INITIAL_ADMIN_ENABLED=true
RANKPEEK_INITIAL_ADMIN_EMAIL=admin@example.com
RANKPEEK_INITIAL_ADMIN_PASSWORD=CHANGE_ME_INITIAL_ADMIN_PASSWORD
RANKPEEK_INITIAL_ADMIN_DISPLAY_NAME=RankPeek Admin
RANKPEEK_CREDITS_COACH_SUMMARY_CHARGE_CREDITS=1
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

Application-level rate limiting is enabled by default. It applies a fixed window to registration, login, refresh-token, and AI analysis endpoints. Keep it enabled for MVP deployments; tune the auth and AI request counts only after looking at real traffic and support needs. This does not replace reverse-proxy or firewall rate limits.

DeepSeek AI is disabled by default. To test the real provider, set these values in `/etc/rankpeek/rankpeek-server.env`:

```bash
RANKPEEK_AI_ENABLED=true
RANKPEEK_AI_PROVIDER=deepseek
RANKPEEK_AI_BASE_URL=https://api.deepseek.com
RANKPEEK_AI_MODEL=deepseek-v4-flash
RANKPEEK_AI_API_KEY=CHANGE_ME_DEEPSEEK_KEY
```

Do not commit `/etc/rankpeek/rankpeek-server.env` or any real secret.

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

## 7. Verify the Server

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

The migration history should be at version `8`, including `user_credit_balances`, `credit_ledger_entries`, and `ai_analysis_runs`:

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

That public smoke run checks `/api/server/health`, `/api/server/version`, and the `X-Request-Id` response header. To also verify admin diagnostics and Flyway version `8`, pass the initial admin credentials through environment variables:

```bash
RANKPEEK_SMOKE_ADMIN_EMAIL=admin@example.com \
RANKPEEK_SMOKE_ADMIN_PASSWORD='CHANGE_ME_INITIAL_ADMIN_PASSWORD' \
/opt/rankpeek/server/rankpeek-server-smoke.sh
```

## Operational Notes

- Keep `RANKPEEK_SERVER_ADDRESS=127.0.0.1` until Nginx and HTTPS are added.
- Do not expose port `18080` directly to the public internet.
- Keep `RANKPEEK_RATE_LIMIT_ENABLED=true`; add Nginx or firewall rate limits before any public exposure.
- `rankpeek.cn-meta.sync.real-source-enabled` remains `false` in production config.
- `rankpeek.ai.enabled` remains `false` unless DeepSeek is intentionally configured for a real integration test.
- Rotate secrets by editing `/etc/rankpeek/rankpeek-server.env` and running `sudo systemctl restart rankpeek-server`.
