# Expanded Course Roadmap: Full-Stack Production Track

## 1. Purpose

The core labs build a reliable outgoing webhook platform. The expanded track turns that backend
into a full-stack production project:

- Backend reliability.
- Distributed systems and messaging.
- React admin console.
- Observability and operations.
- Security hardening.
- CI/CD and deployment.
- Performance, load, and failure drills.

The rule is the same as the core course: introduce a tool only when the system has created the
problem that tool solves.

## 2. Expanded Sequence

| Module | Capability | Main learning question |
|---|---|---|
| Lab 0 | Environment and skeleton | Can the app, database, tests, and receiver run repeatably? |
| Lab 1 | Endpoint registry | Can an HTTP resource be validated and persisted correctly? |
| Lab 2 | Event ingestion and delivery planning | What must be created atomically when an Event arrives? |
| Lab 3 | Delivery worker v0 | How does one pending Delivery get processed? |
| Lab 4 | Attempts and retry v1 | How is every delivery Attempt recorded? |
| Lab 5 | Backoff and dead letter | When should failed work retry, pause, or stop? |
| Lab 6 | Concurrency and crash recovery | What happens when workers race or crash mid-delivery? |
| Lab 7 | Real HTTP sender and scheduler | Can accepted Events be delivered in the background for real? |
| Lab 8 | Delivery read APIs | Can a user inspect Event, Delivery, Attempt, retry, and DLQ state? |
| Lab 9 | React admin console foundation | Can a user operate the platform without curl? |
| Lab 10 | React delivery operations UI | Can a user inspect and recover failed deliveries? |
| Lab 11 | Idempotency | What happens when clients retry `POST /events` after an uncertain response? |
| Lab 12 | Prometheus, Grafana, and tracing | Can the system explain health and latency without reading logs? |
| Lab 13 | Endpoint rate limiting | How do workers avoid overwhelming one receiver? |
| Lab 14 | Security hardening | Can receivers trust our webhook, and can endpoint URLs attack us? |
| Lab 15 | Jenkins CI/CD | Can every change be built, tested, packaged, and checked automatically? |
| Lab 16 | Deployment and runtime operations | Can the system run outside the IDE with repeatable configuration? |
| Lab 17 | Kafka/MQ boundary | When is PostgreSQL polling insufficient? |
| Lab 18 | Transactional outbox | How do we avoid losing DB-to-broker handoff work? |
| Lab 19 | Multi-tenancy and RBAC | Can multiple customers safely share one platform? |
| Final | Failure drill and design review | Can the reliability claims be demonstrated end to end? |

Labs 0-8 are the backend reliability spine. Labs 9-16 turn it into a full-stack production
portfolio project. Labs 17-18 introduce a real message broker only after the PostgreSQL-backed
worker model has created the need.

## 3. React Frontend Track

The frontend should be an operational admin console, not a landing page.

Recommended baseline:

- React with TypeScript.
- Vite for local development.
- React Router for routes.
- TanStack Query or a similar data-fetching layer.
- A small internal component library before adopting a large design system.
- Playwright for end-to-end browser tests.

The React docs introduce components, JSX, rendering data, state, events, lists, and shared state.
Those are the exact concepts needed for the first admin console screens.

### Lab 9: Admin Console Foundation

Build the first usable React app.

Required screens:

- App shell with sidebar navigation.
- Environment indicator showing which backend API base URL is being used.
- Endpoint list page.
- Create Endpoint form.
- Endpoint detail page.

Backend support:

- Reuse Lab 1 APIs.
- Add CORS only for the local frontend dev origin.
- Keep API error contract stable so the frontend can display field-level validation.

Frontend learning goals:

- Components and props.
- Local form state.
- API client wrapper.
- Loading, empty, success, and error states.
- Route params.
- Basic accessibility: labels, focus, keyboard submit.

Tests:

- Component tests for form validation display.
- API-client tests with mocked responses.
- Playwright happy path: create endpoint and see it in the list.

