package ssg.legoflow.service.demo.procedural;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceDescriptor;

public class EchoService extends AbstractService<String, String> {

    public EchoService() {
        super(String.class, String.class, new ServiceDescriptor("echo", "Echoes input back as output"));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String[] convertToOutput(Context ctx, String... input) {
        return input;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String[] convertToInput(Context ctx, String... output) {
        return output;
    }
}
