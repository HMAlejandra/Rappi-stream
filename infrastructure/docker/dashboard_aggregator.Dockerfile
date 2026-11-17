# ===================================================
# Dockerfile — dashboard_aggregator
# Build multi-etapa: compila con Maven, ejecuta con JRE Alpine
# Contexto de build: raíz del proyecto (.)
# ===================================================

# --- Etapa 1: Build ---
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Copiar pom.xml de todos los módulos para cachear dependencias
COPY pom.xml .
COPY common/pom.xml                   common/pom.xml
COPY order_api/pom.xml                order_api/pom.xml
COPY order_processor/pom.xml          order_processor/pom.xml
COPY driver_simulator/pom.xml         driver_simulator/pom.xml
COPY notification_router/pom.xml      notification_router/pom.xml
COPY sms_worker/pom.xml               sms_worker/pom.xml
COPY push_notification_worker/pom.xml push_notification_worker/pom.xml
COPY dashboard_aggregator/pom.xml     dashboard_aggregator/pom.xml
COPY dashboard_backend/pom.xml        dashboard_backend/pom.xml

# Descargar dependencias (capa cacheada si no cambian los pom.xml)
RUN mvn dependency:go-offline -pl common,dashboard_aggregator -am -q

# Copiar fuentes de los módulos necesarios
COPY common/src             common/src
COPY dashboard_aggregator/src dashboard_aggregator/src

# Compilar
RUN mvn clean package -pl common,dashboard_aggregator -am -DskipTests -q

# --- Etapa 2: Runtime ---
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Usuario no-root (Sistemas Operativos — seguridad)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/dashboard_aggregator/target/dashboard_aggregator-*.jar app.jar

# Configuración ergonómica de la JVM para contenedores
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8086

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
