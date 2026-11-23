package io.github.riktigrefactor.java.rename;

import io.github.riktigrefactor.java.analysis.Bindings;
import io.github.riktigrefactor.java.analysis.Compilation;
import io.github.riktigrefactor.java.analysis.SourceTypes;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Every "in type T, method m overrides method o" fact about the workspace, described so it can be compared across a rename. */
final class Overriding {
    private final Compilation compilation;
    private final Bindings bindings;

    Overriding(Compilation compilation, Bindings bindings) {
        this.compilation = compilation;
        this.bindings = bindings;
    }

    Set<String> pairs() {
        SourceTypes sourceTypes = new SourceTypes(compilation);
        Set<String> result = new TreeSet<>();
        for (TypeElement type : sourceTypes.all) {
            Set<TypeElement> supertypes = sourceTypes.supertypes(type);
            for (Element member : compilation.elements.getAllMembers(type)) {
                if (member.getKind() != ElementKind.METHOD || !sourceTypes.isInSource(member)) {
                    continue;
                }
                ExecutableElement method = (ExecutableElement) member;
                for (TypeElement supertype : supertypes) {
                    for (ExecutableElement overridden : SourceTypes.methodsNamed(supertype, method.getSimpleName())) {
                        if (overridden != method && compilation.elements.overrides(method, overridden, type)) {
                            result.add("in " + bindings.elementKey(type) + ", " + bindings.elementKey(method)
                                    + " overrides " + bindings.elementKey(overridden));
                        }
                    }
                }
            }
        }
        return result;
    }
}
