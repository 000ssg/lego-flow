package ssg.legoflow.rpc.graphql.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.graphql.language.GraphQLParser;
import ssg.legoflow.rpc.graphql.schema.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class QueryValidatorTest {

    private GraphQLSchema schema;
    private QueryValidator validator;

    @BeforeEach
    void setUp() {
        var userType = ObjectType.of("User", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID)),
                FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("age", ScalarType.INT),
                FieldDefinition.of("email", ScalarType.STRING)
        ));

        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("user", userType,
                        List.of(ArgumentDefinition.of("id", NonNullType.of(ScalarType.ID)))),
                FieldDefinition.of("users", ListType.of(userType)),
                FieldDefinition.of("hello", ScalarType.STRING)
        ));

        schema = GraphQLSchema.newSchema().query(queryType).build();
        validator = new QueryValidator(schema);
    }

    @Test
    void testValidQuery() {
        var doc = GraphQLParser.parse("{ user(id: \"1\") { name age } }");
        var errors = validator.validate(doc);
        assertThat(errors).isEmpty();
    }

    @Test
    void testFieldNotFound() {
        var doc = GraphQLParser.parse("{ user(id: \"1\") { name nonExistentField } }");
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("nonExistentField"));
    }

    @Test
    void testUnknownArgument() {
        var doc = GraphQLParser.parse("{ user(id: \"1\", unknownArg: true) { name } }");
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("unknownArg"));
    }

    @Test
    void testScalarFieldMustNotHaveSelections() {
        var doc = GraphQLParser.parse("{ hello { name } }");
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("must not have a selection set"));
    }

    @Test
    void testCompositeFieldMustHaveSelections() {
        var doc = GraphQLParser.parse("{ user(id: \"1\") }");
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("must have a selection set"));
    }

    @Test
    void testDuplicateOperationNames() {
        var doc = GraphQLParser.parse("""
                query GetUser { hello }
                query GetUser { hello }
                """);
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("Duplicate operation name"));
    }

    @Test
    void testDuplicateFragmentNames() {
        var doc = GraphQLParser.parse("""
                query { user(id: "1") { ...F } }
                fragment F on User { name }
                fragment F on User { age }
                """);
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("Duplicate fragment name"));
    }

    @Test
    void testUnknownFragment() {
        var doc = GraphQLParser.parse("{ user(id: \"1\") { ...UnknownFragment } }");
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("Unknown fragment"));
    }

    @Test
    void testUnusedFragment() {
        var doc = GraphQLParser.parse("""
                query { hello }
                fragment Unused on User { name }
                """);
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("never used"));
    }

    @Test
    void testFragmentInvalidTypeCondition() {
        var doc = GraphQLParser.parse("""
                query { hello }
                fragment F on NonExistent { name }
                """);
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("unknown type condition"));
    }

    @Test
    void testUndefinedVariable() {
        var doc = GraphQLParser.parse("query { user(id: $undefinedVar) { name } }");
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("not defined"));
    }

    @Test
    void testDefinedVariableUsed() {
        var doc = GraphQLParser.parse("query ($id: ID!) { user(id: $id) { name } }");
        var errors = validator.validate(doc);
        assertThat(errors).isEmpty();
    }

    @Test
    void testDuplicateVariableNames() {
        var doc = GraphQLParser.parse("query ($id: ID!, $id: ID!) { user(id: $id) { name } }");
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("Duplicate variable"));
    }

    @Test
    void testMutationNotSupported() {
        var doc = GraphQLParser.parse("mutation { addUser { id } }");
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("not supported"));
    }

    @Test
    void testTypenameAllowed() {
        var doc = GraphQLParser.parse("{ user(id: \"1\") { __typename name } }");
        var errors = validator.validate(doc);
        assertThat(errors).isEmpty();
    }

    @Test
    void testFragmentTypeConditionMustBeComposite() {
        var doc = GraphQLParser.parse("""
                query { hello }
                fragment F on String { length }
                """);
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("must be object, interface, or union"));
    }

    @Test
    void testValidFragmentUsage() {
        var doc = GraphQLParser.parse("""
                query { user(id: "1") { ...UserFields } }
                fragment UserFields on User { name age }
                """);
        var errors = validator.validate(doc);
        assertThat(errors).isEmpty();
    }

    @Test
    void testInlineFragmentValidation() {
        var doc = GraphQLParser.parse("{ user(id: \"1\") { ... on UnknownType { name } } }");
        var errors = validator.validate(doc);
        assertThat(errors).anyMatch(e -> e.message().contains("Unknown type"));
    }
}
