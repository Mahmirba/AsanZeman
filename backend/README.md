# Asan Zeman core (backend)

Spring Boot 4.1.1 modular monolith on Java 26. See `../CLAUDE.md` for the conventions and `../plan.md` for the
plan and decisions.

```bash
./mvnw verify                          # compile, unit tests, Modulith + ArchUnit rules, JaCoCo gate (D50)
./mvnw verify -Dintegration=true       # + *IT tests on a Testcontainers Oracle Free (Docker needed)
cd ../deploy && cp application.local.example.properties application.local.properties && docker compose up
curl http://localhost:8080/actuator/health
```

Configuration is one `application.properties` (D36). Secrets are `CHANGE_ME` in git; the app refuses to start
until every one is set (`SecretsGuard`). `--enable-preview` is required everywhere (D25).
