# Interview questions: Quiz Engine question bank

26 questions. Each is marked **junior** or **mid**. Technical answers cite a real file and line in this project. Where the code has a known limit, the strong answer names it.

## Group A: screening (7)

### A1. Describe the data model. **junior**

Three value types and one store. `Question` (`src/quiz/Question.java:7`) is `final` and holds `id`, `prompt`, `List<String> choices`, `correctAnswer` (a letter label) and `explanation`. `QuestionAttempt` (`src/quiz/QuestionAttempt.java:6`) pairs one `Question` with the user's chosen label. `QuizResult` (`src/quiz/QuizResult.java:5`) holds the attempts plus a `plannedQuestions` count. `QuestionBank` (`src/quiz/QuestionBank.java:23`) is the store: the file path, the bytes it last saw, and the current `List<Question>`.

### A2. Where is the validation boundary? **junior**

In the `Question` constructor, `src/quiz/Question.java:14`. It trims and rejects blank text through `requireText`, copies the choices into an immutable list with `.toList()`, uppercases the answer label, then calls `validateChoices` (2 to 26 choices) and `validateCorrectAnswer` (the label must index an existing choice). An invalid `Question` object cannot exist, so nothing downstream re-checks. That is the same reason a web handler validates the request body before the repository sees it.

### A3. Walk me through one "request". **junior**

`App.run` (`src/quiz/App.java:21`) is the dispatcher. It reads a menu number and switches at line 29. Menu 5 calls `edit(false)`: `App.edit` at line 44 collects id, prompt, choice count, each choice, the answer letter and the explanation into locals; constructs the `Question` at line 55; calls `bank.create(question)`; and only then prints "Question created and saved." at line 57. That order is the whole point: input parsing, then validation, then persistence, then the success message.

### A4. How does a failure reach the user? **junior**

`App.run` wraps the switch in two catches at `src/quiz/App.java:38`. `IllegalArgumentException` and `NullPointerException` become `"Invalid input: "` plus the message; `IOException` becomes `"Not saved: "` plus the message. So the CLI has exactly two error classes, and they map cleanly to 4xx and 5xx/409 in an HTTP port.

### A5. What identifies a record and can it change? **junior**

The `id` string. `QuestionBank.update` refuses at `src/quiz/QuestionBank.java:43` if `!id.equals(replacement.getId())` with "An update cannot change the question id." So an update is a full replacement of a record under a stable identity, never a rename. In SQL terms the primary key is immutable and the update is a whole-row `SET`.

### A6. Why are there two file formats? **mid**

The legacy format is one pipe-delimited line per question (`data/questions.txt:3`), parsed by `QuestionLoader.parseQuestion` at `src/quiz/QuestionLoader.java:45`. It cannot hold a `|`, a newline or a tab. The versioned format written by `QuestionBank.serialize` (`src/quiz/QuestionBank.java:70`) starts with the header `QUIZ-BANK\t1`, Base64-encodes every text field, and ends with `END\t<count>`. `QuestionBank`'s constructor picks the parser at line 32 by testing `text.startsWith("QUIZ-BANK")`. The limit to say out loud: the first accepted edit converts a legacy file, and an older binary cannot read the result.

### A7. What does "the app is a CLI, not a service" cost you in this interview? **mid**

There is no auth, no per-record ownership, no HTTP status codes, no SQL. `astraupskill/VERIFICATION.md` states that plainly. What transfers is the shape: an immutable domain object, a repository with a single write boundary, optimistic concurrency, and tests that use real files instead of a mocked save. `astraupskill/08-PORT-TO-THE-WEB.md` is the written proof that the transfer was thought through rather than claimed.

## Group B: deep dive (10)

### B1. What exactly does `commit` guarantee? **mid**

Three things, in `src/quiz/QuestionBank.java:53`. (1) Serialize the whole candidate bank first, line 54, so a partial record can never be written. (2) Under a sidecar `FileLock` taken at line 57, compare `original` with the current file bytes at line 59 and throw "Question bank changed. Reopen before saving; your command was not applied." if they differ. (3) Write a temp sibling and `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` at line 63, and only after that succeeds set `original = serialized; questions = List.copyOf(candidate)` at line 65. Memory is published after disk, never before.

### B2. The bug the worked change fixed: what was it? **mid**

The earlier edition was read-only, so "saved" had no meaning. The change described in `astraupskill/03-WORKED-CHANGE.md` is the class of bug where a program prints success before the write lands, or after a partial write. The fix is the ordering in B1 plus the assertion in the test "create persists and reloads" (`test/quiz/QuestionBankTest.java:40`), which constructs a *second* `QuestionBank` over the same path and expects `list().size()` to be 2. Reloading from disk is the only honest assertion for "saved".

