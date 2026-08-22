package ssg.legoflow.blocks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;
public class DefaultContext implements Context {

    private final Logger logger;
    private final ProcessorStatistics statistics;
    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

    public DefaultContext() {
        this(LoggerFactory.getLogger(DefaultContext.class), new ProcessorStatistics());
    }

    public DefaultContext(Logger logger, ProcessorStatistics statistics) {
        this.logger = logger;
        this.statistics = statistics;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public ProcessorStatistics getStatistics() {
        return statistics;
    }

    @Override
    public void handleError(Throwable error) {
        logger.error("Processing error: {}", error.getMessage(), error);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }
}
