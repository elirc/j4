package quiz;

import java.util.Locale;
import java.util.Objects;

public final class QuestionAttempt {
    private final Question question;
    private final String userAnswer;

    public QuestionAttempt(Question question, String userAnswer) {
        this.question = Objects.requireNonNull(question, "question cannot be null");
        this.userAnswer = Objects.requireNonNull(userAnswer, "userAnswer cannot be null")
                .trim()
                .toUpperCase(Locale.ROOT);
        question.getChoiceText(this.userAnswer);
    }

    public Question getQuestion() {
        return question;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public boolean isCorrect() {
        return question.isCorrectAnswer(userAnswer);
    }

    public String getUserAnswerText() {
        return question.getChoiceText(userAnswer);
    }
}
