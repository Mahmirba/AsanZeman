# Deployment and operations runbook

For the runtime decided in D55: the executable jar on VMs, behind the bank's reverse proxy, against one Oracle database.
Items marked **To agree** are proposals that need the ops team's or the DBA's answer.

## 1. What runs where

```
panels ──► reverse proxy ──► VM 1: java -jar asanzeman.jar  ─┐
 (HTTPS)    (TLS 1.2+, HSTS)  VM 2: java -jar asanzeman.jar  ─┼──► Oracle (AZS_* schemas)
                              (port 8080, API)                │      └─► bank services: Yaghut, AAA, Notifier, ARcaptcha, API gateway
monitoring ───────────────────► port 8081 on each VM ─────────┘
```

* The application keeps **no state on the VM**: sessions, locks, the SMS outbox and job locks are all in Oracle. A VM can be
  rebuilt from the jar and its configuration file.
* **Two or more VMs** may run at once. Scheduled jobs run on one of them at a time (ShedLock, D41), so no VM is "the primary".
* Every VM needs the same **JWT key pair** (`azs.security.jwt.*-key-pem`): a token issued by one must be accepted by the others.
* Every VM needs correct time (NTP). Token expiry uses the VM's clock; job locks use the database's.

## 2. A server

| What | Where |
|---|---|
| The jar | `/opt/asanzeman/asanzeman.jar`: the `backend/target/asanzeman-<version>.jar` that `./mvnw verify` builds, installed under this name |
| Its configuration | `/etc/asanzeman/application.properties`, owner `asanzeman`, mode `600`. Template: `deploy/vm/application.server.example.properties` |
| Java options | `/etc/asanzeman/java.env`, optional (`JAVA_OPTS=-Xmx...`) |
| The service | `deploy/vm/asanzeman.service` (systemd; **To agree:** the servers' operating system) |
| Logs | standard output, one JSON line each (ECS format): the journal, or whatever collects it |

Needs Java **26** with `--enable-preview` (D9: not an LTS release, so ops must be able to install and patch it).
Network: Oracle; Yaghut, AAA, Notifier, ARcaptcha and the API gateway (the URLs in the configuration file).
Ports: **8080** to the proxy only, **8081** to the monitoring system and the proxy's health probe only (`docs/ops-settings.md`).

## 3. First installation

1. DBA: create the schemas and accounts, `db/dba/01_create_schemas.sql`; then follow section 4 for the tables.
2. Generate the JWT key pair once and copy it to every VM:
   `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem` and
   `openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem`. Put both PEM texts in the configuration file.
3. Fill in the configuration file. A value left as `CHANGE_ME` stops the application from starting and names the key.
4. Install the jar and the unit, start the service, and check `http://localhost:8081/actuator/health` answers `UP`.
5. Load the branches: the nightly job does it at 02:00, or at startup when the table is empty (D32).
6. Add the agencies through the admin API (`POST /api/admin/agencies`) and set the rejection reasons.
7. Point the proxy at port 8080 of every VM, with the health probe on 8081, and set `server.tomcat.remoteip.internal-proxies`.

## 4. The database

Two accounts, so that the application cannot change the schema or the audit log (plan 6-5):

* **The migration account** (DBA-owned) has the DDL rights and applies the Flyway scripts. It is never used by the application.
* **`AZS_APP`**, the runtime account, has data rights only: `db/dba/03_runtime_grants.sql` gives it SELECT/INSERT/UPDATE/DELETE on
  the tables and **INSERT only** on `AZS_AUDIT.AUDIT_LOG`, and removes the development account's `*ANY TABLE` rights.
  Checked on the development database: the application ran as that account (logins, audit, tokens, the scheduled jobs) with no
  privilege error, and it cannot drop a table or read, change or delete the audit log.

For every release that has migrations (the scripts are `backend/src/main/resources/db/migration/oracle/V*.sql`, also inside the jar):

1. The DBA reviews the new `V*.sql` files.
2. In the maintenance window, with the Flyway command line and the migration account: `flyway validate`, then `flyway migrate`
   (locations as the files, schemas `AZS_BUSINESS,AZS_AUTH,AZS_INQUIRY,AZS_AUDIT,AZS_OUTBOX`, default schema `AZS_BUSINESS`).
3. Run `db/dba/03_runtime_grants.sql AZS_APP` again, so a new table gets its grants.
4. `flyway info` shows every script as applied.

With `spring.flyway.enabled=false` the application does not check the schema version at startup, so step 4 matters.
**To agree:** the DBA's own change process, and the migration account's name.
Migrations are forward-fix only (D20). A risky one has a hand-written undo script in `db/undo/`, run by the DBA.

## 5. A release

Both versions run against the same database while a release rolls, so a migration must work for the old and the new jar
(add first, remove in a later release).

1. Database steps from section 4, if the release has migrations.
2. For each VM in turn: take it out of the proxy; `systemctl stop asanzeman` (requests in flight finish, up to 30 s: Spring's graceful-shutdown wait);
   replace the jar; `systemctl start asanzeman`; wait for `:8081/actuator/health` to be `UP`; put it back in the proxy.
3. Smoke test through the proxy: log in to each panel, and open the branch inbox.
4. Watch the logs (section 7) for a few minutes.

**Rollback:** the same steps with the previous jar. A migration is not undone by this (forward-fix, D20); that is why it has to
suit both versions.

## 6. Backups and recovery (plan 8-5)

* Oracle is backed up by the DBA: a full backup every night at 02:00 with RMAN, archive logs shipped to a standby.
  **Targets: RTO 2 hours, RPO 15 minutes.** Test a restore every three months.
* The application has nothing to restore. After losing a VM, install the jar and the configuration file on a new one (section 2).
* After a database restore, check the nightly jobs: the outbox holds SMS messages that may have been sent or not; the
  dispatcher resends the pending ones.

## 7. What to watch

Scrape `:8081/actuator/prometheus`. Metrics: `azs_requests{type,status}` (requests per state), `azs_inquiry_run` (UC-09 time;
targets P50 8 s, P95 15 s, P99 25 s), `azs_otp_sent`, `hikaricp_connections_*`, and the HTTP metrics.

Log lines that need a person (they are written at ERROR):

| Log text | Meaning |
|---|---|
| `Refusing to start: ... CHANGE_ME` | a secret is not set in the configuration file |
| `branch integrity:` | the branch table has a cycle, a chain, or a closed branch with no replacement |
| `ARcaptcha configuration error` | the ARcaptcha keys are wrong; logins fail |
| `gateway token request refused` | the API gateway's client id or secret is wrong; the inquiries fail |
| `SMS centre refuses our credentials` | the Notifier account is wrong; SMS messages wait |
| `refused by the SMS centre, dead-lettered` | one SMS was rejected for good (a bad number) |
| `could not be rejected` | the 7-day rule failed for one request; the others went on |

Keep the statistics on `AZS_BUSINESS.REQUEST` fresh (`docs/performance.md`): with stale ones the same load went from a 0.05 s to a 13 s P95 and requests failed.

## 8. Open points

* The servers' operating system, and whether Java 26 can be installed on them.
* The proxy's addresses, and that it overwrites `X-Forwarded-For`.
* The DBA's change process and the migration account.
* Training material for branch users on the manual steps in Farangin (Phase 10).
* The first real run of the plan's exit check: production deployment checklist signed off.
