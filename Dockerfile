FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace

# Resolve dependencies separately so Docker can reuse this layer when only source files change.
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress -DskipTests dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests clean package


FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

# Run the public web service without root privileges.
RUN groupadd --system spring && useradd --system --gid spring --home-dir /app spring

COPY --from=build --chown=spring:spring /workspace/target/Backend_BookMyHotel-*.jar /app/app.jar

USER spring

ENV SPRING_PROFILES_ACTIVE=prod

# Render supplies PORT at runtime (normally 10000). Spring reads it in application-prod.properties.
EXPOSE 10000

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
