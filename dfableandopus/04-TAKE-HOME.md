# Take-home: question tagging with a safe migration

Budget 3 to 4 hours. Work in this repository. You will touch validation, persistence and the CLI command boundary, which is the same triangle a web take-home tests with a request body, a repository and a route.

## The brief

Every question in the bank should be able to carry zero or more short tags, for example `java`, `collections`, `io`. The CLI should let a user filter the quiz to a single tag.

### Functional requirements

1. `Question` gains a `List<String> tags` field. Tags are trimmed, lowercased, deduplicated preserving first-seen order, and each must be 1 to 24 characters of `a-z`, `0-9` or `-`. A question may have 0 to 8 tags. Anything else is rejected by the constructor with a message in the style of the existing ones.
2. The create and update forms in `App.edit` (`src/quiz/App.java:44`) ask for tags as one comma-separated line after the explanation. An empty line means no tags. End of input at this prompt behaves exactly like the other fields: `interrupted()` is called and nothing is applied.
3. Tags persist. The versioned format in `QuestionBank.serialize` (`src/quiz/QuestionBank.java:70`) currently writes id, prompt, answer, explanation, choice count, then the choices. Extend it, and bump `HEADER` to `QUIZ-BANK\t2`.
4. **A v1 file must still load.** `QuestionBank.parse` (line 80) currently rejects any header that is not the exact constant (line 82) and the test at `test/quiz/QuestionBankTest.java:53` depends on unknown versions being refused. Accept version 1 as tagless, accept version 2, and still refuse version 99. The first accepted edit upgrades the file to v2, same as the legacy-to-v1 conversion today.
5. A new menu option `8` runs a quiz filtered to one tag. If no question carries that tag, print a message and return to the menu without starting a quiz, in the same spirit as the empty-bank guard at `src/quiz/App.java:69`.

### Non-functional requirements

- No change to the guarantees of `commit`. All persistence still goes through it. Do not add a second write path.
- Nothing may be printed as saved before the atomic move returns.
- The footer count check must still hold.
- `data/questions.txt` is not to be modified. Tests use their own temp files, as they do now.

## What to submit

A patch or branch containing:

- the source changes,
- new cases appended to `test/quiz/QuestionBankTest.java`,
- a short `NOTES.md` (max one page): the format decision and why, the v1 compatibility approach, what you did not do and why, and one thing you would change with another four hours.

## Tests

Add your cases to the existing suite. It is a hand-rolled runner, not JUnit: a case is `test("name", () -> { ... })` using the helpers `equal`, `truth` and `rejects` defined at `test/quiz/QuestionBankTest.java:20`. Use `fixture()` (line 27) for a temp legacy bank.

Run everything with:

```powershell
.\run.ps1 -Test
```

That compiles `src/quiz/*.java` plus `test/quiz/*.java` with `--release 17 -Xlint:all` and runs `quiz.QuestionBankTest`. The final line must read `RESULT passed=` with a number at least 28 plus the cases you added. A compiler warning is a failure for this exercise, because the runner uses `-Xlint:all`.

At minimum cover: tag normalization and dedupe; a rejected tag character; the 8-tag ceiling; a v2 round-trip through a reload; a v1 file loading tagless and then being upgraded by one edit; a v99 file still rejected; the tag filter returning an empty selection.

## Grading rubric

| Criterion | Strong | Weak |
|---|---|---|
| Validation placement | Tag rules live in the `Question` constructor beside `validateChoices`, so no invalid `Question` can exist anywhere | Tags validated in `App.edit`, leaving `QuestionBank` and the parser able to build invalid objects |
| Format migration | Header versioned, v1 parsed as tagless, v99 still rejected, footer count still verified, a round-trip test for each version | Header silently reused for a new field layout, or `startsWith("QUIZ-BANK")` loosened so an unknown version parses as best-effort |
| Persistence discipline | Single `commit` boundary untouched; candidate list built then committed; in-memory publish after the move | A second write path added for tags, or `questions` mutated before `commit` succeeds |
| Encoding | Tags Base64-encoded like every other text field, or a documented reason why the restricted charset makes it safe, with a test that proves a tag cannot inject a `\t` | Tags written raw into the tab-delimited row, so a crafted tag corrupts the row |
| Error paths | EOF at the tag prompt calls `interrupted()` and applies nothing; empty filter result is a message, not an empty quiz | EOF produces a half-built question, or the filtered quiz starts with zero questions and immediately reports a 0 percent score |
| Tests | New cases assert against a *reloaded* bank and compare file bytes on failure paths, matching the style at lines 40 to 46 | Assertions only against the in-memory list, so a no-op save would pass |
| Notes | Names the backward-compatibility cost and at least one thing left undone | Claims completeness, or claims durability that the code does not provide |

## Reviewer's notes

The reviewer opens three things first, in this order.

1. **The test file.** Did new cases reload from disk? A submission whose tag test only inspects `bank.list()` has not tested persistence, and that is the single most common failure in this exercise.
2. **The migration.** Version 1 files must survive. The tempting shortcut is to change `HEADER` and let old files fall into the legacy branch at `src/quiz/QuestionBank.java:32`, which tests `text.startsWith("QUIZ-BANK")`: a v1 file would then reach `QuestionLoader` and be rejected as malformed pipe-delimited text, with a misleading error. A correct submission parses the version number from the header line and branches on it.
3. **The error paths.** Grep for `interrupted()` in `App.edit`. If the tag prompt does not have the same `if (value == null)` guard as every other field, a Ctrl-Z at the wrong moment either applies a partial record or throws a `NullPointerException` into the catch at `src/quiz/App.java:38`.

After those, the reviewer checks that `commit` is byte-identical to what it was, and runs `.\run.ps1 -Test` twice in a row to confirm nothing was left in the bank directory between runs.
