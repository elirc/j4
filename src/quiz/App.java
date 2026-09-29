package quiz;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public final class App {
    private final Scanner scanner;
    private final PrintStream out;
    private final Path file;
    private QuestionBank bank;
    public App(Scanner scanner, PrintStream out) { this(scanner, out, Path.of("data", "questions.txt")); }
    public App(Scanner scanner, PrintStream out, Path file) { this.scanner = scanner; this.out = out; this.file = file; }
    public static void main(String[] args) {
        new App(new Scanner(System.in), System.out, args.length > 0 ? Path.of(args[0]) : Path.of("data", "questions.txt")).run();
    }
    public void run() {
        try { bank = new QuestionBank(file); }
        catch (IOException | QuestionFileException error) { out.println("The quiz could not be started: " + error.getMessage()); return; }
        out.println("Welcome to the Quiz Engine");
        while (true) {
            out.println("\n1. Start quiz\n2. View available questions count\n3. Exit\n4. List questions\n5. Create question\n6. Update question\n7. Delete question");
            String choice = read("Choose an option: "); if (choice == null || choice.equals("3")) break;
            try {
                switch (choice) {
                    case "1" -> quiz();
                    case "2" -> out.println("There are " + bank.list().size() + " questions available.");
                    case "4" -> bank.list().forEach(q -> out.println(q.getId() + ": " + q.getPrompt()));
                    case "5" -> edit(false);
                    case "6" -> edit(true);
                    case "7" -> delete();
                    default -> out.println("Please enter a menu number from 1 through 7.");
                }
            } catch (IllegalArgumentException | NullPointerException error) { out.println("Invalid input: " + error.getMessage()); }
            catch (IOException error) { out.println("Not saved: " + error.getMessage()); }
        }
        out.println("Goodbye! Completed edits were saved when accepted.");
    }
    private String read(String prompt) { out.print(prompt); return scanner.hasNextLine() ? scanner.nextLine().trim() : null; }
    private void edit(boolean updating) throws IOException {
        String id = read("Question id: "); if (id == null) { interrupted(); return; }
        if (updating && bank.find(id).isEmpty()) { out.println("Question not found."); return; }
        if (!updating && bank.find(id).isPresent()) { out.println("Question id already exists."); return; }
        String prompt = read("Prompt: "); if (prompt == null) { interrupted(); return; }
        String rawCount = read("Number of choices (2-26): "); if (rawCount == null) { interrupted(); return; }
        int count = Integer.parseInt(rawCount); if (count < 2 || count > 26) throw new IllegalArgumentException("Choose 2 through 26 choices.");
        List<String> choices = new ArrayList<>();
        for (int i = 0; i < count; i++) { String value = read("Choice " + Question.indexToAnswerLabel(i) + ": "); if (value == null) { interrupted(); return; } choices.add(value); }
        String answer = read("Correct answer letter: "); if (answer == null) { interrupted(); return; }
        String explanation = read("Explanation (optional): "); if (explanation == null) { interrupted(); return; }
        Question question = new Question(id, prompt, choices, answer, explanation);
        if (updating) bank.update(id, question); else bank.create(question);
        out.println(updating ? "Question updated and saved." : "Question created and saved.");
    }
    private void interrupted() { out.println("Input ended. The incomplete edit was not applied."); }
    private void delete() throws IOException {
        String id = read("Question id to delete: "); if (id == null) return;
        if (bank.find(id).isEmpty()) { out.println("Question not found."); return; }
        String confirm = read("Delete " + id + "? Type yes to confirm: ");
        if (!"yes".equalsIgnoreCase(confirm)) { out.println("Deletion cancelled."); return; }
        bank.delete(id); out.println("Question deleted and saved.");
    }
    private void quiz() {
        List<Question> snapshot = bank.list();
        if (snapshot.isEmpty()) { out.println("No questions available. Create one first."); return; }
        QuizResult result = new QuizEngine(scanner, out).runQuiz(snapshot);
        out.println(result.isComplete() ? "Quiz finished!" : "Quiz interrupted; partial result only.");
        out.println("Answered: " + result.getTotalQuestions() + " of " + result.getPlannedQuestions());
        out.println("Unanswered: " + result.getUnansweredCount());
        out.println("Correct answers: " + result.getCorrectCount()); out.println("Incorrect answers: " + result.getIncorrectCount());
        out.println("Score on answered questions: " + String.format(Locale.ROOT, "%.2f", result.getFinalScorePercent()) + "%");
        if (result.getIncorrectCount() > 0) {
            String show = read("Show missed questions and correct answers? (y/n): ");
            if ("y".equalsIgnoreCase(show) || "yes".equalsIgnoreCase(show)) {
                for (QuestionAttempt attempt : result.getIncorrectAttempts()) {
                    out.println(attempt.getQuestion().getPrompt()); out.println("Your answer: " + attempt.getUserAnswerText());
                    out.println("Correct answer: " + attempt.getQuestion().getCorrectChoiceText());
                    out.println("Explanation: " + attempt.getQuestion().getExplanation());
                }
            }
        }
    }
}
