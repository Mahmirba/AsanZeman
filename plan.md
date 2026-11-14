# AsanZeman (آسان ضمان) — Implementation Plan

> Sources:
> - *AsanZeman Architecture Document v1.0* (سند معماری سامانه آسان ضمان, 1405/08/07). Section references like **§3-3-4** point to this document.
> - *SRS-AZS v1.0* (1405/07/27). References look like **SRS: customer completion, step 13**.
>
> **Precedence:** where the two documents conflict, the **architecture document wins**. The SRS is used for business rules the architecture document doesn't spell out, such as the collateral formulas, eligible deposit types and screen behaviour.

---

## 1. What we are building

A system for a bank to process **travel-return guarantees** (ضمانتنامه بازگشت از سفر), from the agency's registration through branch approval to recording the guarantee number. Cancellation, revocation and reporting are included.

> **Current scope: backend only (D31).** The three panels are on hold until the product owner explains the UI. The backend exposes everything through a contract-first REST API (`docs/api/openapi.yaml`).

| Piece | Description |
|---|---|
| **Asan Zeman Core** | One Spring Boot **modular monolith** (Java 25, Spring Boot 4.1, virtual threads; see D9, D24) inside the bank intranet. It has 6 modules: Auth, Request, Inquiry, Notification, Report and Scheduler. |
| **Bank panel** | For branch users and admins. Intranet only. Login: ARcaptcha + AAAService + personnelInfo (§6.2). *UI deferred (D31).* |
| **Agency panel** | In the DMZ at `www.asanzeman.sb24.com/agencies`. Login: Yaghut `loginStatic` + ARcaptcha. *UI deferred (D31).* |
| **Customer panel** | In the DMZ at `www.asanzeman.sb24.com`. Login: Yaghut `loginStatic` + ARcaptcha + SMS OTP (§6.3). *UI deferred (D31).* |
| **Oracle 19c+** | The **only** data store. There is no Redis, Kafka or object storage. Schemas: `AZS_AUTH`, `AZS_BUSINESS`, `AZS_INQUIRY`, `AZS_AUDIT`, `AZS_OUTBOX`. |
| **External** | **Called directly:** AAAService (SOAP, §6.2), Yaghut (4 services), the **SMS Notifier** (`10.0.41.24:8012`, its own user and password; §6.3, §6.4) and **ARcaptcha** (`challenge.sb24.ir`, §6.5). **Through the Bank API Gateway**, with one OAuth2 client-credentials token (D28, §5.2): the 9 inquiries and **personnelInfo**. |
| **Out of scope (manual)** | Farangin (guarantee issuance and revocation) and the Digital Signature system. There is **no API integration** with either one. The branch user does this work by hand while the request is in `UNDER_ACTION`. |

### Core flows (§2-4)
1. **Issuance:** The agency registers the request. The customer completes it: the 9 inquiries run in two stages (Sabte Ahval first, then the other 8 in parallel; §5.1), and then the customer chooses collateral. The branch then locks the request and reviews it, and either approves (→ `UNDER_ACTION`) or rejects it. After approval, the branch signs and issues manually outside the system, then enters the guarantee number (→ `ISSUED`) and an SMS goes out.
2. **Cancellation or revocation by the agency.** What happens depends on how far the request has got (§4.2):
   - **Before «اقدام»** (`REGISTERED_AT_AGENCY` or `PENDING_BRANCH_REVIEW`): nothing has been done in the Digital Signature system or Farangin yet, so the request goes straight to **`CANCELLED` (لغو شده)**. No branch review is needed.
   - **From «اقدام» onward** (`UNDER_REVIEW`, `UNDER_ACTION` or `ISSUED`): something may already have been signed or issued, so a **revocation request (درخواست ابطال)** is sent to the branch. The branch reviews it, revokes manually in Farangin where needed, and confirms (→ **`REVOKED` (ابطال شده)**). The branch can also reject the revocation request.
3. **Agency tracking:** Filtered list of the agency's requests, with Excel export.
4. **Bank reporting:** Advanced filters with Excel export (SXSSF streaming).

---

## 2. Decision log

The architecture document contradicts itself, or is silent, on the points below. They were reviewed with the product owner on 1405/08/17, 1405/08/19, 1405/08/20, 1405/08/22 and 1405/08/23. ✅ means decided, ⏳ means open or proposed. Where the product owner had no preference, the recommended default was adopted. Every value marked *configurable* lives in the single `application.properties` (D36) and can change without a release.

**Still open:** the Yaghut deposit details (§5.4) and how Phase 0 is built (§10). Both are deferred by the product owner.

| # | Topic | Decision | Details |
|---|---|---|---|
| D1 | ✅ Locking across the manual work | **Ownership lock** (`locked_by` on the request), with `SELECT … FOR UPDATE NOWAIT` guarding each change to it. ADR-003 is amended. | §4.1 |
| D2 | ✅ Customer mobile number (**revised** 1405/08/17) | **Stored in plain text**, together with the other customer details, when the agency registers the request (see D21). This replaces the earlier rule "never stored, fetched from Yaghut each time". | §6.1 |
| D3 | ✅ Inquiry technical failure | **Block and let the customer retry.** A *negative* answer rejects the request. A *technical* failure (timeout, 5xx, circuit open, unreadable response) blocks completion without rejecting. On retry, only the failed inquiries are re-run. A passed result is reused for **1 hour** (*configurable*). Manual-review fallback is **not** built. | §5.1 |
| D4 | ✅ Inquiry timeouts and retries | Use the **per-service table in §7-3** (8–15 s, 1–2 retries), not the flat 30 s with 3 retries. The whole run must stay within the **25 s budget** (P99, §9-1). All *configurable*. | §5, §5.1 |
| D5 | ✅ Session lifetime | Access token **15 min** for everyone. Refresh token **8 h** for branch, admin and agency users. **The agency session is capped at the earlier of 8 h and the agency user's Yaghut `sessionExpirationDate`** (D35). **Customers:** a single **30-minute** session with no refresh token. All *configurable*. | §6, §5.4 |
| D6 | ✅ Collateral formulas | Taken from the SRS. | §4.3 |
| D7 | ✅ Where the browser keeps tokens | **No cookies** (an infrastructure restriction). Both tokens are sent only as `Authorization: Bearer`, so **no CSRF protection is needed**. Access token **in memory**. Staff and agency refresh token in **`sessionStorage`**, with **rotation and reuse detection**. Customers: **memory only**, with no refresh token. Nothing in `localStorage`. | §6 |
| D8 | ✅ CI/CD | **GitHub Actions** while the code is on GitHub. The stages mirror §8-2, so the pipeline moves cleanly to GitLab CI later. | §7 Phase 0 |
| D9 | ✅ Versions (**revised** 1405/08/19) | **Java 25 (LTS), Spring Boot 4.1.x, Spring Modulith 2.1.x.** Angular 22 with Nx when the UI starts (deferred, D31). Versions are pinned in the build. This replaces Boot 3.2, Java 17 and Angular 17 from §1-4, which are past upstream support. Java 25 was chosen over 21 because, since Java 24, virtual threads no longer pin their carrier thread inside `synchronized` blocks (D24). | §3 |
| D10 | ✅ Runtime | **Build a Docker image (executable jar) now.** Keep a Tomcat/WAR profile possible. Decide Kubernetes versus VMs with the ops team before Phase 10. | §7 Phase 10 |
| D11 | ✅ Cancellation versus revocation | **Before «اقدام»:** the request goes to `CANCELLED` (لغو شده) immediately. **From «اقدام» onward**, including approved and issued requests: a revocation request goes to the branch and ends in `REVOKED` (ابطال شده). | §4.2 |
| D12 | ✅ Tracking code (**revised** 1405/08/20) | The **full branch code** (as Yaghut returns it, never truncated) + agency (4) + date YYMMDD (6) + a 4-digit counter, so **17–19 digits** depending on the branch. Codes can't collide, because everything after the branch code is fixed-width. Stored as `VARCHAR2(20)`. The counter runs **per branch + agency + day**, in a `tracking_counter` table incremented under a row lock. | §4 |
| D13 | ✅ Secrets (**revised** 1405/08/20) | **All secrets live in the single `application.properties`** (D36): the Yaghut technical account, gateway `client_id`/`client_secret`, Notifier `user`/`pass`, AAA technical account, ARcaptcha `site_key`/`secret_key` (per panel), database password and JWT signing key. **The repo copy holds only placeholders (`CHANGE_ME`).** The real values exist only in the copy on each server, readable by ops only. The app refuses to start while any secret is still `CHANGE_ME`. | §6 |
| D14 | ✅ Pledge formula | **Follow the SRS worked example:** pledge = `(amount − cash) × factor`. For example, 1,000,000,000 rials with 20 % cash pledges 800,000,000 (840,000,000 on expensive deposits). The literal SRS formula, which gives 640,000,000, is treated as a typo. | §4.3 |
| D15 | ✅ Mixed cheap and expensive pledge | **Cheap deposits are filled first** (×1.00), then expensive ones (×1.05, each capped at 95 % of its balance). Within each group, deposits are filled in the order the customer selected them. | §4.3 |
| D16 | ✅ Balance changes before approval | **Balances are re-checked** through Yaghut `deposit`, using the **technical account's** session (D35), when the branch clicks approve. If the deposits no longer cover the collateral, approval is blocked and the shortfall is shown. | §4.3, Phase 4 |
| D17 | ✅ Revocation while the branch is mid-work | **Issuance actions stop**, except for recording a guarantee number that was *already* issued in Farangin. The revocation request goes **straight to the user who holds the lock**. An admin can reassign it with force-release. | §4.2 |
| D18 | ✅ Rounding | Collateral amounts are **rounded up to the whole rial** (`BigDecimal`, `CEILING`), so the bank is never under-covered. | §4.3 |
| D19 | ✅ Guarantee expiry | **There is no `EXPIRED` status, and no job changes issued guarantees.** Asan Zeman has no connection to Farangin, where the guarantee actually lives, so it can't know when one really ends. `expiry_date` is stored as information only (shown in tracking and reports, and usable as a filter). An `ISSUED` request can be revoked at any time. | §4.2 |
| D20 | ✅ Database migrations | **Flyway replaces Liquibase** (§5-4). Versioned SQL migrations, **forward-fix only**, because Flyway undo is a paid feature. A risky change gets a hand-written undo script for the DBA. | §4, Phase 0 |
| D21 | ✅ Customer data snapshot | When the agency registers a request, Yaghut `customerDetailInfo` is called **once**, with the **agency user's own Yaghut session** (D35). The mobile number and the other details are saved **in plain text** and used for **the whole life of the request** (Shahkar, OTP, SMS, display). They are never refreshed from Yaghut or `loginStatic`. **Exception:** gender and birth date for the inquiries come from Sabte Ahval, because Yaghut's gender is unreliable (D26). | §6.1 |
| D22 | ✅ No masking | **Nothing is masked anywhere**: screens, Excel exports, logs, the audit log, error messages and saved raw responses. The masking code stays behind `azs.privacy.masking-enabled: false`, so it can be switched on later without a code change. | §6 |
| D23 | ✅ No rate limiting or bulkheads | **No rate limits** (neither the 100 requests/min per-IP filter from §6, nor the 10 requests/s limiter on outgoing calls) and **no semaphores or bulkheads** on external services. What remains: per-service timeouts and retries, circuit breakers, ARcaptcha, and the OTP lockout. | §5, §6 |
| D24 | ✅ Virtual threads | `spring.threads.virtual.enabled=true`, so incoming HTTP requests, inquiry runs, `@Async` work and scheduled jobs all run on virtual threads. The **Hikari connection pool** is the only limit on database work, and inquiry runs never hold a DB connection while waiting on a service. | §5.1 |
| D25 | ✅ Structured concurrency (decided 1405/08/23) | Each inquiry stage runs in a **`StructuredTaskScope`** (Java 25, **preview**), with a custom `Joiner` that cancels the rest on the first `NEGATIVE`, and the stage deadline as the scope timeout. **This means `--enable-preview` at compile, test and run time** (`maven-compiler-plugin`, the Surefire and Failsafe `argLine`, and the `java` command in the Docker image). The preview API may change in later Java versions, so **a Java upgrade must re-check `StageRunner`**, the only class that uses it. | §5.1 |
| D26 | ✅ Inquiry order: two stages | **Stage 1: Sabte Ahval alone.** It supplies the authoritative gender and birth date. **Stage 2: the other 8 in parallel.** Sepad runs only for men aged 18–50 *inclusive*, using Sabte Ahval's data. Implemented as **one chain of stages** (Chain of Responsibility), with **one Strategy class per inquiry**. The stage list is configurable. | §5.1 |
| D27 | ✅ Time budget (agreed 1405/08/20) | §7-3's timeouts × attempts can't fit the 25 s budget (Sabte Ahval alone is 15 s × 3 = 45 s). **Proposal: deadline-aware retries.** Each stage gets a deadline (stage 1: **10 s**, stage 2: **15 s**, both configurable). A retry starts **only if the time left in the stage is at least that service's timeout**; otherwise the inquiry is `FAILED`, and the customer can retry (D3). | §5.1 |
| D28 | ✅ Gateway token | OAuth2 **client credentials** from `apigw.int.sb2.com/ApiContainer.sso.RCL1/connect/token`. One token per app instance, kept in memory and refreshed before its 2-hour expiry. It is used by **the 9 inquiries and personnelInfo**. The Notifier has its own user and password (D34), and ARcaptcha is still pending. `invalid_client` (HTTP 400) is a configuration error and is not retried. | §5.2 |
| D29 | ✅ Rejection messages | Taken **from the SRS** for every inquiry. The exception is Sabte Ahval `300` (wrong details), which uses the product owner's text because the SRS has none. All messages live in a message config file. | §5.3 |
| D30 | ✅ National code validation | **Exactly 10 digits** after converting Persian and Arabic digits and trimming. Codes made of one repeated digit are rejected, and the **check digit** must pass (§4.5). It's always on, in every environment, with **no zero-padding**: `81029310` is rejected. Enforced on every request DTO (`@IranianNationalCode`) and again before every inquiry. Stored as `VARCHAR2(10)`. | §4.5 |
| D31 | ✅ Backend only for now | **No UI work** (no Nx or Angular workspace, screens or validators) until the product owner explains how the UI should work. The backend is built contract-first, so the panels can follow later. | §1, §7 |
| D32 | ✅ Branches come from Yaghut | There's **no branch form** in the base data. `BranchSyncJob` fills and updates `branch` from Yaghut `branches`, using the technical account (D35). Each branch has a **`parent_branch`**: itself when open, its **replacement** when closed. Replacements are set **only by database scripts**. Both the original branch and the replacement can see and act on a closed branch's requests, including revocations. | §4.4 |
| D33 | ✅ Bank user's branch | It comes from **personnelInfo** (`abrnchcod` only), fetched at every login. `accountName` is the login username without `@sb24.ir`. There's no check that the branch exists in `branch`. The lock holder's display name is `accountName`. | §6.2 |
| D34 | ✅ OTP via the Notifier | The **Notifier generates and checks** a 4-digit code (`SendOtp` and `OtpValidate`); Asan Zeman never stores it. `SendOtp` is called **synchronously**, not through the outbox, because the `sId` is needed at once. Validity is `azs.otp.validity` (120 s). **3 wrong attempts per customer** (not per code) → 15-minute lockout. Each code is used only once. The number is sent as `09xxxxxxxxx`, with `ClientName` `AZS` and its own credentials. | §6.3 |
| D35 | ✅ Yaghut sessions and faults | Yaghut is **session-based**: `loginStatic` returns a **`JSESSIONID` cookie**, which is the **only** thing sent on later calls. The body's `sessionId` and any other cookies are ignored. **Whose session:** the **Asan Zeman technical account** (credentials in `application.properties`) for branch-side calls and `BranchSyncJob`. **In the agency and customer panels, every Yaghut call uses that user's own login session.** **A session is reused until** it's null, its `sessionExpirationDate` passes, or Yaghut answers `InvalidSessionWSException`. Only the technical account can log in again by itself; agency and customer users must log in again. **Faults** come as JSON `{exception, exception_params, success:false, exception_message}` and are matched on `exception`. **Login refusals:** temporary sessions, expired passwords, suspected customers and non-`ACTUAL` customers. **Agencies are identified by `cif`.** The `context` (arg0) fields are ignored. Clients use Spring `RestClient`, not Feign. | §5.4 |
| D36 | ✅ One config file | The whole project uses **one `application.properties`**, with no profile files (`application-dev.properties` and so on). Each server has its own copy with real values. Tests override the few values they need with `@DynamicPropertySource`. All examples in this plan use the `.properties` format. | §5.4, §6 |
| D37 | ✅ ARcaptcha | The backend verifies the panel's `arcaptchaToken` with `POST https://challenge.sb24.ir/api/arcaptcha/api/verify` **before** calling AAA or Yaghut. `site_key` and `secret_key` are **per panel** in config. **If ARcaptcha is down, login is refused** (fail closed). | §6.5 |
| D38 | ✅ Certificates | `azs.integration.<service>.tls.skip-verify` (default `false`) can switch off certificate checks **per service**, never JVM-wide. When it's `true`, a WARN is logged at every startup and a metric is exposed. An optional configurable truststore (`azs.tls.truststore.*`) is also supported, so the bank can switch to proper trust later. | §6 |
| D39 | ✅ AAAService | SOAP `GetRoleWindows` (SOAP 1.1), using **Spring Web Services** with Jakarta JAXB classes generated from the full WSDL (`xsd0`–`xsd28`), not Axis2. Security header: WS-Security **`UsernameToken`** (`PasswordText`, `mustUnderstand="0"`, **no timestamp**) with the AAA technical account. `ApplicationName` and `StoreName` come from config. `DetailError` → refused. Roles: AAA **`Branch` → BRANCH, `Admin` → ADMIN**. Any other role is refused. | §6.2 |
| D40 | ✅ Notification SMS | **`SendSimpleSms`** (`/api/v1/sms/SendSimpleSms` on the Notifier host) through the outbox. Numbers are sent as **`0989xxxxxxxxx`** (converted from the stored `09xxxxxxxxx`; `SendOtp` keeps `09…`). There are **8 SMS events**: 5 with SRS texts and 3 with agreed texts. | §6.4 |

