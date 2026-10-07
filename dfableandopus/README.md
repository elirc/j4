# Interview pack: Quiz Engine question bank (j4)

This folder turns the Java CLI in `src/quiz/` into material for CRUD web-developer interviews. The course next door (`../astraupskill/`) teaches how the app works. This folder teaches how to talk about it to a hiring panel, and how to answer when the panel's stack is Node/Express or ASP.NET rather than Java.

## The pitch

I took a read-only console quiz app and gave its question bank full CRUD without ever letting the program announce a save it had not made. Every mutating command builds a candidate list and hands it to one boundary, `QuestionBank.commit` (`src/quiz/QuestionBank.java:53`), which takes a cooperating file lock, re-reads the destination and refuses if the bytes moved under it, writes a temp sibling and requests `ATOMIC_MOVE`, and only then publishes the new immutable list into memory. The regression suite covers 28 cases and the delivery harness added nine separate-process scenarios, including two JVMs released through a shared gate where exactly one save wins. (That harness is not in this repo; the two-JVM race can be rerun with the driver in `../astraupskill/04-TESTING-AND-DEBUGGING.md`.)

## Off-stack framing

This is Java, and most CRUD job ads here are Node or .NET. Do not hide that. The honest framing is: the file bank is an optimistic-concurrency store with a hand-rolled compare-and-swap, and `../astraupskill/08-PORT-TO-THE-WEB.md` already maps every line of `commit` onto an Express + `node:sqlite` route with `UPDATE ... WHERE version = ?`, 409 and 428. Answer in Java, then translate in the same breath.

## Two-week plan

- Days 1-3: read `01-INTERVIEW-QUESTIONS.md` screening group out loud, with `src/quiz/` open.
- Days 4-6: deep-dive group, plus `03-CODE-READING-DRILL.md` on a timer.
- Days 7-8: rehearse `02-STORIES-AND-RESUME.md` until the 60-second summary needs no notes.
- Days 9-11: do `04-TAKE-HOME.md` end to end against `test/quiz/QuestionBankTest.java`.
- Days 12-13: `05-SYSTEM-DESIGN-FOLLOWUPS.md`, drawing the diagram from memory.
- Every day: `06-FLASHCARDS.md`.

## Files

- [01-INTERVIEW-QUESTIONS.md](01-INTERVIEW-QUESTIONS.md)
- [02-STORIES-AND-RESUME.md](02-STORIES-AND-RESUME.md)
- [03-CODE-READING-DRILL.md](03-CODE-READING-DRILL.md)
- [04-TAKE-HOME.md](04-TAKE-HOME.md)
- [05-SYSTEM-DESIGN-FOLLOWUPS.md](05-SYSTEM-DESIGN-FOLLOWUPS.md)
- [06-FLASHCARDS.md](06-FLASHCARDS.md)
