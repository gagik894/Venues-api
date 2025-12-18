# ==========================================
# STAGE 1: The Builder (Multi-Module Capable)
# ==========================================
FROM eclipse-temurin:22-jdk-alpine AS builder
WORKDIR /workspace/app

# 1. Copy the ENTIRE project
# We stop guessing where 'src' or 'build.gradle' is. We just copy it all.
COPY . .

# 2. Grant permission to gradlew
# We install 'bash' just in case the gradlew script needs it on Alpine
RUN apk add --no-cache bash
RUN chmod +x gradlew

# 3. Build the JAR
# We run 'bootJar' which only builds the executable JAR for the main application.
# We skip tests (-x test) to make the build faster and reliable.
RUN ./gradlew bootJar -x test --no-daemon

# 4. Search and Rescue the JAR
# In a multi-module setup, the JAR is buried in "module-name/build/libs".
# We find the one that ends in .jar but NOT "-plain.jar" (which is the non-executable one).
RUN mkdir -p build/libs && \
    find . -name "*.jar" -type f ! -name "*-plain.jar" -exec cp {} build/libs/app.jar \;

# ==========================================
# STAGE 2: The Runtime (Secure)
# ==========================================
FROM eclipse-temurin:22-jre-alpine

# 1. Create secure user
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

# 2. Copy the found JAR from Stage 1
COPY --from=builder /workspace/app/build/libs/app.jar app.jar

# 3. Secure permissions
RUN chown spring:spring /app/app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]