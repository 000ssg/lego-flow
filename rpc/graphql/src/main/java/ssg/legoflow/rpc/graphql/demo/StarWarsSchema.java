package ssg.legoflow.rpc.graphql.demo;

import ssg.legoflow.rpc.graphql.schema.*;

import java.util.*;

/**
 * Classic Star Wars example schema for GraphQL demonstrations.
 *
 * <p>Includes characters (Human, Droid) implementing a Character interface,
 * an Episode enum, and a SearchResult union type.
 *
 * @since 1.0.0
 */
public final class StarWarsSchema {

    // --- Data ---
    private static final Map<String, Map<String, Object>> HUMANS = new LinkedHashMap<>();
    private static final Map<String, Map<String, Object>> DROIDS = new LinkedHashMap<>();

    static {
        HUMANS.put("1000", Map.of(
                "id", "1000", "name", "Luke Skywalker",
                "homePlanet", "Tatooine",
                "appearsIn", List.of("NEWHOPE", "EMPIRE", "JEDI"),
                "friends", List.of("1002", "1003", "2000", "2001"),
                "__typename", "Human"));
        HUMANS.put("1001", Map.of(
                "id", "1001", "name", "Darth Vader",
                "homePlanet", "Tatooine",
                "appearsIn", List.of("NEWHOPE", "EMPIRE", "JEDI"),
                "friends", List.of("1004"),
                "__typename", "Human"));
        HUMANS.put("1002", Map.of(
                "id", "1002", "name", "Han Solo",
                "homePlanet", "",
                "appearsIn", List.of("NEWHOPE", "EMPIRE", "JEDI"),
                "friends", List.of("1000", "1003", "2001"),
                "__typename", "Human"));
        HUMANS.put("1003", Map.of(
                "id", "1003", "name", "Leia Organa",
                "homePlanet", "Alderaan",
                "appearsIn", List.of("NEWHOPE", "EMPIRE", "JEDI"),
                "friends", List.of("1000", "1002", "2000", "2001"),
                "__typename", "Human"));
        HUMANS.put("1004", Map.of(
                "id", "1004", "name", "Wilhuff Tarkin",
                "homePlanet", "",
                "appearsIn", List.of("NEWHOPE"),
                "friends", List.of("1001"),
                "__typename", "Human"));

        DROIDS.put("2000", Map.of(
                "id", "2000", "name", "C-3PO",
                "primaryFunction", "Protocol",
                "appearsIn", List.of("NEWHOPE", "EMPIRE", "JEDI"),
                "friends", List.of("1000", "1002", "1003", "2001"),
                "__typename", "Droid"));
        DROIDS.put("2001", Map.of(
                "id", "2001", "name", "R2-D2",
                "primaryFunction", "Astromech",
                "appearsIn", List.of("NEWHOPE", "EMPIRE", "JEDI"),
                "friends", List.of("1000", "1002", "1003"),
                "__typename", "Droid"));
    }

    private StarWarsSchema() {}

    /**
     * Creates the Star Wars GraphQL schema.
     *
     * @return the schema
     */
    public static GraphQLSchema create() {
        // Episode enum
        var episodeEnum = EnumType.of("Episode", "NEWHOPE", "EMPIRE", "JEDI");

        // Character interface
        var characterInterface = InterfaceType.of("Character", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID)),
                FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("friends", ListType.of(ScalarType.STRING)),
                FieldDefinition.of("appearsIn", NonNullType.of(ListType.of(episodeEnum)))
        ));

        // Human type
        var humanType = ObjectType.of("Human", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID)),
                FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("homePlanet", ScalarType.STRING),
                FieldDefinition.of("friends", ListType.of(ScalarType.STRING)),
                FieldDefinition.of("appearsIn", NonNullType.of(ListType.of(episodeEnum)))
        ), List.of(characterInterface));

        // Droid type
        var droidType = ObjectType.of("Droid", List.of(
                FieldDefinition.of("id", NonNullType.of(ScalarType.ID)),
                FieldDefinition.of("name", NonNullType.of(ScalarType.STRING)),
                FieldDefinition.of("primaryFunction", ScalarType.STRING),
                FieldDefinition.of("friends", ListType.of(ScalarType.STRING)),
                FieldDefinition.of("appearsIn", NonNullType.of(ListType.of(episodeEnum)))
        ), List.of(characterInterface));

        // SearchResult union
        var searchResult = UnionType.of("SearchResult", List.of(humanType, droidType));

        // Query type
        var heroField = FieldDefinition.of("hero", characterInterface,
                List.of(ArgumentDefinition.of("episode", episodeEnum)));
        heroField.dataFetcher(env -> {
            String episode = env.getArgument("episode");
            if ("EMPIRE".equals(episode)) return DROIDS.get("2001");
            return HUMANS.get("1000");
        });

        var humanField = FieldDefinition.of("human", humanType,
                List.of(ArgumentDefinition.of("id", NonNullType.of(ScalarType.ID))));
        humanField.dataFetcher(env -> {
            String id = env.getArgument("id");
            return HUMANS.get(id);
        });

        var droidField = FieldDefinition.of("droid", droidType,
                List.of(ArgumentDefinition.of("id", NonNullType.of(ScalarType.ID))));
        droidField.dataFetcher(env -> {
            String id = env.getArgument("id");
            return DROIDS.get(id);
        });

        var searchField = FieldDefinition.of("search", ListType.of(searchResult),
                List.of(ArgumentDefinition.of("text", ScalarType.STRING)));
        searchField.dataFetcher(env -> {
            String text = env.getArgument("text");
            var results = new ArrayList<Map<String, Object>>();
            for (var h : HUMANS.values()) {
                if (text == null || ((String) h.get("name")).toLowerCase().contains(text.toLowerCase())) {
                    results.add(h);
                }
            }
            for (var d : DROIDS.values()) {
                if (text == null || ((String) d.get("name")).toLowerCase().contains(text.toLowerCase())) {
                    results.add(d);
                }
            }
            return results;
        });

        var queryType = ObjectType.of("Query", List.of(heroField, humanField, droidField, searchField));

        return GraphQLSchema.newSchema()
                .query(queryType)
                .additionalType(episodeEnum)
                .additionalType(searchResult)
                .build();
    }

    /**
     * Returns a character by ID (human or droid).
     *
     * @param id the character ID
     * @return the character data, or null
     */
    public static Map<String, Object> getCharacter(String id) {
        var human = HUMANS.get(id);
        if (human != null) return human;
        return DROIDS.get(id);
    }
}
