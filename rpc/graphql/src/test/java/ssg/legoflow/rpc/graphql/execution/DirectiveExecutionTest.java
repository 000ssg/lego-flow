package ssg.legoflow.rpc.graphql.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.graphql.schema.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class DirectiveExecutionTest {

    private ExecutionEngine engine;

    @BeforeEach
    void setUp() {
        var helloField = FieldDefinition.of("hello", ScalarType.STRING);
        helloField.dataFetcher(env -> "world");
        var nameField = FieldDefinition.of("name", ScalarType.STRING);
        nameField.dataFetcher(env -> "Alice");
        var ageField = FieldDefinition.of("age", ScalarType.INT);
        ageField.dataFetcher(env -> 30);

        var queryType = ObjectType.of("Query", List.of(helloField, nameField, ageField));
        var schema = GraphQLSchema.newSchema().query(queryType).build();
        engine = new ExecutionEngine(schema);
    }

    @Test
    void testSkipDirectiveTrue() {
        var result = engine.execute("{ hello @skip(if: true) name }", null, null, null);
        var data = (Map<String, Object>) result.getData();
        assertThat(data).doesNotContainKey("hello");
        assertThat(data).containsKey("name");
    }

    @Test
    void testSkipDirectiveFalse() {
        var result = engine.execute("{ hello @skip(if: false) name }", null, null, null);
        var data = (Map<String, Object>) result.getData();
        assertThat(data).containsKey("hello");
        assertThat(data).containsKey("name");
    }

    @Test
    void testIncludeDirectiveTrue() {
        var result = engine.execute("{ hello @include(if: true) name }", null, null, null);
        var data = (Map<String, Object>) result.getData();
        assertThat(data).containsKey("hello");
    }

    @Test
    void testIncludeDirectiveFalse() {
        var result = engine.execute("{ hello @include(if: false) name }", null, null, null);
        var data = (Map<String, Object>) result.getData();
        assertThat(data).doesNotContainKey("hello");
    }

    @Test
    void testSkipWithVariable() {
        var result = engine.execute(
                "query ($skip: Boolean!) { hello @skip(if: $skip) }",
                null, Map.of("skip", true), null);
        var data = (Map<String, Object>) result.getData();
        assertThat(data).doesNotContainKey("hello");
    }

    @Test
    void testIncludeWithVariable() {
        var result = engine.execute(
                "query ($show: Boolean!) { hello @include(if: $show) }",
                null, Map.of("show", false), null);
        var data = (Map<String, Object>) result.getData();
        assertThat(data).doesNotContainKey("hello");
    }
}
