# Identity, snapshots and durable command boundaries

CRUD means creating, reading, updating and deleting records, but reliable behavior depends on rules around those verbs. A question ID identifies one record. An update replaces allowed content under the same ID; it does not quietly create a new record when the ID is missing. A duplicate create fails. A delete of a missing ID returns false, while the CLI tells the learner the record was not found before asking for confirmation.

An immutable object cannot change after construction. Question copies and normalizes its choices and validates its correct answer. QuestionBank exposes immutable lists, so a caller cannot append a record without passing through its persistence commands. Earlier lists remain stable snapshots when the bank accepts another command. This is useful for a quiz already using a particular question set and for tests that need predictable history.

## Candidate state before publication

Suppose create first appends to the visible list and then attempts to write the file. If the write fails, the app would display a question that disappears after restart. The delivered bank instead builds a candidate list, serializes it and persists it before assigning the visible list. Validation failures happen before this boundary. File-read, lock or replacement failures leave the previous in-memory collection intact.

Atomic replacement means the destination switches from one complete file to another through a filesystem operation. The temporary file is created beside the destination so it is on the same filesystem. There is no fallback to an ordinary replacement if atomic move is unsupported. This protects the stated all-or-nothing replacement contract; it is not a proof that bytes survive sudden power loss. The implementation does not force file and directory metadata to stable storage.

## Stale editors and cooperating locks

Two processes can load the same bank. Each remembers its original bytes. During commit, a writer acquires the sidecar lock and compares current bytes with its remembered version. After the first writer saves, the second either encounters the lock or detects changed bytes. It must reopen before trying again. This is analogous to optimistic version checking in a database API, but the scope is the whole file, so unrelated question edits still conflict.

Locks coordinate programs that follow this protocol. A text editor that ignores the sidecar can write between the comparison and replacement. Therefore “no lost updates between arbitrary writers” would be an unsupported claim. State the cooperating-writer assumption clearly.

The versioned format uses a header, encoded fields and a count footer. Base64 allows pipes, semicolons, tabs and newlines without inventing ambiguous escaping. It does not hide answers. Strict UTF-8 decoding and whole-file validation reject malformed content rather than skipping records. A valid empty bank is represented explicitly, while an incomplete footer is treated as corruption. These distinctions translate directly into schema versioning, input validation and migration decisions in larger applications.

## Glossary

*Snapshot*: the bank as one file, read whole and replaced whole. *Candidate*: the next list of questions, built and validated before anything is written. *Advisory lock*: `FileLock.tryLock()` on a sibling `.lock` file; cooperating editors respect it, nothing enforces it. *Atomic replace*: write a temp file, then `ATOMIC_MOVE` over the original. *Stale write*: a commit whose `original` bytes no longer match the file. *Publication*: assigning `questions` and `original` only after the move succeeded. *Aggregate*: the whole bank treated as one unit of consistency. *Baseline*: the bytes last read (`original`). *Identity check*: an id must be unique and an update cannot change it. *Serialization*: the tab-separated format with `END` footer and count. *Footer*: the last line that lets a truncated file be detected. *Attempt*: a recorded answer in a quiz; unanswered is not an attempt. *EOF*: the input ending; a half-typed command is discarded. *Field encoding*: `encode`/`unpack` (`QuestionBank.java:97-98`) Base64 every text field, so tabs and newlines cannot split a row (no escaping is involved). *ETag / If-Match*: the web equivalents of the baseline (see 08).

