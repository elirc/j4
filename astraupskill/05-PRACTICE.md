# Practice toward a database-backed CRUD application

The following tasks are proposed extensions. Complete them after tracing the delivered file-backed commands. Each exercise should include a short user story, an invariant, a failing test and an explanation of the chosen boundary.

## 1. Search and page questions

Add a case-insensitive prompt search and a bounded page size. Preserve stable ordering so moving between pages does not randomly repeat records. Decide whether IDs participate in search. Test zero matches, the final short page and invalid page numbers. Keep search results immutable so a caller cannot bypass create/update/delete by modifying a returned collection. In a future API, map this behavior to validated query parameters rather than accepting raw database expressions.

## 2. Export without changing the authoritative bank

Add a human-readable export command that writes to a separate user-selected path. It should report failures without changing the loaded bank or its remembered bytes. Include delimiter characters and non-ASCII text. Explain why exporting to the current authoritative path would interfere with stale-file checks, and either reject that destination or define a safe explicit operation. Do not describe Base64 as protection for correct answers.

## 3. Save quiz-attempt history

Introduce an AttemptSession containing a stable ID, start time, planned count, immutable question snapshots and completed answers. Define how an interrupted session is represented. Do not infer “incorrect” from “unanswered.” Decide whether later question edits should affect older results; a historical snapshot usually gives clearer review behavior. Test reload after interruption and validate the relationship between planned count and actual attempts. Keep question-bank editing and attempt-history storage as separate responsibilities.

## 4. Add a real HTTP boundary

Expose question create/read/update/delete through a Java HTTP framework of your choice. Validate request bodies and return explicit 201, 200, 204, 400, 404 and conflict outcomes. Keep correct answers out of the learner-facing quiz response if you intend a server-scored experience. Use distinct teacher and learner response models. A route must not dump every field from Question merely because it is convenient. Add real HTTP tests for unknown fields, missing IDs and failed saves before building a browser form.

## 5. Move to SQL with per-record versions

Design Question and Choice tables, unique identity constraints and a version column. Replace whole-file byte comparison with a conditional update under the version the editor read. A transaction must replace a question's choices and correct-answer relationship together. Demonstrate that two users editing different questions can both succeed, while two stale edits of the same question have one winner. This exercise makes the cost of the current coarse file-level conflict policy visible.

## 6. Add ownership and operational checks

Design teacher ownership or class membership from authenticated server identity. Test that knowing another teacher's question ID does not grant edit access. Add a migration plan for legacy banks and a backup/restore drill using disposable copies. Separate a process being alive from its storage being writable. End by documenting which failure modes you tested and which assumptions still depend on the deployment filesystem or database.