The original issue descriptions are in this file's git history (commit `d2c7498`).

---

## 3. Repository layout

```
asanzeman/
├── plan.md                      ← this file
├── docs/
│   ├── architecture/            ← source doc + diagrams, ADRs (one md per ADR)
│   └── api/openapi.yaml         ← API-first contract (§1-3)
├── backend/                     ← Spring Boot modular monolith (Maven)
│   └── src/main/java/com/sb24/asanzeman/
│       ├── AsanZemanApplication.java
│       ├── shared/              ← BaseEntity, errors, idempotency, audit, security utils
│       ├── auth/                ← api/ (public interfaces) + internal/
│       ├── request/             ← domain/, api/, internal/ (RequestService, CollateralService, LockService)
│       ├── inquiry/
│       ├── notification/
│       ├── report/
│       ├── scheduler/
│       └── integration/         ← AaaClient (Spring WS), YaghutClient, GatewayTokenProvider, NotifierClient, ArcaptchaClient, one client per inquiry (+ fakes)
│   └── src/main/resources/wsdl/aaa/   ← AuthorizationSrv WSDL + xsd0–xsd28 (D39)
│   └── src/main/resources/db/migration/   ← Flyway SQL migrations (D20)
├── frontend/                    ← Nx monorepo (ADR-007), DEFERRED (D31): not created yet
│   ├── apps/bank-panel/
│   ├── apps/agency-panel/
│   ├── apps/customer-panel/
│   └── libs/ (ui-rtl, auth, api-client generated from OpenAPI, shared models)
├── deploy/                      ← Dockerfiles, docker-compose (Oracle XE + mocks), k8s manifests
└── .github/workflows/           ← CI (see D8)
```

**Module boundaries:** use **Spring Modulith**, plus an ArchUnit test, so that cross-module calls can only go through each module's `api` package (ADR-001). This check runs in CI and fails the build when broken.

---

## 4. Domain model and state machine

### Entities (§5-1)
`Customer`, `Agency`, `Branch`, `Request`, `Companion` (≤ 5 per request), `Collateral`, `Inquiry`, `RejectionReason` and `AuditLog`. All of them extend `BaseEntity`, which has `id RAW(16)` (SYS_GUID), `created_at`, `updated_at`, `created_by` and `updated_by`.

The `agency` table has a unique **`cif`** column, used to find the agency after its user logs in (D35). Supporting tables: `app_user` (bank users: `accountName`, `prsnCode`, branch and position from personnelInfo), `otp_challenge` (§6.3), `refresh_tokens`, `jwt_blacklist`, `outbox_messages`, `scheduler_lock`, `idempotency_keys`, `tracking_counter`. `branch` is described in §4.4.

### Request lifecycle (§4-3-1, §7-5)

**Issuance request** (`request_type = ISSUANCE`):

```mermaid
stateDiagram-v2
    [*] --> REGISTERED_AT_AGENCY: agency registers
    REGISTERED_AT_AGENCY --> PENDING_BRANCH_REVIEW: customer completes (inquiries OK + collateral)
    REGISTERED_AT_AGENCY --> REJECTED: inquiry negative / 7-day auto-reject
    REGISTERED_AT_AGENCY --> CANCELLED: agency cancels (before «اقدام»)
    PENDING_BRANCH_REVIEW --> CANCELLED: agency cancels (before «اقدام»)
    PENDING_BRANCH_REVIEW --> UNDER_REVIEW: branch user «اقدام» (lock)
    UNDER_REVIEW --> PENDING_BRANCH_REVIEW: abandon / idle timeout
    UNDER_REVIEW --> UNDER_ACTION: approve
    UNDER_REVIEW --> REJECTED: reject (reason + comment)
    UNDER_ACTION --> PENDING_BRANCH_REVIEW: "انصراف از درخواست" (abandon) / admin force-release
    UNDER_ACTION --> ISSUED: guarantee_number entered (issuance)
    UNDER_REVIEW --> REVOKED: revocation completed (§4.2)
    UNDER_ACTION --> REVOKED: revocation completed (§4.2)
    ISSUED --> REVOKED: revocation completed (§4.2)
```

**Revocation request** (`request_type = REVOCATION`, linked to its issuance request by `parent_request_id`). It goes through the same branch review and lock process as an issuance request:

```mermaid
stateDiagram-v2
    [*] --> PENDING_BRANCH_REVIEW: agency submits revocation (parent is in or past «اقدام»)
    [*] --> UNDER_REVIEW: parent is locked → assigned straight to the lock holder (D17)
    PENDING_BRANCH_REVIEW --> UNDER_REVIEW: branch user «اقدام» (lock)
    UNDER_REVIEW --> PENDING_BRANCH_REVIEW: abandon / idle timeout
    UNDER_REVIEW --> UNDER_ACTION: approve revocation
    UNDER_REVIEW --> REJECTED: reject (revocation rejection reason + comment)
    UNDER_ACTION --> PENDING_BRANCH_REVIEW: abandon / admin force-release
    UNDER_ACTION --> COMPLETED: branch confirms revocation done in Farangin (or that nothing was issued)
```

| Status | Persian label | Meaning |
|---|---|---|
| `CANCELLED` | لغو شده | The agency withdrew the request **before «اقدام»**. Nothing was done outside the system. |
| `REVOKED` | ابطال شده | The issuance request was revoked through an approved and completed revocation request. |
| `PENDING_BRANCH_REVIEW` (on a revocation) | در انتظار بررسی درخواست ابطال ضمانتنامه | Waiting in the branch inbox (SRS UC-11, step 6) |

> **Naming.** The architecture document uses `CANCELLED` and `request_type = CANCELLATION` for ابطال. This plan separates **لغو (`CANCELLED`)** from **ابطال (`REVOKED`, `REVOCATION`)**, as the product owner asked. The meaning is unchanged; only the names differ.

- Transitions are implemented as methods on `Request`:
  - issuance: `complete()`, `cancelByAgency()`, `markIssued()`, `markRevoked()`
  - both request types: `lock()`, `approve()`, `reject()`, `abandon()`

  An illegal transition throws an `IllegalStateException`, which the API returns as **409**.
