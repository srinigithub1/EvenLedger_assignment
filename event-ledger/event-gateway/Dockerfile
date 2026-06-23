# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy POMs first for dependency layer caching
COPY pom.xml ./pom.xml
COPY event-gateway/pom.xml ./event-gateway/pom.xml
COPY account-service/pom.xml ./account-service/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl event-gateway --no-transfer-progress -q

# Copy source and build
COPY event-gateway/src ./event-gateway/src
RUN mvn clean package -pl event-gateway -DskipTests --no-transfer-progress -q

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/event-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
