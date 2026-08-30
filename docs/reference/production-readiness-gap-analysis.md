# Production Readiness Gap Analysis

This document is a checkpoint, not a promise that the project is already production-ready.

The project started as a lab-driven learning project. That means some operational problems were
found while running the system, not designed fully up front. That is acceptable only if the lessons
are now captured as architecture boundaries, debugging practices, and hardening work.

The goal is to become production-shaped before becoming production-complete.

## Current Position

The platform is currently a sender-side webhook delivery system.

```text
React dashboard / API client
  -> Spring Boot webhook platform
      -> Endpoint registry
      -> Event ingestion
      -> Delivery records
      -> Concurrent delivery workers
      -> Retry and terminal failure
      -> Attempt history
      -> Operator dashboard
      -> Local demo receiver or external webhook receiver
```

It is not a receiver-side webhook ingestion product. It sends webhooks to subscriber endpoints.

## Already Production-Shaped

These parts already follow recognizable production webhook platform patterns.

| Area | Current implementation | Why it is production-shaped |
|---|---|---|
| Endpoint registry | `webhook_endpoints`, `EndpointEntity`, endpoint API | Outbound webhook systems need durable destination records. |
| Event ingestion | `webhook_events`, `EventEntity`, event API | The platform stores the business event before delivery work. |
| Delivery work item | `webhook_deliveries`, `DeliveryEntity` | Delivery is modeled as durable work, not only an in-memory HTTP call. |
| Attempt history | `webhook_delivery_attempts`, `DeliveryAttemptEntity` | Each send try is inspectable after the fact. |
| Retry policy | `RetryPolicy`, `RetryDecision`, `FailureResult` | The platform separates transient and permanent failures. |
| Dead-letter state | Terminal `FAILED` delivery state and dead-letter query | Exhausted or permanent failures become visible operator state. |
| Concurrent workers | `DeliveryWorkerPool`, `DeliveryClaimer`, claim fields | Multiple workers can process the delivery table without double-claiming the same row. |
| Database-backed claiming | `FOR UPDATE SKIP LOCKED` plus claim metadata | This matches a common PostgreSQL-backed queue pattern. |
| Real HTTP sender | `HttpWebhookSender` | Delivery now performs real network side effects, not only fake mocks. |
| Scheduler | `ScheduledDeliveryWorkerPool` | Delivery work can run automatically without manual API triggers. |
| Operator view | React dashboard for deliveries and attempts | Operators can inspect state without querying the database. |
| Observability foundation | SLF4J logs in startup, sender, worker, scheduler/pool path | A developer can explain delivery failure from logs. |
| Demo receiver | `tools/test_receiver.py`, Docker Compose `receiver` service | The project can demonstrate real sender-to-receiver behavior locally. |

This is more than CRUD. The core state machine exists.

## Still Demo-Shaped

These parts still feel local-demo or learning-project shaped.

| Area | Current limitation | Why it matters |
|---|---|---|
| Dashboard endpoint management | Dashboard can create events and inspect deliveries, but endpoint management is incomplete or awkward. | A good demo should show create endpoint -> create event -> observe delivery without switching tools. |
| Demo startup | Local demo may require starting backend, frontend, database, and receiver separately. | Production-shaped projects need a clear runbook and preferably one-command demo mode. |
| Tenant/application ownership | Endpoints and events are not scoped to tenants/apps. | Real webhook platforms isolate customers and their secrets. |
| Subscription filtering | Events fan out broadly instead of using event-type subscriptions per endpoint. | Real users subscribe endpoints only to relevant event types. |
| Signing | Outbound requests are not signed. | Receivers need to verify the webhook really came from the platform. |
| Idempotency | Event ingestion does not support an idempotency key. | Producers may retry event creation, and the platform should avoid duplicate event fanout. |
| Receiver security | Endpoint validation is basic and SSRF protection is not complete. | A sender platform must not blindly call dangerous internal URLs in production. |
| Replay | Failed deliveries cannot be replayed from the operator UI. | Operators need recovery actions, not only visibility. |
| Endpoint health / disablement | Repeatedly failing endpoints are not disabled or circuit-broken. | A bad receiver can consume worker capacity forever. |
| Rate limiting | No endpoint-level or tenant-level delivery rate limiting. | Receivers may need controlled delivery pace. |
| Metrics | No Prometheus/Micrometer delivery metrics yet. | Operators need aggregate success rate, failure rate, latency, and backlog size. |
| Tracing | No OpenTelemetry traces. | Multi-service debugging is not needed yet, but would matter after service decomposition. |
| Auth | Public/internal APIs are not protected. | Production admin APIs cannot be open. |
| Config hardening | Timeouts, retry budget, and worker settings are only lightly configurable. | Operators need environment-specific settings. |

