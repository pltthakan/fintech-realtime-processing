FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY common-library/pom.xml /common-library/pom.xml
COPY common-library/src /common-library/src
RUN mvn -f /common-library/pom.xml clean install -DskipTests -B

COPY notification-service/pom.xml .
RUN mvn dependency:go-offline -B

COPY notification-service/src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd -r fintech && useradd -r -g fintech fintech
COPY --from=build /app/target/*.jar app.jar
RUN chown fintech:fintech app.jar
USER fintech

EXPOSE 8085

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=45s \
  CMD wget -q --spider http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
