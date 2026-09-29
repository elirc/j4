# Now scale it: system design follow-ups for the question bank

Seven questions an interviewer asks once you have described `QuestionBank`. Each answer starts from what the code does today.

## Today's write path

```text
  App.edit                QuestionBank              filesystem
  (src/quiz/App.java:44)  (src/quiz/QuestionBank.java:53)
     |                          |                        |
     | read id, prompt,         |                        |
     | choices, answer          |                        |
     | new Question(...)  ----> validate in ctor         |
     |                          |                        |
     | bank.create(q)  -------> dup id? -> 409-shaped    |
     |                          | candidate = copy+add   |
     |                          | serialize whole bank   |
     |                          | tryLock(questions.txt.lock) -----> [.lock]
     |                          | read file, compare to `original`
     |                          |     differ? -> "Question bank changed."
     |                          | write .quiz-bank-*.tmp ----------> [tmp]
     |                          | ATOMIC_MOVE tmp -> questions.txt -> [bank]
     |                          | original = bytes; questions = copyOf
     | <---- return             |                        |
     | print "created and saved"|                        |
```

The whole bank is one document, one lock, one reader-visible swap.

## 1. Multi-tenant: many users, each with their own banks

Today there is exactly one path, supplied by `args[0]` in `App.main` (`src/quiz/App.java:19`) or defaulting to `data/questions.txt`. Tenancy is "whatever file you point at", and there is no ownership field anywhere in `Question`.

The file-per-tenant option keeps the entire concurrency model unchanged and gives hard isolation and trivial per-tenant export. It fails on cross-tenant queries and on the number of open files once you have tens of thousands of tenants. The row-level option, a `tenant_id` column with a composite primary key `(tenant_id, id)`, is what a real service does, and the trade is that isolation becomes a property of every query rather than a property of the filesystem: one missing `WHERE tenant_id = ?` leaks. I would take the row-level model plus a repository that cannot be constructed without a tenant, so the predicate is not something a handler can forget.

## 2. Concurrent writers at real volume

Today two writers are serialized by the sidecar lock and the loser is rejected outright (`src/quiz/QuestionBank.java:59`). The granularity is the *whole bank*: two people editing two different questions still conflict, because the comparison is over all bytes.

That is the thing to volunteer before the interviewer finds it. The fix is to move the version from the document to the row, exactly as `astraupskill/08-PORT-TO-THE-WEB.md` does: `UPDATE questions SET ..., version = version + 1 WHERE id = ? AND version = ?`, with `changes === 0` meaning either 404 or 409. Now two editors on different questions never collide, and two editors on the same question still get a clean refusal. The trade-off is that a bank is no longer saved as a unit, so an operation that must change several questions together needs an explicit transaction, and the "footer count" completeness check has no equivalent: you get referential integrity from the database instead.

## 3. Background jobs

There are none. Everything in this app is synchronous inside one command, and that is why "saved" can be printed truthfully at `src/quiz/App.java:57`: the write already happened.

The moment a job appears, that property is at risk. If bulk-importing a thousand questions becomes a queued job, the HTTP response can only honestly say "accepted", 202, with a job id. The strong version keeps the same discipline: the job writes rows in a transaction and only then marks itself complete, and the client polls the job resource. The anti-pattern is enqueueing and returning 201, which is the web version of printing "saved" before the move.

## 4. Pagination at a million questions

`QuestionBank` loads the entire file into memory at construction (`src/quiz/QuestionBank.java:30`) and `list()` at line 36 returns all of it; `App` menu 4 prints every row (`src/quiz/App.java:32`). At a million rows the load is the problem long before the print is.

Keyset pagination beats offset here because the natural sort key already exists and is unique: `WHERE id > :last_seen_id ORDER BY id LIMIT 50`. Offset pagination costs the database a scan of everything skipped and shifts rows under a user who is editing while paging. The trade-off is that keyset gives no page numbers and no jump-to-page, which product owners ask for. I would ship keyset for the API and, if the UI demands page numbers, back them with a separate approximate count rather than making every page pay for an exact one.

## 5. Auth and ownership

There is nothing: no user, no login, no per-record owner. `astraupskill/VERIFICATION.md` says so explicitly, and claiming otherwise in an interview is the fastest way to lose the room.

The first step is a `created_by` on the question and an authorization check *inside* the repository method rather than in the route, because this codebase already demonstrates why: the identity rule that an update cannot change the id lives in `QuestionBank.update` (`src/quiz/QuestionBank.java:43`), not in `App`, so no caller can bypass it. Authorization belongs in the same place for the same reason. The trade-off is that the repository now needs the caller's identity threaded through it, which is more plumbing than a route guard.

## 6. Observability

Today the only signal is text on stdout, and `App.run` returns after printing "The quiz could not be started: ..." (`src/quiz/App.java:23`) without a distinct exit status. `VERIFICATION.md` lists that as a known gap. A supervising script cannot distinguish a corrupt bank from a clean exit.

For a service, the three things worth instrumenting are exactly the three refusal points in `commit`: lock contention, stale-snapshot rejections, and atomic-move failures. A rising stale-rejection rate is the signal that your conflict granularity is too coarse, which is question 2 arriving as a metric instead of as a complaint. Structured logs with the record id and the observed versus expected version, plus a counter per refusal reason, is enough; distributed tracing is overkill for a single-writer store and adds latency you cannot justify yet.

## 7. Migrating this to the target stack

`astraupskill/08-PORT-TO-THE-WEB.md` already writes the Express plus `node:sqlite` version. The mapping to keep in your head: duplicate id becomes a `PRIMARY KEY` violation caught and mapped to 409; the byte comparison becomes `WHERE version = ?`; `FileLock` disappears because compare and write are one statement; the temp file plus `ATOMIC_MOVE` becomes the transaction commit; publishing memory after the move becomes sending the response after `run()` returns, with the new `ETag`.

Two things genuinely do not carry over. The 428 Precondition Required for a missing `If-Match` has no CLI analogue, because the CLI always holds a snapshot and a web client may not send one. And the whole-file footer check has no SQL equivalent; its job is done by constraints. The same design in ASP.NET is EF Core with `[ConcurrencyCheck] int Version`, where `DbUpdateConcurrencyException` is the 409.

## 8. What would you refuse to scale?

The format. Base64-per-field over a tab-delimited row with a count footer (`src/quiz/QuestionBank.java:70`) is a good answer for a file you want to be human-inspectable and corruption-evident at a few thousand rows. It is a bad answer for a million, because every read parses everything and there is no index. The honest position is that the format was designed for the size it has, and the migration trigger is not a rewrite of the serializer but a move to a database.
