# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first (only re-downloads when pom.xml changes).
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

# Build the jar (skip tests here — CI is the test gate).
COPY src ./src
RUN mvn -B -ntp -DskipTests clean package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as a non-root user.
RUN useradd --system --uid 1001 appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser

# Cloud hosts inject PORT; the app reads it via server.port=${PORT:8081}.
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