### Lab 10: Delivery Operations UI

Build screens that expose the reliability model.

Required screens:

- Event ingestion page.
- Event list page.
- Event detail timeline.
- Delivery queue page.
- Delivery detail page.
- Attempt table.
- DLQ page.
- Manual retry/replay controls.

The Event detail page is the most important UI:

```text
Event
  |
  +-- Delivery to Endpoint A
  |     +-- Attempt 1: timeout
  |     +-- Attempt 2: HTTP 500
  |     +-- Attempt 3: HTTP 200
  |
  +-- Delivery to Endpoint B
        +-- Attempt 1: HTTP 200
```

Frontend learning goals:

- Server state vs local UI state.
- Polling for operational pages.
- Optimistic vs pessimistic UI updates.
- Tables with sorting and filters.
- Detail drawers or detail pages.
- Safe destructive actions with confirmation.

Backend support:

- Read APIs for Events, Deliveries, and Attempts.
- Manual retry endpoint.
- DLQ replay endpoint.
- Stable status enum values.

Tests:

- Playwright: ingest event, wait for deliveries, inspect attempts.
- Playwright: failed delivery appears in retry/DLQ views.
- UI regression checks for empty and error states.

### Lab 10B: Frontend Error Experience

This can be a checkpoint inside Lab 10.

Required behavior:

- Field errors from `ApiError.fieldErrors` render next to the correct input.
- Unknown errors render a general alert without leaking stack traces.
- Network failure shows retry affordance.
- Slow requests show loading state without shifting layout.
- Empty lists are clear and not mistaken for loading.

### Lab 10C: Frontend Type Discipline

Add type discipline after the basic UI works.

Options:

- Hand-written TypeScript DTOs that match backend response records.
- OpenAPI generation after the backend API has stabilized.
- Runtime validation for critical API responses if desired.

The important rule: frontend types must not silently diverge from backend contracts.

## 4. Backend API Extensions

The backend will need read and operation APIs once the React UI exists.

Worth adding:

- `GET /api/events`
- `GET /api/events/{eventId}`
- `GET /api/events/{eventId}/deliveries`
- `GET /api/deliveries`
- `GET /api/deliveries/{deliveryId}`
- `GET /api/deliveries/{deliveryId}/attempts`
- `POST /api/deliveries/{deliveryId}/retry`
- `POST /api/events/{eventId}/replay`
- `GET /api/dashboard/summary`

API design topics:

- Pagination.
- Filtering by status, endpoint, event type, and time range.
- Stable sort order.
- API versioning.
- Consistent error contract.
- Idempotent operation endpoints.
- Avoiding entity exposure in JSON.

These APIs should be added because the UI needs them, not because CRUD feels complete.

## 5. Distributed Systems Track

### Kafka and MQ

Use PostgreSQL polling first. Then introduce Kafka or another MQ when the worker loop has clear
limitations:

- Too many pending deliveries for one polling worker.
- Need to distribute work across worker instances.
- Need backpressure and buffering.
- Need independent deployability between ingestion and delivery.

Topics to cover:

- Producer, broker, topic, partition, consumer group.
- Ordering guarantees.
- At-least-once processing.
- Duplicate messages.
- Consumer offset vs business state.
- Dead-letter topic.
- Backpressure.
- Poison messages.

Important lesson:

```text
Kafka/MQ improves asynchronous execution.
It does not remove the need for durable business state.
```

### Distributed Transactions

Do not start with XA or 2PC. Teach the failure first:

```text
Database commit succeeds.
Kafka publish fails.
The API already returned success.
How does the system recover?
```

Patterns to compare:

- Single database transaction.
- Transactional outbox.
- Inbox table.
- Saga.
- TCC.
- Idempotency keys.
- Kafka transactions.
- 2PC/XA.

The course should implement Transactional Outbox because it is concrete, testable, and common in
production business systems.

### Ordering and Partitioning

Useful extension after Kafka/MQ:

