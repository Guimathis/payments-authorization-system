FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml ../pom.xml
COPY src ./src
COPY pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 ../mvnw clean package -DskipTests || true

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/authorization-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
