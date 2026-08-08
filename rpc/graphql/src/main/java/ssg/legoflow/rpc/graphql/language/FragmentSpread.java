package ssg.legoflow.rpc.graphql.language;

import ssg.legoflow.rpc.graphql.schema.Directive;

import java.util.List;

/**
 * Represents a fragment spread (...FragmentName) in a selection set.
 *
 * @param name       the fragment name
 * @param directives the directives applied to this spread
 * @since 0.1.0
 */
public record FragmentSpread(String name,
                             List<Directive.DirectiveUsage> directives) implements Selection {

    @Override
    public String toString() {
        return "..." + name;
    }
}
