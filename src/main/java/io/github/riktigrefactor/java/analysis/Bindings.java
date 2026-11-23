package io.github.riktigrefactor.java.analysis;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
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
    public record Binding(String file, long start, String binding) {}

    private final Compilation compilation;
    private final Map<String, OffsetMap> offsetMaps;

    private Bindings(Compilation compilation, Map<String, OffsetMap> offsetMaps) {
        this.compilation = compilation;
        this.offsetMaps = offsetMaps;
    }

    /** Bindings of a compilation of text that {@code editsByFile} produced from the original. */
    public static Bindings of(Compilation compilation, Map<String, List<TextEdit>> editsByFile) {
        Map<String, OffsetMap> offsetMaps = new LinkedHashMap<>();
        for (String file : compilation.unitsByFile.keySet()) {
            offsetMaps.put(file, new OffsetMap(editsByFile.getOrDefault(file, List.of())));
        }
        return new Bindings(compilation, offsetMaps);
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
            OffsetMap offsets = offsetMaps.get(file);
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
                    if (start < 0 || end < 0) {
                        return;
                    }
                    if (offsets.spansInsertions(start, end)) {
                        return;
                    }
                    long originalStart = offsets.mapStart(start);
                    long originalEnd = offsets.mapEnd(end);
                    if (originalStart == OffsetMap.UNMAPPED || originalEnd == OffsetMap.UNMAPPED) {
                        return;
                    }
                    StringBuilder binding = new StringBuilder();
                    Element element = Occurrences.isStaticImportMember(path) ? null : compilation.trees.getElement(path);
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
                    String key = file + "|" + tree.getKind() + "|" + originalStart + "|" + originalEnd;
                    result.merge(key, new Binding(file, originalStart, binding.toString()),
                            (a, b) -> new Binding(a.file(), a.start(), a.binding() + " ; " + b.binding()));
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
            OffsetMap offsets = offsetMaps.get(file);
            return "source " + file + " " + element.getKind() + " " + leaf.getKind() + " "
                    + offsets.mapStart(compilation.start(unit, leaf)) + "-" + offsets.mapEnd(compilation.end(unit, leaf));
        }
        return switch (element) {
            case PackageElement pkg -> "package " + pkg.getQualifiedName();
            case TypeElement type when !hasSourceEnclosure(type) -> "type " + type.getQualifiedName();
            case ExecutableElement method -> element.getKind() + " " + elementKey(method.getEnclosingElement()) + "#"
                    + method.getSimpleName() + method.getParameters().stream()
                            .map(p -> compilation.types.erasure(p.asType()).toString())
                            .collect(Collectors.joining(",", "(", ")"));
            default -> element.getKind() + " " + (element.getEnclosingElement() == null
                    ? ""
                    : elementKey(element.getEnclosingElement())) + "#" + element.getSimpleName();
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
