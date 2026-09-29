package quiz;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class Question {
    private final String id;
    private final String prompt;
    private final List<String> choices;
    private final String correctAnswer;
    private final String explanation;

    public Question(String id, String prompt, List<String> choices, String correctAnswer, String explanation) {
        this.id = requireText(id, "Question id");
        this.prompt = requireText(prompt, "Question prompt");
        this.choices = Objects.requireNonNull(choices, "Choices cannot be null").stream().map(choice -> requireText(choice, "Choice")).toList();
        this.correctAnswer = requireText(correctAnswer, "Correct answer").toUpperCase(Locale.ROOT);
        this.explanation = explanation == null ? "" : explanation.trim();

        validateChoices();
        validateCorrectAnswer();
    }

    public String getId() {
        return id;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<String> getChoices() {
        return choices;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public boolean hasExplanation() {
        return !explanation.isBlank();
    }

    public boolean isCorrectAnswer(String answer) {
        return answer != null && correctAnswer.equalsIgnoreCase(answer.trim());
    }

    public String getCorrectChoiceText() {
        return getChoiceText(correctAnswer);
    }

    public String getChoiceText(String answerLabel) {
        int index = answerLabelToIndex(answerLabel);
        if (index >= choices.size()) throw new IllegalArgumentException("Answer is outside this question's choices.");
        return choices.get(index);
    }

    public static String indexToAnswerLabel(int index) {
        if (index < 0 || index >= 26) throw new IllegalArgumentException("Choice index must be 0 through 25.");
        return String.valueOf((char) ('A' + index));
    }

    public static int answerLabelToIndex(String answerLabel) {
        String trimmed = requireText(answerLabel, "Answer label").toUpperCase(Locale.ROOT);

        if (trimmed.length() != 1 || trimmed.charAt(0) < 'A' || trimmed.charAt(0) > 'Z') {
            throw new IllegalArgumentException("Answer label must be a single letter.");
        }

        return trimmed.charAt(0) - 'A';
    }

    private void validateChoices() {
        if (choices.size() < 2 || choices.size() > 26) {
            throw new IllegalArgumentException("A question must have two through twenty-six choices.");
        }

        for (String choice : choices) {
            requireText(choice, "Choice");
        }
    }

    private void validateCorrectAnswer() {
        int correctIndex = answerLabelToIndex(correctAnswer);

        if (correctIndex >= choices.size()) {
            throw new IllegalArgumentException("Correct answer does not match any choice.");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null.");

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }

        return trimmed;
    }
}
