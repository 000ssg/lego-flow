package ssg.legoflow.service.manager;

import ssg.legoflow.service.Service;
import ssg.legoflow.service.ServiceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
public class ConnectionThread {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionThread.class);

    private final Service<?, ?> service;
    private final ServiceContext context;
    private volatile Thread thread;

    public ConnectionThread(Service<?, ?> service, ServiceContext context) {
        this.service = service;
        this.context = context;
    }

    public CompletableFuture<Void> start() {
        var future = new CompletableFuture<Void>();
        thread = Thread.ofVirtual()
                .name("connection-" + service.getDescriptor().name())
                .start(() -> {
                    try {
                        LOG.debug("Connecting service: {}", service.getDescriptor().name());
                        service.connect(context);
                        future.complete(null);
                    } catch (Exception e) {
                        LOG.error("Failed to connect service: {}", service.getDescriptor().name(), e);
                        future.completeExceptionally(e);
                    }
                });
        return future;
    }

    public void cancel() {
        var t = thread;
        if (t != null && t.isAlive()) {
            t.interrupt();
        }
    }

    public boolean isAlive() {
        var t = thread;
        return t != null && t.isAlive();
    }
}
