package ssg.legoflow.rpc.graphql.language;

import ssg.legoflow.rpc.graphql.schema.Directive;

import java.util.List;
import java.util.Map;

/**
 * Represents a field selection in a GraphQL query.
 *
 * <p>Fields can have aliases, arguments, directives, and sub-selections.
 *
 * @param alias        the alias, or null
 * @param name         the field name
 * @param arguments    the arguments as name-value pairs
 * @param directives   the directives applied to this field
 * @param selectionSet the sub-selection set, or null for leaf fields
 * @since 1.0.0
 */
public record Field(String alias, String name,
                    Map<String, Value> arguments,
                    List<Directive.DirectiveUsage> directives,
                    SelectionSet selectionSet) implements Selection {

    /**
     * Returns the response name (alias if present, otherwise field name).
     *
     * @return the response key name
     */
    public String responseName() {
        return alias != null ? alias : name;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        if (alias != null) sb.append(alias).append(": ");
        sb.append(name);
        if (arguments != null && !arguments.isEmpty()) {
            sb.append('(');
            var it = arguments.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                sb.append(entry.getKey()).append(": ").append(entry.getValue());
                if (it.hasNext()) sb.append(", ");
            }
            sb.append(')');
        }
        if (selectionSet != null) sb.append(" { ... }");
        return sb.toString();
    }
}
