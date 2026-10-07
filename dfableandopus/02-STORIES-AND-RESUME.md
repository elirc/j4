# Stories and resume lines: Quiz Engine question bank

## Story 1: the word "saved" had to survive a restart

**Situation.** The quiz engine loaded questions from `data/questions.txt` and ran quizzes. Nothing wrote back. Adding create, update and delete to the menu meant the program would start printing "Question created and saved." and a user would believe it.

**Task.** Make that sentence true under three hostile conditions: a restart, a second editor with the same file open, and a crash partway through a write.

**Action.** I gave all three mutating commands one shared write boundary. `create`, `update` and `delete` in `src/quiz/QuestionBank.java` each validate identity, build a candidate `List<Question>`, and call `commit`. `commit` serializes the entire candidate bank before touching disk, takes a cooperating lock on a `.lock` sibling, compares the destination byte for byte against what this instance last saw, writes a temp file, and asks for `ATOMIC_MOVE`. The fields `original` and `questions` are reassigned only after the move returns.

**Result.** `./run.ps1 -Test` reports `RESULT passed=28`. The delivery record also lists nine separate-process scenarios from a harness that is not in this repo. The persistence cases do not trust memory: each builds a second `QuestionBank` over the same path and asserts on what came back from disk.

## Story 2: two JVMs, one gate, one winner

**Situation.** "It handles concurrent editors" is the kind of claim that is usually tested by running two saves one after the other, which proves nothing.

**Task.** Prove that two writers who both loaded the same state cannot both succeed.

**Action.** I built a worker mode into the test binary (`test/quiz/QuestionBankTest.java:30`): a worker loads the bank, writes a "ready" file, then spins until a shared gate file appears, and only then calls `create`. A driver starts two fresh JVMs, waits for both ready files, touches the gate, and reads both stdout streams. The original driver was not committed; `astraupskill/04-TESTING-AND-DEBUGGING.md` has a node replacement that reproduced the result 3 of 3 times on 2026-10-06.

**Result.** `astraupskill/VERIFICATION.md` records exactly one SAVED and one REJECTED, and a third process afterwards observing the expected two-record bank. That is the difference between a concurrency claim and a concurrency test.

## Story 3: refusing the platform's silent default

**Situation.** Java's convenience decoders replace malformed UTF-8 bytes with U+FFFD. A bank file damaged by a bad transfer would have loaded as a wall of question marks and then been written back in that state on the next edit, turning a recoverable file into a permanently corrupt one.

**Task.** Make corruption loud and non-destructive.

**Action.** `decode` builds the decoder explicitly with `onMalformedInput(CodingErrorAction.REPORT)` (`src/quiz/QuestionBank.java:99`). The same instinct produced the whole-bank rejection rules: a truncated `END` footer, an unknown header version, malformed Base64 or one broken legacy row all reject the entire load.

**Result.** Six format-rejection cases pin this, including `test/quiz/QuestionBankTest.java:55`, which writes the two bytes `C3 28` and expects an `IOException` rather than a bank containing a replacement character.

## Resume bullets

- Added create, update and delete to a Java 17 question-bank CLI behind a single write boundary, so a command is persisted and verified on disk before it is reported as successful; 28 regression cases pass.
- Implemented optimistic concurrency over a plain file using a byte-level compare against the loaded snapshot plus a cooperating sidecar `FileLock`, then proved it with two gated JVMs racing one save: exactly one winner, loser told to reopen.
- Replaced a lossy pipe-delimited line format with a versioned, Base64-field format carrying a record count footer, allowing delimiters, tabs, newlines and non-ASCII text to round-trip without escaping bugs.
- Hardened file loading with a REPORT-mode UTF-8 decoder and whole-file rejection of truncated footers, unknown versions and duplicate ids, preventing silent corruption from being written back on the next edit.
- Separated planned from answered questions in quiz scoring so an interrupted session reports answered, unanswered and a score scoped to answered questions, instead of scoring unanswered questions as wrong.

## 60-second spoken summary

"j4 is a small Java 17 command-line quiz app that I extended from read-only into a full CRUD question bank. The interesting part is not the menu, it is the write path. All three mutating commands funnel into one method, `commit`. It serializes the whole bank, takes a cooperating lock on a sidecar file, re-reads the destination and compares it to the bytes this process saw when it loaded, writes a temporary file, and does an atomic rename. In-memory state is updated only after the rename returns, which is why the program can honestly print 'saved'. That gives me optimistic concurrency over a plain text file: if another editor got there first, my command is refused and nothing is lost. I proved it with two JVMs released through a shared gate, where exactly one save wins. It is a CLI, so there is no HTTP or SQL, but the pattern maps one to one: the byte comparison becomes `UPDATE ... WHERE version = ?`, a zero row count becomes 409, and a request with no version becomes 428."

## What I would do next

From `astraupskill/05-PRACTICE.md` and the limits listed in `VERIFICATION.md`:

1. Make startup failure observable to a shell: today `App.run` prints "The quiz could not be started: ..." and returns (`src/quiz/App.java:23`) with no distinct exit status, so a script cannot tell a corrupt bank from a clean exit.
2. Force the temp file and its directory before the `ATOMIC_MOVE` so the durability story matches the atomicity story.
3. Build the Express + SQLite port sketched in `astraupskill/08-PORT-TO-THE-WEB.md` and write its three tests: duplicate id is 409, stale `If-Match` is 409 with the row unchanged, fresh `If-Match` is 200 with a moved `ETag`.
4. Persist quiz attempt history, which today dies with the process.
