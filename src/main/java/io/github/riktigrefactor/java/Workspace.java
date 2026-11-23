package io.github.riktigrefactor.java;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/** The .java files under a directory, by path relative to it with '/' separators. */
public record Workspace(Path root, Map<String, String> sources) {
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("target", "build", "out", "node_modules");

    /** Loads a directory, or the directory of a build file such as pom.xml. */
    public static Workspace load(Path path) throws IOException {
        Path root = (Files.isDirectory(path) ? path : path.toAbsolutePath().getParent())
                .toAbsolutePath()
                .normalize();
        Map<String, String> sources = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && Files.isRegularFile(f))
                    .toList()) {
                Path relative = root.relativize(file);
                if (isSkipped(relative)) {
                    continue;
                }
                sources.put(relative.toString().replace('\\', '/'), Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return new Workspace(root, sources);
    }

    private static boolean isSkipped(Path relative) {
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            String name = relative.getName(i).toString();
            if (name.startsWith(".") || SKIPPED_DIRECTORIES.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /** The workspace-relative form of a file given on the command line, relative to the root or the current directory. */
    public String relativeFile(String file) {
        String asGiven = file.replace('\\', '/');
        if (sources.containsKey(asGiven)) {
            return asGiven;
        }
        Path path = Path.of(file);
        Path absolute = path.isAbsolute() ? path : Path.of("").toAbsolutePath().resolve(path);
        try {
            String candidate = root.relativize(absolute.normalize()).toString().replace('\\', '/');
            if (sources.containsKey(candidate)) {
                return candidate;
            }
        } catch (IllegalArgumentException differentRoot) {
            // A path on another drive can't be inside the workspace.
        }
        return asGiven;
    }
}
