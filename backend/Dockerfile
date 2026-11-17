# Stage 1: build with Maven on Java 26 (D9). Tests run in CI, not here.
FROM maven:3.9-eclipse-temurin-26 AS builder
WORKDIR /build
COPY pom.xml .mvn/ mvnw ./
COPY src src
RUN mvn -q -DskipTests package

# Stage 2: slim JRE runtime, non-root, Tehran time zone, preview features on (D25)
FROM eclipse-temurin:26-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl tzdata && rm -rf /var/lib/apt/lists/*
ENV TZ=Asia/Tehran
RUN useradd -r -u 10001 asanzeman
WORKDIR /app
COPY --from=builder /build/target/asanzeman-*.jar /app/app.jar
RUN chown -R asanzeman:asanzeman /app
USER asanzeman
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s CMD curl -fs http://localhost:8080/actuator/health/liveness || exit 1
# The real application.properties is mounted at /config by the operations team (D36); it is never baked in.
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} --enable-preview -XX:+UseContainerSupport -Duser.timezone=Asia/Tehran -jar app.jar --spring.config.location=optional:classpath:/application.properties,optional:file:/config/application.properties"]
