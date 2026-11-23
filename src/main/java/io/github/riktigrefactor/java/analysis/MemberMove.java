package io.github.riktigrefactor.java.analysis;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;

/**
 * Moves a member declaration from one class to another: qualifies names that resolved through the source's scope,
 * deletes the declaration, and inserts its edited copy at the end of the target's body, with the imports it needs.
 */
public final class MemberMove {
    private final Compilation compilation;
    private final Support support;
    private final Element member;
    private final TypeElement source;
    private final TypeElement target;
    private final String sourceFile;
    private final String targetFile;
    private final CompilationUnitTree sourceUnit;
    private final int declarationStart;
    private final int declarationEnd;

    /** Edits inside the moved declaration, in the coordinates of the original text. */
    public final List<TextEdit> innerEdits = new ArrayList<>();

    private final Set<String> importsToAdd = new LinkedHashSet<>();

    public MemberMove(Compilation compilation, Element member, TypeElement target) {
        this.compilation = compilation;
        this.support = new Support(compilation);
        this.member = member;
        this.source = (TypeElement) member.getEnclosingElement();
        this.target = target;
        TreePath path = compilation.trees.getPath(member);
        this.sourceUnit = path.getCompilationUnit();
        this.sourceFile = compilation.fileOfUnit.get(sourceUnit);
        this.targetFile =
                compilation.fileOfUnit.get(compilation.trees.getPath(target).getCompilationUnit());
        this.declarationStart = (int) compilation.start(sourceUnit, path.getLeaf());
        this.declarationEnd = (int) compilation.end(sourceUnit, path.getLeaf());
    }

    public String sourceFile() {
        return sourceFile;
    }

    public String targetFile() {
        return targetFile;
    }

    /** Whether an edit lies inside the moved declaration, so it must be an inner edit of the copy. */
    public boolean isInside(String file, TextEdit edit) {
        return file.equals(sourceFile) && declarationStart <= edit.start() && edit.end() <= declarationEnd;
    }

