package ssg.legoflow.service;

import ssg.legoflow.blocks.AbstractDataProcessor;
import ssg.legoflow.blocks.ProcessorState;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractService<I, O> extends AbstractDataProcessor<I, O> implements Service<I, O> {

    private final ServiceDescriptor descriptor;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private ServiceContext serviceContext;

    protected AbstractService(Class<I> inputType, Class<O> outputType, ServiceDescriptor descriptor) {
        super(inputType, outputType);
        this.descriptor = descriptor;
    }

    @Override
    public ServiceDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public ServiceContext getServiceContext() {
        return serviceContext;
    }

    @Override
    public void connect(ServiceContext ctx) {
        this.serviceContext = ctx;
        transitionTo(ProcessorState.CONNECTING);
        doConnect(ctx);
        connected.set(true);
        transitionTo(ProcessorState.READY);
    }

    @Override
    public void disconnect(ServiceContext ctx) {
        doDisconnect(ctx);
        connected.set(false);
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public List<String> getDependencies() {
        return descriptor.dependencies();
    }

    @Override
    public int getPriority() {
        return descriptor.priority();
    }

    protected void doConnect(ServiceContext ctx) {}

    protected void doDisconnect(ServiceContext ctx) {}
}