These are not reasons to discard the project. They are the hardening backlog.

## Intentional Out Of Scope For Now

These are deliberately not part of the current phase.

| Capability | Reason to defer |
|---|---|
| Kafka/RabbitMQ/SQS | PostgreSQL-backed claiming is enough to learn durable delivery and concurrency first. |
| Kubernetes/Helm | Deployment complexity would distract from webhook delivery mechanics. |
| Full microservice split | Current monolith is easier to reason about while the domain is still evolving. |
| Prometheus/Grafana | Logs solve the immediate debugging gap; metrics can come after stable state transitions. |
| OpenTelemetry tracing | Tracing becomes more valuable after multiple services or remote dependencies grow. |
| OAuth/OIDC user accounts | API authentication matters later, but it is not the current delivery reliability bottleneck. |
| Multi-region delivery | Far beyond the current project scope. |
| Paid hosted product concerns | Billing, teams, roles, audit logs, and enterprise controls are future product scope. |

Production-shaped does not mean every production capability is implemented.

## Reference Projects And What To Borrow

| Reference | What to borrow | What not to copy yet |
|---|---|---|
| Svix | Delivery vocabulary, retry/signature/observability boundaries, OpenTelemetry as later infrastructure. | Full Rust/Go-style service internals and hosted production complexity. |
| Convoy | Gateway, workers, scheduler, dashboard, retries, monitoring boundaries. | Complete gateway feature set and full monitoring stack now. |
| Hookdeck Outpost | Clear separation of runtime, delivery queue, destinations, logging, and worker concerns. | Full product operations and advanced deployment model now. |
| ErenKarakus1 Webhook Delivery Platform | Java/Spring microservice split: gateway, management, ingestion, delivery, scheduler; HMAC, Kafka jobs, retry queue, dashboard. | Immediate Kafka/Redis/microservice rewrite. |
| manvip28 Webhook Delivery Engine | Java production patterns such as outbox, Redis idempotency, Resilience4j, DLQ. | Adding all reliability tools before the simpler DB-backed flow is stable. |

The project should borrow responsibility boundaries, not technology count.

## Architecture Direction

Stay feature-first for now.

```text
dev.webhook.platform
  common
    api
    exception
  endpoint
    api
    application
    persistence
  event
    api
    application
    domain
    persistence
  delivery
    api
    application
    claim
    domain
    persistence
    sender
```

This is appropriate because the project is still a single Spring Boot application.

Avoid splitting into microservices until there is a concrete reason such as independent scaling,
team ownership, or a real queue boundary.

However, avoid letting `DeliveryWorker` become a god class. The future direction should be:

| Responsibility | Current / future home |
|---|---|
| Claim due work | `delivery.claim.DeliveryClaimer` |
| Execute one delivery attempt | `delivery.application.DeliveryWorker` for now; possibly a smaller delivery processor later. |
| Send HTTP | `delivery.sender.HttpWebhookSender` |
| Retry decision | `delivery.domain.RetryPolicy` |
| Persist attempts | `delivery.persistence.DeliveryAttemptRepository` |
| Dead-letter/replay operations | future `delivery.application` service or `delivery.deadletter` package if it grows. |
| Runtime scheduling | `delivery.application.ScheduledDeliveryWorkerPool` for now; future runtime package only if needed. |
| Observability setup | component-local SLF4J logs now; future `common.observability` only for shared request IDs/MDC/metrics. |

## Operational Readiness Gaps

A production-shaped project should be easy to run and debug.

Current gaps:

