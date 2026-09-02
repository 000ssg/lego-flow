package ssg.legoflow.service.functional;

import ssg.legoflow.service.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
public class ServicePipeline<T> {

    private final List<Function<T, T>> mappers = new ArrayList<>();
    private final List<Predicate<T>> filters = new ArrayList<>();

    public ServicePipeline<T> map(Function<T, T> mapper) {
        mappers.add(mapper);
        return this;
    }

    public ServicePipeline<T> filter(Predicate<T> predicate) {
        filters.add(predicate);
        return this;
    }

    public List<T> process(List<T> data) {
        List<T> result = new ArrayList<>(data);
        for (var filter : filters) {
            result = result.stream().filter(filter).toList();
        }
        for (var mapper : mappers) {
            result = result.stream().map(mapper).toList();
        }
        return result;
    }

    public void forEach(List<T> data, Consumer<T> action) {
        process(data).forEach(action);
    }

    public <R> List<R> collect(List<T> data, Function<T, R> collector) {
        return process(data).stream().map(collector).toList();
    }

    public static <I, O> ServicePipeline<I> from(Service<I, O> service) {
        return new ServicePipeline<>();
    }
}
