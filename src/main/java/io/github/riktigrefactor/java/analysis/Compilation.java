package io.github.riktigrefactor.java.analysis;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/** One parse and full attribution (including flow analysis) of a set of in-memory source files. */
public final class Compilation {
    public final Map<String, String> sources;
    public final Map<String, CompilationUnitTree> unitsByFile = new LinkedHashMap<>();
    public final Map<CompilationUnitTree, String> fileOfUnit = new LinkedHashMap<>();
    public final Map<String, List<Token>> tokensByFile = new LinkedHashMap<>();
    public final List<Diagnostic<? extends JavaFileObject>> errors = new ArrayList<>();
    public final Trees trees;
    public final Elements elements;
    public final Types types;
    public final SourcePositions positions;

    private Compilation(Map<String, String> sources) {
        this.sources = sources;
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        try {
            fileManager.setLocation(StandardLocation.CLASS_PATH, List.of());
            fileManager.setLocation(StandardLocation.SOURCE_PATH, List.of());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<JavaFileObject> files = new ArrayList<>();
        for (var entry : sources.entrySet()) {
            files.add(new InMemorySource(entry.getKey(), entry.getValue()));
        }
        JavacTask task = (JavacTask) compiler.getTask(
                null, fileManager, diagnostics, List.of("-proc:none", "-implicit:none", "-Xlint:none"), null, files);
        Iterable<? extends CompilationUnitTree> units;
        try {
            units = task.parse();
            task.analyze();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (CompilationUnitTree unit : units) {
            String file = pathOf(unit.getSourceFile());
            unitsByFile.put(file, unit);
            fileOfUnit.put(unit, file);
            tokensByFile.put(file, Lexer.tokenize(sources.get(file)));
        }
        for (var diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(diagnostic);
            }
        }
        trees = Trees.instance(task);
        elements = task.getElements();
        types = task.getTypes();
        positions = trees.getSourcePositions();
    }

    public static Compilation analyze(Map<String, String> sources) {
        return new Compilation(sources);
    }

    public static String fileOf(Diagnostic<? extends JavaFileObject> diagnostic) {
        return diagnostic.getSource() == null ? null : pathOf(diagnostic.getSource());
    }

    private static String pathOf(JavaFileObject file) {
        return file.toUri().getPath().substring(1);
    }

    public long start(CompilationUnitTree unit, com.sun.source.tree.Tree tree) {
        return positions.getStartPosition(unit, tree);
    }

    public long end(CompilationUnitTree unit, com.sun.source.tree.Tree tree) {
        return positions.getEndPosition(unit, tree);
    }

    static final class InMemorySource extends SimpleJavaFileObject {
        final String path;
        private final String content;

        InMemorySource(String path, String content) {
            super(toUri(path), Kind.SOURCE);
            this.path = path;
            this.content = content;
        }

        private static URI toUri(String path) {
            try {
                return new URI("mem", null, "/" + path, null);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(path, e);
            }
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }
}
