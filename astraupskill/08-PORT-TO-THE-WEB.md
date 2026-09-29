# 08. Port `QuestionBank.commit` to the web

[Course index](README.md)

`commit` gives three guarantees: an id is unique in the bank, a save based on a stale snapshot is refused, and memory is published only after the file is durably replaced. Here is the same contract as an Express route over SQLite, then the one SQL statement that carries the stale check.

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

The differences are what to notice: the file version needs a lock because compare and replace are two steps; the SQL version compares and writes in one statement. The 428 for a missing `If-Match` has no Java equivalent because the CLI always holds a snapshot.

## Exercise

Write the three tests: duplicate id is 409 with one row; stale `If-Match` is 409 and the row is unchanged; a fresh `If-Match` is 200 and the `ETag` moved. Then write the same route in ASP.NET minimal API with EF Core and `[ConcurrencyCheck] int Version`.
