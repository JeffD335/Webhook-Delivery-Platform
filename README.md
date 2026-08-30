# Fault-Tolerant Webhook Delivery Platform

A test-driven systems project for learning reliable backend engineering with Java, Spring
Boot, PostgreSQL, asynchronous delivery, crash recovery, and observability.

## Current assignment

**Lab 1 — Endpoint Registry**

1. Read the [course overview](docs/00-course-overview.md).
2. Read the [grading policy](docs/grading-policy.md).
3. Complete [Lab 1](docs/labs/lab01-endpoint-registration.md) one checkpoint at a time.
4. Write each checkpoint's student tests before its implementation.

Run the local closed-book grader only after completing a checkpoint:

```bash
./autograders/lab01/grade.sh 1A
```

Replace `1A` with the checkpoint being submitted. The grader is cumulative.

The previous tutorial-style Lab 1 and its sample tests are preserved under
`docs/archive/lab01-v1/`; they are not part of the current assignment.

## Current architecture

The project starts as a modular Spring Boot application backed by PostgreSQL and Flyway.
Message brokers, Redis, Kubernetes, and service decomposition are deliberately deferred until
a later lab establishes a concrete requirement for them.

```text
Event Producer -> Webhook Platform -> Subscriber Endpoint
```

## Useful commands

```bash
mvn test
docker compose up -d postgres
mvn spring-boot:run
```

Run the full local stack with Docker:

```bash
docker compose up --build
```

The application will be available at:

```text
http://localhost:8080
```

PostgreSQL is exposed for local IDE/database tools at:

```text
localhost:5432
database: webhook_platform
username: webhook
password: webhook
```

## Local webhook demo

For the dashboard demo, run three pieces locally:

```text
React Dashboard -> Spring Boot Platform -> Demo Receiver
```

Start PostgreSQL and the demo receiver:

```bash
docker compose up -d postgres receiver
```

Or run the receiver directly without Docker:

```bash
python3 tools/test_receiver.py
```

When Spring Boot runs from IntelliJ IDEA, create the endpoint with:

```text
http://127.0.0.1:9090/webhook
```

When Spring Boot runs inside the Docker Compose `app` service, create the endpoint with:

```text
http://receiver:9090/webhook
```

The receiver also supports failure experiments:

```text
http://127.0.0.1:9090/fail
http://127.0.0.1:9090/slow
```

