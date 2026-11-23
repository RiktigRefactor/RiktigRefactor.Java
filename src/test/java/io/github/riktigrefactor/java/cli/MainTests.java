package io.github.riktigrefactor.java.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTests {
    private static final String PROGRAM = """
            public class Program {
                public static void main(String[] args) {
                    int price = 3;
                    int total = price * 4;
                    System.out.println(total);
                }
            }
            """;

    @TempDir
    Path workspace;

    private record Result(int exitCode, String out, String err) {}

    private Result run(String... args) throws IOException {
        Files.writeString(workspace.resolve("Program.java"), PROGRAM);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int exitCode = Main.run(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void renamePrintsADiff() throws IOException {
        Result result = run(
                "rename",
                "--workspace",
                workspace.toString(),
                "--file",
                "Program.java",
                "--at",
                "3:13",
                "--new-name",
                "cost");
        assertEquals(0, result.exitCode(), result.err());
        assertEquals("""
                --- a/Program.java
                +++ b/Program.java
                @@ -3,2 +3,2 @@
                -        int price = 3;
                -        int total = price * 4;
                +        int cost = 3;
                +        int total = cost * 4;
                """, result.out());
    }

    @Test
    void renameApplyWritesTheFile() throws IOException {
        Result result = run(
                "rename",
                "--workspace",
                workspace.toString(),
                "--file",
                "Program.java",
                "--at",
                "3:13",
                "--new-name",
                "cost",
                "--apply");
        assertEquals(0, result.exitCode(), result.err());
        assertTrue(Files.readString(workspace.resolve("Program.java")).contains("int total = cost * 4;"));
    }

    @Test
    void extractIifeJsonFollowsTheProtocol() throws IOException {
        Result result = run(
                "extract-iife",
                "--workspace",
                workspace.toString(),
                "--file",
                "Program.java",
                "--range",
                "4:21-4:30",
                "--json");
        assertEquals(0, result.exitCode(), result.err());
        JsonNode json = new ObjectMapper().readTree(result.out());
        assertEquals("success", json.get("status").asText());
        assertEquals("Program.java", json.at("/edits/0/file").asText());
        assertEquals(4, json.at("/edits/0/start/line").asInt());
        assertEquals(21, json.at("/edits/0/start/column").asInt());
        assertEquals(
                "((java.util.function.IntSupplier) () -> ",
                json.at("/edits/0/newText").asText());
        assertTrue(json.at("/commit/subject").asText().startsWith(". r "));
    }

    @Test
    void refusalExitsWithOneAndExplains() throws IOException {
        Result result = run(
                "rename",
                "--workspace",
                workspace.toString(),
                "--file",
                "Program.java",
                "--at",
                "3:13",
                "--new-name",
                "total",
                "--json");
        assertEquals(1, result.exitCode());
        JsonNode json = new ObjectMapper().readTree(result.out());
        assertEquals("refused", json.get("status").asText());
        assertEquals("conflicting-declaration", json.at("/reasons/0/code").asText());
    }

    @Test
    void unknownFileIsAnError() throws IOException {
        Result result = run(
                "rename",
                "--workspace",
                workspace.toString(),
                "--file",
                "Missing.java",
                "--at",
                "1:1",
                "--new-name",
                "x");
        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("Missing.java"));
    }
}