- Preserve ordering per Endpoint.
- Preserve ordering per Event type.
- Understand why global ordering is expensive.
- Choose partition keys.
- Explain the tradeoff between parallelism and order.

## 6. Concurrency and Crash Recovery

Worth expanding heavily because it creates strong interview evidence.

Topics:

- Pessimistic locking.
- Optimistic locking.
- `SELECT ... FOR UPDATE SKIP LOCKED`.
- Worker leases.
- Heartbeats.
- Lock timeout.
- Duplicate delivery prevention.
- Safe retry after crash.
- Idempotent state transitions.
- Exactly-once as a product claim vs engineering reality.

Failure drills:

- Worker dies after claiming Delivery but before HTTP call.
- Worker dies after HTTP 200 but before recording Attempt.
- Worker times out but receiver later processes request.
- Two workers claim the same Delivery.
- Database transaction rolls back after HTTP side effect.

The key lesson: once the system talks to the outside world, database rollback cannot undo the
receiver's side effect.

## 7. Observability Track

Prometheus, Grafana, and tracing should appear after there is real worker behavior to observe.

### Metrics

Use Spring Boot Actuator and Micrometer for application metrics, then scrape with Prometheus.

Useful metrics:

- `webhook_events_ingested_total`
- `webhook_deliveries_created_total`
- `webhook_deliveries_pending`
- `webhook_attempts_total`
- `webhook_delivery_success_total`
- `webhook_delivery_failure_total`
- `webhook_delivery_latency_seconds`
- `webhook_retry_scheduled_total`
- `webhook_dlq_total`
- `webhook_worker_claim_duration_seconds`

Dashboards:

- Ingestion rate.
- Pending delivery backlog.
- Attempt success/failure ratio.
- Latency percentiles.
- Retry volume.
- DLQ growth.
- Worker throughput.
- Endpoint health.

Alerts:

- Pending backlog too high.
- DLQ growth above threshold.
- High timeout rate.
- Worker has stopped claiming work.
- Database migration failed.

### Logs

Use structured logs.

Required correlation fields:

- `eventId`
- `deliveryId`
- `attemptId`
- `endpointId`
- `traceId`
- `status`
- `durationMs`

### Tracing

OpenTelemetry can connect:

```text
POST /api/events
  -> DB transaction
  -> outbox publish
  -> queue consume
  -> worker delivery
  -> receiver HTTP call
```

Tracing is useful only after the path has multiple components.

## 8. Security Track

Security should be explicit, not scattered.

Worth adding:

- HMAC signatures.
- Timestamp validation.
- Replay-attack prevention.
- Secret generation and rotation.
- SSRF protection.
- DNS rebinding discussion.
- Private IP blocking.
- URL ownership verification.
- TLS-only production mode.
- Request body size limits.
- Rate limiting.
- Tenant isolation.
- RBAC for admin console.
- Audit log for manual retry/replay.

React UI security:

- Do not display secrets after creation.
- Copy-to-clipboard secret UX.
- Confirmation for destructive operations.
- Role-based visibility for replay and secret rotation.
- Safe rendering of payload JSON.

## 9. CI/CD and DevOps Track

Jenkins fits well after backend and frontend tests exist.

### Jenkins Lab

Pipeline stages:

```text
checkout
backend compile
backend unit/repository/API tests
frontend install
frontend lint
frontend unit tests
frontend build
end-to-end tests
autograder checkpoint
Docker image build
artifact archive
```

Quality gates:

- No skipped lab tests.
- Backend tests pass.
- Frontend tests pass.
- Playwright smoke flow passes.
- Docker image builds.
- Flyway migrations validate.

Useful Jenkins topics:

- Declarative pipeline.
- Environment variables.
- Credentials.
- Build artifacts.
- Test reports.
- Parallel stages.
- Branch-based behavior.
- Manual approval before deployment.

### Docker and Runtime Configuration

Worth adding:

