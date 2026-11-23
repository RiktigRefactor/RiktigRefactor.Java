package io.github.riktigrefactor.java.extractiife;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.github.riktigrefactor.java.analysis.Compilation;
import java.util.ArrayList;
import java.util.List;

/** What a text range selects: exactly one expression, or a run of whole statements of one statement list. */
record Selection(int start, int end, TreePath expression, List<TreePath> statements) {

    boolean isExpression() {
        return expression != null;
    }

    List<TreePath> roots() {
        return isExpression() ? List.of(expression) : statements;
    }

    boolean contains(long position) {
        return start <= position && position < end;
    }

    static Selection find(Compilation compilation, String file, int rawStart, int rawEnd) {
        String text = compilation.sources.get(file);
        CompilationUnitTree unit = compilation.unitsByFile.get(file);
        if (text == null || unit == null || rawStart < 0 || rawEnd > text.length() || rawStart > rawEnd) {
            return null;
        }
        int start = rawStart;
        int end = rawEnd;
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            return null;
        }
        int selectionStart = start;
        int selectionEnd = end;
        TreePath[] expression = new TreePath[1];
        List<List<TreePath>> statementRuns = new ArrayList<>();
        new TreePathScanner<Void, Void>() {
            @Override
            public Void scan(Tree tree, Void unused) {
                if (tree == null) {
                    return null;
                }
                long treeStart = compilation.start(unit, tree);
                long treeEnd = compilation.end(unit, tree);
                if (treeStart > selectionStart || (treeEnd >= 0 && treeEnd < selectionEnd)) {
                    return null;
                }
                TreePath path = new TreePath(getCurrentPath(), tree);
                if (tree instanceof ExpressionTree && treeStart == selectionStart && treeEnd == selectionEnd) {
                    expression[0] = path;
                }
                if (tree instanceof StatementTree && !(tree instanceof VariableTree) && !(tree instanceof ClassTree)
                        && treeStart == selectionStart && treeEnd == selectionEnd) {
                    statementRuns.add(List.of(path));
                }
                List<? extends StatementTree> list = switch (tree) {
                    case BlockTree block -> block.getStatements();
                    case CaseTree caseTree -> caseTree.getStatements();
                    default -> null;
                };
                if (list != null) {
                    addRun(path, list);
                }
                return super.scan(tree, unused);
            }

            private void addRun(TreePath parent, List<? extends StatementTree> list) {
                int first = -1;
                for (int i = 0; i < list.size(); i++) {
                    if (compilation.start(unit, list.get(i)) == selectionStart) {
                        first = i;
                    }
                    if (first >= 0 && compilation.end(unit, list.get(i)) == selectionEnd) {
                        List<TreePath> run = new ArrayList<>();
                        for (int j = first; j <= i; j++) {
                            run.add(new TreePath(parent, list.get(j)));
                        }
                        statementRuns.add(run);
                        return;
                    }
                }
            }
        }.scan(new TreePath(unit), null);
        if (expression[0] != null) {
            return new Selection(start, end, expression[0], null);
        }
        if (!statementRuns.isEmpty()) {
            return new Selection(start, end, null, statementRuns.getLast());
        }
        return null;
    }
}
