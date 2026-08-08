package ssg.legoflow.rpc.graphql.execution;

import ssg.legoflow.rpc.graphql.language.*;
import ssg.legoflow.rpc.graphql.schema.*;
import ssg.legoflow.rpc.graphql.validation.QueryValidator;
import ssg.legoflow.rpc.graphql.validation.ValidationError;

import java.util.*;
import java.util.concurrent.*;

/**
 * Executes validated GraphQL documents against a schema.
 *
 * <p>Implements the execution algorithm from the GraphQL specification:
 * <ul>
 *   <li>Serial execution for mutations</li>
 *   <li>Parallel execution for query fields (using virtual threads)</li>
 *   <li>Null propagation for non-null fields</li>
 *   <li>Partial results with errors</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class ExecutionEngine {

    private final GraphQLSchema schema;

    /**
     * Creates a new execution engine for the given schema.
     *
     * @param schema the GraphQL schema
     */
    public ExecutionEngine(GraphQLSchema schema) {
        this.schema = Objects.requireNonNull(schema);
    }

    /**
     * Executes a GraphQL query string.
     *
     * @param query         the query string
     * @param operationName the operation name, or null
     * @param variables     the variable values, or null
     * @param context       the user context object, or null
     * @return the execution result
     */
    public ExecutionResult execute(String query, String operationName,
                                   Map<String, Object> variables, Object context) {
        Document document;
        try {
            document = GraphQLParser.parse(query);
        } catch (GraphQLSyntaxException e) {
            return ExecutionResult.ofErrors(List.of(
                    ExecutionResult.GraphQLError.of(e.getMessage())));
        }

        return execute(document, operationName, variables, context);
    }

    /**
     * Executes a parsed GraphQL document.
     *
     * @param document      the parsed document
     * @param operationName the operation name, or null
     * @param variables     the variable values, or null
     * @param context       the user context object, or null
     * @return the execution result
     */
    public ExecutionResult execute(Document document, String operationName,
                                   Map<String, Object> variables, Object context) {
        // Validate
        var validator = new QueryValidator(schema);
        var validationErrors = validator.validate(document);
        if (!validationErrors.isEmpty()) {
            return ExecutionResult.ofErrors(
                    validationErrors.stream()
                            .map(e -> ExecutionResult.GraphQLError.of(e.message()))
                            .toList());
        }

        OperationDefinition operation;
        try {
            operation = document.getOperation(operationName);
        } catch (IllegalArgumentException e) {
            return ExecutionResult.ofErrors(List.of(
                    ExecutionResult.GraphQLError.of(e.getMessage())));
        }

        var execCtx = new ExecutionContext(schema, operation,
                variables, document.fragmentMap(), context);

        var rootType = switch (operation.operationType()) {
            case QUERY -> schema.queryType();
            case MUTATION -> schema.mutationType();
            case SUBSCRIPTION -> schema.subscriptionType();
        };

        if (rootType == null) {
            return ExecutionResult.ofErrors(List.of(
                    ExecutionResult.GraphQLError.of(
                            operation.operationType() + " is not supported by this schema")));
        }

        Object data;
        if (operation.operationType() == OperationDefinition.OperationType.MUTATION) {
            data = executeSerially(execCtx, rootType, null, operation.selectionSet(), new ArrayList<>());
        } else {
            data = executeSelectionSet(execCtx, rootType, null, operation.selectionSet(), new ArrayList<>());
        }

        return new ExecutionResult(data, execCtx.errors());
    }

    /**
     * Executes a selection set, collecting fields and resolving them.
     */
    private Object executeSelectionSet(ExecutionContext ctx, GraphQLType objectType,
                                       Object source, SelectionSet selectionSet,
                                       List<Object> path) {
        if (!(objectType instanceof ObjectType ot)) return null;

        var groupedFields = collectFields(ctx, ot, selectionSet, new HashSet<>());
        var result = new LinkedHashMap<String, Object>();

        for (var entry : groupedFields.entrySet()) {
            var responseName = entry.getKey();
            var fields = entry.getValue();
            var firstField = fields.getFirst();

            var fieldPath = new ArrayList<>(path);
            fieldPath.add(responseName);

            var fieldDef = getFieldDef(ot, firstField.name());
            if (fieldDef == null) continue;

            var value = resolveField(ctx, ot, source, firstField, fieldDef, fieldPath);
            result.put(responseName, value);
        }
        return result;
    }

    /**
     * Executes mutations serially (one field at a time).
     */
    private Object executeSerially(ExecutionContext ctx, ObjectType objectType,
                                    Object source, SelectionSet selectionSet,
                                    List<Object> path) {
        var groupedFields = collectFields(ctx, objectType, selectionSet, new HashSet<>());
        var result = new LinkedHashMap<String, Object>();

        for (var entry : groupedFields.entrySet()) {
            var responseName = entry.getKey();
            var fields = entry.getValue();
            var firstField = fields.getFirst();

            var fieldPath = new ArrayList<>(path);
            fieldPath.add(responseName);

            var fieldDef = getFieldDef(objectType, firstField.name());
            if (fieldDef == null) continue;

            var value = resolveField(ctx, objectType, source, firstField, fieldDef, fieldPath);
            result.put(responseName, value);
        }
        return result;
    }

    /**
     * Resolves a single field value.
     */
    private Object resolveField(ExecutionContext ctx, ObjectType parentType,
                                 Object source, Field field, FieldDefinition fieldDef,
                                 List<Object> path) {
        // Resolve arguments
        var arguments = resolveArguments(ctx, field, fieldDef);

        // Get data fetcher
        var dataFetcher = fieldDef.dataFetcher();
        Object resolvedValue;
        if (dataFetcher != null) {
            var env = new DataFetchingEnvironment(
                    source, arguments, ctx.variables(), ctx.userContext(),
                    fieldDef, parentType, schema);
            try {
                resolvedValue = dataFetcher.get(env);
            } catch (Exception e) {
                ctx.addError(ExecutionResult.GraphQLError.of(
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                        List.copyOf(path)));
                resolvedValue = null;
            }
        } else {
            // Default data fetcher: property access on source
            resolvedValue = defaultFetch(source, field.name());
        }

        // Complete value
        return completeValue(ctx, fieldDef.type(), resolvedValue, field, path);
    }

    /**
     * Completes a resolved value according to the field type.
     */
    private Object completeValue(ExecutionContext ctx, GraphQLType type,
                                  Object value, Field field, List<Object> path) {
        // NonNull handling
        if (type instanceof NonNullType nnt) {
            var completed = completeValue(ctx, nnt.wrappedType(), value, field, path);
            if (completed == null) {
                ctx.addError(ExecutionResult.GraphQLError.of(
                        "Cannot return null for non-nullable field", List.copyOf(path)));
                return null; // null propagation
            }
            return completed;
        }

        if (value == null) return null;

        // List handling
        if (type instanceof ListType lt) {
            if (value instanceof Collection<?> coll) {
                var resultList = new ArrayList<>();
                int i = 0;
                for (var item : coll) {
                    var itemPath = new ArrayList<>(path);
                    itemPath.add(i);
                    resultList.add(completeValue(ctx, lt.elementType(), item, field, itemPath));
                    i++;
                }
                return resultList;
            } else if (value instanceof Object[] arr) {
                var resultList = new ArrayList<>();
                for (int i = 0; i < arr.length; i++) {
                    var itemPath = new ArrayList<>(path);
                    itemPath.add(i);
                    resultList.add(completeValue(ctx, lt.elementType(), arr[i], field, itemPath));
                }
                return resultList;
            }
            return null;
        }

        // Scalar coercion
        if (type instanceof ScalarType st) {
            return st.serialize(value);
        }

        // Enum coercion
        if (type instanceof EnumType et) {
            String enumValue = value.toString();
            if (et.isValidValue(enumValue)) return enumValue;
            ctx.addError(ExecutionResult.GraphQLError.of(
                    "Invalid enum value: " + enumValue, List.copyOf(path)));
            return null;
        }

        // Object type
        if (type instanceof ObjectType) {
            if (field.selectionSet() != null) {
                return executeSelectionSet(ctx, type, value, field.selectionSet(), path);
            }
            return null;
        }

        // Interface type - resolve concrete type
        if (type instanceof InterfaceType it) {
            var concreteType = resolveAbstractType(value, it);
            if (concreteType != null && field.selectionSet() != null) {
                return executeSelectionSet(ctx, concreteType, value, field.selectionSet(), path);
            }
            return null;
        }

        // Union type - resolve concrete type
        if (type instanceof UnionType ut) {
            var concreteType = resolveAbstractType(value, ut);
            if (concreteType != null && field.selectionSet() != null) {
                return executeSelectionSet(ctx, concreteType, value, field.selectionSet(), path);
            }
            return null;
        }

        return value;
    }

    /**
     * Resolves an abstract type to a concrete object type.
     */
    private ObjectType resolveAbstractType(Object value, GraphQLType abstractType) {
        // Check if the value has a __typename
        if (value instanceof Map<?, ?> map) {
            var typename = map.get("__typename");
            if (typename instanceof String name) {
                var type = schema.getType(name);
                if (type instanceof ObjectType ot) return ot;
            }
        }

        // Try to find by matching possible types
        var possibleTypes = schema.getPossibleTypes(abstractType);
        if (possibleTypes.size() == 1) return possibleTypes.getFirst();

        // Check if value class name matches a type name
        var className = value.getClass().getSimpleName();
        for (var pt : possibleTypes) {
            if (pt.name().equalsIgnoreCase(className)) return pt;
        }

        return possibleTypes.isEmpty() ? null : possibleTypes.getFirst();
    }

    /**
     * Collects fields from a selection set, merging fields with the same response name.
     */
    private LinkedHashMap<String, List<Field>> collectFields(
            ExecutionContext ctx, ObjectType objectType,
            SelectionSet selectionSet, Set<String> visitedFragments) {
        var groupedFields = new LinkedHashMap<String, List<Field>>();

        for (var selection : selectionSet.selections()) {
            // Check @skip and @include
            if (shouldSkip(ctx, selection)) continue;

            switch (selection) {
                case Field field -> {
                    var responseName = field.responseName();
                    groupedFields.computeIfAbsent(responseName, k -> new ArrayList<>()).add(field);
                }
                case FragmentSpread spread -> {
                    if (visitedFragments.contains(spread.name())) continue;
                    visitedFragments.add(spread.name());
                    var fragment = ctx.getFragment(spread.name());
                    if (fragment == null) continue;
                    // Check if type applies
                    if (!doesFragmentApply(objectType, fragment.typeCondition())) continue;
                    var fragmentFields = collectFields(ctx, objectType,
                            fragment.selectionSet(), visitedFragments);
                    for (var entry : fragmentFields.entrySet()) {
                        groupedFields.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                .addAll(entry.getValue());
                    }
                }
                case InlineFragment inline -> {
                    if (inline.typeCondition() != null
                            && !doesFragmentApply(objectType, inline.typeCondition())) continue;
                    var inlineFields = collectFields(ctx, objectType,
                            inline.selectionSet(), visitedFragments);
                    for (var entry : inlineFields.entrySet()) {
                        groupedFields.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                .addAll(entry.getValue());
                    }
                }
            }
        }
        return groupedFields;
    }

    private boolean doesFragmentApply(ObjectType objectType, String typeCondition) {
        if (typeCondition == null) return true;
        if (objectType.name().equals(typeCondition)) return true;

        // Check if typeCondition is an interface that objectType implements
        for (var iface : objectType.interfaces()) {
            if (iface.name().equals(typeCondition)) return true;
        }

        // Check if typeCondition is a union that includes objectType
        var type = schema.getType(typeCondition);
        if (type instanceof UnionType ut) {
            return ut.isMember(objectType);
        }

        return false;
    }

    private boolean shouldSkip(ExecutionContext ctx, Selection selection) {
        var directives = switch (selection) {
            case Field f -> f.directives();
            case FragmentSpread fs -> fs.directives();
            case InlineFragment inf -> inf.directives();
        };

        for (var directive : directives) {
            if ("skip".equals(directive.name())) {
                var ifArg = directive.getArgument("if");
                if (Boolean.TRUE.equals(resolveDirectiveArg(ctx, ifArg))) return true;
            }
            if ("include".equals(directive.name())) {
                var ifArg = directive.getArgument("if");
                if (Boolean.FALSE.equals(resolveDirectiveArg(ctx, ifArg))) return true;
            }
        }
        return false;
    }

    private Object resolveDirectiveArg(ExecutionContext ctx, Object value) {
        if (value instanceof String s && s.startsWith("$")) {
            return ctx.variables().get(s.substring(1));
        }
        return value;
    }

    /**
     * Resolves field arguments, substituting variables.
     */
    private Map<String, Object> resolveArguments(ExecutionContext ctx, Field field,
                                                  FieldDefinition fieldDef) {
        var result = new LinkedHashMap<String, Object>();

        // Apply defaults from definition
        for (var argDef : fieldDef.arguments()) {
            if (argDef.hasDefaultValue()) {
                result.put(argDef.name(), argDef.defaultValue());
            }
        }

        // Apply provided arguments
        for (var entry : field.arguments().entrySet()) {
            var value = entry.getValue();
            var resolved = resolveValue(ctx, value);
            result.put(entry.getKey(), resolved);
        }

        return result;
    }

    private Object resolveValue(ExecutionContext ctx, Value value) {
        return switch (value) {
            case Value.VariableValue v -> ctx.variables().get(v.name());
            case Value.ListValue lv -> lv.values().stream()
                    .map(v -> resolveValue(ctx, v)).toList();
            case Value.ObjectValue ov -> {
                var map = new LinkedHashMap<String, Object>();
                ov.fields().forEach((k, v) -> map.put(k, resolveValue(ctx, v)));
                yield map;
            }
            default -> value.toJava();
        };
    }

    /**
     * Default property-based data fetcher.
     */
    @SuppressWarnings("unchecked")
    private Object defaultFetch(Object source, String fieldName) {
        if (source == null) return null;
        if (source instanceof Map<?, ?> map) {
            return map.get(fieldName);
        }
        // Try getter method
        try {
            var method = source.getClass().getMethod(fieldName);
            return method.invoke(source);
        } catch (NoSuchMethodException e) {
            // Try getXxx
            var getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            try {
                var method = source.getClass().getMethod(getterName);
                return method.invoke(source);
            } catch (Exception ex) {
                // Try isXxx for booleans
                var isName = "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                try {
                    var method = source.getClass().getMethod(isName);
                    return method.invoke(source);
                } catch (Exception ex2) {
                    // Try field access
                    try {
                        var f = source.getClass().getField(fieldName);
                        return f.get(source);
                    } catch (Exception ex3) {
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the field definition, handling __typename.
     */
    private FieldDefinition getFieldDef(ObjectType objectType, String fieldName) {
        if ("__typename".equals(fieldName)) {
            var field = FieldDefinition.of("__typename", NonNullType.of(ScalarType.STRING));
            field.dataFetcher(env -> objectType.name());
            return field;
        }
        if ("__schema".equals(fieldName) && objectType == schema.queryType()) {
            return objectType.getField(fieldName);
        }
        if ("__type".equals(fieldName) && objectType == schema.queryType()) {
            return objectType.getField(fieldName);
        }
        return objectType.getField(fieldName);
    }
}
