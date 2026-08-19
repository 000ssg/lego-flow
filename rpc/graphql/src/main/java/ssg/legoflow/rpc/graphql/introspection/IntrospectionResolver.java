package ssg.legoflow.rpc.graphql.introspection;

import ssg.legoflow.rpc.graphql.schema.*;
import java.util.*;
/**
 * Provides resolvers for the GraphQL introspection system.
 *
 * <p>Adds __schema, __type, and __typename fields to the query root type,
 * and provides data fetchers for all introspection types.
 *
 * @since 0.1.0
 */
public final class IntrospectionResolver {

    private IntrospectionResolver() {}

    /**
     * Creates introspection field definitions for the query root type.
     *
     * @param schema the schema
     * @return the __schema and __type field definitions
     */
    public static List<FieldDefinition> createIntrospectionFields(GraphQLSchema schema) {
        var schemaField = FieldDefinition.of("__schema", IntrospectionTypes.SCHEMA_TYPE);
        schemaField.dataFetcher(env -> buildSchemaMap(env.getSchema()));

        var typeField = FieldDefinition.of("__type", IntrospectionTypes.TYPE_TYPE,
                List.of(ArgumentDefinition.of("name", NonNullType.of(ScalarType.STRING))));
        typeField.dataFetcher(env -> {
            String name = env.getArgument("name");
            var type = env.getSchema().getType(name);
            return type != null ? buildTypeMap(type, env.getSchema()) : null;
        });

        return List.of(schemaField, typeField);
    }

    /**
     * Builds a map representation of the schema for introspection.
     *
     * @param schema the schema
     * @return the schema map
     */
    public static Map<String, Object> buildSchemaMap(GraphQLSchema schema) {
        var map = new LinkedHashMap<String, Object>();

        // types
        var types = new ArrayList<Map<String, Object>>();
        for (var entry : schema.typeMap().entrySet()) {
            if (!entry.getKey().startsWith("__")) {
                types.add(buildTypeMap(entry.getValue(), schema, new HashSet<>()));
            }
        }
        map.put("types", types);

        map.put("queryType", buildTypeMap(schema.queryType(), schema, new HashSet<>()));
        map.put("mutationType", schema.mutationType() != null
                ? buildTypeMap(schema.mutationType(), schema, new HashSet<>()) : null);
        map.put("subscriptionType", schema.subscriptionType() != null
                ? buildTypeMap(schema.subscriptionType(), schema, new HashSet<>()) : null);

        var directives = new ArrayList<Map<String, Object>>();
        for (var dir : schema.directives().values()) {
            directives.add(buildDirectiveMap(dir));
        }
        map.put("directives", directives);

        return map;
    }

    /**
     * Builds a map representation of a type for introspection.
     *
     * @param type   the type
     * @param schema the schema
     * @return the type map
     */
    public static Map<String, Object> buildTypeMap(GraphQLType type, GraphQLSchema schema) {
        return buildTypeMap(type, schema, new HashSet<>());
    }

