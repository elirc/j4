# Quiz Engine: current learning edition

Start with [astraupskill](astraupskill/README.md) for the current code map, detailed lessons, worked changes, practice and verification. The original walkthrough below describes the earlier read-only edition; this section records the implemented additions.

The app now supports question-bank CRUD: menu 4 lists questions, 5 creates, 6 updates by stable ID and 7 deletes after explicit confirmation. Original options 1–3 remain quiz, count and exit. Each accepted edit saves immediately using a candidate snapshot, cooperating file lock, stale-file check and atomic replacement. A failed command preserves the in-memory bank. Legacy question files remain readable; the first accepted edit converts the selected file to the versioned format. That format supports delimiter characters and multiline text through Base64-encoded UTF-8 fields. Base64 is encoding, not encryption. The old program cannot read the new format; keep a personal backup before using an older binary again.

EOF during a form discards the incomplete command. EOF during a quiz reports answered and unanswered counts separately and labels the score as applying only to answered questions. Duplicate IDs, impossible choice labels, invalid attempts and malformed files are rejected.

```powershell
# JDK 17+ on PATH, from the project root:
.\run.ps1 -Test
.\run.ps1
# Use a separate copy for experimentation:
Copy-Item -LiteralPath data/questions.txt -Destination "$env:TEMP/quiz-learning.txt"
.\run.ps1 -DataPath "$env:TEMP/quiz-learning.txt"
```

The test runner uses only temporary banks. The checked-in example data was not modified. This remains a local Java CLI; the course maps its command and persistence boundaries to future HTTP/SQL CRUD work without claiming a web application exists. File locks coordinate cooperating writers; arbitrary external editors and power-loss durability are outside the verified guarantee.

---

## Original read-only edition walkthrough

# Quiz Engine

This project is a small command-line quiz application written in plain Java. It loads questions from a local file, asks them one at a time, checks the user's answers, tracks the score, and shows results at the end.

The project is intentionally small and explicit so a beginner can understand the whole codebase without needing frameworks or advanced Java features.

## 1. Project overview

The app starts in `App.java`. On startup, it loads questions from `data/questions.txt`. If loading works, the app shows a small menu:

- `1` Start quiz
- `2` View available questions count
- `3` Exit

When the user starts the quiz, `QuizEngine` loops through the loaded questions in order. For each question, it prints the prompt and answer choices, accepts a letter like `A` or `B`, validates the input, and stores the user's answer. When the quiz ends, `QuizResult` calculates:

- total questions
- correct answers
- incorrect answers
- final score percentage

If the user wants, the program also shows missed questions, the correct answers, and explanations.

## 2. File-by-file explanation

### `src/quiz/App.java`

This is the entry point and the main menu controller.

Responsibilities:

- start the application
- load questions at startup
- print the menu
- react to menu choices
- start the quiz
- print the final results

It does not parse the question file itself and it does not contain the scoring rules. That logic is kept in other classes.

### `src/quiz/Question.java`

This class represents one quiz question.

It stores:

- `id`
- `prompt`
- `choices`
- `correctAnswer`
- `explanation`

It also contains validation and helper methods, such as converting choice indexes into labels like `A`, `B`, `C`, and checking whether an answer is correct.

### `src/quiz/QuestionAttempt.java`

This class represents one user's answer to one question.

It connects:

- the `Question`
- the user's selected answer

This makes it easy to calculate results later and to show missed questions at the end.

### `src/quiz/QuestionFileException.java`

This is a custom exception class for file-loading problems.

Using a custom exception makes error messages clearer. Instead of a generic error, we can say exactly that the quiz file is missing or malformed.

### `src/quiz/QuestionLoader.java`

This class reads the local question file and turns each valid line into a `Question` object.

Responsibilities:

- check whether the file exists
- read all lines
- skip blank lines and comments
- parse each line
- validate the data
- return a list of `Question` objects

This keeps file I/O and parsing separate from the quiz gameplay.

### `src/quiz/QuizEngine.java`

This class runs the quiz itself.

Responsibilities:

- show one question at a time
- print answer choices
- read user input
- validate user input
- create a `QuestionAttempt` for each answer
- return a `QuizResult` after the loop finishes

This is the core quiz loop.

### `src/quiz/QuizResult.java`

This class stores all attempts from one quiz session and calculates summary data from them.

Responsibilities:

- count total questions
- count correct answers
- count incorrect answers
- calculate final score percent
- return only the incorrect attempts when needed

This keeps scoring logic in one place.

### `data/questions.txt`

This is the local data file for quiz questions.

