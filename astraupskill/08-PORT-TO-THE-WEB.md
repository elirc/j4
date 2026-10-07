# 08. Port `QuestionBank.commit` to the web

[Course index](README.md)

`commit` gives three guarantees: an id is unique in the bank, a save based on a stale snapshot is refused, and memory is published only after the file is durably replaced. Here is the same contract as an Express route over SQLite, then the one SQL statement that carries the stale check. The route is a sketch: Express is not a dependency of this repo, and `validateQuestion` is yours to write (port the rules of the `Question` constructor, `src/quiz/Question.java:14-23`). The SQL part is runnable below without installing anything.

## The route

```ts
import express from 'express';
import { DatabaseSync } from 'node:sqlite';
const db = new DatabaseSync('bank.sqlite');
db.exec(`CREATE TABLE IF NOT EXISTS questions (id TEXT PRIMARY KEY, prompt TEXT NOT NULL, choices TEXT NOT NULL, answer TEXT NOT NULL, explanation TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1)`);
const app = express(); app.use(express.json({ limit: '64kb' }));

app.post('/questions', (req, res) => {
  const q = validateQuestion(req.body);                       // the same rules as the Question constructor
  try {
    db.prepare('INSERT INTO questions (id, prompt, choices, answer, explanation) VALUES (?, ?, ?, ?, ?)')
      .run(q.id, q.prompt, JSON.stringify(q.choices), q.answer, q.explanation);
  } catch (error) {
    if (String(error.message).includes('UNIQUE')) return res.status(409).json({ error: 'Duplicate question id.' });   // guarantee 1
    throw error;
  }
  res.status(201).set('ETag', '"1"').json({ ...q, version: 1 });
});

app.put('/questions/:id', (req, res) => {
  const match = /^"(\d+)"$/.exec(req.get('if-match') ?? '');
  if (!match) return res.status(428).json({ error: 'Send If-Match with the version you loaded.' });
  const q = validateQuestion({ ...req.body, id: req.params.id });
  const result = db.prepare('UPDATE questions SET prompt = ?, choices = ?, answer = ?, explanation = ?, version = version + 1 WHERE id = ? AND version = ?')
    .run(q.prompt, JSON.stringify(q.choices), q.answer, q.explanation, q.id, Number(match[1]));   // guarantee 2
  if (result.changes === 0) {
    const exists = db.prepare('SELECT 1 FROM questions WHERE id = ?').get(q.id);
    return exists ? res.status(409).json({ error: 'Question bank changed. Reload before saving; your edit was not applied.' })
                  : res.status(404).json({ error: 'Question not found.' });
  }
  res.set('ETag', `"${Number(match[1]) + 1}"`).json({ ...q, version: Number(match[1]) + 1 });    // guarantee 3: respond after the commit
});
```

## Mapping

| Java `commit` | web |
|---|---|
| `find(id).isPresent()` before create | `PRIMARY KEY` and the `UNIQUE` failure mapped to 409 (the database does the check atomically) |
| `Arrays.equals(original, Files.readAllBytes(file))` | `WHERE version = ?` on the `UPDATE`; `changes === 0` is the refusal |
| `FileLock.tryLock()` | not needed: the conditional `UPDATE` is atomic; use a transaction when several rows change together |
| temp file + `ATOMIC_MOVE` | the SQLite transaction commit |
| `original = serialized; questions = List.copyOf(candidate)` after the move | the response is sent after `run()` returns; the client adopts the new `ETag` |

The differences are what to notice: the file version needs a lock because compare and replace are two steps; the SQL version compares and writes in one statement. The 428 for a missing `If-Match` has no Java equivalent because the CLI always holds a snapshot. One divergence to fix if you build it: the `PUT` overwrites any `id` in the body with the URL id, while `QuestionBank.update` rejects an id change outright (`src/quiz/QuestionBank.java:43`). Rejecting a body id that differs from the URL with 400 is the faithful port.

## Exercise

**Goal.** Prove the three guarantees at the SQL layer: a duplicate id is refused, a stale version changes zero rows and leaves the row untouched, and "zero rows" for a missing id can be told apart from a stale one.

**Check.** Save as `sql.test.mjs` outside the repo and run `node --test sql.test.mjs`. It uses the built-in `node:sqlite` (Node 22.13+ runs it without a flag and prints an ExperimentalWarning, which is expected; 22.5 to 22.12 need `--experimental-sqlite`). All three pass as written (2026-10-06, Node 22.16). Use them as the oracle while you wire the same statements into a route.

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';

const db = new DatabaseSync(':memory:');
db.exec(`CREATE TABLE questions (id TEXT PRIMARY KEY, prompt TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1)`);
const insert = db.prepare('INSERT INTO questions (id, prompt) VALUES (?, ?)');
const update = db.prepare('UPDATE questions SET prompt = ?, version = version + 1 WHERE id = ? AND version = ?');
const read = db.prepare('SELECT prompt, version FROM questions WHERE id = ?');

test('duplicate id is refused by the primary key (the 409 case)', () => {
  insert.run('Q1', 'What is atomic?');
  assert.throws(() => insert.run('Q1', 'again'), /UNIQUE/);
  assert.equal(db.prepare('SELECT count(*) AS n FROM questions').get().n, 1);
});

test('a stale version changes zero rows and leaves the row alone', () => {
  assert.equal(update.run('fresh edit', 'Q1', 1).changes, 1);
  assert.equal(update.run('stale edit', 'Q1', 1).changes, 0);
  assert.deepEqual({ ...read.get('Q1') }, { prompt: 'fresh edit', version: 2 });
});

test('zero rows for a missing id must be told apart from a stale one (404 vs 409)', () => {
  assert.equal(update.run('x', 'nope', 1).changes, 0);
  assert.equal(read.get('nope'), undefined);
});
```

**Stretch.** Put the route above on top of these statements and test it over HTTP: duplicate id gives 409 with one row, stale `If-Match` gives 409 with the row unchanged, and a fresh `If-Match` gives 200 with a new `ETag`. Then write the same thing as an ASP.NET minimal API with EF Core and a `[ConcurrencyCheck] int Version`.
