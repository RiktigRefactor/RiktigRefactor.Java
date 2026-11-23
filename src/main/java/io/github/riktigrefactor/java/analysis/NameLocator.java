package io.github.riktigrefactor.java.analysis;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import java.util.List;
import java.util.Set;

/** Finds the token that spells the name of a declaration or reference tree. */
public final class NameLocator {
    private static final Set<String> TYPE_DECLARATION_KEYWORDS = Set.of("class", "interface", "enum", "record");

    private final Compilation compilation;

    public NameLocator(Compilation compilation) {
        this.compilation = compilation;
    }

    /** The simple name a tree spells, or null when it spells none (anonymous class, constructor reference, ...). */
    public static String spelledName(Tree tree, String enclosingClassName) {
        String name = switch (tree) {
            case IdentifierTree identifier -> identifier.getName().toString();
            case MemberSelectTree select -> select.getIdentifier().toString();
            case MemberReferenceTree reference -> reference.getName().toString();
            case VariableTree variable -> variable.getName().toString();
            case MethodTree method -> method.getName().contentEquals("<init>")
                    ? enclosingClassName
                    : method.getName().toString();
            case ClassTree type -> type.getSimpleName().toString();
            case TypeParameterTree parameter -> parameter.getName().toString();
            default -> null;
        };
        if (name == null || name.isEmpty() || name.equals("<init>") || name.equals("this") || name.equals("super")) {
            return null;
        }
        return name;
    }

    /** The token spelling {@code name} within {@code tree}, or null if the tree is synthetic or the name isn't found. */
    public Token nameToken(CompilationUnitTree unit, Tree tree, String name) {
        long start = compilation.start(unit, tree);
        long end = compilation.end(unit, tree);
        if (start < 0 || end < 0 || name == null) {
            return null;
        }
        List<Token> tokens = compilation.tokensByFile.get(compilation.fileOfUnit.get(unit));
        return switch (tree) {
            case IdentifierTree identifier -> tokenStartingAt(tokens, start, name);
            case MemberSelectTree select -> tokenEndingAt(tokens, end, name);
            case MemberReferenceTree reference -> tokenEndingAt(tokens, end, name);
            case VariableTree variable -> firstIdentifier(tokens, after(unit, start, variable.getModifiers(), variable.getType()), end, name);
            case MethodTree method -> firstIdentifier(
                    tokens, after(unit, start, method.getModifiers(), method.getReturnType(), lastOf(method.getTypeParameters())), end, name);
            case ClassTree type -> nameAfterKeyword(tokens, after(unit, start, type.getModifiers()), end, name);
            case TypeParameterTree parameter -> firstIdentifier(tokens, start, end, name);
            default -> null;
        };
    }

    private static Tree lastOf(List<? extends Tree> trees) {
        return trees.isEmpty() ? null : trees.getLast();
    }

    private long after(CompilationUnitTree unit, long start, Tree... precedingParts) {
        long result = start;
        for (Tree part : precedingParts) {
            if (part != null) {
                result = Math.max(result, compilation.end(unit, part));
            }
        }
        return result;
    }

    private static int firstIndexAtOrAfter(List<Token> tokens, long offset) {
        int low = 0;
        int high = tokens.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (tokens.get(middle).start() < offset) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static Token tokenStartingAt(List<Token> tokens, long start, String name) {
        int index = firstIndexAtOrAfter(tokens, start);
        if (index < tokens.size() && tokens.get(index).start() == start && tokens.get(index).isIdentifier(name)) {
            return tokens.get(index);
        }
        return null;
    }

    private static Token tokenEndingAt(List<Token> tokens, long end, String name) {
        int index = firstIndexAtOrAfter(tokens, end) - 1;
        if (index >= 0 && tokens.get(index).end() == end && tokens.get(index).isIdentifier(name)) {
            return tokens.get(index);
        }
        return null;
    }

    private static Token firstIdentifier(List<Token> tokens, long from, long end, String name) {
        for (int i = firstIndexAtOrAfter(tokens, from); i < tokens.size() && tokens.get(i).start() < end; i++) {
            if (tokens.get(i).isIdentifier(name)) {
                return tokens.get(i);
            }
        }
        return null;
    }

    private static Token nameAfterKeyword(List<Token> tokens, long from, long end, String name) {
        for (int i = firstIndexAtOrAfter(tokens, from); i + 1 < tokens.size() && tokens.get(i).start() < end; i++) {
            Token token = tokens.get(i);
            if (token.kind() == Token.Kind.IDENTIFIER && TYPE_DECLARATION_KEYWORDS.contains(token.text())) {
                return tokens.get(i + 1).isIdentifier(name) ? tokens.get(i + 1) : null;
            }
        }
        return null;
    }
}
