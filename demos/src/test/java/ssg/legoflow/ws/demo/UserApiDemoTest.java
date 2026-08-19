package ssg.legoflow.ws.demo;

import ssg.legoflow.blocks.ProcessorStatistics;
import ssg.legoflow.http.core.*;
import ssg.legoflow.service.scope.*;
import ssg.legoflow.service.user.ServiceRole;
import ssg.legoflow.service.user.ServiceUser;
import ssg.legoflow.ws.WebServiceContext;
import ssg.legoflow.ws.WebServiceDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class UserApiDemoTest {

    private UserApiService service;
    private WebServiceContext authenticatedCtx;
    private WebServiceContext anonymousCtx;

    @BeforeEach
    void setUp() {
        service = new UserApiService();
        authenticatedCtx = new StubWebServiceContext(
                ServiceUser.exact("1", "admin", Set.of(ServiceRole.USER, ServiceRole.ADMIN)));
        anonymousCtx = new StubWebServiceContext(ServiceUser.anonymous());
    }

    @Test
    void testAnonymousCanListUsers() {
        var request = HttpRequest.of(HttpMethod.GET, "/users");
        var response = service.handle(anonymousCtx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testAnonymousCannotCreateUser() {
        var request = HttpRequest.of(HttpMethod.POST, "/users");
        request.setBody(ByteBuffer.wrap("{\"name\":\"Alice\"}".getBytes(StandardCharsets.UTF_8)));
        var response = service.handle(anonymousCtx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testAuthenticatedCanCreateUser() {
        var request = HttpRequest.of(HttpMethod.POST, "/users");
        request.setBody(ByteBuffer.wrap("{\"name\":\"Alice\"}".getBytes(StandardCharsets.UTF_8)));
        var response = service.handle(authenticatedCtx, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBodyAsString(StandardCharsets.UTF_8)).contains("Alice");
        assertThat(service.getUserCount()).isEqualTo(1);
    }

    @Test
    void testAuthenticatedCanUpdateUser() {
        var create = HttpRequest.of(HttpMethod.POST, "/users");
        create.setBody(ByteBuffer.wrap("{\"name\":\"Alice\"}".getBytes(StandardCharsets.UTF_8)));
        service.handle(authenticatedCtx, create);

        var update = HttpRequest.of(HttpMethod.PUT, "/users");
        update.setBody(ByteBuffer.wrap("{\"id\":\"1\",\"name\":\"Alice Updated\"}".getBytes(StandardCharsets.UTF_8)));
        var response = service.handle(authenticatedCtx, update);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString(StandardCharsets.UTF_8)).contains("Alice Updated");
    }

    @Test
    void testAnonymousCannotUpdateUser() {
        var create = HttpRequest.of(HttpMethod.POST, "/users");
        create.setBody(ByteBuffer.wrap("{\"name\":\"Alice\"}".getBytes(StandardCharsets.UTF_8)));
        service.handle(authenticatedCtx, create);

        var update = HttpRequest.of(HttpMethod.PUT, "/users");
        update.setBody(ByteBuffer.wrap("{\"id\":\"1\",\"name\":\"Hacked\"}".getBytes(StandardCharsets.UTF_8)));
        var response = service.handle(anonymousCtx, update);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testAuthenticatedCanDeleteUser() {
        var create = HttpRequest.of(HttpMethod.POST, "/users");
        create.setBody(ByteBuffer.wrap("{\"name\":\"ToDelete\"}".getBytes(StandardCharsets.UTF_8)));
        service.handle(authenticatedCtx, create);
        assertThat(service.getUserCount()).isEqualTo(1);

        var delete = HttpRequest.of(HttpMethod.DELETE, "/users?id=1");
        var response = service.handle(authenticatedCtx, delete);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(service.getUserCount()).isEqualTo(0);
    }

    @Test
    void testAnonymousCannotDeleteUser() {
        var create = HttpRequest.of(HttpMethod.POST, "/users");
        create.setBody(ByteBuffer.wrap("{\"name\":\"Protected\"}".getBytes(StandardCharsets.UTF_8)));
        service.handle(authenticatedCtx, create);

        var delete = HttpRequest.of(HttpMethod.DELETE, "/users?id=1");
        var response = service.handle(anonymousCtx, delete);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(service.getUserCount()).isEqualTo(1);
    }

    @Test
    void testGetUserById() {
        var create = HttpRequest.of(HttpMethod.POST, "/users");
        create.setBody(ByteBuffer.wrap("{\"name\":\"Bob\"}".getBytes(StandardCharsets.UTF_8)));
        service.handle(authenticatedCtx, create);

        var get = HttpRequest.of(HttpMethod.GET, "/users?id=1");
        var response = service.handle(anonymousCtx, get);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString(StandardCharsets.UTF_8)).contains("Bob");
    }

    @Test
    void testGetNonExistentUser() {
        var get = HttpRequest.of(HttpMethod.GET, "/users?id=999");
        var response = service.handle(anonymousCtx, get);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static class StubWebServiceContext implements WebServiceContext {
        private final ServiceUser user;
        private final Map<String, Object> attributes = new HashMap<>();

        StubWebServiceContext(ServiceUser user) {
            this.user = user;
        }

        @Override public WebServiceDescriptor getServiceDescriptor() { return null; }
        @Override public String getPathParameter(String name) { return null; }
        @Override public String getQueryParameter(String name) { return null; }
        @Override public HttpRequest getRequest() { return null; }
        @Override public HttpResponse getResponse() { return null; }
        @Override public void setResponse(HttpResponse response) {}
        @Override public org.slf4j.Logger getLogger() { return LoggerFactory.getLogger(StubWebServiceContext.class); }
        @Override public ProcessorStatistics getStatistics() { return null; }
        @Override public void handleError(Throwable error) {}
        @Override @SuppressWarnings("unchecked") public <T> T getAttribute(String key) { return (T) attributes.get(key); }
        @Override public void setAttribute(String key, Object value) { attributes.put(key, value); }
        @Override public SiteScope getSiteScope() { return null; }
        @Override public ApplicationScope getApplicationScope() { return null; }
        @Override public SessionScope getSessionScope() { return null; }
        @Override public RequestScope getRequestScope() { return null; }
        @Override public ServiceUser getUser() { return user; }
        @Override public boolean hasRole(ServiceRole role) { return user.hasRole(role); }
        @Override public void checkPermission(String operation) {}
    }
}
