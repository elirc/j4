# Verify persistence, conflict and interruption

Run `./run.ps1 -Test` with JDK 17 on PATH. The suite is a plain Java executable with explicit assertions, so there is no test framework dependency to install. Compilation uses `--release 17 -Xlint:all`. [QuestionBankTest](../test/quiz/QuestionBankTest.java) creates its own temporary directory and cleans only descendants of that directory. Never change the fixture path to the checked-in question file to make a failing test easier to inspect.

The 28 Java cases cover legacy loading without rewriting bytes, durable create/update/delete, deletion of the final question, duplicate identity, stale instances, outside edits, held locks, failed destination reads, temporary-file cleanup, Unicode and delimiter round-trips, empty explanations, malformed formats, duplicate legacy rows, immutable snapshots, choice bounds and interrupted quizzes. These are behavioral checks: they assert what survives on disk or what the caller can observe, rather than merely repeating an implementation expression.

The runner stops at the **first** failing case. `test()` (`test/quiz/QuestionBankTest.java:19`) lets the exception escape `main`, so a red run prints the PASS lines up to the failure, a stack trace, and no `RESULT` line. The last PASS line tells you where it stopped. Fix that failure and rerun to find the next one.

`run.ps1` compiles into `out/` inside the project (ignored by `.gitignore`). To keep the working tree clean, compile somewhere else:

```bash
javac -encoding UTF-8 --release 17 -Xlint:all -d "$TEMP/quiz-classes" $(find src/quiz test/quiz -name '*.java')
java -cp "$TEMP/quiz-classes" quiz.QuestionBankTest        # or: quiz.App <copy-of-a-bank>
```

## The two-JVM race, reproduced

[VERIFICATION](VERIFICATION.md) mentions nine process-level scenarios from a harness that was **not shipped in this repository** (checked 2026-10-06). The part that matters most can still be run. The test class has a worker mode (`test/quiz/QuestionBankTest.java:30-36`). A worker loads the bank, writes a "ready" file, waits for a gate file, then calls `create` and prints `SAVED` or `REJECTED: ...`. This dependency-free node script is the missing driver. Save it outside the repo and run `node --test <path>/race.test.mjs` from the repo root.

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, execFileSync } from 'node:child_process';
import { mkdtempSync, readdirSync, writeFileSync, existsSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const repo = resolve(process.env.REPO ?? '.');
const work = mkdtempSync(join(tmpdir(), 'quiz-race-'));
const classes = join(work, 'classes');
const files = ['src/quiz', 'test/quiz'].flatMap(d => readdirSync(join(repo, d)).map(f => join(repo, d, f)));
execFileSync('javac', ['-encoding', 'UTF-8', '--release', '17', '-d', classes, ...files]);

test('two JVMs that loaded the same bank: exactly one save wins', async () => {
  const bank = join(work, 'bank.txt'), gate = join(work, 'go');
  writeFileSync(bank, 'Q1|What is atomic?|All or nothing;Every write alone|A|\n');
  const workers = ['W1', 'W2'].map(id => {
    const ready = join(work, 'ready-' + id);
    const child = spawn('java', ['-cp', classes, 'quiz.QuestionBankTest', '--worker', bank, id, ready, gate]);
    let out = ''; child.stdout.on('data', d => { out += d; });
    const done = new Promise(ok => child.on('close', code => ok({ code, out })));
    return { ready, done };
  });
  while (!workers.every(w => existsSync(w.ready))) await new Promise(r => setTimeout(r, 20));
  writeFileSync(gate, 'go');
  const results = await Promise.all(workers.map(w => w.done));
  const all = results.map(r => r.out).join('');
  assert.equal((all.match(/SAVED/g) ?? []).length, 1, all);
  assert.equal((all.match(/REJECTED/g) ?? []).length, 1, all);
  assert.match(readFileSync(bank, 'utf8'), /^QUIZ-BANK\t1\n[\s\S]*END\t2\n$/);
});
```

It passed 3 of 3 runs on 2026-10-06. Note that the legacy one-line bank comes out as a versioned file with two records: the first save also converted the format.

## Break it on purpose

Make each change in a **scratch copy** of `src/`, compile it, and run the suite. These are the observed results (2026-10-06), and they are a good test of whether you can predict which case guards which line.

| Mutation (scratch copy only) | First case that fails | How |
|---|---|---|
| Delete the stale check, `QuestionBank.java:59` | "two loaded editors reject stale second save" (`:45`) | `rejects(IOException…)`: the second save now succeeds and silently drops Q2 |
| Delete the identity guard, `QuestionBank.java:43` | "update missing or changed identity leaves original" (`:43`) | `rejects(IllegalArgumentException…)`: the id-changing update is accepted |
| Drop `.stream().map(requireText).toList()` at `Question.java:17` | "choice input is copied normalized and immutable" (`:59`) | `IndexOutOfBoundsException`: the question aliases the caller's list, which the test then clears |

## Investigate a stale-save report

First compare what each process loaded with what is currently on disk. Do not delete the lock file as a general fix. A stale bank must reopen (today that means exiting with `3` and restarting; [practice Ex 1](05-PRACTICE.md) adds a reload) even if no process currently holds the lock, because its remembered bytes no longer match. The lock solves overlapping cooperating writes; the byte comparison solves edits based on an older snapshot. They address different problems.

For a malformed bank, copy the file before investigation. Check the header, final count and encoded fields. A truncated final row should stop the entire load, not leave a partly usable bank that later overwrites omitted questions. Base64 errors and invalid UTF-8 are rejected. The old loader also rejects duplicate IDs and malformed later rows instead of returning earlier valid records.

For a quiz interrupted by EOF, inspect planned, answered and unanswered counts separately. One answered question is not a completed two-question quiz. The percentage is computed over answered attempts and the UI says so. Test input such as `A` followed by EOF to expose this distinction.

The suite does not test physical power loss, network filesystems or arbitrary editors ignoring locks. It also does not test a browser or HTTP server because none is implemented. Keep those limits with the passing evidence when describing the project to a reviewer.
