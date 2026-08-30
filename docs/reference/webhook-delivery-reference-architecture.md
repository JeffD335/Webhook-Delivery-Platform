# Webhook Delivery Reference Architecture

This project should not invent its architecture from scratch. Labs should be checked against
existing webhook delivery systems first, then simplified deliberately for learning.

## Reference Sources

Primary GitHub project references:

- Codehooks webhook delivery template:
  https://github.com/RestDB/codehooks-io-templates/tree/main/webhook-delivery
- Convoy open-source webhook gateway:
  https://github.com/frain-dev/convoy
- Svix open-source webhook service:
  https://github.com/svix/svix-webhooks
- Flack74 Webhook Delivery Platform:
  https://github.com/Flack74/Webhook-Delivery-Platform

Article and walkthrough references:

- Codehooks walkthrough:
  https://codehooks.io/blog/build-webhook-delivery-system-5-minutes-codehooks-io
- DEV production-ready webhook delivery article:
  https://dev.to/restdbjones/building-a-production-ready-webhook-delivery-system-in-5-minutes-5bhe

Production behavior references:

- Stripe webhook delivery behavior:
  https://docs.stripe.com/webhooks
- GitHub webhook deliveries and redelivery:
  https://docs.github.com/en/webhooks

## Reference Architecture Pattern

Across webhook delivery systems, the durable path usually looks like this:

```text
Application / Project / Tenant
  -> Endpoint
  -> Event / Message
  -> Delivery / EventDelivery
  -> Attempt
  -> Worker / Dispatcher
  -> Retry policy / Backoff
  -> Terminal success or failure
```

The exact names differ by project, but the split is stable:

- Event or Message stores what happened.
- Endpoint stores where to send it.
- Delivery stores one unit of work: send this Event to this Endpoint.
- Attempt stores one concrete send try and its result.
- Worker executes the remote HTTP side effect outside the API request path.
- Retry policy decides whether a failed attempt should be retried and when.

## How Our Project Maps To The Reference Pattern

| Reference concept | Our current concept | Status |
|---|---|---|
| Application / Project / Tenant | Not implemented yet | Later lab |
| Endpoint | `webhook_endpoints` / `EndpointEntity` | Lab 01 |
| Event / Message | `webhook_events` / `EventEntity` | Lab 02 |
| Delivery / EventDelivery | `webhook_deliveries` / `DeliveryEntity` | Lab 02 |
| Worker / Dispatcher | `DeliveryWorker` | Lab 03 |
| Attempt | `webhook_delivery_attempts` / `DeliveryAttemptEntity` | Lab 04 |
| Retry policy / Backoff | `RetryPolicy` / `RetryDecision` | Lab 04 |
| Dead-letter / terminal failure | `DeliveryStatus.FAILED` plus query/view | Lab 04 |
| Manual replay / redelivery | Not implemented yet | Later lab |
| Endpoint subscription/filter | Not implemented yet | Later lab |
| Multi-worker locking | Not implemented yet | Later lab |
| Metrics / dashboard | Not implemented yet | Later lab |

## Retry Policy References

These references should shape Lab 05 and later retry work:

| System | Retry behavior to notice |
|---|---|
| Convoy | Linear/exponential retry options, capped retry budgets, jitter guidance, and configurable retry timing. |
| Svix | Public exponential schedule: immediate, 5 seconds, 5 minutes, 30 minutes, 2 hours, 5 hours, 10 hours, 10 hours. |
| Stripe | Automatic live-mode retries for up to three days using exponential backoff, with dashboard visibility into future retries and delivery attempts. |
| GitHub | No automatic retry for failed webhook deliveries; instead, failed deliveries can be redelivered manually or through the API for a limited retention window. |
| Codehooks template | Queue-based delivery with exponential backoff and auto-disable after repeated failures. |
| Flack74/Webhook-Delivery-Platform | Delayed retry and dead-letter state on top of durable PostgreSQL delivery records and async workers. |

Course simplification:

```text
Lab 05 keeps only three attempts and deterministic short delays.
It borrows the reference pattern, not the production timing values.
```

## Current Deliberate Simplifications

These are intentional teaching simplifications, not final production design:

- We currently fan out each Event to all Endpoints. Production systems usually support subscriptions
  or event-type filtering.
- We do not have tenant/application ownership yet. Production systems normally scope endpoints,
  events, deliveries, and secrets to an application or customer.
- We do not have queue middleware yet. The database is currently the durable work source.
- We do not have multi-worker locking yet. Lab 06 should introduce safe concurrent claiming.
- We do not have endpoint signing yet. Production webhook delivery must sign outbound requests.
- We do not have rate limits, endpoint disablement, manual replay, or dashboards yet.

## Guardrails For Future Labs

Before adding a new lab:

1. Identify the corresponding concept in at least one open-source webhook project or production
   provider document.
2. Record whether our design is copying the reference pattern or intentionally simplifying it.
3. Avoid adding a class just because it seems clean. Add it only if the reference systems have a
   similar responsibility or if the simplification is clearly documented.
4. Keep the naming connected to production vocabulary: Event, Message, Endpoint, Delivery,
   Attempt, Dispatcher, Retry, Backoff, Dead Letter, Replay.

## Notes On Codehooks Template

The Codehooks template is useful as a compact reference because it shows the delivery problem as a
small end-to-end app rather than a large platform. For this course, treat it as a reference for the
high-level flow:

```text
receive webhook/event
  -> persist delivery work
  -> dispatch to target endpoints
  -> store delivery result
```

It is not enough by itself for the full course because our project also needs explicit relational
schema design, attempts, retry policy, worker tests, observability, and production debugging drills.
