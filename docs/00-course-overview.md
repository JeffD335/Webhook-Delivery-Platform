# Course Overview: Fault-Tolerant Webhook Delivery Platform

## 1. Course Thesis

This repository is a sequence of implementation labs for building an outgoing webhook delivery
platform from first principles.

The course is not a copy of a production webhook service. Production references show the same
recurring concerns: durable event storage, asynchronous fan-out, retry scheduling, HMAC signing,
endpoint health, audit trails, queue recovery, and operational visibility. The course teaches
those concerns one at a time, only after the earlier state model can support them.

The main objective is not to accumulate Spring, Kafka, Redis, Kubernetes, and microservice
keywords. The objective is to learn how reliability emerges from explicit invariants:

- What state must exist before an API can return success?
- Which writes must be atomic?
- Which failures are retryable?
- What does the system know after a timeout?
- What durable evidence proves a delivery was attempted?
- Which component owns source-of-truth state?

The application remains a modular monolith until a lab demonstrates a concrete reason to
introduce a process or queue boundary. Microservices are an outcome of a justified design
decision, not the starting point.

## 2. Reference Systems

The course uses external webhook writeups as design input, not as source code to copy.

### GitHub project references

These are the concrete projects to keep in the course outline. We do not copy their code. We use
them to keep our vocabulary and architecture close to real webhook systems.

| Project | Website | Useful signal | How this course adapts it |
|---|---|---|---|
| Codehooks webhook delivery template | https://github.com/RestDB/codehooks-io-templates/tree/main/webhook-delivery | Compact end-to-end outgoing webhook template with queue delivery, registration, retries, signing, monitoring, auto-disable, and audit trail. | Use as a production-readiness checklist. Split the features across labs instead of copying the template. |
| Convoy | https://github.com/frain-dev/convoy | Open-source webhook gateway. Useful for production vocabulary around endpoints, subscriptions, event deliveries, retry, security, and operational visibility. | Use as the larger-system reference when our design needs real-world naming and workflow boundaries. |
| Svix webhooks | https://github.com/svix/svix-webhooks | Open-source and enterprise-ready webhook service. Useful for delivery attempts, message/event abstractions, endpoint application ownership, retry, signing, and dashboard/API shape. | Use as the mature-product reference, especially before adding operations UI, replay, endpoint secrets, and observability. |
| Flack74/Webhook-Delivery-Platform | https://github.com/Flack74/Webhook-Delivery-Platform | A focused delivery-platform implementation with event ingestion API, PostgreSQL storage, Redis queue, async workers, audit trails, retry direction, and HMAC signing. | Use as the closest side-project reference. Rebuild the same reliability ideas in our Spring Boot codebase through smaller checkpoints. |

### Article and provider references

| Reference | Website | Useful signal | How this course adapts it |
|---|---|---|---|
| DEV production-ready webhook delivery article | https://dev.to/restdbjones/building-a-production-ready-webhook-delivery-system-in-5-minutes-5bhe | Emphasizes queue-based delivery, immediate API response, HMAC signatures, retries, health monitoring, and auto-disable. | We turn those features into separate labs instead of presenting them as one template. |
| Codehooks walkthrough | https://codehooks.io/blog/build-webhook-delivery-system-5-minutes-codehooks-io | Shows the production feature checklist: registration, URL verification, SSRF protection, queue delivery, retry, monitoring, and audit trail. | We use this as a production-readiness checklist and explicitly defer security and observability until later labs. |
| Medium webhook system design article | https://medium.com/@gaddamnaveen192/how-would-you-design-a-webhook-system-for-multiple-events-6d1b83f19c3e | Frames the production problem around multiple events, retries, HMAC security, DLQ, and async processing. | We use it as a topic map; the lab specs remain the authority for exact requirements. |

These references agree on one practical lesson: the hard part is not making one HTTP POST. The
hard part is recording enough durable state to recover, retry, explain, and secure the delivery
workflow.

