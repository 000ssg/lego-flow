package ssg.legoflow.rpc.graphql.language;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphQLParserTest {

    @Test
    void testSimpleQuery() {
        var doc = GraphQLParser.parse("{ hero { name } }");
        assertThat(doc.operations()).hasSize(1);
        assertThat(doc.operations().getFirst().operationType())
                .isEqualTo(OperationDefinition.OperationType.QUERY);
    }

    @Test
    void testNamedQuery() {
        var doc = GraphQLParser.parse("query HeroQuery { hero { name } }");
        assertThat(doc.operations().getFirst().name()).isEqualTo("HeroQuery");
    }

    @Test
    void testMutation() {
        var doc = GraphQLParser.parse("mutation AddTodo { addTodo { id } }");
        assertThat(doc.operations().getFirst().operationType())
                .isEqualTo(OperationDefinition.OperationType.MUTATION);
    }

    @Test
    void testSubscription() {
        var doc = GraphQLParser.parse("subscription OnMessage { newMessage { text } }");
        assertThat(doc.operations().getFirst().operationType())
                .isEqualTo(OperationDefinition.OperationType.SUBSCRIPTION);
    }

    @Test
    void testFieldAlias() {
        var doc = GraphQLParser.parse("{ myHero: hero { name } }");
        var field = doc.operations().getFirst().selectionSet().fields().getFirst();
        assertThat(field.alias()).isEqualTo("myHero");
        assertThat(field.name()).isEqualTo("hero");
        assertThat(field.responseName()).isEqualTo("myHero");
    }

    @Test
    void testFieldArguments() {
        var doc = GraphQLParser.parse("{ human(id: \"1000\") { name } }");
        var field = doc.operations().getFirst().selectionSet().fields().getFirst();
        assertThat(field.arguments()).containsKey("id");
        assertThat(field.arguments().get("id")).isInstanceOf(Value.StringValue.class);
    }

    @Test
    void testIntArgument() {
        var doc = GraphQLParser.parse("{ todos(limit: 10) { id } }");
        var field = doc.operations().getFirst().selectionSet().fields().getFirst();
        var limit = (Value.IntValue) field.arguments().get("limit");
        assertThat(limit.value()).isEqualTo(10);
    }

    @Test
    void testFloatArgument() {
        var doc = GraphQLParser.parse("{ radius(value: 3.14) }");
        var field = doc.operations().getFirst().selectionSet().fields().getFirst();
        var value = (Value.FloatValue) field.arguments().get("value");
        assertThat(value.value()).isEqualTo(3.14);
    }

    @Test
    void testBooleanArgument() {
        var doc = GraphQLParser.parse("{ items(active: true) { id } }");
        var field = doc.operations().getFirst().selectionSet().fields().getFirst();
        var active = (Value.BooleanValue) field.arguments().get("active");
        assertThat(active.value()).isTrue();
    }

    @Test
    void testEnumArgument() {
        var doc = GraphQLParser.parse("{ hero(episode: EMPIRE) { name } }");
        var field = doc.operations().getFirst().selectionSet().fields().getFirst();
        var episode = (Value.EnumValue) field.arguments().get("episode");
        assertThat(episode.value()).isEqualTo("EMPIRE");
    }

    @Test
    void testNullArgument() {
        var doc = GraphQLParser.parse("{ user(id: null) { name } }");
        var field = doc.operations().getFirst().selectionSet().fields().getFirst();
        assertThat(field.arguments().get("id")).isInstanceOf(Value.NullValue.class);
    }

    @Test
    void testListArgument() {
        var doc = GraphQLParser.parse("{ users(ids: [1, 2, 3]) { name } }");
        var field = doc.operations().getFirst().selectionSet().fields().getFirst();
        var ids = (Value.ListValue) field.arguments().get("ids");
        assertThat(ids.values()).hasSize(3);
    }

    @Test
    void testObjectArgument() {
        var doc = GraphQLParser.parse("{ addUser(input: {name: \"Alice\", age: 30}) { id } }");
        var field = doc.operations().getFirst().selectionSet().fields().getFirst();
        var input = (Value.ObjectValue) field.arguments().get("input");
        assertThat(input.fields()).containsKeys("name", "age");
    }

    @Test
    void testVariableDefinitions() {
        var doc = GraphQLParser.parse("query ($id: ID!, $limit: Int = 10) { user(id: $id) { name } }");
        var op = doc.operations().getFirst();
        assertThat(op.variableDefinitions()).hasSize(2);
        assertThat(op.variableDefinitions().get(0).name()).isEqualTo("id");
        assertThat(op.variableDefinitions().get(1).name()).isEqualTo("limit");
        assertThat(op.variableDefinitions().get(1).defaultValue()).isNotNull();
    }

    @Test
    void testVariableReference() {
        var doc = GraphQLParser.parse("query ($id: ID!) { user(id: $id) { name } }");
        var field = doc.operations().getFirst().selectionSet().fields().getFirst();
        var id = (Value.VariableValue) field.arguments().get("id");
        assertThat(id.name()).isEqualTo("id");
    }

    @Test
    void testListTypeVariable() {
        var doc = GraphQLParser.parse("query ($ids: [ID!]!) { users(ids: $ids) { name } }");
        var varDef = doc.operations().getFirst().variableDefinitions().getFirst();
        assertThat(varDef.typeName()).isInstanceOf(VariableDefinition.TypeReference.NonNullTypeRef.class);
    }

    @Test
    void testNamedFragment() {
        var doc = GraphQLParser.parse("""
                query { hero { ...HeroFields } }
                fragment HeroFields on Character { name }
                """);
        assertThat(doc.fragments()).hasSize(1);
        assertThat(doc.fragments().getFirst().name()).isEqualTo("HeroFields");
        assertThat(doc.fragments().getFirst().typeCondition()).isEqualTo("Character");
    }

    @Test
    void testFragmentSpread() {
        var doc = GraphQLParser.parse("query { hero { ...HeroFields } } fragment HeroFields on Character { name }");
        var selections = doc.operations().getFirst().selectionSet().selections();
        var heroField = (Field) selections.getFirst();
        var spread = heroField.selectionSet().fragmentSpreads().getFirst();
        assertThat(spread.name()).isEqualTo("HeroFields");
    }

    @Test
    void testInlineFragment() {
        var doc = GraphQLParser.parse("{ hero { name ... on Human { homePlanet } } }");
        var heroField = doc.operations().getFirst().selectionSet().fields().getFirst();
        var inlines = heroField.selectionSet().inlineFragments();
        assertThat(inlines).hasSize(1);
        assertThat(inlines.getFirst().typeCondition()).isEqualTo("Human");
    }

    @Test
    void testInlineFragmentWithoutTypeCondition() {
        var doc = GraphQLParser.parse("{ hero { name ... { id } } }");
        var heroField = doc.operations().getFirst().selectionSet().fields().getFirst();
        var inlines = heroField.selectionSet().inlineFragments();
        assertThat(inlines).hasSize(1);
        assertThat(inlines.getFirst().typeCondition()).isNull();
    }

    @Test
    void testDirectiveOnField() {
        var doc = GraphQLParser.parse("{ hero { name @skip(if: true) } }");
        var heroField = doc.operations().getFirst().selectionSet().fields().getFirst();
        var nameField = heroField.selectionSet().fields().getFirst();
        assertThat(nameField.directives()).hasSize(1);
        assertThat(nameField.directives().getFirst().name()).isEqualTo("skip");
    }

    @Test
    void testDirectiveOnFragmentSpread() {
        var doc = GraphQLParser.parse("""
                { hero { ...F @include(if: true) } }
                fragment F on Character { name }
                """);
        var heroField = doc.operations().getFirst().selectionSet().fields().getFirst();
        var spread = heroField.selectionSet().fragmentSpreads().getFirst();
        assertThat(spread.directives()).hasSize(1);
    }

    @Test
    void testMultipleOperations() {
        var doc = GraphQLParser.parse("""
                query GetHero { hero { name } }
                query GetDroid { droid(id: "2001") { name } }
                """);
        assertThat(doc.operations()).hasSize(2);
    }

    @Test
    void testNestedSelections() {
        var doc = GraphQLParser.parse("{ user { address { city { name } } } }");
        var userField = doc.operations().getFirst().selectionSet().fields().getFirst();
        var addressField = userField.selectionSet().fields().getFirst();
        var cityField = addressField.selectionSet().fields().getFirst();
        var nameField = cityField.selectionSet().fields().getFirst();
        assertThat(nameField.name()).isEqualTo("name");
    }

    @Test
    void testEmptyDocumentThrows() {
        assertThatThrownBy(() -> GraphQLParser.parse(""))
                .isInstanceOf(GraphQLSyntaxException.class);
    }

    @Test
    void testSyntaxErrorThrows() {
        assertThatThrownBy(() -> GraphQLParser.parse("query {"))
                .isInstanceOf(GraphQLSyntaxException.class);
    }

    @Test
    void testGetOperationByName() {
        var doc = GraphQLParser.parse("""
                query A { hero { name } }
                query B { droid { name } }
                """);
        assertThat(doc.getOperation("A").name()).isEqualTo("A");
        assertThat(doc.getOperation("B").name()).isEqualTo("B");
    }

    @Test
    void testGetSingleOperation() {
        var doc = GraphQLParser.parse("{ hero { name } }");
        assertThat(doc.getOperation(null)).isNotNull();
    }

    @Test
    void testMultipleOpsRequireName() {
        var doc = GraphQLParser.parse("""
                query A { hero { name } }
                query B { droid { name } }
                """);
        assertThatThrownBy(() -> doc.getOperation(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testComplexQuery() {
        var query = """
                query HeroNameAndFriends($episode: Episode, $withFriends: Boolean!) {
                  hero(episode: $episode) {
                    name
                    friends @include(if: $withFriends) {
                      name
                    }
                  }
                }
                """;
        var doc = GraphQLParser.parse(query);
        assertThat(doc.operations()).hasSize(1);
        var op = doc.operations().getFirst();
        assertThat(op.variableDefinitions()).hasSize(2);
    }
}
