# Settings ops must make on each server

These come from the Phase 9 security review. Both defaults are the safe choice, so a server that is not configured behaves
as before; the settings below make it correct behind a reverse proxy and keep monitoring off the panels' network.

## Management port (ASVS review M2)

Health, metrics and Prometheus are on their own port, `management.server.port` (default **8081**), not on the API port
(8080).

* Do **not** publish 8081 to the panels' network. Only the monitoring system and the load balancer's health probe need it.
* On 8081: `/actuator/health` and `/actuator/prometheus` are open to whoever can reach the port; `/actuator/metrics` and
  anything else needs an admin token.
* On 8080 the actuator is not there at all.

## Client address behind a proxy (ASVS review L2)

The audit log records the caller's address. Behind a reverse proxy that is the proxy's address unless the proxy's
`X-Forwarded-For` is trusted, and it may be trusted **only from the proxy itself**: anyone else could forge it.

Set, in the server's own configuration file, the regex of the proxy addresses:

```
server.tomcat.remoteip.internal-proxies=10\.1\.2\.(3|4)
```

* Default `(?!)` matches nothing, so no header is trusted and the audit shows the address the connection came from.
* The proxy must **overwrite** `X-Forwarded-For` with the address it saw, not append to what the client sent.
* Check it after setting: log in through the proxy and look at `ip_address` of the latest `LOGIN` row in `AZS_AUDIT.AUDIT_LOG`;
  it must be the client's address, and a request that sets its own `X-Forwarded-For` straight at the application must not
  change it.
