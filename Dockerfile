FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/hcen-fhir-interop-poc-0.1.0.jar app.jar
COPY --from=build /src/target/lib ./lib

ENV PORT=8080
EXPOSE 8080

# Sin usuario fijo: OpenShift asigna un UID arbitrario; los archivos son legibles por cualquiera.
USER 1001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
