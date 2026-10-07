# Practice: close five real gaps in the delivered CLI

Each exercise below is a gap that exists in the code today (2026-10-06), found by running the app, not a hypothetical feature. Every exercise has a **Goal** and a **Check**. The checks drive the real `quiz.App` through standard input, so they test what a user would see rather than an internal method.

Attempt an exercise before reading [the solution guidance](06-SOLUTIONS-AND-REVIEW.md). The rules from the rest of the course still apply: all persistence goes through `QuestionBank.commit`, nothing prints "saved" before the atomic move returns, and `.\run.ps1 -Test` must still end with `RESULT passed=28` (or more, if you add cases).

## Set up the check file once

Save this as `practice.test.mjs` **outside** the repository (for example in your temp folder) so it never becomes part of the project. It needs only Node 18+ and a JDK 17 on PATH; there is nothing to install. It compiles `src/quiz` into a fresh temp directory on every run, so it always tests your current code and never writes into the repo.

```js
// practice.test.mjs: run from the repo root with
//   node --test --test-name-pattern "Ex 1" <path-to>/practice.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync, execFileSync } from 'node:child_process';
import { mkdtempSync, copyFileSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const repo = resolve(process.env.REPO ?? '.');
const work = mkdtempSync(join(tmpdir(), 'quiz-practice-'));
const classes = join(work, 'classes');
execFileSync('javac', ['-encoding', 'UTF-8', '--release', '17', '-d', classes,
  ...readdirSync(join(repo, 'src/quiz')).map(f => join(repo, 'src/quiz', f))]);
const freshBank = name => { const p = join(work, name); copyFileSync(join(repo, 'data/questions.txt'), p); return p; };
const run = (bank, input) => spawnSync('java', ['-cp', classes, 'quiz.App', bank], { input, encoding: 'utf8' });
const create = id => `5\n${id}\nPrompt ${id}\n2\nyes\nno\nA\n\n`;
function live(bank) {
  const child = spawn('java', ['-cp', classes, 'quiz.App', bank]);
  let out = ''; child.stdout.on('data', d => { out += d; });
  const waitFor = (text, from = 0) => new Promise((ok, fail) => {
    const t = setInterval(() => { if (out.indexOf(text, from) >= 0) { clearInterval(t); ok(); } }, 20);
    setTimeout(() => { clearInterval(t); child.kill(); fail(new Error('no "' + text + '" in:\n' + out.slice(from))); }, 20000);
  });
  return { waitFor, send: s => child.stdin.write(s), mark: () => out.length, quit: () => child.stdin.end('3\n') };
}

test('Ex 1: menu 8 reloads the bank so a stale session can save again', async () => {
  const bank = freshBank('ex1.txt');
  const a = live(bank), b = live(bank);
  await a.waitFor('Welcome'); await b.waitFor('Welcome');
  a.send(create('A1')); await a.waitFor('Question created and saved.');
  b.send(create('B1')); await b.waitFor('Not saved: Question bank changed');
  let m = b.mark(); b.send('8\n'); await b.waitFor('Reloaded 6 questions.', m);
  m = b.mark(); b.send(create('B1')); await b.waitFor('Question created and saved.', m);
  a.quit(); b.quit();
});

test('Ex 2: startup failure exits non-zero and names the missing file plainly', () => {
  const r = run(join(work, 'nope.txt'), '3\n');
  assert.notEqual(r.status, 0);
  assert.match(r.stdout + r.stderr, /Question file was not found/);
});

test('Ex 3: a quiz with zero answers prints no percentage', () => {
  const r = run(freshBank('ex3.txt'), '1\n');
  assert.match(r.stdout, /Answered: 0 of 5/);
  assert.doesNotMatch(r.stdout, /Score on answered questions: 0\.00%/);
});

test('Ex 4: a non-numeric choice count gets a domain message, not a Java one', () => {
  const bank = freshBank('ex4.txt'), before = readFileSync(bank);
  const r = run(bank, '5\nX1\nPrompt\nabc\n3\n');
  assert.match(r.stdout, /Invalid input: Choose 2 through 26 choices\./);
  assert.doesNotMatch(r.stdout, /For input string/);
  assert.deepEqual(readFileSync(bank), before);
});

test('Ex 5: a comments-only legacy file opens as an empty bank', () => {
  const bank = join(work, 'ex5.txt');
  writeFileSync(bank, '# no questions yet\n');
  const r = run(bank, '2\n3\n');
  assert.match(r.stdout, /There are 0 questions available\./);
});
```

