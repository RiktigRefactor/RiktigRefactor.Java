package io.github.riktigrefactor.java.analysis;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * What every tree in a compilation binds to, keyed by the tree's position in the original text, so that the bindings
 * of the original and of an edited copy can be compared (the re-binding check).
 */
public final class Bindings {
    /** Where a tree sits in the original text, and what it bound to. */
    public record Binding(String file, long start, long end, boolean viaCopy, String binding) {}

    private final Compilation compilation;
    private final Map<String, List<TextEdit>> editsByFile;

    private Bindings(Compilation compilation, Map<String, List<TextEdit>> editsByFile) {
        this.compilation = compilation;
        this.editsByFile = editsByFile;
    }

    /** Bindings of a compilation of text that {@code editsByFile} produced from the original. */
    public static Bindings of(Compilation compilation, Map<String, List<TextEdit>> editsByFile) {
        return new Bindings(compilation, editsByFile);
    }

    private EditMap mapOf(String file) {
        return EditMap.of(file, editsByFile);
    }

    /**
     * Records the binding of every tree that names an element, and with {@code includeTypes} the type of every
     * expression. Trees that lie inside edited text have no original position and are left out.
     */
    public Map<String, Binding> record(boolean includeTypes) {
        Map<String, Binding> result = new LinkedHashMap<>();
        for (var entry : compilation.unitsByFile.entrySet()) {
            String file = entry.getKey();
            CompilationUnitTree unit = entry.getValue();
            EditMap edits = mapOf(file);
            new TreePathScanner<Void, Void>() {
                @Override
                public Void scan(Tree tree, Void unused) {
                    if (tree != null) {
                        record(new TreePath(getCurrentPath(), tree));
                    }
                    return super.scan(tree, unused);
                }

                private void record(TreePath path) {
                    Tree tree = path.getLeaf();
                    long start = compilation.start(unit, tree);
                    long end = compilation.end(unit, tree);
                    if (start < 0 || end < 0 || edits.spansInsertions(start, end)) {
                        return;
                    }
                    Tree.Kind kind = tree.getKind();
                    EditMap.Span span = edits.map(start, end);
                    if (tree instanceof MemberSelectTree select && qualifierIsInserted(select)) {
                        long nameStart = end - select.getIdentifier().length();
                        span = edits.map(nameStart, end);
                        kind = Tree.Kind.IDENTIFIER;
                    }
                    if (span == null || span.start() == span.end()) {
                        return;
                    }
                    StringBuilder binding = new StringBuilder();
                    Element element =
                            Occurrences.isStaticImportMember(path) ? null : compilation.trees.getElement(path);
                    if (element != null) {
                        binding.append(elementKey(element));
                    }
                    if (includeTypes && tree instanceof ExpressionTree) {
                        TypeMirror type = compilation.trees.getTypeMirror(path);
                        binding.append(" : ").append(type == null ? "?" : normalize(type.toString()));
                    }
                    if (binding.isEmpty()) {
                        return;
                    }
                    String key = span.file() + "|" + kind + "|" + span.start() + "|" + span.end();
                    result.merge(
                            key,
                            new Binding(span.file(), span.start(), span.end(), span.viaCopy(), binding.toString()),
                            (a, b) -> new Binding(
                                    a.file(), a.start(), a.end(), a.viaCopy(), a.binding() + " ; " + b.binding()));
                }

                /** {@code x} rewritten as {@code q.x}, where q is new text: compared as the original identifier. */
                private boolean qualifierIsInserted(MemberSelectTree select) {
                    long start = compilation.start(unit, select.getExpression());
                    long end = compilation.end(unit, select.getExpression());
                    return start >= 0 && end >= 0 && edits.map(start, end) == null;
                }
            }.scan(new TreePath(unit), null);
        }
        return result;
    }

    private static String normalize(String type) {
        return type.replaceAll("capture#\\d+", "capture#");
    }

    /** A description of an element that is the same in the original and in an edited copy. */
    public String elementKey(Element element) {
        TreePath declaration = compilation.trees.getPath(element);
        if (declaration != null && compilation.fileOfUnit.containsKey(declaration.getCompilationUnit())) {
            CompilationUnitTree unit = declaration.getCompilationUnit();
            String file = compilation.fileOfUnit.get(unit);
            Tree leaf = declaration.getLeaf();
            long start = compilation.start(unit, leaf);
            long end = compilation.end(unit, leaf);
            String enclosingName = element.getEnclosingElement() == null
                    ? null
                    : element.getEnclosingElement().getSimpleName().toString();
            Token name =
                    new NameLocator(compilation).nameToken(unit, leaf, NameLocator.spelledName(leaf, enclosingName));
            if (name != null) {
                start = name.start();
                end = name.end();
            }
            EditMap.Span span = mapOf(file).map(start, end);
            if (span == null) {
                return "inserted " + element.getKind() + " " + element.getSimpleName();
            }
            return "source " + span.file() + " " + element.getKind() + " " + leaf.getKind() + " " + span.start() + "-"
                    + span.end();
        }
        return switch (element) {
            case PackageElement pkg -> "package " + pkg.getQualifiedName();
            case TypeElement type when !hasSourceEnclosure(type) -> "type " + type.getQualifiedName();
            case ExecutableElement method ->
                element.getKind() + " " + elementKey(method.getEnclosingElement()) + "#"
                        + method.getSimpleName()
                        + method.getParameters().stream()
                                .map(p -> compilation.types.erasure(p.asType()).toString())
                                .collect(Collectors.joining(",", "(", ")"));
            default ->
                element.getKind() + " "
                        + (element.getEnclosingElement() == null ? "" : elementKey(element.getEnclosingElement())) + "#"
                        + element.getSimpleName();
        };
    }

    private boolean hasSourceEnclosure(Element element) {
        for (Element e = element; e != null; e = e.getEnclosingElement()) {
            if (compilation.trees.getPath(e) != null) {
                return true;
            }
        }
        return false;
    }
}