- The README still starts from early labs instead of the current platform state.
- The demo path needs to be documented as a first-class workflow.
- Dashboard cannot yet manage the full demo flow cleanly.
- Logs are improving, but API/event-planning logs still need to be completed.
- There is no concise runbook for common failures.

Required runbook topics:

```text
How to start PostgreSQL
How to start Spring Boot
How to start React dashboard
How to start demo receiver
How to create an endpoint
How to create an event
How to inspect delivery and attempts
How to simulate receiver down
How to simulate receiver 500
How to simulate timeout
Where to read Spring Boot logs
Where to read receiver logs
How to explain retry and dead-letter behavior
```

## High-Value Hardening Backlog

The next hardening work should be sequenced by demo value and production relevance.

### Pass 1: Finish Observability

- Startup config log.
- Sender start/success/failure logs.
- Worker claim/send/success/retry/dead-letter logs.
- Scheduler and worker pool batch logs.
- API event-created and delivery-planned logs.
- Better missing-data errors in worker.

Outcome:

```text
A failed delivery can be explained from the Run Console and dashboard without querying the DB.
```

### Pass 2: Complete Operator Demo Loop

- Dashboard endpoint creation panel.
- Endpoint list.
- Create event panel connected to visible endpoint count.
- Attempts panel.
- Dead-letter status visibility.
- README demo walkthrough.

Outcome:

```text
A reviewer can use the dashboard to create endpoint -> create event -> observe delivery.
```

### Pass 3: Webhook Authenticity

- Endpoint secret.
- HMAC signature headers.
- Timestamp header.
- Receiver verification example.

Outcome:

```text
The platform can explain how receivers verify webhook authenticity.
```

### Pass 4: Idempotency And Safer Ingestion

- Optional `Idempotency-Key` for event creation.
- Duplicate event handling.
- Clear response showing duplicate vs new event.

Outcome:

```text
Producer retries do not accidentally create duplicate fanout.
```

### Pass 5: Replay And Recovery

- List failed/dead-lettered deliveries.
- Manual replay endpoint.
- Dashboard replay action.
- Attempt history remains intact.

Outcome:

```text
Operators can recover from receiver downtime after the receiver is fixed.
```

### Pass 6: Receiver Safety And Rate Controls

- Endpoint URL validation hardening.
- SSRF guardrails for private/internal IPs.
- Endpoint-level rate limit or circuit breaker.

Outcome:

```text
The sender is safer and less likely to overload or attack receivers.
```

### Pass 7: Metrics

- Micrometer counters for attempts, success, failure, retry, dead-letter.
- Backlog gauge.
- Attempt duration timer.
- Later Prometheus/Grafana integration.

Outcome:

```text
Operators can see aggregate health, not only individual deliveries.
```

## What To Say In An Interview

Do not claim the project is fully production-ready.

Say this instead:

```text
I built a production-shaped webhook delivery platform. It models events, endpoints,
durable deliveries, attempts, retries, dead-lettering, and concurrent workers with
PostgreSQL row locking. I kept it as a single Spring Boot app to focus on the delivery
state machine first, then added a React operator dashboard and observability logs.
The next hardening steps are endpoint signatures, idempotency, replay, and metrics.
```

This is honest and technically defensible.

## Current Risk Summary

| Risk | Severity | Mitigation |
|---|---|---|
| Demo is awkward without endpoint UI and receiver instructions. | High | Build endpoint panel and README demo walkthrough. |
| Logs still incomplete around API/event planning. | Medium | Finish Lab09 slices. |
| No signing/idempotency. | Medium | Add after observability and dashboard loop. |
| No auth/tenancy. | Medium | Defer unless project scope expands beyond portfolio/demo. |
| Too many future production features could distract. | High | Keep hardening backlog sequenced and avoid Kafka/Redis until needed. |

## Bottom Line

The project is not production-complete.

It is already production-shaped in its core delivery state machine:

```text
event -> delivery -> attempt -> worker -> retry/dead-letter -> operator visibility
```

The biggest remaining gap is not the core idea. The biggest gap is operational polish:

```text
runbook, dashboard demo loop, logs, signatures, idempotency, replay, and metrics.
```

That is the right next phase.
