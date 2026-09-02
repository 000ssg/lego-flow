package ssg.legoflow.rpc.graphql.language;

import ssg.legoflow.rpc.graphql.schema.Directive;
import java.util.List;
/**
 * Represents a named fragment definition.
 *
 * <p>Fragment definitions allow reusing selection sets across operations.
 * They have a name, a type condition, and a selection set.
 *
 * @param name          the fragment name
 * @param typeCondition the type condition (type name)
 * @param directives    the directives on this fragment
 * @param selectionSet  the fragment's selection set
 * @since 0.1.0
 */
public record FragmentDefinition(String name, String typeCondition,
                                 List<Directive.DirectiveUsage> directives,
                                 SelectionSet selectionSet) {

    @Override
    public String toString() {
        return "fragment " + name + " on " + typeCondition + " { ... }";
    }
}
