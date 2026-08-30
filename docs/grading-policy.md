# Grading Policy

## 0. Current Policy

Lab 01 and Lab 02 used local closed-book graders. That was useful for locking down early API and
database contracts, but it created too much testing overhead for the reliability labs.

Starting with Lab 05, the default checkoff is manual:

- no hidden tests;
- no new autograder unless explicitly requested;
- a small number of public student tests;
- Codex review of code, database evidence, and lab notes.

Public tests still matter, but they are regression protection, not the main learning product.

## 1. Why the grader is closed-book

This section describes the older Lab 01-Lab 02 workflow.

Student-authored tests are the TDD mechanism. The autograder is an independent check that
the implementation follows the written contract rather than only the examples chosen by the
student.

The grader is not a guessing game:

- It tests only specified observable behavior or explicitly fixed architecture rules.
- It does not require one private implementation strategy.
- It does not inspect variable names, formatting preferences, or undocumented details.
- Each lab has a separate grader directory under `autograders/`.
- Grader source is committed locally but treated as closed-book until the lab is complete.

## 2. Checkoff feedback

A failed checkoff reports:

```text
checkpoint
test category
specified behavior that was violated
relevant exception or stack frame from student code
```

During the exercise, diagnose from the failing category rather than opening the grader source.
The report does not provide a replacement implementation.
When a failure message is too vague to connect to the specification, that is a grader defect
and must be corrected before grading continues.

Autograder feedback should be granular enough to guide student-owned testing. A category such as
`Lab 2 failed` is not acceptable. A category such as
`2D-VALIDATION-invalid-payload-array` is acceptable because the student can return to the spec,
write a visible test for that behavior, and fix the implementation without reading hidden tests.

Each hidden test class should group one learning area. Each test method should name one behavior.
Failure messages should point to:

- The lab checkpoint.
- The behavior category.
- The relevant spec section.
- The expected externally visible behavior.
- The observed behavior or durable side effect.

The grader may reveal which behavior failed. It must not reveal a complete implementation or turn
into the student's only test suite.

## 3. Grading gates

Each lab has scored categories and essential gates. A high numerical score cannot compensate
for an essential correctness failure such as data loss, an invalid transaction boundary, or
a broken HTTP contract.

Lab 1 uses this weighting:

| Category | Points |
|---|---:|
| Build and fixed architecture | 10 |
| Migration and persistence | 20 |
| Create endpoint | 20 |
| Read endpoints | 15 |
| Validation and error contract | 20 |
| Student test quality | 10 |
| Code quality | 5 |

Passing requires at least 85/100 and all essential gates.

## 4. Regression rule

Starting with Lab 2, every checkoff also runs the completed behavior from earlier labs. A later
change that breaks Endpoint Registration blocks the later lab even when its new tests pass.

## 5. Student-test quality

The grader and code review evaluate whether submitted tests would detect representative broken
implementations. It does not award points merely for a test count or a coverage percentage.

Good tests are independent, deterministic, behavior-named, and verify both the response and
important side effects. Tests must not require the public internet or depend on execution
order.

## 6. Local grader boundary

The grader is not encrypted or technically hidden. The student owns the machine and can read
it. Not opening `autograders/<lab>/src/test` before completing the lab is an honor-system rule.

Normal student development uses `mvn test`; this does not execute the grader. A checkpoint is
submitted with:

```bash
./autograders/lab01/grade.sh 1C
```

Checkpoint runs are cumulative: `1C` also reruns the automated gates from `1A` and `1B`.
