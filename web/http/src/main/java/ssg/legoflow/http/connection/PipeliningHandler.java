package ssg.legoflow.http.connection;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * HTTP pipelining support per RFC 7230 §6.3.2.
 *
 * <p>HTTP pipelining allows a client to send multiple requests on the same
 * connection without waiting for the corresponding responses. Responses
 * MUST be sent in the same order as the requests (FIFO ordering).
 *
 * @since 0.1.0
 */
public class PipeliningHandler {

    private final Deque<PipelinedRequest> requestQueue = new ConcurrentLinkedDeque<>();
    private final Deque<HttpResponse> responseQueue = new ConcurrentLinkedDeque<>();
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private final int maxPipelinedRequests;

    /**
     * Creates a pipelining handler with the specified maximum queued requests.
     *
     * @param maxPipelinedRequests the maximum number of pipelined requests
     */
    public PipeliningHandler(int maxPipelinedRequests) {
        if (maxPipelinedRequests < 1) {
            throw new IllegalArgumentException("maxPipelinedRequests must be at least 1");
        }
        this.maxPipelinedRequests = maxPipelinedRequests;
    }

    /**
     * Creates a pipelining handler with the default maximum of 10 requests.
     */
    public PipeliningHandler() {
        this(10);
    }

    /**
     * Enqueues a pipelined request.
     *
     * @param request the HTTP request
     * @return true if the request was enqueued, false if the queue is full
     */
    public boolean enqueueRequest(HttpRequest request) {
        if (pendingCount.get() >= maxPipelinedRequests) {
            return false;
        }
        requestQueue.addLast(new PipelinedRequest(request, pendingCount.incrementAndGet()));
        return true;
    }

    /**
     * Dequeues the next request to process (FIFO order).
     *
     * @return the next request, or null if the queue is empty
     */
    public HttpRequest dequeueRequest() {
        PipelinedRequest pr = requestQueue.pollFirst();
        return pr != null ? pr.request() : null;
    }

    /**
     * Enqueues a response corresponding to the oldest pending request.
     *
     * @param response the HTTP response
     */
    public void enqueueResponse(HttpResponse response) {
        responseQueue.addLast(response);
    }

    /**
     * Dequeues the next response to send (FIFO order, matching request order).
     *
     * @return the next response, or null if none available
     */
    public HttpResponse dequeueResponse() {
        HttpResponse response = responseQueue.pollFirst();
        if (response != null) {
            pendingCount.decrementAndGet();
        }
        return response;
    }

    /**
     * Returns the number of pending (unanswered) pipelined requests.
     *
     * @return the pending count
     */
    public int getPendingCount() {
        return pendingCount.get();
    }

    /**
     * Returns true if there are pending pipelined requests.
     *
     * @return true if requests are pending
     */
    public boolean hasPendingRequests() {
        return !requestQueue.isEmpty();
    }

    /**
     * Returns true if there are responses ready to send.
     *
     * @return true if responses are ready
     */
    public boolean hasReadyResponses() {
        return !responseQueue.isEmpty();
    }

    /**
     * Returns the maximum number of pipelined requests allowed.
     *
     * @return the max pipelined requests
     */
    public int getMaxPipelinedRequests() {
        return maxPipelinedRequests;
    }

    /**
     * Clears all pending requests and responses.
     */
    public void clear() {
        requestQueue.clear();
        responseQueue.clear();
        pendingCount.set(0);
    }

    /**
     * Wraps a request with its sequence number for FIFO ordering.
     */
    private record PipelinedRequest(HttpRequest request, int sequenceNumber) {}
}
