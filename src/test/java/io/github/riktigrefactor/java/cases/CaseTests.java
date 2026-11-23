package io.github.riktigrefactor.java.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.riktigrefactor.java.Outcome;
import io.github.riktigrefactor.java.Warning;
import io.github.riktigrefactor.java.analysis.TextEdit;
import io.github.riktigrefactor.java.extractiife.ExtractIife;
import io.github.riktigrefactor.java.makestatic.MakeMethodStatic;
import io.github.riktigrefactor.java.movestatic.MoveStaticMember;
import io.github.riktigrefactor.java.rename.Rename;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Runs every case directory under src/test/cases as described in the specification's test-format.md. */
class CaseTests {
    static final Path CASES = Path.of("src", "test", "cases");

    @TestFactory
    Stream<DynamicTest> cases() throws IOException {
        List<Path> directories;
        try (Stream<Path> paths = Files.walk(CASES)) {
            directories = paths.filter(p -> Files.isRegularFile(p.resolve("case.json")))
                    .sorted()
                    .toList();
        }
        assertFalse(directories.isEmpty(), "No test cases found under " + CASES.toAbsolutePath());
        return directories.stream()
                .map(directory -> DynamicTest.dynamicTest(
                        CASES.relativize(directory).toString().replace('\\', '/'), () -> runNamed(directory)));
    }

    private static void runNamed(Path directory) throws IOException {
        try {
            run(Case.load(CASES, directory));
        } catch (AssertionError e) {
            throw new AssertionError(
                    CASES.relativize(directory).toString().replace('\\', '/') + ": " + e.getMessage(), e);
        }
    }

    private static Outcome refactor(Case testCase, boolean naive) {
        return switch (testCase.refactoring()) {
            case "rename" -> {
                String newName = testCase.options().path("newName").asText();
                yield naive
                        ? Rename.runNaive(testCase.input(), testCase.markedFile(), testCase.selectionStart(), newName)
                        : Rename.run(testCase.input(), testCase.markedFile(), testCase.selectionStart(), newName);
            }
            case "extract-iife" ->
                naive
                        ? ExtractIife.runNaive(
                                testCase.input(),
                                testCase.markedFile(),
                                testCase.selectionStart(),
                                testCase.selectionEnd())
                        : ExtractIife.run(
                                testCase.input(),
                                testCase.markedFile(),
                                testCase.selectionStart(),
                                testCase.selectionEnd());
            case "make-method-static" -> {
                String receiverName = testCase.options().path("receiverName").asText(null);
                yield naive
                        ? MakeMethodStatic.runNaive(
                                testCase.input(), testCase.markedFile(), testCase.selectionStart(), receiverName)
                        : MakeMethodStatic.run(
                                testCase.input(), testCase.markedFile(), testCase.selectionStart(), receiverName);
            }
            case "move-static-member" -> {
                String target = testCase.options().path("target").asText();
                yield naive
                        ? MoveStaticMember.runNaive(
                                testCase.input(), testCase.markedFile(), testCase.selectionStart(), target)
                        : MoveStaticMember.run(
                                testCase.input(), testCase.markedFile(), testCase.selectionStart(), target);
            }
            default -> throw new IllegalStateException("Unknown refactoring " + testCase.refactoring());
        };
    }

    private static final Pattern CITATION = Pattern.compile("Java §\\d+(\\.\\d+)* \\S.*");

    private static void run(Case testCase) throws IOException {
        assertFalse(testCase.spec().isEmpty(), "The case cites no section of the Java Language Specification");
        for (String citation : testCase.spec()) {
            assertTrue(
                    CITATION.matcher(citation).matches(),
                    "Citation not in the form 'Java §<number> <title>': " + citation);
        }
        String inputFailure = ProgramRunner.failure(testCase.input());
        assertNull(inputFailure, "The input program is a bad test: it " + inputFailure);

        Outcome outcome = refactor(testCase, false);
        if (testCase.naiveShouldFail()) {
            assertNaiveFails(testCase);
        }
        Path expectedDirectory = testCase.directory().resolve("expected");
        if (testCase.refuse() != null) {
            if (!(outcome instanceof Outcome.Refused refused)) {
                fail("Expected refusal " + testCase.refuse() + " but the refactoring succeeded");
                return;
            }
            assertEquals(
                    testCase.refuse(), refused.reason().code(), refused.reason().message());
            assertFalse(Files.exists(expectedDirectory), "A refusal case has no expected/ directory");
            return;
        }
        if (outcome instanceof Outcome.Refused refused) {
            fail("Refused with " + refused.reason().code() + ": "
                    + refused.reason().message());
            return;
        }
        Outcome.Success success = (Outcome.Success) outcome;
        List<String> warningCodes =
                success.warnings().stream().map(Warning::code).distinct().toList();
        for (String expectedWarning : testCase.warnings()) {
            assertTrue(
                    warningCodes.contains(expectedWarning),
                    "Missing warning " + expectedWarning + "; got " + warningCodes);
        }
        Map<String, String> output = TextEdit.apply(testCase.input(), success.edits());
        if (System.getenv("RIKTIG_WRITE_EXPECTED") != null && !Files.exists(expectedDirectory)) {
            for (var entry : output.entrySet()) {
                Path path = expectedDirectory.resolve(entry.getKey());
                Files.createDirectories(path.getParent());
                Files.writeString(path, entry.getValue(), StandardCharsets.UTF_8);
            }
        }
        Map<String, String> expected = Case.readFiles(expectedDirectory);
        assertFalse(expected.isEmpty(), "Missing expected/ directory; run with RIKTIG_WRITE_EXPECTED=1 to create it");
        assertEquals(expected, output);
        String outputFailure = ProgramRunner.failure(output);
        assertNull(outputFailure, "CORRECTNESS BUG: the refactored program " + outputFailure);
    }

    private static void assertNaiveFails(Case testCase) {
        Outcome naive = refactor(testCase, true);
        if (naive instanceof Outcome.Refused refused) {
            fail("The naive implementation refused (" + refused.reason().code() + "), so the case can't show it fails");
            return;
        }
        Map<String, String> naiveOutput = TextEdit.apply(testCase.input(), ((Outcome.Success) naive).edits());
        String failure = ProgramRunner.failure(naiveOutput);
        assertNotNull(
                failure, "The naive implementation passes this edge case, so its assertion can't detect the problem");
    }
}