Against the delivered code all five tests fail, each for the reason the exercise describes. The machine is slow to start JVMs, so a full run can take a minute or two; use `--test-name-pattern` to run one exercise at a time.

## Ex 1. Recover from a stale save without restarting

**Goal.** Today a session that loses a stale-save race can never save again. `commit` refreshes `original` only after a successful move (`src/quiz/QuestionBank.java:65`), the error says "Reopen before saving" (line 59), but the menu (`src/quiz/App.java:26`) has no reopen. Every later create, update or delete in that session is rejected with the same message. Add menu option `8` that rebuilds the bank from disk and prints `Reloaded N questions.`. A failed reload must keep the previous bank, not leave `bank` half-replaced.

**Check.** `node --test --test-name-pattern "Ex 1" practice.test.mjs`. Two live processes: A saves `A1`, B's save is rejected, B reloads, sees 6 questions (5 originals plus A1) and its retried create succeeds. Fails today at the reload step with "Please enter a menu number from 1 through 7."

## Ex 2. Make a failed startup visible to a script

**Goal.** Two startup defects. (a) `App.run` prints the error and `return`s (`src/quiz/App.java:23`), so the process exits with status 0: a script cannot tell a corrupt bank from a clean exit. (b) For a missing file, `QuestionBank` calls `Files.readAllBytes` first (`src/quiz/QuestionBank.java:30`), so the user sees `The quiz could not be started: C:\...\nope.txt` (the bare path, which is all `NoSuchFileException` carries). The friendlier "Question file was not found." in `src/quiz/QuestionLoader.java:11-13` is unreachable from the app. Return a non-zero status on startup failure and say plainly that the file was not found.

**Check.** `node --test --test-name-pattern "Ex 2" practice.test.mjs`. Fails today on `assert.notEqual(r.status, 0)`.

## Ex 3. Do not print a score for zero answers

**Goal.** If input ends before the first answer, the app prints `Answered: 0 of 5` and then `Score on answered questions: 0.00%`. `getFinalScorePercent` returns 0.0 for an empty attempt list (`src/quiz/QuizResult.java:44-46`), and `App.quiz` prints it unconditionally (`src/quiz/App.java:75`). Zero correct out of zero answered is not 0%; it is "no score". Change the presentation, not `QuizResult`: the existing test at `test/quiz/QuestionBankTest.java:63` pins `getFinalScorePercent() == 0.0` and must keep passing.

**Check.** `node --test --test-name-pattern "Ex 3" practice.test.mjs`. Fails today on `doesNotMatch`.

## Ex 4. Keep Java's exception text out of the UI

**Goal.** Typing `abc` for "Number of choices" makes `Integer.parseInt` (`src/quiz/App.java:50`) throw `NumberFormatException`, a subclass of `IllegalArgumentException`, so the generic handler at line 38 prints `Invalid input: For input string: "abc"`. Translate it into the same domain message the range check already uses, `Choose 2 through 26 choices.` The bank file must stay byte-identical.

**Check.** `node --test --test-name-pattern "Ex 4" practice.test.mjs`. Fails today on the first `match`.

## Ex 5. Treat "no questions yet" as empty, not corrupt

**Goal.** An empty versioned bank (`QUIZ-BANK\t1` then `END\t0`) is valid, and the README says deleting the last question leaves one. But a legacy file containing only comments fails startup with "Question file does not contain any questions." (`src/quiz/QuestionLoader.java:38-40`). Absence of rows is not corruption. Make the two formats agree. Decide first whether a zero-byte file should also count as empty, and write down your answer.

**Check.** `node --test --test-name-pattern "Ex 5" practice.test.mjs`. Fails today because startup stops.

## After all five pass

Run `.\run.ps1 -Test` (or the plain `javac` command in [04](04-TESTING-AND-DEBUGGING.md)) and confirm `RESULT passed=28`. Then add one Java case per exercise to `test/quiz/QuestionBankTest.java` where the behavior lives below `App` (Ex 2's missing-file message and Ex 5's empty legacy load can both be tested on `QuestionBank` directly). Ex 1, 3 and 4 live in `App`, which the Java suite does not drive; the node checks are their regression tests.

For the step beyond the CLI (SQL versions, HTTP status codes), do the runnable exercise in [08](08-PORT-TO-THE-WEB.md).