    private static Map<String, Object> buildTypeMap(GraphQLType type, GraphQLSchema schema, Set<String> visited) {
        var map = new LinkedHashMap<String, Object>();

        // Cycle detection for named types
        String typeName = switch (type) {
            case ScalarType s -> s.name();
            case ObjectType o -> o.name();
            case InterfaceType i -> i.name();
            case UnionType u -> u.name();
            case EnumType e -> e.name();
            case InputObjectType io -> io.name();
            case ListType _, NonNullType _ -> null;
        };
        if (typeName != null && !visited.add(typeName)) {
            // Already visited — return a shallow reference (name + kind only)
            map.put("name", typeName);
            map.put("kind", switch (type) {
                case ObjectType _ -> "OBJECT";
                case InterfaceType _ -> "INTERFACE";
                case UnionType _ -> "UNION";
                case EnumType _ -> "ENUM";
                case InputObjectType _ -> "INPUT_OBJECT";
                case ScalarType _ -> "SCALAR";
                default -> "OBJECT";
            });
            return map;
        }

        switch (type) {
            case ScalarType s -> {
                map.put("kind", "SCALAR");
                map.put("name", s.name());
                map.put("description", s.description());
            }
            case ObjectType o -> {
                map.put("kind", "OBJECT");
                map.put("name", o.name());
                map.put("description", o.description());
                var fields = new ArrayList<Map<String, Object>>();
                for (var field : o.fields().values()) {
                    fields.add(buildFieldMap(field, schema, visited));
                }
                map.put("fields", fields);
                map.put("interfaces", o.interfaces().stream()
                        .map(i -> buildTypeMap(i, schema, visited)).toList());
            }
            case InterfaceType i -> {
                map.put("kind", "INTERFACE");
                map.put("name", i.name());
                map.put("description", i.description());
                var fields = new ArrayList<Map<String, Object>>();
                for (var field : i.fields().values()) {
                    fields.add(buildFieldMap(field, schema, visited));
                }
                map.put("fields", fields);
                map.put("possibleTypes", i.implementations().stream()
                        .map(o -> buildTypeMap(o, schema, visited)).toList());
            }
            case UnionType u -> {
                map.put("kind", "UNION");
                map.put("name", u.name());
                map.put("description", u.description());
                map.put("possibleTypes", u.memberTypes().stream()
                        .map(o -> buildTypeMap(o, schema, visited)).toList());
            }
            case EnumType e -> {
                map.put("kind", "ENUM");
                map.put("name", e.name());
                map.put("description", e.description());
                map.put("enumValues", e.values().stream().map(ev -> {
                    var evMap = new LinkedHashMap<String, Object>();
                    evMap.put("name", ev.name());
                    evMap.put("description", ev.description());
                    evMap.put("isDeprecated", ev.deprecated());
                    evMap.put("deprecationReason", ev.deprecationReason());
                    return evMap;
                }).toList());
            }
            case InputObjectType io -> {
                map.put("kind", "INPUT_OBJECT");
                map.put("name", io.name());
                map.put("description", io.description());
                map.put("inputFields", io.fields().values().stream().map(f -> {
                    var fMap = new LinkedHashMap<String, Object>();
                    fMap.put("name", f.name());
                    fMap.put("description", f.description());
                    fMap.put("type", buildTypeMap(f.type(), schema, visited));
                    fMap.put("defaultValue", f.hasDefaultValue()
                            ? String.valueOf(f.defaultValue()) : null);
                    return fMap;
                }).toList());
            }
            case ListType l -> {
                map.put("kind", "LIST");
                map.put("name", null);
                map.put("ofType", buildTypeMap(l.elementType(), schema, visited));
            }
            case NonNullType n -> {
                map.put("kind", "NON_NULL");
                map.put("name", null);
                map.put("ofType", buildTypeMap(n.wrappedType(), schema, visited));
            }
        }

        return map;
    }

    private static Map<String, Object> buildFieldMap(FieldDefinition field, GraphQLSchema schema, Set<String> visited) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", field.name());
        map.put("description", field.description());
        map.put("args", field.arguments().stream().map(a -> {
            var aMap = new LinkedHashMap<String, Object>();
            aMap.put("name", a.name());
            aMap.put("description", a.description());
            aMap.put("type", buildTypeMap(a.type(), schema, visited));
            aMap.put("defaultValue", a.hasDefaultValue() ? String.valueOf(a.defaultValue()) : null);
            return aMap;
        }).toList());
        map.put("type", buildTypeMap(field.type(), schema, visited));
        map.put("isDeprecated", field.isDeprecated());
        map.put("deprecationReason", field.deprecationReason());
        return map;
    }

    private static Map<String, Object> buildDirectiveMap(Directive directive) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", directive.name());
        map.put("description", directive.description());
        map.put("locations", directive.locations().stream()
                .map(l -> l.name()).toList());
        map.put("args", directive.arguments().stream().map(a -> {
            var aMap = new LinkedHashMap<String, Object>();
            aMap.put("name", a.name());
            aMap.put("description", a.description());
            aMap.put("type", a.type().toString());
            aMap.put("defaultValue", a.hasDefaultValue() ? String.valueOf(a.defaultValue()) : null);
            return aMap;
        }).toList());
        return map;
    }
}
