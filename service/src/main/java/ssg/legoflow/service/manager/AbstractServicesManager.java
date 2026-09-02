package ssg.legoflow.service.manager;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.Service;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.user.ServiceUser;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class AbstractServicesManager implements ServicesManager {

    private final Map<String, Service<?, ?>> services = new ConcurrentHashMap<>();
    private final ServiceContext defaultContext;

    public AbstractServicesManager() {
        this(new DefaultServiceContext(ServiceUser.anonymous()));
    }

    public AbstractServicesManager(ServiceContext defaultContext) {
        this.defaultContext = defaultContext;
    }

    @Override
    public void register(Service<?, ?> service) {
        services.put(service.getDescriptor().name(), service);
    }

    @Override
    public void unregister(String serviceName) {
        var svc = services.remove(serviceName);
        if (svc != null && svc.isConnected()) {
            svc.disconnect(defaultContext);
        }
    }

    @Override
    public Service<?, ?> getService(String name) {
        return services.get(name);
    }

    @Override
    public List<Service<?, ?>> getServices() {
        return List.copyOf(services.values());
    }

    @Override
    public void startAll() {
        sortedByDependency().forEach(svc -> {
            if (svc.getState() == ProcessorState.IDLE || svc.getState() == ProcessorState.FAILED) {
                svc.connect(defaultContext);
            }
        });
    }

    @Override
    public void stopAll() {
        var reversed = new ArrayList<>(sortedByDependency());
        Collections.reverse(reversed);
        reversed.forEach(svc -> {
            if (svc.isConnected()) {
                svc.disconnect(defaultContext);
                svc.close();
            }
        });
    }

    @Override
    public void pauseAll() {
        services.values().forEach(svc -> {
            if (svc.getState() == ProcessorState.READY) {
                // Subclasses can implement pause logic
            }
        });
    }

    @Override
    public void resumeAll() {
        services.values().forEach(svc -> {
            if (svc.getState() == ProcessorState.PAUSED) {
                // Subclasses can implement resume logic
            }
        });
    }

    @Override
    public void start(String serviceName) {
        var svc = services.get(serviceName);
        if (svc == null) throw new IllegalArgumentException("Unknown service: " + serviceName);
        for (var dep : svc.getDependencies()) {
            var depSvc = services.get(dep);
            if (depSvc != null && !depSvc.isConnected()) {
                start(dep);
            }
        }
        svc.connect(defaultContext);
    }

    @Override
    public void stop(String serviceName) {
        var svc = services.get(serviceName);
        if (svc == null) throw new IllegalArgumentException("Unknown service: " + serviceName);
        services.values().stream()
                .filter(s -> s.getDependencies().contains(serviceName))
                .filter(Service::isConnected)
                .forEach(dependent -> stop(dependent.getDescriptor().name()));
        svc.disconnect(defaultContext);
        svc.close();
    }

    @Override
    public Map<String, ProcessorState> getStates() {
        var result = new HashMap<String, ProcessorState>();
        services.forEach((name, svc) -> result.put(name, svc.getState()));
        return Map.copyOf(result);
    }

    @Override
    public void close() {
        stopAll();
        services.clear();
    }

    private List<Service<?, ?>> sortedByDependency() {
        var sorted = new ArrayList<>(services.values());
        sorted.sort(Comparator.comparingInt(Service::getPriority));
        return sorted;
    }
}
