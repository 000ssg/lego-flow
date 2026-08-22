package ssg.legoflow.http.connection;

import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PipeliningHandlerTest {

    @Test
    void testEnqueueAndDequeueRequest() {
        // Given
        var handler = new PipeliningHandler();
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");

        // When
        boolean enqueued = handler.enqueueRequest(request);
        var dequeued = handler.dequeueRequest();

        // Then
        assertThat(enqueued).isTrue();
        assertThat(dequeued).isNotNull();
        assertThat(dequeued.getUri()).isEqualTo("/api/data");
    }

    @Test
    void testFIFOOrdering() {
        // Given
        var handler = new PipeliningHandler();

        // When
        handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/first"));
        handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/second"));
        handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/third"));

        // Then — FIFO order
        assertThat(handler.dequeueRequest().getUri()).isEqualTo("/first");
        assertThat(handler.dequeueRequest().getUri()).isEqualTo("/second");
        assertThat(handler.dequeueRequest().getUri()).isEqualTo("/third");
    }

    @Test
    void testMaxPipelinedRequests() {
        // Given
        var handler = new PipeliningHandler(2);

        // When
        assertThat(handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/1"))).isTrue();
        assertThat(handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/2"))).isTrue();
        assertThat(handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/3"))).isFalse();
    }

    @Test
    void testPendingCount() {
        // Given
        var handler = new PipeliningHandler();

        // When
        handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/1"));
        handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/2"));

        // Then
        assertThat(handler.getPendingCount()).isEqualTo(2);
    }

    @Test
    void testResponseFIFO() {
        // Given
        var handler = new PipeliningHandler();
        handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/1"));
        handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/2"));

        // When
        handler.enqueueResponse(HttpResponse.of(HttpStatus.OK, "first"));
        handler.enqueueResponse(HttpResponse.of(HttpStatus.OK, "second"));

        // Then
        assertThat(handler.dequeueResponse().getBodyAsString()).isEqualTo("first");
        assertThat(handler.dequeueResponse().getBodyAsString()).isEqualTo("second");
    }

    @Test
    void testHasPendingRequests() {
        var handler = new PipeliningHandler();
        assertThat(handler.hasPendingRequests()).isFalse();

        handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/test"));
        assertThat(handler.hasPendingRequests()).isTrue();
    }

    @Test
    void testHasReadyResponses() {
        var handler = new PipeliningHandler();
        assertThat(handler.hasReadyResponses()).isFalse();

        handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/test"));
        handler.enqueueResponse(HttpResponse.of(HttpStatus.OK));
        assertThat(handler.hasReadyResponses()).isTrue();
    }

    @Test
    void testClear() {
        var handler = new PipeliningHandler();
        handler.enqueueRequest(HttpRequest.of(HttpMethod.GET, "/1"));
        handler.enqueueResponse(HttpResponse.of(HttpStatus.OK));

        handler.clear();

        assertThat(handler.hasPendingRequests()).isFalse();
        assertThat(handler.hasReadyResponses()).isFalse();
        assertThat(handler.getPendingCount()).isEqualTo(0);
    }

    @Test
    void testDequeueEmptyReturnsNull() {
        var handler = new PipeliningHandler();
        assertThat(handler.dequeueRequest()).isNull();
        assertThat(handler.dequeueResponse()).isNull();
    }

    @Test
    void testInvalidMaxPipelinedRequests() {
        assertThatThrownBy(() -> new PipeliningHandler(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDefaultMaxPipelinedRequests() {
        var handler = new PipeliningHandler();
        assertThat(handler.getMaxPipelinedRequests()).isEqualTo(10);
    }
}
