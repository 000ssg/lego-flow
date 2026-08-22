package ssg.legoflow.benchmarks.auth;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import ssg.legoflow.http.auth.AuthContext;
import ssg.legoflow.http.auth.AuthCredentials;
import ssg.legoflow.http.auth.AuthResult;
import ssg.legoflow.http.auth.basic.BasicAuthScheme;
import ssg.legoflow.http.auth.basic.InMemoryUserStore;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;
/**
 * Benchmarks for authentication handshake performance.
 *
 * Measures the cost of Basic Authentication flow: generating an Authorization
 * header, extracting credentials, and validating against a user store.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = {"-Xms256m", "-Xmx512m"})
@State(Scope.Benchmark)
public class AuthHandshakeBenchmark {

    private static final String USERNAME = "api-user";
    private static final String PASSWORD = "s3cure-p4ssw0rd!";
    private static final String URI = "/api/v1/data?key=benchmark&limit=100";

    private BasicAuthScheme authScheme;
    private ssg.legoflow.http.core.HttpRequest authenticatedRequest;
    private ssg.legoflow.http.core.HttpRequest unauthenticatedRequest;
    private AuthContext authContext;
    private String encodedCredentials;

    @Setup(Level.Iteration)
    public void setup() {
        // Populate user store with 100 users
        InMemoryUserStore store = new InMemoryUserStore();
        store.addUser(USERNAME, PASSWORD, Set.of("admin", "reader"));
        for (int i = 0; i < 100; i++) {
            store.addUser("user-" + i, "password-" + i);
        }

        this.authScheme = new BasicAuthScheme(store);
        this.authContext = AuthContext.ofRealm("lego-flow-api");

        // Pre-build encoded credentials
        String plain = USERNAME + ":" + PASSWORD;
        this.encodedCredentials = Base64.getEncoder()
                .encodeToString(plain.getBytes(StandardCharsets.UTF_8));

        // Build an authenticated request
        authenticatedRequest = ssg.legoflow.http.core.HttpRequest.of(
                ssg.legoflow.http.core.HttpMethod.GET, URI);
        authenticatedRequest.getHeaders()
                .set("Authorization", "Basic " + encodedCredentials);

        // Build an unauthenticated request (no Authorization header)
        unauthenticatedRequest = ssg.legoflow.http.core.HttpRequest.of(
                ssg.legoflow.http.core.HttpMethod.GET, URI);
    }

    /**
     * Authenticate a valid Basic auth request.
     */
    @Benchmark
    public void authenticateValid(Blackhole bh) {
        var result = authScheme.authenticate(authenticatedRequest, authContext);
        if (result instanceof AuthResult.Success success) {
            bh.consume(true);
            bh.consume(success.principal().getName());
        }
    }

    /**
     * Authenticate an unauthenticated request (triggers challenge).
     */
    @Benchmark
    public void authenticateChallenge(Blackhole bh) {
        var result = authScheme.authenticate(unauthenticatedRequest, authContext);
        if (result instanceof AuthResult.Challenge challenge) {
            bh.consume(true);
        }
    }

    /**
     * Manually encode Base64 credentials (measures the header construction cost).
     */
    @Benchmark
    public void encodeCredentials(Blackhole bh) {
        String plain = USERNAME + ":" + PASSWORD;
        byte[] encoded = Base64.getEncoder()
                .encode(plain.getBytes(StandardCharsets.UTF_8));
        bh.consume(new String(encoded, StandardCharsets.UTF_8));
    }

    /**
     * Extract credentials from the Authorization header.
     */
    @Benchmark
    public void extractCredentials(Blackhole bh) {
        var creds = authScheme.extractCredentials(authenticatedRequest);
        if (creds instanceof AuthCredentials.Basic basic) {
            bh.consume(basic.username());
        }
    }

    /**
     * Full handshake: authenticate, check success, extract principal.
     */
    @Benchmark
    public void fullHandshake(Blackhole bh) {
        var result = authScheme.authenticate(authenticatedRequest, authContext);
        if (result instanceof AuthResult.Success success) {
            bh.consume(success.principal().getName());
            bh.consume(success.principal().getRoles().size());
        }
    }
}
