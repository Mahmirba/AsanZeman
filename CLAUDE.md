# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

AsanZeman (آسان ضمان) is a bank system for issuing *travel-return guarantees* (ضمانتنامه بازگشت از سفر). A travel agency registers a request, the customer completes it (9 inquiries, then collateral from their own deposits), and a branch user reviews, approves, and issues the guarantee manually in Farangin. Three panels: bank (branch + admin, intranet), agency (DMZ), customer (DMZ).

**Scope right now: backend only.** No UI work until the product owner explains it (D31).

## Sources of truth, in order

1. `plan.md` — the implementation plan and the **decision log D1–D50**. When anything below, a skill, or a sample project disagrees with it, `plan.md` wins.
2. `docs/architecture/AsanZeman_Architecture_Document_v1.0.docx` — the architecture document (Persian). Section references like §4-3-4 point here. Diagram sources are in `docs/architecture/diagrams/` and `docs/architecture/sequence/`.
3. SRS-AZS v1.0 — business rules the architecture doesn't spell out (collateral formulas, rejection messages).

Never change a decision silently. If a task needs a different answer than a Dx decision, stop and ask; if the product owner agrees, record a new Dxx row in `plan.md` first.

## Stack (D9, D24, D25)

- Java **26** with `--enable-preview` (compile, Surefire/Failsafe `argLine`, and the `java` command). Java 26 is not LTS; Boot 4.1 supports Java 17–27.
- Spring Boot **4.1.1**, Spring Modulith **2.1.x**, Spring Data JPA / Hibernate 7, Spring Security (first-party RS256 JWT), Spring Web Services + WSS4J (AAA SOAP), Resilience4j, Flyway (`flyway-database-oracle`), ShedLock (JDBC provider on `scheduler_lock`), Lombok, springdoc-openapi, Micrometer + OpenTelemetry, log4j2 with JSON/ECS layout.
- Oracle 19c+ is the **only** store. No Redis, Kafka, object storage, or second datasource (D41).
- `spring.threads.virtual.enabled=true`. `StructuredTaskScope` is used **only in `StageRunner`** (inquiry module). No bulkhead, semaphore, or rate limiter (D23).
- Build: Maven. Do not add a `settings.xml` mirror; the SettleIt mirror (`nexus.rahkar.team`) does not apply to this project. Dependencies come from Maven Central or whatever mirror the bank provides later. Maven Central is not reachable from the Claude cloud workspace, so code written there is compiled on the developer machine or CI.

## Layout

Single Maven module. Package root `com.sb24.asanzeman`. Spring Modulith modules, one package each:

```
auth/ request/ inquiry/ notification/ report/ scheduler/ integration/ shared/
```

Inside a module, use **hexagonal architecture** (D49, ADR-017):

```
<module>/
├── domain/            pure Java — no Spring, JPA or Jackson
│   ├── model/         aggregates, entities, value objects, domain exceptions; behaviour lives here
│   ├── port/in/       use-case interfaces + command/result records (the module's @NamedInterface)
│   ├── port/out/      interfaces the module needs: repositories, external services, outbox, clock
│   └── service/       domain services (CollateralCalculator, BranchAccessPolicy, ...)
├── application/       use-case implementations: @Service, @Transactional, depend only on ports
└── infrastructure/
    ├── web/           @RestController + DTOs; inject port/in, never a service class
    ├── persistence/   *JpaEntity, SpringData*Repository, mapper, adapter implementing port/out
    └── external/      adapters over integration/ clients implementing port/out
```

Rules enforced by ArchUnit and the Modulith verification test (build fails otherwise): `domain` imports only `domain` and the JDK; `application` never imports `infrastructure`; another module is reached only through its `port/in`; `JpaRepository` is injected only inside `persistence`. JPA entities and domain models are separate classes with a mapper.

Resources: one `src/main/resources/application.properties` (D36) — no profile files; secrets are `CHANGE_ME` in git. Migrations in `src/main/resources/db/migration/oracle/V001__…sql`; DBA-only scripts (schema users, grants, branch replacement) in `db/dba/`; manual undo scripts in `db/undo/`.

