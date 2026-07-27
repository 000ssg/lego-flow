package ssg.legoflow.rpc.graphql.schema;

import java.util.*;

/**
 * Represents a complete GraphQL schema.
 *
 * <p>A schema defines the query, optional mutation, and optional subscription
 * root types, along with all type definitions and directives.
 *
 * @since 1.0.0
 */
public final class GraphQLSchema {

    private final ObjectType queryType;
    private final ObjectType mutationType;
    private final ObjectType subscriptionType;
    private final Map<String, GraphQLType> typeMap;
    private final Map<String, Directive> directives;
    private final String description;

    private GraphQLSchema(Builder builder) {
        this.queryType = Objects.requireNonNull(builder.queryType, "Query type is required");
        this.mutationType = builder.mutationType;
        this.subscriptionType = builder.subscriptionType;
        this.description = builder.description;

        var types = new LinkedHashMap<String, GraphQLType>();
        // Add built-in scalars
        types.put("Int", ScalarType.INT);
        types.put("Float", ScalarType.FLOAT);
        types.put("String", ScalarType.STRING);
        types.put("Boolean", ScalarType.BOOLEAN);
        types.put("ID", ScalarType.ID);
        // Add additional types
        for (var type : builder.additionalTypes) {
            if (type.name() != null) {
                types.put(type.name(), type);
            }
        }
        // Add root types and their referenced types
        collectTypes(queryType, types);
        if (mutationType != null) collectTypes(mutationType, types);
        if (subscriptionType != null) collectTypes(subscriptionType, types);

        // Register implementations on interface types
        for (var type : types.values()) {
            if (type instanceof ObjectType obj) {
                for (var iface : obj.interfaces()) {
                    iface.addImplementation(obj);
                }
            }
        }

        this.typeMap = Collections.unmodifiableMap(types);

        var dirs = new LinkedHashMap<String, Directive>();
        dirs.put("skip", Directive.SKIP);
        dirs.put("include", Directive.INCLUDE);
        dirs.put("deprecated", Directive.DEPRECATED);
        for (var dir : builder.directives) {
            dirs.put(dir.name(), dir);
        }
        this.directives = Collections.unmodifiableMap(dirs);
    }

    /**
     * Returns the query root type.
     *
     * @return the query type
     */
    public ObjectType queryType() { return queryType; }

    /**
     * Returns the mutation root type, if any.
     *
     * @return the mutation type, or null
     */
    public ObjectType mutationType() { return mutationType; }

    /**
     * Returns the subscription root type, if any.
     *
     * @return the subscription type, or null
     */
    public ObjectType subscriptionType() { return subscriptionType; }

    /**
     * Returns the schema description.
     *
     * @return the description, or null
     */
    public String description() { return description; }

    /**
     * Returns all type definitions in this schema.
     *
     * @return unmodifiable map of type name to type
     */
    public Map<String, GraphQLType> typeMap() { return typeMap; }

    /**
     * Returns the type with the given name.
     *
     * @param name the type name
     * @return the type, or null
     */
    public GraphQLType getType(String name) { return typeMap.get(name); }

    /**
     * Returns all directive definitions in this schema.
     *
     * @return unmodifiable map of directive name to definition
     */
    public Map<String, Directive> directives() { return directives; }

    /**
     * Returns the directive with the given name.
     *
     * @param name the directive name
     * @return the directive, or null
     */
    public Directive getDirective(String name) { return directives.get(name); }

    /**
     * Returns all types that implement the given abstract type.
     *
     * @param abstractType an interface or union type
     * @return the possible concrete object types
     */
    public List<ObjectType> getPossibleTypes(GraphQLType abstractType) {
        return switch (abstractType) {
            case InterfaceType i -> i.implementations();
            case UnionType u -> u.memberTypes();
            default -> List.of();
        };
    }

    /**
     * Returns whether a given object type is a possible type of an abstract type.
     *
     * @param abstractType the abstract type
     * @param objectType   the object type
     * @return true if the object type is a possible type
     */
    public boolean isPossibleType(GraphQLType abstractType, ObjectType objectType) {
        return getPossibleTypes(abstractType).stream()
                .anyMatch(t -> t.name().equals(objectType.name()));
    }

    private void collectTypes(GraphQLType type, Map<String, GraphQLType> types) {
        if (type == null) return;
        switch (type) {
            case ListType l -> collectTypes(l.elementType(), types);
            case NonNullType n -> collectTypes(n.wrappedType(), types);
            case ObjectType o -> {
                if (types.containsKey(o.name())) return;
                types.put(o.name(), o);
                for (var field : o.fields().values()) {
                    collectTypes(field.type(), types);
                    for (var arg : field.arguments()) {
                        collectTypes(arg.type(), types);
                    }
                }
                for (var iface : o.interfaces()) {
                    collectTypes(iface, types);
                }
            }
            case InterfaceType i -> {
                if (types.containsKey(i.name())) return;
                types.put(i.name(), i);
                for (var field : i.fields().values()) {
                    collectTypes(field.type(), types);
                }
            }
            case UnionType u -> {
                if (types.containsKey(u.name())) return;
                types.put(u.name(), u);
                for (var member : u.memberTypes()) {
                    collectTypes(member, types);
                }
            }
            case EnumType e -> types.putIfAbsent(e.name(), e);
            case InputObjectType io -> {
                if (types.containsKey(io.name())) return;
                types.put(io.name(), io);
                for (var field : io.fields().values()) {
                    collectTypes(field.type(), types);
                }
            }
            case ScalarType s -> types.putIfAbsent(s.name(), s);
        }
    }

    /**
     * Creates a new schema builder.
     *
     * @return a new builder
     */
    public static Builder newSchema() {
        return new Builder();
    }

    /**
     * Builder for GraphQLSchema.
     *
     * @since 1.0.0
     */
    public static final class Builder {
        private ObjectType queryType;
        private ObjectType mutationType;
        private ObjectType subscriptionType;
        private String description;
        private final List<GraphQLType> additionalTypes = new ArrayList<>();
        private final List<Directive> directives = new ArrayList<>();

        private Builder() {}

        public Builder query(ObjectType queryType) {
            this.queryType = queryType;
            return this;
        }

        public Builder mutation(ObjectType mutationType) {
            this.mutationType = mutationType;
            return this;
        }

        public Builder subscription(ObjectType subscriptionType) {
            this.subscriptionType = subscriptionType;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder additionalType(GraphQLType type) {
            this.additionalTypes.add(type);
            return this;
        }

        public Builder additionalTypes(Collection<? extends GraphQLType> types) {
            this.additionalTypes.addAll(types);
            return this;
        }

        public Builder directive(Directive directive) {
            this.directives.add(directive);
            return this;
        }

        public GraphQLSchema build() {
            return new GraphQLSchema(this);
        }
    }
}
