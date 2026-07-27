package ssg.legoflow.blocks.exceptions;

public class ProcessorException extends RuntimeException {

    public ProcessorException(String message) {
        super(message);
    }

    public ProcessorException(String message, Throwable cause) {
        super(message, cause);
    }
}