## Conventions that are easy to get wrong

- **No `Idempotency-Key`** header, filter, or table (D46). Repeated commands are rejected by the state machine (409) and the `NOWAIT` row lock. A second *registration* for the same agency + national code within `azs.request.duplicate-window` (10 s) is rejected with 409 under a `FOR UPDATE` lock on the `agency` row (D47).
- **Ownership lock** (D1): `locked_by`, `locked_at`, `last_activity_at` on `request`; every change goes through `SELECT … FOR UPDATE NOWAIT` (`@Lock(PESSIMISTIC_WRITE)` + `jakarta.persistence.lock.timeout=0`). ORA-00054 → 409. `UNDER_REVIEW` auto-releases after 60 min idle; `UNDER_ACTION` never auto-releases. «لغو اقدام» (D44) = `abandon` from `UNDER_ACTION`.
- **Cancel vs revoke** (D11): before «اقدام» → `CANCELLED`; from `UNDER_REVIEW` on → a `REVOCATION` request with `parent_request_id`; completing it sets the parent to `REVOKED`. There is **no `EXPIRED`** status and no expiry job (D19).
- **Inquiries** (D3, D26, D27): stage 1 = Sabte Ahval; stage 2 = the other 8 in parallel; results `PASSED | NEGATIVE | FAILED | SKIPPED`. Only `NEGATIVE` rejects; `FAILED` blocks and allows «تلاش مجدد». Stage deadlines 10 s / 15 s; a retry starts only if the remaining stage time ≥ the service timeout. Gender and birth date come from Sabte Ahval, never from Yaghut.
- **Customer data** (D2, D21, D22): snapshot from Yaghut `customerDetailInfo` at registration, stored **plain text**, used for the request's whole life. **Nothing is masked anywhere** (`azs.privacy.masking-enabled=false` exists but stays off).
- **Balances** are never stored and are read once, when the customer chooses collateral. No re-check at approval (D16 withdrawn).
- **Collateral** (D14, D15, D18): cash ≥ 20 %; pledge = `(amount − cash) × factor`, cheap deposits first (×1.00) then expensive (×1.05, ≤ 95 % of balance); `BigDecimal`, round **up** to whole rials.
- **Tokens** (D5, D7): RS256 JWT, `Authorization: Bearer` only, **no cookies, no CSRF**. Access 15 min; refresh 8 h with rotation + reuse detection (bank/agency); customer gets one 30-min session, no refresh.
- **OTP** (D34): Notifier generates and checks the 4-digit code (`SendOtp` / `OtpValidate`, synchronous, not through the outbox). We store only `sId`, attempts and lockout: 3 wrong attempts **per customer** → 15 min lock; 120 s validity.
- **SMS** (D40): notification SMS only via the outbox and `SendSimpleSms`; numbers as `0989xxxxxxxxx` (stored as `09…`). 400 → dead-letter + alert; 403 → alert + long backoff; 5xx/timeout → retry with backoff.
- **Yaghut** (D35): session-based; send **only** the `JSESSIONID` cookie. Technical account for `BranchSyncJob` only; the agency's and customer's own sessions for everything in their panels. Never retry `InvalidCredentialsWSException` on the technical account. Errors are recognised by the `exception` field, never by HTTP status alone.
- **Gateway token** (D28): OAuth2 client credentials, in-memory, refreshed 5 min before expiry, single-flight; used only by the 9 inquiry clients and personnelInfo. Notifier and ARcaptcha do **not** use it.
- **Bank login** (D33, D37, D39): ARcaptcha first (fail closed) → AAA `GetRoleWindows` (Spring WS, UsernameToken PasswordText, `mustUnderstand="0"`, no Timestamp) → personnelInfo for `abrnchcod` (string). Roles `Branch`→BRANCH, `Admin`→ADMIN.
- **Branches** (D32, D45): synced nightly from Yaghut; `parent_branch` set only by DBA script; `can_act = user.branch == request.branch_code || user.branch == parent_branch(request.branch_code)`. **Not cached** — read the table.
- **National code** (D30): `@IranianNationalCode` — normalise Persian/Arabic digits, exactly 10 digits, reject repeated digits, checksum. Always on.
- **Reports** (D42): CSV only (UTF-8 with BOM), streamed row by row from paged queries. No Excel, no POI.
- **Tables are not partitioned** (D43). `audit_log` is append-only; the app user has INSERT-only grants on it.
- **TLS** (D38): verify by default; `azs.integration.<service>.tls.skip-verify` per client only; never touch JVM-wide trust.
- **Errors** (decided 1405/08/25): RFC 7807 Problem Details via `ProblemDetail` / `@RestControllerAdvice`; Persian messages from `messages_fa.properties` (SRS texts, «شماه» corrected to «شماره»). Technical failures are 503 "بعداً تلاش کنید", never 400.
- **Coverage gate** (D50): every class gets a test in the same commit. JaCoCo `check` in `verify`: 100 % line + branch for `domain` and `application`, >= 90 % line for `infrastructure`; only `*Application`, `config/`, generated JAXB and Lombok-generated code are excluded. By layer: domain -> plain JUnit; use cases -> Mockito on out-ports; web -> `@WebMvcTest` + `@MockitoBean` use cases; persistence -> `@DataJpaTest` on Testcontainers Oracle; external -> WireMock / `MockWebServiceServer`.
- **Tests**: `@MockitoBean` only at our own boundaries, **never for external services**; external services are WireMock stubs + in-app fakes, with synthetic personas (`1000000011` all pass, `1000000028` deceased, `1000000036` Sabte Ahval mismatch, `1000000044` Shahkar mismatch, `1000000125` Sepad timeout then pass). Integration tests use Testcontainers Oracle and are gated by `-Dintegration=true`. No real bank data in the repo.

