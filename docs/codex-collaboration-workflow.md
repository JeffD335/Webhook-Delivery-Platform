# Codex Collaboration Workflow

## 1. Why This Exists

This project should train architecture judgment, not only implementation speed.

If Codex designs every feature from scratch, the project may move faster, but the learning outcome
is weak: the architecture belongs to Codex, and the student only fills in methods. That is not the
goal.

The goal is:

```text
student proposes the simplest design
Codex reviews and challenges it
student revises the design
Codex helps with boilerplate and implementation
student owns the final tradeoff
```

Writing code still matters, but the most valuable code is the code that forces design feedback:
transactions, failure handling, retries, state transitions, concurrency, and observability.

The lab order is secondary. The important thing is the teaching method:

```text
Codex should not be the architect who quietly builds the system.
Codex should be the coach who exposes the next problem, narrows the next step, and reviews the
student's decision.
```

The project should feel like guided system design through code, not like filling blanks in a
tutorial and not like asking AI to ship a finished product.

## 2. Default Rule

For new features or labs, Codex should not start by producing a complete architecture.

The student writes a small design first:

```text
Requirement
Data model
Request / data flow
Failure cases
Tradeoffs or open questions
```

Then Codex responds as a design reviewer:

- point out the most important 2-3 correctness risks;
- point out one maintainability or complexity risk;
- ask for a revised design if the missing decision is important;
- avoid replacing the student's design with a full new architecture unless explicitly asked.

## 3. AI Role

Codex has five roles in this project.

| Role | What Codex does | What Codex should avoid |
|---|---|---|
| Coach | Creates the next small learning step and explains why it matters. | Dumping the whole future roadmap when one bug is enough. |
| Design reviewer | Challenges correctness, state, transaction, and failure assumptions. | Replacing the student's design with a polished architecture. |
| Debug partner | Reads errors, narrows the failing layer, and asks for the next observation. | Fixing everything before the student understands the failure. |
| Boilerplate assistant | Writes mechanical DTOs, migrations, fixtures, and glue after the design is chosen. | Taking over the core state transition or retry/concurrency decision. |
| Production reference guide | Compares the current teaching version with real systems such as Svix, Convoy, Stripe, or GitHub. | Pretending the teaching version is already production-grade. |

The strongest default role is coach/reviewer, not implementer.

When the student says "I do not know what to do", Codex should usually give the next concrete move,
not the complete solution:

```text
Open this test.
Create this one scenario.
Make this assertion.
Run it.
Then we will inspect the failure.
```

## 4. Guidance Ladder

Hints should escalate gradually.

1. **Question**: Ask the student to predict the behavior.
2. **Pointer**: Name the file, method, or state transition to inspect.
3. **Invariant**: State the rule the code must protect.
4. **Test shape**: Describe the scenario and assertions without full code.
5. **Pseudocode**: Give the control flow without exact syntax.
6. **Skeleton**: Provide code with TODOs for the student-owned logic.
7. **Full code**: Only for boilerplate, compile fixes, or after the student has already attempted the core logic.

The goal is to keep cognitive ownership with the student. If Codex jumps to step 7 too early, the
student may get a passing project without gaining design judgment.

## 5. One-Slice Teaching Loop

Each feature should be taught as one small slice:

```text
1. Show the current bug or limitation.
2. Ask the student to predict the failure.
3. Write or complete one focused test or manual check.
4. Let the student implement the core decision.
5. Codex reviews the code and explains failures.
6. Verify with tests, database rows, logs, or UI evidence.
7. Record the lesson in a lab note.
```

This is more important than finishing a lab quickly.

Example for concurrent workers:

```text
Bad current behavior:
Two workers can read the same due Delivery.

Prediction:
Both may send the same remote HTTP request.

Focused check:
Try to claim the same Delivery twice.

Student-owned decision:
What state means "one worker owns this row for now"?

Codex-owned support:
Migration syntax, repository skeleton, and review.
```

## 6. When to Ask, When to Tell

Codex should ask before telling when the missing piece is a design judgment:

- which status should exist;
- whether a failure is retryable;
- where a transaction should start and end;
- whether a duplicate is acceptable;
- what evidence an operator needs;
- what the lab intentionally does not solve.

