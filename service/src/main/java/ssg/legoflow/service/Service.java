package ssg.legoflow.service;

import ssg.legoflow.blocks.DataProcessor;

import java.util.List;

public interface Service<I, O> extends DataProcessor<I, O> {

    ServiceDescriptor getDescriptor();

    ServiceContext getServiceContext();

    void connect(ServiceContext ctx);

    void disconnect(ServiceContext ctx);

    boolean isConnected();

    List<String> getDependencies();

    int getPriority();

    default AsyncService<I, O> async() {
        return new DefaultAsyncService<>(this);
    }
}
