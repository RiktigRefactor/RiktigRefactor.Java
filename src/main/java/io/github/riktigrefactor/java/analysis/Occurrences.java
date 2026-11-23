package io.github.riktigrefactor.java.analysis;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Element;

/** Every name in the workspace that the compiler binds to an element, with the token that spells it. */
public final class Occurrences {
    public record Occurrence(String file, Token token, Element element, TreePath path) {}

    private Occurrences() {}

    public static List<Occurrence> find(Compilation compilation) {
        List<Occurrence> result = new ArrayList<>();
        NameLocator locator = new NameLocator(compilation);
        for (var entry : compilation.unitsByFile.entrySet()) {
            String file = entry.getKey();
            CompilationUnitTree unit = entry.getValue();
            new TreePathScanner<Void, Void>() {
                private String enclosingClassName = null;

                @Override
                public Void visitClass(ClassTree tree, Void unused) {
                    record(getCurrentPath());
                    String saved = enclosingClassName;
                    enclosingClassName = tree.getSimpleName().toString();
                    super.visitClass(tree, unused);
                    enclosingClassName = saved;
                    return null;
                }

                @Override
                public Void scan(Tree tree, Void unused) {
                    if (tree != null && !(tree instanceof ClassTree)) {
                        record(new TreePath(getCurrentPath(), tree));
                    }
                    return super.scan(tree, unused);
                }

                private void record(TreePath path) {
                    Tree tree = path.getLeaf();
                    String name = NameLocator.spelledName(tree, enclosingClassName);
                    if (name == null || isStaticImportMember(path)) {
                        return;
                    }
                    Element element = compilation.trees.getElement(path);
                    if (element == null) {
                        return;
                    }
                    Token token = locator.nameToken(unit, tree, name);
                    if (token != null) {
                        result.add(new Occurrence(file, token, element, path));
                    }
                }
            }.scan(new TreePath(unit), null);
        }
        return result;
    }

    /** The member name of {@code import static p.T.name;}, which names every static member called name at once. */
    public static boolean isStaticImportMember(TreePath path) {
        return path.getParentPath() != null
                && path.getParentPath().getLeaf() instanceof ImportTree importTree
                && importTree.isStatic()
                && importTree.getQualifiedIdentifier() == path.getLeaf();
    }
}
