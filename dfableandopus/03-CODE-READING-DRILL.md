# Code reading drill: Quiz Engine question bank

Six drills for a shared-screen round. Give yourself five minutes each and speak the answer before opening the `<details>`.

## Drill 1: explain this function

From `src/quiz/QuestionBank.java:53`.

```java
    private void commit(List<Question> candidate) throws IOException {
        byte[] serialized = serialize(candidate).getBytes(StandardCharsets.UTF_8);
        Path parent = file.getParent(); Path lockPath = parent.resolve(file.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another editor is saving this bank. Retry after reopening.");
            if (!Arrays.equals(original, Files.readAllBytes(file))) throw new IOException("Question bank changed. Reopen before saving; your command was not applied.");
            Path temporary = Files.createTempFile(parent, ".quiz-bank-", ".tmp");
            try {
                Files.write(temporary, serialized);
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
            original = serialized; questions = List.copyOf(candidate);
        } catch (java.nio.channels.OverlappingFileLockException error) {
            throw new IOException("Another editor is saving this bank. Retry after reopening.", error);
        }
    }
```

<details>
<summary>Answer</summary>

Five phases. **Serialize first** so nothing partial can reach disk and so a serialization failure costs nothing. **Lock**: `tryLock` is non-blocking, so a busy bank fails fast with a message rather than hanging; a null lock means another process holds it, while `OverlappingFileLockException` means another thread in *this* JVM holds it, and both are folded into the same user-facing message. **Compare**: `Arrays.equals(original, Files.readAllBytes(file))` is the optimistic-concurrency check; `original` is the snapshot from load or from the last successful save. **Replace**: temp sibling in the same directory (same volume, so the rename can be atomic) plus `ATOMIC_MOVE`; the `finally` deletes the temp if the move never happened, and is a harmless no-op if it did. **Publish**: the two fields are reassigned last, so any throw above leaves the previously visible state intact. Note `questions = List.copyOf(candidate)`, not `candidate`: the caller's list cannot later mutate the bank.

Strong-answer extras: `tryLock` on the *sidecar*, not on the bank file itself, because the bank file is about to be replaced by a rename and a lock on a replaced inode is meaningless. And atomicity is not durability, there is no `force`.
</details>

## Drill 2: explain this function

From `src/quiz/QuizResult.java:13`.

```java
    public QuizResult(List<QuestionAttempt> attempts, int plannedQuestions) {
        this.attempts = List.copyOf(attempts);
        if (plannedQuestions < attempts.size()) throw new IllegalArgumentException("Planned count is smaller than attempts.");
        this.plannedQuestions = plannedQuestions;
    }

    public int getPlannedQuestions() { return plannedQuestions; }
    public int getUnansweredCount() { return plannedQuestions - attempts.size(); }
    public boolean isComplete() { return getUnansweredCount() == 0; }
```

<details>
<summary>Answer</summary>

A result carries two counts, not one: how many questions the session intended to ask and how many were actually answered. `List.copyOf` defends against the caller mutating the list afterwards, which the test at `test/quiz/QuestionBankTest.java:65` checks by calling `attempts.clear()` after construction and still expecting 50 percent. The guard rejects a planned count that is smaller than the attempts, so a result cannot be constructed that would report a negative unanswered count. The payoff is `getFinalScorePercent` at line 43, which divides by answered questions only: an interrupted session does not get its unanswered questions scored as wrong, and `isComplete()` is the flag `App.quiz` uses at `src/quiz/App.java:71` to print "Quiz interrupted; partial result only."
</details>

## Drill 3: spot the bug

This is a **mutated** copy of `Question`'s constructor and `validateCorrectAnswer`. Find the defect.

```java
    public Question(String id, String prompt, List<String> choices, String correctAnswer, String explanation) {
        this.id = requireText(id, "Question id");
        this.prompt = requireText(prompt, "Question prompt");
        this.choices = Objects.requireNonNull(choices, "Choices cannot be null");
        this.correctAnswer = requireText(correctAnswer, "Correct answer").toUpperCase(Locale.ROOT);
        this.explanation = explanation == null ? "" : explanation.trim();

        validateChoices();
        validateCorrectAnswer();
    }
```

<details>
<summary>Answer</summary>

The real line (`src/quiz/Question.java:17`) is:

```java
        this.choices = Objects.requireNonNull(choices, "Choices cannot be null").stream().map(choice -> requireText(choice, "Choice")).toList();
```

The mutation drops `.stream().map(...).toList()`, which costs three things at once. (1) The field now aliases the caller's list, so the caller can mutate a validated `Question` after the fact, and `getChoices()` hands out a mutable reference. (2) Choices are no longer trimmed. `validateChoices` at line 83 still rejects blank or null entries, but `" yes "` is stored with its spaces, so stored text no longer matches what the rest of the app expects. (3) `getChoices()` no longer throws `UnsupportedOperationException`.

The suite case that dies is "choice input is copied normalized and immutable" (`test/quiz/QuestionBankTest.java:59`). It passes `new ArrayList<>(List.of(" yes ","no"))`, clears it, and expects `getChoices().get(0)` to equal `"yes"`. With the mutation, that call throws `IndexOutOfBoundsException` because the question shares the cleared list (observed by mutation, 2026-10-06). The trim and immutability assertions in the same case would fail next.
</details>

## Drill 4: spot the bug

A **mutated** copy of `QuestionBank.update`.

