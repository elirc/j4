# Flashcards: Quiz Engine question bank

34 pairs. Cover the right column. Every answer is checkable in `src/quiz/` or `test/quiz/`.

| Question | Answer |
|---|---|
| Which method is the single write boundary? | `QuestionBank.commit`, `src/quiz/QuestionBank.java:53` |
| Which three public methods call it? | `create`, `update`, `delete` (lines 38, 42, 49) |
| What is the versioned header constant? | `QUIZ-BANK\t1`, field `HEADER` at line 24 |
| What is the last line of a serialized bank? | `END\t` followed by the record count, line 78 |
| How does the constructor choose a parser? | `text.startsWith("QUIZ-BANK")` at line 32, else `QuestionLoader` |
| What does the constructor do after parsing? | Re-reads the file and throws "Question file changed while loading. Reopen it." if the bytes moved (line 34) |
| What field holds the optimistic-concurrency baseline? | `private byte[] original`, line 26 |
| Exact stale-save message? | "Question bank changed. Reopen before saving; your command was not applied." |
| Exact lock-contention message? | "Another editor is saving this bank. Retry after reopening." |
| Which lock call is used, and why that one? | `channel.tryLock()`, non-blocking, so a busy bank fails fast instead of hanging |
| What is locked, the bank or something else? | A sibling `<filename>.lock`, built at line 55 |
| Which exception is caught and folded into the lock message? | `OverlappingFileLockException`, line 66 (same-JVM contention) |
| What two copy options does the rename use? | `ATOMIC_MOVE` and `REPLACE_EXISTING`, line 63 |
| Why must the temp file be a sibling? | Same volume, otherwise `ATOMIC_MOVE` is not supported |
| What are the last two statements of a successful commit? | `original = serialized; questions = List.copyOf(candidate);` line 65 |
| Does the code claim power-loss durability? | No; `astraupskill/VERIFICATION.md` states no such claim is made |
| Which encoding is applied to every text field? | Base64 of UTF-8 bytes, `encode` at line 97 |
| Is that encryption? | No. The README says Base64 is encoding, not encryption |
| What decoder setting stops silent corruption? | `CodingErrorAction.REPORT` on malformed and unmappable input, line 100 |
| Valid choice-count range? | 2 through 26, `Question.validateChoices`, `src/quiz/Question.java:79` |
| Why 26? | Labels are single letters A to Z, `indexToAnswerLabel` line 63 |
| What does `answerLabelToIndex` reject? | Anything not exactly one letter A to Z, line 71 |
| Where is a question validated? | In the `Question` constructor, line 14; it calls `validateChoices` then `validateCorrectAnswer` |
| What does `requireText` do besides reject blanks? | Trims and returns the trimmed value, line 96 |
| Why does `QuestionAttempt`'s constructor call `getChoiceText`? | To reject a label outside the question's choices at construction, `src/quiz/QuestionAttempt.java:15` |
| Message when a label is past the last choice? | "Answer is outside this question's choices.", `src/quiz/Question.java:59` |
| Message when an update tries to change the id? | "An update cannot change the question id.", `src/quiz/QuestionBank.java:43` |
| What does `delete` return for a missing id? | `false`, line 50, without touching disk |
| What confirmation does the CLI require to delete? | The literal string `yes`, case-insensitive, `src/quiz/App.java:64` |
| Two counts a `QuizResult` carries? | Attempts and `plannedQuestions`, `src/quiz/QuizResult.java:13` |
| What does `getFinalScorePercent` divide by? | Answered questions only, line 48 |
| What does `isComplete()` mean? | `getUnansweredCount() == 0`, line 21 |
| What does EOF mid-quiz produce? | `readValidAnswer` returns null (`src/quiz/QuizEngine.java:57`), the loop breaks, result is partial |
| What does the CLI print for an incomplete form? | "Input ended. The incomplete edit was not applied.", `src/quiz/App.java:59` |
| Two error prefixes in `App.run`? | "Invalid input: " for `IllegalArgumentException`/`NullPointerException`, "Not saved: " for `IOException`, line 38 |
| How many regression cases pass? | 28, plus nine separate-process scenarios; `RESULT passed=28` |
| Command to run the suite? | `.\run.ps1 -Test` |
| Compiler flags the runner uses? | `--release 17 -Xlint:all`, `run.ps1:10` |
| SQL equivalent of the byte comparison? | `UPDATE ... WHERE id = ? AND version = ?`, per `astraupskill/08-PORT-TO-THE-WEB.md` |
| Which status code has no CLI equivalent, and why? | 428, because the CLI always holds a snapshot while a web client may send no `If-Match` |
