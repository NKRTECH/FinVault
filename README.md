# FinVault — Personal Finance Management Backend

A production-grade **Spring Boot 3.4** REST API for managing personal financial records, with RBAC security, AI-powered insights (Gemini), dashboard analytics, and a full observability stack.

## Table of Contents

- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Authentication & RBAC](#authentication--rbac)
- [Database Migrations](#database-migrations)
- [Observability](#observability)
- [Code Quality](#code-quality)
- [Testing](#testing)
- [Project Structure](#project-structure)

---

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│   Client    │────▶│  Spring Boot │────▶│  PostgreSQL  │
│ (REST API)  │◀────│   + JWT Auth │◀────│  (Liquibase) │
└─────────────┘     └──────┬───────┘     └──────────────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ Gemini AI│ │Prometheus│ │  Grafana  │
        │  (LLM)   │ │ + Loki   │ │Dashboard │
        └──────────┘ └──────────┘ └──────────┘
```

## Tech Stack

| Layer            | Technology                                    |
|------------------|-----------------------------------------------|
| **Runtime**      | Java 21, Spring Boot 3.4.4                    |
| **Security**     | Spring Security + JWT (jjwt 0.12.6)           |
| **Database**     | PostgreSQL 16 + Liquibase migrations          |
| **AI**           | Google Gemini API (via WebFlux WebClient)      |
| **API Docs**     | SpringDoc OpenAPI 2.8.4 (Swagger UI)          |
| **Metrics**      | Micrometer + Prometheus                       |
| **Logging**      | Logback + Logstash JSON Encoder → Loki        |
| **Dashboards**   | Grafana (auto-provisioned)                    |
| **Coverage**     | JaCoCo 0.8.12 (≥60% line coverage gate)      |
| **Quality**      | SonarQube 10.7 Community Edition              |
| **Container**    | Docker (multi-stage build) + Docker Compose   |

## Prerequisites

- **Java 21** (or use the bundled Maven wrapper)
- **Docker** & **Docker Compose** (for full stack)
- **PostgreSQL 16** (if running without Docker)
- **Google Gemini API Key** (for AI features)

## Quick Start

### Docker Compose (recommended)

```bash
# 1. Clone and navigate
git clone https://github.com/NKRTECH/FinVault.git
cd FinVault

# 2. Configure environment
cp .env.example .env
# Edit .env with your credentials

# 3. Launch full stack
docker compose up -d

# 4. Verify
curl http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

**Services after startup:**

| Service      | URL                    | Purpose                    |
|-------------|------------------------|----------------------------|
| FinVault API | `http://localhost:8080` | REST API                   |
| Swagger UI   | `http://localhost:8080/swagger-ui.html` | Interactive API docs |
| Grafana      | `http://localhost:3000` | Dashboards & log explorer  |
| Prometheus   | `http://localhost:9091` | Metrics scraping           |
| Loki         | `http://localhost:3100` | Log aggregation            |
| SonarQube    | `http://localhost:9000` | Static analysis            |

### Local Development

```bash
# Start only PostgreSQL
docker compose up postgres -d

# Run with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Configuration

### Environment Variables

| Variable               | Required | Default | Description                          |
|------------------------|----------|---------|--------------------------------------|
| `DB_USERNAME`          | ✅       | —       | PostgreSQL username                  |
| `DB_PASSWORD`          | ✅       | —       | PostgreSQL password                  |
| `JWT_SECRET`           | ✅       | —       | JWT signing key (min 32 chars)       |
| `GEMINI_API_KEY`       | ✅       | —       | Google Gemini API key                |
| `GRAFANA_ADMIN_USER`   | ❌       | `admin` | Grafana admin username               |
| `GRAFANA_ADMIN_PASSWORD`| ❌      | `admin` | Grafana admin password               |
| `SONAR_HOST_URL`       | ❌       | —       | SonarQube server URL                 |
| `SONAR_TOKEN`          | ❌       | —       | SonarQube authentication token       |

### Spring Profiles

| Profile   | Purpose                 | Datasource             | Logging          |
|-----------|-------------------------|------------------------|------------------|
| `dev`     | Local development       | `localhost:5432`       | Console (color)  |
| `docker`  | Docker Compose          | `postgres:5432`        | JSON (structured)|
| `test`    | Unit/integration tests  | H2 in-memory           | Console          |

---

## API Reference

> Full interactive docs available at `/swagger-ui.html` when the app is running.

### Authentication (`/api/v1/auth`)

| Method | Endpoint    | Auth     | Description              |
|--------|-------------|----------|--------------------------|
| POST   | `/register` | Public   | Register a new user      |
| POST   | `/login`    | Public   | Authenticate & get JWT   |

### Financial Records (`/api/v1/records`)

| Method | Endpoint  | Roles                     | Description                      |
|--------|-----------|---------------------------|----------------------------------|
| GET    | `/`       | VIEWER, ANALYST, ADMIN    | List records (paginated, filtered)|
| GET    | `/{id}`   | VIEWER, ANALYST, ADMIN    | Get record by ID                 |
| POST   | `/`       | ADMIN                     | Create a new record              |
| PUT    | `/{id}`   | ADMIN                     | Update a record                  |
| DELETE | `/{id}`   | ADMIN                     | Soft-delete a record             |

**Query Parameters (GET `/`):**
`type`, `category`, `startDate`, `endDate`, `minAmount`, `maxAmount`, `page`, `size`, `sort`

### User Management (`/api/v1/users`)

| Method | Endpoint          | Roles         | Description                    |
|--------|--------------------|---------------|--------------------------------|
| GET    | `/me`             | Authenticated | Get current user profile       |
| PUT    | `/me/password`    | Authenticated | Change own password            |
| GET    | `/`               | ADMIN         | List all users (paginated)     |
| GET    | `/{id}`           | ADMIN         | Get user by ID                 |
| PUT    | `/{id}`           | ADMIN         | Update user                    |
| PATCH  | `/{id}/status`    | ADMIN         | Update user status             |
| PATCH  | `/{id}/roles`     | ADMIN         | Assign roles to user           |
| DELETE | `/{id}`           | ADMIN         | Delete user                    |

### Dashboard Analytics (`/api/v1/dashboard`)

| Method | Endpoint              | Roles           | Description                    |
|--------|-----------------------|-----------------|--------------------------------|
| GET    | `/summary`            | ANALYST, ADMIN  | Income/expense totals & balance|
| GET    | `/category-breakdown` | ANALYST, ADMIN  | Spending by category           |
| GET    | `/monthly-trend`      | ANALYST, ADMIN  | Monthly income vs expense      |
| GET    | `/recent-activity`    | VIEWER+         | Last 10 transactions           |

### AI Features (`/api/v1/ai`)

| Method | Endpoint      | Roles           | Description                         |
|--------|---------------|-----------------|-------------------------------------|
| POST   | `/categorize` | ANALYST, ADMIN  | AI-categorize a transaction         |
| GET    | `/insights`   | ANALYST, ADMIN  | AI-generated financial insights     |

### Response Format

All endpoints return a standard envelope:

```json
{
  "success": true,
  "message": "Records retrieved successfully",
  "data": { ... },
  "timestamp": "2025-04-05T14:30:00"
}
```

---

## Authentication & RBAC

### Roles

| Role         | Records     | Dashboard  | AI        | Users     |
|-------------|-------------|------------|-----------|-----------|
| **ADMIN**   | Full CRUD   | Full       | Full      | Full      |
| **ANALYST** | Read-only   | Full       | Full      | Self only |
| **VIEWER**  | Read-only   | Recent only| —         | Self only |

### Using JWT

```bash
# 1. Login to get token
TOKEN=$(curl -s http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token')

# 2. Use token in subsequent requests
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/records
```

### Demo Accounts

| Username   | Password       | Role     |
|-----------|----------------|----------|
| `admin`   | `admin123`     | ADMIN    |
| `analyst` | `password123`  | ANALYST  |
| `viewer`  | `password123`  | VIEWER   |

> Demo accounts are seeded in `dev` and `docker` profiles only.

---

## Database Migrations

Liquibase manages all schema and seed data:

| Changeset | Description                        |
|-----------|------------------------------------|
| 001       | Create `roles` table               |
| 002       | Create `users` table               |
| 003       | Create `user_roles` join table     |
| 004       | Create `financial_records` table   |
| 005       | Seed roles and admin user          |
| 006       | Seed demo users and financial data |

Migrations run automatically on startup. To verify:

```bash
./mvnw liquibase:status -Dliquibase.url=jdbc:postgresql://localhost:5432/finvault
```

---

## Observability

### Monitoring Stack

The Docker Compose includes a full PLG (Prometheus-Loki-Grafana) stack:

- **Prometheus** (`localhost:9091`) — scrapes `/actuator/prometheus` every 15s
- **Loki** (`localhost:3100`) — aggregates structured JSON logs
- **Promtail** — ships logs from the app container to Loki
- **Grafana** (`localhost:3000`) — auto-provisioned with FinVault Overview dashboard

### Grafana Dashboard Panels

| Panel                  | Source     | Description                          |
|------------------------|-----------|--------------------------------------|
| Request Rate           | Prometheus| HTTP requests/sec by method & status |
| Response Time (p95)    | Prometheus| 95th percentile latency              |
| Error Rate             | Prometheus| 4xx/5xx responses per second         |
| JVM Heap Usage         | Prometheus| Memory consumption                   |
| Active Threads         | Prometheus| Thread pool utilization              |
| DB Connection Pool     | Prometheus| HikariCP connections                 |
| Application Logs       | Loki      | Live structured log stream           |
| Log Error Count        | Loki      | Error/warn log volume over time      |

### Actuator Endpoints

| Endpoint              | Auth Required | Description             |
|-----------------------|---------------|-------------------------|
| `/actuator/health`    | No            | Health check             |
| `/actuator/info`      | No            | Application info         |
| `/actuator/prometheus`| No            | Prometheus metrics       |
| `/actuator/metrics`   | ADMIN         | Micrometer metrics       |
| `/actuator/loggers`   | ADMIN         | Runtime log level config |
| `/actuator/env`       | ADMIN         | Environment properties   |

---

## Code Quality

### JaCoCo Coverage

```bash
# Generate coverage report
./mvnw clean verify

# View HTML report
open target/site/jacoco/index.html
```

**Quality gate:** ≥60% line coverage (enforced at build time).

**Exclusions:** DTOs, entities, enums, configs, exception handlers, main class.

### SonarQube Analysis

```bash
# Start SonarQube
docker compose up sonarqube -d

# Wait for startup (~2 min), then run analysis
./mvnw clean verify sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=<your-token>
```

---

## Testing

```bash
# Run all tests
./mvnw clean test

# Run with coverage report
./mvnw clean verify
```

**Test suite:** 139 tests across 19 suites — controllers, services, security, and integration.

| Suite Category      | Tests | Coverage                          |
|--------------------|-------|-----------------------------------|
| Controller tests   | 50+   | All 5 controllers                 |
| Service tests      | 60+   | All business logic services       |
| Security tests     | 15+   | JWT auth, RBAC enforcement        |
| Integration tests  | 10+   | Full Spring context boot          |

---

## Project Structure

```
finvault-backend/
├── src/main/java/com/finvault/
│   ├── config/          # Security, JWT, Gemini, OpenAPI configs
│   ├── controller/      # REST controllers (5)
│   ├── dto/             # Request/Response DTOs
│   ├── entity/          # JPA entities (User, Role, FinancialRecord)
│   ├── enums/           # RecordType, RoleName, UserStatus
│   ├── exception/       # Global exception handler + custom exceptions
│   ├── repository/      # Spring Data JPA repositories
│   ├── security/        # JWT filter, UserDetails implementation
│   └── service/         # Business logic services
├── src/main/resources/
│   ├── application.yml          # Common config
│   ├── application-dev.yml      # Dev profile
│   ├── application-docker.yml   # Docker profile
│   ├── logback-spring.xml       # Structured logging config
│   └── db/changelog/            # Liquibase migrations
├── monitoring/
│   ├── prometheus/prometheus.yml
│   ├── loki/loki-config.yml
│   ├── promtail/promtail-config.yml
│   └── grafana/provisioning/    # Datasources + dashboards
├── Dockerfile                   # Multi-stage build
├── docker-compose.yml           # Full stack orchestration
└── pom.xml                      # Maven build (JaCoCo, SonarQube)
```

---

## License

This project is part of a technical assessment and is not licensed for production use.