- Every transition publishes a Spring `ApplicationEvent` (`RequestRegistered`, `RequestCancelled`, `RevocationRequested`, `RequestApproved`, `RequestRejected`, `RequestRevoked`, `AutoRejectTriggered`, …). Notification and Audit subscribe to these events with `@TransactionalEventListener(AFTER_COMMIT)` (or `BEFORE_COMMIT` when writing to the outbox).

### 4.1 Request locking (resolves D1, amends ADR-003)

**The problem.** A database row lock from `SELECT … FOR UPDATE NOWAIT` is released at the end of its transaction, so it can't last longer than one HTTP request. The SRS says that once a branch user clicks **«اقدام»** (take action), the request is locked and disappears from every other user's inbox. It stays that way until the user finishes, or clicks **«انصراف از درخواست»** (abandon). That covers the whole `UNDER_REVIEW` and `UNDER_ACTION` period, including the manual digital-signature and Farangin steps, which can take hours.

**The design.** The lock is two layers working together:

1. **Ownership lock (lasts across requests).** It is stored as columns on `request`:
   - `locked_by` (RAW(16), the branch user), `locked_by_name` (for the inbox label), `locked_at` and `last_activity_at`
   - `version` (NUMBER, JPA `@Version`), as a second safeguard

   A request is locked whenever `locked_by IS NOT NULL`. The status says which phase it is in: `UNDER_REVIEW` or `UNDER_ACTION`.

2. **Row lock (lasts one transaction).** **Every** command that reads or changes ownership runs inside a single short transaction:
   ```
   BEGIN
     SELECT … FROM request WHERE id = :id FOR UPDATE NOWAIT   -- @Lock(PESSIMISTIC_WRITE) + lock.timeout=0
     -- ORA-00054 → 409 "این درخواست در حال اقدام توسط کاربر دیگری است" (another user is acting on this request)
     check branch scope, current status, and ownership
     apply the transition, write the audit row, publish the domain event
   COMMIT                                                     -- row lock released immediately
   ```
   This makes taking ownership atomic. Two users clicking «اقدام» at the same moment can't both win: one gets the row lock, and the other gets ORA-00054, which becomes a 409.

| Command | Allowed when | Effect |
|---|---|---|
| `POST /api/branch/requests/{id}/lock` («اقدام») | `PENDING_BRANCH_REVIEW` and request belongs to the user's branch, **or** the user already holds it (idempotent re-entry) | `UNDER_REVIEW`, sets `locked_by`, `locked_at` and `last_activity_at` |
| `approve` | `UNDER_REVIEW` and `locked_by = me` | `UNDER_ACTION`. **Ownership is kept** through the manual signing and Farangin steps |
| `reject` | `UNDER_REVIEW` and `locked_by = me` | `REJECTED`, clears the lock |
| `issue` (enter guarantee number) / `complete-revocation` | `UNDER_ACTION` and `locked_by = me` | Issuance request → `ISSUED`. Revocation request → `COMPLETED`, and its issuance request → `REVOKED`. Clears the lock. |
| `abandon` («انصراف از درخواست») | `UNDER_REVIEW` or `UNDER_ACTION`, and `locked_by = me` | `PENDING_BRANCH_REVIEW`, clears the lock |
| `force-release` (ADMIN only, reason required) | locked by anyone | `PENDING_BRANCH_REVIEW`, clears the lock, **audited** |
| any other mutating call while `locked_by ≠ me` | — | **409**, with the holder's name and the time it was locked |

- **Inbox (SRS):** locked requests are removed from other users' *action* lists but stay **visible read-only**, labelled "در حال بررسی / در حال اقدام — {name}". All branch users can still open the details. Which requests a user sees and can act on follows the branch rule in §4.4. The holder's name shown is their `accountName` (D33).
- **Stale locks.** ADR-003 says the lock is freed if the user's session crashes, but an ownership lock doesn't free itself. So:
  - **`UNDER_REVIEW`:** a scheduler job releases locks idle longer than `azs.lock.review-idle-timeout` (default **60 min**, configurable) back to `PENDING_BRANCH_REVIEW`. This is audited as `AUTO_RELEASE`.
  - **`UNDER_ACTION`:** **never** released automatically, because the user may be partway through Farangin. It is freed only by the holder (abandon or complete) or by an admin force-release.
- **Every lock event is audited:** LOCK, ABANDON, AUTO_RELEASE and FORCE_RELEASE, with who did it, when, the IP and the reason.
- **Tests (Phase 4):** concurrent lock attempts (exactly one wins and the other gets 409); a non-owner trying approve, reject or issue (409); idempotent re-lock by the owner; auto-release of `UNDER_REVIEW` only; admin force-release; the lock surviving between HTTP calls.

### 4.2 Agency cancellation versus revocation (resolves D11)

**The rule (from the product owner).** Clicking «اقدام» is the dividing line:
- **Before «اقدام»**, the branch hasn't touched the Digital Signature system or Farangin, so the agency can simply **cancel (لغو)**.
- **From «اقدام» onward**, a contract may have been signed or a guarantee issued outside the system, so the branch must **revoke (ابطال)** it.

**One action, two outcomes.** The agency panel has a single «ثبت درخواست ابطال» (submit revocation request) action. The backend decides the outcome inside one transaction, under the row lock from §4.1:

```
BEGIN
  SELECT … FROM request WHERE id = :id FOR UPDATE NOWAIT     -- same guard as §4.1
  check agency ownership
  CASE status
    REGISTERED_AT_AGENCY, PENDING_BRANCH_REVIEW        → status = CANCELLED                       (لغو)
    UNDER_REVIEW, UNDER_ACTION, ISSUED                 → create REVOCATION request (parent_request_id)  (ابطال)
    REJECTED, CANCELLED, REVOKED                       → 409 (nothing to cancel)
    a revocation is already open                       → 409 (duplicate)
COMMIT
```

- **The confirmation message the agency sees depends on the status.** Before «اقدام», it says the request will be cancelled immediately. From «اقدام» onward, it uses the SRS text: «… پس از تایید، درخواست برای شعبه مربوطه ارسال خواهد شد» (after confirmation, the request will be sent to the branch). The API response also says which outcome happened (`CANCELLED` or `REVOCATION_REQUESTED`).
- **Race with the branch.** Suppose the agency clicks cancel at the same moment a branch user clicks «اقدام». The row lock lets only one of them go first. If the branch wins, the agency's call sees `UNDER_REVIEW` and creates a revocation request instead, which is the correct outcome. If the agency wins, the request is cancelled, and the branch user's «اقدام» gets a 409.
- **When the issuance request is mid-work (D17).** If it is in `UNDER_REVIEW` or `UNDER_ACTION`:
  - approve and reject on the issuance request are disabled
  - the holder may still record a guarantee number that was *already* issued in Farangin, so the records match reality
  - the new revocation request is assigned directly to that holder, with its status set to `UNDER_REVIEW` and its `locked_by` set to the holder
- **When revocation completes:** the issuance request moves to `REVOKED`, an audit row is written, and the customer and agency receive an SMS. If nothing had actually been issued in Farangin, the branch confirms that instead, and the issuance request still ends as `REVOKED`.
- **When revocation is rejected:** the revocation request becomes `REJECTED`, with a reason from the admin-managed list of revocation rejection reasons (SRS use case 6) and a comment. The issuance request is unchanged. If it was mid-work, its normal flow resumes.
- **No expiry (D19).** Asan Zeman never changes an `ISSUED` request because of its `expiry_date`, since the real guarantee lives in Farangin. An `ISSUED` request can be revoked at any time. `expiry_date` is shown, and can be filtered on, in tracking and reports.
- **Reports and tracking** show `CANCELLED` and `REVOKED` as separate statuses. Both the agency and the bank panels can filter on each.
- **Tests (Phase 5):**
  - every source status gives the correct outcome, as in the table above
  - the cancel-versus-«اقدام» race
  - a duplicate revocation returns 409
  - revocation of a request that is mid-work goes straight to the lock holder
  - a rejected revocation leaves the issuance request unchanged

### 4.3 Collateral calculation (resolves D6; source: SRS customer completion, steps 8–13 and the calculation table)

**Two kinds of collateral**
- **Cash (وجه نقد):** money is **withdrawn** from the customer's deposits.
- **Deposit pledge (توثیق سپرده):** money is **blocked** in the customer's deposits.

**Deposits shown to the customer** come from Yaghut `deposit` (§5.4), using the customer's own session. They're filtered in the request and again in the backend. A deposit is shown only if it is:
- held individually (انفرادی), not joint, with the customer as owner **and** signatory
- open and active, and not blocked for debit or credit (مسدود بدهکار/بستانکار)
- in rials. Foreign-currency deposits are never shown.

| Deposit type | Price class | Allowed for cash | Allowed for pledge |
|---|---|---|---|
| Short-term normal (کوتاه‌مدت عادی) | Cheap | ✅ | ✅ |
| Short-term Win (کوتاه‌مدت وین) | Cheap | ✅ | ✅ |
| Qard-al-Hasan savings (قرض‌الحسنه پس‌انداز) | Cheap | ✅ | ✅ |
| Qard-al-Hasan savings Win (قرض‌الحسنه پس‌انداز وین) | Cheap | ✅ | ✅ |
| Qard-al-Hasan current (قرض‌الحسنه جاری) | Cheap | ✅ | ✅ |
| Long-term (بلندمدت) | Expensive | ❌ | ✅ (block ≤ 95 % of balance) |
| Short-term special (کوتاه‌مدت ویژه) | Expensive | ❌ | ✅ (block ≤ 95 % of balance) |

> The SRS table row for cash lists fewer types than its own deposit-filter rules. This plan follows the **filter rules**, which give the full list above.

**Mapping Yaghut deposits to these types.** Yaghut returns a `group` (`depositGroupTypeWS`) and a numeric `depositType`. The mapping lives in configuration, so it can change without a release:

| Yaghut `group` | Type | Price class |
|---|---|---|
| `SHORT_ACCOUNT` | short-term normal | cheap |
| `PASANDAZ` | Qard-al-Hasan savings | cheap |
| `JARI_ACCOUNT` | Qard-al-Hasan current | cheap |
| `LONG_ACCOUNT` | long-term | expensive |
| `SPECIAL_SHORT_ACCOUNT` | short-term special | expensive |
| `SPECIAL_LONG_ACCOUNT` | ⏳ open: expensive, or not eligible? | — |
| `ANDOKHTE_ACCOUNT`, `EDARE_SHODE`, `OTHERS` | not eligible (proposed) | — |
| *(by `depositType` code)* | ⏳ short-term Win, savings Win: codes still needed | cheap |

- **The withdrawable balance used for the collateral rules is `availableBalance`.**
- **Individual versus joint** is read from `owner` (`depositOwnerTypeWS`). Its values are still needed.

**Share percentages (SRS step 12)**
- Cash share `c`: 20 % to 100 %, in steps of 10. **At least 20 % cash is mandatory.**
- Pledge share `p`: 0 %, or 10 % to 80 %, in steps of 10.
- `c + p = 100 %`. If `c = 100 %`, the pledge options are disabled.
- A deposit can be marked for cash, pledge or both, and more than one deposit can be selected.

**Formulas.** `A` is the guarantee amount in rials.

| Part | Amount | Rule that must hold |
|---|---|---|
| Cash | `C = c × A` | Σ withdrawable balance of the deposits selected for cash ≥ `C` |
| Pledge base | `R = A − C` (= `p × A`) | — |
| Pledge on cheap deposits | `R_cheap × 1.00` | Σ withdrawable balance of the selected cheap deposits ≥ the amount blocked on them |
| Pledge on expensive deposits | `R_exp × 1.05` | For **each** expensive deposit, the amount blocked ≤ **95 %** of its withdrawable balance. If not, show an error and ask the customer to pick another option. |
| **Total collateral** | `C + R_cheap × 1.00 + R_exp × 1.05` | `R_cheap + R_exp = R`, split per D15 |

- When one deposit is used for both cash and pledge, the cash withdrawn plus the amount blocked must fit within that deposit's withdrawable balance.
- **Rounding:** use `BigDecimal` and round to whole rials with `CEILING`, so the bank is never under-collateralised (D18).
- **Insufficient balance (SRS sub-flow 11):** show «مشتری گرامی! به دلیل کافی‌نبودن موجودی در سپرده‌های انتخابی برای وثیقه، ثبت درخواست امکان‌پذیر نیست…» (insufficient balance in the selected deposits). The request cannot be submitted.
- **UI:** show a green tick next to each deposit row once the selection covers the required amount. When cash alone is enough at 100 %, the other rows are disabled.

