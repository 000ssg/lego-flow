package ssg.legoflow.service.functional;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.Service;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class ServiceBuilder<I, O> {

    private final Class<I> inputType;
    private final Class<O> outputType;
    private ServiceDescriptor descriptor;
    private BiFunction<Context, I[], O[]> convertToOutput;
    private BiFunction<Context, O[], I[]> convertToInput;
    private BiConsumer<ServiceContext, Service<I, O>> onConnect;
    private BiConsumer<ServiceContext, Service<I, O>> onDisconnect;

    private ServiceBuilder(Class<I> inputType, Class<O> outputType) {
        this.inputType = inputType;
        this.outputType = outputType;
    }

    public static <I, O> ServiceBuilder<I, O> of(Class<I> inputType, Class<O> outputType) {
        return new ServiceBuilder<>(inputType, outputType);
    }

    public ServiceBuilder<I, O> descriptor(ServiceDescriptor descriptor) {
        this.descriptor = descriptor;
        return this;
    }

    public ServiceBuilder<I, O> descriptor(String name, String description) {
        this.descriptor = new ServiceDescriptor(name, description);
        return this;
    }

    public ServiceBuilder<I, O> onConvertToOutput(BiFunction<Context, I[], O[]> fn) {
        this.convertToOutput = fn;
        return this;
    }

    public ServiceBuilder<I, O> onConvertToInput(BiFunction<Context, O[], I[]> fn) {
        this.convertToInput = fn;
        return this;
    }

    public ServiceBuilder<I, O> onConnect(BiConsumer<ServiceContext, Service<I, O>> handler) {
        this.onConnect = handler;
        return this;
    }

    public ServiceBuilder<I, O> onDisconnect(BiConsumer<ServiceContext, Service<I, O>> handler) {
        this.onDisconnect = handler;
        return this;
    }

    @SuppressWarnings("unchecked")
    public Service<I, O> build() {
        if (descriptor == null) throw new IllegalStateException("ServiceDescriptor is required");
        if (convertToOutput == null) throw new IllegalStateException("convertToOutput function is required");
        if (convertToInput == null) throw new IllegalStateException("convertToInput function is required");

        var desc = this.descriptor;
        var toOutput = this.convertToOutput;
        var toInput = this.convertToInput;
        var connectHandler = this.onConnect;
        var disconnectHandler = this.onDisconnect;

        return new AbstractService<I, O>(inputType, outputType, desc) {

            @Override
            protected O[] convertToOutput(Context ctx, I... input) {
                return toOutput.apply(ctx, input);
            }

            @Override
            protected I[] convertToInput(Context ctx, O... output) {
                return toInput.apply(ctx, output);
            }

            @Override
            protected void doConnect(ServiceContext ctx) {
                if (connectHandler != null) connectHandler.accept(ctx, this);
            }

            @Override
            protected void doDisconnect(ServiceContext ctx) {
                if (disconnectHandler != null) disconnectHandler.accept(ctx, this);
            }
        };
    }
}
