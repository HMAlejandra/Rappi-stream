# ===================================================
# Dockerfile — order_processor
# Build multi-etapa. Contexto de build: raíz del proyecto.
#
# En docker-compose:
#   build:
#     context: .
#     dockerfile: order_processor/Dockerfile
# ===================================================

# --- Etapa 1: Build ---
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Copiar pom.xml de módulos para cachear dependencias
COPY pom.xml .
COPY common/pom.xml               common/pom.xml
COPY order_api/pom.xml            order_api/pom.xml
COPY order_processor/pom.xml      order_processor/pom.xml
COPY driver_simulator/pom.xml     driver_simulator/pom.xml
COPY notification_router/pom.xml  notification_router/pom.xml
COPY sms_worker/pom.xml           sms_worker/pom.xml
COPY push_notification_worker/pom.xml push_notification_worker/pom.xml
COPY dashboard_aggregator/pom.xml dashboard_aggregator/pom.xml
COPY dashboard_backend/pom.xml    dashboard_backend/pom.xml

# Descargar dependencias (capa cacheada)
RUN mvn dependency:go-offline -pl common,order_processor -am -q

# Copiar fuentes
COPY common/src         common/src
COPY order_processor/src order_processor/src

# Compilar solo los módulos necesarios
RUN mvn clean package -pl common,order_processor -am -DskipTests -q

# --- Etapa 2: Runtime ---
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Usuario no-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/order_processor/target/order_processor-*.jar app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
