package ssg.legoflow.rpc.graphql.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.graphql.schema.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class ExecutionEngineTest {

    private ExecutionEngine engine;

    @BeforeEach
    void setUp() {
        var addressType = ObjectType.of("Address", List.of(
                FieldDefinition.of("city", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("country", ScalarType.STRING)
        ));

        var userType = ObjectType.of("User", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID)),
                FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("age", ScalarType.INT),
                FieldDefinition.of("email", ScalarType.STRING),
                FieldDefinition.of("address", addressType)
        ));

        var helloField = FieldDefinition.of("hello", ScalarType.STRING);
        helloField.dataFetcher(env -> "world");

        var userField = FieldDefinition.of("user", userType,
                List.of(ArgumentDefinition.of("id", NonNullType.of(ScalarType.ID))));
        userField.dataFetcher(env -> {
            String id = env.getArgument("id");
            if ("1".equals(id)) {
                return Map.of(
                        "id", "1", "name", "Alice", "age", 30, "email", "alice@example.com",
                        "address", Map.of("city", "Wonderland", "country", "Fiction"));
            }
            return null;
        });

        var usersField = FieldDefinition.of("users", ListType.of(userType));
        usersField.dataFetcher(env -> List.of(
                Map.of("id", "1", "name", "Alice", "age", 30),
                Map.of("id", "2", "name", "Bob", "age", 25)));

        var greetField = FieldDefinition.of("greet", ScalarType.STRING,
                List.of(ArgumentDefinition.of("name", ScalarType.STRING, "World")));
        greetField.dataFetcher(env -> {
            String name = env.getArgument("name");
            return "Hello, " + name + "!";
        });

        var queryType = ObjectType.of("Query", List.of(
                helloField, userField, usersField, greetField));

        var schema = GraphQLSchema.newSchema().query(queryType).build();
        engine = new ExecutionEngine(schema);
    }

    @Test
    void testSimpleFieldResolution() {
        var result = engine.execute("{ hello }", null, null, null);
        assertThat(result.hasErrors()).isFalse();
        var data = (Map<String, Object>) result.getData();
        assertThat(data.get("hello")).isEqualTo("world");
    }

    @Test
    void testFieldWithArgument() {
        var result = engine.execute("{ user(id: \"1\") { name age } }", null, null, null);
        assertThat(result.hasErrors()).isFalse();
        var data = (Map<String, Object>) result.getData();
        var user = (Map<String, Object>) data.get("user");
        assertThat(user.get("name")).isEqualTo("Alice");
        assertThat(user.get("age")).isEqualTo(30);
    }

    @Test
    void testNestedObjectResolution() {
        var result = engine.execute("{ user(id: \"1\") { name address { city country } } }",
                null, null, null);
        assertThat(result.hasErrors()).isFalse();
        var data = (Map<String, Object>) result.getData();
        var user = (Map<String, Object>) data.get("user");
        var address = (Map<String, Object>) user.get("address");
        assertThat(address.get("city")).isEqualTo("Wonderland");
    }

    @Test
    void testListResolution() {
        var result = engine.execute("{ users { name } }", null, null, null);
        assertThat(result.hasErrors()).isFalse();
        var data = (Map<String, Object>) result.getData();
        var users = (List<Map<String, Object>>) data.get("users");
        assertThat(users).hasSize(2);
        assertThat(users.get(0).get("name")).isEqualTo("Alice");
        assertThat(users.get(1).get("name")).isEqualTo("Bob");
    }

    @Test
    void testFieldAlias() {
        var result = engine.execute("{ greeting: hello }", null, null, null);
        var data = (Map<String, Object>) result.getData();
        assertThat(data).containsKey("greeting");
        assertThat(data.get("greeting")).isEqualTo("world");
    }

    @Test
    void testMultipleAliases() {
        var result = engine.execute("""
                {
                  a: user(id: "1") { name }
                  b: user(id: "999") { name }
                }
                """, null, null, null);
        var data = (Map<String, Object>) result.getData();
        assertThat(data).containsKeys("a", "b");
        assertThat(data.get("b")).isNull();
    }

    @Test
    void testDefaultArgument() {
        var result = engine.execute("{ greet }", null, null, null);
        var data = (Map<String, Object>) result.getData();
        assertThat(data.get("greet")).isEqualTo("Hello, World!");
    }

    @Test
    void testExplicitArgument() {
        var result = engine.execute("{ greet(name: \"Alice\") }", null, null, null);
        var data = (Map<String, Object>) result.getData();
        assertThat(data.get("greet")).isEqualTo("Hello, Alice!");
    }

    @Test
    void testVariableSubstitution() {
        var result = engine.execute(
                "query ($id: ID!) { user(id: $id) { name } }",
                null, Map.of("id", "1"), null);
        var data = (Map<String, Object>) result.getData();
        var user = (Map<String, Object>) data.get("user");
        assertThat(user.get("name")).isEqualTo("Alice");
    }

    @Test
    void testNullForNonExistentRecord() {
        var result = engine.execute("{ user(id: \"999\") { name } }", null, null, null);
        var data = (Map<String, Object>) result.getData();
        assertThat(data.get("user")).isNull();
    }

    @Test
    void testTypename() {
        var result = engine.execute("{ user(id: \"1\") { __typename name } }", null, null, null);
        var data = (Map<String, Object>) result.getData();
        var user = (Map<String, Object>) data.get("user");
        assertThat(user.get("__typename")).isEqualTo("User");
    }

    @Test
    void testSyntaxError() {
        var result = engine.execute("{ invalid query {{{", null, null, null);
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void testValidationError() {
        var result = engine.execute("{ nonExistent }", null, null, null);
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void testResolverException() {
        var errorField = FieldDefinition.of("error", ScalarType.STRING);
        errorField.dataFetcher(env -> { throw new RuntimeException("Test error"); });
        var queryType = ObjectType.of("Query", List.of(errorField));
        var schema = GraphQLSchema.newSchema().query(queryType).build();
        var eng = new ExecutionEngine(schema);

        var result = eng.execute("{ error }", null, null, null);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().getFirst().message()).isEqualTo("Test error");
    }

    @Test
    void testPartialResult() {
        var nameField = FieldDefinition.of("name", ScalarType.STRING);
        nameField.dataFetcher(env -> "Alice");
        var errorField = FieldDefinition.of("broken", ScalarType.STRING);
        errorField.dataFetcher(env -> { throw new RuntimeException("broken"); });
        var queryType = ObjectType.of("Query", List.of(nameField, errorField));
        var schema = GraphQLSchema.newSchema().query(queryType).build();
        var eng = new ExecutionEngine(schema);

        var result = eng.execute("{ name broken }", null, null, null);
        assertThat(result.hasErrors()).isTrue();
        var data = (Map<String, Object>) result.getData();
        assertThat(data.get("name")).isEqualTo("Alice");
    }

    @Test
    void testExecutionResultToMap() {
        var result = new ExecutionResult(Map.of("hello", "world"), List.of());
        var map = result.toMap();
        assertThat(map).containsKey("data");
        assertThat(map).doesNotContainKey("errors");
    }

    @Test
    void testExecutionResultWithErrors() {
        var result = new ExecutionResult(null,
                List.of(ExecutionResult.GraphQLError.of("bad")));
        var map = result.toMap();
        assertThat(map).containsKey("errors");
        assertThat(map).containsKey("data");
        assertThat(map.get("data")).isNull();
    }

    @Test
    void testGraphQLErrorToMap() {
        var error = new ExecutionResult.GraphQLError(
                "Test error", List.of("field"), null, null);
        var map = error.toMap();
        assertThat(map.get("message")).isEqualTo("Test error");
        assertThat(map.get("path")).isEqualTo(List.of("field"));
    }
}