- Dockerfile for backend.
- Dockerfile for frontend.
- Compose stack with PostgreSQL, Kafka/MQ, Prometheus, Grafana, and app services.
- Environment-specific configuration.
- Health checks.
- Readiness checks.
- Migration-on-start vs migration job.

### Deployment Extension

Optional advanced topics:

- Kubernetes manifests.
- Helm chart.
- Rolling deployment.
- Blue/green deployment.
- Canary delivery worker rollout.
- Secrets management.
- Backup and restore.

## 10. Testing Expansion

The expanded course should teach testing as a system design tool.

Worth adding:

- Unit tests for pure rules.
- Repository tests for persistence.
- API integration tests.
- Worker integration tests.
- Contract tests between frontend and backend.
- Playwright end-to-end tests.
- Testcontainers for PostgreSQL and Kafka/MQ.
- Load tests with realistic event volume.
- Fault injection tests.
- Snapshot tests only where output is stable.
- Accessibility checks for React pages.

Avoid:

- Tests that depend on row order without sorting.
- `Thread.sleep` as synchronization.
- Mocking the normal business path in integration tests.
- Frontend tests that only check implementation details.

## 11. Performance Track

Performance should come after correctness.

Questions to answer:

- How many Events per second can ingestion accept?
- How many pending Deliveries can one worker drain?
- Which index is missing?
- Where does the database lock?
- What is the cost of storing large payloads?
- How does retry storm behavior affect the system?
- What happens when one Endpoint is slow?

Topics:

- Database indexes and query plans.
- Batch claiming.
- Worker pool sizing.
- Connection pool sizing.
- HTTP client timeout tuning.
- Payload size limits.
- Backpressure.
- Rate limiting per Endpoint.
- Load testing and capacity notes.

## 12. Product and UX Extensions

The project becomes more realistic if it includes operator workflows.

Worth adding:

- Endpoint health page.
- Endpoint disable/enable.
- Endpoint verification flow.
- Secret rotation page.
- Event schema/version page.
- Delivery timeline.
- Attempt raw request/response view.
- DLQ triage.
- Manual replay.
- Dashboard summary.
- Audit log.
- Tenant switcher.
- User and role management.

Every UI page should answer a real operational question:

```text
What happened?
What is stuck?
Can I safely retry it?
Which endpoint is unhealthy?
Did this receiver verify our signature?
```

## 13. Data and Platform Extensions

Advanced backend topics:

- Event schema versioning.
- Payload storage strategy.
- Payload compression.
- Payload redaction.
- Retention policy.
- Archival.
- Partitioned tables.
- Multi-tenant database design.
- Sharding discussion.
- Read model for dashboard queries.
- Audit log append-only design.

Do not add these early. They are valuable only after the base workflow is stable.

## 14. Suggested Capstone

A strong final project should demonstrate:

1. Register two Endpoints.
2. Ingest multiple Events.
3. Create Deliveries atomically.
4. Run workers concurrently.
5. Record every Attempt.
6. Retry failed deliveries.
7. Move exhausted work to DLQ.
8. Publish delivery work through Kafka/MQ or outbox relay.
9. Recover from worker crash without losing accepted work.
10. Inspect everything in the React admin console.
11. See metrics in Prometheus/Grafana.
12. Run CI in Jenkins.
13. Explain all reliability claims in a design review.

The final demo should include one happy path and at least three failure drills.

## 15. External Documentation Map

Use official docs for tool-specific learning:

- [React Learn](https://react.dev/learn)
- [Apache Kafka documentation](https://kafka.apache.org/documentation/)
- [Prometheus overview](https://prometheus.io/docs/introduction/overview/)
- [OpenTelemetry documentation](https://opentelemetry.io/docs/)
- [Jenkins documentation](https://www.jenkins.io/doc/)
- [Docker Compose documentation](https://docs.docker.com/compose/)

Read these after the relevant lab creates the need. Reading all of them before Lab 2 will create
more confusion than progress.
