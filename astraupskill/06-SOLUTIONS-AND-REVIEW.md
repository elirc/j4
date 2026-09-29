# Review guidance and solution sketches

Use these sketches to evaluate [practice work](05-PRACTICE.md), not as a substitute for implementing and testing it. A good solution can use different classes while preserving the same observable guarantees.

For search, normalize the comparison policy once and preserve an explicit order, such as insertion order or stable ID order. Compute the page after filtering. Reject negative offsets and unreasonable limits before slicing. Return an immutable list. Test a page boundary with more records than the selected page size; a three-record fixture cannot demonstrate a twenty-record paging bug.

For export, separate serialization from authoritative replacement. An export operation should not update the bank's remembered original bytes. Resolve the destination and compare it with the authoritative file before writing. Decide what to do when a destination already exists and make replacement explicit. A test should capture the bank's exact bytes, trigger an export failure and prove both the file and in-memory questions remain unchanged.

For session history, retain the question content used during the attempt. If Q1 is edited tomorrow, yesterday's answer should still be judged against yesterday's question. Store planned count and answered attempts separately, with a clear completion flag or derivable status. Validate impossible combinations on load. Show a partial session as partial rather than using a high answered-only percentage as evidence of completion.

For HTTP CRUD, keep untrusted request parsing outside the immutable domain constructor but still let the constructor enforce domain invariants. Translate known failures deliberately. A missing record can become 404; an invalid answer label becomes 400; a stale version becomes 409 or a documented conditional-request status. Unexpected storage errors should produce a generic server failure with diagnostic context kept in logs. A creation response should identify a readable resource. A 204 response has no JSON body, so browser code must not always call `response.json()`.

For SQL persistence, a useful update resembles “update Question where ID and version match; increment version; replace choices in the same transaction.” Zero affected rows requires distinguishing absence from a stale version according to your API policy. Add foreign keys and uniqueness rules that protect choice identity and ownership. A transaction alone does not prevent a read-then-write lost update; the condition at the write boundary matters.

For authorization, derive the owner from trusted identity, not a form field. Scope reads as well as writes, including nested choice IDs and attempt sessions. Review access with two users and copied resource IDs. Be explicit about which responses reveal correct answers and which are safe for learners taking a quiz.

In code review, ask the author to demonstrate one late failure and one competing edit. Check that success messages follow the durable boundary, incomplete inputs cannot mutate records, and old data has a migration path. Ask which assumption would fail on a network filesystem or with a writer that ignores the lock. Precise limits are a sign that the design has been understood.

## Two tests worth copying the shape of

The existing suite already demonstrates the two habits asked for above: capture the exact bytes and prove nothing moved, and prove the published list is not a live handle. Both are from `test/quiz/QuestionBankTest.java:46` and `:58`.

```java
            test("outside file edits are preserved", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); Files.writeString(f,"outside editor data"); rejects(IOException.class,()->b.delete("Q1")); equal(Files.readString(f),"outside editor data"); equal(b.list().size(),1); });

            test("bank list and prior snapshot cannot be mutated", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); List<Question> prior=b.list(); rejects(UnsupportedOperationException.class,()->prior.clear()); b.create(q("Q2")); equal(prior.size(),1); });
```

