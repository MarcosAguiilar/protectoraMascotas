# --- Build ---
FROM maven:3.9.8-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

# --- Run ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Render expone la variable PORT (p.ej. 10000). Liga el server a ese puerto.
CMD ["sh","-c","java -Dserver.port=${PORT} -jar app.jar"]
