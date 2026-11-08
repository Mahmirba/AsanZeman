# AsanZeman (آسان ضمان) — Implementation Plan

> Sources:
> - *AsanZeman Architecture Document v1.0* (سند معماری سامانه آسان ضمان, 1405/08/07). Section references like **§3-3-4** point to this document.
> - *SRS-AZS v1.0* (1405/07/27). References look like **SRS: customer completion, step 13**.
>
> **Precedence:** where the two documents conflict, the **architecture document wins**. The SRS is used for business rules the architecture document doesn't spell out, such as the collateral formulas, eligible deposit types and screen behaviour.

---

## 1. What we are building

A system for a bank to process **travel-return guarantees** (ضمانتنامه بازگشت از سفر), from the agency's registration through branch approval to recording the guarantee number. Cancellation, revocation and reporting are included.

| Piece | Description |
|---|---|
| **Asan Zeman Core** | One Spring Boot **modular monolith** (Java 21, Spring Boot 4.1, see D9) inside the bank intranet. It has 6 modules: Auth, Request, Inquiry, Notification, Report and Scheduler. |
| **Bank panel** | Angular SPA for branch users and admins (one app, role-based). Intranet only. Login: AAAService + ARcaptcha. |
| **Agency panel** | Angular SPA in the DMZ at `www.asanzeman.sb24.com/agencies`. Login: Yaghut `loginStatic` + ARcaptcha. |
| **Customer panel** | Angular SPA in the DMZ at `www.asanzeman.sb24.com`. Login: Yaghut `loginStatic` + ARcaptcha + SMS OTP. |
| **Oracle 19c+** | The **only** data store. There is no Redis, Kafka or object storage. Schemas: `AZS_AUTH`, `AZS_BUSINESS`, `AZS_INQUIRY`, `AZS_AUDIT`, `AZS_OUTBOX`. |
| **External** | **Called directly:** AAAService and Yaghut (4 services). **Called through the Bank API Gateway** (`apigw.int.sb24.com`, single token): the 9 inquiries, ARcaptcha and SMS Notifier. |
| **Out of scope (manual)** | Farangin (guarantee issuance and revocation) and the Digital Signature system. There is **no API integration** with either one. The branch user does this work by hand while the request is in `UNDER_ACTION`. |

### Core flows (§2-4)
1. **Issuance:** The agency registers the request. The customer completes it, which runs the 9 inquiries in parallel and covers collateral selection. The branch then locks the request and reviews it, and either approves (→ `UNDER_ACTION`) or rejects it. After approval, the branch signs and issues manually outside the system, then enters the guarantee number (→ `ISSUED`) and an SMS goes out.
2. **Cancellation or revocation by the agency.** What happens depends on how far the request has got (§4.2):
   - **Before «اقدام»** (`REGISTERED_AT_AGENCY` or `PENDING_BRANCH_REVIEW`): nothing has been done in the Digital Signature system or Farangin yet, so the request goes straight to **`CANCELLED` (لغو شده)**. No branch review is needed.
   - **From «اقدام» onward** (`UNDER_REVIEW`, `UNDER_ACTION` or `ISSUED`): something may already have been signed or issued, so a **revocation request (درخواست ابطال)** is sent to the branch. The branch reviews it, revokes manually in Farangin where needed, and confirms (→ **`REVOKED` (ابطال شده)**). The branch can also reject the revocation request.
3. **Agency tracking:** Filtered list of the agency's requests, with Excel export.
4. **Bank reporting:** Advanced filters with Excel export (SXSSF streaming).

---

## 2. Decision log

The architecture document contradicts itself, or is silent, on the points below. **All of them are now decided** (✅), following a review with the product owner on 1405/08/17. Where the product owner had no preference, the recommended default was adopted. Every value marked *configurable* lives in `application.yml` and can change without a release.