### B3. Why does the constructor read the file twice? **mid**

`src/quiz/QuestionBank.java:30` reads the bytes, line 32 may hand the same path to `QuestionLoader` which reads it again independently, and line 34 re-reads and compares: `if (!Arrays.equals(original, Files.readAllBytes(this.file))) throw new IOException("Question file changed while loading. Reopen it.")`. Without that, the legacy path could parse bytes that no longer match the `original` snapshot, and every later staleness check would be comparing against the wrong baseline.

### B4. What breaks if you delete line 59? **mid**

The stale check. Two loaded editors would both save, and the second would silently erase the first one's record: last writer wins with no signal. The test "two loaded editors reject stale second save" (`test/quiz/QuestionBankTest.java:45`) would fail on `equal(Files.readString(f), winner)`, and so would "outside file edits are preserved" (line 46), which writes `"outside editor data"` over the file and expects `delete("Q1")` to throw rather than clobber it.

### B5. What breaks if you delete the `FileLock`? **mid**

Line 59 and line 63 stop being one unit. Two processes could both pass the byte comparison and then both `ATOMIC_MOVE`; the move itself stays atomic, so the file is never torn, but one command is lost while both report success. `ATOMIC_MOVE` gives you no-torn-file, not compare-and-swap. The test at `test/quiz/QuestionBankTest.java:47` holds the sidecar lock externally and expects `create` to throw "Another editor is saving this bank."

### B6. Why serialize the whole bank instead of appending? **mid**

Because the footer `END\t<count>` at `src/quiz/QuestionBank.java:78` is a completeness check, verified on load at line 93. Append-only writing cannot maintain it, and a crash mid-append would leave a file that parses. With whole-file replacement the only two observable states are "old file" and "new file". The test "truncated footer rejects whole bank" (`test/quiz/QuestionBankTest.java:52`) chops the footer and expects the whole load to fail rather than silently returning a short bank.

### B7. Why Base64 every field? **mid**

Because the record separator is `\t` and the row terminator is `\n`, and question text legitimately contains both. `encode` at `src/quiz/QuestionBank.java:97` makes every field alphabet-safe. The round-trip test at `test/quiz/QuestionBankTest.java:50` stores the id `Q|2`, the prompt `Café | tabs\tand\nlines` and a choice `\path|value`. Say clearly in the interview: this is encoding, not encryption, and the project README says so.

### B8. What is `decode` protecting against? **mid**

`decode` at `src/quiz/QuestionBank.java:99` builds a UTF-8 decoder with `onMalformedInput(CodingErrorAction.REPORT)` instead of the JDK default of REPLACE. Default behaviour turns invalid bytes into U+FFFD, so a corrupt file would load as a bank full of question marks and then be saved back in that corrupted state. REPORT turns it into an exception. Test: "invalid UTF8 does not become replacement characters" writes the bytes `C3 28` and expects an `IOException` (`test/quiz/QuestionBankTest.java:55`).

### B9. Explain the separation between "answered" and "planned". **mid**

`QuizResult` takes both a list of attempts and `plannedQuestions` (`src/quiz/QuizResult.java:13`), rejects a planned count below the attempt count at line 15, and exposes `getUnansweredCount` and `isComplete` at lines 20 and 21. `getFinalScorePercent` at line 43 divides by `getTotalQuestions()`, which is the answered count. So a user who answers one of two questions correctly and then hits EOF scores 100 percent on answered questions, one unanswered, and `isComplete()` is false. `App.quiz` prints all three facts (`src/quiz/App.java:71`). The test at `test/quiz/QuestionBankTest.java:64` pins exactly that.

### B10. Why is `QuestionAttempt`'s constructor calling a getter it ignores? **mid**

`src/quiz/QuestionAttempt.java:15` calls `question.getChoiceText(this.userAnswer)` purely for the side effect of validation: `getChoiceText` throws "Answer is outside this question's choices." (`src/quiz/Question.java:59`) for a label past the end. It means an attempt object cannot exist for a label the question does not offer, so `getUserAnswerText()` can never blow up later during result printing. The test at `test/quiz/QuestionBankTest.java:61` rejects `"Z"` and `""` for a two-choice question.

## Group C: behavioral, STAR (5)

### C1. Tell me about a time you had to make a system tell the truth. **mid**

