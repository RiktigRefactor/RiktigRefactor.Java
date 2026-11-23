package io.github.riktigrefactor.java.analysis;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/** Type relationships among the classes, interfaces, enums and records declared in the workspace, anonymous ones included. */
public final class SourceTypes {
    private final Compilation compilation;
    public final List<TypeElement> all = new ArrayList<>();

    public SourceTypes(Compilation compilation) {
        this.compilation = compilation;
        for (var unit : compilation.unitsByFile.values()) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitClass(ClassTree tree, Void unused) {
                    if (compilation.trees.getElement(getCurrentPath()) instanceof TypeElement type) {
                        all.add(type);
                    }
                    return super.visitClass(tree, unused);
                }
            }.scan(new TreePath(unit), null);
        }
    }

    public boolean isInSource(Element element) {
        TreePath path = compilation.trees.getPath(element);
        return path != null && compilation.fileOfUnit.containsKey(path.getCompilationUnit());
    }

    /** Whether {@code member} is declared in {@code type} or one of its supertypes. */
    public boolean isMemberOf(Element member, TypeElement type) {
        return compilation.types.isSubtype(
                compilation.types.erasure(type.asType()),
                compilation.types.erasure(member.getEnclosingElement().asType()));
    }

    /** Every proper supertype of {@code type}, transitively. */
    public Set<TypeElement> supertypes(TypeElement type) {
        Set<TypeElement> result = new LinkedHashSet<>();
        addSupertypes(type.asType(), result);
        return result;
    }

    private void addSupertypes(TypeMirror type, Set<TypeElement> result) {
        for (TypeMirror supertype : compilation.types.directSupertypes(type)) {
            if (supertype instanceof DeclaredType declared
                    && declared.asElement() instanceof TypeElement element
                    && result.add(element)) {
                addSupertypes(supertype, result);
            }
        }
    }

    public static List<ExecutableElement> methodsNamed(TypeElement type, CharSequence name) {
        List<ExecutableElement> result = new ArrayList<>();
        for (Element member : type.getEnclosedElements()) {
            if (member.getKind() == ElementKind.METHOD && member.getSimpleName().contentEquals(name)) {
                result.add((ExecutableElement) member);
            }
        }
        return result;
    }
}
