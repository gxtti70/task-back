FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/target/taskmanager-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx384m", "-XX:+UseSerialGC", "-jar", "/app/app.jar"]
