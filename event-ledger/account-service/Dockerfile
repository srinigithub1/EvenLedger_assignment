# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./pom.xml
COPY event-gateway/pom.xml ./event-gateway/pom.xml
COPY account-service/pom.xml ./account-service/pom.xml

RUN mvn dependency:go-offline -pl account-service --no-transfer-progress -q

COPY account-service/src ./account-service/src
RUN mvn clean package -pl account-service -DskipTests --no-transfer-progress -q

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/account-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
