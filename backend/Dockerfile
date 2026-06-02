FROM gradle:8.10-jdk11 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
# Use GRADLE_OPTS to limit memory usage on small VPS
ENV GRADLE_OPTS="-Xmx256m -Dorg.gradle.daemon=false -Dorg.gradle.parallel=false -Dorg.gradle.workers.max=1"
RUN gradle buildFatJar --no-daemon

FROM eclipse-temurin:11-jre
EXPOSE 8080
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*.jar ktor-app.jar
CMD ["java", "-jar", "ktor-app.jar"]
