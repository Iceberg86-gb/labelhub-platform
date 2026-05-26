FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY packages/contracts/openapi packages/contracts/openapi
COPY services/api/pom.xml services/api/pom.xml
COPY services/api/src services/api/src
RUN mvn -pl services/api -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/services/api/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
