package io.github.riktigrefactor.java.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** A test case directory: case.json, input/ (with selection markers) and, unless a refusal is expected, expected/. */
record Case(
        Path directory,
        String refactoring,
        JsonNode options,
        String refuse,
        boolean naiveShouldFail,
        List<String> warnings,
        Map<String, String> input,
        String markedFile,
        int selectionStart,
        int selectionEnd) {

    static final String CURSOR = "/*|*/";
    static final String RANGE_START = "/*[*/";
    static final String RANGE_END = "/*]*/";

    static Case load(Path casesRoot, Path directory) {
        try {
            JsonNode json = new ObjectMapper().readTree(directory.resolve("case.json").toFile());
            String refactoring = casesRoot.relativize(directory).getName(0).toString();
            List<String> warnings = new ArrayList<>();
            json.path("warnings").forEach(w -> warnings.add(w.asText()));
            Map<String, String> input = readFiles(directory.resolve("input"));
            String markedFile = null;
            int start = -1;
            int end = -1;
            for (var entry : input.entrySet()) {
                String text = entry.getValue();
                if (!text.contains(CURSOR) && !text.contains(RANGE_START)) {
                    continue;
                }
                if (markedFile != null) {
                    throw new IllegalStateException("More than one file has a selection marker in " + directory);
                }
                markedFile = entry.getKey();
                if (text.contains(CURSOR)) {
                    start = text.indexOf(CURSOR);
                    end = start;
                    entry.setValue(text.replace(CURSOR, ""));
                } else {
                    start = text.indexOf(RANGE_START);
                    String withoutStart = text.replace(RANGE_START, "");
                    end = withoutStart.indexOf(RANGE_END);
                    entry.setValue(withoutStart.replace(RANGE_END, ""));
                }
            }
            if (markedFile == null) {
                throw new IllegalStateException("No selection marker in " + directory);
            }
            return new Case(directory, refactoring, json.path("options"), json.path("refuse").asText(null),
                    json.path("naiveShouldFail").asBoolean(false), warnings, input, markedFile, start, end);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, String> readFiles(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        if (!Files.isDirectory(root)) {
            return files;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                files.put(root.relativize(path).toString().replace('\\', '/'), Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