Each non-comment line contains one question in a simple text format.

### `README.md`

This file explains the project in beginner-friendly language so you can study the design after reading the code.

## 3. Class-by-class explanation

### `App`

`App` is the high-level coordinator. It does not do everything itself. Instead, it tells the right class to do the right job.

Why this is good for learning:

- you see how one class can coordinate others
- the `main` method stays small
- business logic does not get mixed into startup code

### `Question`

`Question` is a model class. A model class stores data that belongs together.

Here, one question has:

- a unique id
- a prompt
- a list of answer choices
- the correct answer label
- an optional explanation

This class also protects its own data by validating that:

- text fields are not blank
- there are at least two choices
- the correct answer label points to a real choice

### `QuestionAttempt`

This is another model class. It stores what happened when the user answered one question.

This is useful because it keeps quiz history in a clean structure instead of spreading it across many variables.

### `QuestionFileException`

This class exists to describe quiz file problems clearly.

Examples:

- the file does not exist
- a line has too few fields
- a question has an invalid correct answer

### `QuestionLoader`

This is a repository-style class. In bigger projects you may hear terms like repository, loader, or data access class. Here it simply means: "the class that gets questions from storage."

Storage in this project is a plain text file.

### `QuizEngine`

This class contains the quiz loop. It knows how to ask questions and collect answers, but it does not know how to read the file. That is a separate responsibility.

### `QuizResult`

This class represents the final outcome of one quiz session.

It does not ask questions. It only works with the completed list of attempts and computes the totals.

## 4. Method-by-method explanation

### `App.main`

Creates the app and starts it.

### `App.run`

Loads questions first. If loading fails, the program stops gracefully. If loading succeeds, it shows the menu in a loop until the user exits.

### `App.loadQuestions`

Uses `QuestionLoader` to read the file. If something goes wrong, it catches the exception and prints a friendly message.

### `App.printMenu`

Prints the three menu options.

### `App.startQuiz`

Creates a `QuizEngine`, runs the quiz, receives a `QuizResult`, and prints the summary.

### `App.showQuestionCount`

Prints how many questions are currently loaded.

### `App.shouldShowCorrectAnswers`

Asks the user whether they want to see missed questions and correct answers.

### `App.printMissedQuestions`

Loops through incorrect attempts and shows:

- the question prompt
- the user's answer
- the correct answer
- the explanation if available

### `Question.Question(...)`

The constructor stores the question data and validates it immediately.

This is a good habit: invalid objects should usually be blocked as early as possible.

### `Question.get...`

These getter methods return the question's fields.

### `Question.hasExplanation`

Returns `true` if the explanation is not blank.

### `Question.isCorrectAnswer`

Checks whether the user's answer matches the correct answer label.

### `Question.getCorrectChoiceText`

Returns the actual answer text for the correct label.

Example:

- label: `A`
- answer text: `Java Virtual Machine`

### `Question.getChoiceText`

Converts a label like `B` into the choice text at the matching list position.

### `Question.indexToAnswerLabel`

Converts a number like `0` into `A`, `1` into `B`, and so on.

### `Question.answerLabelToIndex`

Converts `A` into `0`, `B` into `1`, and so on.

### `Question.validateChoices`

Checks that the question has at least two choices and that each choice has text.

### `Question.validateCorrectAnswer`

Checks that the correct answer label points to a real choice.

### `Question.requireText`

A helper method that checks that important text values are not null or blank.

### `QuestionAttempt.QuestionAttempt(...)`

Stores the question and the user's answer. It also normalizes the answer to uppercase.

### `QuestionAttempt.getQuestion`

Returns the related `Question`.

### `QuestionAttempt.getUserAnswer`

Returns the user's chosen label.

### `QuestionAttempt.isCorrect`

Checks whether the attempt is correct by asking the `Question`.

### `QuestionAttempt.getUserAnswerText`

Returns the full text for the user's selected choice.

### `QuestionLoader.loadQuestions`

Checks whether the file exists, reads all lines, skips blank lines and comments, parses each valid line, and returns the full list.

### `QuestionLoader.parseQuestion`

Splits one line into fields and builds a `Question`.

### `QuestionLoader.parseChoices`

Splits the choices field by semicolons and validates that each choice has text.

### `QuizEngine.runQuiz`

Loops over all questions, asks them one by one, stores the attempts, and returns a result object at the end.

### `QuizEngine.askQuestion`

Prints one question and its choices, reads a valid answer, gives a small response, and creates a `QuestionAttempt`.

### `QuizEngine.readValidAnswer`

Keeps asking until the user enters a valid choice letter.