```java
    public synchronized void update(String id, Question replacement) throws IOException {
        if (find(id).isEmpty()) throw new IllegalArgumentException("Question not found.");
        List<Question> candidate = new ArrayList<>(questions);
        for (int i = 0; i < candidate.size(); i++) if (candidate.get(i).getId().equals(id)) candidate.set(i, replacement);
        commit(candidate);
    }
```

<details>
<summary>Answer</summary>

The missing guard is the real first line at `src/quiz/QuestionBank.java:43`:

```java
        if (!id.equals(replacement.getId())) throw new IllegalArgumentException("An update cannot change the question id.");
```

Without it, `update("Q1", questionWithIdQ2)` finds Q1, replaces the element at that index with a record whose id is Q2, and commits. If Q2 already exists the bank now holds two records with id Q2, which `find` resolves by `findFirst()` and which the versioned parser will reject on the next load at line 91 ("Duplicate question id.") After that, the file is unloadable: the bad write is persisted and the app cannot start. A repository must not let a caller rewrite a primary key through an update path. The failing case is "update missing or changed identity leaves original" at `test/quiz/QuestionBankTest.java:43`, which calls `b.update("Q1", q("Q2"))` and expects the file bytes to be unchanged.
</details>

## Drill 5: predict the output

The bank holds two questions, Q1 and Q2, each with choices A and B and correct answer A. Standard input is exactly:

```text
1
A
```

and then end of input. What does the program print for the result block?

<details>
<summary>Answer</summary>

`App.quiz` at `src/quiz/App.java:67` runs `QuizEngine.runQuiz` over a two-element snapshot. Question 1 gets `A`, which matches, so an attempt is recorded. For question 2, `readValidAnswer` hits `!scanner.hasNextLine()` at `src/quiz/QuizEngine.java:57`, returns null, `askQuestion` returns null at line 43, and `runQuiz` breaks out of the loop at line 24 with one attempt but `questions.size()` of 2 passed as `plannedQuestions` at line 28.

So `isComplete()` is false and the block reads:

```text
Quiz interrupted; partial result only.
Answered: 1 of 2
Unanswered: 1
Correct answers: 1
Incorrect answers: 0
Score on answered questions: 100.00%
```

No missed-question prompt appears, because `getIncorrectCount()` is 0 and the prompt at line 76 is guarded. This is the behaviour pinned by "EOF midway does not count unanswered as incorrect" (`test/quiz/QuestionBankTest.java:64`). The interviewer's follow-up is "is 100 percent the right number here?" and the honest answer is that the label carries the caveat: the score is explicitly scoped to answered questions, and the unanswered count is printed next to it.
</details>

## Drill 6: review this diff

Three defects. Find them all.

```diff
--- a/src/quiz/QuestionBank.java
+++ b/src/quiz/QuestionBank.java
@@
     public synchronized void create(Question question) throws IOException {
         if (find(question.getId()).isPresent()) throw new IllegalArgumentException("Question id already exists.");
-        List<Question> candidate = new ArrayList<>(questions); candidate.add(question); commit(candidate);
+        questions.add(question);
+        commit(questions);
     }
@@
     private void commit(List<Question> candidate) throws IOException {
         byte[] serialized = serialize(candidate).getBytes(StandardCharsets.UTF_8);
         Path parent = file.getParent(); Path lockPath = parent.resolve(file.getFileName() + ".lock");
         try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
              FileLock lock = channel.tryLock()) {
             if (lock == null) throw new IOException("Another editor is saving this bank. Retry after reopening.");
             if (!Arrays.equals(original, Files.readAllBytes(file))) throw new IOException("Question bank changed. Reopen before saving; your command was not applied.");
-            Path temporary = Files.createTempFile(parent, ".quiz-bank-", ".tmp");
+            Path temporary = Files.createTempFile(".quiz-bank-", ".tmp");
             try {
-                Files.write(temporary, serialized);
-                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
+                Files.write(file, serialized);
             } finally { Files.deleteIfExists(temporary); }
             original = serialized; questions = List.copyOf(candidate);
```

<details>
<summary>Answer</summary>

**Defect 1, mutating the live list.** `questions` is assigned `List.copyOf(candidate)` at `src/quiz/QuestionBank.java:65`, so it is immutable and `questions.add(question)` throws `UnsupportedOperationException` immediately. Even if it were mutable, the point of the candidate copy is that a failed commit must leave the visible bank untouched; this version publishes the new record before the write is attempted, which is the exact ordering bug the project exists to avoid.

**Defect 2, temp file in the wrong directory.** `Files.createTempFile(parent, ...)` puts the temp beside the bank; the two-argument form puts it in the system temp directory, usually a different volume, where `ATOMIC_MOVE` fails with `AtomicMoveNotSupportedException`. It also leaks the temp into a shared directory. The related test is "atomic writer leaves no temporary artifacts" at `test/quiz/QuestionBankTest.java:49`, which scans the bank's own directory for any `.quiz-bank-` prefix.

**Defect 3, the write is no longer atomic.** `Files.write(file, serialized)` truncates the destination and writes in place. A failure mid-write leaves a truncated bank, and a concurrent reader can observe a torn file. Everything the stale check bought is thrown away, because the window between "file was correct" and "file is correct again" is now the whole write. The replacement also makes the `finally` block pointless: it deletes a temp that was never written to.
</details>
