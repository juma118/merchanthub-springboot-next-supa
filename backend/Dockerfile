# ── Stage 1: build the Spring Boot fat JAR ───────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first for faster incremental builds.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
# Skip test compilation/execution in the image build (tests need Docker for
# Testcontainers). Run them locally with `mvn test`.
RUN mvn -q -B clean package -Dmaven.test.skip=true

# ── Stage 2: slim runtime image ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Non-root for safety.
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
