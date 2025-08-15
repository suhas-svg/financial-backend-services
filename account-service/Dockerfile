# ----- BUILD STAGE -----
FROM maven:3.9-eclipse-temurin-22 AS builder

# Build arguments for environment-specific configuration
ARG BUILD_ENV=prod
ARG MAVEN_OPTS="-Xmx1024m -XX:+UseG1GC"
ARG SKIP_TESTS=true

WORKDIR /app

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy Maven wrapper and configuration files first for better layer caching
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./

# Make Maven wrapper executable
RUN chmod +x mvnw

# Download dependencies (this layer will be cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B --no-transfer-progress

# Copy source code
COPY src ./src

# Build application with environment-specific profile
RUN if [ "$SKIP_TESTS" = "true" ]; then \
      ./mvnw clean package -DskipTests -B --no-transfer-progress -P${BUILD_ENV}; \
    else \
      ./mvnw clean package -B --no-transfer-progress -P${BUILD_ENV}; \
    fi

# Extract JAR layers for better Docker layer caching
RUN java -Djarmode=layertools -jar target/*.jar extract

# ----- RUNTIME STAGE -----
FROM eclipse-temurin:22-jre-alpine AS runtime

# Build arguments for runtime configuration
ARG BUILD_ENV=prod
ARG APP_VERSION=latest
ARG BUILD_DATE
ARG VCS_REF

# Labels for better image management and traceability
LABEL maintainer="Account Service Team" \
      org.opencontainers.image.title="Account Service" \
      org.opencontainers.image.description="Financial Account Service Microservice" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="https://github.com/suhasan/account-service" \
      environment="${BUILD_ENV}"

# Install security updates and required packages
RUN apk update && \
    apk upgrade && \
    apk add --no-cache \
      curl \
      ca-certificates \
      tzdata && \
    rm -rf /var/cache/apk/*

# Create non-root user for security
RUN addgroup -g 1001 -S appuser && \
    adduser -u 1001 -S appuser -G appuser

WORKDIR /app

# Create necessary directories with proper permissions
RUN mkdir -p /app/logs /app/tmp && \
    chown -R appuser:appuser /app

# Copy JAR layers from builder stage for optimal layer caching
COPY --from=builder --chown=appuser:appuser /app/dependencies/ ./
COPY --from=builder --chown=appuser:appuser /app/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appuser /app/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appuser /app/application/ ./

# Switch to non-root user
USER appuser

# Expose application and metrics ports
EXPOSE 8080 9001

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM optimization arguments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -XX:+OptimizeStringConcat \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.profiles.active=${BUILD_ENV}"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
