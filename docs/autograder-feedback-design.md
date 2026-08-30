# Autograder Feedback Design

## 1. Purpose

The autograder should help the student diagnose incomplete behavior without replacing their own
tests.

The target workflow is:

```text
run hidden checkpoint
        |
read failing category
        |
return to spec section
        |
write or fix a visible student test
        |
fix production code
        |
rerun student tests
        |
rerun checkpoint
```

The hidden grader is not a treasure hunt. If the student cannot tell which part of the spec was
violated, the grader feedback is too vague.

## 2. Feedback Principles

- Report one behavior per test method.
- Group related behaviors into named categories.
- Include the checkpoint and spec section in the display name or assertion message.
- Prefer behavior names over implementation names.
- Show enough observed state to diagnose the layer.
- Do not leak full hidden test source or implementation recipes.

Good category:

```text
2D-VALIDATION-invalid-payload-array
```

Bad category:

```text
strict test failed
```

Good assertion message:

```text
[2D validation: payload array] Expected POST /api/events to return 400 and leave
event/delivery counts at 0. Observed status=201, events=1, deliveries=0.
Spec: Lab 2 Guided Spec, Compatibility Contract -> Required durable outcomes.
```

Bad assertion message:

```text
expected 400 but was 201
```

## 3. Lab 2 Hidden Test Categories

Lab 2 should be split into small categories. A student should be able to run the grader and know
which concept is incomplete.

### 2A: Public Contract and Skeleton

Purpose: fixed API shape and architecture boundary.

Representative tests:

| Test category | Behavior |
|---|---|
| `2A-CONTRACT-create-event-request-shape` | `CreateEventRequest` exposes only `type` and `payload` |
| `2A-CONTRACT-event-response-shape` | `EventResponse` exposes `id`, `type`, `payload`, `createdAt`, `deliveryCount` |
| `2A-ARCH-controller-uses-service` | Controller does not bypass service/repositories |
| `2A-ARCH-no-entity-response` | API does not expose JPA entity as response body |

### 2B: Event Type Validation

Purpose: the event-type rule is precise before the student has to debug controllers, transactions,
or schema. This checkpoint directly checks `EventTypeValidator.isValid(...)`. API status and
durable side effects are checked in later categories after persistence exists.

Representative tests:

| Test category | Behavior |
|---|---|
| `2B-VALIDATION-valid-type` | valid dotted lowercase types return `true` |
| `2B-VALIDATION-blank-type` | blank type returns `false` |
| `2B-VALIDATION-uppercase-type` | `Order.Created` returns `false` |
| `2B-VALIDATION-missing-dot` | `order` returns `false` |
| `2B-VALIDATION-extra-segment` | `order.created.now` returns `false` |
| `2B-VALIDATION-space-in-type` | `order created` returns `false` |
| `2B-VALIDATION-overlength-type` | over-100-character type returns `false` |

Feedback should explicitly say whether the validator returned the wrong boolean or threw.

### 2C: Payload Validation

Purpose: payload must be present and be a JSON object.

Representative tests:

| Test category | Behavior |
|---|---|
| `2C-PAYLOAD-empty-object` | `{}` payload is accepted |
| `2C-PAYLOAD-missing` | missing payload returns 400 and creates no rows |
| `2C-PAYLOAD-null` | `null` payload returns 400 and creates no rows |
| `2C-PAYLOAD-array` | array payload returns 400 and creates no rows |
| `2C-PAYLOAD-string` | string payload returns 400 and creates no rows |
| `2C-PAYLOAD-round-trip` | object payload persists and returns without semantic change |

### 2D: Persistence and Schema

Purpose: durable storage matches the state model.

Representative tests:

| Test category | Behavior |
|---|---|
| `2D-SCHEMA-events-table-required-columns` | event table has id, event type, payload, created time |
| `2D-SCHEMA-deliveries-table-required-columns` | delivery table has id, event ref, endpoint ref, status, created time |
| `2D-SCHEMA-event-fk` | delivery cannot reference a missing event |
| `2D-SCHEMA-endpoint-fk` | delivery cannot reference a missing endpoint |
| `2D-SCHEMA-unique-event-endpoint` | duplicate `(event_id, endpoint_id)` is rejected |
| `2D-SCHEMA-status-query-index` | schema supports status-based delivery lookup |
| `2D-REPOSITORY-find-by-status` | pending deliveries can be found by status |

Schema feedback should avoid dumping database internals unless needed. It should state which
invariant is unprotected.

### 2E: Delivery Planning

Purpose: one valid Event creates the correct pending work list.

Representative tests:

