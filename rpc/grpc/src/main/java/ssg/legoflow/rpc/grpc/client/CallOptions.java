package ssg.legoflow.rpc.grpc.client;

import ssg.legoflow.rpc.grpc.metadata.Metadata;
import ssg.legoflow.rpc.grpc.transport.GrpcEncoding;
import ssg.legoflow.rpc.grpc.transport.GrpcTimeout;

import java.time.Duration;

/**
 * Options for a gRPC client call: deadline, compression, and custom metadata.
 */
public class CallOptions {

    private GrpcTimeout timeout;
    private GrpcEncoding encoding;
    private Metadata metadata;
    private String authority;
    private int maxResponseSize;

    public CallOptions() {
        this.encoding = GrpcEncoding.IDENTITY;
        this.metadata = new Metadata();
        this.maxResponseSize = 4 * 1024 * 1024;
    }

    public GrpcTimeout timeout() {
        return timeout;
    }

    public CallOptions timeout(GrpcTimeout timeout) {
        this.timeout = timeout;
        return this;
    }

    public CallOptions deadline(Duration duration) {
        this.timeout = GrpcTimeout.fromDuration(duration);
        return this;
    }

    public GrpcEncoding encoding() {
        return encoding;
    }

    public CallOptions encoding(GrpcEncoding encoding) {
        this.encoding = encoding;
        return this;
    }

    public Metadata metadata() {
        return metadata;
    }

    public CallOptions metadata(Metadata metadata) {
        this.metadata = metadata;
        return this;
    }

    public String authority() {
        return authority;
    }

    public CallOptions authority(String authority) {
        this.authority = authority;
        return this;
    }

    public int maxResponseSize() {
        return maxResponseSize;
    }

    public CallOptions maxResponseSize(int maxResponseSize) {
        this.maxResponseSize = maxResponseSize;
        return this;
    }

    public static CallOptions defaults() {
        return new CallOptions();
    }

    public static CallOptions withDeadline(Duration deadline) {
        return new CallOptions().deadline(deadline);
    }

    public static CallOptions withEncoding(GrpcEncoding encoding) {
        return new CallOptions().encoding(encoding);
    }
}