## 3. Target System

The final project is an outgoing webhook platform:

```text
Producer application
        |
        | POST event
        v
Event ingestion API
        |
        | durable transaction
        v
PostgreSQL source of truth
  - endpoints
  - events
  - deliveries
  - attempts
        |
        | delivery IDs / outbox messages
        v
Queue boundary
        |
        v
Worker pool
        |
        | signed HTTP POST
        v
Customer endpoint
        |
        | response / timeout / network error
        v
Attempt record + delivery state transition
```

Two rules guide the design:

1. PostgreSQL owns business state. A queue may accelerate execution, but it must not become the
   only place where important delivery work exists.
2. Ingestion and delivery are separate concerns. Accepting an Event should not wait for every
   customer Endpoint to respond.

## 4. Core Vocabulary

| Term | Meaning |
|---|---|
| Endpoint | A durable customer destination URL registered with the platform |
| Event | A business fact received from a producer, such as `order.created` |
| Delivery | The durable task connecting one Event to one Endpoint |
| Attempt | One concrete HTTP request made for one Delivery |
| Retry | A later Attempt scheduled after a retryable failure |
| Dead letter | A terminal state for work that exhausted its retry budget |
| Queue boundary | A handoff from durable state to asynchronous execution |
| Outbox | A table-backed handoff pattern that prevents losing DB-to-queue work |

These terms should stay distinct in code, tests, docs, and design review. Confusing Delivery
with Attempt is the most common source of poor webhook designs.

## 5. Course Model

The teaching model borrows four useful constraints from systems courses such as BusTub and
MIT 6.5840:

1. Staff code fixes public interfaces and removes unimportant boilerplate.
2. Each lab is divided into independently gradable checkpoints.
3. The student writes tests before implementation.
4. Every later lab must continue to pass earlier regression suites.

The course is intentionally not "build the whole thing and then test it." Each lab adds one
piece of state, one failure mode, or one boundary. A lab is complete only when its invariant can
be demonstrated by tests and manual evidence.

## 6. Roles

### Course staff provides

- The specification and observable behavior.
- A compiling skeleton with fixed public interfaces.
- Small public smoke tests.
- Teaching test skeletons when syntax would distract from the concept.
- A local closed-book checkpoint grader.
- Failure-category feedback and code review.

### The student provides

- Production implementation.
- Database migrations.
- Student-authored unit, repository, API, and worker tests.
- Lab notes explaining design decisions and debugging evidence.
- Manual demonstrations against PostgreSQL and local receivers.

The closed-book grader never tests behavior that is absent from the specification. Internal
implementation choices remain free unless the specification marks an interface or architecture
rule as fixed.

## 7. Development Loop

For every checkpoint, use this loop:

```text
read one checkpoint contract
        |
explain the current invariant
        |
write or complete a failing student test
        |
implement the smallest correct change
        |
refactor while tests remain green
        |
run all completed-lab tests
        |
run the checkpoint autograder
        |
record evidence in lab notes
```

Do not implement future checkpoints early. Doing so makes failures harder to localize and
weakens the value of the checkpoint design.

## 8. Learning Phases

### Phase A: Durable State Before Delivery

Labs 0-2 establish the data model and API discipline:

- The app starts repeatably.
- Endpoint registration is validated and persisted.
- Event ingestion atomically creates planned Delivery work.

No HTTP webhook is sent yet. The goal is to make the platform know what must be delivered.

### Phase B: Execution and Failure Evidence

Labs 3-5 turn planned Delivery rows into real HTTP Attempts:

- A worker sends one pending Delivery.
- Every Attempt is recorded.
- Timeouts, non-2xx responses, and network failures are classified.
- Retries are scheduled.
- Worker races and crashes are handled.

The goal is at-least-once delivery with durable evidence.

### Phase C: Queue Boundary and Handoff Safety

Labs 6-7 introduce queue and outbox concepts:

