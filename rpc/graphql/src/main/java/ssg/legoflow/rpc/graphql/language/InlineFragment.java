package ssg.legoflow.rpc.graphql.language;

import ssg.legoflow.rpc.graphql.schema.Directive;

import java.util.List;

/**
 * Represents an inline fragment in a selection set.
 *
 * <p>Inline fragments are used to conditionally include fields based on
 * the runtime type of an object, or to apply directives to a group of fields.
 *
 * @param typeCondition the type condition name, or null if omitted
 * @param directives    the directives on this inline fragment
 * @param selectionSet  the selection set
 * @since 0.1.0
 */
public record InlineFragment(String typeCondition,
                             List<Directive.DirectiveUsage> directives,
                             SelectionSet selectionSet) implements Selection {

    @Override
    public String toString() {
        return typeCondition != null
                ? "... on " + typeCondition + " { ... }"
                : "... { ... }";
    }
}
