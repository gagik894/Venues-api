# ==========================================
# STAGE 1: Dependencies Cache Layer
# ==========================================
FROM eclipse-temurin:21-jdk-jammy AS deps
WORKDIR /workspace/app

# Copy only build configuration files first (for layer caching)
COPY gradle ./gradle
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY */build.gradle.kts ./modules/

# Download dependencies (this layer is cached unless build files change)
RUN ./gradlew dependencies --no-daemon || true

# ==========================================
# STAGE 2: Build the Application
# ==========================================
FROM deps AS builder
WORKDIR /workspace/app

# Now copy the entire source code
COPY . .

# Build the JAR (tests run separately in CI)
RUN ./gradlew bootJar -x test --no-daemon

# Find and extract the executable JAR (not the -plain.jar)
# Create the target directory
RUN mkdir -p /app

# Explicitly copy ONLY from your main module's build folder
# REPLACE 'app' WITH YOUR ACTUAL MAIN MODULE NAME (e.g., 'server', 'api', 'core')
RUN cp app/build/libs/*.jar /app/app.jar

# Failsafe: Check if we accidentally copied the "plain" jar and remove it if so
# (Rare, but good safety)
RUN rm -f /app/*-plain.jar

# ==========================================
# STAGE 3: Secure Runtime (Distroless)
# ==========================================
FROM gcr.io/distroless/java21-debian12:nonroot

# Use non-root user (distroless default: nonroot uid 65532)
WORKDIR /app
COPY --from=builder --chown=nonroot:nonroot /app/app.jar /app/app.jar

EXPOSE 8080

# Health check (curl not available in distroless, so use java-based check in app or external monitoring)
# Note: Docker HEALTHCHECK with distroless requires external tooling or Spring Boot Actuator + sidecar

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]