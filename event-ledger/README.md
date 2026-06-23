# Event Ledger

A two-microservice Event Ledger system. The **Event Gateway API** (port 8080) is the public-facing service that receives client events, enforces idempotency, and persists them locally. The **Account Service** (port 8081) is an internal-only service that owns account state and computes balances. The Gateway calls the Account Service synchronously over REST to apply each event as a transaction.

## 1. Project Overview

| Service | Port | Visibility | Database |
|---------|------|------------|----------|
| Event Gateway API | 8080 | Public-facing | H2 in-memory (`gatewaydb`) |
| Account Service | 8081 | Internal only | H2 in-memory (`accountdb`) |

Each service runs independently with its own embedded H2 database. The two services share no state and no database.

Built with **Java 17**, **Spring Boot 3.2.5**, **Spring Cloud 2023.0.1**, and **Resilience4j**, organized as a multi-module Maven project (`event-ledger-parent`, version `1.0.0-SNAPSHOT`).

## 2. Architecture

```
Client / Browser
       │
       ▼  HTTP REST
┌─────────────────┐
│  Event Gateway  │  :8080   (public-facing)
│  (H2: gatewaydb)│
└────────┬────────┘
         │  REST + X-Trace-Id header
         ▼
┌─────────────────┐
│ Account Service │  :8081   (internal only)
│ (H2: accountdb) │
└─────────────────┘
```

**Event Gateway API** — Accepts `POST /events` from clients, enforces idempotency on `eventId`, stores every event in its own database, and forwards CREDIT/DEBIT transactions to the Account Service. All read endpoints serve from the Gateway's own database, so they keep working even when the Account Service is down.

**Account Service** — Owns accounts and transactions. Applies transactions, computes net balances on demand, and returns account details. It is never exposed to external clients; only the Gateway calls it.

## 3. Prerequisites

- Java 17+
- Maven 3.9+
- Docker + Docker Compose (for the containerized run)

## 4. Running with Docker Compose (Recommended)

```bash
cd event-ledger
docker-compose up --build
# Gateway:         http://localhost:8080
# Account Service: http://localhost:8081
docker-compose down
```

## 5. Running Locally (Without Docker)

Start the Account Service first so the Gateway has a healthy downstream.

```bash
# Terminal 1 — Account Service
mvn spring-boot:run -pl account-service -f event-ledger/pom.xml

# Terminal 2 — Event Gateway
mvn spring-boot:run -pl event-gateway -f event-ledger/pom.xml
```

## 6. Building

```bash
mvn clean package -f event-ledger/pom.xml
```

## 7. Running Tests

```bash
# All tests
mvn test -f event-ledger/pom.xml

# Single module
mvn test -pl account-service -f event-ledger/pom.xml
mvn test -pl event-gateway -f event-ledger/pom.xml

# Specific test class
mvn test -pl event-gateway -f event-ledger/pom.xml -Dtest=IdempotencyTest
mvn test -pl event-gateway -f event-ledger/pom.xml -Dtest=ResiliencyTest
mvn test -pl event-gateway -f event-ledger/pom.xml -Dtest=TracePropagationTest
```

## 8. API Reference

### Event Gateway (port 8080)

```bash
# Submit event (idempotent on eventId)
curl -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"evt-001","accountId":"acct-123","type":"CREDIT","amount":150.00,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z","metadata":{"source":"mainframe-batch"}}'

# Get event by ID (works even if Account Service is down)
curl http://localhost:8080/events/evt-001

# List events by account, sorted by eventTimestamp (works even if Account Service is down)
curl 'http://localhost:8080/events?account=acct-123'

# Health check (verifies DB connectivity)
curl http://localhost:8080/health
```

### Account Service (port 8081)

```bash
# Apply a transaction
curl -X POST http://localhost:8081/accounts/acct-123/transactions \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"evt-001","type":"CREDIT","amount":150.00,"currency":"USD"}'

# Get net balance: SUM(CREDIT) - SUM(DEBIT)
curl http://localhost:8081/accounts/acct-123/balance

# Get account details + recent transactions
curl http://localhost:8081/accounts/acct-123

# Health check (verifies DB connectivity)
curl http://localhost:8081/health
```

## 9. Resiliency Pattern: Circuit Breaker

The Gateway → Account Service call is protected by a **Resilience4j circuit breaker** (`@CircuitBreaker(name = "accountService")` on `AccountServiceClient.applyTransaction`). A circuit breaker was chosen over bulkhead or timeout+retry for three reasons. First, it has an **observable state** (CLOSED / OPEN / HALF_OPEN) exposed through Actuator metrics, making downstream health easy to monitor. Second, its **fast-fail behavior** in the OPEN state maps exactly to the graceful-degradation requirement: once the Account Service is failing, the Gateway returns `503` immediately instead of piling up slow or hanging requests. Third, Resilience4j ships with **Spring Boot autoconfiguration**, so the breaker is wired up declaratively via an annotation and `application.yml` with no boilerplate.

The breaker starts **CLOSED**. It uses a count-based sliding window of the last **5 calls**; once the **failure rate reaches 50%** within that window it trips to **OPEN**. While OPEN, all calls short-circuit straight to the fallback for **10 seconds** without touching the network. After that wait it moves to **HALF_OPEN** and permits **2 probe calls** — if they succeed the breaker closes again, otherwise it returns to OPEN. Any failure (timeout, connection refused, downstream error, or an open circuit) routes to `applyTransactionFallback`, which throws `AccountServiceUnavailableException`; the API layer maps that to HTTP `503`.

Configuration values (from `event-gateway/src/main/resources/application.yml`):

```yaml
resilience4j:
  circuitbreaker:
    instances:
      accountService:
        slidingWindowSize: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 2
```

In addition, the underlying `RestTemplate` is configured with a **3s connect timeout** and a **5s read timeout**, so a slow or unresponsive Account Service can never make the Gateway hang indefinitely — the call fails fast, counts as a breaker failure, and surfaces as a `503`.

## 10. Distributed Tracing

Every request flowing through the system carries a single trace ID, propagated end to end:

- The Gateway's `TraceIdFilter` generates a UUID trace ID per incoming request, or reuses an inbound `X-Trace-Id` header if the client already supplied one.
- The trace ID is stored in the SLF4J **MDC** under the key `traceId`, so it appears in every JSON log line as `"traceId":"..."` in both services.
- When the Gateway calls the Account Service, it forwards the trace ID as the `X-Trace-Id` HTTP header.
- The Account Service extracts that header into its own MDC, so both services log the same `traceId` for one logical request.
- The Gateway also echoes `X-Trace-Id` back in the HTTP response headers, so callers can correlate their request with server-side logs.

## 11. Key Design Decisions

- **Idempotency via a unique DB constraint** on `eventId` in the Gateway database. This handles concurrent duplicate submissions correctly — the database, not application-level checking, is the source of truth, so two simultaneous requests with the same `eventId` cannot both apply a transaction.
- **Balance is always recomputed** as `SUM(CREDIT) - SUM(DEBIT)`, never stored as a running total. This makes the system inherently safe against out-of-order events: a net sum is order-independent.
- **Two separate H2 in-memory databases** (`gatewaydb`, `accountdb`) with no shared state, enforcing the microservice boundary.
- **Event is persisted to the Gateway DB before calling the Account Service.** This guarantees that `GET /events/{id}` and `GET /events?account=` keep working even when the downstream call fails or the Account Service is unreachable.
- **`noRollbackFor = AccountServiceUnavailableException`** on the event-processing transaction ensures the event's FAILED status is committed when the downstream returns `503`, rather than being rolled back along with the exception.
