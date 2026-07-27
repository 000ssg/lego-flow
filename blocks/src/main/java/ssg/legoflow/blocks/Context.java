package ssg.legoflow.blocks;

import org.slf4j.Logger;

public interface Context {

    Logger getLogger();

    ProcessorStatistics getStatistics();

    void handleError(Throwable error);

    <T> T getAttribute(String key);

    void setAttribute(String key, Object value);
}
