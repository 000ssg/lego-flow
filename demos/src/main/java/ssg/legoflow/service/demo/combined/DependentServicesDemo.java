package ssg.legoflow.service.demo.combined;

import ssg.legoflow.service.Service;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.functional.ServiceBuilder;
import ssg.legoflow.service.manager.AbstractServicesManager;

import java.util.List;

/**
 * Demonstrates dependency-aware service management: services that depend
 * on each other are started in correct order and stopped in reverse.
 *
 * @since 0.1
 */
public class DependentServicesDemo {

    private final AbstractServicesManager manager;
    private final Service<String, String> database;
    private final Service<String, String> cache;
    private final Service<String, String> api;

    public DependentServicesDemo(ServiceContext ctx) {
        manager = new AbstractServicesManager(ctx);

        database = ServiceBuilder.of(String.class, String.class)
                .descriptor(new ServiceDescriptor("database", "Database", 0, List.of()))
                .onConvertToOutput((c, i) -> i)
                .onConvertToInput((c, o) -> o)
                .build();

        cache = ServiceBuilder.of(String.class, String.class)
                .descriptor(new ServiceDescriptor("cache", "Cache", 1, List.of("database")))
                .onConvertToOutput((c, i) -> i)
                .onConvertToInput((c, o) -> o)
                .build();

        api = ServiceBuilder.of(String.class, String.class)
                .descriptor(new ServiceDescriptor("api", "API", 2, List.of("cache")))
                .onConvertToOutput((c, i) -> i)
                .onConvertToInput((c, o) -> o)
                .build();

        manager.register(database);
        manager.register(cache);
        manager.register(api);
    }

    public void startAll() {
        manager.startAll();
    }

    public void startService(String name) {
        manager.start(name);
    }

    public void stopService(String name) {
        manager.stop(name);
    }

    public void stopAll() {
        manager.stopAll();
    }

    public void close() {
        manager.close();
    }

    public AbstractServicesManager getManager() {
        return manager;
    }

    public Service<String, String> getDatabase() {
        return database;
    }

    public Service<String, String> getCache() {
        return cache;
    }

    public Service<String, String> getApi() {
        return api;
    }
}
