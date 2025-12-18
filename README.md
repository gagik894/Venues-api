# Venues API - Government-Grade Ticket Management System

Multi-module Kotlin Spring Boot application with secure CI/CD pipeline.

## 🚀 Quick Start

### Local Development

```bash
# Run with Docker Compose
docker compose up --build

# Or run with Gradle
./gradlew bootRun
```

Application available at: `http://localhost:8080`

### Building

```bash
./gradlew build          # Build all modules
./gradlew test           # Run all tests
./gradlew bootJar        # Build executable JAR
./gradlew clean          # Clean build outputs
```

## 📚 Documentation

- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Complete production deployment guide
- **[CI_VALIDATION.md](CI_VALIDATION.md)** - Pre-deploy validation checklist
- **[IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)** - CI/CD implementation overview

## 🏗️ Project Structure

Multi-module Gradle project with:

- **Modular monolith** architecture (ports & adapters)
- **Kotlin** with Spring Boot
- **PostgreSQL** for data persistence
- **Flyway** for database migrations
- Version catalog in `gradle/libs.versions.toml`
- Build cache and configuration cache enabled

### Modules

Each domain module follows clean architecture:

- `*-api` - Public ports (DTOs, interfaces)
- `*` - Implementation (services, repositories, entities)

## 🔒 Security & Quality

- ✅ Distroless Docker images (non-root, no shell)
- ✅ Automated security scanning (Trivy)
- ✅ SBOM generation
- ✅ SHA-tagged immutable deployments
- ✅ Health checks with automatic rollback
- ✅ Audit logging on all mutations

## 🚢 CI/CD Pipeline

**Workflow:** `.github/workflows/production.yml`

1. **Test & Build JAR** - Run tests, build artifact
2. **Build & Scan Image** - Create minimal image, security scan
3. **Deploy to Production** - Manual approval, health check, auto-rollback

See [DEPLOYMENT.md](DEPLOYMENT.md) for complete setup.

## 📋 Development Guidelines

Follow project-specific instructions in `.github/copilot-instructions.md`:

- Government-quality code standards
- SOLID principles & DRY
- Module boundaries via ports
- Snapshot pricing pattern
- Atomic DB operations
- Professional commenting (Google-style)

## 🛠️ Gradle

This project uses the Gradle Wrapper (`./gradlew`) for consistent builds.

- [Gradle Wrapper docs](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
- [Gradle tasks reference](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks)