package ssg.legoflow.rpc.graphql.execution;

import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.graphql.schema.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class MutationExecutionTest {

    @Test
    void testSerialMutationExecution() {
        var counter = new AtomicInteger(0);
        var results = Collections.synchronizedList(new ArrayList<Integer>());

        var incrementField = FieldDefinition.of("increment", ScalarType.INT);
        incrementField.dataFetcher(env -> {
            int val = counter.incrementAndGet();
            results.add(val);
            return val;
        });

        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("count", ScalarType.INT)));
        var mutationType = ObjectType.of("Mutation", List.of(incrementField));
        var schema = GraphQLSchema.newSchema()
                .query(queryType)
                .mutation(mutationType)
                .build();
        var engine = new ExecutionEngine(schema);

        var result = engine.execute("""
                mutation {
                  a: increment
                  b: increment
                  c: increment
                }
                """, null, null, null);

        assertThat(result.hasErrors()).isFalse();
        var data = (Map<String, Object>) result.getData();
        assertThat(data.get("a")).isEqualTo(1);
        assertThat(data.get("b")).isEqualTo(2);
        assertThat(data.get("c")).isEqualTo(3);
        // Verify serial execution order
        assertThat(results).containsExactly(1, 2, 3);
    }

    @Test
    void testMutationWithInputObject() {
        var todos = Collections.synchronizedList(new ArrayList<Map<String, Object>>());
        var idCounter = new AtomicInteger(1);

        var todoInput = InputObjectType.of("TodoInput", List.of(
                InputObjectType.InputFieldDefinition.of("title", NonNullType.of(ScalarType.STRING)),
                InputObjectType.InputFieldDefinition.of("completed", ScalarType.BOOLEAN, false)));
        var todoType = ObjectType.of("Todo", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID)),
                FieldDefinition.of("title", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("completed", NonNullType.of(ScalarType.BOOLEAN))));

        var addTodoField = FieldDefinition.of("addTodo", todoType,
                List.of(ArgumentDefinition.of("input", NonNullType.of(todoInput))));
        addTodoField.dataFetcher(env -> {
            var input = (Map<String, Object>) env.getArgument("input");
            var todo = new LinkedHashMap<String, Object>();
            todo.put("id", idCounter.getAndIncrement());
            todo.put("title", input.get("title"));
            todo.put("completed", input.getOrDefault("completed", false));
            todos.add(todo);
            return todo;
        });

        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("x", ScalarType.INT)));
        var mutationType = ObjectType.of("Mutation", List.of(addTodoField));
        var schema = GraphQLSchema.newSchema()
                .query(queryType)
                .mutation(mutationType)
                .additionalType(todoInput)
                .build();
        var engine = new ExecutionEngine(schema);

        var result = engine.execute("""
                mutation {
                  addTodo(input: {title: "Write tests", completed: false}) {
                    id title completed
                  }
                }
                """, null, null, null);

        assertThat(result.hasErrors()).isFalse();
        var data = (Map<String, Object>) result.getData();
        var todo = (Map<String, Object>) data.get("addTodo");
        assertThat(todo.get("title")).isEqualTo("Write tests");
        assertThat(todo.get("completed")).isEqualTo(false);
    }

    @Test
    void testMutationErrorHandling() {
        var deleteField = FieldDefinition.of("delete", ScalarType.BOOLEAN,
                List.of(ArgumentDefinition.of("id", NonNullType.of(ScalarType.ID))));
        deleteField.dataFetcher(env -> {
            throw new RuntimeException("Not found");
        });

        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("x", ScalarType.INT)));
        var mutationType = ObjectType.of("Mutation", List.of(deleteField));
        var schema = GraphQLSchema.newSchema()
                .query(queryType)
                .mutation(mutationType)
                .build();
        var engine = new ExecutionEngine(schema);

        var result = engine.execute("mutation { delete(id: \"1\") }", null, null, null);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().getFirst().message()).isEqualTo("Not found");
    }

    @Test
    void testMutationNotSupportedSchema() {
        var queryType = ObjectType.of("Query", List.of(
                FieldDefinition.of("x", ScalarType.INT)));
        var schema = GraphQLSchema.newSchema().query(queryType).build();
        var engine = new ExecutionEngine(schema);

        var result = engine.execute("mutation { x }", null, null, null);
        assertThat(result.hasErrors()).isTrue();
    }
}
