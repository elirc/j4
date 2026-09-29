package quiz;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public final class QuestionBankTest {
    interface Check { void run() throws Exception; }
    private static int passed;
    private static Path directory;
    static void test(String name, Check check) throws Exception { check.run(); passed++; System.out.println("PASS " + name); }
    static void equal(Object actual, Object expected) { if (!java.util.Objects.equals(actual, expected)) throw new AssertionError("Expected " + expected + ", got " + actual); }
    static void truth(boolean value) { if (!value) throw new AssertionError("Condition was false"); }
    static void rejects(Class<? extends Throwable> type, Check action) throws Exception {
        try { action.run(); } catch (Throwable error) { if (type.isInstance(error)) return; throw new AssertionError("Wrong exception: " + error, error); }
        throw new AssertionError("Expected " + type.getName());
    }
    static Question q(String id) { return new Question(id, "What is atomic?", List.of("All or nothing", "Every write alone"), "A", ""); }
    static Path fixture() throws IOException { Path file = Files.createTempFile(directory, "questions-", ".txt"); Files.writeString(file, "Q1|What is atomic?|All or nothing;Every write alone|A|\n", StandardCharsets.UTF_8); return file; }
    static QuizResult quiz(String input, List<Question> questions) { return new QuizEngine(new Scanner(input), new PrintStream(new ByteArrayOutputStream())).runQuiz(questions); }
    public static void main(String[] args) throws Exception {
        if (args.length == 5 && args[0].equals("--worker")) {
            QuestionBank bank = new QuestionBank(Path.of(args[1])); Files.writeString(Path.of(args[3]), "ready");
            long deadline = System.nanoTime() + 20_000_000_000L;
            while (!Files.exists(Path.of(args[4]))) { if (System.nanoTime() > deadline) throw new IOException("Worker gate timed out"); Thread.sleep(10); }
            try { bank.create(q(args[2])); System.out.println("SAVED"); } catch (IOException error) { System.out.println("REJECTED: " + error.getMessage()); }
            return;
        }
        directory = Files.createTempDirectory("astra-quiz-tests-");
        try {
            test("legacy bank loads without rewriting bytes", () -> { Path f=fixture(); byte[] before=Files.readAllBytes(f); equal(new QuestionBank(f).list().size(),1); truth(java.util.Arrays.equals(before,Files.readAllBytes(f))); });
            test("create persists and reloads", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); b.create(q("Q2")); equal(new QuestionBank(f).list().size(),2); });
            test("duplicate create preserves file and memory", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); String before=Files.readString(f); rejects(IllegalArgumentException.class,()->b.create(q("Q1"))); equal(Files.readString(f),before); equal(b.list().size(),1); });
            test("update keeps identity and persists full replacement", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); b.update("Q1",new Question("Q1","Changed",List.of("No","Yes"),"B","Why")); Question loaded=new QuestionBank(f).find("Q1").orElseThrow(); equal(loaded.getPrompt(),"Changed"); equal(loaded.getCorrectAnswer(),"B"); });
            test("update missing or changed identity leaves original", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); String before=Files.readString(f); rejects(IllegalArgumentException.class,()->b.update("Q1",q("Q2"))); rejects(IllegalArgumentException.class,()->b.update("missing",q("missing"))); equal(Files.readString(f),before); });
            test("deleting final question leaves reloadable empty bank", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); truth(b.delete("Q1")); equal(new QuestionBank(f).list().size(),0); truth(!b.delete("Q1")); b.create(q("Q2")); equal(new QuestionBank(f).list().size(),1); });
            test("two loaded editors reject stale second save", () -> { Path f=fixture(); QuestionBank a=new QuestionBank(f),b=new QuestionBank(f); a.create(q("Q2")); String winner=Files.readString(f); rejects(IOException.class,()->b.create(q("Q3"))); equal(Files.readString(f),winner); equal(b.list().size(),1); });
            test("outside file edits are preserved", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); Files.writeString(f,"outside editor data"); rejects(IOException.class,()->b.delete("Q1")); equal(Files.readString(f),"outside editor data"); equal(b.list().size(),1); });
            test("cooperating file lock rejects simultaneous save", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); try(FileChannel channel=FileChannel.open(f.resolveSibling(f.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE); FileLock lock=channel.lock()) { truth(lock.isValid()); rejects(IOException.class,()->b.create(q("Q2"))); } equal(b.list().size(),1); });
            test("failed destination read preserves in-memory bank", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); Files.delete(f); Files.createDirectory(f); rejects(IOException.class,()->b.create(q("Q2"))); equal(b.list().size(),1); });
            test("atomic writer leaves no temporary artifacts", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); b.create(q("Q2")); try(var paths=Files.list(directory)) { truth(paths.noneMatch(path->path.getFileName().toString().startsWith(".quiz-bank-"))); } });
            test("unicode delimiters backslashes and newlines round trip", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); Question special=new Question("Q|2","Café | tabs\tand\nlines",List.of("a;b","\\path|value"),"B","Why?\nBecause.\r\n"); b.create(special); Question loaded=new QuestionBank(f).find("Q|2").orElseThrow(); equal(loaded.getPrompt(),special.getPrompt()); equal(loaded.getChoices(),special.getChoices()); equal(loaded.getExplanation(),special.getExplanation()); });
            test("empty optional explanation survives save", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); b.create(q("Q2")); equal(new QuestionBank(f).find("Q2").orElseThrow().getExplanation(),""); });
            test("truncated footer rejects whole bank", () -> { Path f=fixture(); new QuestionBank(f).create(q("Q2")); String text=Files.readString(f); Files.writeString(f,text.substring(0,text.lastIndexOf("END\t"))); rejects(QuestionFileException.class,()->new QuestionBank(f)); });
            test("unknown bank format never falls back to legacy", () -> { Path f=fixture(); Files.writeString(f,"QUIZ-BANK\t99\nEND\t0\n"); rejects(QuestionFileException.class,()->new QuestionBank(f)); });
            test("malformed base64 is rejected", () -> { Path f=fixture(); new QuestionBank(f).create(q("Q2")); String text=Files.readString(f).replace("UTE=","!invalid!"); Files.writeString(f,text); rejects(QuestionFileException.class,()->new QuestionBank(f)); });
            test("invalid UTF8 does not become replacement characters", () -> { Path f=fixture(); Files.write(f,new byte[]{(byte)0xC3,(byte)0x28}); rejects(IOException.class,()->new QuestionBank(f)); });
            test("duplicate legacy ids reject entire load", () -> { Path f=fixture(); Files.writeString(f,Files.readString(f)+Files.readString(f)); rejects(QuestionFileException.class,()->new QuestionBank(f)); });
            test("malformed legacy row after valid row rejects whole load", () -> { Path f=fixture(); Files.writeString(f,Files.readString(f)+"broken\n"); rejects(QuestionFileException.class,()->new QuestionBank(f)); });
            test("bank list and prior snapshot cannot be mutated", () -> { Path f=fixture(); QuestionBank b=new QuestionBank(f); List<Question> prior=b.list(); rejects(UnsupportedOperationException.class,()->prior.clear()); b.create(q("Q2")); equal(prior.size(),1); });
            test("choice input is copied normalized and immutable", () -> { List<String> choices=new ArrayList<>(List.of(" yes ","no")); Question q=new Question(" id "," prompt ",choices,"a",null); choices.clear(); equal(q.getId(),"id"); equal(q.getChoices().get(0),"yes"); rejects(UnsupportedOperationException.class,()->q.getChoices().clear()); });
            test("choice count and labels share the A to Z range", () -> { rejects(IllegalArgumentException.class,()->new Question("x","x",java.util.Collections.nCopies(27,"x"),"A","")); rejects(IllegalArgumentException.class,()->Question.indexToAnswerLabel(-1)); rejects(IllegalArgumentException.class,()->Question.indexToAnswerLabel(26)); equal(Question.indexToAnswerLabel(25),"Z"); });
            test("attempt rejects an answer outside its question", () -> { rejects(IllegalArgumentException.class,()->new QuestionAttempt(q("Q1"),"Z")); rejects(IllegalArgumentException.class,()->new QuestionAttempt(q("Q1"),"")); truth(!q("Q1").isCorrectAnswer(null)); });
            test("invalid quiz input retries without an extra attempt", () -> { QuizResult result=quiz("Z\nAA\na\n",List.of(q("Q1"))); equal(result.getTotalQuestions(),1); equal(result.getCorrectCount(),1); truth(result.isComplete()); });
            test("EOF before first answer returns zero answered and one unanswered", () -> { QuizResult result=quiz("",List.of(q("Q1"))); equal(result.getTotalQuestions(),0); equal(result.getUnansweredCount(),1); equal(result.getFinalScorePercent(),0.0); truth(!result.isComplete()); });
            test("EOF midway does not count unanswered as incorrect", () -> { QuizResult result=quiz("A\n",List.of(q("Q1"),q("Q2"))); equal(result.getTotalQuestions(),1); equal(result.getIncorrectCount(),0); equal(result.getUnansweredCount(),1); equal(result.getFinalScorePercent(),100.0); });
            test("score and immutable attempts derive from answered snapshot", () -> { List<QuestionAttempt> attempts=new ArrayList<>(List.of(new QuestionAttempt(q("Q1"),"A"),new QuestionAttempt(q("Q2"),"B"))); QuizResult result=new QuizResult(attempts); attempts.clear(); equal(result.getFinalScorePercent(),50.0); equal(result.getIncorrectAttempts().size(),1); rejects(UnsupportedOperationException.class,()->result.getAttempts().clear()); });
            test("invalid planned count cannot mislabel a result", () -> { rejects(IllegalArgumentException.class,()->new QuizResult(List.of(new QuestionAttempt(q("Q1"),"A")),0)); rejects(IllegalArgumentException.class,()->new QuizResult(List.of(),-1)); });
            System.out.println("RESULT passed="+passed);
        } finally {
            // Only paths below the exact directory created by this test are removed.
            try(var paths=Files.walk(directory)) { for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) { if(!path.normalize().startsWith(directory.normalize())) throw new IOException("Unsafe test cleanup"); Files.deleteIfExists(path); } }
        }
    }
}