| # | Topic | Decision | Details |
|---|---|---|---|
| D1 | ✅ Locking across the manual work | **Ownership lock** (`locked_by` on the request), with `SELECT … FOR UPDATE NOWAIT` guarding each change to it. ADR-003 is amended. | §4.1 |
| D2 | ✅ Customer mobile number | **Never stored in plain text.** Fetched from Yaghut whenever it's needed. Only `mobile_hash` and `mobile_last2` are kept. | §6.1 |
| D3 | ✅ Inquiry technical failure | **Block and let the customer retry.** A *negative* answer rejects the request. A *technical* failure (timeout, 5xx, circuit open, unreadable response) blocks completion without rejecting. On retry, only the failed inquiries are re-run. A passed result is reused for **1 hour** (*configurable*). Manual-review fallback is **not** built. | §5.1 |
| D4 | ✅ Inquiry timeouts and retries | Use the **per-service table in §7-3** (8–15 s, 1–2 retries), not the flat 30 s with 3 retries. The whole run must stay within the **25 s budget** (P99, §9-1). All *configurable*. | §5, §5.1 |
| D5 | ✅ Session lifetime | Access token **15 min** for everyone. Refresh token **8 h** for branch, admin and agency users. **Customers:** a single **30-minute** session with no refresh token. All *configurable*. | §6 |
| D6 | ✅ Collateral formulas | Taken from the SRS. | §4.3 |
| D7 | ✅ Where the browser keeps tokens | **No cookies** (an infrastructure restriction). Both tokens are sent only as `Authorization: Bearer`, so **no CSRF protection is needed**. Access token **in memory**. Staff and agency refresh token in **`sessionStorage`**, with **rotation and reuse detection**. Customers: **memory only**, with no refresh token. Nothing in `localStorage`. | §6 |
| D8 | ✅ CI/CD | **GitHub Actions** while the code is on GitHub. The stages mirror §8-2, so the pipeline moves cleanly to GitLab CI later. | §7 Phase 0 |
| D9 | ✅ Versions | **Java 21 (LTS), Spring Boot 4.1.x, Spring Modulith 2.1.x, Angular 22 with Nx.** Versions are pinned in the build. This replaces Boot 3.2, Java 17 and Angular 17 from §1-4, which are past upstream support. | §3 |
| D10 | ✅ Runtime | **Build a Docker image (executable jar) now.** Keep a Tomcat/WAR profile possible. Decide Kubernetes versus VMs with the ops team before Phase 10. | §7 Phase 10 |
| D11 | ✅ Cancellation versus revocation | **Before «اقدام»:** the request goes to `CANCELLED` (لغو شده) immediately. **From «اقدام» onward**, including approved and issued requests: a revocation request goes to the branch and ends in `REVOKED` (ابطال شده). | §4.2 |
| D12 | ✅ Tracking code | 17 digits: branch (3) + agency (4) + date YYMMDD (6) + a 4-digit counter. The counter runs **per branch + agency + day**, in a `tracking_counter` table incremented under a row lock. | §4 |
| D13 | ✅ Secret store | **Not known yet.** The app reads secrets from **environment variables or mounted files**, so Vault, Kubernetes Secrets or encrypted files can be added later without code changes. Locally, a key file is used. | §6 |
| D14 | ✅ Pledge formula | **Follow the SRS worked example:** pledge = `(amount − cash) × factor`. For example, 1,000,000,000 rials with 20 % cash pledges 800,000,000 (840,000,000 on expensive deposits). The literal SRS formula, which gives 640,000,000, is treated as a typo. | §4.3 |
| D15 | ✅ Mixed cheap and expensive pledge | **Cheap deposits are filled first** (×1.00), then expensive ones (×1.05, each capped at 95 % of its balance). Within each group, deposits are filled in the order the customer selected them. | §4.3 |
| D16 | ✅ Balance changes before approval | **Balances are re-checked** through Yaghut `getDeposit` when the branch clicks approve. If the deposits no longer cover the collateral, approval is blocked and the shortfall is shown. | §4.3, Phase 4 |
| D17 | ✅ Revocation while the branch is mid-work | **Issuance actions stop**, except for recording a guarantee number that was *already* issued in Farangin. The revocation request goes **straight to the user who holds the lock**. An admin can reassign it with force-release. | §4.2 |
| D18 | ✅ Rounding | Collateral amounts are **rounded up to the whole rial** (`BigDecimal`, `CEILING`), so the bank is never under-covered. | §4.3 |
| D19 | ✅ Guarantee expiry | **There is no `EXPIRED` status, and no job changes issued guarantees.** Asan Zeman has no connection to Farangin, where the guarantee actually lives, so it can't know when one really ends. `expiry_date` is stored as information only (shown in tracking and reports, and usable as a filter). An `ISSUED` request can be revoked at any time. | §4.2 |
| D20 | ✅ Database migrations | **Flyway replaces Liquibase** (§5-4). Versioned SQL migrations, **forward-fix only**, because Flyway undo is a paid feature. A risky change gets a hand-written undo script for the DBA. | §4, Phase 0 |

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
│       └── integration/         ← AAAClient, YaghutClient, BankApiGatewayClient (+ mock impls)
│   └── src/main/resources/db/migration/   ← Flyway SQL migrations (D20)
├── frontend/                    ← Nx monorepo (ADR-007)
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

