# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **greenfield take-home project**: an Event Ledger system composed of two independently-running microservices. No code exists yet — start from scratch. Language is constrained to **Java, Python, or C#**.

---

## Architecture

```
Browser / Client  →  Event Gateway API (public-facing, port e.g. 8080)
                           │ REST (sync)
                           ▼
                     Account Service (internal, port e.g. 8081)
```

**Event Gateway API** — receives client events, enforces idempotency, stores events locally, calls Account Service to apply transactions.

**Account Service** — owns account state and balances. Only called by the Gateway. Never exposed externally.

Each service has its own separate embedded/in-memory database (H2, SQLite, etc.). They must **not** share state or a database.

---

## API Contracts

### Event Gateway API

| Method | Endpoint | Notes |
|--------|----------|-------|
| `POST` | `/events` | Submit event; idempotent on `eventId` |
| `GET` | `/events/{id}` | Works even if Account Service is down |
| `GET` | `/events?account={accountId}` | Returns events ordered by `eventTimestamp` (not arrival order); works if Account Service is down |
| `GET` | `/health` | Must check DB connectivity |

### Account Service (internal only)

| Method | Endpoint | Notes |
|--------|----------|-------|
| `POST` | `/accounts/{accountId}/transactions` | Apply CREDIT or DEBIT |
| `GET` | `/accounts/{accountId}/balance` | Net balance = sum(CREDITs) − sum(DEBITs) |
| `GET` | `/accounts/{accountId}` | Account details + recent transactions |
| `GET` | `/health` | Must check DB connectivity |

### Event Payload (`POST /events`)

```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata": { "source": "mainframe-batch" }
}
```

All fields except `metadata` are required. `amount` must be > 0. `type` must be `"CREDIT"` or `"DEBIT"`.

---

## Key Behavioral Requirements

### Idempotency
Duplicate `eventId` submissions must return the original event (with an appropriate 2xx or 409 status) and must **not** re-apply the transaction to the account balance.

### Out-of-Order Events
Events may arrive with an earlier `eventTimestamp` than previously stored events. The `GET /events?account=` listing must always be sorted by `eventTimestamp`. Balances must be computed correctly regardless of arrival order (balance is a net sum — ordering doesn't affect it, but idempotency does).

### Graceful Degradation
- `POST /events` when Account Service is down → return `503`, do not hang
- `GET /events/{id}` and `GET /events?account=` when Account Service is down → **still work** (Gateway reads its own DB)
- Balance queries when Account Service is down → return a clear error

### Resiliency Pattern (choose one)
Implement on the Gateway → Account Service call:
- **Circuit breaker** — stop calling after repeated failures, return 503
- **Bulkhead** — isolate calls to prevent thread pool exhaustion
- **Timeout + retry with backoff** — don't retry indefinitely

Be ready to explain the choice in the README.

### Distributed Tracing
- Generate a trace ID at the Gateway per incoming request
- Propagate via HTTP header to Account Service (e.g., `X-Trace-Id` or `traceparent` for W3C)
- Both services log the trace ID in every structured log line
- OpenTelemetry is preferred

### Structured Logging
JSON format with fields: `timestamp`, `level`, `service`, `traceId`, and `message` at minimum.

---

## Build & Test Commands

Commands depend on language chosen. Fill in after scaffolding:

**Java (Spring Boot + Maven):**
```bash
# Build
mvn clean package -DskipTests

# Run services
java -jar event-gateway/target/*.jar
java -jar account-service/target/*.jar

# Test (all)
mvn test

# Test (single)
mvn test -pl event-gateway -Dtest=IdempotencyTest
```

**Python (FastAPI + pytest):**
```bash
# Install deps
pip install -r requirements.txt

# Run services
uvicorn event_gateway.main:app --port 8080
uvicorn account_service.main:app --port 8081

# Test (all)
pytest

# Test (single)
pytest tests/test_idempotency.py -v
```

**C# (ASP.NET + dotnet test):**
```bash
# Build
dotnet build

# Run services
dotnet run --project EventGateway
dotnet run --project AccountService

# Test
dotnet test
```

---

## Running with Docker Compose (preferred)

```bash
docker-compose up --build
docker-compose down
```

---

## Test Coverage Requirements

Tests must cover:
1. **Idempotency** — same `eventId` twice does not duplicate or change balance
2. **Out-of-order** — events arrive out of timestamp order, listing is still sorted
3. **Balance computation** — correct net balance after mixed CREDIT/DEBIT
4. **Validation** — missing fields, zero/negative amount, invalid type all return 4xx
5. **Resiliency** — simulate Account Service down, Gateway returns 503 on POST, GET endpoints still work
6. **Trace propagation** — trace ID present in both service logs for a single request
7. **Integration** — at least one full Gateway → Account Service flow

---

## Deliverables Checklist

- [ ] Two independently runnable services with separate databases
- [ ] All required API endpoints functional
- [ ] Idempotency enforced
- [ ] Out-of-order events handled
- [ ] Resiliency pattern implemented and documented
- [ ] Distributed tracing with trace ID propagation
- [ ] Structured JSON logging with trace ID
- [ ] Health check endpoints
- [ ] At least one custom metric
- [ ] Automated tests covering all requirements
- [ ] `docker-compose.yml` (or manual start instructions)
- [ ] `README.md` with architecture, setup, run, test, and resiliency explanation
- [ ] Git history with meaningful incremental commits (do not squash)
