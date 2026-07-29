# Multi-stage build for Transaction Service
FROM eclipse-temurin:22-jdk-alpine@sha256:f412633b75c929e68fd83d36a3abd9104c778161b987e5088eab645b0e5af3f6 AS builder

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn
COPY pom.xml .
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application (skip test compilation completely)
RUN ./mvnw clean compile -Dmaven.test.skip=true
RUN ./mvnw package -Dmaven.test.skip=true -Dmaven.compile.skip=true

# Production stage
FROM eclipse-temurin:21-jre-alpine-3.23@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

# Apply published Alpine security fixes while preserving the pinned base image.
RUN apk upgrade --no-cache

# Create app user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Set working directory
WORKDIR /app

# Create logs directory with proper permissions
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# Copy the built jar from builder stage
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

# Switch to app user
USER appuser

# Expose application port only
EXPOSE 8081

# Health check — uses the actual application port (H4 fix: was incorrectly 9002)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# JVM optimization arguments for production
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
