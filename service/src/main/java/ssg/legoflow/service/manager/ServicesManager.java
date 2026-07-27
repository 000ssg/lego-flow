package ssg.legoflow.service.manager;

import ssg.legoflow.blocks.ProcessorState;
import ssg.legoflow.service.Service;

import java.util.List;
import java.util.Map;

public interface ServicesManager extends AutoCloseable {

    void register(Service<?, ?> service);

    void unregister(String serviceName);

    Service<?, ?> getService(String name);

    List<Service<?, ?>> getServices();

    void startAll();

    void stopAll();

    void pauseAll();

    void resumeAll();

    void start(String serviceName);

    void stop(String serviceName);

    Map<String, ProcessorState> getStates();

    @Override
    void close();
}
