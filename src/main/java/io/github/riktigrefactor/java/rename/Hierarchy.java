package io.github.riktigrefactor.java.rename;

import io.github.riktigrefactor.java.analysis.Compilation;
import io.github.riktigrefactor.java.analysis.SourceTypes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** The methods that must be renamed together because they override one another, directly or through a common subtype. */
final class Hierarchy {
    record Result(Set<ExecutableElement> methods, String externalOverridden) {}

    private final Compilation compilation;
    private final SourceTypes sourceTypes;

    Hierarchy(Compilation compilation) {
        this.compilation = compilation;
        this.sourceTypes = new SourceTypes(compilation);
    }

    Result of(ExecutableElement target) {
        List<ExecutableElement> candidates = new ArrayList<>();
        for (TypeElement type : sourceTypes.all) {
            candidates.addAll(SourceTypes.methodsNamed(type, target.getSimpleName()));
        }
        Set<ExecutableElement> group = new LinkedHashSet<>(List.of(target));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (ExecutableElement candidate : candidates) {
                if (!group.contains(candidate) && group.stream().anyMatch(member -> related(candidate, member))) {
                    group.add(candidate);
                    grew = true;
                }
            }
        }
        for (ExecutableElement member : group) {
            for (TypeElement type : sourceTypes.all) {
                if (!sourceTypes.isMemberOf(member, type)) {
                    continue;
                }
                for (TypeElement supertype : sourceTypes.supertypes(type)) {
                    if (sourceTypes.isInSource(supertype)) {
                        continue;
                    }
                    for (ExecutableElement external : SourceTypes.methodsNamed(supertype, target.getSimpleName())) {
                        if (compilation.elements.overrides(member, external, type)) {
                            return new Result(group, supertype.getQualifiedName() + "." + external);
                        }
                    }
                }
            }
        }
        return new Result(group, null);
    }

    private boolean related(ExecutableElement a, ExecutableElement b) {
        for (TypeElement type : sourceTypes.all) {
            if (sourceTypes.isMemberOf(a, type) && sourceTypes.isMemberOf(b, type)
                    && (compilation.elements.overrides(a, b, type) || compilation.elements.overrides(b, a, type))) {
                return true;
            }
        }
        return false;
    }
}
