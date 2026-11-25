# Performance notes (Phase 9)

Measured on a developer machine: Oracle Free in Docker, one app instance, the fake bank services. These are **not UAT
numbers**; they show that nothing in the code is slow by design and what to watch when UAT repeats them.

## Results

| What | Target (architecture doc 9-1) | Measured |
|---|---|---|
| CSV export, 100,000 rows (agency) | bounded memory | 26 s, app memory flat (about 490 MB before and after) |
| CSV export, about 1,100 rows | P95 10 s | 0.22 s |
| Branch inbox, first page | P95 2 s | 0.09 s alone; P95 0.05 s with 40 clients at once |
| Agency tracking list, first page | (no row in 9-1) | 0.02 s alone; P95 0.03 s with 40 clients at once |
| Branch list filtered by status (REJECTED) | | P95 0.12 s with 40 clients at once |

The parallel runs were 400 requests with 40 at a time, against 100,000 requests spread over 500 agencies with about 4,000
of them open. No request failed and the Hikari pool (20 connections) never timed out.

## What to watch

* **Optimizer statistics.** The app user may not gather statistics. With stale statistics Oracle chose a poor plan (a
  full scan and sort of the whole request table) and the same 40-client run went from P95 0.05 s to P95 13 s with failed
  requests and Hikari connection timeouts. The DBA must keep statistics fresh on `AZS_BUSINESS.REQUEST` (Oracle's nightly
  automatic job is normally enough; check it is enabled, and gather by hand after a large load or migration).
* **One agency with a very large share of all requests** makes every list for it scan and sort that share. That is
  inherent to newest-first paging; a real agency has far fewer rows than the test's single-agency worst case.
* An extra index on `(agency_id, registered_at DESC, id)` made no difference with realistic data, so none was added.
* `/actuator/prometheus` is public on the main port. Block it at the ingress or move management to its own port (D10).

## How the numbers were made

Seed rows for the test were inserted with `registered_by = 'PERF'` and deleted afterwards. The export was timed with `curl`
against `/api/agency/requests/export` and `/api/branch/requests/export`, memory with the process working set sampled every
250 ms, and the parallel runs with `xargs -P 40` over `curl`. A k6 or Gatling script against UAT data is still to be
written when the UAT environment exists.