Supporting tables: `otp_codes`, `refresh_tokens`, `jwt_blacklist`, `outbox_messages`, `scheduler_lock`, `idempotency_keys`, `tracking_counter`.

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

- **Inbox (SRS):** locked requests are removed from other users' *action* lists but stay **visible read-only**, labelled "در حال بررسی / در حال اقدام — {name}". All branch users can still open the details.
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

**Deposits shown to the customer** come from Yaghut `getDeposit`, filtered in the backend. A deposit is shown only if it is:
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

> The SRS table row for cash lists fewer types than its own deposit-filter rules. This plan follows the **filter rules**, which give the full list above. The mapping from Yaghut deposit type codes to price class goes in a reference table, so it can change without a release.

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

---

## 5. Integration plan (§4-5, §7)

| Client | Target | Notes |
|---|---|---|
| `AAAClient` | AAAService (direct HTTPS) | Login for branch/admin users. Returns the user and their role (`ROLE_BRANCH` or `ROLE_ADMIN`) and the branch code. |
| `YaghutClient` | Yaghut (direct HTTPS) | Four calls: `loginStatic`, `getBranch`, `getDeposit` and `getCustomerDetailInfo`. Deposit data is **never stored**. |
| `BankApiGatewayClient` | `apigw.int.sb24.com` | One token for everything: the 9 inquiries, ARcaptcha and Notifier (`SendOTP`, `SendSimpleSms`). |

- **Anti-corruption layer:** each external service gets its own adapter that maps its format to domain types.
- **Resilience4j** (§7-6): circuit breaker opens at 50 % failures over a 20-call window and stays open 30 s. Timeouts and retries per service, from the §7-3 table (8–15 s, 1–2 retries with exponential backoff, on 5xx and timeouts only; D4). Bulkhead of 20. Rate limiter of 10 req/s. All values configurable per client.
- Every call sends an `Idempotency-Key` header and the correlation/trace ID.
- **Mocks: two layers**, built from the bank's service documents, one service at a time:
  - **WireMock server (main layer):** runs in docker-compose and answers exactly like the real service (URLs, headers, JSON or SOAP, error codes). The app's **real** client code calls it, so serialisation, auth, timeouts and retries are all exercised. It's also used in integration tests.
  - **In-app fakes:** Java implementations of the same client interfaces, under the `fake` profile, for fast unit tests and frontend work with no server.
- **What each service gets:**
  - a contract summary in `docs/integrations/<service>.md`, which includes the classification of every result code as business-negative or technical (it drives D3)
  - a client interface with DTOs and an adapter (the anti-corruption layer)
  - WireMock stubs for the happy path, each business negative, and the technical failures: delay past the timeout, 5xx, a malformed body, a dropped connection, and "fails then recovers" using WireMock scenarios
  - client tests
