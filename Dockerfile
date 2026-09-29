FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/*.jar app.jar
USER app
EXPOSE 5000
HEALTHCHECK --interval=10s --timeout=2s --retries=3 CMD wget -qO- http://localhost:5000/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
