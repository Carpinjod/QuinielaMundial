# ── Stage 1: Build ──
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# First copy only pom.xml to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy source and build
COPY src/ src/
RUN mvn package -DskipTests -B

# ── Stage 2: Run ──
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/quinielamundial-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENV DATA_DIR=/app/data
ENV PORT=8080

VOLUME ["/app/data"]

CMD ["java", "-jar", "app.jar"]
