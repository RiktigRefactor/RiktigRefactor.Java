package io.github.riktigrefactor.java.rename;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.github.riktigrefactor.java.Outcome;
import io.github.riktigrefactor.java.RefusalCodes;
import io.github.riktigrefactor.java.Warning;
import io.github.riktigrefactor.java.analysis.Bindings;
import io.github.riktigrefactor.java.analysis.Compilation;
import io.github.riktigrefactor.java.analysis.NameLocator;
import io.github.riktigrefactor.java.analysis.Occurrences;
import io.github.riktigrefactor.java.analysis.Occurrences.Occurrence;
import io.github.riktigrefactor.java.analysis.Prepared;
import io.github.riktigrefactor.java.analysis.TextEdit;
import io.github.riktigrefactor.java.analysis.Token;
import io.github.riktigrefactor.java.analysis.Verification;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Renames a symbol and every reference to it, then proves that every name still binds as before. */
public final class Rename {
    private static final Set<String> RESTRICTED_IDENTIFIERS = Set.of("var", "yield", "record", "sealed", "permits", "_");
    private static final Set<String> NAMES_LOOKED_UP_BY_THE_RUNTIME = Set.of(
            "serialVersionUID", "serialPersistentFields", "writeObject", "readObject", "readObjectNoData",
            "writeReplace", "readResolve", "finalize");

    private final Compilation compilation;
    private final String newName;

    private Rename(Compilation compilation, String newName) {
        this.compilation = compilation;
        this.newName = newName;
    }

    public static Outcome run(Map<String, String> sources, String file, int cursor, String newName) {
        return run(sources, file, cursor, newName, false);
    }

    /** The deliberately naive rename: edits the references of the one symbol at the cursor, with no checks. */
    public static Outcome runNaive(Map<String, String> sources, String file, int cursor, String newName) {
        return run(sources, file, cursor, newName, true);
    }

    private static Outcome run(Map<String, String> sources, String file, int cursor, String newName, boolean naive) {
        Prepared prepared = Prepared.analyze(sources);
        if (prepared.refusal() != null) {
            return prepared.refusal();
        }
        return new Rename(prepared.compilation(), newName).rename(file, cursor, naive);
    }

    private Outcome rename(String file, int cursor, boolean naive) {
        List<Occurrence> occurrences = Occurrences.find(compilation);
        Occurrence atCursor = null;
        for (Occurrence occurrence : occurrences) {
            if (occurrence.file().equals(file)
                    && occurrence.token().start() <= cursor
                    && cursor <= occurrence.token().end()) {
                atCursor = occurrence;
                break;
            }
        }
        if (atCursor == null) {
            return Outcome.refuse(RefusalCodes.NO_SYMBOL, "There is no renameable symbol at the cursor.", file, cursor, cursor,
                    "Put the cursor on the name of a declaration or a reference to one.");
        }
        Element target = atCursor.element();
        if (target.getKind() == ElementKind.CONSTRUCTOR) {
            target = target.getEnclosingElement();
        }
        String oldName = target.getSimpleName().toString();
        Token at = atCursor.token();
        if (newName.equals(oldName)) {
            return new Outcome.Success(List.of(), List.of());
        }
        Set<Element> targets;
        if (naive) {
            targets = Set.of(target);
        } else {
            Outcome refusal = checkRenameable(target, oldName, file, at);
            if (refusal != null) {
                return refusal;
            }
            if (target instanceof ExecutableElement method) {
                var hierarchy = new Hierarchy(compilation).of(method);
                if (hierarchy.externalOverridden() != null) {
                    return Outcome.refuse(RefusalCodes.OVERRIDES_EXTERNAL,
                            "'" + oldName + "' overrides or is overridden alongside " + hierarchy.externalOverridden()
                                    + ", which is not declared in the workspace.",
                            file, at.start(), at.end());
                }
                targets = new LinkedHashSet<>(hierarchy.methods());
            } else {
                targets = Set.of(target);
            }
        }

        Map<String, TextEdit> editsByPosition = new LinkedHashMap<>();
        for (Occurrence occurrence : occurrences) {
            Element element = occurrence.element();
            boolean isTarget = targets.contains(element)
                    || (element.getKind() == ElementKind.CONSTRUCTOR
                            && occurrence.path().getLeaf() instanceof MethodTree
                            && targets.contains(element.getEnclosingElement()));
            if (isTarget) {
                Token token = occurrence.token();
                editsByPosition.put(occurrence.file() + ":" + token.start(),
                        new TextEdit(occurrence.file(), token.start(), token.end(), newName));
            }
        }
        Outcome staticImportRefusal = editStaticImports(oldName, targets, editsByPosition, naive);
        if (staticImportRefusal != null) {
            return staticImportRefusal;
        }
        List<TextEdit> edits = new ArrayList<>(editsByPosition.values());
        List<Warning> warnings = stringsEqualToName(oldName);
        if (naive) {
            return new Outcome.Success(edits, warnings);
        }

        Verification verification = Verification.verify(compilation, edits, false, error -> {
            String code = error.getCode();
            return code != null && code.startsWith("compiler.err.already.defined")
                    ? RefusalCodes.CONFLICTING_DECLARATION
                    : RefusalCodes.WOULD_INTRODUCE_ERRORS;
        });
        if (verification.refusal != null) {
            return new Outcome.Refused(verification.refusal);
        }
        Set<String> overridingBefore = new Overriding(compilation, Bindings.of(compilation, Map.of())).pairs();
        Set<String> overridingAfter =
                new Overriding(verification.edited, Bindings.of(verification.edited, verification.editsByFile)).pairs();
        if (!overridingBefore.equals(overridingAfter)) {
            Set<String> changed = new TreeSet<>(overridingAfter);
            changed.removeAll(overridingBefore);
            if (changed.isEmpty()) {
                changed = new TreeSet<>(overridingBefore);
                changed.removeAll(overridingAfter);
            }
            return Outcome.refuse(RefusalCodes.OVERRIDING_WOULD_CHANGE,
                    "The rename would change which methods override which: " + changed.iterator().next(),
                    file, at.start(), at.end());
        }
        return new Outcome.Success(edits, warnings);
    }

