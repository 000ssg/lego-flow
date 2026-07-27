package ssg.legoflow.rpc.graphql.execution;

import org.junit.jupiter.api.Test;
import ssg.legoflow.rpc.graphql.schema.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class InterfaceUnionExecutionTest {

    @Test
    void testInterfaceResolution() {
        var animalInterface = InterfaceType.of("Animal", List.of(
                FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("sound", ScalarType.STRING)));

        var dogType = ObjectType.of("Dog", List.of(
                FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("sound", ScalarType.STRING),
                FieldDefinition.of("breed", ScalarType.STRING)),
                List.of(animalInterface));

        var catType = ObjectType.of("Cat", List.of(
                FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("sound", ScalarType.STRING),
                FieldDefinition.of("indoor", ScalarType.BOOLEAN)),
                List.of(animalInterface));

        var animalField = FieldDefinition.of("animal", animalInterface);
        animalField.dataFetcher(env -> Map.of(
                "name", "Rex", "sound", "Woof", "breed", "Labrador",
                "__typename", "Dog"));

        var queryType = ObjectType.of("Query", List.of(animalField));
        var schema = GraphQLSchema.newSchema()
                .query(queryType)
                .additionalType(dogType)
                .additionalType(catType)
                .build();
        var engine = new ExecutionEngine(schema);

        var result = engine.execute("""
                {
                  animal {
                    name
                    sound
                    ... on Dog { breed }
                  }
                }
                """, null, null, null);

        assertThat(result.hasErrors()).isFalse();
        var data = (Map<String, Object>) result.getData();
        var animal = (Map<String, Object>) data.get("animal");
        assertThat(animal.get("name")).isEqualTo("Rex");
        assertThat(animal.get("breed")).isEqualTo("Labrador");
    }

    @Test
    void testUnionResolution() {
        var bookType = ObjectType.of("Book", List.of(
                FieldDefinition.of("title", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("author", ScalarType.STRING)));

        var movieType = ObjectType.of("Movie", List.of(
                FieldDefinition.of("title", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("director", ScalarType.STRING)));

        var searchResult = UnionType.of("SearchResult", List.of(bookType, movieType));

        var searchField = FieldDefinition.of("search", ListType.of(searchResult));
        searchField.dataFetcher(env -> List.of(
                Map.of("title", "Lord of the Rings", "author", "Tolkien", "__typename", "Book"),
                Map.of("title", "Inception", "director", "Nolan", "__typename", "Movie")));

        var queryType = ObjectType.of("Query", List.of(searchField));
        var schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();
        var engine = new ExecutionEngine(schema);

        var result = engine.execute("""
                {
                  search {
                    ... on Book { title author }
                    ... on Movie { title director }
                  }
                }
                """, null, null, null);

        assertThat(result.hasErrors()).isFalse();
        var data = (Map<String, Object>) result.getData();
        var items = (List<Map<String, Object>>) data.get("search");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("author")).isEqualTo("Tolkien");
        assertThat(items.get(1).get("director")).isEqualTo("Nolan");
    }

    @Test
    void testFragmentOnInterface() {
        var nodeInterface = InterfaceType.of("Node", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID))));
        var userType = ObjectType.of("User", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID)),
                FieldDefinition.of("name", ScalarType.STRING)),
                List.of(nodeInterface));

        var nodeField = FieldDefinition.of("node", nodeInterface);
        nodeField.dataFetcher(env -> Map.of("id", "1", "name", "Alice", "__typename", "User"));

        var queryType = ObjectType.of("Query", List.of(nodeField));
        var schema = GraphQLSchema.newSchema()
                .query(queryType)
                .additionalType(userType)
                .build();
        var engine = new ExecutionEngine(schema);

        var result = engine.execute("""
                {
                  node {
                    id
                    ...UserFragment
                  }
                }
                fragment UserFragment on User {
                  name
                }
                """, null, null, null);

        assertThat(result.hasErrors()).isFalse();
        var data = (Map<String, Object>) result.getData();
        var node = (Map<String, Object>) data.get("node");
        assertThat(node.get("name")).isEqualTo("Alice");
    }
}
