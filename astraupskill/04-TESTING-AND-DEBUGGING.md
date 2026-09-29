# Verify persistence, conflict and interruption

Run `./run.ps1 -Test` with JDK 17 on PATH. The suite is a plain Java executable with explicit assertions, so there is no test framework dependency to install. Compilation uses `--release 17 -Xlint:all`. [QuestionBankTest](../test/quiz/QuestionBankTest.java) creates its own temporary directory and cleans only descendants of that directory. Never change the fixture path to the checked-in question file to make a failing test easier to inspect.

The 28 Java cases cover legacy loading without rewriting bytes, durable create/update/delete, deletion of the final question, duplicate identity, stale instances, outside edits, held locks, failed destination reads, temporary-file cleanup, Unicode and delimiter round-trips, empty explanations, malformed formats, duplicate legacy rows, immutable snapshots, choice bounds and interrupted quizzes. These are behavioral checks: they assert what survives on disk or what the caller can observe, rather than merely repeating an implementation expression.

The separate verification harness adds nine process-level scenarios. A create in one JVM is read by a new JVM. Updates and confirmed deletes are also reloaded. Cancelled deletion and incomplete input preserve exact bytes. Corrupt startup does not rewrite the file. A final concurrency scenario starts two JVM workers, waits until both have loaded the same bank, releases their saves together and expects one saved result and one rejection. A subsequent process sees exactly the original question plus one winner.

## Without PowerShell

```bash
javac --release 17 -Xlint:all -d out $(find src/quiz test/quiz -name '*.java')
java -cp out quiz.QuestionBankTest        # the tests; or: java -cp out quiz.App data/questions.txt
```

## Investigate a stale-save report

First compare what each process loaded with what is currently on disk. Do not delete the lock file as a general fix. A stale bank must reopen even if no process currently holds the lock, because its remembered bytes no longer match. The lock solves overlapping cooperating writes; the byte comparison solves edits based on an older snapshot. They address different problems.

For a malformed bank, copy the file before investigation. Check the header, final count and encoded fields. A truncated final row should stop the entire load, not leave a partly usable bank that later overwrites omitted questions. Base64 errors and invalid UTF-8 are rejected. The old loader also rejects duplicate IDs and malformed later rows instead of returning earlier valid records.

For a quiz interrupted by EOF, inspect planned, answered and unanswered counts separately. One answered question is not a completed two-question quiz. The percentage is computed over answered attempts and the UI says so. Test input such as `A` followed by EOF to expose this distinction.

The suite does not test physical power loss, network filesystems or arbitrary editors ignoring locks. It also does not test a browser or HTTP server because none is implemented. Keep those limits with the passing evidence when describing the project to a reviewer.
