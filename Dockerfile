# ============================================
# Stage 1: Build
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (cache dependencies)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ============================================
# Stage 2: Runtime
# ============================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S finvault && adduser -S finvault -G finvault

# Create logs directory
RUN mkdir -p /app/logs && chown -R finvault:finvault /app/logs

# Copy the built artifact from builder stage
COPY --from=builder /app/target/finvault-backend.jar app.jar

# Switch to non-root user
USER finvault

# Expose application port and management port
EXPOSE 8080 9090

# Health check (uses PORT if set by platform, else defaults to 9090 for local docker)
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:${PORT:-9090}/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