*Situation:* the quiz app loaded questions from a file and could not change them; adding CRUD meant printing "saved". *Task:* make that word survive a restart and a second editor. *Action:* I put all three mutating commands behind one boundary (`QuestionBank.commit`) instead of writing files in three menu branches, added a byte-level stale check and a sidecar lock, and reordered the code so in-memory publication happens after `ATOMIC_MOVE`. *Result:* 28 regression cases pass with `./run.ps1 -Test`, including a scenario where a second loaded bank's save is rejected and the winner's bytes are compared exactly.

### C2. Tell me about handling concurrency without a database. **mid**

*Situation:* two copies of the CLI can open the same bank file. *Task:* guarantee one winner, with the loser told why. *Action:* capture the bytes at load, re-read and compare inside a `FileLock` before writing, and throw a message that tells the user to reopen. *Result:* `astraupskill/VERIFICATION.md` records a two-JVM test where both workers load, a shared gate releases them together, exactly one prints SAVED and the other prints REJECTED, and a third process then sees a two-record bank. The worker harness is in `test/quiz/QuestionBankTest.java:30`.

### C3. Tell me about a time you chose stricter defaults than the platform's. **junior**

*Situation:* Java's default UTF-8 decoding replaces malformed bytes silently. *Task:* stop a corrupt bank from being loaded and then re-saved as mojibake. *Action:* built an explicit decoder with REPORT on malformed and unmappable input (`src/quiz/QuestionBank.java:99`). *Result:* the case at `test/quiz/QuestionBankTest.java:55` proves two bad bytes now fail the load instead of becoming replacement characters.

### C4. Tell me about a limit you documented rather than papered over. **mid**

*Situation:* the versioned format is not backward compatible and file locks are advisory. *Task:* decide between pretending and disclosing. *Action:* wrote both limits into `README.md` and into `astraupskill/VERIFICATION.md`: "Sidecar locks coordinate writers that follow the protocol; arbitrary external editors can ignore them", plus no power-loss durability claim and no distinct failure exit status at startup. *Result:* the known-limits list became the backlog, and reviewers stopped having to discover the gaps themselves.

### C5. Tell me about a test you rewrote because it was not testing anything. **junior**

*Situation:* it is easy to assert a save by checking the in-memory list. *Task:* assert the thing the user actually cares about. *Action:* every persistence case in `QuestionBankTest` constructs a fresh `QuestionBank` over the same temp path and asserts on that, and the failure cases compare `Files.readString(f)` against the bytes captured before the failed command. *Result:* 28 passing cases that would have caught a silent no-op save; the cleanup block at `test/quiz/QuestionBankTest.java:70` refuses to delete anything outside the directory the test created.

## Group D: the follow-ups after your answer (4)

### D1. "You said the move is atomic. Is your save durable?" **mid**

No, and the code does not claim it. `ATOMIC_MOVE` guarantees a reader sees either the old file or the new one, never a half file. It does not `fsync` the temp file or the directory, so a power cut can lose the rename. `astraupskill/VERIFICATION.md` states "No power-loss durability claim is made." The fix would be an `FileChannel.force(true)` on the temp file before the move plus a directory sync where the platform supports it.

### D2. "If you moved this to Postgres, would you keep the lock?" **mid**

No. `astraupskill/08-PORT-TO-THE-WEB.md` maps `Arrays.equals(original, ...)` onto `UPDATE questions SET ... , version = version + 1 WHERE id = ? AND version = ?` and treats `changes === 0` as the refusal. Compare and write become one statement, so the lock has nothing to protect. The lock is only needed because the file version does compare and replace in two steps.

### D3. "What HTTP status would each of your errors be?" **junior**

Duplicate id ("Question id already exists.", `src/quiz/QuestionBank.java:39`) is 409. "Question not found." (line 44) is 404. Constructor validation failures are 400. The stale-bank `IOException` at line 59 is 409. A request that sends no version at all is 428 Precondition Required, which has no CLI equivalent because the CLI always holds a snapshot: that asymmetry is named in the mapping table of `astraupskill/08-PORT-TO-THE-WEB.md`.

### D4. "Your delete returns a boolean. Why not throw?" **mid**

`QuestionBank.delete` returns false for a missing id (`src/quiz/QuestionBank.java:50`) because absence is not an error at the repository layer; the caller decides. `App.delete` checks `bank.find(id).isEmpty()` first and prints "Question not found." (`src/quiz/App.java:62`), then requires the literal string `yes` at line 64 before calling through. In HTTP terms, the repository returning false lets the handler pick 404 versus an idempotent 204, and the test at `test/quiz/QuestionBankTest.java:44` asserts the second delete of the same id returns false.