    private Outcome checkRenameable(Element target, String oldName, String file, Token at) {
        if (!SourceVersion.isIdentifier(newName) || SourceVersion.isKeyword(newName)
                || RESTRICTED_IDENTIFIERS.contains(newName)) {
            return Outcome.refuse(RefusalCodes.INVALID_IDENTIFIER,
                    "'" + newName + "' is not a valid Java identifier, or is a keyword or restricted identifier.",
                    file, at.start(), at.end());
        }
        TreePath declaration = compilation.trees.getPath(target);
        if (declaration == null || !compilation.fileOfUnit.containsKey(declaration.getCompilationUnit())
                || target.getKind() == ElementKind.PACKAGE || target.getKind() == ElementKind.MODULE
                || compilation.positions.getEndPosition(declaration.getCompilationUnit(), declaration.getLeaf()) < 0) {
            return Outcome.refuse(RefusalCodes.NOT_RENAMEABLE,
                    "'" + oldName + "' is not declared in the workspace source (or is implicitly declared), so it can't be renamed.",
                    file, at.start(), at.end());
        }
        Element owner = target.getEnclosingElement();
        if (target.getKind() == ElementKind.METHOD && owner.getKind() == ElementKind.ANNOTATION_TYPE) {
            return Outcome.refuse(RefusalCodes.ANNOTATION_MEMBER,
                    "'" + oldName + "' is an annotation element; its name is read by reflection and by the `value` shorthand.",
                    file, at.start(), at.end());
        }
        if (isRecordComponentOrItsParts(target)) {
            return Outcome.refuse(RefusalCodes.RECORD_COMPONENT,
                    "'" + oldName + "' is a record component (or its field, accessor or canonical constructor parameter); renaming it is not supported yet.",
                    file, at.start(), at.end());
        }
        if (target instanceof ExecutableElement method && (isMainMethod(method) || method.getModifiers().contains(Modifier.NATIVE))) {
            return Outcome.refuse(RefusalCodes.ENTRY_POINT,
                    "'" + oldName + "' is found by name from outside Java (a main method or a native method).",
                    file, at.start(), at.end());
        }
        if (target instanceof ExecutableElement method && isMainSignature(method, newName)) {
            return Outcome.refuse(RefusalCodes.ENTRY_POINT,
                    "Renaming to 'main' would add a program entry point.", file, at.start(), at.end());
        }
        boolean isMember = target.getKind() == ElementKind.FIELD || target.getKind() == ElementKind.METHOD;
        if (isMember && (NAMES_LOOKED_UP_BY_THE_RUNTIME.contains(oldName) || NAMES_LOOKED_UP_BY_THE_RUNTIME.contains(newName))) {
            return Outcome.refuse(RefusalCodes.NAME_USED_BY_RUNTIME,
                    "The Java runtime looks up members named '"
                            + (NAMES_LOOKED_UP_BY_THE_RUNTIME.contains(oldName) ? oldName : newName)
                            + "' by name (serialization or finalization).",
                    file, at.start(), at.end());
        }
        if (target instanceof TypeElement type && owner.getKind() == ElementKind.PACKAGE
                && type.getModifiers().contains(Modifier.PUBLIC)) {
            return Outcome.refuse(RefusalCodes.WOULD_RENAME_FILE,
                    "'" + oldName + "' is a public top-level type, so its file would have to be renamed too, which is not supported yet.",
                    file, at.start(), at.end());
        }
        return null;
    }