- **Test personas:** the national code picks the scenario (for example, `0000000001` means all OK, `0000000002` means deceased, `0000000009` means Sepad times out once and then succeeds), so any case can be reproduced without changing config. A persona table goes in `docs/integrations/personas.md`.
- **Only synthetic data** goes in stubs and in the repo. Any real customer data in the bank's documents is replaced before it is committed.
- **Order:**
  1. Gateway token, ARcaptcha and Notifier (Phase 1)
  2. AAAService and Yaghut `loginStatic`
  3. Yaghut `getCustomerDetailInfo`, `getBranch` and `getDeposit`
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
- **The 9 inquiries run in parallel** with `CompletableFuture`, on a dedicated bounded executor. A run takes as long as its slowest inquiry, capped at the 25 s budget. Different customers' runs are independent and run side by side.
- **Each service has its own timeout and retries** (§7-3 table, D4), applied by Resilience4j inside the client. An inquiry is marked `FAILED` only after its retries are used up. When a circuit breaker is open, the inquiry fails at once, without waiting for the timeout.
- **One run per request at a time.** The row lock (§4.1) is held for milliseconds, only to record "a run has started". If the customer double-clicks or opens a second tab, the call **attaches to the run already in progress** and returns its `runId`, so the customer never sees an error. A run older than `azs.inquiry.run-stale-after` (default **60 s**) is treated as dead (for example, after a server restart), and a new one may start.

**Retry by the customer**
- A «تلاش مجدد» (try again) button appears, with a cooldown of `azs.inquiry.retry-cooldown` (default **60 s**).
- On retry, **only the `FAILED` inquiries, and any whose result has gone stale, are re-run**. A `PASSED` result is reused for `azs.inquiry.result-reuse-window` (default **1 hour**).
- The 7-day auto-reject still applies. If a service stays down for a week, the request is auto-rejected under the existing rule.

**What is saved:** one `inquiry` row per inquiry per attempt, with:
- `run_id`, `inquiry_type`, `attempt_no`, `result` and `failure_kind` (`TIMEOUT`, `UNAVAILABLE`, `CIRCUIT_OPEN` or `BAD_RESPONSE`)
- the provider's response code, `duration_ms`, start and finish times, and the raw response with personal data masked

**Monitoring:** a Micrometer counter and timer per inquiry type and result, plus an alert when an inquiry type's `FAILED` rate is above a threshold. Ops then hears about an outage before customers complain.

**Tests (Phase 3):**
- every combination of `NEGATIVE`, `FAILED` and `PASSED`
- a double-click attaches to the running run
- a retry re-runs only the failed and stale inquiries
- the cooldown is enforced
- a stale run is recovered
- an open circuit fails fast
- a full run stays inside the 25 s budget with injected latency

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
- **Secrets (D13):** the JWT private key, database password, gateway token and mobile-hash salt are read from **environment variables or mounted files** only, never committed to the repo. The bank's secret store (Vault, Kubernetes Secrets or encrypted files) is plugged in at deployment time.
- **Authorization:** `@PreAuthorize` on every endpoint. Roles are `BRANCH`, `ADMIN`, `AGENCY` and `CUSTOMER`. Every query is also scoped by ownership: an agency only sees its own requests, a branch only its own branch, and a customer only their own requests.
- **OTP:** 6 digits with a 120 s TTL, stored hashed in `otp_codes`. 3 wrong attempts trigger a 15-minute lockout. After OTP succeeds, the national code returned by Yaghut must match the request's customer.
- **Data protection:** encryption at rest via TDE, provided by the DBA. National codes are masked in logs. Mobile numbers are handled as in §6.1. TLS 1.2+ with HSTS.
- **Audit:** an append-only `audit_log` table in `AZS_AUDIT`, partitioned monthly and kept 7 years. The app's DB user has `INSERT`-only grants on it. It is written for every state change and every admin edit, with old/new JSON, IP and user agent.
- **Abuse protection:** a rate-limit filter allows 100 requests/min per client IP, read from the proxy's `X-Forwarded-For` header. Login pages use ARcaptcha. CSP headers are set and Angular's auto-escaping is relied on.
- **Idempotency:** every POST/PUT requires an `Idempotency-Key`. Keys and their stored responses are kept in `idempotency_keys` for 24 h.

### 6.1 Customer mobile number (resolves D2)

**Rule:** the plain mobile number **never reaches the database**: not in tables, the outbox, the audit log or application logs. The number to use is the one registered in Farangin (SRS), and Yaghut provides it.

