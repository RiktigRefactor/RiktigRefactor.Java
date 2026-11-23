package io.github.riktigrefactor.java.cases;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/** Compiles a set of source files and runs the one class that declares {@code public static void main(String[])}. */
final class ProgramRunner {
    /** Null when the program compiled and ran without throwing; otherwise what went wrong. */
    static String failure(Map<String, String> sources) {
        Path output;
        try {
            output = Files.createTempDirectory("riktig-case");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            String compileErrors = compile(sources, output);
            if (compileErrors != null) {
                return "does not compile: " + compileErrors;
            }
            return run(output);
        } finally {
            deleteRecursively(output);
        }
    }

    private static String compile(Map<String, String> sources, Path output) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(output.toFile()));
            fileManager.setLocation(StandardLocation.CLASS_PATH, List.of());
            List<JavaFileObject> files = new ArrayList<>();
            for (var entry : sources.entrySet()) {
                files.add(new SimpleJavaFileObject(URI.create("mem:///" + entry.getKey()), JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return entry.getValue();
                    }
                });
            }
            boolean ok = compiler.getTask(
                            null,
                            fileManager,
                            diagnostics,
                            List.of("-proc:none", "-Xlint:none", "-nowarn"),
                            null,
                            files)
                    .call();
            if (ok) {
                return null;
            }
            StringBuilder message = new StringBuilder();
            for (var diagnostic : diagnostics.getDiagnostics()) {
                message.append('\n')
                        .append(
                                diagnostic.getSource() == null
                                        ? ""
                                        : diagnostic.getSource().getName())
                        .append(':')
                        .append(diagnostic.getLineNumber())
                        .append(": ")
                        .append(diagnostic.getMessage(null));
            }
            return message.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String run(Path output) {
        List<String> classNames;
        try (Stream<Path> files = Files.walk(output)) {
            classNames = files.filter(f -> f.toString().endsWith(".class"))
                    .map(f -> output.relativize(f)
                            .toString()
                            .replace('\\', '/')
                            .replace('/', '.')
                            .replaceAll("\\.class$", ""))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (var loader =
                new URLClassLoader(new URL[] {output.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            loader.setDefaultAssertionStatus(true);
            List<Method> mains = new ArrayList<>();
            for (String className : classNames) {
                Class<?> type = Class.forName(className, false, loader);
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getName().equals("main")
                            && Modifier.isStatic(method.getModifiers())
                            && Modifier.isPublic(method.getModifiers())
                            && method.getParameterCount() == 1
                            && method.getParameterTypes()[0] == String[].class) {
                        mains.add(method);
                    }
                }
            }
            if (mains.size() != 1) {
                return "expected exactly one main method, found " + mains.size();
            }
            Method main = mains.getFirst();
            main.setAccessible(true);
            var executor = Executors.newSingleThreadExecutor();
            try {
                executor.submit(() -> main.invoke(null, (Object) new String[0])).get(10, TimeUnit.SECONDS);
                return null;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() instanceof InvocationTargetException invocation
                        ? invocation.getCause()
                        : e.getCause();
                return "throws " + cause;
            } catch (TimeoutException e) {
                return "did not finish within 10 seconds";
            } finally {
                executor.shutdownNow();
            }
        } catch (IOException | ReflectiveOperationException e) {
            return "could not be run: " + e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    private static void deleteRecursively(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ProgramRunner() {}
}
