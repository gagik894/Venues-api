# ==========================================
# STAGE 1: Build & Cache
# ==========================================
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Copy Gradle configuration first to leverage Docker layer caching
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./

# Copy module build files (Adjust this pattern if your modules are nested differently)
COPY */build.gradle.kts ./modules/

# Download dependencies (Cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code and build
COPY . .
RUN chmod +x gradlew
RUN ./gradlew bootJar -x test --no-daemon

# Extract the JAR to a clean location
# CHANGE 'app' below to your actual main module name (e.g., 'backend' or 'server')
RUN mkdir -p /build-out && \
    cp app/build/libs/*.jar /build-out/app.jar && \
    rm -f /build-out/*-plain.jar

# ==========================================
# STAGE 2: Secure Production Runtime
# ==========================================
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

# Run as non-root user (ID 65532) for security
COPY --from=builder --chown=nonroot:nonroot /build-out/app.jar /app/app.jar

# Expose port 8080 (Internal to container network)
EXPOSE 8080

# JVM Optimization Flags for Containers
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]