# Read by responsibility and data ownership

Start with [Question.java](../src/quiz/Question.java). A Question has stable identity, prompt, choices, correct answer and explanation. It is now final and immutable: input choices are copied and normalized, and getters expose an immutable list. There can be two through twenty-six choices because labels run from A through Z. A constructor that accepts an unreachable twenty-seventh choice would create invalid state even if the menu usually prevents it.

[QuestionAttempt.java](../src/quiz/QuestionAttempt.java) associates an immutable question snapshot with one valid selected answer. It rejects a label outside that question's choices. [QuizResult.java](../src/quiz/QuizResult.java) owns an immutable list of attempts and the planned question count. Answered, incorrect and unanswered are distinct values. A partial session with one correct answer out of two planned questions has one unanswered question and a 100% score on answered questions; the UI labels that denominator explicitly.

[QuizEngine.java](../src/quiz/QuizEngine.java) handles the interaction loop. Invalid input retries the same question. End-of-input returns a partial result instead of throwing Scanner's exception or fabricating a wrong answer. It receives questions as a list and does not read files. This makes it possible to test scoring and interruption independently of storage.

[QuestionLoader.java](../src/quiz/QuestionLoader.java) is the legacy parser. It reads the original pipe/semicolon format, skips comments, validates every row and rejects duplicate IDs. [QuestionBank.java](../src/quiz/QuestionBank.java) owns the current question collection and persistence commands. It accepts the legacy format on load and writes a versioned format on the first successful edit. Its public create, update and delete methods build candidate collections. Its private commit method owns locking, stale-file comparison and atomic replacement.

[App.java](../src/quiz/App.java) maps menu choices into commands. It gathers a complete form before constructing a Question, asks for deletion confirmation and prints success only after the bank accepts the save. The optional data path allows tests and learners to keep their data separate. [run.ps1](../run.ps1) compiles and launches the selected mode.

Trace a create in this order: App.edit collects fields; Question validates them; QuestionBank.create rejects duplicate identity; commit serializes a candidate; the lock and original-byte comparison arbitrate stale writers; atomic move replaces the file; the bank publishes the new immutable collection; App prints success. Then compare [QuestionBankTest.java](../test/quiz/QuestionBankTest.java) with each step. Find tests that would fail if validation, conflict detection or delayed memory publication were removed.

## Where each responsibility lives

Line ranges were checked against the code on 2026-10-06. The whole of `src/quiz` is 561 lines, so reading it all is a realistic first-day goal.

| Responsibility | Location |
|---|---|
| Domain invariants (trim, 2-26 choices, answer within range) | `Question.java:14-23`, label helpers `:63-76` |
| One answer must name a real choice | `QuestionAttempt.java:10-16` |
| Planned vs answered, score on answered only | `QuizResult.java:13-21`, `:43-49` |
| Quiz loop, retry on bad input, EOF returns null | `QuizEngine.java:18-29`, `:54-67` |
| Legacy pipe/semicolon parser | `QuestionLoader.java:10-43` |
| Format sniffing at startup, reread guard | `QuestionBank.java:28-35` |
| Create / update / delete build candidates | `QuestionBank.java:38-52` |
| The one write boundary | `QuestionBank.java:53-69` (`commit`) |
| Versioned format, footer, strict UTF-8 | `QuestionBank.java:70-101` |
| Menu, forms, delete confirmation, result printing | `App.java:21-42`, `:44-58`, `:60-66`, `:67-86` |

The menu has no "reload" entry (`App.java:26`). After a stale-save rejection, the only way to reopen today is to exit and restart. [Practice Ex 1](05-PRACTICE.md) adds one.

The lock file is a coordination artifact, not the database. It can remain after the process exits; operating-system lock ownership is tied to the open channel. Deleting the filename while another process uses it is not the intended recovery procedure.
