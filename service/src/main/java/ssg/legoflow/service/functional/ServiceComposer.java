package ssg.legoflow.service.functional;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.Service;
import ssg.legoflow.service.ServiceContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class ServiceComposer {

    private final List<Service<?, ?>> chain = new ArrayList<>();

    public ServiceComposer add(Service<?, ?> service) {
        chain.add(service);
        return this;
    }

    public List<Service<?, ?>> getChain() {
        return List.copyOf(chain);
    }

    public void connectAll(ServiceContext ctx) {
        for (var svc : chain) {
            if (!svc.isConnected()) {
                svc.connect(ctx);
            }
        }
    }

    public void disconnectAll(ServiceContext ctx) {
        for (int i = chain.size() - 1; i >= 0; i--) {
            var svc = chain.get(i);
            if (svc.isConnected()) {
                svc.disconnect(ctx);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <I, M> void wire(Service<I, M> source, Service<M, ?> target) {
        BiConsumer<Context, M[]> forwardAccept = (ctx, data) -> target.consume(ctx, data);
        chain.add(source);
        if (!chain.contains(target)) {
            chain.add(target);
        }
    }

    public static ServiceComposer compose(Service<?, ?>... services) {
        var composer = new ServiceComposer();
        for (var svc : services) {
            composer.add(svc);
        }
        return composer;
    }
}
