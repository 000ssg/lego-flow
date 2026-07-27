package ssg.legoflow.rpc.graphql.validation;

import ssg.legoflow.rpc.graphql.language.*;
import ssg.legoflow.rpc.graphql.schema.*;

import java.util.*;

/**
 * Validates a GraphQL document against a schema.
 *
 * <p>Implements key validation rules from the GraphQL specification:
 * <ul>
 *   <li>Fields must exist on the type</li>
 *   <li>Arguments must match field definitions</li>
 *   <li>Fragment type conditions must be valid</li>
 *   <li>Variables must be defined and used</li>
 *   <li>Unique operation names and fragment names</li>
 *   <li>Leaf field selections (scalars/enums must not have sub-selections)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class QueryValidator {

    private final GraphQLSchema schema;
    private final List<ValidationRule> rules;

    /**
     * Creates a validator with the default validation rules.
     *
     * @param schema the schema to validate against
     */
    public QueryValidator(GraphQLSchema schema) {
        this.schema = schema;
        this.rules = createDefaultRules();
    }

    /**
     * Creates a validator with custom rules.
     *
     * @param schema the schema
     * @param rules  the validation rules
     */
    public QueryValidator(GraphQLSchema schema, List<ValidationRule> rules) {
        this.schema = schema;
        this.rules = List.copyOf(rules);
    }

    /**
     * Validates the document against all rules.
     *
     * @param document the document to validate
     * @return the validation errors (empty if valid)
     */
    public List<ValidationError> validate(Document document) {
        var errors = new ArrayList<ValidationError>();
        for (var rule : rules) {
            errors.addAll(rule.validate(document, schema));
        }
        return errors;
    }

    private List<ValidationRule> createDefaultRules() {
        return List.of(
                this::validateUniqueOperationNames,
                this::validateUniqueFragmentNames,
                this::validateFragmentUsage,
                this::validateFields,
                this::validateVariables
        );
    }

    private List<ValidationError> validateUniqueOperationNames(Document doc, GraphQLSchema s) {
        var errors = new ArrayList<ValidationError>();
        var names = new HashSet<String>();
        for (var op : doc.operations()) {
            if (op.name() != null && !names.add(op.name())) {
                errors.add(ValidationError.of(
                        "Duplicate operation name: " + op.name(), "UniqueOperationNames"));
            }
        }
        return errors;
    }

    private List<ValidationError> validateUniqueFragmentNames(Document doc, GraphQLSchema s) {
        var errors = new ArrayList<ValidationError>();
        var names = new HashSet<String>();
        for (var frag : doc.fragments()) {
            if (!names.add(frag.name())) {
                errors.add(ValidationError.of(
                        "Duplicate fragment name: " + frag.name(), "UniqueFragmentNames"));
            }
        }
        return errors;
    }

    private List<ValidationError> validateFragmentUsage(Document doc, GraphQLSchema s) {
        var errors = new ArrayList<ValidationError>();
        var fragmentMap = doc.fragmentMap();

        // Check fragment type conditions
        for (var frag : doc.fragments()) {
            var type = s.getType(frag.typeCondition());
            if (type == null) {
                errors.add(ValidationError.of(
                        "Fragment '" + frag.name() + "' has unknown type condition: " + frag.typeCondition(),
                        "FragmentTypeCondition"));
            } else if (!(type instanceof ObjectType) && !(type instanceof InterfaceType) && !(type instanceof UnionType)) {
                errors.add(ValidationError.of(
                        "Fragment '" + frag.name() + "' type condition must be object, interface, or union",
                        "FragmentTypeCondition"));
            }
        }

        // Collect used fragments
        var usedFragments = new HashSet<String>();
        for (var op : doc.operations()) {
            collectUsedFragments(op.selectionSet(), usedFragments, fragmentMap);
        }

        // Check for unused fragments
        for (var frag : doc.fragments()) {
            if (!usedFragments.contains(frag.name())) {
                errors.add(ValidationError.of(
                        "Fragment '" + frag.name() + "' is never used", "NoUnusedFragments"));
            }
        }

        return errors;
    }

    private void collectUsedFragments(SelectionSet selectionSet, Set<String> used,
                                       Map<String, FragmentDefinition> fragmentMap) {
        if (selectionSet == null) return;
        for (var selection : selectionSet.selections()) {
            switch (selection) {
                case Field f -> collectUsedFragments(f.selectionSet(), used, fragmentMap);
                case FragmentSpread spread -> {
                    if (used.add(spread.name())) {
                        var frag = fragmentMap.get(spread.name());
                        if (frag != null) {
                            collectUsedFragments(frag.selectionSet(), used, fragmentMap);
                        }
                    }
                }
                case InlineFragment inline ->
                    collectUsedFragments(inline.selectionSet(), used, fragmentMap);
            }
        }
    }

    private List<ValidationError> validateFields(Document doc, GraphQLSchema s) {
        var errors = new ArrayList<ValidationError>();
        for (var op : doc.operations()) {
            var rootType = switch (op.operationType()) {
                case QUERY -> s.queryType();
                case MUTATION -> s.mutationType();
                case SUBSCRIPTION -> s.subscriptionType();
            };
            if (rootType == null) {
                errors.add(ValidationError.of(
                        op.operationType() + " is not supported", "RootTypeExists"));
                continue;
            }
            validateSelectionSet(op.selectionSet(), rootType, s, doc.fragmentMap(), errors);
        }
        // Also validate fragment selection sets
        for (var frag : doc.fragments()) {
            var type = s.getType(frag.typeCondition());
            if (type instanceof ObjectType ot) {
                validateSelectionSet(frag.selectionSet(), ot, s, doc.fragmentMap(), errors);
            }
        }
        return errors;
    }

    private void validateSelectionSet(SelectionSet selectionSet, ObjectType objectType,
                                       GraphQLSchema s, Map<String, FragmentDefinition> fragments,
                                       List<ValidationError> errors) {
        if (selectionSet == null) return;
        for (var selection : selectionSet.selections()) {
            switch (selection) {
                case Field field -> {
                    if ("__typename".equals(field.name())) continue;
                    if ("__schema".equals(field.name()) || "__type".equals(field.name())) continue;

                    var fieldDef = objectType.getField(field.name());
                    if (fieldDef == null) {
                        errors.add(ValidationError.of(
                                "Field '" + field.name() + "' does not exist on type '" + objectType.name() + "'",
                                "FieldsOnCorrectType"));
                        continue;
                    }

                    // Validate arguments
                    for (var argEntry : field.arguments().entrySet()) {
                        var argDef = fieldDef.getArgument(argEntry.getKey());
                        if (argDef == null) {
                            errors.add(ValidationError.of(
                                    "Unknown argument '" + argEntry.getKey() + "' on field '" + field.name() + "'",
                                    "KnownArgumentNames"));
                        }
                    }

                    // Check leaf fields don't have sub-selections
                    var unwrapped = fieldDef.type().unwrap();
                    if ((unwrapped instanceof ScalarType || unwrapped instanceof EnumType)
                            && field.selectionSet() != null) {
                        errors.add(ValidationError.of(
                                "Field '" + field.name() + "' must not have a selection set (is a leaf type)",
                                "ScalarLeafs"));
                    }

                    // Check composite fields have sub-selections
                    if ((unwrapped instanceof ObjectType || unwrapped instanceof InterfaceType
                            || unwrapped instanceof UnionType)
                            && field.selectionSet() == null) {
                        errors.add(ValidationError.of(
                                "Field '" + field.name() + "' must have a selection set (is a composite type)",
                                "ScalarLeafs"));
                    }

                    // Recurse into sub-selections
                    if (field.selectionSet() != null && unwrapped instanceof ObjectType fieldOt) {
                        validateSelectionSet(field.selectionSet(), fieldOt, s, fragments, errors);
                    }
                }
                case FragmentSpread spread -> {
                    var frag = fragments.get(spread.name());
                    if (frag == null) {
                        errors.add(ValidationError.of(
                                "Unknown fragment: " + spread.name(), "KnownFragmentNames"));
                    }
                }
                case InlineFragment inline -> {
                    if (inline.typeCondition() != null) {
                        var type = s.getType(inline.typeCondition());
                        if (type == null) {
                            errors.add(ValidationError.of(
                                    "Unknown type: " + inline.typeCondition(), "FragmentTypeCondition"));
                        } else if (type instanceof ObjectType inlineOt) {
                            validateSelectionSet(inline.selectionSet(), inlineOt, s, fragments, errors);
                        }
                    } else {
                        validateSelectionSet(inline.selectionSet(), objectType, s, fragments, errors);
                    }
                }
            }
        }
    }

    private List<ValidationError> validateVariables(Document doc, GraphQLSchema s) {
        var errors = new ArrayList<ValidationError>();
        for (var op : doc.operations()) {
            var definedVars = new HashSet<String>();
            var usedVars = new HashSet<String>();

            for (var varDef : op.variableDefinitions()) {
                if (!definedVars.add(varDef.name())) {
                    errors.add(ValidationError.of(
                            "Duplicate variable: $" + varDef.name(), "UniqueVariableNames"));
                }
            }

            collectUsedVariables(op.selectionSet(), usedVars, doc.fragmentMap());

            // Check all used variables are defined
            for (var used : usedVars) {
                if (!definedVars.contains(used)) {
                    errors.add(ValidationError.of(
                            "Variable '$" + used + "' is not defined",
                            "NoUndefinedVariables"));
                }
            }
        }
        return errors;
    }

    private void collectUsedVariables(SelectionSet selectionSet, Set<String> used,
                                       Map<String, FragmentDefinition> fragments) {
        if (selectionSet == null) return;
        for (var selection : selectionSet.selections()) {
            switch (selection) {
                case Field f -> {
                    for (var argValue : f.arguments().values()) {
                        collectVariablesInValue(argValue, used);
                    }
                    collectUsedVariables(f.selectionSet(), used, fragments);
                }
                case FragmentSpread spread -> {
                    var frag = fragments.get(spread.name());
                    if (frag != null) {
                        collectUsedVariables(frag.selectionSet(), used, fragments);
                    }
                }
                case InlineFragment inline ->
                    collectUsedVariables(inline.selectionSet(), used, fragments);
            }
        }
    }

    private void collectVariablesInValue(Value value, Set<String> used) {
        switch (value) {
            case Value.VariableValue v -> used.add(v.name());
            case Value.ListValue lv -> lv.values().forEach(v -> collectVariablesInValue(v, used));
            case Value.ObjectValue ov -> ov.fields().values().forEach(v -> collectVariablesInValue(v, used));
            default -> { /* literal values have no variables */ }
        }
    }
}
