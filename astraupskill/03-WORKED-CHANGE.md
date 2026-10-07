# Worked change: save a question without lying about success

The earlier program loaded questions and ran quizzes. Adding CRUD creates a new responsibility: after “Question created and saved,” restarting must recover that question. This walkthrough follows [QuestionBank](../src/quiz/QuestionBank.java) and [App](../src/quiz/App.java) to show where that promise is made.

Begin with a complete user command. App.edit reads the ID, prompt, choice count, each choice, correct answer and optional explanation into local variables. If input ends at any step, it reports that the incomplete edit was not applied. Only after all fields exist does it construct a Question. Constructor validation catches blank text, invalid counts and answer labels. The bank has not changed yet.

Create checks the ID against the current bank, copies the existing list and appends the new immutable Question. Update verifies both existence and unchanged identity, then replaces the matching element in a candidate list. Delete filters the candidate after confirmation in the CLI. All three commands call the same commit boundary, so storage guarantees are not duplicated in three menu branches.

Commit serializes the whole candidate first. It opens the sidecar lock file and attempts a lock. A contending cooperating writer causes a clear failure. With the lock held, it reads the authoritative file and compares it to the bytes captured when this bank loaded or last saved. If they differ, the command is stale. It does not merge records or silently overwrite another writer's changes.

Next it creates a temporary sibling, writes the complete serialized bytes and requests atomic replacement of the destination. Cleanup removes the temporary file if it still exists. Only after successful replacement does the bank update its remembered bytes and publish the new immutable list. Returning from the bank allows App to print success. Follow that order with a debugger and identify every point where an exception leaves the old visible state in place.

## The three commands and the one commit boundary

Here are the three mutating commands, from `src/quiz/QuestionBank.java:38`. Each one validates identity, builds a *candidate* list, and then hands that candidate to the same `commit`; none of them writes to disk itself.

```java
    public synchronized void create(Question question) throws IOException {
        if (find(question.getId()).isPresent()) throw new IllegalArgumentException("Question id already exists.");
        List<Question> candidate = new ArrayList<>(questions); candidate.add(question); commit(candidate);
    }
    public synchronized void update(String id, Question replacement) throws IOException {
        if (!id.equals(replacement.getId())) throw new IllegalArgumentException("An update cannot change the question id.");
        if (find(id).isEmpty()) throw new IllegalArgumentException("Question not found.");
        List<Question> candidate = new ArrayList<>(questions);
        for (int i = 0; i < candidate.size(); i++) if (candidate.get(i).getId().equals(id)) candidate.set(i, replacement);
        commit(candidate);
    }
    public synchronized boolean delete(String id) throws IOException {
        if (find(id).isEmpty()) return false;
        List<Question> candidate = questions.stream().filter(q -> !q.getId().equals(id)).toList(); commit(candidate); return true;
    }
```

## Example failure

Open two copies of the app against the same disposable bank. Both initially contain Q1. The first creates Q2. The second tries to create Q3. Its save must be rejected with `Not saved: Question bank changed. Reopen before saving; your command was not applied.` because it was based on the old file. Q2 remains on disk, and the second app's memory still holds its earlier snapshot. There is no reload command, so exit that app (`3`), start it again, review the current questions and reapply Q3 deliberately. Until you restart, every save from that session is rejected the same way, because `original` is refreshed only by a successful commit (line 65).

The test suite exercises duplicate identity, failed destination reads, stale instances and held locks. Its worker mode (`test/quiz/QuestionBankTest.java:30-36`) also lets two separate JVMs load the bank, wait at a shared gate, and then save together. Exactly one should win. The driver for that race was not shipped with the repo, so [04](04-TESTING-AND-DEBUGGING.md#the-two-jvm-race-reproduced) gives a runnable one. This is stronger than running two saves sequentially and calling the result a concurrency test. The limitation remains explicit: programs that ignore the sidecar lock are outside the protocol, and atomic replacement alone is not power-loss durability.