| Where it is needed | Where it comes from |
|---|---|
| Sending the login OTP | The `loginStatic` response for that login. Held in memory only for that one call. |
| Shahkar inquiry (UC-09) | `getCustomerDetailInfo`, fetched just before the inquiries run. In memory only. |
| Notification SMS (issued, rejected, cancelled, revoked, auto-rejected) | Looked up **when the message is sent**. The outbox row stores `recipient_ref` (customer number or national code), not the number. The dispatcher calls `getCustomerDetailInfo` and then the Notifier. |
| Masked display ("…to 01\*\*\*\*\*\*\*09") | `customer.mobile_last2` |
| Noticing that the customer's number has changed | `customer.mobile_hash` (salted SHA-256, with the salt kept in the secret store) compared with the hash of Yaghut's latest answer |

**OTP stays fast and still uses the outbox**, as the architecture document requires:
- The OTP is written to the outbox with priority `HIGH`.
- The dispatcher is woken immediately after commit by an `AFTER_COMMIT` event, so it doesn't wait for the next polling cycle.
- The OTP has a short TTL, so it is never retried after it expires.

**If Yaghut is down when a notification is due,** the outbox row stays pending and is retried with backoff (Phase 6). Nothing is lost, and the number is still never stored.

**Logging:** the mobile number is never logged. Any field named `mobile` is masked by the JSON log encoder.

---

## 7. Delivery phases

Each phase ends with a demoable result and its own exit criteria. External services are mocked until the bank grants access.

### Phase 0 — Foundation
- Repo skeleton (layout in §3): Maven backend (Java 21, Spring Boot 4.1.x, Spring Modulith 2.1.x) and Nx frontend (Angular 22). All versions pinned (D9).
- `docker-compose` with Oracle XE (gvenzl/oracle-xe) and mock external services.
- Flyway baseline (D20): `spring-boot-starter-flyway` plus `flyway-database-oracle`. Migrations in `db/migration`, named `V001__create_schemas.sql`, `V002__…` and so on. Flyway manages all 5 schemas, with its history table in `AZS_BUSINESS`. The first migrations create `BaseEntity` columns and the reference tables (`branch`, `agency`, `rejection_reason`). Schema users and grants that need DBA rights go in a separate `db/dba/` script that the DBA runs, not Flyway.
- Cross-cutting pieces: error model (RFC 7807), correlation ID filter, structured JSON logging, Actuator/Micrometer, OpenTelemetry, idempotency filter, audit writer, Spring Modulith/ArchUnit boundary test.
- CI on **GitHub Actions** (D8): build → unit tests (JaCoCo ≥ 80 %) → integration tests (Testcontainers Oracle) → SonarQube/OWASP Dependency-Check → Docker image build → Trivy image scan. The stages mirror §8-2 so they port to GitLab CI.
- First draft of `docs/api/openapi.yaml`.

**Exit:** `docker compose up` gives a running core plus Oracle, CI is green, and the health endpoint responds.

### Phase 1 — Auth module
- Bank login: ARcaptcha → AAAService → JWT with the user's role.
- Agency login: ARcaptcha → Yaghut `loginStatic` → agency profile → JWT.
- Customer login: ARcaptcha → `loginStatic` → OTP via Notifier → verify → JWT.
- Refresh, logout and blacklist. OTP table with attempt limits and lockout.
- Angular: shared `auth` library, a login screen per app, HTTP interceptor and route guards, RTL layout.

**Exit:** all three panels can log in against the mocks. The OTP brute-force limits are covered by tests.

### Phase 2 — Request registration (agency side)
- Agency registers an issuance request: customer lookup (`getCustomerDetailInfo`), branch selection (`getBranch`), amount, expiry and companions (≤ 5).
- Tracking-code generator (D12) and the `Request` aggregate with the state machine and domain events.
- SMS notifying the customer, through the outbox (pulled forward from Phase 6 in its minimal form).

**Exit:** a request created in the agency panel shows up in the customer panel.

### Phase 3 — Customer completion: inquiries and collateral (UC-09)
- InquiryService as designed in §5.1: background runs, the 9 inquiries in parallel, `PASSED`/`NEGATIVE`/`FAILED`/`SKIPPED`, «تلاش مجدد» with cooldown and result reuse, and per-attempt history.
- Filter eligible deposits from `getDeposit` (individual, owner and signatory, active, rial) and classify them as cheap or expensive (§4.3).
- CollateralService: Cash, cheap-pledge and expensive-pledge strategies (§4.3). The SRS worked examples become unit tests (D14, D15, D18).
- `start-completion` and `complete` endpoints. Customer panel steps: review the request → inquiries → choose collateral → submit.

