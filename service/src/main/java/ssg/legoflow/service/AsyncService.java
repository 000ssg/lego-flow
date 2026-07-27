package ssg.legoflow.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.blocks.ProcessorStatistics;

import java.util.concurrent.CompletableFuture;

public interface AsyncService<I, O> {

    @SuppressWarnings("unchecked")
    CompletableFuture<Void> consume(ServiceContext ctx, I... data);

    @SuppressWarnings("unchecked")
    CompletableFuture<Void> submit(ServiceContext ctx, O... data);

    CompletableFuture<Void> connect(ServiceContext ctx);

    CompletableFuture<Void> disconnect(ServiceContext ctx);

    ProcessorState getState();

    ProcessorStatistics getStatistics();

    Service<I, O> sync();
}