## Skills

The `spring-boot-4-skills` plugin is installed. Use these: `spring-modulith`, `hexagonal-architecture`, `layered-architecture` (only for what hexagonal leaves open), `domain-driven-design`, `flyway-migrations`, `spring-data-jpa`, `transactional-patterns`, `spring-security-jwt`, `security-audit-logging`, `http-interface-clients`, `resilience-retry`, `async-execution`, `scheduled-tasks`, `configuration-properties`, `problem-details-rfc9457`, `rest-api-conventions`, `openapi-first`, `testing-pyramid`, `production-observability`, `container-native-deployment`, and the `spring-reviewer` / `spring-security-reviewer` agents after each phase.

Do **not** apply: `idempotency-patterns`, `spring-data-redis`, `spring-cloud-gateway`, `webflux-reactive-patterns`, `multi-tenancy`, `event-driven-messaging`, `spring-batch`, `hateoas`, `api-versioning`, Spring AI / MCP skills. They contradict decisions above.

## Reference project

`settleit` (same team, Spring Boot 4 + Java 25 + PostgreSQL + Redis) is the style reference for Lombok usage, entity state methods, `UrlMappings`/`Pagination`/`ValidationPatterns`, `JalaliDateConverter`, logging filters, test layout, Dockerfile, Helm chart and GitLab CI. Do not copy its package layering (we use hexagonal, D49), PostgreSQL types, Redis/ShedLock-Redis, profile YAML files, OkHttp, `ErrorResponse` envelope, or its `settings.xml` mirror.

## Build and run

```bash
./mvnw clean verify                      # unit tests + Modulith/ArchUnit boundary tests
./mvnw verify -Dintegration=true         # + Testcontainers Oracle integration tests (Docker required)
./mvnw spring-boot:run                   # needs application.properties with real values
docker compose up                        # Oracle XE + WireMock stubs + the app
```

## Git

Commit after each completed step with a short imperative subject (`request: ownership lock and NOWAIT`). The remote is `https://github.com/Mahmirba/AsanZeman.git` (reset it with `git remote set-url origin …` if it reverts to the old name). Commit messages end with the `Co-Authored-By` and `Claude-Session` trailers the session provides.