**Worked examples.** These become unit tests. `A = 1,000,000,000` rials.

| Case | Cash | Pledge | Total collateral | Notes |
|---|---|---|---|---|
| 100 % cash | 1,000,000,000 | 0 | 1,000,000,000 | pledge rows disabled |
| 20 % cash, 80 % cheap pledge | 200,000,000 | 800,000,000 | 1,000,000,000 | SRS example (D14) |
| 20 % cash, 80 % expensive pledge | 200,000,000 | 840,000,000 | 1,040,000,000 | SRS example; that deposit's balance must be ≥ 884,210,527 (840M ÷ 0.95, rounded up) |
| 50 % cash, 50 % expensive pledge | 500,000,000 | 525,000,000 | 1,025,000,000 | |
| 10 % cash | — | — | — | rejected, below the 20 % minimum |
| 20 % cash, 70 % pledge | — | — | — | rejected, shares must add up to 100 % |

**Implementation.** `CollateralService` uses the Strategy pattern from §4-3-2, with these strategies:
- `CashStrategy`
- `CheapPledgeStrategy` (factor 1.00)
- `ExpensivePledgeStrategy` (factor 1.05, cap 0.95)

Each strategy is an immutable value class. The factors, the cap and the share ranges live in configuration.

**What gets saved:** one `Collateral` row per deposit allocation, with:
- deposit number and type, price class, collateral type (CASH or PLEDGE)
- share %, factor, allocated amount and calculated collateral

**Balances are never saved** (§6-3-1). They are fetched again at branch approval (D16).

### 4.4 Branches (D32)

**The table:** `branch` in `AZS_BUSINESS`:

