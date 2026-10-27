FROM gradle:8.11-jdk21 AS build
WORKDIR /app

# Gradle-Wrapper und Build-Konfiguration zuerst kopieren
COPY gradlew gradlew.bat ./
RUN chmod +x ./gradlew
COPY gradle ./gradle
COPY build.gradle.kts gradle.properties settings.gradle.kts ./

# Abhängigkeiten auflösen (mit Retry-Logik)
RUN ./gradlew --version && ./gradlew clean --no-daemon || true

# Quellcode kopieren und bauen
COPY src ./src
RUN ./gradlew buildFatJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
