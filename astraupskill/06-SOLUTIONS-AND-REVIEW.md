# Solutions and senior review

Use the sketches to check your [practice work](05-PRACTICE.md) after you have tried it. Each sketch was applied to a scratch copy of the repo on 2026-10-06. With all five applied, the five practice checks passed and `quiz.QuestionBankTest` still printed `RESULT passed=28` with no `-Xlint:all` warnings. Your version can differ, as long as the checks pass and the Java suite stays green.

## Solution sketches

**Ex 1, reload.** Add a menu case that builds a *new* `QuestionBank` and swaps it in only if construction succeeds:

```java
case "8" -> { try { bank = new QuestionBank(file); out.println("Reloaded " + bank.list().size() + " questions."); }
              catch (QuestionFileException error) { out.println("Reload failed; keeping the previous bank: " + error.getMessage()); } }
```

`QuestionFileException` extends `Exception`, not `IOException`, so the existing `catch (IOException ...)` at `src/quiz/App.java:39` does not cover it and the code will not compile without the inner catch. An `IOException` from the constructor is still caught at line 39. In both cases `bank` keeps its old value, because the assignment never happens. Also update the menu text at line 26 and the "1 through 7" message at line 36. Reloading discards nothing the user has saved: the stale session has no unsaved work because a rejected command is never applied.

**Ex 2, startup status.** Make `run()` return `boolean` (false on the startup `catch`, true after "Goodbye") and have `main` call `System.exit(1)` when it is false. Keep `System.exit` out of `run()` itself, so the Java suite or a future test can construct `App` without killing the JVM. For the message, check `Files.exists(this.file)` at the top of the `QuestionBank` constructor and throw `QuestionFileException("Question file was not found.")`. There is a race between that check and `readAllBytes`, but losing it only produces the old bare-path message, which is harmless.

**Ex 3, no score for zero answers.** In `App.quiz`, print `Score: not available, no questions were answered.` when `result.getTotalQuestions() == 0`, and the existing line otherwise. Leave `QuizResult.getFinalScorePercent` alone: it is pinned by `test/quiz/QuestionBankTest.java:63`. How to *display* "no data" is a presentation decision. Web APIs face the same choice and usually solve it with `null` rather than `0`.

**Ex 4, domain message.** Wrap the parse: `try { count = Integer.parseInt(rawCount); } catch (NumberFormatException e) { throw new IllegalArgumentException("Choose 2 through 26 choices."); }`. A reviewer should also ask why line 38 catches `NullPointerException` at all. It turns programming errors into "Invalid input" messages, and that hides bugs.

**Ex 5, empty legacy file.** Delete the `questions.isEmpty()` throw at `src/quiz/QuestionLoader.java:38-40`. No case in the Java suite depends on it. A zero-byte file then also loads as empty, because `""` does not start with `QUIZ-BANK` and goes to the legacy loader. If you decide a zero-byte file is suspicious (a truncated write by another tool), reject it explicitly in `QuestionBank` and write that decision down.

## Findings a senior reviewer would raise

Every item was reproduced on 2026-10-06 by compiling into a temp directory and driving `quiz.App` or `quiz.QuestionBankTest`.

| # | Finding | Evidence | Severity |
|---|---|---|---|
| 1 | A session that hits one stale save is stuck: there is no reload, so every later command in that session fails the same way | `src/quiz/QuestionBank.java:59` (compare), `:65` (`original` refreshed only on success); menu at `src/quiz/App.java:26` has no reopen | Medium: the user must quit and restart |
| 2 | Startup failure exits with status 0 | `src/quiz/App.java:23` returns from `run()`; `main` (line 19) ignores the outcome | Medium for any script or CI use |
| 3 | A missing file shows only its path; the "not found" message in the legacy loader is dead code on this path | `src/quiz/QuestionBank.java:30` reads bytes before `QuestionLoader.java:11` can check existence | Low |
| 4 | A comments-only legacy file is "corrupt", while an empty versioned bank is valid | `src/quiz/QuestionLoader.java:38-40` versus `QuestionBank.parse` (`:80-96`), which accepts `END\t0` | Low, but an inconsistent contract |
| 5 | Java exception text reaches the user | `src/quiz/App.java:50` `Integer.parseInt`, printed by `:38` | Low |
| 6 | `Score on answered questions: 0.00%` is printed when nothing was answered | `src/quiz/QuizResult.java:44-46`, `src/quiz/App.java:75` | Low |
| 7 | EOF at the delete prompt is silent, unlike the create/update form, which prints "Input ended. The incomplete edit was not applied." | `src/quiz/App.java:61` returns without a message; compare `:45` and `:59` | Cosmetic |
| 8 | The hand-rolled test runner stops at the first failing case, so one red run hides any later failures | `test/quiz/QuestionBankTest.java:19`: `test()` lets the exception escape `main`; cleanup in the `finally` at `:68-71` still runs | Process: fix one failure at a time, or catch per test and count |

None of these weakens the core guarantee. Nothing is shown as saved before the move returns, and no failure path corrupts the file. They are the edges a reviewer finds once the main path is right, and that is the level of review a mid-level engineer is expected to give.

## Reviewing the web and SQL step (08)

For HTTP CRUD, parse the untrusted request body outside the domain object, then let the domain constructor enforce invariants anyway. Map failures on purpose: missing record 404, invalid label 400, stale version 409 (or 412 if you use `If-Match` strictly), missing version 428. A 204 has no body, so client code must not call `response.json()` on it unconditionally.

For SQL, the update must carry its own condition: `UPDATE ... WHERE id = ? AND version = ?`. A transaction alone does not prevent a read-then-write lost update. When zero rows change, you must tell "absent" apart from "stale" with a second read, as the runnable check in 08 shows. Choices belong in a child table that is replaced in the same transaction as the parent row.

## Two tests worth copying the shape of

The existing suite already shows the two habits asked for above. One captures the exact bytes and proves nothing moved. The other proves the published list is not a live handle. They are `test/quiz/QuestionBankTest.java:46` and `:58`.

```java
            test("outside file edits are preserved", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); Files.writeString(f,"outside editor data"); rejects(IOException.class,()->b.delete("Q1")); equal(Files.readString(f),"outside editor data"); equal(b.list().size(),1); });

            test("bank list and prior snapshot cannot be mutated", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); List<Question> prior=b.list(); rejects(UnsupportedOperationException.class,()->prior.clear()); b.create(q("Q2")); equal(prior.size(),1); });
```