| Test category | Behavior |
|---|---|
| `2E-PLANNING-zero-endpoints` | valid Event with zero Endpoints creates one Event and zero Deliveries |
| `2E-PLANNING-two-endpoints` | valid Event with two Endpoints creates two Deliveries |
| `2E-PLANNING-duplicate-url-not-collapsed` | two Endpoint rows with the same URL still create two Deliveries |
| `2E-PLANNING-initial-status-pending` | every new Delivery starts as `PENDING` |
| `2E-PLANNING-endpoint-created-after-event` | later Endpoint registration does not retroactively create Delivery |

Feedback should report observed counts:

```text
events=<n>, deliveries=<n>, pendingDeliveries=<n>
```

### 2F: API Response Contract

Purpose: the API response proves durable state and exposes the right public shape.

Representative tests:

| Test category | Behavior |
|---|---|
| `2F-API-created-status` | valid POST returns 201, not 200 |
| `2F-API-location-header` | Location is `/api/events/{id}` |
| `2F-API-response-fields` | response contains id, type, payload, createdAt, deliveryCount |
| `2F-API-delivery-count` | deliveryCount equals persisted Delivery rows |
| `2F-API-no-entity-leak` | response does not expose internal entity fields |

### 2G: Transaction Boundary

Purpose: Event and Delivery writes are atomic.

Representative tests:

| Test category | Behavior |
|---|---|
| `2G-TX-invalid-input-no-rows` | invalid request creates no Event or Delivery rows |
| `2G-TX-delivery-failure-rolls-back-event` | Delivery failure leaves no committed Event row |
| `2G-TX-no-partial-deliveries` | partial Delivery creation is not committed |
| `2G-TX-service-layer-boundary` | transaction boundary covers event save and delivery save |

Feedback should be explicit about committed state:

```text
Expected rollback to leave events=0 and deliveries=0. Observed events=1, deliveries=1.
```

### 2H: Regression from Earlier Labs

Purpose: Lab 2 must not break Lab 1 Endpoint Registry.

Representative tests:

| Test category | Behavior |
|---|---|
| `2H-REGRESSION-endpoint-create` | endpoint creation still works |
| `2H-REGRESSION-endpoint-list` | endpoint list still works |
| `2H-REGRESSION-duplicate-url` | duplicate endpoint URLs remain allowed |
| `2H-REGRESSION-endpoint-validation` | invalid endpoint URLs still return 400 |

## 4. Suggested Lab 2 Grader Layout

The physical test files should mirror the categories:

```text
autograders/lab02/src/test/java/dev/webhook/autograder/lab02/
├── Checkpoint2AContractTest.java
├── Checkpoint2BEventTypeValidationTest.java
├── Checkpoint2CPayloadValidationTest.java
├── Checkpoint2DPersistenceSchemaTest.java
├── Checkpoint2EDeliveryPlanningTest.java
├── Checkpoint2FApiResponseContractTest.java
├── Checkpoint2GTransactionBoundaryTest.java
└── Checkpoint2HRegressionTest.java
```

Each class should contain several small test methods instead of one large scenario.

## 5. Failure Output Template

Use this format in assertion messages:

```text
[<category>] <one-sentence behavior>
Spec: <spec file> -> <section>
Expected: <externally visible behavior and durable side effect>
Observed: <status/body/counts/exception>
Next step: write a visible student test for this behavior before changing code.
```

Example:

```text
[2E-PLANNING-two-endpoints] A valid Event with two Endpoints should create exactly two
PENDING Deliveries.
Spec: lab02-guided-event-ingestion-spec.md -> One Event, Many Deliveries.
Expected: status=201, events=1, deliveries=2, pendingDeliveries=2.
Observed: status=201, events=1, deliveries=1, pendingDeliveries=1.
Next step: add a visible API or service test that arranges two Endpoints and asserts two
Delivery rows.
```

## 6. What the Grader Should Not Do

- Do not expose full hidden test source in output.
- Do not prescribe exact class internals when the spec allows alternatives.
- Do not collapse multiple behavior failures into one generic message.
- Do not require out-of-scope features such as Kafka, retry, worker execution, or Attempts in
  Lab 2.
- Do not make the hidden grader the only way students learn the requirements.

## 7. Student Workflow After a Failure

When a hidden test fails, the student should not immediately edit production code. The intended
loop is:

1. Read the category and spec section.
2. Add or improve a visible student test for that behavior.
3. Run `mvn test`.
4. Fix production code until the visible test passes.
5. Rerun the checkpoint grader.

This makes hidden feedback a guide back to student-owned tests, not a replacement for them.