- Learn when polling PostgreSQL is sufficient.
- Learn what a queue improves and what it can break.
- Prevent losing work between database commit and broker enqueue.

The goal is to justify asynchronous infrastructure with a concrete bottleneck and a concrete
failure mode.

### Phase D: Production Hardening

Lab 8 and the final exercise add production concerns:

- HMAC signatures and timestamp validation.
- SSRF and endpoint safety restrictions.
- Metrics, logs, audit queries, and dashboards.
- Replay, manual retry, and operational runbooks.

The goal is to make reliability claims observable and defensible.

## 9. Lab Sequence

This is the core reliability sequence. A larger full-stack track, including React frontend,
Prometheus, Jenkins, deployment, multi-tenancy, and performance work, is documented in
[`docs/02-expanded-course-roadmap.md`](02-expanded-course-roadmap.md).

| Lab | Capability | Core invariant | Primary tests | Explicitly not yet |
|---|---|---|---|---|
| 0 | Environment and skeleton | App, database, migrations, tests, and receiver can run repeatably | Context test, manual health check | Business logic |
| 1 | Endpoint registry | A valid Endpoint is durably registered; invalid registration creates no row | Unit, repository, API | Event ingestion, delivery, URL ownership |
| 2 | Event ingestion and delivery planning | Event and all required Delivery rows are created atomically | Unit, repository, API transaction tests | HTTP sending, Attempt, retry |
| 3 | Single delivery worker | One pending Delivery produces one durable Attempt | Worker integration, receiver tests | Retry policy, concurrency |
| 4 | Retry and timeout | Retryable failures schedule future work without losing Attempt evidence | Failure injection, time-control tests | Worker races, queue broker |
| 5 | Concurrency and crash recovery | Multiple workers cannot process the same Delivery incorrectly | Locking, crash simulation | External queue |
| 6 | Queue boundary and flow control | Delivery execution can be decoupled without changing source-of-truth semantics | Queue integration, backpressure tests | Outbox correctness |
| 7 | Transactional outbox | DB-to-queue handoff cannot silently lose accepted work | Outbox relay tests, crash tests | Full production security |
| 8 | Security and observability | Deliveries can be verified, protected, inspected, and operated | Security tests, metrics/log assertions | New product features |
| Final | Failure exercise and design review | Reliability claims are demonstrated, not asserted | End-to-end failure drills | Scope creep |

## 10. Progressive Architecture

The project should not jump directly to a production diagram. The architecture changes only when
a lab creates the reason.

### Labs 0-2

```text
Spring Boot API -> PostgreSQL
```

### Labs 3-5

```text
Spring Boot API -> PostgreSQL <- Worker loop
                         |
                         v
                  Customer endpoint
```

### Labs 6-7

```text
Spring Boot API -> PostgreSQL -> Queue -> Worker pool -> Customer endpoint
                  source of truth
```

### Lab 8 and final

```text
API + Worker + Queue + PostgreSQL
        |
        +-- signatures
        +-- endpoint safety checks
        +-- metrics and audit queries
        +-- replay and operations workflows
```

This progression mirrors production systems without hiding the reason each part exists.

## 11. Reliability Invariants

By the end of the course, the implementation should defend these claims:

- An accepted Event is durably recorded.
- Required Delivery rows are created atomically with the Event.
- A Delivery is never considered done without a recorded Attempt.
- A timeout is treated as unknown receiver state, not proof of no side effect.
- Retry scheduling is bounded and visible.
- Two workers cannot both complete the same Delivery in conflicting ways.
- Queue loss cannot erase the only record of accepted work.
- Outbound requests can be authenticated by receivers.
- Unsafe Endpoint URLs are rejected before delivery.
- Operators can answer what happened to an Event without reading application memory.

Every lab owns a subset of these claims. The final review connects them.

## 12. Test Layers

There are three test owners and several test layers.

### Public smoke tests

These are committed to the repository. They verify that the staff skeleton compiles, the
application starts, and fixed public types have not accidentally changed. They do not prove that
a lab is complete.

