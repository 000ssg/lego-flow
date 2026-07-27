package ssg.legoflow.database.redis.commands;

import org.junit.jupiter.api.*;
import ssg.legoflow.database.redis.client.RedisClient;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.RedisServer;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for geo commands: GEOADD, GEODIST, GEOPOS, GEOSEARCH.
 */
class GeoCommandsTest {

    private static RedisServer server;
    private RedisClient client;

    // Known city coordinates
    private static final double PARIS_LON = 2.3522;
    private static final double PARIS_LAT = 48.8566;
    private static final double LONDON_LON = -0.1278;
    private static final double LONDON_LAT = 51.5074;
    private static final double BERLIN_LON = 13.4050;
    private static final double BERLIN_LAT = 52.5200;
    private static final double MADRID_LON = -3.7038;
    private static final double MADRID_LAT = 40.4168;

    @BeforeAll
    static void startServer() throws IOException {
        server = new RedisServer();
        server.start(0);
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void connect() throws IOException {
        client = new RedisClient("127.0.0.1", server.port());
        client.connect();
        client.execute("FLUSHALL");
    }

    @AfterEach
    void disconnect() {
        client.close();
    }

    @Test
    void testGeoaddSingleMember() throws IOException {
        long added = RedisClient.extractLong(
                client.execute("GEOADD", "cities",
                        String.valueOf(PARIS_LON), String.valueOf(PARIS_LAT), "Paris"));
        assertThat(added).isEqualTo(1);
    }

    @Test
    void testGeoaddMultipleMembers() throws IOException {
        long added = RedisClient.extractLong(
                client.execute("GEOADD", "cities",
                        String.valueOf(PARIS_LON), String.valueOf(PARIS_LAT), "Paris",
                        String.valueOf(LONDON_LON), String.valueOf(LONDON_LAT), "London",
                        String.valueOf(BERLIN_LON), String.valueOf(BERLIN_LAT), "Berlin"));
        assertThat(added).isEqualTo(3);
    }

    @Test
    void testGeoaddUpdateExisting() throws IOException {
        client.execute("GEOADD", "cities",
                String.valueOf(PARIS_LON), String.valueOf(PARIS_LAT), "Paris");
        // Add same member with different coords should return 0 (updated, not new)
        long added = RedisClient.extractLong(
                client.execute("GEOADD", "cities",
                        "3.0", "49.0", "Paris"));
        assertThat(added).isEqualTo(0);
    }

    @Test
    void testGeoaddWithNx() throws IOException {
        client.execute("GEOADD", "cities",
                String.valueOf(PARIS_LON), String.valueOf(PARIS_LAT), "Paris");
        // NX: don't update existing
        long added = RedisClient.extractLong(
                client.execute("GEOADD", "cities", "NX",
                        "3.0", "49.0", "Paris",
                        String.valueOf(LONDON_LON), String.valueOf(LONDON_LAT), "London"));
        assertThat(added).isEqualTo(1); // Only London added
    }

    @Test
    void testGeodistMeters() throws IOException {
        addCities();
        RespType resp = client.execute("GEODIST", "cities", "Paris", "London", "km");
        String distStr = RedisClient.extractString(resp);
        double dist = Double.parseDouble(distStr);
        // Paris to London is approximately 340-345 km
        assertThat(dist).isBetween(300.0, 400.0);
    }

    @Test
    void testGeodistMiles() throws IOException {
        addCities();
        RespType resp = client.execute("GEODIST", "cities", "Paris", "London", "mi");
        String distStr = RedisClient.extractString(resp);
        double dist = Double.parseDouble(distStr);
        // Paris to London ~213 miles
        assertThat(dist).isBetween(180.0, 250.0);
    }

    @Test
    void testGeodistNonexistentMember() throws IOException {
        addCities();
        RespType resp = client.execute("GEODIST", "cities", "Paris", "Tokyo", "km");
        assertThat(resp).isInstanceOf(RespType.BulkString.class);
        assertThat(((RespType.BulkString) resp).value()).isNull();
    }

    @Test
    void testGeopos() throws IOException {
        addCities();
        RespType resp = client.execute("GEOPOS", "cities", "Paris");
        assertThat(resp).isInstanceOf(RespType.Array.class);
        List<RespType> outer = ((RespType.Array) resp).elements();
        assertThat(outer).hasSize(1);

        // First element should be an array of [lon, lat]
        RespType.Array coords = (RespType.Array) outer.get(0);
        assertThat(coords.elements()).hasSize(2);
        double lon = Double.parseDouble(RedisClient.extractString(coords.elements().get(0)));
        double lat = Double.parseDouble(RedisClient.extractString(coords.elements().get(1)));
        // Should be close to original coords (geohash has some precision loss)
        assertThat(lon).isCloseTo(PARIS_LON, within(0.01));
        assertThat(lat).isCloseTo(PARIS_LAT, within(0.01));
    }

    @Test
    void testGeoposMultipleMembers() throws IOException {
        addCities();
        RespType resp = client.execute("GEOPOS", "cities", "Paris", "NonExistent", "London");
        List<RespType> results = ((RespType.Array) resp).elements();
        assertThat(results).hasSize(3);

        // Paris should have coordinates
        assertThat(((RespType.Array) results.get(0)).elements()).hasSize(2);
        // NonExistent should be nil
        assertThat(((RespType.Array) results.get(1)).elements()).isNull();
        // London should have coordinates
        assertThat(((RespType.Array) results.get(2)).elements()).hasSize(2);
    }

    @Test
    void testGeosearchByRadius() throws IOException {
        addCities();
        // Search within 500km of Paris
        RespType resp = client.execute("GEOSEARCH", "cities",
                "FROMLONLAT", String.valueOf(PARIS_LON), String.valueOf(PARIS_LAT),
                "BYRADIUS", "500", "km", "ASC");
        List<String> members = RedisClient.extractStringList(resp);
        // Paris should be in results (distance 0), London ~340km should be in
        assertThat(members).contains("Paris");
    }

    @Test
    void testGeosearchFromMember() throws IOException {
        addCities();
        // Search within 400km of London
        RespType resp = client.execute("GEOSEARCH", "cities",
                "FROMMEMBER", "London",
                "BYRADIUS", "400", "km", "ASC");
        List<String> members = RedisClient.extractStringList(resp);
        assertThat(members).contains("London");
    }

    @Test
    void testGeosearchWithCount() throws IOException {
        addCities();
        // Search within 2000km of Paris, limit to 2 results
        RespType resp = client.execute("GEOSEARCH", "cities",
                "FROMLONLAT", String.valueOf(PARIS_LON), String.valueOf(PARIS_LAT),
                "BYRADIUS", "2000", "km", "ASC", "COUNT", "2");
        List<String> members = RedisClient.extractStringList(resp);
        assertThat(members).hasSize(2);
    }

    @Test
    void testGeosearchDescOrder() throws IOException {
        addCities();
        // Search within 2000km of Paris, DESC order
        RespType resp = client.execute("GEOSEARCH", "cities",
                "FROMLONLAT", String.valueOf(PARIS_LON), String.valueOf(PARIS_LAT),
                "BYRADIUS", "2000", "km", "DESC");
        List<String> members = RedisClient.extractStringList(resp);
        assertThat(members).isNotEmpty();
        // First result should be the farthest
        // Paris itself should be last (distance 0)
        assertThat(members.get(members.size() - 1)).isEqualTo("Paris");
    }

    private void addCities() throws IOException {
        client.execute("GEOADD", "cities",
                String.valueOf(PARIS_LON), String.valueOf(PARIS_LAT), "Paris",
                String.valueOf(LONDON_LON), String.valueOf(LONDON_LAT), "London",
                String.valueOf(BERLIN_LON), String.valueOf(BERLIN_LAT), "Berlin",
                String.valueOf(MADRID_LON), String.valueOf(MADRID_LAT), "Madrid");
    }
}
