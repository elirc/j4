# Learn reliable CRUD with a small question bank

This project is small enough to read completely, yet it now demonstrates problems that appear in larger CRUD applications: stable identity, validated replacement, conflicting editors, durable saves, corrupt input and user cancellation. Your goal is to explain why a successful command is safe to display, then carry those habits into an HTTP service and database later.

Read the current setup at the top of [README](../README.md). Use JDK 17 or newer and run `./run.ps1 -Test`. The runner compiles the source into `out/` (git-ignored) with Java 17 compatibility and all compiler warnings, then executes the regression suite against temporary question files. For interactive work, copy `data/questions.txt` to a disposable location and pass it with `-DataPath`. The supplied example data remains unchanged by this improvement pass.

## Your first useful session

List the bank with menu 4. Create a question using menu 5: choose ID `CRUD1`, prompt “What does an atomic save preserve?”, two choices, answer `A`, and an optional explanation. Exit, restart with the same path and list again. The record survives because the create command persisted it before announcing success. Update it through menu 6 without changing its ID. Cancel a deletion once, verify the question remains, and then confirm deletion by typing `yes`.

The bank can become empty after deleting its final question. An empty versioned bank is valid data; starting a quiz then offers guidance to create a question. A missing or malformed file is a different state and stops startup. Learning to distinguish absence, emptiness and corruption will help you design clearer web APIs.

Follow [the map](01-CODEBASE-MAP.md), [concepts](02-CONCEPTS.md), [worked change](03-WORKED-CHANGE.md) and [testing guide](04-TESTING-AND-DEBUGGING.md). Attempt [practice](05-PRACTICE.md) before reading [solution guidance](06-SOLUTIONS-AND-REVIEW.md); its five exercises fix real gaps and each comes with a runnable `node --test` check. Finish with [the trace lab](07-TRACE-LAB.md) and compare your understanding with [verification](VERIFICATION.md).

Keep a short note for every change: user intent, accepted input, invariant, persistence boundary, failure message and test evidence. For example, “An incomplete question form must not add a half-built record” leads to gathering all fields first, constructing a validated Question, and only then calling create. End-of-input tests prove the boundary.

This is a local CLI and file repository, not an implemented web service. There is no login, database, browser or quiz-attempt history persistence. The course uses the actual Java code to prepare for those next steps. A mid-level engineer should be able to name the delivered guarantees and explain exactly which assumptions stop them from extending to arbitrary deployment environments.
