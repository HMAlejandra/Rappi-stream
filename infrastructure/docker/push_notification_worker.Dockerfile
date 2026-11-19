# ===================================================
# Dockerfile — push_notification_worker
# ===================================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /workspace

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

RUN mvn dependency:go-offline -pl common,push_notification_worker -am -q

COPY common/src                  common/src
COPY push_notification_worker/src push_notification_worker/src

RUN mvn clean package -pl common,push_notification_worker -am -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /workspace/push_notification_worker/target/push_notification_worker-*.jar app.jar
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8085
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
