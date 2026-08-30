# Evolutionary Webhook Platform Roadmap

## Core Principle

Do not start by asking:

```text
What should the final architecture be?
```

Ask instead:

```text
What is broken in the current version, and what is the smallest next step that fixes that problem?
```

This project should evolve from a normal CRUD service into a small distributed system by repeatedly
running into concrete production problems.

## Evolution Path

```text
Endpoint CRUD
  -> first webhook send
  -> durable Delivery model
  -> async database-backed worker
  -> retry and backoff
  -> concurrent workers
  -> idempotency
  -> dead-letter operations
  -> endpoint rate limiting
  -> observability
  -> horizontal scaling
  -> real message queue
  -> transactional outbox
  -> security hardening
```

The important rule: introduce a tool only after the system has created the problem that tool solves.

Kafka, Redis, Prometheus, Grafana, and an outbox are useful later. They should not appear just
because a production architecture diagram usually contains them.

## Current Project Mapping

The current project already has more than plain CRUD:

| Stage | Status | Notes |
|---|---|---|
| Endpoint CRUD | Done | Endpoint API, validation, persistence, and error handling exist. |
| Durable Event and Delivery model | Done | `Event` and `Delivery` are separated. One event can create delivery work. |
| Async worker model | Partial | `DeliveryWorker.processOne()` exists, but it is not wired as a scheduled runtime worker. |
| Retry and backoff | Mostly done | Lab 05 added result-aware retry policy for 404, 429, 5xx, and timeout-like failures. |
| DeliveryAttempt history | Done | Attempts record per-send evidence. |
| Dead-letter query | Minimal done | `FAILED` deliveries can be queried as the current dead-letter view. |
| Concurrent workers | Next | Lab 06 should make duplicate local claiming visible and fix it. |
| Real HTTP delivery | Missing | `WebhookSender` is still an abstraction used mostly by tests. |
| Operator APIs and UI | Missing | Delivery list/detail, attempt timeline, DLQ page, and dashboard are still needed. |
| Observability | Missing | Metrics, structured logs, and runbooks are not implemented yet. |
| Security | Missing | HMAC signatures, SSRF hardening, replay protection, and secret rotation are later work. |
| Real MQ and outbox | Later | Add only after PostgreSQL polling creates an obvious limitation. |

## Near-Term Route

### 1. Finish Lab 05 Evidence

Lab 05 is mostly complete when tests pass and the notes explain concrete failure behavior:

- 404 becomes terminal `FAILED`.
- 429 retries with slower backoff.
- 5xx retries within the retry budget.
- timeout-like failures are treated as transient.
- dead-letter is visible as `webhook_deliveries.status = FAILED`.

### 2. Lab 06: Concurrent Workers

The current bug:

```text
Worker A reads Delivery D1.
Worker B reads Delivery D1.
Both send the same webhook.
The database may reject one duplicate attempt, but the remote side effect already happened.
```

The learning target is safe claiming:

```text
due Delivery -> claimed/in progress -> send -> finish
```

Production reference:

```sql
SELECT ...
FOR UPDATE SKIP LOCKED
```

Teaching implementation can start with claim columns and an atomic claim operation:

```text
claimed_by
claim_expires_at
```

The point is not exactly-once delivery. The point is preventing avoidable duplicates created by
two local workers racing on the same row.

### 3. Real HTTP Sender and Scheduler

After worker claiming is safe, make delivery real:

- Implement a real HTTP sender with timeout settings.
- Wire the worker into Spring scheduling.
- Add a small local receiver for demos.
- Show that `POST /events` returns quickly while delivery happens in the background.

This fills the gap created by skipping the earlier "synchronous webhook send" learning step.

### 4. Delivery Read APIs and Operator Console

Add backend APIs because the frontend needs them:

- `GET /api/deliveries`
- `GET /api/deliveries/{id}`
- `GET /api/deliveries/{id}/attempts`
- `GET /api/events/{id}/deliveries`
- `POST /api/deliveries/{id}/retry`

Then build a small operations UI:

- queue view;
- delivery detail;
- attempt timeline;
- dead-letter page;
- manual retry action.

### 5. Idempotency

Add `Idempotency-Key` for event ingestion:

```text
same key + same request -> same Event response
same key + conflicting request -> error
```

This teaches why real systems usually promise at-least-once delivery plus idempotency, not simple
exactly-once execution.

### 6. Observability

Add evidence for operation:

- structured logs with `eventId`, `deliveryId`, `attemptId`, and `endpointId`;
- metrics for success, failure, retry, dead-letter, backlog, and latency;
- a small runbook for stuck deliveries and failing endpoints.

### 7. Rate Limiting and Endpoint Protection

Only after multiple workers exist:

- endpoint-level rate limit;
- backoff after repeated 429 or timeout;
- optional circuit-breaker-like pause;
- later Redis token bucket if local memory is not enough.

### 8. Queue and Outbox

Do not introduce RabbitMQ or Kafka until the PostgreSQL-backed worker model has visible limits:

- database polling becomes expensive;
- throughput needs independent worker scaling;
- queue buffering and backpressure become important.

Once a broker appears, the next real problem is the dual-write problem:

```text
DB insert succeeds, queue publish fails.
Queue publish succeeds, DB transaction rolls back.
```

That is when transactional outbox becomes justified.

## Final Portfolio Shape

A strong demo should show:

1. Register endpoints.
2. Ingest an event.
3. Create durable deliveries.
4. Run multiple workers safely.
5. Send real HTTP webhooks.
6. Record every attempt.
7. Retry transient failures.
8. Dead-letter permanent or exhausted failures.
9. Inspect the flow in a UI.
10. Manually retry a failed delivery.
11. Explain metrics and logs.
12. Explain why MQ and outbox come later.

This is enough to present the project as a production-like webhook delivery platform without
pretending it is already a full SaaS provider.
