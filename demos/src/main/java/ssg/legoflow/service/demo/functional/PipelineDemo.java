package ssg.legoflow.service.demo.functional;

import ssg.legoflow.service.functional.ServicePipeline;
import java.util.List;
public class PipelineDemo {

    private final ServicePipeline<String> pipeline;

    public PipelineDemo() {
        this.pipeline = new ServicePipeline<String>()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .map(String::toUpperCase);
    }

    public List<String> process(List<String> input) {
        return pipeline.process(input);
    }

    public ServicePipeline<String> getPipeline() {
        return pipeline;
    }
}
