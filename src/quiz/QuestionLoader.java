package quiz;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class QuestionLoader {
    public List<Question> loadQuestions(Path filePath) throws QuestionFileException {
        if (!Files.exists(filePath)) {
            throw new QuestionFileException("Question file was not found.");
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(filePath);
        } catch (IOException e) {
            throw new QuestionFileException("Question file could not be read.", e);
        }

        List<Question> questions = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int lineNumber = i + 1;

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            Question question = parseQuestion(line, lineNumber);
            if (!ids.add(question.getId())) throw new QuestionFileException("Duplicate question id at line " + lineNumber + ".");
            questions.add(question);
        }

        if (questions.isEmpty()) {
            throw new QuestionFileException("Question file does not contain any questions.");
        }

        return List.copyOf(questions);
    }

    private Question parseQuestion(String line, int lineNumber) throws QuestionFileException {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 4 || parts.length > 5) {
            throw new QuestionFileException("Malformed question at line " + lineNumber + ".");
        }

        String id = parts[0];
        String prompt = parts[1];
        List<String> choices = parseChoices(parts[2], lineNumber);
        String correctAnswer = parts[3];
        String explanation = parts.length == 5 ? parts[4] : "";

        try {
            return new Question(id, prompt, choices, correctAnswer, explanation);
        } catch (IllegalArgumentException e) {
            throw new QuestionFileException("Invalid question at line " + lineNumber + ": " + e.getMessage(), e);
        }
    }

    private List<String> parseChoices(String rawChoices, int lineNumber) throws QuestionFileException {
        String[] choiceParts = rawChoices.split(";", -1);
        List<String> choices = new ArrayList<>();

        for (String choicePart : choiceParts) {
            String trimmedChoice = choicePart.trim();

            if (trimmedChoice.isEmpty()) {
                throw new QuestionFileException("Empty answer choice found at line " + lineNumber + ".");
            }

            choices.add(trimmedChoice);
        }

        if (choices.size() < 2) {
            throw new QuestionFileException("Question at line " + lineNumber + " must have at least two choices.");
        }

        return choices;
    }
}