### Student tests

These are the tests written or completed before production code. They belong in
`src/test/java` and should cover happy paths, invalid inputs, persistence side effects, state
transitions, and regressions discovered while debugging.

### Closed-book autograder tests

Each lab has an independent grader under `autograders/`. It checks public behavior, boundary
cases, architectural rules, and earlier-lab regressions. The files are locally readable but
treated as closed-book until the lab is complete. See [grading policy](grading-policy.md).

### Layered testing ladder

| Layer | What it protects | What it should avoid |
|---|---|---|
| Unit | Pure rules such as event type validation, status transitions, backoff calculation | Spring, database, HTTP |
| Repository | JPA mappings, constraints, custom queries | MockMvc, JSON response shape |
| API integration | HTTP contract, validation, service transaction, durable side effects | Mocked normal business path |
| Worker integration | HTTP delivery, receiver behavior, Attempt recording | Browser/UI concerns |
| Failure injection | Timeout, retryable failure, crash, queue handoff loss | Random sleeps or nondeterminism |
| Manual acceptance | Real PostgreSQL and local receiver evidence | Untested production assumptions |

Lab 2 intentionally strengthens unit and repository testing because Lab 1 leaned too heavily on
API integration tests.

## 13. Definition of Done

A lab is complete only when all of the following are true:

- Every required checkpoint passes its autograder.
- `mvn test` passes without skipped lab tests.
- The manual PostgreSQL acceptance flow passes from a clean database.
- Student lab notes contain design answers and one debugging record.
- The required test layers exist and would catch representative broken implementations.
- No out-of-scope feature was added to bypass the assignment.
- High-priority review findings are resolved.

A working happy path alone is not completion.

## 14. Design Review Standard

After each lab, the student should be able to answer:

1. What state did this lab add?
2. What invariant does that state protect?
3. Which test proves the invariant?
4. What failure mode is still intentionally unsolved?
5. What production feature would build on this lab?

Example:

```text
Lab 2 adds Delivery rows.
They protect the invariant that every accepted Event has explicit planned work.
The transaction test proves an Event cannot commit without its Deliveries.
HTTP delivery is still unsolved.
Lab 3 builds the worker on top of PENDING Deliveries.
```

## 15. AI Assistance Rules

The full collaboration method is documented in `docs/codex-collaboration-workflow.md`. That method
is more important than the exact lab order.

AI should act as a coach and reviewer:

- expose the next concrete problem;
- ask for the student's design judgment;
- provide hints in small steps;
- review correctness, tradeoffs, and failure evidence;
- implement boilerplate only after the design decision is clear.

AI should not act as an autopilot architect that silently designs and implements the whole system.

Before requesting an implementation hint, record:

1. The failing test or observed behavior.
2. The expected behavior from the specification.
3. The smallest layer that could explain the failure.
4. The evidence already collected.

Hints should escalate gradually: question, structural hint, pseudocode, local code review.
Requesting a complete lab implementation defeats the test-driven exercise.

AI may help write teaching specs, review code, explain errors, or generate TODO skeletons. It
should not silently replace the student's design evidence, tests, or debugging notes.

## 16. Git Discipline

Use one commit per completed checkpoint. Example:

```text
lab1: add endpoint schema migration
lab1: persist endpoint records
lab1: implement endpoint creation API
```

Do not commit secrets, database volumes, IDE state, disabled tests, or local receiver logs.

## 17. Reading Order

Recommended reading path:

1. `docs/01-webhook-learning-roadmap.md`
2. `docs/labs/lab00-setup.md`
3. `docs/labs/lab01-endpoint-registration.md`
4. `docs/labs/lab02-event-ingestion-and-delivery-planning.md`
5. `docs/02-expanded-course-roadmap.md`
6. External reference systems only after you can explain the current lab's state model.

External materials are useful for motivation, but the lab specification is the source of truth
for grading.
