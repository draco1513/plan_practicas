# ---- Etapa 1: compilar con Maven y JDK 21 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- Etapa 2: ejecutar solo con el JRE ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home taskflow && mkdir -p /app/storage && chown taskflow /app/storage
COPY --from=build /app/target/taskflow-*.jar app.jar
USER taskflow
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
