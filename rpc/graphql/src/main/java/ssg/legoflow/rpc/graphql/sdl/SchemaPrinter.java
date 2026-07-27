package ssg.legoflow.rpc.graphql.sdl;

import ssg.legoflow.rpc.graphql.schema.*;

import java.util.stream.Collectors;

/**
 * Prints a GraphQL schema as Schema Definition Language (SDL).
 *
 * @since 1.0.0
 */
public final class SchemaPrinter {

    private SchemaPrinter() {}

    /**
     * Prints the complete schema as SDL.
     *
     * @param schema the schema to print
     * @return the SDL string
     */
    public static String print(GraphQLSchema schema) {
        var sb = new StringBuilder();

        // Schema definition
        sb.append("schema {\n");
        sb.append("  query: ").append(schema.queryType().name()).append("\n");
        if (schema.mutationType() != null) {
            sb.append("  mutation: ").append(schema.mutationType().name()).append("\n");
        }
        if (schema.subscriptionType() != null) {
            sb.append("  subscription: ").append(schema.subscriptionType().name()).append("\n");
        }
        sb.append("}\n");

        // Type definitions
        for (var entry : schema.typeMap().entrySet()) {
            var type = entry.getValue();
            if (type instanceof ScalarType st && st.isBuiltIn()) continue;
            if (entry.getKey().startsWith("__")) continue;

            sb.append("\n");
            printType(sb, type);
        }

        // Custom directives
        for (var dir : schema.directives().values()) {
            if ("skip".equals(dir.name()) || "include".equals(dir.name())
                    || "deprecated".equals(dir.name())) continue;
            sb.append("\n");
            printDirective(sb, dir);
        }

        return sb.toString();
    }

    /**
     * Prints a single type as SDL.
     *
     * @param type the type to print
     * @return the SDL string
     */
    public static String printType(GraphQLType type) {
        var sb = new StringBuilder();
        printType(sb, type);
        return sb.toString();
    }

    private static void printType(StringBuilder sb, GraphQLType type) {
        switch (type) {
            case ScalarType s -> {
                if (s.description() != null) sb.append("\"").append(s.description()).append("\"\n");
                sb.append("scalar ").append(s.name()).append("\n");
            }
            case ObjectType o -> {
                if (o.description() != null) sb.append("\"").append(o.description()).append("\"\n");
                sb.append("type ").append(o.name());
                if (!o.interfaces().isEmpty()) {
                    sb.append(" implements ");
                    sb.append(o.interfaces().stream()
                            .map(InterfaceType::name)
                            .collect(Collectors.joining(" & ")));
                }
                sb.append(" {\n");
                for (var field : o.fields().values()) {
                    printField(sb, field);
                }
                sb.append("}\n");
            }
            case InterfaceType i -> {
                if (i.description() != null) sb.append("\"").append(i.description()).append("\"\n");
                sb.append("interface ").append(i.name()).append(" {\n");
                for (var field : i.fields().values()) {
                    printField(sb, field);
                }
                sb.append("}\n");
            }
            case UnionType u -> {
                if (u.description() != null) sb.append("\"").append(u.description()).append("\"\n");
                sb.append("union ").append(u.name()).append(" = ");
                sb.append(u.memberTypes().stream()
                        .map(ObjectType::name)
                        .collect(Collectors.joining(" | ")));
                sb.append("\n");
            }
            case EnumType e -> {
                if (e.description() != null) sb.append("\"").append(e.description()).append("\"\n");
                sb.append("enum ").append(e.name()).append(" {\n");
                for (var v : e.values()) {
                    sb.append("  ").append(v.name());
                    if (v.deprecated()) {
                        sb.append(" @deprecated");
                        if (v.deprecationReason() != null) {
                            sb.append("(reason: \"").append(v.deprecationReason()).append("\")");
                        }
                    }
                    sb.append("\n");
                }
                sb.append("}\n");
            }
            case InputObjectType io -> {
                if (io.description() != null) sb.append("\"").append(io.description()).append("\"\n");
                sb.append("input ").append(io.name()).append(" {\n");
                for (var field : io.fields().values()) {
                    sb.append("  ").append(field.name()).append(": ").append(printTypeRef(field.type()));
                    if (field.hasDefaultValue()) {
                        sb.append(" = ").append(printValue(field.defaultValue()));
                    }
                    sb.append("\n");
                }
                sb.append("}\n");
            }
            case ListType l -> sb.append("[").append(printTypeRef(l.elementType())).append("]");
            case NonNullType n -> sb.append(printTypeRef(n.wrappedType())).append("!");
        }
    }

    private static void printField(StringBuilder sb, FieldDefinition field) {
        sb.append("  ");
        if (field.description() != null) {
            sb.append("\"").append(field.description()).append("\"\n  ");
        }
        sb.append(field.name());
        if (!field.arguments().isEmpty()) {
            sb.append("(");
            var args = field.arguments();
            for (int i = 0; i < args.size(); i++) {
                var arg = args.get(i);
                if (i > 0) sb.append(", ");
                sb.append(arg.name()).append(": ").append(printTypeRef(arg.type()));
                if (arg.hasDefaultValue()) {
                    sb.append(" = ").append(printValue(arg.defaultValue()));
                }
            }
            sb.append(")");
        }
        sb.append(": ").append(printTypeRef(field.type()));
        if (field.isDeprecated()) {
            sb.append(" @deprecated");
            if (field.deprecationReason() != null) {
                sb.append("(reason: \"").append(field.deprecationReason()).append("\")");
            }
        }
        sb.append("\n");
    }

    /**
     * Prints a type reference as SDL syntax.
     *
     * @param type the type
     * @return the SDL string
     */
    public static String printTypeRef(GraphQLType type) {
        return switch (type) {
            case ListType l -> "[" + printTypeRef(l.elementType()) + "]";
            case NonNullType n -> printTypeRef(n.wrappedType()) + "!";
            default -> type.name();
        };
    }

    private static String printValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + s + "\"";
        if (value instanceof Boolean || value instanceof Number) return String.valueOf(value);
        return String.valueOf(value);
    }

    private static void printDirective(StringBuilder sb, Directive dir) {
        sb.append("directive @").append(dir.name());
        if (!dir.arguments().isEmpty()) {
            sb.append("(");
            var args = dir.arguments();
            for (int i = 0; i < args.size(); i++) {
                var arg = args.get(i);
                if (i > 0) sb.append(", ");
                sb.append(arg.name()).append(": ").append(printTypeRef(arg.type()));
                if (arg.hasDefaultValue()) {
                    sb.append(" = ").append(printValue(arg.defaultValue()));
                }
            }
            sb.append(")");
        }
        sb.append(" on ");
        sb.append(dir.locations().stream()
                .map(Directive.Location::name)
                .collect(Collectors.joining(" | ")));
        sb.append("\n");
    }
}
