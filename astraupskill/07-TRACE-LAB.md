# Trace a durable edit and a competing writer

Prepare a disposable copy of `data/questions.txt` and launch the app with `-DataPath` pointing to it. Keep the source open at [App.edit](../src/quiz/App.java) and [QuestionBank.commit](../src/quiz/QuestionBank.java). This lab changes only the copy you choose. Do not use your only copy of a bank while experimenting with malformed content.

Create question `TRACE1` with two choices and an empty explanation. Before submitting the final line, identify what exists only in local form variables. Once the command succeeds, inspect the selected file: it now starts with `QUIZ-BANK` and format version `1`, contains encoded fields and ends with a question count. The first edit converted the legacy representation. Restart and confirm that the empty explanation and choices survive.

Update TRACE1 with a prompt containing a pipe and a choice containing a semicolon. The versioned format can represent these characters because fields are encoded, rather than treating those characters as separators inside the text. The CLI reads single-line values; the underlying model and storage also support multiline strings, which the regression tests exercise. Do not infer that the interactive form has a multiline editor.

Now start a second app process against the same copy. Both load the same bank. In the first, create TRACE2. In the second, attempt an update to TRACE1. Expect `Not saved: Question bank changed. Reopen before saving; your command was not applied.` The second process still holds its earlier snapshot. The app has no reload command, so quit it with `3` and start it again before reapplying the intended edit. Try one more save in the second process before quitting: it is rejected again, because `original` changes only on a successful commit. Explain why a completed first save can cause rejection even after its file lock has already been released.

Try delete twice: first answer `no`, then `yes`. Compare file bytes after the cancelled attempt and inspect the list after restart following the confirmed attempt. The confirmation is a UI decision before the command; the persistence boundary is still in the bank. A future API would need its own authorization and version checks regardless of what a browser confirmation dialog says.

End input halfway through a create form. The application reports that the incomplete edit was not applied. End input during a quiz and inspect the partial result. Record planned, answered, correct, incorrect and unanswered counts in a table. Explain why unanswered questions are neither valid QuestionAttempts nor automatically wrong answers.

Finish by writing a trace with eight stages: collect input, construct the domain object, validate identity, build candidate state, lock and compare, replace storage, publish memory and report success. Mark the possible failures beside each stage. Then propose where those responsibilities would live in an HTTP controller, service and SQL repository. Keep the mapping conceptual; those web layers remain an exercise in this project.

## The commit boundary you are tracing

Keep this open while you run the two-process experiment. It is `QuestionBank.commit`, `src/quiz/QuestionBank.java:53`: serialize, lock, compare against the bytes read at load, replace atomically, and only then publish the new list.

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

## Expected results for the end-of-input cases

Fill your own table first, then compare. With `data/questions.txt` copied to a scratch file (five questions, Q1 to Q5). The quiz rows were reproduced on 2026-10-06 by piping `1` (and then `A`, `B`) into the app:

| case | planned | answered | correct | incorrect | unanswered | bank file |
|---|---|---|---|---|---|---|
| input ends halfway through a create form | - | - | - | - | - | byte-identical to before ("not applied" is printed, nothing was committed) |
| input ends during a quiz after answering Q1 correctly and Q2 incorrectly | 5 | 2 | 1 | 1 | 3 | unchanged (a quiz never writes) |
| input ends before the first answer | 5 | 0 | 0 | 0 | 5 | unchanged |

Unanswered questions are not `QuestionAttempt`s: an attempt records a chosen letter, and there is none. They are not automatically wrong either; "wrong" is a comparison between a choice and the answer, and there is no choice. That is why the result reports these counts, and labels the score as covering answered questions only. In the zero-answer row the app still prints `Score on answered questions: 0.00%`; [practice Ex 3](05-PRACTICE.md) asks whether that line should exist at all.