    private boolean isRecordComponentOrItsParts(Element target) {
        if (target.getKind() == ElementKind.RECORD_COMPONENT) {
            return true;
        }
        Element owner = target.getEnclosingElement();
        if (target.getKind() == ElementKind.PARAMETER && owner.getKind() == ElementKind.CONSTRUCTOR) {
            owner = owner.getEnclosingElement();
        }
        if (owner == null || owner.getKind() != ElementKind.RECORD) {
            return false;
        }
        for (var component : ((TypeElement) owner).getRecordComponents()) {
            if (component.getSimpleName().contentEquals(target.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMainMethod(ExecutableElement method) {
        return isMainSignature(method, method.getSimpleName().toString());
    }

    private static boolean isMainSignature(ExecutableElement method, String name) {
        if (!name.equals("main") || method.getParameters().size() > 1) {
            return false;
        }
        if (method.getParameters().isEmpty()) {
            return true;
        }
        TypeMirror type = method.getParameters().getFirst().asType();
        return type instanceof ArrayType array && array.getComponentType().getKind() == TypeKind.DECLARED
                && array.getComponentType().toString().equals("java.lang.String");
    }

    /**
     * {@code import static p.T.name;} imports every static member of T called name. Its name is edited only when
     * every such member is renamed; otherwise the import would have to be split, which is not supported yet.
     */
    private Outcome editStaticImports(String oldName, Set<Element> targets, Map<String, TextEdit> edits, boolean naive) {
        NameLocator locator = new NameLocator(compilation);
        for (var entry : compilation.unitsByFile.entrySet()) {
            CompilationUnitTree unit = entry.getValue();
            for (ImportTree importTree : unit.getImports()) {
                if (!importTree.isStatic() || !(importTree.getQualifiedIdentifier() instanceof MemberSelectTree select)
                        || !select.getIdentifier().contentEquals(oldName)) {
                    continue;
                }
                TreePath qualifier = new TreePath(new TreePath(new TreePath(new TreePath(unit), importTree), select), select.getExpression());
                if (!(compilation.trees.getElement(qualifier) instanceof TypeElement type)) {
                    continue;
                }
                List<Element> imported = new ArrayList<>();
                for (Element member : compilation.elements.getAllMembers(type)) {
                    if (member.getModifiers().contains(Modifier.STATIC) && member.getSimpleName().contentEquals(oldName)) {
                        imported.add(member);
                    }
                }
                if (imported.stream().noneMatch(targets::contains)) {
                    continue;
                }
                Token token = locator.nameToken(unit, select, oldName);
                if (!naive && (!targets.containsAll(imported) || token == null)) {
                    return Outcome.refuse(RefusalCodes.STATIC_IMPORT_SHARED,
                            "This static import also imports other members named '" + oldName + "', which are not renamed.",
                            entry.getKey(), (int) compilation.start(unit, importTree), (int) compilation.end(unit, importTree),
                            "Split the import into one per member, or import the type and qualify the uses.");
                }
                if (token != null) {
                    edits.put(entry.getKey() + ":" + token.start(), new TextEdit(entry.getKey(), token.start(), token.end(), newName));
                }
            }
        }
        return null;
    }

    private List<Warning> stringsEqualToName(String oldName) {
        List<Warning> warnings = new ArrayList<>();
        for (var entry : compilation.unitsByFile.entrySet()) {
            CompilationUnitTree unit = entry.getValue();
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitLiteral(LiteralTree literal, Void unused) {
                    if (oldName.equals(literal.getValue())) {
                        int start = (int) compilation.start(unit, literal);
                        warnings.add(new Warning("string-matches-name",
                                "This string equals the old name '" + oldName + "' and is not updated; check whether it is used to look the symbol up by name.",
                                entry.getKey(), start, (int) compilation.end(unit, literal)));
                    }
                    return null;
                }
            }.scan(new TreePath(unit), null);
        }
        return warnings;
    }
}
