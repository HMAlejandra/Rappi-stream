# ===================================================
# Dockerfile — driver_simulator
#
# Build multi-etapa:
#   Etapa 1 (builder): compila el JAR con Maven
#   Etapa 2 (runtime): imagen mínima solo con el JAR
#
# El contexto de build es la raíz del proyecto (.)
# porque Maven necesita acceder al módulo common.
# En docker-compose:
#   build:
#     context: .
#     dockerfile: driver_simulator/Dockerfile
# ===================================================

# --- Etapa 1: Build ---
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Copiar pom.xml raíz y poms de módulos para cachear dependencias
COPY pom.xml .
COPY common/pom.xml          common/pom.xml
COPY driver_simulator/pom.xml driver_simulator/pom.xml
COPY order_api/pom.xml        order_api/pom.xml
COPY order_processor/pom.xml  order_processor/pom.xml
COPY notification_router/pom.xml notification_router/pom.xml
COPY sms_worker/pom.xml       sms_worker/pom.xml
COPY push_notification_worker/pom.xml push_notification_worker/pom.xml
COPY dashboard_aggregator/pom.xml dashboard_aggregator/pom.xml
COPY dashboard_backend/pom.xml    dashboard_backend/pom.xml

# Descargar dependencias (esta capa se cachea si los pom.xml no cambian)
RUN mvn dependency:go-offline -pl common,driver_simulator -am -q

# Copiar fuentes de common y driver_simulator
COPY common/src         common/src
COPY driver_simulator/src driver_simulator/src

# Compilar solo los módulos necesarios
RUN mvn clean package -pl common,driver_simulator -am -DskipTests -q

# --- Etapa 2: Runtime ---
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Usuario no-root por seguridad
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/driver_simulator/target/driver_simulator-*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