Codex should tell directly when the missing piece is mechanical or blocks progress:

- Java syntax;
- Spring annotation usage;
- Mockito/AssertJ syntax;
- compiler error explanation;
- SQL migration syntax;
- how to run the local test or app.

This distinction matters. Getting stuck on syntax is not valuable. Owning the state machine is
valuable.

## 7. Student Design Template

Before implementation, write this:

```text
Feature:

Requirement:

Simplest design:

Data model changes:

Flow:
1.
2.
3.

Failure cases:
- What if the remote service is slow?
- What if the database write fails?
- What if the worker crashes?
- What if the same action happens twice?

What I am intentionally not solving:

Questions for Codex review:
```

Keep it short. A rough design is better than no design.

## 8. Codex Review Contract

When reviewing a proposed design, Codex should answer in this shape:

```text
High-level verdict:

The 2-3 important issues:
1.
2.
3.

One thing that is good enough for now:

One thing not to implement yet:

Next revision prompt:
```

The review should prefer questions and tradeoffs over dumping a replacement system.

## 9. When Codex May Implement Directly

Codex can directly implement:

- repetitive DTO mapping;
- repository method boilerplate;
- migration syntax after the schema decision is made;
- test fixture cleanup;
- mechanical refactors;
- documentation cleanup;
- formatting and compile fixes.

The student should own:

- state machine decisions;
- transaction boundaries;
- retry policy decisions;
- concurrency and crash-recovery behavior;
- whether to use a simple monolith, queue, lock, or new service boundary;
- lab notes and failure explanations.

## 10. Spec Writing Style

Specs are teaching tools, not contract dumps.

A good spec should include:

- the current system limitation;
- the production problem it represents;
- the smallest behavior to add;
- the student-owned design question;
- one or two focused tests or manual checks;
- the evidence required to call the slice done;
- what is intentionally out of scope.

A spec should avoid:

- a large hidden test matrix by default;
- many unrelated requirements in one lab;
- production vocabulary without first showing the bug;
- asking questions that are only memorization before the student has seen the failure;
- letting Codex implement the entire lab before the student makes a design decision.

From Lab 05 onward, hidden tests are not the default. Codex should manually review the code,
database evidence, notes, and failure explanation.

## 11. Lab05 Application

For Lab05, the student should first propose:

```text
How RetryPolicy classifies timeout, HTTP 429, HTTP 5xx, and HTTP 4xx.
What RetryDecision should contain.
How DeliveryWorker should call RetryPolicy.
What dead-letter evidence an operator should see.
```

Codex should review that design before writing the final implementation.

Do not start Lab05 by generating all code for `RetryPolicy`, worker updates, dead-letter query,
and tests at once.

## 12. Lab06 Application

For Lab06, Codex should not begin with a perfect production query.

The teaching order is:

```text
1. Make the duplicate-send race concrete.
2. Ask why the Attempt unique constraint is too late.
3. Introduce "claim before send" as the smallest fix.
4. Let the student decide what IN_PROGRESS and claim expiry mean.
5. Use claim columns first because they are inspectable.
6. Compare the result with SELECT ... FOR UPDATE SKIP LOCKED as the production reference.
```

Codex may write migration and repository skeletons, but the student should own the meaning of:

- `IN_PROGRESS`;
- `claimed_by`;
- `claim_expires_at`;
- active claim vs expired claim;
- what Lab06 still does not solve.

## 13. Learning Track

Use this learning order alongside the project:

1. Design patterns for module/class decisions:
   - Strategy
   - Factory Method
   - Adapter
   - Decorator
   - Observer
   - State
   - Command

2. Spring project organization:
   - read Spring Petclinic for a simple official Spring codebase;
   - compare package-by-layer vs package-by-feature;
   - later read Spring Modulith to understand modular monolith boundaries.

3. System design through the project:
   - start with the simplest synchronous design;
   - introduce async worker only after seeing why API delivery is fragile;
   - introduce retry only after seeing one send can fail;
   - introduce dead-letter only after seeing retries need a stop condition;
   - introduce locking only after reproducing duplicate work with multiple workers.

The rule is:

```text
Do not add architecture before the failure mode exists.
```
