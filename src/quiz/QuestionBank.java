package quiz;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Immutable questions; each successful command is persisted before becoming visible in memory. */
public final class QuestionBank {
    private static final String HEADER = "QUIZ-BANK\t1";
    private final Path file;
    private byte[] original;
    private List<Question> questions;
    public QuestionBank(Path file) throws IOException, QuestionFileException {
        this.file = file.toAbsolutePath().normalize();
        this.original = Files.readAllBytes(this.file);
        String text = decode(original);
        this.questions = text.startsWith("QUIZ-BANK") ? parse(text) : new QuestionLoader().loadQuestions(this.file);
        // The legacy parser reads the same file independently; refuse a concurrent change during load.
        if (!Arrays.equals(original, Files.readAllBytes(this.file))) throw new IOException("Question file changed while loading. Reopen it.");
    }
    public synchronized List<Question> list() { return questions; }
    public synchronized Optional<Question> find(String id) { return questions.stream().filter(q -> q.getId().equals(id)).findFirst(); }
    public synchronized void create(Question question) throws IOException {
        if (find(question.getId()).isPresent()) throw new IllegalArgumentException("Question id already exists.");
        List<Question> candidate = new ArrayList<>(questions); candidate.add(question); commit(candidate);
    }
    public synchronized void update(String id, Question replacement) throws IOException {
        if (!id.equals(replacement.getId())) throw new IllegalArgumentException("An update cannot change the question id.");
        if (find(id).isEmpty()) throw new IllegalArgumentException("Question not found.");
        List<Question> candidate = new ArrayList<>(questions);
        for (int i = 0; i < candidate.size(); i++) if (candidate.get(i).getId().equals(id)) candidate.set(i, replacement);
        commit(candidate);
    }
    public synchronized boolean delete(String id) throws IOException {
        if (find(id).isEmpty()) return false;
        List<Question> candidate = questions.stream().filter(q -> !q.getId().equals(id)).toList(); commit(candidate); return true;
    }
    private void commit(List<Question> candidate) throws IOException {
        byte[] serialized = serialize(candidate).getBytes(StandardCharsets.UTF_8);
        Path parent = file.getParent(); Path lockPath = parent.resolve(file.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another editor is saving this bank. Retry after reopening.");
            if (!Arrays.equals(original, Files.readAllBytes(file))) throw new IOException("Question bank changed. Reopen before saving; your command was not applied.");
            Path temporary = Files.createTempFile(parent, ".quiz-bank-", ".tmp");
            try {
                Files.write(temporary, serialized);
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
            original = serialized; questions = List.copyOf(candidate);
        } catch (java.nio.channels.OverlappingFileLockException error) {
            throw new IOException("Another editor is saving this bank. Retry after reopening.", error);
        }
    }
    private static String serialize(List<Question> questions) {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        for (Question q : questions) {
            text.append(encode(q.getId())).append('\t').append(encode(q.getPrompt())).append('\t')
                .append(q.getCorrectAnswer()).append('\t').append(encode(q.getExplanation())).append('\t').append(q.getChoices().size());
            for (String choice : q.getChoices()) text.append('\t').append(encode(choice));
            text.append('\n');
        }
        return text.append("END\t").append(questions.size()).append('\n').toString();
    }
    private static List<Question> parse(String text) throws QuestionFileException {
        List<String> lines = text.lines().toList();
        if (lines.size() < 2 || !lines.get(0).equals(HEADER)) throw new QuestionFileException("Unknown or incomplete question bank format.");
        List<Question> result = new ArrayList<>(); Set<String> ids = new HashSet<>();
        try {
            for (int i = 1; i < lines.size() - 1; i++) {
                String[] parts = lines.get(i).split("\t", -1);
                if (parts.length < 7) throw new IllegalArgumentException("Incomplete question row.");
                int count = Integer.parseInt(parts[4]); if (count < 2 || count > 26 || parts.length != count + 5) throw new IllegalArgumentException("Choice count mismatch.");
                List<String> choices = new ArrayList<>(); for (int j = 5; j < parts.length; j++) choices.add(unpack(parts[j]));
                Question q = new Question(unpack(parts[0]), unpack(parts[1]), choices, parts[2], unpack(parts[3]));
                if (!ids.add(q.getId())) throw new IllegalArgumentException("Duplicate question id."); result.add(q);
            }
            if (!lines.get(lines.size() - 1).equals("END\t" + result.size())) throw new IllegalArgumentException("Missing or invalid bank footer.");
            return List.copyOf(result);
        } catch (IllegalArgumentException | CharacterCodingException error) { throw new QuestionFileException("Invalid question bank: " + error.getMessage(), error); }
    }
    private static String encode(String text) { return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }
    private static String unpack(String text) throws CharacterCodingException { return decode(Base64.getDecoder().decode(text)); }
    private static String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
}
