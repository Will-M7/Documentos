# --- Etapa 1: compilar ---
FROM eclipse-temurin:21-jdk AS compilacion
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# --- Etapa 2: ejecutar (solo JRE, sin código fuente ni Maven) ---
# En la fase 1 se agregará LibreOffice para convertir a PDF.
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /app/datos && chown app:app /app/datos
WORKDIR /app
COPY --from=compilacion /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