### `QuizEngine.isValidAnswer`

Checks whether the input is a single letter within the available choice range.

### `QuizResult.QuizResult(...)`

Stores the finished list of attempts.

### `QuizResult.getTotalQuestions`

Returns the total number of attempts.

### `QuizResult.getCorrectCount`

Counts how many attempts are correct.

### `QuizResult.getIncorrectCount`

Returns total minus correct.

### `QuizResult.getFinalScorePercent`

Calculates the score percentage.

### `QuizResult.getAttempts`

Returns all attempts.

### `QuizResult.getIncorrectAttempts`

Returns only the attempts where the user answered incorrectly.

## 5. How question loading works

The project uses a simple custom text format instead of JSON.

One line looks like this:

```text
Q1|What does JVM stand for?|Java Virtual Machine;Java Variable Method;Joint Visual Model;Java Verified Mode|A|The JVM runs Java bytecode on your computer.
```

The `|` character separates the main fields:

1. id
2. prompt
3. all choices
4. correct answer letter
5. optional explanation

Inside the choices field, `;` separates each answer choice.

Why use this format?

- it is easy to read
- it is easy to edit by hand
- it avoids needing a JSON library
- the parser stays short enough for a beginner to understand

Why not JSON?

Plain Java does not include a built-in JSON parser that is beginner-friendly. If we used JSON without a library, the parsing code would become more complicated than the quiz itself. Since the goal is learning core Java, a simple text format is a better fit.

## 6. How the quiz loop works

The quiz loop lives in `QuizEngine.runQuiz`.

The flow is:

1. Start with an empty list of attempts.
2. Loop through the questions in order.
3. Print the current question.
4. Print the choices as `A`, `B`, `C`, and so on.
5. Read the user's input.
6. If the input is invalid, ask again.
7. Create a `QuestionAttempt`.
8. After all questions are answered, build a `QuizResult`.

This is a good example of control flow because it shows:

- loops
- method calls
- validation
- object creation
- list usage

## 7. How scoring works

Scoring is handled by `QuizResult`.

It does not store a separate score variable that is updated during the quiz. Instead, it calculates the result from the attempts list when needed.

That means:

- `getCorrectCount()` counts correct attempts
- `getIncorrectCount()` uses total minus correct
- `getFinalScorePercent()` calculates the percentage

This approach is simple and keeps scoring logic in one class.

## 8. Why the project was structured this way

This structure tries to balance two goals:

- small enough for a beginner
- organized enough to teach good habits

Why not put everything in one file?

You could, but then file reading, question data, the menu, the quiz loop, and scoring would all mix together. That becomes harder to read and harder to change.

Why not create many more classes?

You could also go too far and make the project harder for a beginner by adding too many layers and abstractions. This version keeps only the classes that clearly help:

- one app/controller class
- a few model classes
- one loader class
- one engine class
- one result class

This teaches separation of concerns without making the design feel magical.

## 9. What a beginner should study first

Study these parts in this order:

1. `Question`
2. `QuestionAttempt`
3. `QuizResult`
4. `QuestionLoader`
5. `QuizEngine`
6. `App`

Why this order works:

- first learn the data objects
- then learn how data is loaded
- then learn how the quiz runs
- finally learn how the whole app is coordinated

Important Java topics to review while reading:

- classes and objects
- constructors
- fields
- methods
- `List`
- loops
- `if` statements
- `Scanner`
- file I/O with `Files.readAllLines`
- exceptions

## 10. Three small exercises to extend the project

### Exercise 1: Add shuffle mode

Add a menu option that shuffles the order of questions before starting the quiz.

What you will practice:

- list copying
- `Collections.shuffle`
- making behavior optional

### Exercise 2: Accept numeric answers too

Let the user answer with `1`, `2`, `3`, `4` as well as `A`, `B`, `C`, `D`.

What you will practice:

- input normalization
- converting numbers to indexes
- validation rules

### Exercise 3: Save quiz results to a file

After the quiz ends, write the final result summary to a text file.

What you will practice:

- file output
- building strings
- reusing result data

## How to compile and run

From the project root:

```powershell
javac -d out src\quiz\*.java
java -cp out quiz.App
```

Make sure you run the program from the project root so it can find `data/questions.txt`.

## Example session

```text
Welcome to the Quiz Engine

1. Start quiz
2. View available questions count
3. Exit
Choose an option: 1

Question 1 of 5
What does JVM stand for?
A. Java Virtual Machine
B. Java Variable Method
C. Joint Visual Model
D. Java Verified Mode
Your answer: A
Correct!
```