    /** Names in the moved declaration that resolved through the source's scope are qualified with the source. */
    public void qualifySourceScope(TreePath path) {
        String sourceReference = support.typeReference(source, targetFile);
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(IdentifierTree tree, Void unused) {
                Element element = compilation.trees.getElement(getCurrentPath());
                int start = (int) compilation.start(sourceUnit, tree);
                if (element == null || element.equals(member) || start < 0) {
                    return null;
                }
                if (element instanceof TypeElement type) {
                    if (type.getNestingKind() == NestingKind.TOP_LEVEL) {
                        if (!sourceFile.equals(targetFile)
                                && !Support.packageOf(type).equals("java.lang")
                                && !Support.packageOf(type).equals(Support.packageOf(target))) {
                            importsToAdd.add(type.getQualifiedName().toString());
                        }
                    } else if (type.getNestingKind() == NestingKind.MEMBER) {
                        String reference = support.typeReference(type, targetFile);
                        innerEdits.add(new TextEdit(
                                sourceFile, start, start + tree.getName().length(), reference));
                    }
                    return null;
                }
                boolean isStaticMemberOfSourceScope = (element.getKind() == ElementKind.FIELD
                                || element.getKind() == ElementKind.METHOD)
                        && Support.isStatic(element)
                        && element.getEnclosingElement() instanceof TypeElement owner
                        && compilation.types.isSubtype(
                                compilation.types.erasure(source.asType()), compilation.types.erasure(owner.asType()));
                if (isStaticMemberOfSourceScope) {
                    innerEdits.add(TextEdit.insert(sourceFile, start, sourceReference + "."));
                }
                return null;
            }
        }.scan(path, null);
    }

    /** Deletes the declaration (with its line) and inserts its edited copy at the end of the target's body. */
    public void moveDeclaration(List<TextEdit> edits) {
        String text = compilation.sources.get(sourceFile);
        int deleteStart = declarationStart;
        while (deleteStart > 0 && (text.charAt(deleteStart - 1) == ' ' || text.charAt(deleteStart - 1) == '\t')) {
            deleteStart--;
        }
        int deleteEnd = declarationEnd;
        boolean wholeLine = deleteStart == 0 || text.charAt(deleteStart - 1) == '\n';
        if (wholeLine) {
            int lineEnd = deleteEnd;
            while (lineEnd < text.length() && (text.charAt(lineEnd) == ' ' || text.charAt(lineEnd) == '\t')) {
                lineEnd++;
            }
            if (lineEnd < text.length() && text.charAt(lineEnd) == '\n') {
                deleteEnd = lineEnd + 1;
                int afterBlank = deleteEnd;
                while (afterBlank < text.length()
                        && (text.charAt(afterBlank) == ' ' || text.charAt(afterBlank) == '\t')) {
                    afterBlank++;
                }
                boolean previousLineBlank = deleteStart >= 1
                        && text.substring(text.lastIndexOf('\n', deleteStart - 2) + 1, deleteStart - 1)
                                .isBlank();
                if (previousLineBlank && afterBlank < text.length() && text.charAt(afterBlank) == '\n') {
                    deleteEnd = afterBlank + 1;
                }
            } else {
                deleteStart = declarationStart;
            }
        } else {
            deleteStart = declarationStart;
        }
        edits.add(new TextEdit(sourceFile, deleteStart, deleteEnd, ""));

        CompilationUnitTree targetUnit = compilation.unitsByFile.get(targetFile);
        ClassTree targetTree = (ClassTree) compilation.trees.getPath(target).getLeaf();
        int closingBrace = (int) compilation.end(targetUnit, targetTree) - 1;
        String targetText = compilation.sources.get(targetFile);
        int lineStart = targetText.lastIndexOf('\n', closingBrace - 1) + 1;
        String closingIndent = targetText.substring(lineStart, closingBrace).isBlank()
                ? targetText.substring(lineStart, closingBrace)
                : "";
        String memberIndent = closingIndent + "    ";
        boolean emptyBody = targetTree.getMembers().stream().allMatch(m -> compilation.end(targetUnit, m) < 0);
        boolean braceOnOwnLine = targetText.substring(lineStart, closingBrace).isBlank();
        int insertAt = braceOnOwnLine ? lineStart : closingBrace;
        String prefix = (emptyBody ? "" : "\n") + memberIndent;
        String suffix = "\n";
        if (!braceOnOwnLine) {
            prefix = " ";
            suffix = " ";
        }
        edits.add(TextEdit.copy(
                compilation.sources,
                targetFile,
                insertAt,
                insertAt,
                sourceFile,
                declarationStart,
                declarationEnd,
                prefix,
                suffix,
                innerEdits));
        if (!importsToAdd.isEmpty()) {
            addImports(targetUnit, edits);
        }
    }

    private void addImports(CompilationUnitTree targetUnit, List<TextEdit> edits) {
        Set<String> existing = new LinkedHashSet<>();
        for (ImportTree importTree : targetUnit.getImports()) {
            existing.add(importTree.getQualifiedIdentifier().toString());
        }
        StringBuilder text = new StringBuilder();
        for (String name : importsToAdd) {
            if (!existing.contains(name)) {
                text.append("import ").append(name).append(";\n");
            }
        }
        if (text.isEmpty()) {
            return;
        }
        int at;
        if (!targetUnit.getImports().isEmpty()) {
            at = (int) compilation.end(targetUnit, targetUnit.getImports().getLast()) + 1;
        } else if (targetUnit.getPackage() != null) {
            at = (int) compilation.end(targetUnit, targetUnit.getPackage()) + 1;
            text.insert(0, "\n");
        } else {
            at = 0;
            text.append("\n");
        }
        edits.add(TextEdit.insert(targetFile, at, text.toString()));
    }
}
