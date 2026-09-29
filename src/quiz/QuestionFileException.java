package quiz;

public class QuestionFileException extends Exception {
    private static final long serialVersionUID = 1L;
    public QuestionFileException(String message) {
        super(message);
    }

    public QuestionFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