**Exit:** the full completion flow works with the mocks, and the UC-09 P95 is ≤ 15 s with injected latency.

### Phase 4 — Branch review and manual issuance
- Branch inbox (`status, branch_id` index), scoped to the user's branch and its contracted agencies (SRS).
- Ownership lock as designed in §4.1: «اقدام», approve, reject, abandon, admin force-release, and the idle auto-release job for `UNDER_REVIEW`.
- Re-check balances at approval (D16).
- `UNDER_ACTION` screen: checklist for the manual steps (digital signature, then Farangin), guarantee-number entry with format validation, and «انصراف از درخواست».
- Inbox shows locked requests read-only, with the holder's name.

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
- Outbox worker with priorities (OTP first), retries with backoff and dead-lettering. Templates for issued, rejected, cancelled, revoked and auto-rejected messages, using the SRS wording.
- The recipient's number is looked up through Yaghut when each message is sent (§6.1). The outbox never stores a mobile number.

**Exit:** with the SMS mock down for 5 minutes, no messages are lost and all of them are delivered after it recovers.

### Phase 7 — Tracking and reporting
- Agency tracking: filters, pagination and Excel export.
- Bank reports: advanced filters and SXSSF streaming export. Reports use a read-only datasource that can point at the Data Guard standby.
- Admin: CRUD for agencies, rejection reasons and other reference data, with a Caffeine cache that is invalidated on writes.

**Exit:** a 100k-row export runs in bounded memory. A 1,000-row export meets P95 ≤ 10 s.

### Phase 8 — Scheduler
- Leader election through `scheduler_lock`, shedlock style.
- Jobs: auto-reject requests left incomplete after 7 days, release idle `UNDER_REVIEW` locks (§4.1), purge expired OTPs and blacklist entries, and maintain partitions. There is **no** expiry job for issued guarantees (D19).

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
| Unit | JUnit 5, AssertJ, Mockito | State machine transitions, collateral strategies, tracking code generation, OTP rules |
| Module | Spring Modulith `@ApplicationModuleTest` | Each module tested on its own, events published and consumed |
| Integration | Testcontainers (Oracle XE), WireMock | Repositories, `SELECT FOR UPDATE NOWAIT` → 409, Flyway migrations from an empty schema, external client resilience |
| Contract | OpenAPI validation in CI | Backend matches `openapi.yaml`, generated Angular client compiles |
| Architecture | ArchUnit / Modulith verify | Module boundaries, layering (no business logic in controllers) |
| E2E | Playwright | Main path for each panel: issuance, cancellation before «اقدام», revocation, tracking, report |
| Performance | k6 or Gatling | SLA table in §9-1 |
| Security | OWASP ZAP, Dependency-Check, Trivy | Pipeline gates |

---

## 9. Definition of done (per feature)
- The OpenAPI contract is updated first, then implemented.
- Unit and integration tests are written, coverage is ≥ 80 % on changed code, and the module-boundary test passes.
- State changes are audited, and POST/PUT endpoints are idempotent.
- Metrics and logs are added. Logs contain no PII (national code masked, no mobile number).
- Database changes are a new Flyway migration. Applied migrations are never edited (forward-fix only). Risky changes include a hand-written undo script in `db/undo/` for the DBA.
- Persian/RTL UI has been reviewed in all affected panels.

---

## 10. Immediate next steps
1. All decisions D1–D19 are made (§2).
2. Get the API specs for Yaghut (including the deposit type codes), AAAService and the Bank API Gateway, plus the §7-3 timeout table values per service, so the mocks and the config match the real services.
3. Record the decisions that change the architecture document as ADRs in `docs/architecture/`:
   - **ADR-008:** the ownership lock (amends ADR-003)
   - **ADR-009:** لغو versus ابطال, and no expiry status
   - **ADR-010:** the version upgrade (Java 21, Boot 4.1, Angular 22)
4. Decide how Phase 0 is built and checked. This workspace can't download packages, so either GitHub Actions does the building, or the work runs on a computer that can.
5. Start **Phase 0** in this repository.
