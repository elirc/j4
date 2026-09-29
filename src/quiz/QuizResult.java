package quiz;

import java.util.List;

public class QuizResult {
    private final List<QuestionAttempt> attempts;
    private final int plannedQuestions;

    public QuizResult(List<QuestionAttempt> attempts) {
        this(attempts, attempts.size());
    }

    public QuizResult(List<QuestionAttempt> attempts, int plannedQuestions) {
        this.attempts = List.copyOf(attempts);
        if (plannedQuestions < attempts.size()) throw new IllegalArgumentException("Planned count is smaller than attempts.");
        this.plannedQuestions = plannedQuestions;
    }

    public int getPlannedQuestions() { return plannedQuestions; }
    public int getUnansweredCount() { return plannedQuestions - attempts.size(); }
    public boolean isComplete() { return getUnansweredCount() == 0; }

    public int getTotalQuestions() {
        return attempts.size();
    }

    public int getCorrectCount() {
        int correctCount = 0;

        for (QuestionAttempt attempt : attempts) {
            if (attempt.isCorrect()) {
                correctCount++;
            }
        }

        return correctCount;
    }

    public int getIncorrectCount() {
        return getTotalQuestions() - getCorrectCount();
    }

    public double getFinalScorePercent() {
        if (attempts.isEmpty()) {
            return 0.0;
        }

        return (getCorrectCount() * 100.0) / getTotalQuestions();
    }

    public List<QuestionAttempt> getAttempts() {
        return attempts;
    }

    public List<QuestionAttempt> getIncorrectAttempts() {
        return attempts.stream()
                .filter(attempt -> !attempt.isCorrect())
                .toList();
    }
}