| Column | Source |
|---|---|
| `branch_code` (PK, `VARCHAR2`) | Yaghut `branches` → `branchCode`, **always handled as a string** (the deprecated numeric `code` isn't used), with the full code as returned (D12). Matched to a bank user's `String.valueOf(abrnchcod)` exactly |
| `name`, `city`, `address`, `tel`, `fax`, `zip_code` | Yaghut `branches` |
| `parent_branch` (FK to `branch.branch_code`) | **set to `branch_code` when the branch is first inserted, and never overwritten by the sync** |
| `active` | `false` once Yaghut stops returning the branch. Yaghut doesn't return closed branches |
| `last_synced_at` | when the job last saw the branch |

**`BranchSyncJob`** (Scheduler module):
- **When:** at startup if the table is empty, then on a schedule (default nightly at 02:00, configurable). Leader election means one instance runs it.
- **Fetching:** the technical account's session (D35), paging with `offset`/`length` until `totalRecord` is reached. The changes are applied **only after every page has been read**.
- **What it does, in one transaction:**
  - a **new** branch is inserted, with `parent_branch = branch_code`
  - an **existing** branch has its details updated, but its `parent_branch` is left alone
  - a branch **missing** from Yaghut is set to `active = false`. It's never deleted, because requests and agency contracts point to it
- **If Yaghut fails:** nothing changes, ops gets an alert, and the next run tries again.
- **Afterwards:** the branch cache is cleared, and a summary is logged and audited.
- There is **no** admin form or API for branches.

**Closed branches and their replacements**
- A branch is **closed** when `active = false` **or** `parent_branch ≠ branch_code`.
- A closed branch can't be chosen for new requests.
- **Replacements are set only by database script.** `db/dba/set_branch_replacement.sql` is a reviewed template with two parameters: the closed branch and its replacement. It:
  - checks that the replacement is active and not closed itself
  - sets `parent_branch`
  - **re-points every branch that pointed to the closed one**, so there are never chains (A → B → C becomes A → C)
  - writes an audit row
- **Integrity check job** (nightly and at startup) raises an alert on:
  - cycles
  - chains
  - parents that are inactive or closed
  - **inactive branches with no replacement** («شعبه X بسته شده اما شعبه جایگزین ندارد»)
- **Cache:** branch data is cached for a short time (default 5 minutes), so a script's change reaches the inboxes without a restart.

**Who sees and acts on a request** (issuance and revocation alike):

```
handling_branch(request) = branch(request.branch_code).parent_branch
can_act(user, request)   = user.branch == request.branch_code        -- original branch, even if closed
                        || user.branch == handling_branch(request)  -- current replacement
```

- The same rule decides the **list or inbox** and **every action** («اقدام», approve, reject, the guarantee number, revocation review and confirmation). The API enforces it, and an index on `parent_branch` keeps the inbox query fast.
- The **ownership lock (§4.1)** stops the two branches from colliding: whoever clicks «اقدام» first owns the request.
- **When a branch closes, no locks are released.** The holder keeps access and can finish the work they started in Farangin. D17 is unchanged.
- **Revocations** of a closed branch's `ISSUED` requests reach both branches. A revocation already pending at the moment of closure moves to the replacement too, because the rule is applied when the inbox is queried.
- **Requests keep their original `branch_code`** for history. Reports show both the "original branch" and the "handled by" branch.
- **Agency contracts** stay on the old branch code, and the replacement serves them through `parent_branch`. No records are moved.
- **An in-between branch loses access:** if A is replaced by B and B is later replaced by C, A's requests are re-pointed to C, so A's and C's users can act on them, and B's can't.
- **Tests:** every flow against a closed branch (issuance review, revocation after closure, a revocation pending at closure, a lock held at closure); both branches able to act; a third branch refused; the script's checks; and each integrity-check alert.

### 4.5 National code validation (D30)

One class, `NationalCode`, is used by the `@IranianNationalCode` Bean Validation annotation and by the check that runs before each inquiry:

1. **Normalise:** convert Persian (`۰–۹`) and Arabic (`٠–٩`) digits to `0–9`, and trim.
2. **Format:** exactly 10 digits. There's **no zero-padding**, so 8 or 9 digits are rejected.
3. **Repeated digits:** reject a code made of one digit repeated (`0000000000` … `9999999999`).
4. **Check digit:**
   ```
   s = Σ d[i]×(10−i) for i = 0..8;   r = s mod 11
   valid ⇔ (r < 2 ∧ d[9] = r) ∨ (r ≥ 2 ∧ d[9] = 11 − r)
   ```

- **Error message:** «کد ملی وارد شده معتبر نیست.» (HTTP 400, RFC 7807).
- **Always on**, in every environment.
- **Tests:** valid codes; every wrong check digit; repeated digits; Persian and Arabic digits; 8, 9 and 11 digits; letters and spaces.

---

## 5. Integration plan (§4-5, §7)

| Client | Target | Notes |
|---|---|---|
| `AaaClient` | AAAService, SOAP over HTTPS, direct | `GetRoleWindows`: checks the branch user's password and returns their roles (§6.2, D39). The branch comes from personnelInfo |
| `YaghutClient` + `YaghutSessionManager` | Yaghut (direct HTTPS, session cookies) | Four calls: `loginStatic`, `branches`, `customerDetailInfo` and `deposit` (§5.4). Deposit data is **never stored**. |
| `GatewayTokenProvider` | `apigw.int.sb2.com` (token endpoint) | OAuth2 client credentials (D28). The token is added to **the 9 inquiry clients only** (§5.2). |
| 9 inquiry clients | `apigw.int.sb24.com` / `apigw3p.int.sb24.com` | One client per inquiry, with its own host, path and request fields in config (§5.3). |
| `PersonnelInfoClient` | `apigw.int.sb24.com` (gateway token) | Gives the logged-in bank user's branch (§6.2, D33). |
| `NotifierClient` | `https://10.0.41.24:8012` (direct, `user` and `pass`) | `SendOtp` and `OtpValidate` (§6.3, D34); `SendSimpleSms` (§6.4, D40) |
| `ArcaptchaClient` | `https://challenge.sb24.ir` (direct) | token verification (§6.5, D37) |

- **Anti-corruption layer:** each external service gets its own adapter that maps its format to domain types.
- **Resilience4j** (§7-6): circuit breaker opens at 50 % failures over a 20-call window and stays open 30 s. Timeouts and retries per service, from the §7-3 table (8–15 s, 1–2 retries with exponential backoff, on 5xx and timeouts only; D4), with retries limited by the stage deadline (D27). **No bulkheads or rate limiters** (D23). All values are configurable per client.
- Every call sends an `Idempotency-Key` header and the correlation/trace ID.
- **Mocks: two layers**, built from the bank's service documents, one service at a time:
  - **WireMock server (main layer):** runs in docker-compose and answers exactly like the real service (URLs, headers, JSON or SOAP, error codes). The app's **real** client code calls it, so serialisation, auth, timeouts and retries are all exercised. It's also used in integration tests.
  - **In-app fakes:** Java implementations of the same client interfaces, under the `fake` profile, for fast unit tests and frontend work with no server.
- **What each service gets:**
  - a contract summary in `docs/integrations/<service>.md`, which includes the classification of every result code as business-negative or technical (it drives D3)
  - a client interface with DTOs and an adapter (the anti-corruption layer)
  - WireMock stubs for the happy path, each business negative, and the technical failures: delay past the timeout, 5xx, a malformed body, a dropped connection, and "fails then recovers" using WireMock scenarios
  - client tests
- **Test personas:** the national code picks the scenario, so any case can be reproduced without changing config. **Every persona code passes the check digit (D30)**; codes like `0000000001` aren't valid. For example: `1000000011` means all OK, `1000000028` deceased, `1000000036` Sabte Ahval mismatch, `1000000044` Shahkar mismatch, `1000000125` Sepad times out once and then succeeds. The full table goes in `docs/integrations/personas.md`.
- **Only synthetic data** goes in stubs and in the repo. Any real customer data in the bank's documents is replaced before it is committed.
- **Order:**
  1. Gateway token, ARcaptcha and Notifier (Phase 1)
  2. AAAService, personnelInfo and Yaghut `loginStatic`
  3. Yaghut `customerDetailInfo`, `branches` and `deposit`
  4. the 9 inquiries

### 5.1 Running the customer inquiries (UC-09, resolves D3 and D4)

**Every inquiry result falls into one of four groups:**

| Result | Meaning | Examples |
|---|---|---|
| `PASSED` | The service answered, and the answer is OK | alive, Shahkar matches, no bounced cheques |
| `NEGATIVE` | The service answered, and the answer disqualifies the customer | deceased, military-service status not allowed, bounced cheque |
| `FAILED` | **No usable answer** after the automatic retries | timeout, 5xx error, connection refused, circuit breaker open, response that can't be read |
| `SKIPPED` | The inquiry doesn't apply to this customer | Sepad for women, or men outside ages 18–50 |

A result is only `NEGATIVE` when the service clearly says so. Anything unclear counts as `FAILED`, so nobody is rejected because of a technical fault.

**How the results are combined**
- Any `NEGATIVE` → **reject** with the SRS message for that inquiry. The inquiries still running are cancelled.
- No `NEGATIVE`, but at least one `FAILED` → **blocked**. The request stays in `REGISTERED_AT_AGENCY`; nothing is rejected and no SMS is sent. The customer sees:
  «مشتری گرامی! در حال حاضر امکان انجام برخی استعلام‌ها وجود ندارد. لطفاً دقایقی دیگر مجدداً تلاش کنید.» ("some inquiries can't be done right now, please try again in a few minutes")
- Everything `PASSED` or `SKIPPED` → the customer goes on to choose collateral (§4.3).

**How a run works**
- **It runs in the background.** `POST /api/customer/requests/{id}/inquiries` starts a run and returns **202** with a `runId`. The panel polls `GET …/inquiries/{runId}`, or uses server-sent events, and shows each of the 9 inquiries live (✓, ✗ or ⟳). A slow service therefore can't push the page past the proxy's HTTP timeout.
- **Two stages, run as one chain (D26):**
  ```
  Stage 1: [SABT_AHVAL]                      → NEGATIVE: reject · FAILED: blocked · PASSED: continue
                                               (gender + birth date go into the InquiryContext)
  Stage 2: [SHAHKAR, SEPAD, BLACKLIST, SAMA_CHEQUE, SAMAT, BOARD_RELATION,
            IMPORT_COMMITMENT, EXPORT_COMMITMENT]  (in parallel)
                                             → any NEGATIVE: reject (cancels the rest)
                                             → any FAILED: blocked · all PASSED/SKIPPED: collateral
  ```
  - Each inquiry is an `InquiryHandler` strategy with `type()`, `appliesTo(ctx)` (Sepad: male and aged 18–50 inclusive, from Sabte Ahval) and `execute(ctx)`.
  - A stage's handlers run in parallel. A stage with a single handler behaves like a classic chain link.
  - The stage list lives in config (`azs.inquiry.stages`), so a stage can be added without code changes.
  - If Sabte Ahval is `FAILED`, stage 2 doesn't run.
- **Threads:** each stage is one **`StructuredTaskScope`** (D25), and every fork runs on its own **virtual thread** (D24):
  ```java
  try (var scope = StructuredTaskScope.open(new StopOnFirstNegative(),
                                            cfg -> cfg.withTimeout(stageDeadline))) {
      handlers.stream().filter(h -> h.appliesTo(ctx))
              .forEach(h -> scope.fork(() -> h.execute(ctx)));
      return scope.join();   // the first NEGATIVE cancels the rest; the deadline cancels any stragglers
  }                          // leaving the block guarantees no inquiry is still running
  ```
  - `StopOnFirstNegative` is a custom `Joiner`. Its `onComplete` returns `true` (stop) as soon as a result is `NEGATIVE`. Inquiries still running when the deadline passes are marked `FAILED` (`TIMEOUT`).
  - Cancellation interrupts the forked threads, and the HTTP clients (`RestClient` on the JDK `HttpClient`) react to the interrupt.
  - The correlation ID is carried into the forks with Micrometer context propagation; `ScopedValue` is an option for request context.
  - Only `StageRunner` uses the preview API.
- **Time:** stage 1 then stage 2, each with its own deadline (D27). Different customers' runs are independent and run side by side.
- **Each service has its own timeout and retries** (§7-3 table, D4), applied by Resilience4j inside the client. An inquiry is marked `FAILED` only after its retries are used up. When a circuit breaker is open, the inquiry fails at once, without waiting for the timeout.
- **One run per request at a time.** The row lock (§4.1) is held for milliseconds, only to record "a run has started". If the customer double-clicks or opens a second tab, the call **attaches to the run already in progress** and returns its `runId`, so the customer never sees an error. A run older than `azs.inquiry.run-stale-after` (default **60 s**) is treated as dead (for example, after a server restart), and a new one may start.

**Retry by the customer**
- A «تلاش مجدد» (try again) button appears, with a cooldown of `azs.inquiry.retry-cooldown` (default **60 s**).
- On retry, **only the `FAILED` inquiries, and any whose result has gone stale, are re-run**. A `PASSED` result is reused for `azs.inquiry.result-reuse-window` (default **1 hour**).
- The 7-day auto-reject still applies. If a service stays down for a week, the request is auto-rejected under the existing rule.

**What is saved:** one `inquiry` row per inquiry per attempt, with:
- `run_id`, `inquiry_type`, `attempt_no`, `result` and `failure_kind` (`TIMEOUT`, `UNAVAILABLE`, `CIRCUIT_OPEN` or `BAD_RESPONSE`)
- the provider's response code, `duration_ms`, start and finish times, the provider's tracking ID (`conversationId`, `trackingCode`, `requestId`, …), and the full raw response (no masking, D22)

**Monitoring:** a Micrometer counter and timer per inquiry type and result, plus an alert when an inquiry type's `FAILED` rate is above a threshold. Ops then hears about an outage before customers complain.

**Tests (Phase 3):**
- every combination of `NEGATIVE`, `FAILED` and `PASSED`
- a double-click attaches to the running run
- a retry re-runs only the failed and stale inquiries
- the cooldown is enforced
- a stale run is recovered
- an open circuit fails fast
- a full run stays inside the stage deadlines with injected latency, and retries stop at the deadline (D27)
- stage 2 doesn't run when Sabte Ahval fails, and Sepad is `SKIPPED` for women and for men outside 18–50 (the boundaries 18 and 50 are included)

### 5.2 Gateway token (D28)

- **Request:** `POST https://apigw.int.sb2.com/ApiContainer.sso.RCL1/connect/token`, with a form body of `grant_type=client_credentials`, `client_id` and `client_secret`.
  - The Postman headers in the bank's sample (`Postman-Token`, `User-Agent`, the `BIGipServer` load-balancer cookies, …) are **not** sent.
- **Response:** `access_token`, `expires_in: 7200`, `token_type: Bearer` and `scope`.
  - The error case is HTTP **400** with `{"error":"invalid_client"}`.
- **Implementation:** Spring Security's OAuth2 client (`spring.security.oauth2.client.registration.bank-gateway`), plus a `RestClient` interceptor that adds `Authorization: Bearer …` to the inquiry clients only.
  - The token is cached in memory and refreshed about 5 minutes before it expires.
  - When many calls need a token at once, only one token request is sent.
  - A 401 from an inquiry clears the cached token, fetches a new one once, and retries the call.
- **Errors:**
  - `invalid_client` → not retried. The waiting inquiries become `FAILED` and ops gets an alert.
  - 5xx, a timeout or a dropped connection → 2 retries with backoff, then `FAILED`.
- **Secrets:** `client_id` and `client_secret` come only from the environment or the secret store (D13). The values shared during the design review should be **rotated** before go-live.
- **Mock personas:**
  - `test-client`: success
  - `test-short-ttl`: `expires_in: 60`
  - wrong credentials: `invalid_client`
  - `503`, then `200`
  - a slow answer and a malformed body
  - all the inquiry stubs require `Bearer mock-gw-…` and return 401 without it

### 5.3 Inquiry catalogue (from the bank's service documents)

**Shared rules for every inquiry**
- The fixed request fields (`ChanelCode`, `requestType`, `personType`, …) are **configurable** per inquiry. `branch-code: REQUEST` means "use the request's branch".
- Values are checked at startup, so a missing setting stops the app with a clear error.
- Any HTTP 4xx, 5xx, timeout, broken JSON or unknown value counts as `FAILED`. A 400 also raises an alert to the developers, since it means we sent something wrong.
- The **birth date** always comes from the Sabte Ahval result, formatted per service. One `JalaliDateFormatter` produces every format.

| # | Inquiry | Endpoint (POST, gateway token) | Request | `PASSED` | `NEGATIVE` (SRS message) | `FAILED` (beyond the shared rules) |
|---|---|---|---|---|---|---|
| 1 | **Sabte Ahval** (stage 1) | `apigw3p.int.sb24.com/ApiContainer.SBCivil.SabtREST/api/Civil/GetEstelamSabt` | `sabtParam {nationalCode, birthDate "yyyyMMdd", requestType}` + `clientTrackingParam {userName, ip, branchCode, chanelCode}` (all configurable) | `serviceStatus.statusField "200"` and `isaliveCode 0` | `isaliveCode 1` → «…به دلیل عدم امکان ارائه خدمات بانکی به شخص فوت‌شده…»; `statusField "300"` (`err.record.not.found`) → «اطلاعات هویتی مشتری صحیح نمی‌باشد» | `hasError: true` (any `errorCd`); any other `statusField` or `isaliveCode`. **Don't rely on `hasError`**: a mismatch still comes back with `hasError: false` |
| 2 | **Shahkar** | `apigw.int.sb24.com/ApiContainer.SBShahkar/api/ShahkarWrap/ServiceIdMatching` | `serviceNumber` (mobile), `identificationNo` (national code), `identificationType "0"`, `serviceType "2"`, `ClientTrackingparam {UserName, Ip, BranchCode, ChanelCode}` (**PascalCase**; type fields are strings) | `response 200` | `response 600` (`NotIdentifiedException`) → «…به دلیل عدم تطابق شماره تلفن همراه با کدملی… موبایلت ← پروفایل ← تنظیمات ← تغییر شماره تلفن همراه» | `response 311` (invalid national code) → alert. **`id` is always "200"; use `response`** |
| 3 | **Sepad** (men 18–50) | `apigw.int.sb24.com/ApiContainer.SBSepad.RCL1/api/Sepad/GetEstelam` | `nationalId`, `birthDate "yyyy/MM/dd"` (slashes) | `result "1"` (مجاز), **or** `result "0"` with «مشمولی با اطلاعات وارد شده یافت نشد» | `result "0"` with «غیرمجاز» → «…به دلیل عدم احراز وضعیت مجاز نظام وظیفه…» | `result "0"` with any other text; `errorCode`, `errorMessage` or `errorResult` set. The text is normalised (Arabic `ي`/`ك` → `ی`/`ک`, ZWNJ, spaces, a trailing `.`), and the known texts are in config. `expirationDate` is ignored |
| 4 | **Blacklist** | `apigw.int.sb24.com/ApiContainer.SBCustomerGroup/api/SBGetCstBlackList/GetCstBlackList` | `nationalKey` | `result: false` | `result: true` → «…به دلیل محدودیت‌های بانک مرکزی… لطفاً به آژانس هواپیمایی خود مراجعه کنید.» | `result` missing. **Inverted:** mapped to a Java field `onBlacklist` |
| 5 | **SamaCheque** (bounced cheques) | `apigw.int.sb24.com/ApiContainer.SBSamaCheque/api/CbiSamaCheque/GetBouncedChequesByPersonInfo` | `personInfo {nationalId, personType 1}`, `requestInfo "1"` | `isValid: true`, `cheques: []` | any cheque → «…به دلیل دارا بودن چک برگشتی…» | `isValid: false` (`chq402` national code → alert; `chq453` personType → alert; others); `cheques: null`. The cheque details are saved for the branch, and `serial` is trimmed |
| 6 | **Samat** (non-current loans) | `apigw.int.sb24.com/ApiContainer.SBSamatWrp/api/CbiSamat/GetEstelamAsli2` | `cstmrShenase` (national code), `cstmrType "1"`, `estlmId "1"` (all strings) | Σ over all loans, at every bank, of (`amSarResid` + `amMoavagh` + `amMashkuk`) **= 0**, including when there are no loans | that sum **> 0** → «…به دلیل دارا بودن بدهی (تسهیلات) غیرجاری…» | `hasError: true` (`errorCd "11"` national code → alert); `returnValue: null`. Amounts are `BigDecimal`. `amSukht`, `dasteBandi` and `badHesabiDate` are stored but don't affect the result |
| 7 | **BoardRelation** (credit limits) | `apigw.int.sb24.com/ApiContainer.SBCBIBoardRelation/api/SBQueryLimit/QueryLimit` | `amount` (the guarantee amount, in rials), `customerType 1`, `nationalIdOrLegalId`, `birthDateOrRegisterDate "yyyyMMdd"` | `result: true` | `result: false` → «…به دلیل محدودیت‌های بانک مرکزی… لطفاً به آژانس هواپیمایی خود مراجعه کنید.» The service's `description` (which limit was broken) is saved for the branch | `result` missing; HTTP 400 → alert |
| 8 | **ImportCommitment** (varedati) | `apigw.int.sb24.com/ApiContainer.SBRafTaahodArzi.RCL1/api/RafeTaahodArzi/GetTaahodArziRafNashodeh` | header `ChanelCode: 15`; body `shenasehMelliOrShomarehKartBazargani` (the 10-digit national code) | `returnData` has items, all with `izMojaz: true` | any `izMojaz: false` → «…به دلیل محدودیت‌های بانک مرکزی… لطفاً به آژانس هواپیمایی خود مراجعه کنید.» | `returnData` empty or `null`; HTTP 404. The percentage and amount strings are parsed to `BigDecimal` and saved |
| 9 | **ExportCommitment** | **GET** `apigw.int.sb24.com/ApiContainer.SBICEPbn/api/Status/{nationalCode}` | the national code **in the URL path**, with no body | `commitment_creation: true`, **or** HTTP 404 with `detail` «No MerchantCommitment matches the given query.» (not an exporter) | `commitment_creation: false` → «…به دلیل محدودیت‌های بانک مرکزی… لطفاً به آژانس هواپیمایی خود مراجعه کنید.» | HTTP 404 with **any other** `detail` (a wrong URL must never approve customers; the text is kept in config) → alert; `commitment_creation` missing. The other fields are saved for the branch. **The opposite of ImportCommitment**, where "no data" is `FAILED` |

- **Customer messages:** the SRS texts are shortened above («…»). The full texts go in `messages_fa.properties`, and the SRS typo «شماه» is corrected to «شماره».
- **Contract docs and stubs:** each inquiry gets a `docs/integrations/<inquiry>.md`, WireMock stubs for every row above, and test personas (§5).
- **The bank's samples contain real-looking personal data and tokens.** None of it goes into the repo; the stubs use synthetic personas only.

### 5.4 Yaghut (D35)

**The four calls.** Each is a REST call to `{yaghut-base-url}/…`. The request body is only the request bean; the `context` (arg0) fields are ignored.

| Call | Path | Request | Response used | Faults |
|---|---|---|---|---|
| `loginStatic` | `/user/loginStatic` | `username`, `password` | **`JSESSIONID` from `Set-Cookie`**, `sessionExpirationDate`, `cif` (identifies an **agency**), `code` (national code; for a customer it must equal the request's national code), `name`, `temporarySessionId`/`temporaryCause`, `passwordExpirationBean` | `InvalidCredentialsWSException`, `SuspectedCustomerWSException` |
| `branches` | `/branches` | `offset`, `length` (paging) | `totalRecord`, `branches[] {branchCode, name, city, address, tel, fax, zipCode}` | — |
| `customerDetailInfo` | `/customerDetailInfo` | **exactly one** of `cif` or `code`. We send `code`, the national code | the snapshot fields (§6.1) and `personalityType`. **Only `ACTUAL` customers are accepted**; foreign nationals with a valid national code are allowed | `InvalidCustomerWSException` |
| `deposit` | `/deposit` | `cif`, `depositStatus: OPEN`, `personality: [ACTUAL]`, `signatureStatus: [OWNER_OF_DEPOSIT_AND_SIGNATURE]`, `includeCurrency: [rial]`, `needWithdrawableBalance: true`, `includeCreditAccount: false`, `includeSupportAccount: false` | `depositNumber`, `group`, `depositType`, `availableBalance`, `owner`, `depositStatus`, `currency` | none. An invalid or unrelated deposit just gives an **empty list** |

**Whose session is used:**

| Use | Session |
|---|---|
| `BranchSyncJob` (`branches`), nightly | **Asan Zeman technical account** |
| `deposit` re-check at branch approval (D16) | **Asan Zeman technical account** |
| `customerDetailInfo` at agency registration | **the agency user's own login session** |
| `deposit` during customer completion | **the customer's own login session** |

**Rule:** in the agency and customer panels, every Yaghut call uses that user's own login session. Only the branch side and the nightly job use the technical account.

**`YaghutSessionManager`**
- **Reading the cookie:** after `loginStatic`, **all** `Set-Cookie` headers are searched for **`JSESSIONID`** (it may not be the first), and each part is trimmed. Every later call sends only `Cookie: JSESSIONID=…`. The body's `sessionId` and any other cookies are ignored.
  - A login that returns no `JSESSIONID` is an **error**. The old session is never reused instead.
- **Reuse:** the session is reused until it's **null**, its `sessionExpirationDate` passes, or Yaghut answers **`InvalidSessionWSException`**. There's no early refresh.
- **The technical account** has one session per app instance, kept in memory.
  - A `ReentrantLock` (rather than `synchronized`, which suits virtual threads) means that when several callers need a login at the same moment, only one `loginStatic` is sent and the others reuse its result.
  - On `InvalidSessionWSException` it drops the session, logs in again once and retries the call.
  - `InvalidCredentialsWSException` on this account is a **configuration error**: ops gets an alert, and the call **isn't retried**, so the account doesn't get locked.
- **Agency and customer sessions:** the `JSESSIONID` and `sessionExpirationDate` are kept server-side with the user's Asan Zeman session, so the panels never see the cookie (D7). Their password is never stored, so these sessions **can't be renewed by themselves**. On `InvalidSessionWSException`, or once the expiry has passed, the Asan Zeman session ends with «نشست شما در یاقوت منقضی شده است. لطفاً دوباره وارد شوید.»
  - **The agency's Asan Zeman session is capped at the earlier of 8 hours and Yaghut's `sessionExpirationDate`**, so it ends before a Yaghut call can fail.

**Login checks after `loginStatic`** (agency and customer):

| Condition | Result |
|---|---|
| `temporarySessionId` is set (any `temporaryCause`: `TICKET_LOGIN`, `DIGITAL_SIGNATURE_LOGIN`, `OTHER`) | refused: «لطفاً از طریق اینترنت‌بانک وارد شوید.» |
| the password has expired (`passwordExpirationBean`) | refused: «رمز عبور شما منقضی شده است. لطفاً ابتدا رمز خود را در اینترنت‌بانک تغییر دهید.» |
| `SuspectedCustomerWSException` | refused: «امکان ارائه خدمت به شما از این مسیر وجود ندارد. لطفاً به شعبه مراجعه کنید.» |
| **agency:** no `agency` row with this `cif` | refused (the agency isn't registered in Asan Zeman) |
| **customer:** `code` isn't the request's national code | refused |

**Faults** come back as JSON:

```json
{ "exception": "InvalidSessionWSException", "exception_params": "{}", "success": false, "exception_message": "…" }
```

- The error handler parses the body and uses the **`exception` field** as the fault name.
- **Any response with `success: false`** is a fault, whatever its HTTP status.
- A body that isn't this JSON (for example a proxy's HTML page) counts as technical.
- `exception_message` is logged in full, but isn't used for decisions.

| Fault or condition | Result |
|---|---|
| `InvalidCredentialsWSException` (agency or customer login) | login refused: «نام کاربری یا رمز عبور اشتباه است.» |
| `InvalidCredentialsWSException` (technical account) | configuration error: ops alert and "try again later" |
| `SuspectedCustomerWSException` | login refused: «امکان ارائه خدمت به شما از این مسیر وجود ندارد. لطفاً به شعبه مراجعه کنید.» |
| `InvalidCustomerWSException` | agency registration refused: «مشتری با این کد ملی در بانک یافت نشد.» |
| **`InvalidSessionWSException`** | technical account: log in again once and retry. Agency or customer: end the Asan Zeman session and ask them to log in again |
| **`ServiceAccessIsDeniedWSException`** ("Principal is not active for Service Id … Channel Id …") | **no retry**, and an ops alert that includes the service and channel IDs from `exception_message`. Technical account → "try again later". Agency or customer → «دسترسی شما به این سرویس یاقوت فعال نیست. لطفاً با راهبر سامانه تماس بگیرید.» |
| `WebserviceException`, an unknown fault, or no body | technical: "try again later" (503) |
| timeout, connection error, 5xx | technical: "try again later" (503). **Never** reported as a 400 |

The raw error body is always logged in full (D22), so a new fault name shows up in the logs and can be added to the list.

**Example settings** (`application.properties`, D36):

```properties
azs.integration.yaghut.base-url=CHANGE_ME
azs.integration.yaghut.technical-user.username=CHANGE_ME
azs.integration.yaghut.technical-user.password=CHANGE_ME
azs.integration.yaghut.deposit.include-currency=IRR
```

**Decided on 1405/08/22:**
- the session-expired fault is `InvalidSessionWSException`
- only `JSESSIONID` is used
- temporary sessions, expired passwords and suspected customers are refused
- agencies are identified by `cif`
- only `ACTUAL` customers are accepted
- `branchCode` is **always a string**. personnelInfo's numeric `abrnchcod` is converted with `String.valueOf` and compared exactly, with no zero-stripping. The branch integrity check logs any Yaghut code starting with `0`, so a mismatch would show up in UAT
- the agency session is capped at Yaghut's expiry

**⏳ Still open for Yaghut (deferred by the product owner):**
- the **deposit details**: Win `depositType` codes, whether `SPECIAL_LONG_ACCOUNT` is expensive or not eligible, the rial currency code, and the `depositOwnerTypeWS` values

---

## 6. Security plan (§6)

- **JWT:** RS256. Claims: `sub`, `role`, `branch_code` / `agency_id`, `iat` and `exp`. Revocation through `jwt_blacklist`.
- **Session lifetime (D5):** access token **15 min** for everyone. Refresh token **8 h** for branch, admin and agency users. **Customers** get a single **30-minute** session with no refresh token, enough to complete one request. All configurable.
- **Token storage in the browser (D7). No cookies are used anywhere.**
  - Every call sends `Authorization: Bearer <access token>`. The browser never attaches a credential by itself, so there's no CSRF exposure and no CSRF tokens. This replaces the CSRF and SameSite parts of §6-5.
  - **Access token:** in memory only (an Angular service), never written to storage.
  - **Refresh token, for staff and agency users:** in `sessionStorage`. It belongs to one tab, isn't shared with other tabs, and is wiped when the tab closes, so it survives an F5 but not closing the browser. After a reload, the panel calls `POST /api/auth/refresh` with the refresh token in the request body and gets a new pair.
  - **Rotation:** every refresh returns a **new** refresh token and invalidates the old one. Only a hash of each token is stored, in `refresh_tokens`, grouped by a `family_id` per login.
  - **Reuse detection:** if an already-used refresh token is presented again, the **whole family is revoked**, the user has to log in again, and a security audit event is written.
  - **Customers:** the 30-minute session token stays in memory only, with no refresh token. A reload means logging in again with OTP, which is acceptable for a one-off visit.
  - **XSS defences, since storage is readable by scripts:** a strict CSP (`script-src 'self'`, no inline scripts and no third-party scripts), Angular's built-in sanitising with no `bypassSecurityTrust*` calls (checked by a lint rule), and `sessionStorage` cleared on logout.
- **Secrets (D13, D36):** the JWT private key, database password, gateway `client_id`/`client_secret`, Notifier `user`/`pass` and the Yaghut technical account all live in the single `application.properties`. The repo copy holds only `CHANGE_ME` placeholders. The real values exist only in each server's copy, readable by ops only (`chmod 600`), and the app refuses to start while any secret is still `CHANGE_ME`.
- **Authorization:** `@PreAuthorize` on every endpoint. Roles are `BRANCH`, `ADMIN`, `AGENCY` and `CUSTOMER`. Every query is also scoped by ownership: an agency only sees its own requests, a branch only its own branch, and a customer only their own requests.
- **OTP:** generated and checked by the Notifier (§6.3, D34). After OTP succeeds, the national code returned by Yaghut must match the request's customer.
- **Data protection:** encryption at rest via TDE, provided by the DBA, and TLS 1.2+ with HSTS. Customer data is stored in plain text (D21), and **nothing is masked** (D22).
- **Audit:** an append-only `audit_log` table in `AZS_AUDIT`, partitioned monthly and kept 7 years. The app's DB user has `INSERT`-only grants on it. It is written for every state change and every admin edit, with old/new JSON, IP and user agent.
- **Abuse protection:** **no rate limiting** (D23). Every login is checked with ARcaptcha **first**, failing closed (§6.5). OTPs lock out after 3 wrong attempts. CSP headers are set, and Angular's auto-escaping is relied on.
- **TLS (D38):** certificates are verified by default. `azs.integration.<service>.tls.skip-verify=true` switches checks off for **that client only**, with a startup WARN. An optional truststore is configurable. TLS 1.2/1.3 only. The JVM-wide defaults are never changed.
- **Idempotency:** every POST/PUT requires an `Idempotency-Key`. Keys and their stored responses are kept in `idempotency_keys` for 24 h.

### 6.1 Customer data and the mobile number (D2 revised, D21, D22)

- **When:** as the agency registers a request, Yaghut `customerDetailInfo` is called once, with the agency user's session (D35).
- **What is saved**, in plain text, in the request's customer record: `cif`, `code` (national code), first, last and full name, `fatherName`, `birthDate`, `mobile`, `ssn`, the certificate serial and series, `personalityType`, and `gender`. Gender is stored, but not used (D26).
- **How long:** that snapshot is used for the **whole life of the request**. It is **not** refreshed later from Yaghut or `loginStatic`.

| Where it is needed | Where it comes from |
|---|---|
| Sending the login OTP | the saved mobile number |
| Shahkar inquiry (`serviceNumber`) | the saved mobile number |
| Notification SMS (issued, rejected, cancelled, revoked, auto-rejected) | the saved mobile number. The outbox row may hold it directly |
| Display in the panels and Excel exports | the saved data, shown in full (D22) |
| **Gender and birth date for the inquiries** | **the Sabte Ahval result**, not the snapshot, because Yaghut's gender is unreliable (D26) |

- **OTP stays fast and still uses the outbox**, as the architecture document requires. It's written with priority `HIGH`, and the dispatcher is woken immediately after commit. The OTP has a short TTL, so it is never retried after it expires.
- **Risk accepted by the product owner:** the architecture document (§6-3-1) asked for the mobile number to be protected. With plain-text storage and no masking, anyone with read access to the database, a backup, the logs or an export can see customers' mobile numbers and identity details. TDE still protects disks and backups at rest. Masking can be switched back on with `azs.privacy.masking-enabled` (D22).

### 6.2 Bank user login (D33)

```
ARcaptcha → AAA GetRoleWindows (password + roles) → personnelInfo (branch) → JWT { sub, role, branch_code }
```

**AAAService: `GetRoleWindows` (D39)**
- **Endpoint:** `azs.integration.aaa.url`, default `https://biservice:8101/SB.SecrityService.DomainVer2/AuthorizationSrv.svc`. SOAP 1.1, document/literal, `SOAPAction: http://www.sb24.com/Services/Security/AuthorizationSrv/GetRoleWindows/Request`.
- **Client:** Spring Web Services (`WebServiceTemplate`), with Jakarta JAXB classes generated at build time from the full WSDL in `src/main/resources/wsdl/aaa/`. The old Axis2 stub isn't reused.
- **Security header:** `Wss4jSecurityInterceptor` adds `wsse:Security` (`mustUnderstand="0"`) with a `UsernameToken` (`PasswordText`) for the **AAA technical account**. No timestamp, matching the existing working client; `azs.integration.aaa.wss.timestamp` can add one if AAA starts requiring it.
- **Request:** `storageparam { ApplicationName, StoreName }` from config, plus `param { UserName, Password }`, which are what the branch user typed.
- **Response:** `GetRoleWindowsResult { DetailError[] {ErrorCode, ErrorDesc, InnerException}, UserRoles[] }`.

| AAA result | Login |
|---|---|
| `DetailError` has entries | refused (401): «نام کاربری یا رمز عبور اشتباه است.». The code and description are logged in full |
| no `UserRoles` value maps (`Branch` → BRANCH, `Admin` → ADMIN, both configurable; any other role is ignored) | refused: «شما مجوز دسترسی به سامانه آسان ضمان را ندارید.» |
| SOAP fault, timeout, connection error | technical: "try again later" (503) |

- **Response headers:** the old `CustomHandler` cleared `mustUnderstand` on response headers for Axis2. The Spring WS client doesn't reject unprocessed response headers, so it shouldn't be needed. If UAT shows otherwise, an equivalent client interceptor will be added.

**personnelInfo**
- **Request:** `POST https://apigw.int.sb24.com/ApiContainer.GetOverallPersonelInfo/api/GetOverallPersonelInfo/GetOverallPersonelInfo`, with the gateway token.
  - The body is `{ "cfcifNO": 0, "accountName": "<username>", "personelCode": 0 }`.
  - `accountName` is the username typed at login, with any `@sb24.ir` suffix removed (the suffix is configurable).
- **The user's branch is `userInfo.abrnchcod`.** `abrnchcodmain` and `prAcvBranch` are ignored and not saved.
  - `prsnCode`, `personelUserName`, `aBranchName`, `position` and `job` are saved in `app_user` for the audit log.
- **When it's fetched:** at **every login**. A user moved to another branch gets the new one at their next login, and the branch stays fixed for the 8-hour session.
- **No check that the branch is in `branch`.** A head-office user, for example, can log in but will see no requests.
- **Errors:**
  - HTTP 404 «اطلاعات یافت نشد» → login refused with «اطلاعات پرسنلی شما یافت نشد. لطفاً با راهبر سامانه تماس بگیرید.»
  - 5xx, a timeout or a broken response → login fails with "try again later". Nobody is let in without a branch.
- **Display name** (for example the lock holder in the inbox): the `accountName`.

### 6.3 Customer OTP through the Notifier (D34)

The Notifier is called **directly** at an internal address (`https://10.0.41.24:8012` by default), with no gateway token. Host, port and paths are configured per environment.

| Call | Body | Success | Errors |
|---|---|---|---|
| `POST /api/v1/Sms/SendOtp` | `user`, `pass`, `number` (`09xxxxxxxxx`, as stored), `ClientName` (`AZS`), `args` (default `["آسان ضمان"]`, configurable; the SMS centre's template inserts the 4-digit code itself), `lang: "fa"` | `retval[] { number, smsId, sId }` | **403** «please contact the SamanBank Sms Center support» → a configuration error with an alert; **500**, a timeout or a broken response → "try again later" |
| `POST /api/v1/Sms/OtpValidate` | the same identification fields, plus `OtpCode` (what the customer typed) and `OtpId` (the `sId`) | `{"isValid": true}` | **410** or `{"isValid": false}` → the code is wrong or expired |

- **The code** is 4 digits, generated and checked by the Notifier. **Asan Zeman never sees or stores it.**
- **`SendOtp` is synchronous**, not sent through the outbox, because the `sId` is needed immediately and the customer is waiting. The outbox stays for notification SMS.
- **`otp_challenge` table:** `id`, customer and request, `otp_id` (the `sId`), `sms_id`, `sent_at`, `status` (`SENT`, `VERIFIED`, `EXPIRED`), plus the customer's attempt counter and `locked_until`.
- **Expiry:** decided on our side with `azs.otp.validity` (default 120 s, which should match the Notifier's own setting). After that time the Notifier isn't called, and the customer sees «کد تأیید منقضی شده است. لطفاً کد جدید دریافت کنید.» Within the time, 410 or `isValid: false` → «کد تأیید وارد شده صحیح نیست.»
- **Attempt limit:** the Notifier has none, so Asan Zeman enforces it. **3 wrong attempts per customer**, counted across codes so that a new code doesn't reset the count, → a **15-minute lockout**. Both values are configurable.
- **One use per code:** after `isValid: true`, the challenge becomes `VERIFIED`, and its `sId` can't be used again.
- **Credentials:** `ClientName`, `user` and `pass` are issued by the SMS centre for Asan Zeman and come only from the secret store (D13).
- **Mock personas:** `SendOtp` returns a fixed `sId`; the code `1234` is valid and anything else gets 410; specific mobile numbers trigger 403, 500 and a slow answer.

### 6.4 Notification SMS through `SendSimpleSms` (D40)

**The call:** `POST {notifier}/api/v1/sms/SendSimpleSms`. Note the lowercase `sms`, which is kept exactly.

```json
{ "user": "…", "pass": "…", "ClientName": "…", "numbers": ["0989xxxxxxxxx"], "message": "…full text…" }
```

- **Number:** the mobile saved at registration (D21), converted from `09xxxxxxxxx` to **`0989xxxxxxxxx`**, which means dropping the leading `0` and adding `098`.
- **Response:** `retval[] { number, smsId, sId: null }`. The `smsId` is saved on the outbox row, for tracing with the SMS centre.
- **One recipient per outbox row.** The row is written in the **same transaction** as the status change; the worker sends it with retries.

| Error | Handling |
|---|---|
| **400** «'Request.numbers' is not in the correct format.» | **no retry**. The row goes to the dead-letter list, with an alert |
| **403** «please contact the SamanBank Sms Center support» | **ops alert**. The row stays pending with a long backoff, so it's sent once the credentials are fixed |
| **500**, timeout, connection error | retry with backoff |

**The 8 SMS events.** All texts are templates in the message config, filled with `{customerName}`, `{agencyName}`, `{branchName}` and `{sentDate}` (Jalali `yyyy/mm/dd`). They all go to the customer.

| # | Event | Source | Text |
|---|---|---|---|
| 1 | Agency registers a request | SRS | «مشتری گرامی! به‌منظور تکمیل فرم درخواست صدور ضمانت‌نامه بازگشت از سفر برای آژانس هواپیمایی {agencyName} به لینک زیر مراجعه کنید. www.asanzeman.sb24.com» |
| 2 | Guarantee issued | SRS | «بانک سامان / مشتری گرامی / {salutation} {customerName} / ضمانت‌نامه «بازگشت از سفر» به ذی‌نفعی آژانس هواپیمایی {agencyName} در شعبه {branchName} بانک صادر شده است. / {sentDate}» |
| 3 | Issuance rejected by the branch | SRS | «… درخواست صدور ضمانت‌نامه «بازگشت از سفر» به ذی‌نفعی آژانس هواپیمایی {agencyName} در شعبه {branchName} بانک مورد تایید قرار نگرفت. / {sentDate}» |
| 4 | Guarantee revoked | SRS | «… ضمانت‌نامه «بازگشت از سفر» به ذی‌نفعی آژانس هواپیمایی {agencyName} در شعبه {branchName} بانک ابطال شده است. / {sentDate}» |
| 5 | Revocation rejected | SRS | «… درخواست ابطال ضمانت‌نامه «بازگشت از سفر» به ذی‌نفعی آژانس هواپیمایی {agencyName} در شعبه {branchName} بانک مورد تایید قرار نگرفت. / {sentDate}» |
| 6 | Cancelled by the agency (`CANCELLED`) | agreed 1405/08/23 | «… درخواست صدور ضمانت‌نامه «بازگشت از سفر» به ذی‌نفعی آژانس هواپیمایی {agencyName} به درخواست آژانس لغو شد. / {sentDate}» |
| 7 | Auto-rejected after 7 days | agreed 1405/08/23 | «… درخواست صدور ضمانت‌نامه «بازگشت از سفر» به ذی‌نفعی آژانس هواپیمایی {agencyName} به دلیل عدم تکمیل در مهلت ۷ روزه رد شد. / {sentDate}» |
| 8 | Rejected by an inquiry | agreed 1405/08/23 | «… درخواست صدور ضمانت‌نامه «بازگشت از سفر» به ذی‌نفعی آژانس هواپیمایی {agencyName} به دلیل عدم احراز شرایط، امکان‌پذیر نیست. / {sentDate}» |

- **`{salutation}`** is «آقا» or «خانم», from the **Sabte Ahval gender** (D26). When the gender isn't known (events 1, 6, 7, and 8 if Sabte Ahval didn't pass), the salutation and name line are left out, and the SMS starts with «مشتری گرامی».
- **The login code** is sent with `SendOtp`, not `SendSimpleSms` (§6.3).

### 6.5 ARcaptcha (D37)

- **Panels:** the widget runs in the panel (deferred, D31). The panel sends the one-time token as `arcaptchaToken` in the JSON login body, with no cookies (D7).
- **Order:** the backend verifies the token **first**, before AAA or Yaghut, so bots can't use the login to guess passwords or lock accounts.
- **Call:** `POST https://challenge.sb24.ir/api/arcaptcha/api/verify`, with `Content-Type: application/json` and the body `{ "challenge_id": <token>, "site_key": …, "secret_key": … }`.
  - The response is `{ "success": true|false, "error-codes": [...] }`.
  - `site_key` and `secret_key` are **per panel** (`azs.integration.arcaptcha.bank.*`, `.agency.*`, `.customer.*`). If the bank issues one shared key, the same values are simply entered three times.

| Result | Login |
|---|---|
| `success: true` | continue |
| `timeout-or-duplicate`, `invalid-input-response`, `missing-input-response`, or `success: false` with no known code | 400: «لطفاً کپچا را دوباره حل کنید.» |
| `missing-input-sitekey`, `missing-input-secret`, `invalid-input-sitekey`, `invalid-input-secret`, `bad-request` | configuration error: ops alert, and "try again later" to the user |
| timeout, network error, 5xx | **login refused** (fail closed): "try again later" |

- **One use per token:** never cached or reused.
- **Timeouts:** 2 s to connect, 5 s to read.
- **TLS:** `challenge.sb24.ir` may use a self-signed certificate. See D38.

---

## 7. Delivery phases

Each phase ends with a demoable result and its own exit criteria. External services are mocked until the bank grants access.

### Phase 0 — Foundation
- Repo skeleton (layout in §3): **Maven backend only** (Java 25, Spring Boot 4.1.x, Spring Modulith 2.1.x, `spring.threads.virtual.enabled=true`, **`--enable-preview`** in the compiler, the tests and the runtime for D25). All versions pinned (D9, D24). There's **no frontend workspace** (D31).
- `docker-compose` with Oracle XE (gvenzl/oracle-xe) and mock external services.
- Flyway baseline (D20): `spring-boot-starter-flyway` plus `flyway-database-oracle`. Migrations in `db/migration`, named `V001__create_schemas.sql`, `V002__…` and so on. Flyway manages all 5 schemas, with its history table in `AZS_BUSINESS`. The first migrations create `BaseEntity` columns and the reference tables (`branch`, `agency`, `rejection_reason`). Schema users and grants that need DBA rights go in a separate `db/dba/` script that the DBA runs, not Flyway.
- Cross-cutting pieces: error model (RFC 7807), correlation ID filter, structured JSON logging, Actuator/Micrometer, OpenTelemetry, idempotency filter, audit writer, Spring Modulith/ArchUnit boundary test.
- CI on **GitHub Actions** (D8): build → unit tests (JaCoCo ≥ 80 %) → integration tests (Testcontainers Oracle) → SonarQube/OWASP Dependency-Check → Docker image build → Trivy image scan. The stages mirror §8-2 so they port to GitLab CI.
- First draft of `docs/api/openapi.yaml`.

**Exit:** `docker compose up` gives a running core plus Oracle, CI is green, and the health endpoint responds.

### Phase 1 — Auth module
- ARcaptcha verification for every login (§6.5).
- Bank login (§6.2): ARcaptcha → AAA `GetRoleWindows` (Spring WS + WS-Security `UsernameToken`, JAXB from the WSDL) → role mapping → personnelInfo (`abrnchcod`) → JWT with role and `branch_code`.
- The per-service TLS settings and optional truststore (D38).
- Agency login: ARcaptcha → Yaghut `loginStatic` (the agency user's own credentials; the `JSESSIONID` is kept server-side) → login checks (§5.4) → agency found by **`cif`** → JWT, with the session capped at Yaghut's expiry.
- `YaghutSessionManager`: the technical account, `JSESSIONID` handling, `InvalidSessionWSException` retries, and JSON fault mapping (§5.4).
- Customer login (§6.3): ARcaptcha → `loginStatic` (the session is kept for `deposit`; `code` must equal the request's national code) → `SendOtp` → `OtpValidate` → JWT, with `otp_challenge`, the validity check, the per-customer lockout and one use per code.
- Gateway token provider (§5.2), shared by personnelInfo and the inquiries.
- Refresh with rotation and reuse detection, logout, and the JWT blacklist.
- `@IranianNationalCode` and the `NationalCode` class (§4.5).

**Exit:** all three login flows work **through the API** against the mocks. The OTP lockout, expiry and one-use rules, and the national-code rules, are covered by tests.

### Phase 2 — Request registration (agency side)
- Agency registers an issuance request: customer lookup with `customerDetailInfo` (the agency's session), whose **snapshot is saved for the life of the request** (D21); branch selection from the synced `branch` table (open branches only, §4.4); amount; expiry date (informational only, D19); and companions (≤ 5).
- Tracking-code generator (D12) and the `Request` aggregate with the state machine and domain events.
- SMS notifying the customer, through the outbox (pulled forward from Phase 6 in its minimal form).

**Exit:** a request registered through the agency API can be fetched through the customer API.

### Phase 3 — Customer completion: inquiries and collateral (UC-09)
- InquiryService as designed in §5.1: background runs, the **two-stage chain** (Sabte Ahval, then 8 in parallel, D26) on virtual threads, stage deadlines (D27), `PASSED`/`NEGATIVE`/`FAILED`/`SKIPPED`, «تلاش مجدد» with cooldown and result reuse, and per-attempt history.
- The 9 inquiry clients with their WireMock stubs and personas, following the catalogue in §5.3.
- Filter eligible deposits from Yaghut `deposit` (the customer's session; individual, owner and signatory, open, rial) and classify them as cheap or expensive by `group` and `depositType` (§4.3).
- CollateralService: Cash, cheap-pledge and expensive-pledge strategies (§4.3). The SRS worked examples become unit tests (D14, D15, D18).
- API endpoints for each step: review the request → run the inquiries → choose collateral → submit.

**Exit:** the full completion flow works with the mocks, and the UC-09 P95 is ≤ 15 s with injected latency.

### Phase 4 — Branch review and manual issuance
- `BranchSyncJob`, the integrity check, and the `set_branch_replacement.sql` template (§4.4). `branch` must be populated before requests can be registered, so the sync job can be pulled forward into Phase 2.
- Branch inbox and the `can_act` rule (§4.4): the original branch **or** its replacement, enforced by the API.
- Ownership lock as designed in §4.1: «اقدام», approve, reject, abandon, admin force-release, and the idle auto-release job for `UNDER_REVIEW`.
- Re-check balances at approval (D16).
- `UNDER_ACTION` API: guarantee-number entry with format validation, and «انصراف از درخواست».
- The inbox API returns locked requests with the holder's `accountName`.

**Exit:** all the §4.1 lock tests pass, including concurrent «اقدام» clicks where exactly one wins. The full issuance path reaches `ISSUED` with an SMS sent.

### Phase 5 — Cancellation and revocation (§4.2)
- One agency action, with the backend choosing the outcome under the row lock:
  - **before «اقدام»** → `CANCELLED` (لغو) immediately
  - **from «اقدام» onward** → a revocation request (ابطال)
- Revocation request flow: branch inbox → «اقدام» → approve or reject (using the revocation rejection reasons) → `UNDER_ACTION` → manual revocation in Farangin → confirm. The issuance request becomes `REVOKED`.
- Revocation of a request that is mid-work goes straight to the lock holder (D17).
- Agency confirmation message chosen by status. SMS notifications. `CANCELLED` and `REVOKED` both appear in tracking and reports.

**Exit:** every test listed in §4.2 passes, and every step is recorded in the audit log.

### Phase 6 — Notification module (complete)
- Outbox worker calling **`SendSimpleSms`** (§6.4), with retries and backoff, dead-lettering on 400, and a long backoff plus an alert on 403. `smsId` is stored.
- The 8 SMS templates (§6.4), with the salutation from Sabte Ahval's gender.
- The recipient's number is the one saved at registration (§6.1, D21), sent as `0989xxxxxxxxx`.

**Exit:** with the SMS mock down for 5 minutes, no messages are lost and all of them are delivered after it recovers.

### Phase 7 — Tracking and reporting
- Agency tracking: filters, pagination and Excel export.
- Bank reports: advanced filters and SXSSF streaming export. Reports use a read-only datasource that can point at the Data Guard standby.
- Admin API: CRUD for agencies, rejection reasons and other reference data, with a Caffeine cache that is invalidated on writes. **Branches are excluded:** they come only from Yaghut and database scripts (D32).

**Exit:** a 100k-row export runs in bounded memory. A 1,000-row export meets P95 ≤ 10 s.

### Phase 8 — Scheduler
- Leader election through `scheduler_lock`, shedlock style.
- Jobs: auto-reject requests left incomplete after 7 days, release idle `UNDER_REVIEW` locks (§4.1), `BranchSyncJob` and the branch integrity check (§4.4), purge old `otp_challenge` rows and blacklist entries, and maintain partitions. There is **no** expiry job for issued guarantees (D19).

**Exit:** with 2 instances running, each job runs exactly once.

### Phase 9 — Hardening and performance
- Load tests (k6 or Gatling) against the SLAs in §9-1. HikariCP tuning, checks on the Oracle execution plans, and Bitmap and partitioning decisions.
- Security review: OWASP ASVS checklist, penetration test findings, CSP and HSTS verification.
- Grafana dashboards: requests per state, UC-09 duration and OTPs sent.

**Exit:** all SLAs met in UAT-like conditions and no high or critical findings remain.

### Phase 10 — Deployment and go-live
- Agree the runtime with the ops team (D10): Kubernetes or Docker on VMs, with Tomcat/WAR as a fallback. Then write the manifests and the rolling or blue-green release.
- Flyway migrations go through DBA review for production. Production runs `flyway validate` before `migrate`. Backup and DR runbook (RTO 2 h, RPO 15 min). Training material for branch users on the manual steps.

**Exit:** production deployment checklist signed off.

---

## 8. Testing strategy

| Level | Tools | Focus |
|---|---|---|
| Unit | JUnit 5, AssertJ, plain Mockito `@Mock` (no Spring) | State machine transitions, collateral strategies, tracking code generation, OTP rules, each inquiry's result mapping (§5.3) |
| Module | Spring Modulith `@ApplicationModuleTest` | Each module tested on its own, events published and consumed |
| Integration | Testcontainers (Oracle XE), WireMock | Repositories, `SELECT FOR UPDATE NOWAIT` → 409, Flyway migrations from an empty schema, external client resilience |
| Client | WireMock (`wiremock-spring-boot`), **no mock beans** | Each external client against its stubs: success, every error code, timeout, 503 then success, the 401 token refresh |
| Web slice | `@WebMvcTest` + `@MockitoBean` for the service behind the controller | Validation, the 409 mapping, security rules |
| Contract | OpenAPI validation in CI | Backend matches `openapi.yaml` |
| Architecture | ArchUnit / Modulith verify | Module boundaries, layering (no business logic in controllers) |
| API end-to-end | `@SpringBootTest` + Testcontainers Oracle + WireMock | The main paths **through the REST API**: issuance, cancellation before «اقدام», revocation, closed-branch handling, tracking, report. Browser E2E (Playwright) is deferred with the UI (D31) |
| Performance | k6 or Gatling | SLA table in §9-1 |
| Security | OWASP ZAP, Dependency-Check, Trivy | Pipeline gates |

**Mocking rules.** `@MockBean` was removed in Spring Boot 4, and its replacement is `@MockitoBean`.
- Use **`@MockitoBean`** only to cut a test off at *our own* boundaries: a controller's service, or another module's API inside `@ApplicationModuleTest`.
- **Never use it for external services.** Those are always tested through WireMock, so the HTTP, serialisation, timeouts, retries and result-code mapping are exercised.
- Keep the number of distinct `@MockitoBean` combinations small, because each one forces a new Spring context and slows the suite down.

---

## 9. Definition of done (per feature)
- The OpenAPI contract is updated first, then implemented.
- Unit and integration tests are written, coverage is ≥ 80 % on changed code, and the module-boundary test passes.
- State changes are audited, and POST/PUT endpoints are idempotent.
- Metrics and logs are added. Nothing is masked (D22).
- Database changes are a new Flyway migration. Applied migrations are never edited (forward-fix only). Risky changes include a hand-written undo script in `db/undo/` for the DBA.
- Persian error and message texts are in the message config. UI review is deferred (D31).

---

## 10. Immediate next steps
1. **Still open, and deferred by the product owner:**
   - the **Yaghut deposit details** (§5.4): Win `depositType` codes, `SPECIAL_LONG_ACCOUNT`, the rial code and the `owner` values. Needed before Phase 3
   - **how Phase 0 is built and checked.** This workspace can't download packages, so either GitHub Actions does the building, or the work runs on a computer that can
2. **To get from the bank:**
   - each inquiry's gateway token **scope**
   - Asan Zeman's AAA `ApplicationName`/`StoreName` and technical account
   - the ARcaptcha site and secret keys
   - the Notifier `ClientName`, user and password
   - the bank's internal CA certificate, if certificate checks are to stay on (D38)
3. **Rotate** the gateway client secret and the Notifier credentials that were shared during the review.
4. Record the decisions that change the architecture document as ADRs in `docs/architecture/`:
   - **ADR-008:** the ownership lock (amends ADR-003)
   - **ADR-009:** لغو versus ابطال, and no expiry status
   - **ADR-010:** the version upgrade (Java 25, Boot 4.1) and virtual threads
   - **ADR-011:** plain-text customer data, no masking, no rate limiting (amends §6-3-1 and §6)
   - **ADR-012:** the two-stage inquiry chain, with `StructuredTaskScope` (preview)
   - **ADR-013:** branches from Yaghut, with `parent_branch` replacement
   - **ADR-014:** the Notifier is called directly and `SendOtp` is synchronous (amends §7-4)
   - **ADR-015:** Yaghut sessions, with a technical account plus user sessions, and one `application.properties` for all configuration and secrets
   - **ADR-016:** AAA through Spring Web Services instead of Axis2, and per-service TLS verification switches
5. **UI:** waits for the product owner's explanation (D31).
6. Start **Phase 0** (backend only) in this repository.
