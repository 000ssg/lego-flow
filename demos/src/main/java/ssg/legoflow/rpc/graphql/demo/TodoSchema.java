package ssg.legoflow.rpc.graphql.demo;

import ssg.legoflow.rpc.graphql.schema.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Todo CRUD application schema with mutations.
 *
 * @since 0.1.0
 */
public final class TodoSchema {

    private final Map<Integer, Map<String, Object>> todos = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);

    /**
     * Creates a new TodoSchema instance with sample data.
     */
    public TodoSchema() {
        addTodo("Learn GraphQL", false);
        addTodo("Build a schema", false);
        addTodo("Write tests", true);
    }

    private Map<String, Object> addTodo(String title, boolean completed) {
        int id = idCounter.getAndIncrement();
        var todo = new LinkedHashMap<String, Object>();
        todo.put("id", id);
        todo.put("title", title);
        todo.put("completed", completed);
        todos.put(id, todo);
        return todo;
    }

    /**
     * Creates the Todo GraphQL schema.
     *
     * @return the schema
     */
    public GraphQLSchema create() {
        var statusEnum = EnumType.of("TodoStatus", "ALL", "ACTIVE", "COMPLETED");

        var todoInput = InputObjectType.of("TodoInput", List.of(
                InputObjectType.InputFieldDefinition.of("title", NonNullType.of(ScalarType.STRING)),
                InputObjectType.InputFieldDefinition.of("completed", ScalarType.BOOLEAN, false)
        ));

        var todoType = ObjectType.of("Todo", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID)),
                FieldDefinition.of("title", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("completed", NonNullType.of(ScalarType.BOOLEAN))
        ));

        // Query
        var todosField = FieldDefinition.of("todos", NonNullType.of(ListType.of(NonNullType.of(todoType))),
                List.of(ArgumentDefinition.of("status", statusEnum, "ALL")));
        todosField.dataFetcher(env -> {
            String status = env.getArgument("status");
            return switch (status != null ? status : "ALL") {
                case "ACTIVE" -> todos.values().stream()
                        .filter(t -> !Boolean.TRUE.equals(t.get("completed"))).toList();
                case "COMPLETED" -> todos.values().stream()
                        .filter(t -> Boolean.TRUE.equals(t.get("completed"))).toList();
                default -> new ArrayList<>(todos.values());
            };
        });

        var todoField = FieldDefinition.of("todo", todoType,
                List.of(ArgumentDefinition.of("id", NonNullType.of(ScalarType.ID))));
        todoField.dataFetcher(env -> {
            var id = env.getArgument("id");
            int todoId = id instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(id));
            return todos.get(todoId);
        });

        var todoCountField = FieldDefinition.of("todoCount", NonNullType.of(ScalarType.INT));
        todoCountField.dataFetcher(env -> todos.size());

        var queryType = ObjectType.of("Query", List.of(todosField, todoField, todoCountField));

        // Mutation
        var addTodoField = FieldDefinition.of("addTodo", NonNullType.of(todoType),
                List.of(ArgumentDefinition.of("input", NonNullType.of(todoInput))));
        addTodoField.dataFetcher(env -> {
            @SuppressWarnings("unchecked")
            var input = (Map<String, Object>) env.getArgument("input");
            var title = (String) input.get("title");
            var completed = Boolean.TRUE.equals(input.get("completed"));
            return addTodo(title, completed);
        });

        var toggleTodoField = FieldDefinition.of("toggleTodo", todoType,
                List.of(ArgumentDefinition.of("id", NonNullType.of(ScalarType.ID))));
        toggleTodoField.dataFetcher(env -> {
            var id = env.getArgument("id");
            int todoId = id instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(id));
            var todo = todos.get(todoId);
            if (todo != null) {
                todo.put("completed", !Boolean.TRUE.equals(todo.get("completed")));
            }
            return todo;
        });

        var deleteTodoField = FieldDefinition.of("deleteTodo", todoType,
                List.of(ArgumentDefinition.of("id", NonNullType.of(ScalarType.ID))));
        deleteTodoField.dataFetcher(env -> {
            var id = env.getArgument("id");
            int todoId = id instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(id));
            return todos.remove(todoId);
        });

        var clearCompletedField = FieldDefinition.of("clearCompleted",
                NonNullType.of(ListType.of(NonNullType.of(todoType))));
        clearCompletedField.dataFetcher(env -> {
            var cleared = todos.values().stream()
                    .filter(t -> Boolean.TRUE.equals(t.get("completed"))).toList();
            cleared.forEach(t -> todos.remove(t.get("id")));
            return cleared;
        });

        var mutationType = ObjectType.of("Mutation", List.of(
                addTodoField, toggleTodoField, deleteTodoField, clearCompletedField));

        return GraphQLSchema.newSchema()
                .query(queryType)
                .mutation(mutationType)
                .additionalType(statusEnum)
                .additionalType(todoInput)
                .build();
    }

    /**
     * Returns the current todos (for testing).
     *
     * @return the todos map
     */
    public Map<Integer, Map<String, Object>> getTodos() {
        return Collections.unmodifiableMap(todos);
    }
}
